package com.example.interview.ai.model;

import com.example.interview.resume.dto.SuggestionDTO;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AiProvider.analyzeResume 输出（简历方案 §4.1，R2-2）。
 * provider 由产出方自报（mock / qwen）：真实 Provider 内部降级 Mock 时结果携带 mock，
 * 落库 resume_analysis.provider 即"实际产出结果的 Provider"（协议 R-04 口径）。
 * digest 不由模型产出——由规则解析器 toDigest 确定性生成（Mock/真实语义一致，F2 契约稳定）。
 */
@Data
public class AiResumeAnalysis {

    /** mock / qwen（实际产出方自报；空由 ScoreCalculator 兜底 mock） */
    private String provider;
    /** 六维 {basic,education,skill,project,experience,expression}，每维 0~100 */
    private Map<String, Integer> dimensionScores;
    /** 亮点（≤5 条） */
    private List<String> strengths;
    /** 3~8 条分级建议（数量/排序/风险级由 ScoreCalculator 统一约束） */
    private List<SuggestionDTO> suggestions;
    /** 总评（≤500 字） */
    private String summary;
}
