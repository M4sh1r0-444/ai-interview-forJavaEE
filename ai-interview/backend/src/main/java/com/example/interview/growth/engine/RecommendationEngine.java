package com.example.interview.growth.engine;

import com.example.interview.growth.entity.LearningResource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 推荐引擎（计划 §6.7，方案 §8.3）——纯逻辑，不依赖 Spring/数据库：
 * <pre>
 * 优先级 = (100 - 掌握度) × 技能权重   （未考察技能按 0 参与，决策 D23）
 * 输出   = 优先级 Top3 × 每技能匹配 learning_resource + 错题重练 + 下一次面试配置建议
 * </pre>
 * 全部候选技能无一命中短板规则时，输出通用提升计划（不点名短板）。
 */
public class RecommendationEngine {

    /** 短板技能输出上限（计划 §6.7：Top3）。 */
    public static final int TOP_SKILLS = 3;

    /** 错题重练条目：最近一场面试 missingPoints 命中的题目（计划 §6.7）。 */
    public record RetryQuestion(Long questionId, String questionContent, List<String> missingPoints) {
    }

    /** 候选技能输入：能力模型项 + 掌握度历史（升序）+ 规则引擎理由 + 匹配资源与错题。 */
    public record SkillCandidate(String skillCode, String skillName, Double weight, Integer requiredLevel,
                                 List<Double> scoreHistory, List<String> weaknessReasons,
                                 List<LearningResource> resources, List<RetryQuestion> retryQuestions) {
    }

    /** 排序后的推荐项：mastery 为最新掌握度（未考察为 null）。 */
    public record RankedSkill(SkillCandidate candidate, Double mastery, double priority, boolean weak) {
    }

    /** 推荐计划：Top3 + 是否存在任一短板技能（false → 通用计划文案）。 */
    public record Plan(List<RankedSkill> top, boolean anyWeakness) {
    }

    /** 排序取 Top3；优先级降序，同分保持能力模型序（稳定排序）。 */
    public Plan plan(List<SkillCandidate> candidates) {
        List<RankedSkill> ranked = new ArrayList<>();
        boolean anyWeakness = false;
        for (SkillCandidate c : candidates == null ? List.<SkillCandidate>of() : candidates) {
            Double mastery = latest(c.scoreHistory());
            double score = mastery != null ? mastery : 0.0;
            double weight = c.weight() != null ? c.weight() : 0.0;
            boolean weak = c.weaknessReasons() != null && !c.weaknessReasons().isEmpty();
            anyWeakness |= weak;
            ranked.add(new RankedSkill(c, mastery, round2((100 - score) * weight), weak));
        }
        ranked.sort(Comparator.comparingDouble(RankedSkill::priority).reversed());
        return new Plan(ranked.subList(0, Math.min(TOP_SKILLS, ranked.size())), anyWeakness);
    }

    /** Top3 平均掌握度（未考察按 0），供难度建议。 */
    public double averageMastery(List<RankedSkill> top) {
        return top.stream().mapToDouble(r -> r.mastery() != null ? r.mastery() : 0.0)
                .average().orElse(0);
    }

    /** 下一次面试配置建议：有短板点名重点技能；无短板输出通用提升计划（计划 §6.7）。 */
    public String nextInterviewSuggested(String positionName, Plan plan) {
        if (plan.top().isEmpty()) {
            return "暂无可推荐技能，请先完成一场面试";
        }
        double avg = averageMastery(plan.top());
        if (!plan.anyWeakness()) {
            int next = Math.min(5, (avg < 50 ? 2 : avg < 75 ? 3 : 4) + 1);
            return String.format("综合表现稳定、无明显短板：建议下一场 %s 挑战难度 %d 并复盘过往错题，保持练习节奏",
                    positionName, next);
        }
        int difficulty = avg < 50 ? 2 : avg < 75 ? 3 : 4;
        String focus = plan.top().stream()
                .limit(2)
                .map(r -> r.candidate().skillName())
                .reduce((a, b) -> a + "、" + b)
                .orElse("");
        return String.format("建议下一场：%s · 难度 %d · 重点覆盖 %s", positionName, difficulty, focus);
    }

    private Double latest(List<Double> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        return history.get(history.size() - 1);
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
