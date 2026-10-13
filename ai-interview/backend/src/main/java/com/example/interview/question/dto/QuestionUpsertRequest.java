package com.example.interview.question.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 管理端新增/修改题目的字段校验（计划 §4.3 question）。 */
@Data
public class QuestionUpsertRequest {

    @NotNull(message = "岗位不能为空")
    private Long positionId;

    @NotBlank(message = "题型不能为空")
    @jakarta.validation.constraints.Pattern(regexp = "TECHNICAL|PROJECT|SCENARIO|BEHAVIORAL",
            message = "题型必须为 TECHNICAL/PROJECT/SCENARIO/BEHAVIORAL")
    private String type;

    @NotBlank(message = "题目内容不能为空")
    @Size(min = 4, max = 1000, message = "题目内容长度需在 4~1000 字")
    private String content;

    @Size(max = 1000, message = "题目背景不能超过 1000 字")
    private String background;

    @NotNull(message = "难度不能为空")
    @Min(value = 1, message = "难度最小为 1")
    @Max(value = 5, message = "难度最大为 5")
    private Integer difficulty;

    @NotEmpty(message = "技能标签不能为空")
    @Size(max = 10, message = "技能标签最多 10 个")
    private List<String> skillTags;

    @Size(max = 4000, message = "参考答案不能超过 4000 字")
    private String referenceAnswer;

    @Size(max = 10, message = "得分点最多 10 条")
    private List<String> keyPoints;

    @Size(max = 10, message = "失分点最多 10 条")
    private List<String> deductionPoints;

    @Size(max = 10, message = "追问模板最多 10 条")
    private List<String> followupTemplates;

    /** 修改时必填（乐观锁）；新增时忽略 */
    private Integer version;
}
