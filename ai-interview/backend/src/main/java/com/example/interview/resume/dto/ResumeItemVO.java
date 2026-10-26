package com.example.interview.resume.dto;

import com.example.interview.resume.entity.Resume;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历列表项（简历协议 R-02 records[] 元素）：状态与总分摘要，overallScore 仅 ANALYZED 时存在。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResumeItemVO {

    private Long id;
    private String fileName;
    private String fileType;
    private Long positionId;
    private String positionName;
    /** ANALYZING / ANALYZED / FAILED */
    private String status;
    /** 仅 ANALYZED 时存在（0~100） */
    private Integer overallScore;
    private Boolean isActive;
    private LocalDateTime createdAt;

    public static ResumeItemVO from(Resume r, String positionName, Integer overallScore) {
        return ResumeItemVO.builder()
                .id(r.getId())
                .fileName(r.getFileName())
                .fileType(r.getFileType())
                .positionId(r.getPositionId())
                .positionName(positionName)
                .status(r.getStatus())
                .overallScore(overallScore)
                .isActive(r.getIsActive() != null && r.getIsActive() == 1)
                .createdAt(r.getCreatedAt())
                .build();
    }
}
