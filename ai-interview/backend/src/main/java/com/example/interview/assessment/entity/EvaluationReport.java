package com.example.interview.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 表 7：评估报告。session_id 唯一（一场面试一份报告，重试生成走更新/幂等）。
 * 逐题证据（题目/回答/得分点/证据句）不入库（9 表约束）：报告读取时按消息
 * 确定性重算（Mock 评分引擎无随机），真实 Provider 的缓存方案排期 M4（Redis）。
 */
@Data
@TableName(value = "evaluation_report", autoResultMap = true)
public class EvaluationReport {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 一场面试一份（UNIQUE） */
    private Long sessionId;
    private BigDecimal totalScore;
    /** 六维得分 {"technical":82,"depth":75,...}，TEXT 场无 voice */
    @TableField(value = "dimension_scores_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Double> dimensionScores;
    @TableField(value = "strengths_json", typeHandler = JacksonTypeHandler.class)
    private List<String> strengths;
    @TableField(value = "weaknesses_json", typeHandler = JacksonTypeHandler.class)
    private List<String> weaknesses;
    @TableField(value = "improvement_plan_json", typeHandler = JacksonTypeHandler.class)
    private List<String> improvementPlan;
    /** AI 生成的整体评语（summary + 正文合并存储） */
    private String reportText;
    private LocalDateTime createdAt;
}
