package com.example.interview.aiconfig.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存/测试 AI 服务配置请求（个人中心「AI 服务配置」）。apiKey 在更新时
 * 可留空 = 保留已保存的 Key；首次保存（尚无配置行）必须填写。
 */
@Data
public class SaveAiConfigRequest {

    @NotBlank(message = "接口地址不能为空")
    @Size(max = 500, message = "接口地址不能超过 500 字符")
    private String apiUrl;

    /** OpenAI 兼容服务的 API Key；更新时留空 = 保留已保存值 */
    @Size(max = 200, message = "API Key 不能超过 200 字符")
    private String apiKey;

    @NotBlank(message = "模型名不能为空")
    @Size(max = 100, message = "模型名不能超过 100 字符")
    private String model;
}
