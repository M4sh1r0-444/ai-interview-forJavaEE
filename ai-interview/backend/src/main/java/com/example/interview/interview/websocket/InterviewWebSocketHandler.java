package com.example.interview.interview.websocket;

import com.example.interview.auth.security.JwtAuthenticationFilter;
import com.example.interview.common.util.JsonUtils;
import com.example.interview.common.util.JwtUtils;
import com.example.interview.interview.entity.InterviewSession;
import com.example.interview.interview.mapper.InterviewSessionMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 面试 WebSocket：ws://host/ws/interviews/{sessionId}?token=&lt;jwt&gt;（计划 §5.6 / §7-3 R7）。
 * 浏览器 WS 不能自定义 Header，连接时用 query token 鉴权（验签 + 黑名单 + 会话归属），
 * 失败立即 close(4401)。同一会话允许多连接（刷新页面），推送遍历全部连接。
 * 心跳：服务端 30s PING，60s 无 PONG 踢除。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewWebSocketHandler extends TextWebSocketHandler {

    public static final CloseStatus UNAUTHORIZED = new CloseStatus(4401, "unauthorized");

    private static final long PONG_TIMEOUT_MILLIS = 60_000;

    /** sessionId → 该会话的全部连接（注册表） */
    private final Map<Long, Set<WebSocketSession>> registry = new ConcurrentHashMap<>();
    /** wsSessionId → 最近一次 PONG/连接时间 */
    private final Map<String, Long> lastAlive = new ConcurrentHashMap<>();

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;
    private final InterviewSessionMapper sessionMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) throws Exception {
        Long sessionId = parseSessionId(ws.getUri());
        if (sessionId == null || !authorize(ws, sessionId)) {
            safeClose(ws);
            return;
        }
        registry.computeIfAbsent(sessionId, k -> new CopyOnWriteArraySet<>()).add(ws);
        lastAlive.put(ws.getId(), System.currentTimeMillis());
        log.info("WS 连接建立 session={} 连接数={} remote={}",
                sessionId, registry.get(sessionId).size(), ws.getRemoteAddress());
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        lastAlive.put(session.getId(), System.currentTimeMillis());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 下行-only 协议；客户端文本消息忽略（心跳走协议层 PING/PONG）
        log.debug("忽略客户端文本消息 session={}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        lastAlive.remove(ws.getId());
        Long sessionId = parseSessionId(ws.getUri());
        if (sessionId != null) {
            Set<WebSocketSession> set = registry.get(sessionId);
            if (set != null) {
                set.remove(ws);
                if (set.isEmpty()) {
                    registry.remove(sessionId, set);
                }
            }
        }
        log.info("WS 连接关闭 session={} status={}", sessionId, status);
    }

    /** 是否有活跃连接（测试与观测用）。 */
    public boolean hasConnections(Long sessionId) {
        Set<WebSocketSession> set = registry.get(sessionId);
        return set != null && !set.isEmpty();
    }

    /** 按类型向该会话全部连接推送（计划 §5.6 下行消息）。 */
    public void push(Long sessionId, WsMessageType type, Map<String, Object> payload) {
        Set<WebSocketSession> set = registry.get(sessionId);
        if (set == null || set.isEmpty()) {
            log.debug("WS 推送跳过（无连接）session={} type={}", sessionId, type);
            return;
        }
        String text = JsonUtils.toJson(WsMessage.of(type, payload));
        for (WebSocketSession ws : set) {
            if (ws.isOpen()) {
                try {
                    // WebSocketSession 非线程安全：同一连接串行发送
                    synchronized (ws) {
                        ws.sendMessage(new TextMessage(text));
                    }
                } catch (IOException e) {
                    log.warn("WS 推送失败 session={} type={}: {}", sessionId, type, e.getMessage());
                }
            }
        }
    }

    /** 服务端心跳：30s PING，60s 无 PONG 踢除注册表（计划 §5.6）。 */
    @Scheduled(fixedDelay = 30_000)
    public void heartbeat() {
        long now = System.currentTimeMillis();
        lastAlive.forEach((wsId, last) -> {
            if (now - last > PONG_TIMEOUT_MILLIS) {
                registry.values().forEach(set -> set.stream()
                        .filter(ws -> wsId.equals(ws.getId()) && ws.isOpen())
                        .findFirst()
                        .ifPresent(this::safeClose));
                lastAlive.remove(wsId);
            }
        });
    }

    /** 连接鉴权：token 验签 + Redis 黑名单 + 会话归属（学生本人或 ADMIN）。 */
    private boolean authorize(WebSocketSession ws, Long sessionId) {
        String token = parseToken(ws.getUri());
        if (token == null || token.isBlank()) {
            return false;
        }
        Claims claims;
        try {
            claims = jwtUtils.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.info("WS 连接 token 无效: {}", e.getMessage());
            return false;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(JwtAuthenticationFilter.BLACKLIST_PREFIX + claims.getId()))) {
            log.info("WS 连接 token 已登出 jti={}", claims.getId());
            return false;
        }
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return false;
        }
        Long uid = jwtUtils.getUid(claims);
        boolean owner = session.getUserId().equals(uid);
        boolean admin = "ADMIN".equals(jwtUtils.getRole(claims));
        if (!owner && !admin) {
            log.info("WS 连接越权 session={} uid={}", sessionId, uid);
            return false;
        }
        return true;
    }

    private Long parseSessionId(URI uri) {
        if (uri == null || uri.getPath() == null) {
            return null;
        }
        String[] segments = uri.getPath().split("/");
        String last = segments[segments.length - 1];
        try {
            return Long.valueOf(last);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String parseToken(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String param : uri.getQuery().split("&")) {
            int eq = param.indexOf('=');
            if (eq > 0 && "token".equals(param.substring(0, eq))) {
                return param.substring(eq + 1);
            }
        }
        return null;
    }

    private void safeClose(WebSocketSession ws) {
        try {
            ws.close(UNAUTHORIZED);
        } catch (IOException ignored) {
            // 连接已断开
        }
    }

    /** 测试辅助：当前注册的全部会话 id。 */
    List<Long> registeredSessions() {
        return List.copyOf(registry.keySet());
    }
}
