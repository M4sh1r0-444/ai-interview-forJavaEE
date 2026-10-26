package com.example.interview.ai.model;

import com.example.interview.resume.dto.ResumeDigest;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * personalizeQuestions 的输入上下文（简历方案 §4.1，R3-3）：
 * 岗位 + 简历摘要 + 本场主问题列表（ID/题型/技能标签/原题干），
 * start 时一次批量送 AI 改写题干。
 */
@Data
@Builder
public class QuestionPersonalizeContext {

    /** 所属面试会话（真实 Provider 会话级熔断与日志追踪用） */
    private Long sessionId;
    /** 归属用户（V9 用户级 AI 配置路由用；null 走平台默认） */
    private Long userId;
    private String positionName;
    /** 个性化摘要；null = 普通面试（调用方不会发起改写） */
    private ResumeDigest digest;
    /** 本场主问题（题库原题） */
    private List<QuestionBrief> questions;

    /** 题目快照（不引 question 实体，ai 包不依赖 question 包） */
    @Data
    @Builder
    public static class QuestionBrief {
        private Long questionId;
        /** TECHNICAL / PROJECT / SCENARIO / BEHAVIORAL */
        private String type;
        private List<String> skillTags;
        private String content;
    }
}
