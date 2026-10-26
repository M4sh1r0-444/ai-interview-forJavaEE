package com.example.interview.ai.model;

import com.example.interview.resume.dto.ResumeProfile;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * analyzeResume 的输入上下文（简历方案 §4.1，R2-2）。maskedText 为 PII 脱敏后全文
 * （已截断至 app.resume.max-analysis-chars）；profile 为规则解析结果（含板块行，
 * 供 Mock 评分与证据句提取）。岗位为空 → 通用分析（技能按通用词典评估）。
 */
@Data
@Builder
public class ResumeAnalysisContext {

    /** 归属用户（V9 用户级 AI 配置路由用；null 走平台默认） */
    private Long userId;
    /** 简历 id（日志追踪与 Provider 熔断键） */
    private Long resumeId;
    /** 脱敏后简历全文（截断后） */
    private String maskedText;
    private ResumeProfile profile;
    /** 分析对照岗位名；null = 通用分析 */
    private String positionName;
    /** 岗位能力模型技能（名称 + 权重）；null = 通用分析 */
    private List<PositionSkill> positionSkills;

    /** 岗位能力模型技能项（Prompt 展示与 Mock 覆盖率计算用）。 */
    @Data
    @Builder
    public static class PositionSkill {
        private String name;
        private Double weight;
    }
}
