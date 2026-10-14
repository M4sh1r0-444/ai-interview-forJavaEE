package com.example.interview.growth.dto;

import com.example.interview.interview.entity.InterviewSession;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面试历史条目（计划 §5.4 #20）：sessionId、岗位、模式、总分、开始时间、状态。
 */
@Data
@Builder
public class HistoryItemVO {

    private Long sessionId;
    private Long positionId;
    private String positionName;
    /** TEXT / AUDIO */
    private String mode;
    private Integer difficulty;
    private Integer plannedRounds;
    private Integer currentRound;
    /** CREATED 未开始的会话不进历史（无 startedAt） */
    private String status;
    /** 报告生成后回填；ABORTED/未生成时为 null */
    private BigDecimal totalScore;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public static HistoryItemVO from(InterviewSession s, String positionName) {
        return HistoryItemVO.builder()
                .sessionId(s.getId())
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
                .build();
    }
}
