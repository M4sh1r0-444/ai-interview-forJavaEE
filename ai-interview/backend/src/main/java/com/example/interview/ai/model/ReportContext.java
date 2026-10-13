package com.example.interview.ai.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * generateReport 的输入上下文（计划 §6.6 报告生成）。M3 接入使用。
 */
@Data
@Builder
public class ReportContext {

    private String positionName;
    private Integer difficulty;
    /** 逐题评分结果 */
    private List<AiTurnEvaluation> turnEvaluations;
    /** 会话六维聚合分 */
    private Map<String, Double> dimensionScores;
    /** 近 10 条历史 */
    private List<NextQuestionContext.HistoryMessage> history;
}
