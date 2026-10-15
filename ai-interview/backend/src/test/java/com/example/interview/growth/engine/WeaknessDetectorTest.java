package com.example.interview.growth.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 计划 §8.1 WeaknessDetectorTest：四条规则各一用例，
 * 不误报（单次 55 分不触发规则①）。
 */
class WeaknessDetectorTest {

    private final WeaknessDetector detector = new WeaknessDetector();

    @Test
    void rule1_recentTwoBelow60Fires() {
        List<String> reasons = detector.detectSkill(
                new WeaknessDetector.SkillSnapshot(List.of(72.0, 55.0, 58.0), 70, 0));
        assertThat(reasons).anyMatch(r -> r.contains("均低于 60"));
    }

    @Test
    void rule1_singleLowScoreDoesNotFire() {
        // 单次 55：不足两条记录，规则①不误报（计划 §8.1 关键断言）
        List<String> reasons = detector.detectSkill(
                new WeaknessDetector.SkillSnapshot(List.of(55.0), 70, 0));
        assertThat(reasons).noneMatch(r -> r.contains("均低于 60"));
        // 最近两次只有一次低于 60 同样不触发
        assertThat(detector.detectSkill(
                        new WeaknessDetector.SkillSnapshot(List.of(70.0, 55.0), 70, 0)))
                .noneMatch(r -> r.contains("均低于 60"));
    }

    @Test
    void rule2_latestBelowRequiredLevelFires() {
        List<String> reasons = detector.detectSkill(
                new WeaknessDetector.SkillSnapshot(List.of(80.0, 62.0), 70, 0));
        assertThat(reasons).anyMatch(r -> r.contains("低于岗位要求 70"));
        // 达标不触发
        assertThat(detector.detectSkill(
                        new WeaknessDetector.SkillSnapshot(List.of(80.0, 72.0), 70, 0)))
                .isEmpty();
    }

    @Test
    void rule3_twoMissingPointQuestionsFire() {
        List<String> reasons = detector.detectSkill(
                new WeaknessDetector.SkillSnapshot(List.of(75.0), 70, 2));
        assertThat(reasons).anyMatch(r -> r.contains("2 道题存在未覆盖的失分点"));
        // 仅 1 道题命中失分点不触发（阈值 ≥2）
        assertThat(detector.detectSkill(
                        new WeaknessDetector.SkillSnapshot(List.of(75.0), 70, 1)))
                .isEmpty();
    }

    @Test
    void rule4_communicationDimensionTwoConsecutiveBelow60() {
        Optional<String> reason = detector.detectDimension(
                new WeaknessDetector.DimensionSnapshot("communication", List.of(70.0, 58.0, 52.0)));
        assertThat(reason).isPresent();
        assertThat(reason.get()).contains("communication").contains("低于 60");
        // 一次低分不误报；voice 维度同样适用
        assertThat(detector.detectDimension(
                new WeaknessDetector.DimensionSnapshot("communication", List.of(52.0))))
                .isEmpty();
        assertThat(detector.detectDimension(
                        new WeaknessDetector.DimensionSnapshot("voice", List.of(50.0, 45.0))))
                .isPresent();
    }
}
