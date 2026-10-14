package com.example.interview.assessment.service;

import com.example.interview.ai.model.AiTurnEvaluation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 逐题评分的进程内暂存（9 表约束下评分结果不入库）：
 * TurnEngine 每受理一次回答追加该轮最新快照（按回答顺序累积，供应变得分衡量回升幅度）；
 * ReportService 生成/读取报告时取用，缺漏轮次用 AiProvider 确定性重算（Mock 无随机）。
 * 重启丢失可接受——重算路径保证报告可重建；真实 Provider 的 Redis 缓存排期 M4。
 */
@Component
public class TurnEvaluationStore {

    private final Map<Long, Map<Integer, List<AiTurnEvaluation>>> store = new ConcurrentHashMap<>();

    /** 追加一轮中某次回答后的评分快照。 */
    public void append(Long sessionId, int roundNo, AiTurnEvaluation evaluation) {
        store.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(roundNo, k -> new ArrayList<>())
                .add(evaluation);
    }

    /** 某轮全部评分快照（按回答顺序）；无则 null。 */
    public List<AiTurnEvaluation> get(Long sessionId, int roundNo) {
        Map<Integer, List<AiTurnEvaluation>> rounds = store.get(sessionId);
        return rounds != null ? rounds.get(roundNo) : null;
    }

    /** 会话全部已评分轮次（round 升序）；空返回空 Map。 */
    public Map<Integer, List<AiTurnEvaluation>> allRounds(Long sessionId) {
        Map<Integer, List<AiTurnEvaluation>> rounds = store.get(sessionId);
        if (rounds == null) {
            return Map.of();
        }
        Map<Integer, List<AiTurnEvaluation>> copy = new ConcurrentHashMap<>();
        rounds.forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
        return copy;
    }

    /** 报告生成落库后清理，防长期运行内存增长。 */
    public void clear(Long sessionId) {
        store.remove(sessionId);
    }
}
