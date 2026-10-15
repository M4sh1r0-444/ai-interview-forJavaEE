package com.example.interview.growth.service;

import com.example.interview.assessment.service.ReportGeneratedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 推荐缓存预热器：报告生成完成后预生成该用户的推荐缓存
 * （Redis TTL 10 分钟，计划 §6.6/§6.7）。监听在报告异步线程内
 * 同步执行，不阻塞请求线程；失败不影响报告主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationCacheWarmer {

    private final RecommendationService recommendationService;

    @EventListener
    public void onReportGenerated(ReportGeneratedEvent event) {
        try {
            recommendationService.warmFor(event.userId(), event.positionId());
        } catch (Exception e) {
            log.warn("推荐缓存预生成失败 session={} userId={}: {}",
                    event.sessionId(), event.userId(), e.getMessage());
        }
    }
}
