package com.example.interview.assessment.engine;

import com.example.interview.interview.entity.InterviewMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语音指标与 voice 维度评分（计划 §4.3 / §6.6，mode=AUDIO 场）。
 *
 * <pre>
 * 语速 = 转写字数 ÷ 时长(分钟)；停顿次数由转写中的省略号/破折号标记推断
 * （interview_message 无 pause_count 列、9 表 DDL 冻结，见进度记录决策）；
 * 语气词密度 = 语气词次数 / (字数/100)。
 * voice 分 = 语速×0.5 + 停顿×0.2 + 语气词×0.3，各指标分段线性映射，clamp 0~100。
 * </pre>
 * 纯逻辑组件（无状态、不抛错），VoiceMetricsCalculatorTest 覆盖。
 */
@Component
public class VoiceMetricsCalculator {

    /** 语速满分区（字/分钟，计划 §6.6：180~260 满分） */
    static final double SPEED_BEST_LOW = 180;
    static final double SPEED_BEST_HIGH = 260;
    /** 语速过渡区上下界：过渡区内 60→100 线性 */
    static final double SPEED_OK_LOW = 120;
    static final double SPEED_OK_HIGH = 320;

    private static final Pattern FILLER = Pattern.compile("嗯|啊|呃|然后|就是说|那个|这个|其实|反正");
    /** 停顿标记：省略号（中英文）、长破折号（先长后短，避免单个 — 重复计数）、显式停顿标注 */
    private static final Pattern PAUSE_MARKER = Pattern.compile("……|\\.\\.\\.|——|—|\\[停顿\\]|（停顿）");

    /** 指标快照：报告/调试观测用。 */
    public record VoiceMetrics(int chars, Integer durationMs, double speedCharsPerMin,
                               int pauseCount, int fillerCount, double fillerDensityPer100) {
    }

    public VoiceMetrics compute(String transcript, Integer durationMs) {
        String text = transcript != null ? transcript : "";
        int chars = text.replaceAll("\\s+", "").length();
        int fillers = 0;
        Matcher m = FILLER.matcher(text);
        while (m.find()) {
            fillers++;
        }
        int pauses = 0;
        Matcher p = PAUSE_MARKER.matcher(text);
        while (p.find()) {
            pauses++;
        }
        double speed = durationMs != null && durationMs > 0 && chars > 0
                ? chars / (durationMs / 60000.0) : 0;
        // 空转写/零时长不除零：密度按 0 计
        double density = chars > 0 ? fillers / (chars / 100.0) : 0;
        return new VoiceMetrics(chars, durationMs, speed, pauses, fillers, density);
    }

    /** voice 维度分（0~100）。无时长或无转写时给中性偏低分（信息不足不宜加分）。 */
    public int voiceScore(String transcript, Integer durationMs) {
        VoiceMetrics metrics = compute(transcript, durationMs);
        if (metrics.chars() == 0 || metrics.durationMs() == null || metrics.durationMs() <= 0) {
            return 55;
        }
        double speedScore = speedScore(metrics.speedCharsPerMin());
        double pauseScore = pauseScore(metrics.pauseCount(), metrics.durationMs());
        double fillerScore = fillerScore(metrics.fillerDensityPer100());
        double total = speedScore * 0.5 + pauseScore * 0.2 + fillerScore * 0.3;
        // 语速在极端区（<120 或 >320 字/分）时语音表现一票否决：封顶 60
        if (metrics.speedCharsPerMin() < SPEED_OK_LOW || metrics.speedCharsPerMin() > SPEED_OK_HIGH) {
            total = Math.min(60, total);
        }
        return (int) Math.round(Math.max(0, Math.min(100, total)));
    }

    /** 对该轮已有评分补 voice 维度（AUDIO 场由引擎侧调用；TEXT 场不加）。 */
    public void applyVoiceDimension(Map<String, Integer> scores,
                                    String transcript, Integer durationMs) {
        scores.put("voice", voiceScore(transcript, durationMs));
    }

    /** 语速分段线性：180~260 满分；120~180 与 260~320 线性 60→100；区间外 30~60。 */
    double speedScore(double speed) {
        if (speed >= SPEED_BEST_LOW && speed <= SPEED_BEST_HIGH) {
            return 100;
        }
        if (speed >= SPEED_OK_LOW && speed < SPEED_BEST_LOW) {
            return 60 + 40 * (speed - SPEED_OK_LOW) / (SPEED_BEST_LOW - SPEED_OK_LOW);
        }
        if (speed > SPEED_BEST_HIGH && speed <= SPEED_OK_HIGH) {
            return 100 - 40 * (speed - SPEED_BEST_HIGH) / (SPEED_OK_HIGH - SPEED_BEST_HIGH);
        }
        // 过慢（<120，含大量停顿的挤牙膏式回答）或过快（>320）
        return speed < SPEED_OK_LOW ? 40 : 30;
    }

    /** 停顿分：按每 2 分钟折算停顿率，0 次 100 分，每 2 分钟 1 次 80 分，2 次 60 分，更多 40 分以下。 */
    double pauseScore(int pauseCount, int durationMs) {
        double twoMinUnits = Math.max(1.0, durationMs / 120_000.0);
        double pausesPer2Min = pauseCount / twoMinUnits;
        if (pausesPer2Min <= 0.01) {
            return 100;
        } else if (pausesPer2Min <= 1.01) {
            return 80;
        } else if (pausesPer2Min <= 2.01) {
            return 60;
        } else if (pausesPer2Min <= 4.01) {
            return 40;
        }
        return 20;
    }

    /** 语气词密度分：每百字 ≤0.5 满分；≤1.5 得 80；≤3 得 60；更高线性下降。 */
    double fillerScore(double densityPer100) {
        if (densityPer100 <= 0.5) {
            return 100;
        } else if (densityPer100 <= 1.5) {
            return 80;
        } else if (densityPer100 <= 3) {
            return 60;
        }
        return Math.max(20, 60 - (densityPer100 - 3) * 10);
    }

    /** 该轮全部 USER 消息的转写拼接与时长求和（AUDIO 轮语音指标输入）。 */
    public static String joinedTranscript(List<InterviewMessage> roundMessages) {
        StringBuilder sb = new StringBuilder();
        roundMessages.stream()
                .filter(msg -> "USER".equals(msg.getSpeaker()))
                .forEach(msg -> sb.append(msg.getContentText() != null ? msg.getContentText() : "").append('\n'));
        return sb.toString().trim();
    }

    public static int sumDurationMs(List<InterviewMessage> roundMessages) {
        return roundMessages.stream()
                .filter(msg -> "USER".equals(msg.getSpeaker()))
                .mapToInt(msg -> msg.getAudioDurationMs() != null ? msg.getAudioDurationMs() : 0)
                .sum();
    }
}
