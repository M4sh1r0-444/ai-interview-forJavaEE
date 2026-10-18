package com.example.interview.assessment.service;

import com.example.interview.ai.model.AiTurnEvaluation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 逐题评分的进程内暂存（9 表约束下评分结果不入库）：
 * TurnEngine 每受理一次回答追加该轮最新快照（按回答顺序累积，供应变得分衡量回升幅度）；
 * ReportService 生成/读取报告时取用，缺漏轮次用 AiProvider 确定性重算（Mock 无随机）。
 * 重启丢失可接受——重算路径保证报告可重建；真实 Provider 的 Redis 缓存排期 M4。
 *
 * <p>内存边界（FIX-16）：报告生成/读取完成、会话中止时 clear；另有定时清理
 * （每 10 分钟清 2 小时未访问的会话），长期运行不无界增长。</p>
 */
@Slf4j
@Component
public class TurnEvaluationStore {

    /** 会话空闲阈值：超过该时长未访问的评分快照视为泄漏，定时清理。 */
    static final long IDLE_MILLIS = Duration.ofHours(2).toMillis();
    private static final long EVICT_INTERVAL_MILLIS = 600_000L; // 10min（@Scheduled 需编译期常量）

    private final Map<Long, Map<Integer, List<AiTurnEvaluation>>> store = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastAccess = new ConcurrentHashMap<>();

    /** 追加一轮中某次回答后的评分快照。 */
    public void append(Long sessionId, int roundNo, AiTurnEvaluation evaluation) {
        store.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(roundNo, k -> new ArrayList<>())
                .add(evaluation);
        touch(sessionId);
    }

    /** 某轮全部评分快照（按回答顺序）；无则 null（不复活已清理会话的访问记录）。 */
    public List<AiTurnEvaluation> get(Long sessionId, int roundNo) {
        Map<Integer, List<AiTurnEvaluation>> rounds = store.get(sessionId);
        if (rounds == null) {
            return null;
        }
        touch(sessionId);
        return rounds.get(roundNo);
    }

    /** 会话全部已评分轮次（round 升序）；空返回空 Map。 */
    public Map<Integer, List<AiTurnEvaluation>> allRounds(Long sessionId) {
        Map<Integer, List<AiTurnEvaluation>> rounds = store.get(sessionId);
        if (rounds == null) {
            return Map.of();
        }
        touch(sessionId);
        Map<Integer, List<AiTurnEvaluation>> copy = new ConcurrentHashMap<>();
        rounds.forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
        return copy;
    }

    /** 报告生成落库/会话中止后清理，防长期运行内存增长。 */
    public void clear(Long sessionId) {
        store.remove(sessionId);
        lastAccess.remove(sessionId);
    }

    /** 当前暂存会话数（测试与自检观测用）。 */
    public int size() {
        return store.size();
    }

    /** 定时清理长时间未访问的会话快照（FIX-16：abort/报告失败等路径的兜底）。 */
    @Scheduled(fixedDelay = EVICT_INTERVAL_MILLIS)
    public void evictIdleSessions() {
        evictIdleSessions(System.currentTimeMillis());
    }

    int evictIdleSessions(long nowMillis) {
        int evicted = 0;
        for (Map.Entry<Long, Long> e : lastAccess.entrySet()) {
            if (nowMillis - e.getValue() > IDLE_MILLIS) {
                store.remove(e.getKey());
                lastAccess.remove(e.getKey());
                evicted++;
            }
        }
        if (evicted > 0) {
            log.info("评分暂存定时清理：剔除 {} 个 2 小时未访问会话，剩余 {}", evicted, store.size());
        }
        return evicted;
    }

    private void touch(Long sessionId) {
        lastAccess.put(sessionId, System.currentTimeMillis());
    }
}
