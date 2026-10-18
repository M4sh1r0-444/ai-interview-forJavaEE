package com.example.interview.assessment.service;

import com.example.interview.ai.model.AiTurnEvaluation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIX-16 评分暂存内存边界：clear 后不残留；定时清理剔除 2 小时未访问会话、
 * 保留活跃会话（append/get 触达视为活跃）。
 */
class TurnEvaluationStoreTest {

    private final TurnEvaluationStore store = new TurnEvaluationStore();

    private AiTurnEvaluation eval(double score) {
        AiTurnEvaluation e = new AiTurnEvaluation();
        e.setScores(Map.of("technical", (int) score));
        return e;
    }

    @Test
    void appendAndGetRoundSnapshots() {
        store.append(1L, 0, eval(70));
        store.append(1L, 0, eval(80));
        assertThat(store.get(1L, 0)).hasSize(2);
        assertThat(store.get(1L, 1)).isNull();
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void clearRemovesSessionAndAccessRecord() {
        store.append(1L, 0, eval(70));
        store.clear(1L);
        assertThat(store.get(1L, 0)).isNull();
        assertThat(store.size()).isZero();
        // clear 后再淘汰不应误报（访问记录一并移除）
        assertThat(store.evictIdleSessions(Long.MAX_VALUE)).isZero();
    }

    @Test
    void evictIdleSessionsRemovesOnlyIdleOnes() {
        long now = System.currentTimeMillis();
        store.append(10L, 0, eval(70));           // 刚写入，活跃
        store.append(20L, 0, eval(70));           // 将被手工置为空闲
        store.append(30L, 0, eval(70));           // get 触达后仍活跃
        // 模拟 20 号会话 3 小时未访问
        try {
            java.lang.reflect.Field f = TurnEvaluationStore.class.getDeclaredField("lastAccess");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Long, Long> lastAccess = (Map<Long, Long>) f.get(store);
            lastAccess.put(20L, now - TurnEvaluationStore.IDLE_MILLIS - 1);
            lastAccess.put(10L, now - TurnEvaluationStore.IDLE_MILLIS + 60_000); // 2h 内
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        int evicted = store.evictIdleSessions(now);
        assertThat(evicted).isEqualTo(1);
        assertThat(store.get(20L, 0)).isNull();
        assertThat(store.get(10L, 0)).isNotNull();
        assertThat(store.get(30L, 0)).isNotNull();

        // 全部置为空闲后一次性清理
        try {
            java.lang.reflect.Field f = TurnEvaluationStore.class.getDeclaredField("lastAccess");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Long, Long> lastAccess = (Map<Long, Long>) f.get(store);
            lastAccess.replaceAll((k, v) -> now - TurnEvaluationStore.IDLE_MILLIS - 1);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        assertThat(store.evictIdleSessions(now)).isEqualTo(2);
        assertThat(store.size()).isZero();
    }
}
