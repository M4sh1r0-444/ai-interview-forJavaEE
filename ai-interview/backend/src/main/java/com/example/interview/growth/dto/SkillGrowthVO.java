package com.example.interview.growth.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 成长曲线视图（计划 §5.4 #18）：
 * {@code [{skillCode, skillName, records:[{evaluatedAt, score}], current}]}。
 * records 按 evaluated_at 升序（计划 §3.8：追加式记录即成长曲线数据源）。
 */
@Data
@Builder
public class SkillGrowthVO {

    private String skillCode;
    private String skillName;
    private List<GrowthPoint> records;
    /** 当前掌握度 = 最新一条记录的 score（计划 §3.8） */
    private BigDecimal current;

    @Data
    @Builder
    public static class GrowthPoint {
        private LocalDateTime evaluatedAt;
        private BigDecimal score;
    }
}
