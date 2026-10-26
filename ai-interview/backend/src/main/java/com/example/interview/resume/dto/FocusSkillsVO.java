package com.example.interview.resume.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * R-08 个性化重点考察预览（协议 §3 R-08）：纯规则计算结果，
 * 供面试设置页展示命中技能 / 缺口技能与命中权重占比。
 */
@Data
@Builder
public class FocusSkillsVO {

    private Long resumeId;
    private Long positionId;
    private String positionName;
    /** 命中技能，按岗位权重降序 */
    private List<MatchedSkill> matched;
    /** 岗位要求但简历未体现 */
    private List<GapSkill> gaps;
    /** 命中岗位技能权重占比 0~1；0 表示将按通用策略组卷 */
    private double hitRatio;

    @Data
    @Builder
    public static class MatchedSkill {
        private String skillCode;
        private String skillName;
        private Double weight;
        /** 简历中体现该技能的原文表述（digest 技能名） */
        private List<String> matchedTerms;
    }

    @Data
    @Builder
    public static class GapSkill {
        private String skillCode;
        private String skillName;
    }
}
