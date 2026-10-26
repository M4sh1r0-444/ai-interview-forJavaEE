package com.example.interview.resume.dto;

import com.example.interview.common.util.JsonUtils;
import com.example.interview.resume.entity.Resume;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历详情（简历协议 R-03，前端轮询端点）。profile / digest / analysis 仅在对应数据
 * 就绪时出现（NON_NULL）：ANALYZING 期三者缺席，ANALYZED 后齐全，FAILED 携带 failReason。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResumeDetailVO {

    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Long positionId;
    private String positionName;
    /** ANALYZING / ANALYZED / FAILED */
    private String status;
    private String failReason;
    private Integer charCount;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 结构化抽取（脱敏后），键按简历实际内容可能缺席 */
    private Object profile;
    /** 个性化摘要（简历方案 §3.2），仅 ANALYZED 时存在 */
    private Object digest;
    /** 分析结果，结构同 R-04，仅 ANALYZED 时存在 */
    private ResumeAnalysisVO analysis;

    public static ResumeDetailVO from(Resume r, String positionName, ResumeAnalysisVO analysis) {
        return ResumeDetailVO.builder()
                .id(r.getId())
                .fileName(r.getFileName())
                .fileType(r.getFileType())
                .fileSize(r.getFileSize())
                .positionId(r.getPositionId())
                .positionName(positionName)
                .status(r.getStatus())
                .failReason(r.getFailReason())
                .charCount(r.getCharCount())
                .isActive(r.getIsActive() != null && r.getIsActive() == 1)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .profile(parseJson(r.getProfileJson()))
                .digest(parseJson(r.getDigestJson()))
                .analysis(analysis)
                .build();
    }

    /** 落库 JSON 字符串 → 泛型对象（Map/List），缺失或损坏返回 null 由 NON_NULL 省略。 */
    private static Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.parse(json, Object.class);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
