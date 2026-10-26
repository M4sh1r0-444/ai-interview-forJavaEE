package com.example.interview.ai.provider;

import com.example.interview.ai.model.AiResumeAnalysis;
import com.example.interview.ai.model.ResumeAnalysisContext;
import com.example.interview.resume.ResumeFixtures;
import com.example.interview.resume.dto.ResumeProfile;
import com.example.interview.resume.dto.SuggestionDTO;
import com.example.interview.resume.service.ResumeProfileParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 简历计划 §8.1 MockResumeAnalyzerTest（R2-2，≥5 用例）：规则确定性（同输入同输出）、
 * 六维齐全且 0~100、分档与建议风险级一致性（<40 HIGH / 40~69 MEDIUM / ≥70 LOW）、
 * 好简历得分显著高于极简简历、空简历容错不抛异常。
 */
class MockResumeAnalyzerTest {

    private final MockAiProvider provider = new MockAiProvider();
    private final ResumeProfileParser parser = new ResumeProfileParser();

    private static final List<String> DIMENSIONS =
            List.of("basic", "education", "skill", "project", "experience", "expression");

    private ResumeAnalysisContext contextOf(String maskedText, String positionName,
                                            List<ResumeAnalysisContext.PositionSkill> positionSkills) {
        ResumeProfile profile = parser.parse(maskedText, null);
        return ResumeAnalysisContext.builder()
                .resumeId(1L)
                .maskedText(maskedText)
                .profile(profile)
                .positionName(positionName)
                .positionSkills(positionSkills)
                .build();
    }

    private ResumeAnalysisContext goodResumeContext() {
        String masked = parser.maskPii(ResumeFixtures.CHINESE_RESUME_MD);
        return contextOf(masked, "Java 后端开发工程师", List.of(
                ResumeAnalysisContext.PositionSkill.builder().name("Java基础与面向对象").weight(0.25).build(),
                ResumeAnalysisContext.PositionSkill.builder().name("并发编程与JVM").weight(0.15).build(),
                ResumeAnalysisContext.PositionSkill.builder().name("Spring生态").weight(0.2).build(),
                ResumeAnalysisContext.PositionSkill.builder().name("MySQL数据库").weight(0.2).build(),
                ResumeAnalysisContext.PositionSkill.builder().name("Redis与消息队列").weight(0.15).build(),
                ResumeAnalysisContext.PositionSkill.builder().name("沟通表达").weight(0.05).build()));
    }

    private static String riskBand(int score) {
        return score < 40 ? "HIGH" : score < 70 ? "MEDIUM" : "LOW";
    }

    @Test
    void deterministicOutputForSameInput() {
        // 同输入同输出（答辩可解释的规则引擎红线）
        AiResumeAnalysis first = provider.analyzeResume(goodResumeContext());
        AiResumeAnalysis second = provider.analyzeResume(goodResumeContext());
        assertThat(second).isEqualTo(first);
        assertThat(first.getProvider()).isEqualTo("mock");
    }

    @Test
    void sixDimensionsCompleteAndWithinRange() {
        AiResumeAnalysis r = provider.analyzeResume(goodResumeContext());
        assertThat(r.getDimensionScores()).containsOnlyKeys(DIMENSIONS);
        r.getDimensionScores().forEach((dim, score) ->
                assertThat(score).as(dim).isBetween(0, 100));
    }

    @Test
    void suggestionRiskLevelMatchesDimensionBand() {
        AiResumeAnalysis r = provider.analyzeResume(goodResumeContext());
        assertThat(r.getSuggestions()).isNotEmpty();
        for (SuggestionDTO s : r.getSuggestions()) {
            assertThat(s.getRiskLevel())
                    .as("dimension=%s score=%d", s.getDimension(), r.getDimensionScores().get(s.getDimension()))
                    .isEqualTo(riskBand(r.getDimensionScores().get(s.getDimension())));
        }
    }

    @Test
    void goodResumeOutscoresMinimalOne() {
        AiResumeAnalysis good = provider.analyzeResume(goodResumeContext());
        // 极简简历：无板块标题、无项目、无经历、篇幅短
        String minimal = parser.maskPii("张三，会一点 Java，希望找一份工作，用过 MySQL 数据库和 Redis 缓存。");
        AiResumeAnalysis weak = provider.analyzeResume(contextOf(minimal, null, null));
        double goodAvg = avg(good.getDimensionScores());
        double weakAvg = avg(weak.getDimensionScores());
        assertThat(goodAvg).isGreaterThan(weakAvg);
        // 分档可解释：好简历的 basic（要素齐全+四板块）应显著高于极简简历
        assertThat(good.getDimensionScores().get("basic"))
                .isGreaterThan(weak.getDimensionScores().get("basic") + 20);
    }

    @Test
    void minimalResumeProducesHighRiskSuggestions() {
        String minimal = parser.maskPii("张三，会一点 Java，希望找一份工作。");
        AiResumeAnalysis r = provider.analyzeResume(contextOf(minimal, null, null));
        assertThat(r.getSuggestions()).isNotEmpty();
        assertThat(r.getSuggestions()).anyMatch(s -> "HIGH".equals(s.getRiskLevel()));
    }

    @Test
    void emptyInputToleratedWithoutException() {
        AiResumeAnalysis r = provider.analyzeResume(ResumeAnalysisContext.builder()
                .resumeId(9L).maskedText("").profile(new ResumeProfile()).build());
        assertThat(r.getDimensionScores()).containsOnlyKeys(DIMENSIONS);
        assertThat(r.getStrengths()).isNotEmpty();
        assertThat(r.getSummary()).isNotBlank();
        assertThat(r.getSuggestions()).isNotEmpty();
    }

    @Test
    void suggestionsCarryEvidenceAndBoundedCount() {
        AiResumeAnalysis r = provider.analyzeResume(goodResumeContext());
        assertThat(r.getSuggestions()).hasSizeLessThanOrEqualTo(8);
        // 非亮点建议附证据句（脱敏后板块首句；板块缺失时为"无"）
        r.getSuggestions().stream()
                .filter(s -> !"LOW".equals(s.getRiskLevel()))
                .forEach(s -> assertThat(s.getEvidence()).isNotBlank());
    }

    private double avg(Map<String, Integer> scores) {
        return scores.values().stream().mapToInt(Integer::intValue).average().orElse(0);
    }
}
