package com.example.interview.ai.provider;

import com.example.interview.ai.model.AiEndpoint;
import com.example.interview.ai.parser.AiResponseParser;
import com.example.interview.ai.prompt.PromptBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按端点组合构建/缓存 OpenAiCompatibleProvider（V9 用户级 AI 配置）。
 * 熔断/失败计数是 Provider 的实例级状态，按「配置组合」分桶恰好语义正确：
 * 不同用户、不同端点互不影响；同一用户多会话共享同一实例的健康状态。
 * 缓存上限 32：超限淘汰任意一条旧配置（被淘汰实例的存量会话自然跑完，
 * 后续调用重建实例仅重置该组合的熔断计数，无正确性影响）。
 */
@Component
@RequiredArgsConstructor
public class AiProviderFactory {

    private static final int MAX_INSTANCES = 32;

    private final WebClient aiWebClient;
    private final AiResponseParser parser;
    private final PromptBuilder promptBuilder;
    private final MockAiProvider mockAiProvider;

    private final Map<AiEndpoint, OpenAiCompatibleProvider> cache = new ConcurrentHashMap<>();

    /** 取（或构建）该端点组合对应的 Provider 实例。 */
    public OpenAiCompatibleProvider get(AiEndpoint endpoint) {
        OpenAiCompatibleProvider cached = cache.get(endpoint);
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            cached = cache.get(endpoint);
            if (cached != null) {
                return cached;
            }
            if (cache.size() >= MAX_INSTANCES) {
                cache.keySet().stream()
                        .filter(k -> !k.equals(endpoint))
                        .findFirst()
                        .ifPresent(cache::remove);
            }
            cached = new OpenAiCompatibleProvider(aiWebClient, endpoint, parser, promptBuilder, mockAiProvider);
            cache.put(endpoint, cached);
            return cached;
        }
    }
}
