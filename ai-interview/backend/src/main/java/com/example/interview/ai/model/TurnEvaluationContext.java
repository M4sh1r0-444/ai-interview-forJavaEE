package com.example.interview.ai.model;

import com.example.interview.question.entity.Question;
import com.example.interview.interview.entity.InterviewMessage;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * evaluateTurn 的输入上下文（计划 §6.6：题目 + 该轮全部问答 + 知识片段）。M3 接入使用。
 */
@Data
@Builder
public class TurnEvaluationContext {

    private Question question;
    /** 该轮全部消息（含主问题、追问与回答，按时间升序） */
    private List<InterviewMessage> roundMessages;
    private List<String> knowledgeChunks;
}
