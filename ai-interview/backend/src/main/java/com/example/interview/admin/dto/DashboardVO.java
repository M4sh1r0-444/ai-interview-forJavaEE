package com.example.interview.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端仪表盘视图（计划 §5.5 #27）：用户数/场次/题量/平均分/近 7 日趋势。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardVO {

    private Long userCount;
    /** 已 start 过的面试场次（started_at 非空） */
    private Long sessionCount;
    private Long questionCount;
    /** 全部已出分场次的平均总分；无出分场次为 null */
    private BigDecimal avgScore;
    /** 近 7 日（含今日）按日趋势，日期升序 */
    private List<DailyItem> recent7Days;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyItem {
        /** yyyy-MM-dd */
        private String date;
        /** 当日开始的面试场次 */
        private Long sessionCount;
        /** 当日出分场次的平均总分（可为 null） */
        private BigDecimal avgScore;
    }
}
