package com.example.interview.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户启停请求（FIX-11，FIX-18 复用）：status 必填且取值 0/1，
 * 缺失/非法值统一 400/40001，不再出现 HTTP 200 返回 40001 的分叉。
 */
@Data
public class StatusUpdateRequest {

    /** 1 启用 0 禁用 */
    @NotNull(message = "status 必填")
    @Min(value = 0, message = "status 取值 0 或 1")
    @Max(value = 1, message = "status 取值 0 或 1")
    private Integer status;
}
