package com.example.interview.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学习资源新增/修改请求（计划 §5.5 #29★）。修改为全量替换语义
 * （字段与新增一致，均必填）。
 */
@Data
public class ResourceUpsertRequest {

    @NotNull(message = "positionId 必填")
    private Long positionId;
    @NotBlank(message = "skillCode 必填")
    @Size(max = 50)
    private String skillCode;
    @NotBlank(message = "title 必填")
    @Size(max = 200, message = "title 最长 200 字")
    private String title;
    /** ARTICLE / QUESTION / PRACTICE / PROJECT */
    @NotBlank(message = "resourceType 必填")
    private String resourceType;
    @Size(max = 500, message = "contentUrl 最长 500 字")
    private String contentUrl;
    @NotNull(message = "difficulty 必填")
    @Min(value = 1, message = "difficulty 取值 1~5")
    @Max(value = 5, message = "difficulty 取值 1~5")
    private Integer difficulty;
    /** 1 启用 0 下架；缺省 1 */
    private Integer enabled;
}
