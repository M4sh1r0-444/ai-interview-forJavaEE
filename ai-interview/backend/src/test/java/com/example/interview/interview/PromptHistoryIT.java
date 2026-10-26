package com.example.interview.interview;

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
import com.example.interview.interview.mapper.InterviewMessageMapper;
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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIX-06：出题 Prompt 的【最近对话】不得包含尚未问到的未来主问题。
 * start 预写 5 轮主问题（round_no=0..4）；第 n 轮的 nextQuestion 上下文 history
 * 只能含 round_no ≤ n 的内容。旧实现 LIMIT 10 无轮次过滤 → 第 0 轮就把
 * 第 1~4 轮题干全部喂给模型（红灯）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PromptHistoryIT {

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private InterviewMessageMapper messageMapper;

    /** 记录型 Provider：捕获每次 nextQuestion 的上下文，强制返回 NEXT_QUESTION 逐轮推进 */
    static final List<NextQuestionContext> captured = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class RecordingProviderConfig {
        @Bean
        @Primary
        AiProvider recordingProvider() {
            return new RecordingProvider();
        }
    }

    static class RecordingProvider implements AiProvider {
        private final MockAiProvider delegate = new MockAiProvider();

        @Override
        public AiNextQuestionResult nextQuestion(NextQuestionContext ctx) {
            captured.add(ctx);
            AiNextQuestionResult r = new AiNextQuestionResult();
            r.setAction(AiNextQuestionResult.ACTION_NEXT_QUESTION);
            return r;
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
    void clearCaptured() {
        captured.clear();
    }

    private record Session(String token, long sessionId) {
    }

    private Session newUserWithSession() {
        String uname = "prompt" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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

    /** 该轮主问题的题干标记（contentText 末段 = 背景+题干中的题干部分，各题唯一）。 */
    private String stemMarker(long sessionId, int roundNo) {
        InterviewMessage m = messageMapper.selectList(
                        com.baomidou.mybatisplus.core.toolkit.Wrappers.<InterviewMessage>lambdaQuery()
                                .eq(InterviewMessage::getSessionId, sessionId)
                                .eq(InterviewMessage::getRoundNo, roundNo)
                                .eq(InterviewMessage::getSpeaker, "AI")
                                .orderByAsc(InterviewMessage::getId)
                                .last("LIMIT 1"))
                .get(0);
        String text = m.getContentText();
        int lastNl = text.lastIndexOf('\n');
        String stem = lastNl > 0 ? text.substring(lastNl + 1) : text;
        return stem.substring(0, Math.min(24, stem.length()));
    }

    private List<String> historyTexts(int index) {
        return captured.get(index).getHistory().stream()
                .map(NextQuestionContext.HistoryMessage::getContent).toList();
    }

    @Test
    void promptHistoryNeverContainsFutureMainQuestions() throws Exception {
        Session s = newUserWithSession();
        rest.postForEntity("/api/interviews/" + s.sessionId() + "/start",
                jsonEntity(s.token(), null), String.class);

        // 第 0 轮回答 → 第 0 次 nextQuestion：history 不得含第 1~4 轮题干
        submitAnswerAndAwaitNext(s, 0, 1);
        List<String> h0 = historyTexts(0);
        for (int future = 1; future <= 4; future++) {
            String marker = stemMarker(s.sessionId(), future);
            assertThat(h0).as("第 0 轮 Prompt 不应包含第 %d 轮题干", future)
                    .noneMatch(t -> t.contains(marker));
        }
        assertThat(h0).anyMatch(t -> t.contains(stemMarker(s.sessionId(), 0)));

        // 第 1 轮：history 可含第 0/1 轮，不得含第 2~4 轮
        submitAnswerAndAwaitNext(s, 1, 2);
        List<String> h1 = historyTexts(1);
        for (int future = 2; future <= 4; future++) {
            String marker = stemMarker(s.sessionId(), future);
            assertThat(h1).as("第 1 轮 Prompt 不应包含第 %d 轮题干", future)
                    .noneMatch(t -> t.contains(marker));
        }
        assertThat(h1).anyMatch(t -> t.contains(stemMarker(s.sessionId(), 0)));
        assertThat(h1).anyMatch(t -> t.contains(stemMarker(s.sessionId(), 1)));

        // 第 2 轮：不得含第 3~4 轮
        submitAnswerAndAwaitNext(s, 2, 3);
        List<String> h2 = historyTexts(2);
        for (int future = 3; future <= 4; future++) {
            String marker = stemMarker(s.sessionId(), future);
            assertThat(h2).as("第 2 轮 Prompt 不应包含第 %d 轮题干", future)
                    .noneMatch(t -> t.contains(marker));
        }
    }

    /** 提交该轮回答并等待第 expectedCaptured 次 nextQuestion 上下文被记录。 */
    private void submitAnswerAndAwaitNext(Session s, int roundNo, int expectedCaptured)
            throws InterruptedException {
        rest.postForEntity("/api/interviews/" + s.sessionId() + "/messages/text",
                jsonEntity(s.token(), Map.of("roundNo", roundNo,
                        "content", "第" + roundNo + "轮回答：互斥锁与逻辑过期解决缓存击穿，TTL 加随机抖动。")),
                String.class);
        long deadline = System.currentTimeMillis() + 10_000;
        while (captured.size() < expectedCaptured && System.currentTimeMillis() < deadline) {
            Thread.sleep(150);
        }
        assertThat(captured.size()).isGreaterThanOrEqualTo(expectedCaptured);
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
