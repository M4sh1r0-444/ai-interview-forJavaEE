package com.example.interview.assessment.engine;

import com.example.interview.position.dto.CompetencyModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评分计算引擎（计划 §6.6，方案 §7.2 权重公式）。纯逻辑组件，单测覆盖。
 *
 * <pre>
 * 内容 = technical×.30 + depth×.25 + logic×.20 + evidence×.15 + match×.10   （缺失维度权重归一化）
 * 表达 = clarity×.30 + fluency×.20 + pace×.20 + confidence×.15 + completeness×.15
 *        （TEXT 场无表达子维度时退化为 communication 分）
 * 总分 = 内容×.50 + 表达×.30 + 应变×.10 + 岗位匹配×.10
 * 应变 = 60 + 追问轮回答内容分回升幅度均值（无追问轮取 65 中性值）
 * </pre>
 * 权重来自岗位 competency_model_json.scoreWeights（管理端可调），缺省用上述默认值。
 */
@Component
public class ScoreCalculator {

    /** 六维编码（voice 仅 AUDIO 场由 M4 语音指标产出） */
    public static final List<String> DIMENSIONS = List.of(
            "technical", "depth", "logic", "match", "communication", "voice");

    private static final List<String> CONTENT_DIMS = List.of(
            "technical", "depth", "logic", "evidence", "match");
    private static final List<String> EXPRESSION_DIMS = List.of(
            "clarity", "fluency", "pace", "confidence", "completeness");

    /** 维度缺失时的中性分（不出现在分子分母，仅兜底） */
    private static final double NEUTRAL = 60.0;
    /** 无追问轮（无法衡量应变）时的中性分 */
    private static final double ADAPTABILITY_NEUTRAL = 65.0;

    /** 题目难度权重（计划 §6.7：1~5 → 0.6/0.8/1.0/1.2/1.4）。 */
    public static double difficultyWeight(int difficulty) {
        return switch (Math.max(1, Math.min(5, difficulty))) {
            case 1 -> 0.6;
            case 2 -> 0.8;
            case 4 -> 1.2;
            case 5 -> 1.4;
            default -> 1.0;
        };
    }

    /** 逐题内容分（权重归一化到已出现的子维度，计划 §6.6）。 */
    public double contentScore(Map<String, Integer> rawScores, CompetencyModel.DimensionWeights weights) {
        return weightedScore(rawScores, CONTENT_DIMS, weights);
    }

    /** 逐题表达分；TEXT 场无表达子维度时退化为 communication（方案 §7.2 表达≈沟通表达）。 */
    public double expressionScore(Map<String, Integer> rawScores, CompetencyModel.DimensionWeights weights) {
        double weighted = weightedScore(rawScores, EXPRESSION_DIMS, weights);
        if (!Double.isNaN(weighted)) {
            return weighted;
        }
        Integer communication = rawScores != null ? rawScores.get("communication") : null;
        return communication != null ? clamp(communication) : NEUTRAL;
    }

    /** 总分（方案 §7.2 原式），结果 clamp 0~100 并保留两位小数。 */
    public double totalScore(double content, double expression, double adaptability, double match,
                             CompetencyModel.TotalWeights weights) {
        double wc = weights != null && weights.getContent() != null ? weights.getContent() : 0.50;
        double we = weights != null && weights.getExpression() != null ? weights.getExpression() : 0.30;
        double wa = weights != null && weights.getAdaptability() != null ? weights.getAdaptability() : 0.10;
        double wm = weights != null && weights.getMatch() != null ? weights.getMatch() : 0.10;
        double total = clamp(content) * wc + clamp(expression) * we
                + clamp(adaptability) * wa + clamp(match) * wm;
        return Math.round(clamp(total) * 100) / 100.0;
    }

    /** 应变分：追问轮回答内容分回升幅度均值 + 60 基线（计划 §6.6 Mock 评分规则）。 */
    public double adaptabilityScore(List<List<Double>> roundContentSequences) {
        if (roundContentSequences == null || roundContentSequences.isEmpty()) {
            return ADAPTABILITY_NEUTRAL;
        }
        List<Double> improvements = new ArrayList<>();
        for (List<Double> seq : roundContentSequences) {
            if (seq != null && seq.size() >= 2) {
                improvements.add(seq.get(seq.size() - 1) - seq.get(0));
            }
        }
        if (improvements.isEmpty()) {
            return ADAPTABILITY_NEUTRAL;
        }
        double avg = improvements.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return clamp(60 + avg);
    }

    /**
     * 会话六维聚合：逐题各维分按题目难度权重加权平均（计划 §6.6）。
     * 某题缺该维分时该题不参与该维的分母。输出 key 与出现过的维度一致（TEXT 场无 voice）。
     */
    public Map<String, Double> aggregateDimensions(List<TurnScores> turns) {
        Map<String, Double> numerator = new LinkedHashMap<>();
        Map<String, Double> denominator = new LinkedHashMap<>();
        if (turns != null) {
            for (TurnScores t : turns) {
                if (t == null || t.scores == null) {
                    continue;
                }
                for (Map.Entry<String, Integer> e : t.scores.entrySet()) {
                    if (e.getKey() == null || e.getValue() == null) {
                        continue;
                    }
                    numerator.merge(e.getKey(), clamp(e.getValue()) * t.weight, Double::sum);
                    denominator.merge(e.getKey(), t.weight, Double::sum);
                }
            }
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (String dim : DIMENSIONS) {
            Double den = denominator.get(dim);
            if (den != null && den > 0) {
                result.put(dim, Math.round(numerator.get(dim) / den * 100) / 100.0);
            }
        }
        return result;
    }

    /** 数值序列的加权平均（内容/表达分的会话级聚合用）。 */
    public double weightedAverage(List<double[]> valueAndWeights) {
        if (valueAndWeights == null || valueAndWeights.isEmpty()) {
            return NEUTRAL;
        }
        double num = 0;
        double den = 0;
        for (double[] vw : valueAndWeights) {
            if (vw != null && vw.length == 2 && vw[1] > 0) {
                num += vw[0] * vw[1];
                den += vw[1];
            }
        }
        return den > 0 ? num / den : NEUTRAL;
    }

    public double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }

    /** NaN 表示该组子维度全缺失（表达分退化逻辑用）。 */
    private double weightedScore(Map<String, Integer> rawScores, List<String> dims,
                                 CompetencyModel.DimensionWeights weights) {
        if (rawScores == null) {
            return Double.NaN;
        }
        double num = 0;
        double den = 0;
        for (String dim : dims) {
            Integer value = rawScores.get(dim);
            Double weight = weightOf(weights, dim);
            if (value != null && weight != null && weight > 0) {
                num += clamp(value) * weight;
                den += weight;
            }
        }
        return den > 0 ? num / den : Double.NaN;
    }

    private Double weightOf(CompetencyModel.DimensionWeights weights, String dim) {
        if (weights == null) {
            return switch (dim) {
                case "technical" -> 0.30;
                case "depth" -> 0.25;
                case "logic" -> 0.20;
                case "evidence" -> 0.15;
                case "match" -> 0.10;
                // 表达子维度默认权重（方案 §7.2；TEXT 场不出现，AUDIO 场 M4 产出）
                case "clarity" -> 0.30;
                case "fluency" -> 0.20;
                case "pace" -> 0.20;
                case "confidence" -> 0.15;
                case "completeness" -> 0.15;
                default -> null;
            };
        }
        return switch (dim) {
            case "technical" -> weights.getTechnical();
            case "depth" -> weights.getDepth();
            case "logic" -> weights.getLogic();
            case "evidence" -> weights.getEvidence();
            case "match" -> weights.getMatch();
            case "clarity" -> weights.getClarity();
            case "fluency" -> weights.getFluency();
            case "pace" -> weights.getPace();
            case "confidence" -> weights.getConfidence();
            case "completeness" -> weights.getCompleteness();
            default -> null;
        };
    }

    /** 聚合输入：一轮的原始六维分 + 该轮权重（题目难度权重）。 */
    public record TurnScores(Map<String, Integer> scores, double weight) {
    }
}
