package com.example.interview.growth.engine;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.growth.entity.AbilityRecord;
import com.example.interview.growth.mapper.AbilityRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 掌握度更新引擎（计划 §3.8 / §6.7，方案 §8.1）。
 *
 * <pre>
 * 会话技能得分 = 会话内相关题目得分的难度加权均分（难度权重 1~5 → 0.6/0.8/1.0/1.2/1.4）
 * 新掌握度 = 首次 ? 本次得分 : 旧×0.7 + 本次×0.3     （方案 §8.1）
 * </pre>
 *
 * ability_record 只追加不更新；applyForSession 以 source_session_id 幂等
 * （报告重试/并发生成不会重复折算），供 ReportService 在报告落库后调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasteryUpdater {

    public static final double OLD_WEIGHT = 0.7;
    public static final double CURRENT_WEIGHT = 0.3;

    private final AbilityRecordMapper abilityRecordMapper;

    /** 一轮的技能命中与得分（ReportService 聚合产出）。 */
    public record SkillTurn(List<String> skillTags, double weight, double score) {
    }

    /** 会话 → skill_code → 难度加权均分；题目多技能标签时各技能均计该轮得分。 */
    public Map<String, Double> sessionSkillScores(List<SkillTurn> turns) {
        Map<String, double[]> acc = new LinkedHashMap<>();
        if (turns != null) {
            for (SkillTurn turn : turns) {
                if (turn == null || turn.skillTags() == null || turn.skillTags().isEmpty()
                        || turn.weight() <= 0) {
                    continue;
                }
                for (String skill : turn.skillTags()) {
                    if (skill == null || skill.isBlank()) {
                        continue;
                    }
                    double[] sums = acc.computeIfAbsent(skill, k -> new double[2]);
                    sums[0] += clamp(turn.score()) * turn.weight();
                    sums[1] += turn.weight();
                }
            }
        }
        Map<String, Double> result = new LinkedHashMap<>();
        acc.forEach((skill, sums) -> result.put(skill, round2(sums[1] > 0 ? sums[0] / sums[1] : 0)));
        return result;
    }

    /** 新掌握度：首次记录直取本次得分；此后 旧×0.7 + 本次×0.3（方案 §8.1）。 */
    public double nextMastery(Double previous, double current) {
        double next = previous == null ? clamp(current)
                : clamp(previous) * OLD_WEIGHT + clamp(current) * CURRENT_WEIGHT;
        return round2(clamp(next));
    }

    /**
     * 报告落库后追加本次面试的能力记录（计划 §6.6 finish 编排的掌握度环节）。
     * 该会话已有记录时幂等跳过；每次面试对每个命中技能插入一条。
     */
    @Transactional
    public void applyForSession(Long userId, Long positionId, Long sessionId,
                                Map<String, Double> skillScores) {
        if (skillScores == null || skillScores.isEmpty()) {
            return;
        }
        Long existing = abilityRecordMapper.selectCount(Wrappers.<AbilityRecord>lambdaQuery()
                .eq(AbilityRecord::getSourceSessionId, sessionId));
        if (existing != null && existing > 0) {
            log.info("掌握度已更新过，幂等跳过 session={} records={}", sessionId, existing);
            return;
        }
        for (Map.Entry<String, Double> e : skillScores.entrySet()) {
            AbilityRecord record = new AbilityRecord();
            record.setUserId(userId);
            record.setPositionId(positionId);
            record.setSkillCode(e.getKey());
            record.setSourceSessionId(sessionId);
            record.setScore(BigDecimal.valueOf(nextMastery(currentMastery(userId, positionId, e.getKey()),
                    e.getValue())).setScale(2, RoundingMode.HALF_UP));
            abilityRecordMapper.insert(record);
        }
        log.info("掌握度更新完成 session={} userId={} skills={}", sessionId, userId, skillScores.keySet());
    }

    /** (user, position, skill) 当前掌握度（最新一条 score）；无记录返回 null。 */
    public Double currentMastery(Long userId, Long positionId, String skillCode) {
        AbilityRecord latest = abilityRecordMapper.selectOne(Wrappers.<AbilityRecord>lambdaQuery()
                .eq(AbilityRecord::getUserId, userId)
                .eq(AbilityRecord::getPositionId, positionId)
                .eq(AbilityRecord::getSkillCode, skillCode)
                .orderByDesc(AbilityRecord::getEvaluatedAt)
                .orderByDesc(AbilityRecord::getId)
                .last("LIMIT 1"));
        return latest != null ? latest.getScore().doubleValue() : null;
    }

    private double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
