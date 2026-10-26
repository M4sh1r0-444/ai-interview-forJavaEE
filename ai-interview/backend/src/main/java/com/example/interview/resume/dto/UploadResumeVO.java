package com.example.interview.resume.dto;

import com.example.interview.resume.entity.Resume;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传简历响应（简历协议 R-01；R-05 reanalyze 复用）。上传即返回：
 * status=ANALYZING（解析成功，等待 AI）或 FAILED（解析失败，见 failReason）。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UploadResumeVO {

    private Long id;
    private String fileName;
    /** PDF / DOCX / MD / TXT */
    private String fileType;
    private Long fileSize;
    private Long positionId;
    /** ANALYZING / FAILED（上传时）；ANALYZED（reanalyze 前已完成的旧态不出现于此） */
    private String status;
    /** 仅 FAILED 时存在 */
    private String failReason;
    /** 抽取到的文本字符数（解析失败为 0） */
    private Integer charCount;
    private LocalDateTime createdAt;

    public static UploadResumeVO from(Resume r) {
        return UploadResumeVO.builder()
                .id(r.getId())
                .fileName(r.getFileName())
                .fileType(r.getFileType())
                .fileSize(r.getFileSize())
                .positionId(r.getPositionId())
                .status(r.getStatus())
                .failReason(r.getFailReason())
                .charCount(r.getCharCount())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
