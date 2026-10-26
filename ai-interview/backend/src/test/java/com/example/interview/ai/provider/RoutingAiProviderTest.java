package com.example.interview.ai.provider;

import com.example.interview.aiconfig.service.UserAiConfigService;
import com.example.interview.ai.model.AiEndpoint;
import com.example.interview.ai.model.AiNextQuestionResult;
import com.example.interview.ai.model.NextQuestionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** V9 RoutingAiProvider：用户配置命中 → 用户实例；无 userId/无配置/解密失败 → 平台默认；释放与淘汰。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoutingAiProviderTest {

    @Mock
    private AiProvider defaultProvider;
    @Mock
    private AiProviderFactory factory;
    @Mock
    private UserAiConfigService userAiConfigService;
    /** factory.get 返回具体类 OpenAiCompatibleProvider，mock 类型须一致 */
    @Mock
    private OpenAiCompatibleProvider userProvider;

    private RoutingAiProvider routing;

    private NextQuestionContext ctx(Long sessionId, Long userId) {
        return NextQuestionContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .questionContent("谈谈 JVM 调优")
                .requestedAction("FOLLOW_UP")
                .build();
    }

    // ==================== 路由决策 ====================

    @Test
    void routesToUserProviderWhenConfigExists() {
        routing = new RoutingAiProvider(defaultProvider, factory, userAiConfigService);
        AiEndpoint endpoint = new AiEndpoint("https://u.com/v1", "sk-u", "qwen-max");
        when(userAiConfigService.resolveEndpoint(7L)).thenReturn(endpoint);
        when(factory.get(endpoint)).thenReturn(userProvider);
        when(userProvider.nextQuestion(any())).thenReturn(new AiNextQuestionResult());

        routing.nextQuestion(ctx(5L, 7L));
        verify(userProvider).nextQuestion(any(NextQuestionContext.class));
        verify(defaultProvider, never()).nextQuestion(any());
    }

    @Test
    void fallsBackToDefaultWithoutUserIdOrConfig() {
        routing = new RoutingAiProvider(defaultProvider, factory, userAiConfigService);
        when(userAiConfigService.resolveEndpoint(8L)).thenReturn(null);   // 无配置/解密失败
        when(defaultProvider.nextQuestion(any())).thenReturn(new AiNextQuestionResult());

        routing.nextQuestion(ctx(6L, null));      // ctx 未带 userId（防御）
        routing.nextQuestion(ctx(6L, 8L));       // 有 userId 但无配置
        verify(defaultProvider, org.mockito.Mockito.times(2)).nextQuestion(any());
        verify(factory, never()).get(any());
    }

    // ==================== releaseSession：双透传 ====================

    @Test
    void releaseSessionForwardsToDefaultAndRoutedDelegate() {
        routing = new RoutingAiProvider(defaultProvider, factory, userAiConfigService);
        AiEndpoint endpoint = new AiEndpoint("https://u.com/v1", "sk-u", "qwen-max");
        when(userAiConfigService.resolveEndpoint(7L)).thenReturn(endpoint);
        when(factory.get(endpoint)).thenReturn(userProvider);
        when(userProvider.nextQuestion(any())).thenReturn(new AiNextQuestionResult());

        routing.nextQuestion(ctx(5L, 7L));          // 会话 5 路由到用户实例
        routing.releaseSession(5L);
        verify(defaultProvider).releaseSession(5L);
        verify(userProvider).releaseSession(5L);

        // delegate 已随 release 移除：再次释放只打默认实例
        routing.releaseSession(5L);
        verify(defaultProvider, org.mockito.Mockito.times(2)).releaseSession(5L);
        verify(userProvider, org.mockito.Mockito.times(1)).releaseSession(5L);
    }

    // ==================== 会话状态淘汰 ====================

    @Test
    void evictStaleSessionsRemovesIdleEntries() {
        routing = new RoutingAiProvider(defaultProvider, factory, userAiConfigService);
        AiEndpoint endpoint = new AiEndpoint("https://u.com/v1", "sk-u", "qwen-max");
        when(userAiConfigService.resolveEndpoint(7L)).thenReturn(endpoint);
        when(factory.get(endpoint)).thenReturn(userProvider);
        when(userProvider.nextQuestion(any())).thenReturn(new AiNextQuestionResult());

        long now = System.currentTimeMillis();
        routing.nextQuestion(ctx(5L, 7L));
        assertThat(routing.evictStaleSessions(now)).isZero();          // 刚访问不淘汰
        assertThat(routing.evictStaleSessions(now + 2 * 60 * 60_000L)).isEqualTo(1);  // 2h 后淘汰

        // 淘汰后 release 只打默认实例（delegate 已清理）
        routing.releaseSession(5L);
        verify(userProvider, never()).releaseSession(5L);
    }
}
