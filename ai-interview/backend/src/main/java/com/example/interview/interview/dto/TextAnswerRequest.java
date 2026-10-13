package com.example.interview.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用例 11：POST /api/interviews/{id}/messages/text（计划 §5.3）。文本长度 ≤5000（§7-9）。
 */
@Data
public class TextAnswerRequest {

    @NotNull(message = "roundNo 必填")
    @Min(value = 0, message = "roundNo 从 0 开始")
    private Integer roundNo;

    @NotBlank(message = "回答内容不能为空")
    @Size(max = 5000, message = "回答不能超过 5000 字")
    private String content;
}
