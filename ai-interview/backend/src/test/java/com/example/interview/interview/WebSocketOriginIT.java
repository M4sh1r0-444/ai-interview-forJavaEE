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
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * FIX-04：WS 握手 Origin 白名单可配置（app.websocket.allowed-origins）。
 * 生产同源（Nginx 80：http://127.0.0.1 / http://localhost）必须放行；
 * 开发源 5173 不回归；第三方 Origin 拒绝（403）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.websocket.allowed-origins=http://127.0.0.1,http://localhost,http://127.0.0.1:5173,http://localhost:5173"
})
class WebSocketOriginIT {

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;

    private record Session(String token, long sessionId) {
    }

    private Session newUserWithSession() {
        String uname = "wsorg" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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
    void prodSameOriginHandshakeAllowed() throws Exception {
        Session s = newUserWithSession();
        assertThat(handshakeAccepted(s, "http://127.0.0.1")).isTrue();
        assertThat(handshakeAccepted(s, "http://localhost")).isTrue();
    }

    @Test
    void dev5173OriginStillAllowed() throws Exception {
        Session s = newUserWithSession();
        assertThat(handshakeAccepted(s, "http://127.0.0.1:5173")).isTrue();
        assertThat(handshakeAccepted(s, "http://localhost:5173")).isTrue();
    }

    @Test
    void foreignOriginRejectedWith403() throws Exception {
        Session s = newUserWithSession();
        try {
            StandardWebSocketClient client = new StandardWebSocketClient();
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.setOrigin("http://evil.example");
            client.doHandshake(new TextWebSocketHandler() { }, headers,
                            URI.create("ws://127.0.0.1:" + port + "/ws/interviews/" + s.sessionId()
                                    + "?token=" + s.token()))
                    .get(5, TimeUnit.SECONDS);
            fail("外部 Origin 握手应被拒绝");
        } catch (ExecutionException e) {
            assertThat(e.getCause()).hasMessageContaining("403");
        }
    }

    /** 尝试带指定 Origin 握手，返回是否成功升级。 */
    private boolean handshakeAccepted(Session s, String origin) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        try {
            client.doHandshake(new TextWebSocketHandler() { }, headers,
                            URI.create("ws://127.0.0.1:" + port + "/ws/interviews/" + s.sessionId()
                                    + "?token=" + s.token()))
                    .get(5, TimeUnit.SECONDS);
            return true;
        } catch (ExecutionException e) {
            return false;
        }
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
