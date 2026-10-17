package com.example.interview.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.example.interview.assessment.dto.QuestionEvidenceVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 表 7：评估报告。session_id 唯一（一场面试一份报告，重试生成走更新/幂等）。
 * 逐题证据自 V7 起随报告落库（question_evidence_json，FIX-07）：读取接口不再
 * 调用 AI Provider 重算；V7 前旧报告该列为 NULL，读取回退确定性重算一次并回填。
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
    /** 逐题证据快照（FIX-07/V7；旧报告为 NULL 时读取回退重算并回填） */
    @TableField(value = "question_evidence_json", typeHandler = JacksonTypeHandler.class)
    private List<QuestionEvidenceVO> questionEvidence;
    /** AI 生成的整体评语（summary + 正文合并存储） */
    private String reportText;
    private LocalDateTime createdAt;
}
