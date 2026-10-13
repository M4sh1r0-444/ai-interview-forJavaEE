package com.example.interview.ai.model;

import lombok.Data;

import java.util.List;

/**
 * AiProvider.nextQuestion 输出（计划 §6.6 / §4.3）。
 * action=FOLLOW_UP 时 content 为追问文本；action=NEXT_QUESTION 时主问题取自组卷序列，
 * content 可为空。真实 Provider 的 JSON 形态：
 * <pre>{"action":"FOLLOW_UP","content":"...","difficulty":4,"targetSkills":["redis_mq"],"reason":"..."}</pre>
 */
@Data
public class AiNextQuestionResult {

    public static final String ACTION_FOLLOW_UP = "FOLLOW_UP";
    public static final String ACTION_NEXT_QUESTION = "NEXT_QUESTION";

    /** FOLLOW_UP / NEXT_QUESTION */
    private String action;
    private String content;
    private Integer difficulty;
    private List<String> targetSkills;
    private String reason;
}
