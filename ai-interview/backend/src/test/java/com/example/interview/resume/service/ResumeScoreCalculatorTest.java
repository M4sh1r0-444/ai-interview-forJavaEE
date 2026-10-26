package com.example.interview.resume.service;

import com.example.interview.ai.model.AiResumeAnalysis;
import com.example.interview.resume.dto.SuggestionDTO;
import com.example.interview.resume.entity.ResumeAnalysis;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 简历计划 §8.1 ResumeScoreCalculatorTest（R2-5，≥8 用例）：六维加权与四舍五入、
 * clamp、缺维补 40、建议 riskLevel 重算与 HIGH→MEDIUM→LOW 排序、超 8 截断、
 * 不足 3 补齐、strengths/summary 兜底、provider 兜底与保留。
 */
class ResumeScoreCalculatorTest {

    private final ResumeScoreCalculator calculator = new ResumeScoreCalculator();

    private static Map<String, Integer> dims(int basic, int education, int skill,
                                             int project, int experience, int expression) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("basic", basic);
        m.put("education", education);
        m.put("skill", skill);
        m.put("project", project);
        m.put("experience", experience);
        m.put("expression", expression);
        return m;
    }

    private static AiResumeAnalysis analysis(Map<String, Integer> dims, SuggestionDTO... suggestions) {
        AiResumeAnalysis ai = new AiResumeAnalysis();
        ai.setDimensionScores(dims);
        ai.setSuggestions(List.of(suggestions));
        return ai;
    }

    // ==================== 总分 ====================

    @Test
    void overallIsWeightedSumRounded() {
        // 80×.1 + 60×.1 + 40×.25 + 20×.25 + 50×.1 + 100×.2 = 54
        AiResumeAnalysis ai = analysis(dims(80, 60, 40, 20, 50, 100));
        assertThat(calculator.aggregate(1L, ai).getOverallScore()).isEqualTo(54);

        // 0.5 恰好进位：90×.1+90×.1+90×.25+85×.25+90×.1+90×.2 = 88.75 → 89
        AiResumeAnalysis half = analysis(dims(90, 90, 90, 85, 90, 90));
        assertThat(calculator.aggregate(1L, half).getOverallScore()).isEqualTo(89);
    }

    @Test
    void clampsOutOfRangeScores() {
        ResumeAnalysis r = calculator.aggregate(1L, analysis(dims(150, -5, 100, 0, 100, 100)));
        assertThat(r.getDimensionScores())
                .containsEntry("basic", 100)
                .containsEntry("education", 0);
    }

    @Test
    void fillsMissingDimensionsWith40() {
        Map<String, Integer> partial = new LinkedHashMap<>();
        partial.put("skill", 80);
        partial.put("project", 60);
        AiResumeAnalysis ai = analysis(partial);
        ResumeAnalysis r = calculator.aggregate(1L, ai);
        assertThat(r.getDimensionScores()).containsOnlyKeys(
                "basic", "education", "skill", "project", "experience", "expression");
        assertThat(r.getDimensionScores().get("basic")).isEqualTo(40);
        // 80×.25 + 60×.25 + (40×.1 + 40×.1 + 40×.1 + 40×.2) = 55
        assertThat(r.getOverallScore()).isEqualTo(55);
    }

    @Test
    void toleratesNullAnalysis() {
        ResumeAnalysis r = calculator.aggregate(7L, null);
        assertThat(r.getProvider()).isEqualTo("mock");
        assertThat(r.getOverallScore()).isEqualTo(40);
        assertThat(r.getSuggestions()).hasSize(3); // 全 40 分 → 补齐到 3 条 MEDIUM
        assertThat(r.getStrengths()).isNotEmpty();
        assertThat(r.getSummaryText()).isNotBlank();
    }

    // ==================== 建议约束 ====================

    @Test
    void recomputesRiskLevelAndSortsHighMediumLow() {
        // 输入乱序且 riskLevel 与分数不符：project=30（标 LOW 应修正为 HIGH）、basic=80（标 HIGH 应修正为 LOW）
        AiResumeAnalysis ai = analysis(dims(80, 50, 50, 30, 50, 50),
                sug("LOW", "project", "项目待提升"),
                sug("MEDIUM", "education", "教育待补"),
                sug("HIGH", "basic", "基础很棒"));
        ResumeAnalysis r = calculator.aggregate(1L, ai);
        List<String> levels = r.getSuggestions().stream().map(SuggestionDTO::getRiskLevel).toList();
        // project(30)=HIGH 在最前，education(50)=MEDIUM 次之，basic(80)=LOW 最后
        assertThat(levels).containsExactly("HIGH", "MEDIUM", "LOW");
        assertThat(r.getSuggestions().get(0).getDimension()).isEqualTo("project");
        assertThat(r.getSuggestions().get(2).getDimension()).isEqualTo("basic");
    }

    @Test
    void capsSuggestionsAtEightKeepingHighRisk() {
        List<SuggestionDTO> many = new ArrayList<>();
        // 2 条 HIGH（skill=20、project=25）
        many.add(sug("HIGH", "skill", "技能弱"));
        many.add(sug("HIGH", "project", "项目弱"));
        // 7 条 MEDIUM（basic=60 维度重复不同标题）+ LOW
        IntStream.rangeClosed(1, 7).forEach(i -> many.add(sug("MEDIUM", "education", "教育建议" + i)));
        many.add(sug("LOW", "basic", "基础好"));
        ResumeAnalysis r = calculator.aggregate(1L,
                analysis(dims(80, 60, 20, 25, 60, 60), many.toArray(SuggestionDTO[]::new)));
        assertThat(r.getSuggestions()).hasSize(8);
        assertThat(r.getSuggestions().subList(0, 2))
                .allSatisfy(s -> assertThat(s.getRiskLevel()).isEqualTo("HIGH"));
    }

    @Test
    void padsSuggestionsToAtLeastThree() {
        // 全维 ≥70 的好简历：无短板建议 → 补齐 LOW 亮点项到 3 条
        AiResumeAnalysis ai = analysis(dims(90, 80, 85, 75, 70, 88),
                sug("LOW", "skill", "技能很棒"));
        ResumeAnalysis r = calculator.aggregate(1L, ai);
        assertThat(r.getSuggestions()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(r.getSuggestions()).allSatisfy(s ->
                assertThat(s.getRiskLevel()).isEqualTo("LOW"));
    }

    // ==================== 亮点 / 总评 / provider ====================

    @Test
    void generatesStrengthsFallbackFromScores() {
        AiResumeAnalysis ai = analysis(dims(90, 40, 85, 30, 40, 40));
        ai.setStrengths(null);
        ResumeAnalysis r = calculator.aggregate(1L, ai);
        assertThat(r.getStrengths()).isNotEmpty();
        assertThat(String.join(";", r.getStrengths())).contains("基础信息完整度");
        assertThat(r.getStrengths()).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void truncatesSummaryAndKeepsProviderLabel() {
        AiResumeAnalysis ai = analysis(dims(50, 50, 50, 50, 50, 50));
        ai.setSummary("长".repeat(600));
        ai.setProvider("qwen");
        assertThat(calculator.aggregate(1L, ai).getSummaryText()).hasSize(500);
        assertThat(calculator.aggregate(1L, ai).getProvider()).isEqualTo("qwen");

        ai.setProvider(null);
        assertThat(calculator.aggregate(1L, ai).getProvider()).isEqualTo("mock");
    }

    private static SuggestionDTO sug(String riskLevel, String dimension, String title) {
        return SuggestionDTO.builder().riskLevel(riskLevel).dimension(dimension)
                .title(title).issue("issue").suggestion("suggestion").evidence("evidence").build();
    }
}
