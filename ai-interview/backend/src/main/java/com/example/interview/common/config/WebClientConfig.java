package com.example.interview.common.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 调用大模型的 WebClient（计划 §4.3）：响应超时取 ai.timeout（默认 30s）、
 * 连接超时同值；日志过滤器只记方法与 URL，Authorization 头不落日志（脱敏，计划 §7-7）。
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient aiWebClient(AiProperties properties) {
        int timeoutMs = (int) Math.min(properties.getTimeout(), Integer.MAX_VALUE);
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(properties.getTimeout()))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs)
                .doOnConnected(conn -> conn.addHandlerLast(
                        new io.netty.handler.timeout.ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS)));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter((request, next) -> {
                    // 只记录方法与路径；API Key 在 Authorization 头里，绝不输出
                    org.slf4j.LoggerFactory.getLogger("ai.webclient")
                            .debug("AI 请求 {} {}", request.method(), request.url().getPath());
                    return next.exchange(request);
                })
                .build();
    }
}
