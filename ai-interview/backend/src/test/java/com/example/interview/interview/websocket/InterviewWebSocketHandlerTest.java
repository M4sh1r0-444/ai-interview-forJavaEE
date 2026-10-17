package com.example.interview.interview.websocket;

import com.example.interview.auth.security.JwtAuthenticationFilter;
import com.example.interview.common.util.JwtUtils;
import com.example.interview.interview.entity.InterviewSession;
import com.example.interview.interview.mapper.InterviewSessionMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FIX-03：服务端心跳必须真正发送 PingMessage（浏览器/标准客户端只对 PING 自动回 PONG，
 * 不会主动发 PONG）；心跳超时关闭码 4408，与鉴权失败 4401 区分；PONG 刷新存活时间。
 */
class InterviewWebSocketHandlerTest {

    private JwtUtils jwtUtils;
    private StringRedisTemplate redisTemplate;
    private InterviewSessionMapper sessionMapper;
    private InterviewWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        jwtUtils = mock(JwtUtils.class);
        redisTemplate = mock(StringRedisTemplate.class);
        sessionMapper = mock(InterviewSessionMapper.class);
        handler = new InterviewWebSocketHandler(jwtUtils, redisTemplate, sessionMapper);
        handler.pongTimeoutMillis = 60_000; // 单测无 Spring 注入，显式给默认超时
    }

    /** 走完整 afterConnectionEstablished（token 验签 + 归属校验）拿到一个已注册连接。 */
    private WebSocketSession connectedSession(long sessionId, long uid) throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtUtils.parse("tok")).thenReturn(claims);
        when(claims.getId()).thenReturn("jti-1");
        when(redisTemplate.hasKey(JwtAuthenticationFilter.BLACKLIST_PREFIX + "jti-1")).thenReturn(false);
        InterviewSession s = new InterviewSession();
        s.setUserId(uid);
        when(sessionMapper.selectById(sessionId)).thenReturn(s);
        when(jwtUtils.getUid(claims)).thenReturn(uid);

        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getUri()).thenReturn(URI.create("ws://127.0.0.1/ws/interviews/" + sessionId + "?token=tok"));
        when(ws.getId()).thenReturn("ws-" + sessionId);
        when(ws.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(ws);
        assertThat(handler.hasConnections(sessionId)).isTrue();
        return ws;
    }

    /** 反射老化存活时间戳，模拟长时间无 PONG。 */
    @SuppressWarnings("unchecked")
    private void ageLastAlive(String wsId, long ageMillis) throws Exception {
        Field field = InterviewWebSocketHandler.class.getDeclaredField("lastAlive");
        field.setAccessible(true);
        Map<String, Long> map = (Map<String, Long>) field.get(handler);
        map.put(wsId, System.currentTimeMillis() - ageMillis);
    }

    @Test
    void heartbeatSendsPingToOpenSession() throws Exception {
        WebSocketSession ws = connectedSession(1L, 9L);

        handler.heartbeat();

        org.mockito.ArgumentCaptor<WebSocketMessage<?>> captor =
                org.mockito.ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(ws, atLeastOnce()).sendMessage(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(m -> assertThat(m).isInstanceOf(PingMessage.class));
        verify(ws, never()).close(any(CloseStatus.class));
    }

    @Test
    void idleConnectionClosedWithHeartbeatTimeoutCode4408Not4401() throws Exception {
        WebSocketSession ws = connectedSession(2L, 9L);
        ageLastAlive("ws-2", 61_000); // 超过 60s 无 PONG

        handler.heartbeat();

        org.mockito.ArgumentCaptor<CloseStatus> status =
                org.mockito.ArgumentCaptor.forClass(CloseStatus.class);
        verify(ws).close(status.capture());
        assertThat(status.getValue().getCode()).isEqualTo(4408);
        assertThat(status.getValue().getCode()).isNotEqualTo(4401);
    }

    @Test
    void pongRefreshesAliveAndPreventsTimeoutClose() throws Exception {
        WebSocketSession ws = connectedSession(3L, 9L);
        ageLastAlive("ws-3", 59_000);                     // 接近 60s 超时阈值
        handler.handlePongMessage(ws, new PongMessage()); // 客户端回 PONG，存活时间刷新为当前
        ageLastAliveRelativeAfterPong();                  // PONG 后仅推进 5s（若 PONG 未生效则已 64s 超时）

        handler.heartbeat();

        org.mockito.ArgumentCaptor<WebSocketMessage<?>> captor =
                org.mockito.ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(ws, atLeastOnce()).sendMessage(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(m -> assertThat(m).isInstanceOf(PingMessage.class));
        verify(ws, never()).close(any(CloseStatus.class));
    }

    /** PONG 刷新后把存活时间再推进 5s：若 handlePongMessage 确实刷新，则距 PONG 仅 5s；否则累计 64s 已超时。 */
    @SuppressWarnings("unchecked")
    private void ageLastAliveRelativeAfterPong() throws Exception {
        Field field = InterviewWebSocketHandler.class.getDeclaredField("lastAlive");
        field.setAccessible(true);
        Map<String, Long> map = (Map<String, Long>) field.get(handler);
        map.merge("ws-3", 5_000L, Math::subtractExact);
    }
}
