package com.example.interview.resume.service;

import com.example.interview.ai.model.AiResumeAnalysis;
import com.example.interview.resume.dto.SuggestionDTO;
import com.example.interview.resume.entity.ResumeAnalysis;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 六维聚合与建议约束（简历方案 §3.3/§3.4，R2-5）。无论结果来自 Mock 规则还是真实模型，
 * 落库前统一过本类：分数 clamp 0~100、缺维补 40、总分加权四舍五入（权重代码内固定常量，
 * 与基线报告权重同样处理方式）；建议 riskLevel 按维度分档重算（&lt;40 HIGH / 40~69 MEDIUM /
 * ≥70 LOW）、HIGH→MEDIUM→LOW 排序、超 8 截断（保高风险）、不足 3 按分数补齐。
 */
@Component
public class ResumeScoreCalculator {

    /** 六维权重（方案 §3.3，顺序即建议排序的维度次序） */
    private static final Map<String, Double> WEIGHTS = new LinkedHashMap<>();

    static {
        WEIGHTS.put("basic", 0.10);
        WEIGHTS.put("education", 0.10);
        WEIGHTS.put("skill", 0.25);
        WEIGHTS.put("project", 0.25);
        WEIGHTS.put("experience", 0.10);
        WEIGHTS.put("expression", 0.20);
    }

    /** 真实 AI 缺维时的补齐分（40 = MEDIUM 档下限，提示用户该维信息不足） */
    static final int MISSING_DIM_SCORE = 40;
    private static final int MIN_SUGGESTIONS = 3;
    private static final int MAX_SUGGESTIONS = 8;
    private static final int MAX_STRENGTHS = 5;

    private static final Map<String, String> DIM_NAMES = Map.of(
            "basic", "基础信息完整度", "education", "教育背景", "skill", "技能与岗位匹配",
            "project", "项目经历质量", "experience", "实习与实践经历", "expression", "表达与排版规范");
    private static final Map<String, Integer> RISK_ORDER = Map.of("HIGH", 0, "MEDIUM", 1, "LOW", 2);

    /**
     * 聚合为可落库实体（不含 createdAt，由数据库默认值生成）。
     * provider 取结果自报值（mock/qwen），空兜底 mock。
     */
    public ResumeAnalysis aggregate(Long resumeId, AiResumeAnalysis ai) {
        Map<String, Integer> dims = normalizeScores(ai != null ? ai.getDimensionScores() : null);

        ResumeAnalysis result = new ResumeAnalysis();
        result.setResumeId(resumeId);
        result.setProvider(ai != null && ai.getProvider() != null && !ai.getProvider().isBlank()
                ? ai.getProvider() : "mock");
        result.setOverallScore(overallOf(dims));
        result.setDimensionScores(dims);
        result.setStrengths(normalizeStrengths(ai != null ? ai.getStrengths() : null, dims));
        result.setSuggestions(normalizeSuggestions(ai != null ? ai.getSuggestions() : null, dims));
        result.setSummaryText(normalizeSummary(ai != null ? ai.getSummary() : null, dims));
        return result;
    }

    // ==================== 分数 ====================

    /** clamp 0~100、四舍五入、缺维补 40（真实 AI 字段缺失的确定性修复）。 */
    private Map<String, Integer> normalizeScores(Map<String, Integer> raw) {
        Map<String, Integer> dims = new LinkedHashMap<>();
        for (String dim : WEIGHTS.keySet()) {
            Integer v = raw != null ? raw.get(dim) : null;
            dims.put(dim, v == null ? MISSING_DIM_SCORE : clamp(v));
        }
        return dims;
    }

    /** overall = Σ(维度分 × 权重)，四舍五入（权重和为 1）。 */
    private int overallOf(Map<String, Integer> dims) {
        double total = 0;
        for (Map.Entry<String, Double> w : WEIGHTS.entrySet()) {
            total += dims.get(w.getKey()) * w.getValue();
        }
        return (int) Math.round(total);
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    // ==================== 建议（§3.4 数量与排序约束） ====================

    /**
     * 约束链：过滤非法维度 → riskLevel 按维度分重算 → 按（风险级, 维度次序）排序 →
     * 去重（维度+标题）→ 截断 8 条（排序后高风险在前）→ 不足 3 按分数补齐。
     */
    List<SuggestionDTO> normalizeSuggestions(List<SuggestionDTO> raw, Map<String, Integer> dims) {
        List<SuggestionDTO> list = new ArrayList<>();
        if (raw != null) {
            for (SuggestionDTO s : raw) {
                if (s == null || s.getDimension() == null || !WEIGHTS.containsKey(s.getDimension())) {
                    continue;
                }
                SuggestionDTO fixed = SuggestionDTO.builder()
                        .riskLevel(riskLevelOf(dims.get(s.getDimension())))
                        .dimension(s.getDimension())
                        .title(orDefault(s.getTitle(), "「" + DIM_NAMES.get(s.getDimension()) + "」待改进"))
                        .issue(orDefault(s.getIssue(), "该维度得分 " + dims.get(s.getDimension())))
                        .suggestion(orDefault(s.getSuggestion(), "参考岗位要求完善该维度内容"))
                        .evidence(s.getEvidence())
                        .build();
                list.add(fixed);
            }
        }
        list.sort(Comparator
                .comparingInt((SuggestionDTO s) -> RISK_ORDER.getOrDefault(s.getRiskLevel(), 3))
                .thenComparingInt(s -> dimensionOrder(s.getDimension())));
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<SuggestionDTO> result = new ArrayList<>();
        for (SuggestionDTO s : list) {
            String key = s.getDimension() + "|" + s.getTitle();
            if (seen.add(key) && result.size() < MAX_SUGGESTIONS) {
                result.add(s);
            }
        }
        padSuggestions(result, dims);
        return result;
    }

    /** 补齐到 ≥3：优先为未覆盖且 ≥70 的最高分维度补 LOW 亮点项，再为最低分未覆盖维度补 MEDIUM。 */
    private void padSuggestions(List<SuggestionDTO> result, Map<String, Integer> dims) {
        if (result.size() >= MIN_SUGGESTIONS) {
            return;
        }
        List<Map.Entry<String, Integer>> byScoreDesc = dims.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).toList();
        for (Map.Entry<String, Integer> e : byScoreDesc) {
            if (result.size() >= MIN_SUGGESTIONS) {
                return;
            }
            if (e.getValue() >= 70 && !covered(result, e.getKey())) {
                result.add(SuggestionDTO.builder()
                        .riskLevel("LOW")
                        .dimension(e.getKey())
                        .title("「" + DIM_NAMES.get(e.getKey()) + "」为优势维度")
                        .issue("该维度得分 " + e.getValue() + "，构成简历亮点")
                        .suggestion("保持该维度的现有写法；面试前确保相关表述经得起追问")
                        .build());
            }
        }
        List<Map.Entry<String, Integer>> byScoreAsc = dims.entrySet().stream()
                .sorted(Map.Entry.comparingByValue()).toList();
        for (Map.Entry<String, Integer> e : byScoreAsc) {
            if (result.size() >= MIN_SUGGESTIONS) {
                return;
            }
            if (!covered(result, e.getKey())) {
                result.add(SuggestionDTO.builder()
                        .riskLevel(riskLevelOf(e.getValue()))
                        .dimension(e.getKey())
                        .title("「" + DIM_NAMES.get(e.getKey()) + "」待改进")
                        .issue("该维度得分 " + e.getValue() + "，低于 70 分合格线")
                        .suggestion("参考岗位要求完善该维度内容后重新评估")
                        .build());
            }
        }
    }

    private boolean covered(List<SuggestionDTO> list, String dim) {
        return list.stream().anyMatch(s -> s.getDimension().equals(dim));
    }

    /** 分档（§3.4）：<40 HIGH；40~69 MEDIUM；≥70 LOW。 */
    static String riskLevelOf(int score) {
        if (score < 40) {
            return "HIGH";
        }
        return score < 70 ? "MEDIUM" : "LOW";
    }

    private int dimensionOrder(String dim) {
        int i = 0;
        for (String d : WEIGHTS.keySet()) {
            if (d.equals(dim)) {
                return i;
            }
            i++;
        }
        return 99;
    }

    // ==================== 亮点与总评 ====================

    /** 亮点 ≤5 条；空或缺失时按维度分（≥70 前 3）生成兜底文案。 */
    List<String> normalizeStrengths(List<String> raw, Map<String, Integer> dims) {
        List<String> strengths = raw == null ? List.of() : raw.stream()
                .filter(s -> s != null && !s.isBlank()).limit(MAX_STRENGTHS).toList();
        if (!strengths.isEmpty()) {
            return strengths;
        }
        List<String> fallback = new ArrayList<>();
        dims.entrySet().stream()
                .filter(e -> e.getValue() >= 70)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .forEach(e -> fallback.add("「" + DIM_NAMES.get(e.getKey()) + "」表现较好（" + e.getValue() + " 分）"));
        if (fallback.isEmpty()) {
            fallback.add("简历信息完整，已具备可评估的基础材料");
        }
        return fallback;
    }

    /** 总评 ≤500 字；空时按分数分布生成兜底。 */
    String normalizeSummary(String raw, Map<String, Integer> dims) {
        if (raw != null && !raw.isBlank()) {
            return raw.length() <= 500 ? raw : raw.substring(0, 500);
        }
        int overall = overallOf(dims);
        Map.Entry<String, Integer> best = dims.entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElseThrow();
        Map.Entry<String, Integer> worst = dims.entrySet().stream()
                .min(Map.Entry.comparingByValue()).orElseThrow();
        return "简历总分 " + overall + " 分：优势在「" + DIM_NAMES.get(best.getKey()) + "」（" + best.getValue()
                + " 分），短板在「" + DIM_NAMES.get(worst.getKey()) + "」（" + worst.getValue()
                + " 分）。建议优先处理高风险条目后重新上传评估。";
    }

    private String orDefault(String s, String def) {
        return s == null || s.isBlank() ? def : s;
    }
}
