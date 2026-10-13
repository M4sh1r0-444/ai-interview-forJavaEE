package com.example.interview.interview.dto;

import com.example.interview.interview.entity.InterviewSession;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会话详情视图（计划 §5.3 #9/#14/#15）。
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

    public static InterviewDetailVO from(InterviewSession s, String positionName, Long remainSeconds) {
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
                .build();
    }
}
