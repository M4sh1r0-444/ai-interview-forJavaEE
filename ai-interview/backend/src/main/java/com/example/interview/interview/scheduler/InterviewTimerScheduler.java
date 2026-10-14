package com.example.interview.interview.scheduler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.assessment.service.ReportService;
import com.example.interview.interview.entity.InterviewSession;
import com.example.interview.interview.mapper.InterviewSessionMapper;
import com.example.interview.interview.statemachine.InterviewStatus;
import com.example.interview.interview.statemachine.InterviewStateMachine;
import com.example.interview.interview.websocket.InterviewWebSocketHandler;
import com.example.interview.interview.websocket.WsMessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 面试定时器（计划 §4.3 / §5.6 TIMER / §6.1 TIMEOUT）：
 * 每秒扫描内存注册表，剩余时间按粒度推送（常规 30s，最后 60s 内 10s，可配）；
 * 到期自动转 TIMEOUT。durationMinutes 未落库（9 表约束），注册表在内存，
 * 重启后按"启动时间 + 最大时长"上界兜底恢复（决策 D13，见进度记录）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewTimerScheduler {

    private static class Entry {
        private volatile LocalDateTime deadline;
        private volatile long lastPushedRemain = -1;

        private Entry(LocalDateTime deadline) {
            this.deadline = deadline;
        }
    }

    private final Map<Long, Entry> timers = new ConcurrentHashMap<>();

    private final InterviewSessionMapper sessionMapper;
    private final InterviewStateMachine stateMachine;
    private final InterviewWebSocketHandler wsHandler;
    private final ReportService reportService;

    @Value("${app.interview.timer-push-seconds:30}")
    private int pushSeconds;
    @Value("${app.interview.timer-final-push-seconds:10}")
    private int finalPushSeconds;
    @Value("${app.interview.timer-final-window-seconds:60}")
    private int finalWindowSeconds;
    @Value("${app.interview.max-duration-minutes:60}")
    private int maxDurationMinutes;

    /** start 时注册会话截止时间；重复注册覆盖（测试可用过去时间强制到期）。 */
    public void register(Long sessionId, LocalDateTime deadline) {
        timers.put(sessionId, new Entry(deadline));
        log.info("定时器注册 session={} 截止={}", sessionId, deadline);
    }

    /** 会话终态（FINISHED/ABORTED/TIMEOUT）后注销。 */
    public void cancel(Long sessionId) {
        if (timers.remove(sessionId) != null) {
            log.info("定时器注销 session={}", sessionId);
        }
    }

    /** 剩余秒数；未注册返回 null（供详情接口恢复前端倒计时）。 */
    public Long remainSeconds(Long sessionId) {
        Entry e = timers.get(sessionId);
        if (e == null) {
            return null;
        }
        return Math.max(0, Duration.between(LocalDateTime.now(), e.deadline).getSeconds());
    }

    @Scheduled(fixedDelay = 1000)
    void tick() {
        LocalDateTime now = LocalDateTime.now();
        timers.forEach((sessionId, entry) -> {
            long remain = Duration.between(now, entry.deadline).getSeconds();
            if (remain <= 0) {
                if (timers.remove(sessionId) != null) {
                    expire(sessionId);
                }
                return;
            }
            long granularity = remain <= finalWindowSeconds ? finalPushSeconds : pushSeconds;
            if (entry.lastPushedRemain < 0 || entry.lastPushedRemain - remain >= granularity) {
                entry.lastPushedRemain = remain;
                wsHandler.push(sessionId, WsMessageType.TIMER, Map.of("remainSeconds", remain));
            }
        });
    }

    /** 到期收卷：多来源条件更新 → TIMEOUT，推送剩余 0 与终态进度。 */
    private void expire(Long sessionId) {
        boolean ok;
        try {
            stateMachine.transitionFromAnyOf(sessionId,
                    InterviewStatus.sourcesOf(InterviewStatus.TIMEOUT), InterviewStatus.TIMEOUT);
            ok = true;
        } catch (Exception e) {
            // 会话已终态（如刚 FINISHED/ABORTED），静默
            log.info("到期转 TIMEOUT 未生效 session={}: {}", sessionId, e.getMessage());
            ok = false;
        }
        if (ok) {
            wsHandler.push(sessionId, WsMessageType.TIMER, Map.of("remainSeconds", 0));
            InterviewSession s = sessionMapper.selectById(sessionId);
            if (s != null) {
                wsHandler.push(sessionId, WsMessageType.PROGRESS, Map.of(
                        "status", InterviewStatus.TIMEOUT.name(),
                        "currentRound", s.getCurrentRound(),
                        "plannedRounds", s.getPlannedRounds()));
            }
            // 自动收卷走 finish 报告流程（计划 §6.1）：TIMEOUT 同样生成报告（M3-3.3）
            reportService.generateAsync(sessionId);
            log.info("面试到期自动收卷 session={} → TIMEOUT，报告生成已触发", sessionId);
        }
    }

    /**
     * 重启恢复：内存定时器丢失后，对仍在进行中的会话按 started_at + 最大时长上界重挂；
     * 已超上界的直接收卷（M2-2.7 到期 TIMEOUT 的兜底路径）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        List<InterviewSession> active = sessionMapper.selectList(Wrappers.<InterviewSession>lambdaQuery()
                .in(InterviewSession::getStatus,
                        InterviewStatus.WAITING_USER.name(), InterviewStatus.PROCESSING_AI.name()));
        LocalDateTime now = LocalDateTime.now();
        for (InterviewSession s : active) {
            if (s.getStartedAt() == null) {
                continue;
            }
            LocalDateTime upperBound = s.getStartedAt().plusMinutes(maxDurationMinutes);
            if (upperBound.isBefore(now)) {
                timers.put(s.getId(), new Entry(now.minusSeconds(1)));
                log.warn("启动恢复：会话已超最大时长，收卷 session={}", s.getId());
            } else {
                timers.put(s.getId(), new Entry(upperBound));
                log.warn("启动恢复：按最大时长上界重挂定时器 session={} 截止={}", s.getId(), upperBound);
            }
        }
    }
}
