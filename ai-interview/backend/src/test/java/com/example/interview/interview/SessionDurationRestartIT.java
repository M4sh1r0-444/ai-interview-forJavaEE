package com.example.interview.interview;

import com.example.interview.common.util.JsonUtils;
import com.example.interview.interview.entity.InterviewSession;
import com.example.interview.interview.mapper.InterviewSessionMapper;
import com.example.interview.interview.scheduler.InterviewTimerScheduler;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIX-08：用户选择的面试时长随会话落库（V7 planned_duration_minutes），
 * 重启（@DirtiesContext 触发真实上下文重建 → ApplicationReadyEvent 恢复）后
 * 剩余时间仍按 15 分钟计，不再被 60 分钟上界覆盖（旧缺陷实测 899s → 3548s）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SessionDurationRestartIT {

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private InterviewSessionMapper sessionMapper;
    @Autowired
    private InterviewTimerScheduler timerScheduler;

    /** 跨上下文重启存活（同一 JVM 内静态字段） */
    static volatile long sessionId;
    static volatile String token;

    @Test
    @Order(1)
    void createAndStartFifteenMinuteSession() {
        String uname = "dur" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        rest.postForEntity("/api/auth/register", jsonEntity(null,
                Map.of("username", uname, "password", "123456")), String.class);
        Map<String, Object> login = body(rest.postForEntity("/api/auth/login", jsonEntity(null,
                Map.of("username", uname, "password", "123456")), String.class));
        token = ((Map<String, Object>) login.get("data")).get("token").toString();
        Map<String, Object> created = body(rest.postForEntity("/api/interviews", jsonEntity(token,
                        Map.of("positionId", 1, "mode", "TEXT", "difficulty", 3,
                                "rounds", 5, "durationMinutes", 15)),
                String.class));
        sessionId = ((Number) ((Map<String, Object>) created.get("data")).get("id")).longValue();

        rest.postForEntity("/api/interviews/" + sessionId + "/start",
                jsonEntity(token, null), String.class);

        InterviewSession s = sessionMapper.selectById(sessionId);
        assertThat(s.getPlannedDurationMinutes())
                .as("时长应在创建时落库").isEqualTo(15);
        Long remain = timerScheduler.remainSeconds(sessionId);
        assertThat(remain).isBetween(850L, 900L);
    }

    @Test
    @Order(2)
    void remainSecondsRestoredFromPersistedDurationAfterRestart() {
        // 本方法运行前上下文已重建：ApplicationReadyEvent 的 recoverOnStartup
        // 已按 started_at + planned_duration_minutes 重挂定时器
        assertThat(sessionId).isPositive();
        InterviewSession s = sessionMapper.selectById(sessionId);
        assertThat(s.getStatus()).isEqualTo("WAITING_USER");
        assertThat(s.getPlannedDurationMinutes()).isEqualTo(15);

        Long remain = timerScheduler.remainSeconds(sessionId);
        assertThat(remain)
                .as("重启后剩余时间仍约 15 分钟（而非 60 分钟上界），实际 %s", remain)
                .isBetween(840L, 900L);
    }

    private HttpEntity<String> jsonEntity(String tok, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tok != null) {
            headers.setBearerAuth(tok);
        }
        return new HttpEntity<>(body != null ? JsonUtils.toJson(body) : null, headers);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(org.springframework.http.ResponseEntity<String> entity) {
        return JsonUtils.parse(entity.getBody(), Map.class);
    }
}
