package com.example.interview.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.interview.admin.dto.DashboardVO;
import com.example.interview.auth.mapper.UserMapper;
import com.example.interview.interview.entity.InterviewSession;
import com.example.interview.interview.mapper.InterviewSessionMapper;
import com.example.interview.question.entity.Question;
import com.example.interview.question.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端仪表盘（计划 §5.5 #27）：用户数/场次/题量/平均分/近 7 日趋势。
 * 聚合走 MyBatis-Plus selectMaps（静态列名表达式，无拼接注入面）。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserMapper userMapper;
    private final QuestionMapper questionMapper;
    private final InterviewSessionMapper sessionMapper;

    public DashboardVO dashboard() {
        LocalDateTime since = LocalDate.now().minusDays(6).atStartOfDay();
        Map<String, BigDecimal> avgByDay = avgScoreByDay(since);
        Map<String, Long> sessionsByDay = sessionCountByDay(since);

        List<DashboardVO.DailyItem> recent = LocalDate.now().minusDays(6).datesUntil(LocalDate.now().plusDays(1))
                .map(day -> {
                    String key = DATE.format(day);
                    return DashboardVO.DailyItem.builder()
                            .date(key)
                            .sessionCount(sessionsByDay.getOrDefault(key, 0L))
                            .avgScore(avgByDay.get(key))
                            .build();
                })
                .toList();

        return DashboardVO.builder()
                .userCount(userMapper.selectCount(null))
                // 场次=真正开始过的面试（started_at 非空），CREATED 草稿不计
                .sessionCount(sessionMapper.selectCount(new QueryWrapper<InterviewSession>()
                        .isNotNull("started_at")))
                .questionCount(questionMapper.selectCount(null))
                .avgScore(overallAvgScore())
                .recent7Days(recent)
                .build();
    }

    /** 全部出分场次的平均总分（无出分场次返回 null）。 */
    private BigDecimal overallAvgScore() {
        List<Map<String, Object>> rows = sessionMapper.selectMaps(new QueryWrapper<InterviewSession>()
                .select("AVG(total_score) AS avg_score")
                .isNotNull("total_score"));
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).get("avg_score") == null) {
            return null;
        }
        return toDecimal(rows.get(0).get("avg_score"));
    }

    /** 近 7 日按 finished_at 日期的平均总分（当日无出分场次无条目）。 */
    private Map<String, BigDecimal> avgScoreByDay(LocalDateTime since) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (Map<String, Object> row : sessionMapper.selectMaps(new QueryWrapper<InterviewSession>()
                .select("DATE_FORMAT(finished_at, '%Y-%m-%d') AS stat_date", "AVG(total_score) AS avg_score")
                .isNotNull("finished_at")
                .isNotNull("total_score")
                .ge("finished_at", since)
                .groupBy("DATE_FORMAT(finished_at, '%Y-%m-%d')"))) {
            if (row.get("stat_date") != null && row.get("avg_score") != null) {
                result.put(String.valueOf(row.get("stat_date")), toDecimal(row.get("avg_score")));
            }
        }
        return result;
    }

    /** 近 7 日按 started_at 日期的开考场次。 */
    private Map<String, Long> sessionCountByDay(LocalDateTime since) {
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : sessionMapper.selectMaps(new QueryWrapper<InterviewSession>()
                .select("DATE_FORMAT(started_at, '%Y-%m-%d') AS stat_date", "COUNT(*) AS cnt")
                .isNotNull("started_at")
                .ge("started_at", since)
                .groupBy("DATE_FORMAT(started_at, '%Y-%m-%d')"))) {
            if (row.get("stat_date") != null && row.get("cnt") != null) {
                result.put(String.valueOf(row.get("stat_date")), ((Number) row.get("cnt")).longValue());
            }
        }
        return result;
    }

    private BigDecimal toDecimal(Object value) {
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }
}
