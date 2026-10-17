package com.example.interview.common.config;

import com.example.interview.interview.websocket.InterviewWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

/**
 * 注册面试 WebSocket 端点 /ws/interviews/{sessionId}（计划 §4.3）。
 * 鉴权在 Handler 连接期完成，Security 层 /ws/** 放行（§7-3）。
 * 握手 Origin 白名单可配置：app.websocket.allowed-origins（逗号分隔，FIX-04）——
 * 开发默认 5173 两源；prod/Compose 经 Nginx 80 入口需包含 http://127.0.0.1、http://localhost。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final InterviewWebSocketHandler handler;

    @Value("${app.websocket.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private List<String> allowedOrigins;

    public WebSocketConfig(InterviewWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/interviews/*")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }
}
