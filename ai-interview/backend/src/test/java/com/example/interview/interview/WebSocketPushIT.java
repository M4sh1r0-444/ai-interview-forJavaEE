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
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 计划 M2-2.6 完成标准：连接 /ws/interviews/{id}?token= 后依次收到
 * QUESTION（start）、FOLLOW_UP（文字回答）、TIMER（定时器，test profile 1s 粒度）；
 * 无效 token 连接被 close(4401)。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketPushIT {

    private static final String GOOD_ANSWER =
            "缓存击穿指热点 key 过期瞬间大量请求同时打到数据库，常见对策是互斥锁保证只有一个线程回源、"
                    + "逻辑过期让旧值先行兜底，并把热点 key 的 TTL 加随机抖动错开过期时间 42。";

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;

    /** 收集下行消息 + 关闭状态。 */
    private static class CollectingHandler extends TextWebSocketHandler {
        final BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        volatile CloseStatus closeStatus;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            received.add(JsonUtils.parse(message.getPayload(), Map.class));
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closeStatus = status;
        }
    }

    private record Session(String token, long sessionId) {
    }

    private Session newUserWithSession() {
        String uname = "ws" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
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
    void pushQuestionFollowupAndTimer() throws Exception {
        Session s = newUserWithSession();
        CollectingHandler handler = new CollectingHandler();
        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://127.0.0.1:" + port + "/ws/interviews/" + s.sessionId()
                + "?token=" + s.token());
        client.doHandshake(handler, new WebSocketHttpHeaders(), uri).get(5, TimeUnit.SECONDS);

        // start → QUESTION
        rest.postForEntity("/api/interviews/" + s.sessionId() + "/start",
                jsonEntity(s.token(), null), String.class);
        Map<String, Object> question = poll(handler, "QUESTION", 5);
        assertThat(((Map<String, Object>) question.get("payload")).get("content")).asString().isNotBlank();
        assertThat(((Map<String, Object>) question.get("payload")).get("roundNo")).isEqualTo(0);

        // 文字回答 → FOLLOW_UP（默认策略：首答追问一次）
        rest.postForEntity("/api/interviews/" + s.sessionId() + "/messages/text",
                jsonEntity(s.token(), Map.of("roundNo", 0, "content", GOOD_ANSWER)), String.class);
        poll(handler, "FOLLOW_UP", 5);

        // TIMER（test profile 推送粒度 1s）
        poll(handler, "TIMER", 5);
    }

    @Test
    void invalidTokenIsClosedWith4401() throws Exception {
        Session s = newUserWithSession();
        CollectingHandler handler = new CollectingHandler();
        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://127.0.0.1:" + port + "/ws/interviews/" + s.sessionId()
                + "?token=not-a-jwt");
        client.doHandshake(handler, new WebSocketHttpHeaders(), uri).get(5, TimeUnit.SECONDS);

        long deadline = System.currentTimeMillis() + 5000;
        while (handler.closeStatus == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(handler.closeStatus).isNotNull();
        assertThat(handler.closeStatus.getCode()).isEqualTo(4401);
    }

    // ==================== 工具 ====================

    /** 依序取出下一条目标类型消息（跳过其他类型）。 */
    private static Map<String, Object> poll(CollectingHandler handler, String type, int timeoutSeconds)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> msg = handler.received.poll(200, TimeUnit.MILLISECONDS);
            if (msg != null && type.equals(msg.get("type"))) {
                return msg;
            }
        }
        throw new AssertionError("等待 WS 消息 " + type + " 超时，已收到: " + handler.received);
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
