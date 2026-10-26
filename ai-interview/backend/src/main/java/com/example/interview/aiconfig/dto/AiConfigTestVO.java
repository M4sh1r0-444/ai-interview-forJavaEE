package com.example.interview.aiconfig.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/** 连通性测试结果（POST /api/users/me/ai-config/test；测试不落库）。 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiConfigTestVO {

    /** true=可正常调用 */
    private boolean ok;
    /** 结果说明（失败原因摘要，可直接展示） */
    private String message;
    /** 耗时毫秒（ok=true 时有值） */
    private Long latencyMs;
}
