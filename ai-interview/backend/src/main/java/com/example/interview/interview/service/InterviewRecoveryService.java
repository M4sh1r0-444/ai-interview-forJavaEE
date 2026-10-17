package com.example.interview.interview.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.interview.entity.InterviewSession;
import com.example.interview.interview.mapper.InterviewSessionMapper;
import com.example.interview.interview.statemachine.InterviewStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 重启恢复处理中会话（FIX-08）：进程在 PROCESSING_AI 中途退出后，重启时对卡死会话
 * 重新投递 TurnEngine.processTurn——重跑成功即正常产出追问/下一题；重跑失败由
 * TurnEngine.recover 回退 WAITING_USER 并推 ERROR，用户可重试，不再静默挂起。
 * 独立组件避免 Scheduler↔TurnEngine 循环依赖；当前 Compose 单实例部署无需分布式锁，
 * 多实例时需保证仅单实例执行恢复（文档注明）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewRecoveryService {

    private final InterviewSessionMapper sessionMapper;
    private final TurnEngine turnEngine;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverProcessingSessions() {
        List<InterviewSession> stuck = sessionMapper.selectList(
                Wrappers.<InterviewSession>lambdaQuery()
                        .eq(InterviewSession::getStatus, InterviewStatus.PROCESSING_AI.name()));
        if (stuck.isEmpty()) {
            return;
        }
        for (InterviewSession s : stuck) {
            if (s.getStartedAt() == null) {
                continue; // 从未 start（不应处于 PROCESSING_AI），跳过
            }
            log.warn("启动恢复：重投处理中会话 session={} round={}", s.getId(), s.getCurrentRound());
            turnEngine.processTurn(s.getId());
        }
    }
}
