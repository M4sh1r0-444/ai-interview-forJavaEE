package com.example.interview.knowledge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识片段新增请求（计划 §5.5-25）：keywords/skillTags 为逗号分隔字符串（列存储格式）。
 */
@Data
public class KnowledgeUpsertRequest {

    @NotNull
    private Long positionId;
    @NotBlank
    @Size(min = 2, max = 200)
    private String title;
    /** 300~8000 字（计划 §7-9 文本长度限制） */
    @NotBlank
    @Size(min = 10, max = 8000)
    private String content;
    /** 逗号分隔关键词（建议 8~15 个，参与全文检索） */
    @NotBlank
    @Size(min = 4, max = 500)
    private String keywords;
    /** 逗号分隔技能编码，与能力模型 skill_code 对齐 */
    @NotBlank
    @Size(min = 2, max = 200)
    private String skillTags;
    /** 权重占位：难度提示等扩展字段暂不启用 */
    @Min(1)
    @Max(5)
    private Integer difficulty;
}
