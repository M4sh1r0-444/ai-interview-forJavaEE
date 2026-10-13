package com.example.interview.interview.statemachine;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 面试状态机（计划 §6.1）。合法转换表内置于枚举，数据库 status 列为唯一事实源。
 *
 * <pre>
 * CREATED ──start──▶ RUNNING ──发出首题──▶ WAITING_USER
 * WAITING_USER ──收到回答──▶ PROCESSING_AI ──产出追问/下一题──▶ WAITING_USER
 * PROCESSING_AI ──轮次用完──▶ FINISHED
 * RUNNING/WAITING_USER/PROCESSING_AI ──用户中止──▶ ABORTED
 * WAITING_USER/PROCESSING_AI ──定时器到期──▶ TIMEOUT
 * </pre>
 */
public enum InterviewStatus {
    CREATED, RUNNING, WAITING_USER, PROCESSING_AI, FINISHED, ABORTED, TIMEOUT;

    private static final Map<InterviewStatus, Set<InterviewStatus>> ALLOWED = Map.of(
            CREATED, Set.of(RUNNING, ABORTED),
            RUNNING, Set.of(WAITING_USER, ABORTED),
            WAITING_USER, Set.of(PROCESSING_AI, ABORTED, TIMEOUT, FINISHED),
            PROCESSING_AI, Set.of(WAITING_USER, FINISHED, ABORTED, TIMEOUT));
    // FINISHED / ABORTED / TIMEOUT 为终态

    /** 该状态是否可单向流转到目标状态。 */
    public boolean canTransitionTo(InterviewStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** 编程期非法转换直接失败（并发冲突由 InterviewStateMachine 映射为 10005）。 */
    public void assertCanTransitionTo(InterviewStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("非法状态转换: " + this + " -> " + target);
        }
    }

    /** 可流转到目标状态的全部来源状态（用于 abort/timeout 等多来源转换）。 */
    public static Set<InterviewStatus> sourcesOf(InterviewStatus target) {
        return Arrays.stream(values())
                .filter(s -> s.canTransitionTo(target))
                .collect(Collectors.toSet());
    }

    public boolean isTerminal() {
        return this == FINISHED || this == ABORTED || this == TIMEOUT;
    }
}
