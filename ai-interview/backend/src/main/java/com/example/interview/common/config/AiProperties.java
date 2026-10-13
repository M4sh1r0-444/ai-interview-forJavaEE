package com.example.interview.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 配置（计划 §4.3 / 附录 B）：provider/apiUrl/apiKey/model/timeout。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** mock / qwen */
    private String provider = "mock";
    private String apiUrl;
    private String apiKey;
    private String model = "qwen-plus";
    private long timeout = 30000;
}
