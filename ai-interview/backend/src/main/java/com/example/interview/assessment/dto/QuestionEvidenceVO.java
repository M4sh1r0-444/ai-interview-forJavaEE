package com.example.interview.assessment.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 报告逐题证据视图（计划 §4.3 assessment：题目/回答/得分/coveredPoints/
 * missingPoints/证据句/评分理由）。
 */
@Data
@Builder
public class QuestionEvidenceVO {

    private Integer roundNo;
    private Long questionId;
    /** 主问题题干（含背景，与面试时提问一致） */
    private String questionContent;
    /** 该轮全部回答拼接摘要 */
    private String answerExcerpt;
    private Integer difficulty;
    private List<String> targetSkills;
    /** 该轮六维原始分 */
    private Map<String, Integer> scores;
    /** 该轮内容分（权重归一化后） */
    private Double contentScore;
    private List<String> coveredPoints;
    private List<String> missingPoints;
    /** 支撑打分的原句证据 */
    private List<String> evidence;
    private String reason;
}
