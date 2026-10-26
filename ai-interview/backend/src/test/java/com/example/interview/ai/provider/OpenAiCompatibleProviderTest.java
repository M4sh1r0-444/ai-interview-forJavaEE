package com.example.interview.ai.provider;

import com.example.interview.ai.model.AiEndpoint;
import com.example.interview.ai.model.AiNextQuestionResult;
import com.example.interview.ai.model.AiReport;
import com.example.interview.ai.model.NextQuestionContext;
import com.example.interview.ai.model.ReportContext;
import com.example.interview.ai.parser.AiResponseParser;
import com.example.interview.ai.prompt.PromptBuilder;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAiCompatibleProvider（M4-4.6）：用 JDK HttpServer 模拟 OpenAI 兼容接口。
 * 覆盖：正常 JSON 解析；Bearer 鉴权头必带（FIX-01）；401/5xx/坏 JSON → 重试 1 次后
 * 降级 Mock；连续 3 次失败会话锁定 mock（后续调用不再打到服务端）。
 */
class OpenAiCompatibleProviderTest {

    private HttpServer server;
    private OpenAiCompatibleProvider provider;
    private final AtomicInteger requestCount = new AtomicInteger();
    /** 每个请求应答的 content；优先消费 contentQueue（模拟首答失败重试成功的场景） */
    private final java.util.Deque<String> contentQueue = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private volatile String respondContent;
    private volatile int respondStatus = 200;
    /** FIX-01：记录每个请求的 Authorization 头 */
    private final java.util.List<String> authHeaders = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** FIX-01：为 true 时缺少/错误 Authorization 返回 401（模拟真实网关鉴权） */
    private volatile boolean requireAuth = false;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            authHeaders.add(auth);
            boolean unauthorized = requireAuth && !"Bearer test-key".equals(auth);
            int status = unauthorized ? 401 : respondStatus;
            String queued = contentQueue.poll();
            String content = unauthorized ? "" : (queued != null ? queued
                    : (respondContent != null ? respondContent : ""));
            String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                    + toJsonString(content) + "}}]}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        WebClient webClient = WebClient.builder().build();
        provider = new OpenAiCompatibleProvider(webClient,
                new AiEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                        "test-key", "qwen-plus"),
                new AiResponseParser(), new PromptBuilder(), new MockAiProvider());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private NextQuestionContext context() {
        return NextQuestionContext.builder()
                .sessionId(77L)
                .positionName("Java后端")
                .positionSkills(List.of("Spring"))
                .difficulty(3).roundNo(0).plannedRounds(5)
                .questionContent("谈谈缓存击穿")
                .requestedAction("FOLLOW_UP")
                .instruction("针对缺失要点追问")
                .followupCount(0)
                .build();
    }

    @Test
    void parsesValidJsonResponse() {
        respondContent = "{\"action\":\"FOLLOW_UP\",\"content\":\"说说互斥锁方案的缺点？\","
                + "\"difficulty\":4,\"targetSkills\":[\"redis_mq\"],\"reason\":\"未提锁粒度\"}";
        AiNextQuestionResult r = provider.nextQuestion(context());
        assertThat(r.getAction()).isEqualTo("FOLLOW_UP");
        assertThat(r.getContent()).contains("互斥锁");
        assertThat(r.getDifficulty()).isEqualTo(4);
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void sendsBearerAuthorizationHeaderAgainstAuthRequiredStub() {
        // FIX-01：要求 Bearer 的 stub 下，请求头正确且结果被真实解析（不降级 Mock）
        requireAuth = true;
        respondContent = "{\"action\":\"FOLLOW_UP\",\"content\":\"追问：缓存穿透与击穿的区别？\","
                + "\"difficulty\":4,\"reason\":\"概念混淆\"}";
        AiNextQuestionResult r = provider.nextQuestion(context());
        assertThat(r.getAction()).isEqualTo("FOLLOW_UP");
        assertThat(r.getContent()).contains("缓存穿透");
        assertThat(r.getReason()).doesNotStartWith("mock:"); // 真实解析，不是 Mock 降级
        assertThat(requestCount.get()).isEqualTo(1);
        assertThat(authHeaders).containsExactly("Bearer test-key");
    }

    @Test
    void fallsBackToMockAfter401Unauthorized() {
        // FIX-01：鉴权失败（401）→ 重试 1 次 → 仍 401 → 降级 Mock，不抛异常
        respondStatus = 401;
        AiNextQuestionResult r = provider.nextQuestion(context());
        assertThat(r.getAction()).isEqualTo("FOLLOW_UP");
        assertThat(r.getContent()).isNotBlank();
        assertThat(r.getReason()).startsWith("mock:");
        assertThat(requestCount.get()).isEqualTo(2); // 1 次失败 + 1 次重试
    }

    @Test
    void fenceWrappedJsonParsesAfterOneGarbageRetry() {
        // 首答非 JSON → 退避 1s 重试 → 围栏包裹 JSON 剥离后解析成功（不触发 Mock 降级）
        contentQueue.addLast("抱歉我想先聊聊天气");
        contentQueue.addLast("```json\n{\"action\":\"FOLLOW_UP\",\"content\":\"重试成功的追问\","
                + "\"difficulty\":3,\"reason\":\"ok\"}\n```");
        AiNextQuestionResult r = provider.nextQuestion(context());
        assertThat(r.getContent()).contains("重试成功");
        assertThat(r.getReason()).isEqualTo("ok");
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void fallsBackToMockOnServerError() {
        respondStatus = 500;
        AiNextQuestionResult r = provider.nextQuestion(context());
        // Mock 兜底：requestedAction=FOLLOW_UP → 模板追问
        assertThat(r.getAction()).isEqualTo("FOLLOW_UP");
        assertThat(r.getContent()).isNotBlank();
        assertThat(r.getReason()).startsWith("mock:");
        // 1 次失败 + 1 次重试 = 2 次请求
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void fallsBackToMockOnInvalidJsonAndLocksSessionAfter3Failures() {
        respondStatus = 200;
        respondContent = "这不是 JSON，模型今天只想聊天"; // 解析失败 → 重试 → 仍失败 → mock

        for (int i = 1; i <= 3; i++) {
            AiNextQuestionResult r = provider.nextQuestion(context());
            assertThat(r.getReason()).startsWith("mock:"); // 全部降级成功
            assertThat(requestCount.get()).isEqualTo(2 * i); // 每次调用重试 1 回
        }

        // 第 3 次失败后会话锁定：第 4 次不再请求服务端
        AiNextQuestionResult locked = provider.nextQuestion(context());
        assertThat(locked.getReason()).startsWith("mock:");
        assertThat(requestCount.get()).isEqualTo(6);

        // FIX-16：会话终态显式释放——锁与失败计数清空，再次调用重新打到服务端
        provider.releaseSession(context().getSessionId());
        provider.nextQuestion(context());
        assertThat(requestCount.get()).isEqualTo(8); // 解除锁定后再次真实调用（重试 1 次）
    }

    @Test
    void evictStaleSessionsClearsIdleState() throws Exception {
        respondContent = "不是 JSON";
        for (int i = 0; i < 3; i++) {
            provider.nextQuestion(context()); // 触发锁定
        }
        // 反射把 lastAccess 置为 2 小时前，验证定时淘汰兜底清理（FIX-16）
        java.lang.reflect.Field f = OpenAiCompatibleProvider.class.getDeclaredField("lastAccess");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<Long, Long> lastAccess = (java.util.Map<Long, Long>) f.get(provider);
        long now = System.currentTimeMillis();
        lastAccess.put(context().getSessionId(), now - 2 * 60 * 60_000L);
        assertThat(provider.evictStaleSessions(now)).isEqualTo(1);
        // 淘汰后锁定状态消失：再次调用会打到服务端
        int before = requestCount.get();
        provider.nextQuestion(context());
        assertThat(requestCount.get()).isGreaterThan(before);
    }

    @Test
    void reportFallbackUsesMockAssembly() {
        respondStatus = 500;
        com.example.interview.ai.model.AiTurnEvaluation turn =
                new com.example.interview.ai.model.AiTurnEvaluation();
        turn.setScores(Map.of("technical", 72, "depth", 70, "logic", 68, "match", 75, "communication", 71));
        turn.setCoveredPoints(List.of("缓存击穿概念"));
        turn.setMissingPoints(List.of("互斥锁方案"));
        ReportContext ctx = ReportContext.builder()
                .sessionId(88L)
                .positionName("Java后端")
                .difficulty(3).roundCount(2).totalScore(75.0)
                .turnEvaluations(List.of(turn))
                .dimensionScores(Map.of("technical", 75.0, "depth", 70.0))
                .build();
        AiReport report = provider.generateReport(ctx);
        assertThat(report.getSummary()).contains("Java后端").contains("75");
        assertThat(report.getStrengths()).isNotEmpty();
        assertThat(report.getImprovementPlan()).isNotEmpty();
    }

    private static String toJsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
