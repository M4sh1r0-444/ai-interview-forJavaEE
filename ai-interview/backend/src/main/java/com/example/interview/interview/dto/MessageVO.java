package com.example.interview.interview.dto;

import com.example.interview.interview.entity.InterviewMessage;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息视图（计划 §5.3 #10/#11/#16）。followupIndex：该 AI 消息在其主问题轮次中的
 * 序号（0=主问题，1..3=追问），由 Service 按时间序推导，不落库。
 */
@Data
@Builder
public class MessageVO {

    private Long id;
    private Long sessionId;
    private Integer roundNo;
    private String speaker;
    private String inputType;
    private String contentText;
    private Integer audioDurationMs;
    private String asrText;
    private Long questionId;
    private List<String> keywords;
    /** 仅 AI 消息：0=主问题，≥1=第几轮追问 */
    private Integer followupIndex;
    private LocalDateTime createdAt;

    public static MessageVO from(InterviewMessage m, Integer followupIndex) {
        return MessageVO.builder()
                .id(m.getId())
                .sessionId(m.getSessionId())
                .roundNo(m.getRoundNo())
                .speaker(m.getSpeaker())
                .inputType(m.getInputType())
                .contentText(m.getContentText())
                .audioDurationMs(m.getAudioDurationMs())
                .asrText(m.getAsrText())
                .questionId(m.getQuestionId())
                .keywords(m.getKeywords())
                .followupIndex(followupIndex)
                .createdAt(m.getCreatedAt())
                .build();
    }
}
