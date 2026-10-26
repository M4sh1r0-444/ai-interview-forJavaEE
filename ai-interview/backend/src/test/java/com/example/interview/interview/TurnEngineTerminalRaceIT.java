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
import com.example.interview.assessment.service.ReportService;
import com.example.interview.common.util.JsonUtils;
import com.example.interview.interview.entity.InterviewMessage;
import com.example.interview.interview.entity.InterviewSession;
import com.example.interview.interview.mapper.InterviewMessageMapper;
import com.example.interview.interview.mapper.InterviewSessionMapper;
import com.example.interview.interview.statemachine.InterviewStateMachine;
import com.example.interview.interview.statemachine.InterviewStatus;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIX-05 终态竞态：提交回答后 TurnEngine 异步处理期间 finish/abort/超时到达，
 * 会话进入终态后引擎不得再写消息、推进轮次或重复触发报告。
 * 用可闩锁的慢 AiProvider 把 nextQuestion 阻塞在 AI 调用处，交叉点后再放行。
 * 对旧实现红灯：applyFollowup 先 insert 后 CAS（失败不回滚 → 消息泄漏）、
 * applyAdvance 无条件 updateById(currentRound)（终态后轮次漂移）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TurnEngineTerminalRaceIT {

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private InterviewSessionMapper sessionMapper;
    @Autowired
    private InterviewMessageMapper messageMapper;
    @Autowired
    private InterviewStateMachine stateMachine;
    @Autowired
    private ReportService reportService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    /** 慢 Provider 控制点（Spring 单例读取静态字段；每个用例前重置） */
    static volatile CountDownLatch providerEntered = new CountDownLatch(1);
    static volatile CountDownLatch releaseProvider = new CountDownLatch(1);
    static final AtomicReference<String> forcedAction = new AtomicReference<>("FOLLOW_UP");

    @TestConfiguration
    static class SlowProviderConfig {
        @Bean
        @Primary
        AiProvider latchedAiProvider() {
            return new LatchedProvider();
        }
    }

    /** nextQuestion 阻塞在闩锁上；evaluateTurn/generateReport 委托 Mock 快速返回。 */
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
            if (AiNextQuestionResult.ACTION_NEXT_QUESTION.equals(forcedAction.get())) {
                AiNextQuestionResult r = new AiNextQuestionResult();
                r.setAction(AiNextQuestionResult.ACTION_NEXT_QUESTION);
                return r;
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

        @Override
        public com.example.interview.ai.model.AiQuestionPersonalization personalizeQuestions(
                com.example.interview.ai.model.QuestionPersonalizeContext ctx) {
            return delegate.personalizeQuestions(ctx);
        }
    }

    @BeforeEach
    void resetLatches() {
        providerEntered = new CountDownLatch(1);
        releaseProvider = new CountDownLatch(1);
        forcedAction.set("FOLLOW_UP");
    }

    private static final String GOOD_ANSWER =
            "缓存击穿指热点 key 过期瞬间大量请求同时打到数据库，常见对策是互斥锁保证只有一个线程回源、"
                    + "逻辑过期让旧值先行兜底，并把热点 key 的 TTL 加随机抖动错开过期时间 42。";

    private record Session(String token, long sessionId) {
    }

    private Session newUserWithSession() {
        String uname = "race" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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

    /** 提交回答并等慢 Provider 进入 nextQuestion（此时 TurnEngine 已过状态读取点）。 */
    private void submitAnswerAndBlockAi(Session s, long messagesBefore) throws Exception {
        rest.postForEntity("/api/interviews/" + s.sessionId() + "/messages/text",
                jsonEntity(s.token(), Map.of("roundNo", 0, "content", GOOD_ANSWER)), String.class);
        assertThat(providerEntered.await(5, TimeUnit.SECONDS)).as("AI 处理应已进入阻塞点").isTrue();
    }

    private void releaseAndSettle() throws Exception {
        releaseProvider.countDown();
        Thread.sleep(1500); // 等异步引擎走完失败路径
    }

    @Test
    void finishDuringAiProcessingLeavesNoFollowupWrite() throws Exception {
        Session s = newUserWithSession();
        rest.postForEntity("/api/interviews/" + s.sessionId() + "/start",
                jsonEntity(s.token(), null), String.class);
        long messagesBefore = countMessages(s.sessionId());
        submitAnswerAndBlockAi(s, messagesBefore);

        rest.postForEntity("/api/interviews/" + s.sessionId() + "/finish",
                jsonEntity(s.token(), null), String.class);
        releaseAndSettle();

        InterviewSession after = sessionMapper.selectById(s.sessionId());
        assertThat(after.getStatus()).isEqualTo("FINISHED");
        // 终态后无新增 AI 消息（仅 +1 条 USER 回答；旧实现追问已 insert → 红灯）
        assertThat(countMessages(s.sessionId())).isEqualTo(messagesBefore + 1);
        assertThat(after.getCurrentRound()).as("终态后轮次不漂移").isZero();
        assertThat(reportService.reportIdOf(s.sessionId())).isNotNull();
    }

    @Test
    void abortDuringAiProcessingLeavesNoFollowupWrite() throws Exception {
        Session s = newUserWithSession();
        rest.postForEntity("/api/interviews/" + s.sessionId() + "/start",
                jsonEntity(s.token(), null), String.class);
        long messagesBefore = countMessages(s.sessionId());
        submitAnswerAndBlockAi(s, messagesBefore);

        rest.postForEntity("/api/interviews/" + s.sessionId() + "/abort",
                jsonEntity(s.token(), null), String.class);
        releaseAndSettle();

        InterviewSession after = sessionMapper.selectById(s.sessionId());
        assertThat(after.getStatus()).isEqualTo("ABORTED");
        assertThat(countMessages(s.sessionId())).isEqualTo(messagesBefore + 1);
        assertThat(after.getCurrentRound()).isZero();
        assertThat(reportService.reportIdOf(s.sessionId())).isNull(); // ABORTED 不出报告
    }

    @Test
    void timeoutTransitionDuringAiProcessingLeavesNoFollowupWrite() throws Exception {
        Session s = newUserWithSession();
        rest.postForEntity("/api/interviews/" + s.sessionId() + "/start",
                jsonEntity(s.token(), null), String.class);
        long messagesBefore = countMessages(s.sessionId());
        submitAnswerAndBlockAi(s, messagesBefore);

        // 等价于定时器到期：同一状态机条件更新 PROCESSING_AI → TIMEOUT
        assertThat(stateMachine.tryTransition(s.sessionId(),
                InterviewStatus.PROCESSING_AI, InterviewStatus.TIMEOUT)).isTrue();
        releaseAndSettle();

        InterviewSession after = sessionMapper.selectById(s.sessionId());
        assertThat(after.getStatus()).isEqualTo("TIMEOUT");
        assertThat(countMessages(s.sessionId())).isEqualTo(messagesBefore + 1);
        assertThat(after.getCurrentRound()).isZero();
    }

    @Test
    void finishDuringAiAdvanceDoesNotDriftCurrentRound() throws Exception {
        forcedAction.set(AiNextQuestionResult.ACTION_NEXT_QUESTION);
        Session s = newUserWithSession();
        rest.postForEntity("/api/interviews/" + s.sessionId() + "/start",
                jsonEntity(s.token(), null), String.class);
        long messagesBefore = countMessages(s.sessionId());
        submitAnswerAndBlockAi(s, messagesBefore);

        rest.postForEntity("/api/interviews/" + s.sessionId() + "/finish",
                jsonEntity(s.token(), null), String.class);
        releaseAndSettle();

        InterviewSession after = sessionMapper.selectById(s.sessionId());
        assertThat(after.getStatus()).isEqualTo("FINISHED");
        // 旧实现 applyAdvance 无条件 updateById(currentRound=next) → 红灯
        assertThat(after.getCurrentRound()).as("终态后 currentRound 不得漂移").isZero();
        assertThat(reportService.reportIdOf(s.sessionId())).isNotNull();
    }

    private long countMessages(long sessionId) {
        return messageMapper.selectCount(Wrappers.<InterviewMessage>lambdaQuery()
                .eq(InterviewMessage::getSessionId, sessionId));
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
