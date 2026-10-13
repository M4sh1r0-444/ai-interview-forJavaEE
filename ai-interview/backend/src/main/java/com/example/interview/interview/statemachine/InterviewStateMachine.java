package com.example.interview.interview.statemachine;

import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import com.example.interview.interview.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 状态机服务：所有状态转换经此走条件更新，影响行数=0 视为并发冲突 → 10005（计划 §6.1）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewStateMachine {

    private final InterviewSessionMapper sessionMapper;

    /** 尝试单来源转换；返回 false 表示当前状态已被并发修改。 */
    public boolean tryTransition(Long sessionId, InterviewStatus from, InterviewStatus to) {
        from.assertCanTransitionTo(to);
        int rows = sessionMapper.casStatus(sessionId, from.name(), to.name());
        if (rows > 0) {
            log.info("会话状态转换 session={} {} -> {}", sessionId, from, to);
        }
        return rows > 0;
    }

    /** 单来源转换，冲突抛 10005。 */
    public void transition(Long sessionId, InterviewStatus from, InterviewStatus to) {
        if (!tryTransition(sessionId, from, to)) {
            throw statusConflict(sessionId, from, to);
        }
    }

    /** 多来源转换（abort/TIMEOUT 等），任一来源命中即成功；全部未命中抛 10005。 */
    public void transitionFromAnyOf(Long sessionId, Set<InterviewStatus> sources, InterviewStatus to) {
        for (InterviewStatus from : sources) {
            if (from.canTransitionTo(to) && tryTransition(sessionId, from, to)) {
                return;
            }
        }
        throw statusConflict(sessionId, null, to);
    }

    private BizException statusConflict(Long sessionId, InterviewStatus from, InterviewStatus to) {
        log.info("状态冲突 session={} 期望来源={} 目标={}", sessionId, from, to);
        return new BizException(ResultCode.STATUS_CONFLICT,
                "面试状态已变化，请刷新会话后重试（期望 " + from + " -> " + to + "）");
    }
}
