package com.example.interview.interview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用例 9：POST /api/interviews（计划 §5.3）。
 * 范围校验（difficulty 1~5 / rounds 5~8 / durationMinutes 15~60）在 Service 做 → 40002。
 */
@Data
public class CreateInterviewRequest {

    @NotNull(message = "positionId 必填")
    private Long positionId;
    /** TEXT / AUDIO，缺省 TEXT */
    private String mode = "TEXT";
    @NotNull(message = "difficulty 必填")
    private Integer difficulty;
    @NotNull(message = "rounds 必填")
    private Integer rounds;
    @NotNull(message = "durationMinutes 必填")
    private Integer durationMinutes;
    /** 个性化面试来源简历（R3-1，可选）：须为本人且 ANALYZED（40401/10009，Service 校验）；
     *  不传 = 普通面试，行为与基线完全一致。简历绑定岗位与面试岗位允许不同。 */
    private Long resumeId;
}
