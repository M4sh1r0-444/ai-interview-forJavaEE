package com.example.interview.assessment.service;

/**
 * 报告生成完成事件（计划 §6.6 finish 编排：报告落库 → 掌握度更新 →
 * 推荐缓存预生成 → WS 推 REPORT_READY）。由 ReportService 发布，
 * growth 域监听预生成推荐缓存（M5-5.3）。
 */
public record ReportGeneratedEvent(Long sessionId, Long userId, Long positionId) {
}
