package com.example.interview.assessment.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 计划 §8.1 VoiceMetricsCalculatorTest：600 字/200 秒 → 语速 180 字/分；
 * 空转写不除零；分段线性打分边界。
 */
class VoiceMetricsCalculatorTest {

    private final VoiceMetricsCalculator calculator = new VoiceMetricsCalculator();

    @Test
    void speedIs180For600CharsIn200Seconds() {
        String transcript = "语".repeat(600);
        VoiceMetricsCalculator.VoiceMetrics m = calculator.compute(transcript, 200_000);
        assertThat(m.speedCharsPerMin()).isEqualTo(180.0);
        assertThat(m.chars()).isEqualTo(600);
        assertThat(m.fillerCount()).isZero();
    }

    @Test
    void emptyTranscriptDoesNotDivideByZero() {
        VoiceMetricsCalculator.VoiceMetrics m = calculator.compute("", 200_000);
        assertThat(m.chars()).isZero();
        assertThat(m.speedCharsPerMin()).isZero();
        assertThat(m.fillerDensityPer100()).isZero();

        VoiceMetricsCalculator.VoiceMetrics nullAll = calculator.compute(null, null);
        assertThat(nullAll.speedCharsPerMin()).isZero();
        assertThat(nullAll.fillerDensityPer100()).isZero();
    }

    @Test
    void fillerAndPauseMarkersAreCounted() {
        String transcript = "嗯这个缓存击穿……就是说热点 key 过期——大量请求打到数据库那个嗯";
        VoiceMetricsCalculator.VoiceMetrics m = calculator.compute(transcript, 60_000);
        // 嗯×2 + 这个 + 就是说 + 那个 = 5；…… 与 —— = 2 次停顿
        assertThat(m.fillerCount()).isEqualTo(5);
        assertThat(m.pauseCount()).isEqualTo(2);
    }

    @Test
    void voiceScoreInBestSpeedBandIsHigh() {
        // 600 字 / 160 秒 → 225 字/分（180~260 满分区），无停顿无语气词 → 接近满分
        int score = calculator.voiceScore("答".repeat(600), 160_000);
        assertThat(score).isBetween(90, 100);
    }

    @Test
    void voiceScoreLowWhenTooSlowOrTooFast() {
        // 100 字 / 200 秒 → 30 字/分（过慢）
        assertThat(calculator.voiceScore("答".repeat(100), 200_000)).isLessThanOrEqualTo(60);
        // 1200 字 / 200 秒 → 360 字/分（过快）
        assertThat(calculator.voiceScore("答".repeat(1200), 200_000)).isLessThanOrEqualTo(60);
    }

    @Test
    void voiceScoreNeutralWhenNoDurationOrTranscript() {
        assertThat(calculator.voiceScore("答".repeat(300), null)).isEqualTo(55);
        assertThat(calculator.voiceScore("", 200_000)).isEqualTo(55);
        assertThat(calculator.voiceScore(null, null)).isEqualTo(55);
    }

    @Test
    void speedScoreBoundariesAreLinear() {
        assertThat(calculator.speedScore(180)).isEqualTo(100);
        assertThat(calculator.speedScore(260)).isEqualTo(100);
        // 过渡区 120~180 线性 60→100：中点 150 → 80
        assertThat(calculator.speedScore(150)).isEqualTo(80);
        // 过渡区 260~320 线性 100→60：中点 290 → 80
        assertThat(calculator.speedScore(290)).isEqualTo(80);
        assertThat(calculator.speedScore(100)).isEqualTo(40);
        assertThat(calculator.speedScore(400)).isEqualTo(30);
    }

    @Test
    void applyVoiceDimensionPutsVoiceIntoScores() {
        java.util.Map<String, Integer> scores = new java.util.HashMap<>(java.util.Map.of("technical", 80));
        calculator.applyVoiceDimension(scores, "答".repeat(600), 160_000);
        assertThat(scores).containsKey("voice");
        assertThat(scores.get("voice")).isBetween(0, 100);
    }
}
