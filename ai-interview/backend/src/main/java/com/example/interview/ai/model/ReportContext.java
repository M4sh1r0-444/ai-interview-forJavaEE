package com.example.interview.ai.model;

import com.example.interview.resume.dto.ResumeDigest;
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

    /** 所属面试会话（真实 Provider 会话级熔断与日志追踪用） */
    private Long sessionId;
    /** 归属用户（V9 用户级 AI 配置路由用；null 走平台默认） */
    private Long userId;
    private String positionName;
    private Integer difficulty;
    /** 完成的主问题轮次数 */
    private Integer roundCount;
    /** 计算所得总分（文案引用） */
    private Double totalScore;
    /** 逐题评分结果（每轮取最终快照） */
    private List<AiTurnEvaluation> turnEvaluations;
    /** 会话六维聚合分 */
    private Map<String, Double> dimensionScores;
    /** 近 10 条历史 */
    private List<NextQuestionContext.HistoryMessage> history;
    /** 个性化会话的简历摘要（R3-5）；null = 普通面试，报告不追加"简历 vs 实测"对比条目 */
    private ResumeDigest resumeDigest;
}
