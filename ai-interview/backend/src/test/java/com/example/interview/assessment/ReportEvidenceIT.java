package com.example.interview.assessment;

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
import com.example.interview.assessment.entity.EvaluationReport;
import com.example.interview.assessment.mapper.EvaluationReportMapper;
import com.example.interview.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIX-07：报告证据持久化后，读取接口不再调用 AI Provider。
 * - 生成报告后连续 GET 两次：evaluateTurn 计数零增长，两次 evidence 完全一致；
 * - 推荐缓存清空（强制未命中）后调 recommendations：不触发 AI 评分；
 * - 旧报告（question_evidence_json 置 NULL）读取回退重算一次并回填，再读不再调 AI。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReportEvidenceIT {

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private EvaluationReportMapper reportMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    static final AtomicInteger EVALUATE_COUNT = new AtomicInteger();

    @TestConfiguration
    static class CountingProviderConfig {
        @Bean
        @Primary
        AiProvider countingProvider() {
            return new CountingProvider();
        }
    }

    static class CountingProvider implements AiProvider {
        private final MockAiProvider delegate = new MockAiProvider();

        @Override
        public AiNextQuestionResult nextQuestion(NextQuestionContext ctx) {
            return delegate.nextQuestion(ctx);
        }

        @Override
        public AiTurnEvaluation evaluateTurn(TurnEvaluationContext ctx) {
            EVALUATE_COUNT.incrementAndGet();
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

    private record Session(String token, long sessionId, long userId) {
    }

    private Session newUserWithFinishedSession() throws InterruptedException {
        String uname = "evid" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Map<String, Object> reg = body(rest.postForEntity("/api/auth/register", jsonEntity(null,
                Map.of("username", uname, "password", "123456")), String.class));
        Map<String, Object> login = body(rest.postForEntity("/api/auth/login", jsonEntity(null,
                Map.of("username", uname, "password", "123456")), String.class));
        String token = ((Map<String, Object>) login.get("data")).get("token").toString();
        long uid = ((Number) ((Map<String, Object>) ((Map<String, Object>) login.get("data"))
                .get("user")).get("id")).longValue();
        Map<String, Object> created = body(rest.postForEntity("/api/interviews", jsonEntity(token,
                        Map.of("positionId", 1, "mode", "TEXT", "difficulty", 3,
                                "rounds", 5, "durationMinutes", 15)),
                String.class));
        long id = ((Number) ((Map<String, Object>) created.get("data")).get("id")).longValue();
        rest.postForEntity("/api/interviews/" + id + "/start", jsonEntity(token, null), String.class);
        rest.postForEntity("/api/interviews/" + id + "/messages/text",
                jsonEntity(token, Map.of("roundNo", 0,
                        "content", "缓存击穿用互斥锁与逻辑过期应对，TTL 加随机抖动，先查缓存再回源数据库。")),
                String.class);
        Thread.sleep(1500); // 等异步引擎完成追问（Mock 毫秒级），避免与 finish 竞态
        rest.postForEntity("/api/interviews/" + id + "/finish", jsonEntity(token, null), String.class);
        awaitReport(id); // 报告生成完成
        return new Session(token, id, uid);
    }

    @Test
    void getReportDoesNotCallAiProviderAfterPersistence() throws Exception {
        Session s = newUserWithFinishedSession();
        int baseline = EVALUATE_COUNT.get();

        Map<String, Object> first = getReport(s);
        Map<String, Object> second = getReport(s);

        assertThat(evidenceOf(first)).isNotEmpty();
        assertThat(evidenceOf(first)).isEqualTo(evidenceOf(second)); // 两次读取证据一致
        assertThat(EVALUATE_COUNT.get())
                .as("报告读取不得触发 evaluateTurn").isEqualTo(baseline);
    }

    @Test
    void recommendationsOnCacheMissDoNotCallAi() throws Exception {
        Session s = newUserWithFinishedSession();
        // 清空该用户推荐缓存，强制未命中重算
        Set<String> keys = redisTemplate.keys("rec:" + s.userId() + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        int baseline = EVALUATE_COUNT.get();

        var resp = rest.exchange("/api/users/me/recommendations",
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(authHeaders(s.token())), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(EVALUATE_COUNT.get())
                .as("推荐重算不得触发 evaluateTurn（证据来自报告快照）").isEqualTo(baseline);
    }

    @Test
    void legacyReportWithoutEvidenceFallsBackOnceAndBackfills() throws Exception {
        Session s = newUserWithFinishedSession();
        // 模拟 V7 前旧报告：证据列置 NULL
        reportMapper.update(null, Wrappers.<EvaluationReport>lambdaUpdate()
                .eq(EvaluationReport::getSessionId, s.sessionId())
                .set(EvaluationReport::getQuestionEvidence, null));
        int baseline = EVALUATE_COUNT.get();

        Map<String, Object> first = getReport(s);
        assertThat(EVALUATE_COUNT.get())
                .as("旧报告首次读取允许确定性重算一次")
                .isGreaterThan(baseline);

        // 已回填：再次读取走快照不再调 AI，且证据语义一致（scores 为 Map，键序不敏感）
        int afterBackfill = EVALUATE_COUNT.get();
        Map<String, Object> again = getReport(s);
        assertThat(EVALUATE_COUNT.get()).isEqualTo(afterBackfill);
        assertThat(evidenceOf(first)).isEqualTo(evidenceOf(again));

        // 库内快照已回填（非 NULL）
        EvaluationReport saved = reportMapper.selectOne(Wrappers.<EvaluationReport>lambdaQuery()
                .eq(EvaluationReport::getSessionId, s.sessionId()));
        assertThat(saved.getQuestionEvidence()).isNotNull().isNotEmpty();
    }

    // ==================== 工具 ====================

    /** 从报告响应中取逐题证据（List<Map>，Map.equals 对键序不敏感）。 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> evidenceOf(Map<String, Object> reportResp) {
        return (List<Map<String, Object>>) ((Map<String, Object>) reportResp.get("data"))
                .get("questionEvidence");
    }

    private Map<String, Object> getReport(Session s) {
        var resp = rest.exchange("/api/interviews/" + s.sessionId() + "/report",
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(authHeaders(s.token())), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = JsonUtils.parse(resp.getBody(), Map.class);
        assertThat(body.get("code")).isEqualTo(0);
        return body;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private void awaitReport(long sessionId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            EvaluationReport r = reportMapper.selectOne(Wrappers.<EvaluationReport>lambdaQuery()
                    .eq(EvaluationReport::getSessionId, sessionId));
            if (r != null) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("报告生成超时 session=" + sessionId);
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
