package com.example.interview.common.config;

import com.example.interview.interview.websocket.InterviewWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册面试 WebSocket 端点 /ws/interviews/{sessionId}（计划 §4.3）。
 * 鉴权在 Handler 连接期完成，Security 层 /ws/** 放行（§7-3）。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final InterviewWebSocketHandler handler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/interviews/*")
                // 开发期前端 5173 跨域握手；prod 走 Nginx 同源（§11 R11）
                .setAllowedOrigins("http://localhost:5173", "http://127.0.0.1:5173");
    }
}
