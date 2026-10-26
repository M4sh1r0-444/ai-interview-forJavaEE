package com.example.interview.interview.dto;

import com.example.interview.interview.entity.InterviewSession;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会话详情视图（计划 §5.3 #9/#14/#15）。R3-6 增量字段：resumeId/resumeFileName
 * （个性化会话来源简历，NON_NULL 缺席）与 personalized（= resumeId 非空，恒出现）。
 */
@Data
@Builder
public class InterviewDetailVO {

    private Long id;
    private Long userId;
    private Long positionId;
    private String positionName;
    private String mode;
    private Integer difficulty;
    private Integer plannedRounds;
    private Integer currentRound;
    private String status;
    private BigDecimal totalScore;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    /** 剩余秒数（未挂定时器时为 null，前端自行处理） */
    private Long remainSeconds;
    /** 个性化面试来源简历（R3-6/R-10）；普通面试缺席 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long resumeId;
    /** 简历文件名（供房间页展示；简历被删除后缺席） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String resumeFileName;
    /** 是否个性化面试（= resumeId 非空） */
    private boolean personalized;

    public static InterviewDetailVO from(InterviewSession s, String positionName, Long remainSeconds) {
        return from(s, positionName, remainSeconds, null);
    }

    public static InterviewDetailVO from(InterviewSession s, String positionName, Long remainSeconds,
                                         String resumeFileName) {
        return InterviewDetailVO.builder()
                .id(s.getId())
                .userId(s.getUserId())
                .positionId(s.getPositionId())
                .positionName(positionName)
                .mode(s.getMode())
                .difficulty(s.getDifficulty())
                .plannedRounds(s.getPlannedRounds())
                .currentRound(s.getCurrentRound())
                .status(s.getStatus())
                .totalScore(s.getTotalScore())
                .startedAt(s.getStartedAt())
                .finishedAt(s.getFinishedAt())
                .createdAt(s.getCreatedAt())
                .remainSeconds(remainSeconds)
                .resumeId(s.getResumeId())
                .resumeFileName(s.getResumeId() != null ? resumeFileName : null)
                .personalized(s.getResumeId() != null)
                .build();
    }
}
