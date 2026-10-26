package com.example.interview.ai.provider;

import com.example.interview.aiconfig.service.UserAiConfigService;
import com.example.interview.ai.model.AiEndpoint;
import com.example.interview.ai.model.AiNextQuestionResult;
import com.example.interview.ai.model.AiQuestionPersonalization;
import com.example.interview.ai.model.AiReport;
import com.example.interview.ai.model.AiResumeAnalysis;
import com.example.interview.ai.model.AiTurnEvaluation;
import com.example.interview.ai.model.NextQuestionContext;
import com.example.interview.ai.model.QuestionPersonalizeContext;
import com.example.interview.ai.model.ReportContext;
import com.example.interview.ai.model.ResumeAnalysisContext;
import com.example.interview.ai.model.TurnEvaluationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按用户路由的 AiProvider（V9 用户级 AI 配置）：容器内的 aiProvider Bean。
 * ctx.userId 命中用户级配置 → AiProviderFactory 按配置组合取实例；无配置 /
 * 解密失败 → 平台默认（全局 mock/qwen 装配逻辑不变）。用户实例内部的三级
 * 容错照常生效（重试→mock 降级→会话熔断），用户服务故障不阻断面试。
 *
 * <p>sessionDelegates 记录每个会话实际路由到的实例，releaseSession 同时释放
 * 默认实例与用户实例（FIX-16 语义保持）；定时淘汰 1h 未活跃条目，防终态
 * 未释放会话的状态累积。每次路由多一次配置查询（每轮 2~3 次，可忽略）。</p>
 */
@Slf4j
public class RoutingAiProvider implements AiProvider {

    private static final long SESSION_STATE_IDLE_MILLIS = java.time.Duration.ofHours(1).toMillis();
    private static final long EVICT_INTERVAL_MILLIS = 600_000L; // 10min（@Scheduled 需编译期常量）

    private final AiProvider defaultProvider;
    private final AiProviderFactory factory;
    private final UserAiConfigService userAiConfigService;

    /** sessionId → 实际路由到的实例（releaseSession 透传用） */
    private final Map<Long, AiProvider> sessionDelegates = new ConcurrentHashMap<>();
    /** sessionId → 最近访问时间（定时淘汰用） */
    private final Map<Long, Long> lastAccess = new ConcurrentHashMap<>();

    public RoutingAiProvider(AiProvider defaultProvider, AiProviderFactory factory,
                             UserAiConfigService userAiConfigService) {
        this.defaultProvider = defaultProvider;
        this.factory = factory;
        this.userAiConfigService = userAiConfigService;
    }

    @Override
    public AiNextQuestionResult nextQuestion(NextQuestionContext ctx) {
        return route(ctx.getSessionId(), ctx.getUserId()).nextQuestion(ctx);
    }

    @Override
    public AiTurnEvaluation evaluateTurn(TurnEvaluationContext ctx) {
        return route(ctx.getSessionId(), ctx.getUserId()).evaluateTurn(ctx);
    }

    @Override
    public AiReport generateReport(ReportContext ctx) {
        return route(ctx.getSessionId(), ctx.getUserId()).generateReport(ctx);
    }

    /** F1 简历分析（R2）：按简历归属用户路由（不记 sessionDelegates——简历无 release 语义，
     *  且 resumeId 与 sessionId 数值可能相同，避免串台；实例内部状态由 1h 淘汰兜底）。 */
    @Override
    public AiResumeAnalysis analyzeResume(ResumeAnalysisContext ctx) {
        return route(null, ctx.getUserId()).analyzeResume(ctx);
    }

    /** F2 题干改写（R3）：start 阶段调用，按会话归属用户路由（与其他会话调用同口径）。 */
    @Override
    public AiQuestionPersonalization personalizeQuestions(QuestionPersonalizeContext ctx) {
        return route(ctx.getSessionId(), ctx.getUserId()).personalizeQuestions(ctx);
    }

    /** 解析该用户的端点：有用户配置走对应实例，否则走平台默认。 */
    private AiProvider route(Long sessionId, Long userId) {
        AiEndpoint endpoint = userId == null ? null : userAiConfigService.resolveEndpoint(userId);
        AiProvider delegate = endpoint != null ? factory.get(endpoint) : defaultProvider;
        if (sessionId != null) {
            sessionDelegates.put(sessionId, delegate);
            lastAccess.put(sessionId, System.currentTimeMillis());
        }
        return delegate;
    }

    /** 会话终态释放：默认实例与该会话实际路由到的用户实例都要释放（FIX-16）。 */
    @Override
    public void releaseSession(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        AiProvider delegate = sessionDelegates.remove(sessionId);
        lastAccess.remove(sessionId);
        defaultProvider.releaseSession(sessionId);
        if (delegate != null) {
            delegate.releaseSession(sessionId);
        }
    }

    /** 定时淘汰长时间未访问的会话状态（终态未显式 release 的兜底）。 */
    @Scheduled(fixedDelay = EVICT_INTERVAL_MILLIS)
    public void evictStaleSessions() {
        evictStaleSessions(System.currentTimeMillis());
    }

    int evictStaleSessions(long nowMillis) {
        int evicted = 0;
        for (Map.Entry<Long, Long> e : lastAccess.entrySet()) {
            if (nowMillis - e.getValue() > SESSION_STATE_IDLE_MILLIS) {
                sessionDelegates.remove(e.getKey());
                lastAccess.remove(e.getKey());
                evicted++;
            }
        }
        if (evicted > 0) {
            log.info("AI Provider 会话状态定时清理：剔除 {} 个 1 小时未活跃会话", evicted);
        }
        return evicted;
    }
}
