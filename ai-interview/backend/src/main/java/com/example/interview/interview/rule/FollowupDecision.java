package com.example.interview.interview.rule;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 追问规则引擎输出。difficultyDelta 仅作用于追问/提示难度，clamp 1~5 由使用方执行。
 */
@Getter
@RequiredArgsConstructor
public class FollowupDecision {

    public enum Action { FOLLOW_UP, NEXT_QUESTION }

    /** 规则编码（Mock 选模板参考） */
    public static final String R_MISSING_POINT = "MISSING_POINT";
    public static final String R_VAGUE = "VAGUE";
    public static final String R_DEDUCTION = "DEDUCTION";
    public static final String R_GOOD_COVERAGE = "GOOD_COVERAGE";
    public static final String R_LOW_SCORE_STREAK = "LOW_SCORE_STREAK";
    public static final String R_HIGH_SCORE_STREAK = "HIGH_SCORE_STREAK";
    public static final String R_CAP_REACHED = "CAP_REACHED";
    public static final String R_DEFAULT = "DEFAULT";

    private final Action action;
    private final String reasonCode;
    /** 注入 Prompt 的指令（真实 Provider 用；Mock 仅日志） */
    private final String instruction;
    private final int difficultyDelta;

    public static FollowupDecision of(Action action, String reasonCode, String instruction, int difficultyDelta) {
        return new FollowupDecision(action, reasonCode, instruction, difficultyDelta);
    }
}
