package com.example.interview.growth.engine;

import com.example.interview.growth.entity.LearningResource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 计划 §8.1 RecommendationEngineTest：优先级排序正确；
 * 短板技能资源必现；无短板时输出通用计划。
 */
class RecommendationEngineTest {

    private final RecommendationEngine engine = new RecommendationEngine();

    private LearningResource resource(long id, String skill, String type) {
        LearningResource r = new LearningResource();
        r.setId(id);
        r.setPositionId(1L);
        r.setSkillCode(skill);
        r.setTitle("资源" + id);
        r.setResourceType(type);
        r.setDifficulty(3);
        r.setEnabled(1);
        return r;
    }

    @Test
    void ranksByPriorityDescending() {
        // spring: (100-40)×0.15=9.0；mysql 未考察按 0: (100-0)×0.10=10.0；java_basics: (100-80)×0.15=3.0
        List<RecommendationEngine.RankedSkill> top = engine.plan(List.of(
                candidate("java_basics", "Java基础", 0.15, 70, List.of(80.0), List.of()),
                candidate("spring", "Spring生态", 0.15, 70, List.of(40.0), List.of("最新掌握度 40.0 低于岗位要求 70")),
                candidate("mysql", "MySQL", 0.10, 70, List.of(), List.of()))).top();

        assertThat(top).extracting(RecommendationEngine.RankedSkill::candidate)
                .extracting(c -> c.skillCode())
                .containsExactly("mysql", "spring", "java_basics");
        assertThat(top.get(0).priority()).isEqualTo(10.0);
        assertThat(top.get(1).priority()).isEqualTo(9.0);
        assertThat(top.get(2).priority()).isEqualTo(3.0);
        assertThat(top.get(1).mastery()).isEqualTo(40.0);
        assertThat(top.get(0).mastery()).isNull();
    }

    @Test
    void weakSkillCarriesReasonsResourcesAndRetryQuestions() {
        RecommendationEngine.SkillCandidate weak = new RecommendationEngine.SkillCandidate(
                "spring", "Spring生态", 0.15, 70, List.of(40.0),
                List.of("最新掌握度 40.0 低于岗位要求 70"),
                List.of(resource(1, "spring", "ARTICLE"), resource(2, "spring", "PRACTICE")),
                List.of(new RecommendationEngine.RetryQuestion(9L, "说说事务传播？", List.of("未提 REQUIRES_NEW"))));
        RecommendationEngine.Plan plan = engine.plan(List.of(
                candidate("mysql", "MySQL", 0.10, 70, List.of(90.0), List.of()), weak));

        assertThat(plan.anyWeakness()).isTrue();
        RecommendationEngine.RankedSkill top = plan.top().get(0); // 优先级 9.0 > 1.0
        assertThat(top.weak()).isTrue();
        // 短板技能资源必现（含错题重练条目）
        assertThat(top.candidate().resources()).hasSize(2);
        assertThat(top.candidate().retryQuestions()).hasSize(1);
        assertThat(top.candidate().retryQuestions().get(0).missingPoints())
                .containsExactly("未提 REQUIRES_NEW");
        assertThat(engine.nextInterviewSuggested("Java后端", plan))
                .contains("Java后端").contains("重点覆盖");
    }

    @Test
    void healthySkillsProduceGenericPlan() {
        RecommendationEngine.Plan plan = engine.plan(List.of(
                candidate("java_basics", "Java基础", 0.15, 70, List.of(82.0), List.of()),
                candidate("spring", "Spring生态", 0.15, 70, List.of(88.0), List.of())));

        assertThat(plan.anyWeakness()).isFalse();
        assertThat(plan.top()).allSatisfy(r -> assertThat(r.weak()).isFalse());
        // 无短板时输出通用计划（不点名重点覆盖，建议挑战更高难度）
        String suggested = engine.nextInterviewSuggested("Java后端", plan);
        assertThat(suggested).contains("稳定").contains("Java后端").doesNotContain("重点覆盖");
    }

    private RecommendationEngine.SkillCandidate candidate(String code, String name, double weight,
                                                          int required, List<Double> history,
                                                          List<String> reasons) {
        return new RecommendationEngine.SkillCandidate(code, name, weight, required,
                history, reasons, List.of(), List.of());
    }
}
