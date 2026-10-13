package com.example.interview.ai;

import com.example.interview.ai.provider.AiProvider;
import com.example.interview.ai.provider.MockAiProvider;
import com.example.interview.common.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AiProvider 装配（计划 §6.8）：默认 mock；qwen 模式于 M4 接入
 * OpenAiCompatibleProvider，当前显式回退 mock 并告警。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiConfig {

    private final AiProperties properties;

    @Bean
    public AiProvider aiProvider(MockAiProvider mockAiProvider) {
        if (!"mock".equalsIgnoreCase(properties.getProvider())) {
            log.warn("ai.provider={} 的真实实现排期在 M4，当前回退 MockAiProvider（离线可演示）",
                    properties.getProvider());
        }
        return mockAiProvider;
    }
}
