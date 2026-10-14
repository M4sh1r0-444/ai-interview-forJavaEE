package com.example.interview.assessment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报告视图（计划 §5.3 #17 / §4.3）：六维分、亮点、不足、改进建议、整体评语
 * 与逐题证据，满足验收“分数/证据/亮点/不足/建议”齐全。
 */
@Data
@Builder
public class ReportVO {

    private Long reportId;
    private Long sessionId;
    private String positionName;
    private String mode;
    private String status;
    private BigDecimal totalScore;
    /** 六维得分（TEXT 场无 voice；M4 AUDIO 场补齐） */
    private Map<String, Double> dimensionScores;
    private String summary;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> improvementPlan;
    private String reportText;
    private List<QuestionEvidenceVO> questionEvidence;
    private LocalDateTime createdAt;
}
