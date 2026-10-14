package com.example.interview.assessment.engine;

import com.example.interview.position.dto.CompetencyModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 评分公式单测（计划 §8.1 ScoreCalculatorTest）：手算样例总分误差 ≤0.01；
 * 缺失子维度权重归一化；岗位自定义权重生效；分数 clamp 0~100；
 * 六维按题目难度加权聚合；应变分映射。
 */
class ScoreCalculatorTest {

    private final ScoreCalculator calculator = new ScoreCalculator();

    // ==================== 内容/表达/总分（手算样例） ====================

    @Test
    void handCalculatedTotalScoreWithDefaultWeights() {
        // 内容 = 82×.30+75×.25+80×.20+70×.15+85×.10 = 78.35
        // 表达 = 无表达子维度 → communication 78；应变 72、匹配 85 为给定值
        // 总分 = 78.35×.50+78×.30+72×.10+85×.10 = 78.275 → 78.28
        Map<String, Integer> scores = Map.of(
                "technical", 82, "depth", 75, "logic", 80, "evidence", 70,
                "match", 85, "communication", 78);

        double content = calculator.contentScore(scores, null);
        assertThat(content).isCloseTo(78.35, within(0.001));

        double expression = calculator.expressionScore(scores, null);
        assertThat(expression).isCloseTo(78.0, within(0.001));

        double total = calculator.totalScore(content, expression, 72, 85, null);
        assertThat(total).isCloseTo(78.28, within(0.01));
        assertThat(total).isLessThanOrEqualTo(100);
    }

    @Test
    void missingSubDimensionRenormalizesWeights() {
        // 无 evidence：内容 = (82×.30+75×.25+80×.20+85×.10)/0.85 = 67.85/0.85 ≈ 79.82
        Map<String, Integer> scores = Map.of(
                "technical", 82, "depth", 75, "logic", 80, "match", 85);
        assertThat(calculator.contentScore(scores, null)).isCloseTo(79.82, within(0.01));
    }

    @Test
    void expressionSubDimensionsOverrideCommunicationFallback() {
        // 表达 = 80×.30+70×.20+90×.20+60×.15+100×.15 = 80.0（子维度优先于 communication 退化）
        Map<String, Integer> scores = Map.of(
                "clarity", 80, "fluency", 70, "pace", 90,
                "confidence", 60, "completeness", 100, "communication", 30);
        assertThat(calculator.expressionScore(scores, null)).isCloseTo(80.0, within(0.001));
    }

    @Test
    void positionWeightsChangeResults() {
        // 岗位把内容权重改为“只看技术正确性”，总分只看内容
        CompetencyModel.DimensionWeights contentW = new CompetencyModel.DimensionWeights();
        contentW.setTechnical(1.0);
        assertThat(calculator.contentScore(Map.of("technical", 90, "depth", 10), contentW))
                .isCloseTo(90.0, within(0.001));

        CompetencyModel.TotalWeights totalW = new CompetencyModel.TotalWeights();
        totalW.setContent(1.0);
        totalW.setExpression(0.0);
        totalW.setAdaptability(0.0);
        totalW.setMatch(0.0);
        assertThat(calculator.totalScore(66.0, 10.0, 99.0, 12.0, totalW))
                .isCloseTo(66.0, within(0.001));
    }

    @Test
    void scoresClampedToZeroAndHundred() {
        CompetencyModel.DimensionWeights onlyTechnical = new CompetencyModel.DimensionWeights();
        onlyTechnical.setTechnical(1.0);
        assertThat(calculator.contentScore(Map.of("technical", 150), onlyTechnical))
                .isCloseTo(100.0, within(0.001));
        assertThat(calculator.contentScore(Map.of("technical", -20), onlyTechnical))
                .isCloseTo(0.0, within(0.001));
        // 总分各分量先 clamp 再加权，恒在 0~100
        assertThat(calculator.totalScore(200, 200, 200, 200, null)).isEqualTo(100.0);
        assertThat(calculator.totalScore(-5, -5, -5, -5, null)).isEqualTo(0.0);
    }

    // ==================== 六维聚合（难度加权） ====================

    @Test
    void aggregateDimensionsWeightedByQuestionDifficulty() {
        List<ScoreCalculator.TurnScores> turns = List.of(
                new ScoreCalculator.TurnScores(Map.of("technical", 60, "communication", 80), 0.6),
                new ScoreCalculator.TurnScores(Map.of("technical", 90), 1.0));
        Map<String, Double> dims = calculator.aggregateDimensions(turns);
        // technical = (60×0.6+90×1.0)/1.6 = 78.75；communication 只有一轮 = 80
        assertThat(dims.get("technical")).isCloseTo(78.75, within(0.001));
        assertThat(dims.get("communication")).isCloseTo(80.0, within(0.001));
        assertThat(dims).doesNotContainKey("match");
        assertThat(dims).doesNotContainKey("voice");
    }

    @Test
    void difficultyWeightsFollowPlan() {
        assertThat(ScoreCalculator.difficultyWeight(1)).isEqualTo(0.6);
        assertThat(ScoreCalculator.difficultyWeight(2)).isEqualTo(0.8);
        assertThat(ScoreCalculator.difficultyWeight(3)).isEqualTo(1.0);
        assertThat(ScoreCalculator.difficultyWeight(4)).isEqualTo(1.2);
        assertThat(ScoreCalculator.difficultyWeight(5)).isEqualTo(1.4);
        // 越界值 clamp 到 1~5 档
        assertThat(ScoreCalculator.difficultyWeight(0)).isEqualTo(0.6);
        assertThat(ScoreCalculator.difficultyWeight(9)).isEqualTo(1.4);
    }

    @Test
    void emptyTurnsAggregateToEmptyDimensions() {
        assertThat(calculator.aggregateDimensions(List.of())).isEmpty();
        assertThat(calculator.aggregateDimensions(null)).isEmpty();
    }

    // ==================== 应变分（追问回升幅度） ====================

    @Test
    void adaptabilityFromFollowupImprovement() {
        // 回升 +20、+10 → 60+15 = 75
        assertThat(calculator.adaptabilityScore(List.of(List.of(50.0, 70.0), List.of(80.0, 90.0))))
                .isCloseTo(75.0, within(0.001));
        // 回落 → 低于基线
        assertThat(calculator.adaptabilityScore(List.of(List.of(80.0, 70.0))))
                .isCloseTo(50.0, within(0.001));
        // 巨幅回升 clamp 100
        assertThat(calculator.adaptabilityScore(List.of(List.of(30.0, 100.0))))
                .isEqualTo(100.0);
    }

    @Test
    void adaptabilityNeutralWhenNoFollowupRounds() {
        // 无多答轮次（无法衡量应变）与空输入均取 65 中性值
        assertThat(calculator.adaptabilityScore(List.of())).isEqualTo(65.0);
        assertThat(calculator.adaptabilityScore(null)).isEqualTo(65.0);
        assertThat(calculator.adaptabilityScore(List.of(List.of(50.0)))).isEqualTo(65.0);
    }

    // ==================== 加权平均 ====================

    @Test
    void weightedAverageOfTurnLevelScores() {
        assertThat(calculator.weightedAverage(List.of(new double[]{70, 0.6}, new double[]{90, 1.4})))
                .isCloseTo(84.0, within(0.001));
        assertThat(calculator.weightedAverage(List.of())).isEqualTo(60.0);
    }
}
