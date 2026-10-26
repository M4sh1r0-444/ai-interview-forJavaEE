package com.example.interview.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.example.interview.resume.dto.SuggestionDTO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 表 11：简历分析结果（V8）。resume_id 唯一——一份简历一条结果，重分析为事务内
 * delete + insert 覆盖（简历计划 §3）。JSON 列经 JacksonTypeHandler 映射（模式同 EvaluationReport）。
 */
@Data
@TableName(value = "resume_analysis", autoResultMap = true)
public class ResumeAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 一份简历一条（UNIQUE） */
    private Long resumeId;
    /** 实际产出结果的 Provider：mock / qwen */
    private String provider;
    /** 总分 0~100 */
    private Integer overallScore;
    /** 六维 {basic,education,skill,project,experience,expression}，每维 0~100 */
    @TableField(value = "dimension_scores_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Integer> dimensionScores;
    @TableField(value = "strengths_json", typeHandler = JacksonTypeHandler.class)
    private List<String> strengths;
    /** 3~8 条，HIGH→MEDIUM→LOW 排序 */
    @TableField(value = "suggestions_json", typeHandler = JacksonTypeHandler.class)
    private List<SuggestionDTO> suggestions;
    /** 总评（≤500 字） */
    private String summaryText;
    private LocalDateTime createdAt;
}
