package com.example.interview.aiconfig.dto;

import com.example.interview.aiconfig.entity.UserAiConfig;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * AI 服务配置回显（api_key 绝不回明文）。hasApiKey=true 且 apiKeyMasked=null
 * 表示密文无法解密（加密密钥已轮换），前端应提示重新保存。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiConfigVO {

    /** OpenAI 兼容接口地址 */
    private String apiUrl;
    /** 模型名 */
    private String model;
    /** 是否已保存 API Key（行存在即 true） */
    private Boolean hasApiKey;
    /** 掩码 Key（前 4 + **** + 后 4；长度 ≤8 全掩码）；解密失败为 null */
    private String apiKeyMasked;

    public static AiConfigVO from(UserAiConfig entity, String decryptedKey) {
        return AiConfigVO.builder()
                .apiUrl(entity.getApiUrl())
                .model(entity.getModel())
                .hasApiKey(true)
                .apiKeyMasked(mask(decryptedKey))
                .build();
    }

    /** 掩码规则：长度 >8 取前 4 + **** + 后 4，否则全掩码。 */
    static String mask(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
