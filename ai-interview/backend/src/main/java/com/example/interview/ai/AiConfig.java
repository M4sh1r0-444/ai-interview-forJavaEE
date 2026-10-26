package com.example.interview.ai;

import com.example.interview.ai.model.AiEndpoint;
import com.example.interview.ai.parser.AiResponseParser;
import com.example.interview.ai.prompt.PromptBuilder;
import com.example.interview.ai.provider.AiProvider;
import com.example.interview.ai.provider.AiProviderFactory;
import com.example.interview.ai.provider.MockAiProvider;
import com.example.interview.ai.provider.OpenAiCompatibleProvider;
import com.example.interview.ai.provider.RoutingAiProvider;
import com.example.interview.aiconfig.service.UserAiConfigService;
import com.example.interview.common.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * AiProvider 装配（计划 §6.8 + V9 用户级配置）：容器内的 aiProvider Bean 为
 * RoutingAiProvider——ctx.userId 命中用户级配置（V9 表）时按配置组合路由到
 * AiProviderFactory 缓存的实例，否则走平台默认。平台默认仍按 §6.8 装配：
 * mock（离线可演示）或 qwen（OpenAI 兼容真实 Provider + 三级容错）；
 * qwen 模式缺少 api-url/api-key 时启动即失败（配置错误应当显式暴露，而非
 * 静默降级）。运行期网络/服务故障由各 Provider 内部自动回退 Mock（无网/欠费
 * 均不阻断面试）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiConfig {

    private final AiProperties properties;

    @Bean
    public AiProvider aiProvider(MockAiProvider mockAiProvider, WebClient aiWebClient,
                                  AiResponseParser parser, PromptBuilder promptBuilder,
                                  AiProviderFactory factory, UserAiConfigService userAiConfigService) {
        AiProvider defaultProvider =
                buildDefault(mockAiProvider, aiWebClient, parser, promptBuilder);
        log.info("AI Provider = 路由模式（平台默认见上；用户级配置优先，V9）");
        return new RoutingAiProvider(defaultProvider, factory, userAiConfigService);
    }

    /** 平台默认 Provider：原 §6.8 装配逻辑（mock 或 qwen 全局端点）。 */
    private AiProvider buildDefault(MockAiProvider mockAiProvider, WebClient aiWebClient,
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
        return new OpenAiCompatibleProvider(aiWebClient,
                new AiEndpoint(properties.getApiUrl(), properties.getApiKey(), properties.getModel()),
                parser, promptBuilder, mockAiProvider);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
