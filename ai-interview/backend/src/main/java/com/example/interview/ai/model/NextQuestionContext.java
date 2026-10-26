package com.example.interview.ai.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * nextQuestion 的输入上下文（计划 §4.3 PromptBuilder 输入）。
 * 追问规则引擎的决策以 requestedAction/instruction/difficultyDelta/reasonCode
 * 中性字段注入，ai 包不依赖 interview 包。
 */
@Data
@Builder
public class NextQuestionContext {

    /** 所属面试会话（真实 Provider 会话级熔断与日志追踪用） */
    private Long sessionId;
    /** 归属用户（V9 用户级 AI 配置路由用；null 走平台默认） */
    private Long userId;
    private String positionName;
    private List<String> positionSkills;

    private int difficulty;
    private int roundNo;
    private int plannedRounds;

    // 当前主问题快照
    private Long questionId;
    private String questionContent;
    private Integer questionDifficulty;
    private List<String> keyPoints;
    private List<String> deductionPoints;
    private List<String> followupTemplates;
    private List<String> targetSkills;

    // 近 10 条历史（speaker + content，按时间升序）
    private List<HistoryMessage> history;

    // 知识片段（M4 RAG 检索结果，M2 为空）
    private List<String> knowledgeChunks;

    private String lastUserAnswer;

    // 追问规则引擎决策（中性化字段）
    /** FOLLOW_UP / NEXT_QUESTION */
    private String requestedAction;
    /** 注入 Prompt 的规则指令（如"针对缺失要点追问"） */
    private String instruction;
    private int difficultyDelta;
    /** 决策命中的规则编码（MISSING_POINT/VAGUE/DEDUCTION/...，Mock 选模板用） */
    private String reasonCode;
    private int followupCount;

    @Data
    @Builder
    public static class HistoryMessage {
        private String speaker;
        private String content;
    }
}
