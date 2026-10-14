package com.example.interview.ai;

import com.example.interview.ai.parser.AiResponseParser;
import com.example.interview.ai.prompt.PromptBuilder;
import com.example.interview.ai.provider.AiProvider;
import com.example.interview.ai.provider.MockAiProvider;
import com.example.interview.ai.provider.OpenAiCompatibleProvider;
import com.example.interview.common.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * AiProvider 装配（计划 §6.8）：mock（默认，离线可演示）或 qwen
 * （OpenAI 兼容真实 Provider + 三级容错）。qwen 模式缺少 api-url/api-key 时
 * 启动即失败（配置错误应当显式暴露，而非静默降级）；运行期网络/服务故障
 * 由 Provider 内部自动回退 Mock（无网/欠费均不阻断面试）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiConfig {

    private final AiProperties properties;

    @Bean
    public AiProvider aiProvider(MockAiProvider mockAiProvider, WebClient aiWebClient,
                                  AiResponseParser parser, PromptBuilder promptBuilder) {
        if ("mock".equalsIgnoreCase(properties.getProvider())) {
            log.info("AI Provider = mock（离线演示模式）");
            return mockAiProvider;
        }
        if (isBlank(properties.getApiUrl()) || isBlank(properties.getApiKey())) {
            throw new IllegalStateException(
                    "ai.provider=" + properties.getProvider() + " 需要配置 AI_API_URL 与 AI_API_KEY");
        }
        log.info("AI Provider = {}（model={}，api-url={}），故障时自动降级 mock",
                properties.getProvider(), properties.getModel(), properties.getApiUrl());
        return new OpenAiCompatibleProvider(aiWebClient, properties, parser, promptBuilder, mockAiProvider);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
