package com.example.interview.growth.engine;

import com.example.interview.growth.entity.AbilityRecord;
import com.example.interview.growth.mapper.AbilityRecordMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 计划 §8.1 MasteryUpdaterTest：旧 70 本次 90 → 76；首次记录 = 本次得分；
 * 加权聚合与幂等落库（纯逻辑 + Mockito，不起 Spring 容器）。
 */
class MasteryUpdaterTest {

    private final AbilityRecordMapper mapper = mock(AbilityRecordMapper.class);
    private final MasteryUpdater updater = new MasteryUpdater(mapper);

    // ==================== 公式（方案 §8.1 / 计划 §3.8） ====================

    @Test
    void firstRecordTakesCurrentScore() {
        assertThat(updater.nextMastery(null, 90)).isEqualTo(90.0);
        assertThat(updater.nextMastery(null, 66.67)).isEqualTo(66.67);
    }

    @Test
    void old70Current90Becomes76() {
        assertThat(updater.nextMastery(70.0, 90.0)).isEqualTo(76.0);
    }

    @Test
    void formulaClampsTo0And100() {
        assertThat(updater.nextMastery(null, 120)).isEqualTo(100.0);
        assertThat(updater.nextMastery(95.0, 200)).isEqualTo(96.5);
        assertThat(updater.nextMastery(-30.0, -50)).isEqualTo(0.0);
    }

    // ==================== 会话技能得分聚合（计划 §6.7） ====================

    @Test
    void sessionSkillScoresWeightByDifficulty() {
        // 同技能两轮：难度4 权重1.2 得80、难度2 权重0.8 得60 → (96+48)/2.0 = 72
        Map<String, Double> scores = updater.sessionSkillScores(List.of(
                new MasteryUpdater.SkillTurn(List.of("redis_mq"), 1.2, 80),
                new MasteryUpdater.SkillTurn(List.of("redis_mq"), 0.8, 60)));
        assertThat(scores).containsOnlyKeys("redis_mq");
        assertThat(scores.get("redis_mq")).isCloseTo(72.0, within(0.001));
    }

    @Test
    void multiSkillTagCountsEachSkillAndIgnoresInvalidTurns() {
        Map<String, Double> scores = updater.sessionSkillScores(List.of(
                new MasteryUpdater.SkillTurn(List.of("java_basics", "spring"), 1.0, 70),
                new MasteryUpdater.SkillTurn(List.of(), 1.0, 90),
                new MasteryUpdater.SkillTurn(List.of("mysql"), 0, 90),
                new MasteryUpdater.SkillTurn(null, 1.0, 90)));
        assertThat(scores).containsOnlyKeys("java_basics", "spring");
        assertThat(scores.get("java_basics")).isEqualTo(70.0);
        assertThat(scores.get("spring")).isEqualTo(70.0);
    }

    @Test
    void emptyTurnsYieldEmptyScores() {
        assertThat(updater.sessionSkillScores(List.of())).isEmpty();
        assertThat(updater.sessionSkillScores(null)).isEmpty();
    }

    // ==================== 落库（只追加 + 幂等） ====================

    @Test
    void applyForSessionFirstTimeTakesCurrentScore() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.selectOne(any())).thenReturn(null);

        updater.applyForSession(9L, 1L, 100L, Map.of("mysql", 88.5));

        ArgumentCaptor<AbilityRecord> captor = ArgumentCaptor.forClass(AbilityRecord.class);
        verify(mapper).insert(captor.capture());
        AbilityRecord inserted = captor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(9L);
        assertThat(inserted.getPositionId()).isEqualTo(1L);
        assertThat(inserted.getSkillCode()).isEqualTo("mysql");
        assertThat(inserted.getSourceSessionId()).isEqualTo(100L);
        assertThat(inserted.getScore()).isEqualByComparingTo(BigDecimal.valueOf(88.5));
    }

    @Test
    void applyForSessionWithHistoryApplies7And3Formula() {
        AbilityRecord previous = new AbilityRecord();
        previous.setScore(BigDecimal.valueOf(70));
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.selectOne(any())).thenReturn(previous);

        updater.applyForSession(9L, 1L, 100L, Map.of("mysql", 90.0));

        ArgumentCaptor<AbilityRecord> captor = ArgumentCaptor.forClass(AbilityRecord.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getScore()).isEqualByComparingTo(BigDecimal.valueOf(76.0));
    }

    @Test
    void applyForSessionIsIdempotentPerSession() {
        when(mapper.selectCount(any())).thenReturn(2L);

        updater.applyForSession(9L, 1L, 100L, Map.of("mysql", 90.0));

        verify(mapper, never()).insert(any(AbilityRecord.class));
    }

    @Test
    void applyForSessionWithNoSkillScoresIsNoOp() {
        updater.applyForSession(9L, 1L, 100L, Map.of());

        verify(mapper, never()).selectCount(any());
        verify(mapper, never()).insert(any(AbilityRecord.class));
    }

    // ==================== 当前掌握度查询 ====================

    @Test
    void currentMasteryReturnsLatestOrNull() {
        assertThat(updater.currentMastery(9L, 1L, "mysql")).isNull();

        AbilityRecord latest = new AbilityRecord();
        latest.setScore(BigDecimal.valueOf(64.5));
        when(mapper.selectOne(any())).thenReturn(latest);
        assertThat(updater.currentMastery(9L, 1L, "mysql")).isEqualTo(64.5);
    }
}
