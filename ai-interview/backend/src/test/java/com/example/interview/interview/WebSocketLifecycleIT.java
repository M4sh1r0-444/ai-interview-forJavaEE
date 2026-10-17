package com.example.interview.interview;

import com.example.interview.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIX-03：真实 WS 连接在心跳周期下长期存活。
 * 压缩配置（心跳 1s / PONG 超时 4s）：标准客户端对服务端 PING 自动回 PONG，
 * 若服务端只踢不发 PING（旧缺陷），空闲连接约 4~5s 后被 4401 关闭；
 * 修复后 PING→PONG 刷新存活，连接空闲 9s（跨越两个超时周期）仍 open。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.websocket.heartbeat-interval-ms=1000",
        "app.websocket.pong-timeout-ms=4000"
})
class WebSocketLifecycleIT {

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;

    private record Session(String token, long sessionId) {
    }

    private Session newUserWithSession() {
        String uname = "wslife" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        rest.postForEntity("/api/auth/register", jsonEntity(null,
                Map.of("username", uname, "password", "123456")), String.class);
        Map<String, Object> login = body(rest.postForEntity("/api/auth/login", jsonEntity(null,
                Map.of("username", uname, "password", "123456")), String.class));
        String token = ((Map<String, Object>) login.get("data")).get("token").toString();
        Map<String, Object> created = body(rest.postForEntity("/api/interviews", jsonEntity(token,
                        Map.of("positionId", 1, "mode", "TEXT", "difficulty", 3,
                                "rounds", 5, "durationMinutes", 15)),
                String.class));
        long id = ((Number) ((Map<String, Object>) created.get("data")).get("id")).longValue();
        return new Session(token, id);
    }

    @Test
    void idleConnectionSurvivesMultipleHeartbeatTimeoutCycles() throws Exception {
        Session s = newUserWithSession();
        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://127.0.0.1:" + port + "/ws/interviews/" + s.sessionId()
                + "?token=" + s.token());
        WebSocketSession ws = client.doHandshake(new TextWebSocketHandler() {
                    // 不主动发任何消息：空闲连接，只依赖协议层 PING/PONG
                }, new WebSocketHttpHeaders(), uri)
                .get(5, TimeUnit.SECONDS);
        assertThat(ws.isOpen()).isTrue();

        // 空闲 9s > 2 × pong-timeout(4s)：服务端必须持续 PING 且收到客户端自动 PONG 才能存活
        Thread.sleep(9_000);

        assertThat(ws.isOpen())
                .as("空闲连接不应被心跳关闭（服务端需发 PING，客户端协议层自动回 PONG）")
                .isTrue();
        ws.close();
    }

    private HttpEntity<String> jsonEntity(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(body != null ? JsonUtils.toJson(body) : null, headers);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(org.springframework.http.ResponseEntity<String> entity) {
        return JsonUtils.parse(entity.getBody(), Map.class);
    }
}
