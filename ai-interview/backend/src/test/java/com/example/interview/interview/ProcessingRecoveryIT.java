package com.example.interview.interview;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.ai.model.AiNextQuestionResult;
import com.example.interview.ai.model.AiResumeAnalysis;
import com.example.interview.ai.model.AiReport;
import com.example.interview.ai.model.AiTurnEvaluation;
import com.example.interview.ai.model.NextQuestionContext;
import com.example.interview.ai.model.ReportContext;
import com.example.interview.ai.model.ResumeAnalysisContext;
import com.example.interview.ai.model.TurnEvaluationContext;
import com.example.interview.ai.provider.AiProvider;
import com.example.interview.ai.provider.MockAiProvider;
import com.example.interview.common.util.JsonUtils;
import com.example.interview.interview.entity.InterviewMessage;
import com.example.interview.interview.entity.InterviewSession;
import com.example.interview.interview.mapper.InterviewMessageMapper;
import com.example.interview.interview.mapper.InterviewSessionMapper;
import com.example.interview.interview.service.InterviewRecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIX-08：PROCESSING_AI 卡死会话重启后可恢复。
 * 用闩锁 Provider 把首次 processTurn 卡在 nextQuestion（模拟进程死在 AI 调用处），
 * 调用 InterviewRecoveryService.recoverProcessingSessions()（即 ApplicationReadyEvent
 * 重启时执行的动作）重投；放行后断言：
 * - 会话回到 WAITING_USER 且产生追问（恢复成功）；
 * - 双重投递幂等（FIX-05 CAS 保护）：第 0 轮只有 1 条追问消息。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProcessingRecoveryIT {

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private InterviewSessionMapper sessionMapper;
    @Autowired
    private InterviewMessageMapper messageMapper;
    @Autowired
    private InterviewRecoveryService recoveryService;

    static volatile CountDownLatch providerEntered = new CountDownLatch(1);
    static volatile CountDownLatch releaseProvider = new CountDownLatch(1);

    @TestConfiguration
    static class LatchedProviderConfig {
        @Bean
        @Primary
        AiProvider latchedProvider() {
            return new LatchedProvider();
        }
    }

    static class LatchedProvider implements AiProvider {
        private final MockAiProvider delegate = new MockAiProvider();

        @Override
        public AiNextQuestionResult nextQuestion(NextQuestionContext ctx) {
            CountDownLatch entered = providerEntered;
            CountDownLatch release = releaseProvider;
            if (entered != null) {
                entered.countDown();
            }
            if (release != null) {
                try {
                    assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return delegate.nextQuestion(ctx);
        }

        @Override
        public AiTurnEvaluation evaluateTurn(TurnEvaluationContext ctx) {
            return delegate.evaluateTurn(ctx);
        }

        @Override
        public AiReport generateReport(ReportContext ctx) {
            return delegate.generateReport(ctx);
        }

        @Override
        public AiResumeAnalysis analyzeResume(ResumeAnalysisContext ctx) {
            return delegate.analyzeResume(ctx);
        }
    }

    @BeforeEach
    void resetLatches() {
        providerEntered = new CountDownLatch(1);
        releaseProvider = new CountDownLatch(1);
    }

    @Test
    void stuckProcessingSessionRecoversToWaitingUserWithSingleFollowup() throws Exception {
        String uname = "recov" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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

        rest.postForEntity("/api/interviews/" + id + "/start", jsonEntity(token, null), String.class);
        rest.postForEntity("/api/interviews/" + id + "/messages/text",
                jsonEntity(token, Map.of("roundNo", 0,
                        "content", "缓存击穿用互斥锁与逻辑过期应对，TTL 加随机抖动。")),
                String.class);
        assertThat(providerEntered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(sessionMapper.selectById(id).getStatus()).isEqualTo("PROCESSING_AI");

        // 模拟重启动作：恢复服务对 PROCESSING_AI 会话重投（与首次提交并发阻塞在同一闩锁）
        recoveryService.recoverProcessingSessions();
        Thread.sleep(500); // 等重投任务也进入阻塞点
        releaseProvider.countDown();

        // 断言：恢复成功回到 WAITING_USER，且追问恰好一条（双重投递幂等）
        long deadline = System.currentTimeMillis() + 10_000;
        InterviewSession s = sessionMapper.selectById(id);
        while (!"WAITING_USER".equals(s.getStatus()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
            s = sessionMapper.selectById(id);
        }
        assertThat(s.getStatus()).as("重投后应恢复到 WAITING_USER").isEqualTo("WAITING_USER");

        List<InterviewMessage> round0 = messageMapper.selectList(
                Wrappers.<InterviewMessage>lambdaQuery()
                        .eq(InterviewMessage::getSessionId, id)
                        .eq(InterviewMessage::getRoundNo, 0)
                        .eq(InterviewMessage::getSpeaker, "AI")
                        .orderByAsc(InterviewMessage::getId));
        assertThat(round0).as("第 0 轮 = 主问题 + 恰好 1 条追问（无重复投递泄漏）").hasSize(2);
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
