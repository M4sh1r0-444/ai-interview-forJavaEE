package com.example.interview.ai.provider;

import com.example.interview.ai.model.AiChatResponse;
import com.example.interview.ai.model.AiNextQuestionResult;
import com.example.interview.ai.model.AiReport;
import com.example.interview.ai.model.AiTurnEvaluation;
import com.example.interview.ai.model.NextQuestionContext;
import com.example.interview.ai.model.ReportContext;
import com.example.interview.ai.model.TurnEvaluationContext;
import com.example.interview.ai.parser.AiFallbackException;
import com.example.interview.ai.parser.AiResponseParser;
import com.example.interview.ai.prompt.PromptBuilder;
import com.example.interview.common.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAI 兼容真实 Provider（计划 §6.8 / 方案 §6.6）：WebClient POST {api-url}/chat/completions，
 * system 限定只输出 JSON + response_format=json_object；超时取 ai.timeout。
 *
 * <p>三级容错（计划 §6.8）：
 * ① 正常调用 → JSON 校验失败/超时/5xx 时重试 1 次（退避 1s）；
 * ② 仍失败 → 委托 {@link MockAiProvider}（题目模板追问 / 规则评分 / 结构化报告拼装）；
 * ③ 连续 3 次真实调用失败 → 本会话自动锁定 mock 直至结束（会话级熔断，WARN 提示）。
 * 锁定为会话粒度，成功调用即清零计数。</p>
 */
@Slf4j
public class OpenAiCompatibleProvider implements AiProvider {

    private static final String SYSTEM_PROMPT =
            "你是面试平台的AI引擎。严格按照用户消息中的输出要求作答，只输出一个合法的 JSON 对象，"
                    + "不要任何解释、markdown 围栏或多余文本。所有文案使用中文。";
    /** 重试退避（计划 §6.8：失败重试 1 次，退避 1s） */
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(1);
    /** 连续失败达到该次数后会话锁定 mock（计划 §6.8） */
    private static final int SESSION_LOCK_THRESHOLD = 3;

    private final WebClient webClient;
    private final AiProperties properties;
    private final AiResponseParser parser;
    private final PromptBuilder promptBuilder;
    private final MockAiProvider mock;

    /** sessionId → 连续失败次数；sessionId 为空（如非会话调用）时按 0 号桶计 */
    private final Map<Long, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    /** 已锁定 mock 的会话 */
    private final Map<Long, Boolean> lockedSessions = new ConcurrentHashMap<>();

    public OpenAiCompatibleProvider(WebClient webClient, AiProperties properties,
                                    AiResponseParser parser, PromptBuilder promptBuilder,
                                    MockAiProvider mock) {
        this.webClient = webClient;
        this.properties = properties;
        this.parser = parser;
        this.promptBuilder = promptBuilder;
        this.mock = mock;
    }

    // ==================== 三个能力方法 ====================

    @Override
    public AiNextQuestionResult nextQuestion(NextQuestionContext ctx) {
        if (locked(ctx.getSessionId())) {
            return mock.nextQuestion(ctx);
        }
        try {
            AiNextQuestionResult r = call(promptBuilder.buildNextQuestionPrompt(ctx),
                    AiNextQuestionResult.class, ctx.getSessionId());
            if (!AiNextQuestionResult.ACTION_FOLLOW_UP.equals(r.getAction())
                    && !AiNextQuestionResult.ACTION_NEXT_QUESTION.equals(r.getAction())) {
                throw new AiFallbackException("action 非法：" + r.getAction());
            }
            if (AiNextQuestionResult.ACTION_FOLLOW_UP.equals(r.getAction())
                    && (r.getContent() == null || r.getContent().isBlank())) {
                throw new AiFallbackException("FOLLOW_UP 缺少 content");
            }
            onSuccess(ctx.getSessionId());
            return r;
        } catch (Exception e) {
            return onFallback("nextQuestion", ctx.getSessionId(), e, () -> mock.nextQuestion(ctx));
        }
    }

    @Override
    public AiTurnEvaluation evaluateTurn(TurnEvaluationContext ctx) {
        if (locked(ctx.getSessionId())) {
            return mock.evaluateTurn(ctx);
        }
        try {
            AiTurnEvaluation eval = call(promptBuilder.buildEvaluateTurnPrompt(ctx),
                    AiTurnEvaluation.class, ctx.getSessionId());
            if (eval.getScores() == null || eval.getScores().isEmpty()) {
                throw new AiFallbackException("评分结果缺少 scores");
            }
            onSuccess(ctx.getSessionId());
            return eval;
        } catch (Exception e) {
            return onFallback("evaluateTurn", ctx.getSessionId(), e, () -> mock.evaluateTurn(ctx));
        }
    }

    @Override
    public AiReport generateReport(ReportContext ctx) {
        if (locked(ctx.getSessionId())) {
            return mock.generateReport(ctx);
        }
        try {
            AiReport report = call(promptBuilder.buildReportPrompt(ctx), AiReport.class, ctx.getSessionId());
            if (report.getSummary() == null || report.getSummary().isBlank()) {
                throw new AiFallbackException("报告缺少 summary");
            }
            onSuccess(ctx.getSessionId());
            return report;
        } catch (Exception e) {
            return onFallback("generateReport", ctx.getSessionId(), e, () -> mock.generateReport(ctx));
        }
    }

    // ==================== 调用与容错 ====================

    /** 单次 Chat 调用：POST /chat/completions，解析首个 choice 的 content 为目标 JSON。 */
    private <T> T callOnce(String prompt, Class<T> type) {
        AiChatResponse resp = webClient.post()
                .uri(properties.getApiUrl() + "/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "model", properties.getModel(),
                        "messages", List.of(
                                Map.of("role", "system", "content", SYSTEM_PROMPT),
                                Map.of("role", "user", "content", prompt)),
                        "temperature", 0.3,
                        "response_format", Map.of("type", "json_object")))
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, r ->
                        r.bodyToMono(String.class).map(b -> new WebClientResponseException(
                                r.statusCode().value(), "AI 服务 5xx", null, b.getBytes(), null)))
                .bodyToMono(AiChatResponse.class)
                .block();
        String content = resp != null ? resp.firstContent() : null;
        if (content == null || content.isBlank()) {
            throw new AiFallbackException("AI 返回 choices 为空");
        }
        log.debug("AI 原始返回：{}", content);
        return parser.parse(content, type);
    }

    /** 带一次重试的调用（超时/5xx/解析失败均触发；退避 1s）。 */
    private <T> T call(String prompt, Class<T> type, Long sessionId) {
        try {
            return callOnce(prompt, type);
        } catch (Exception first) {
            log.warn("AI 调用失败（将重试 1 次）session={}：{}", sessionId, first.getMessage());
            try {
                Thread.sleep(RETRY_BACKOFF.toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            return callOnce(prompt, type);
        }
    }

    private boolean locked(Long sessionId) {
        return sessionId != null && Boolean.TRUE.equals(lockedSessions.get(sessionId));
    }

    private void onSuccess(Long sessionId) {
        if (sessionId != null) {
            consecutiveFailures.remove(sessionId);
        }
    }

    /** 失败统一降级 Mock；连续 3 次失败锁会话（计划 §6.8 第 3 级）。 */
    private <T> T onFallback(String method, Long sessionId, Exception e, java.util.function.Supplier<T> mockCall) {
        if (sessionId != null) {
            int failures = consecutiveFailures.merge(sessionId, 1, Integer::sum);
            if (failures >= SESSION_LOCK_THRESHOLD && lockedSessions.putIfAbsent(sessionId, Boolean.TRUE) == null) {
                log.warn("AI 连续 {} 次调用失败，会话 {} 锁定 mock 直至结束（{}）",
                        failures, sessionId, e.getMessage());
            }
        }
        log.warn("AI {} 降级 Mock session={}：{}", method, sessionId, e.getMessage());
        return mockCall.get();
    }
}
