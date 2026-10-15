package com.example.interview.growth.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 短板识别引擎（计划 §6.7 四规则，方案 §8.2）——纯逻辑，不依赖 Spring/数据库：
 * <pre>
 * ① 某技能最近两次掌握度记录均 &lt; 60（不足两次不触发，防单场波动误报）
 * ② 最新掌握度 &lt; 岗位 requiredLevel
 * ③ 最近一场面试中该技能 ≥ 2 道题 missingPoints 命中
 * ④ communication / voice 维度连续两次 &lt; 60
 * </pre>
 * 数据快照由 RecommendationService 组装传入。
 */
public class WeaknessDetector {

    public static final double LOW_SCORE = 60.0;
    public static final int MISSING_POINT_QUESTIONS = 2;

    /**
     * 单技能快照：scoreHistory 按时间升序（最新在末尾，空=未考察）；
     * missingPointQuestions = 最近一场面试该技能 missingPoints 命中的题目数。
     */
    public record SkillSnapshot(List<Double> scoreHistory, Integer requiredLevel, int missingPointQuestions) {
    }

    /** 维度快照：sessionScores 按场次时间升序，每场一个点（仅统计出现过该维度分的场次）。 */
    public record DimensionSnapshot(String dimension, List<Double> sessionScores) {
    }

    /** 技能规则 ①~③：返回命中的短板理由（无命中返回空列表）。 */
    public List<String> detectSkill(SkillSnapshot snapshot) {
        List<String> reasons = new ArrayList<>();
        List<Double> history = snapshot == null || snapshot.scoreHistory() == null
                ? List.of() : snapshot.scoreHistory();
        // ① 最近两次均 <60（不足两次不触发）
        if (history.size() >= 2
                && history.get(history.size() - 1) < LOW_SCORE
                && history.get(history.size() - 2) < LOW_SCORE) {
            reasons.add(String.format("最近两次掌握度 %.1f/%.1f 均低于 %.0f，持续薄弱",
                    history.get(history.size() - 2), history.get(history.size() - 1), LOW_SCORE));
        }
        // ② 最新掌握度低于岗位要求
        if (!history.isEmpty() && snapshot.requiredLevel() != null
                && history.get(history.size() - 1) < snapshot.requiredLevel()) {
            reasons.add(String.format("最新掌握度 %.1f 低于岗位要求 %d",
                    history.get(history.size() - 1), snapshot.requiredLevel()));
        }
        // ③ 最近一场该技能 ≥2 道题存在未覆盖的失分点
        if (snapshot != null && snapshot.missingPointQuestions() >= MISSING_POINT_QUESTIONS) {
            reasons.add(String.format("最近一场面试该技能有 %d 道题存在未覆盖的失分点",
                    snapshot.missingPointQuestions()));
        }
        return reasons;
    }

    /** 规则 ④：communication/voice 维度连续两次 <60（不足两次不触发）。 */
    public Optional<String> detectDimension(DimensionSnapshot snapshot) {
        List<Double> scores = snapshot == null || snapshot.sessionScores() == null
                ? List.of() : snapshot.sessionScores();
        if (scores.size() >= 2
                && scores.get(scores.size() - 1) < LOW_SCORE
                && scores.get(scores.size() - 2) < LOW_SCORE) {
            return Optional.of(String.format("%s 维度连续两场低于 %.0f（%.1f/%.1f），表达短板明显",
                    snapshot.dimension(), LOW_SCORE,
                    scores.get(scores.size() - 2), scores.get(scores.size() - 1)));
        }
        return Optional.empty();
    }
}
