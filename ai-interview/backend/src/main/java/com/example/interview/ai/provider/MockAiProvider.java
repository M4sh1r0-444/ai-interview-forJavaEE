package com.example.interview.ai.provider;

import com.example.interview.ai.model.AiNextQuestionResult;
import com.example.interview.ai.model.AiReport;
import com.example.interview.ai.model.AiTurnEvaluation;
import com.example.interview.ai.model.NextQuestionContext;
import com.example.interview.ai.model.ReportContext;
import com.example.interview.ai.model.TurnEvaluationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 离线 Mock 实现（计划 §1.3 / §6.8）：无网环境可完整演示。
 * nextQuestion 依据追问规则引擎的决策产出模板追问，模板用尽后循环通用追问池，
 * 保证"mock 追问可循环产出"（M2-2.3 完成标准）。
 * evaluateTurn / generateReport 于 M3 实现规则评分引擎与报告拼装。
 */
@Slf4j
@Component
public class MockAiProvider implements AiProvider {

    /** 通用追问池：题目模板用尽时循环取用（计划 §6.8 第 3 级）。 */
    private static final List<String> GENERIC_FOLLOWUPS = List.of(
            "能结合你做过的项目，举一个具体的例子说明吗？",
            "这个方案有什么潜在的风险或边界情况？你是怎么考虑取舍的？",
            "如果流量规模扩大十倍，你刚才的方案需要做哪些调整？");

    @Override
    public AiNextQuestionResult nextQuestion(NextQuestionContext ctx) {
        AiNextQuestionResult r = new AiNextQuestionResult();
        boolean followUp = AiNextQuestionResult.ACTION_FOLLOW_UP.equals(ctx.getRequestedAction());
        r.setAction(followUp ? AiNextQuestionResult.ACTION_FOLLOW_UP
                : AiNextQuestionResult.ACTION_NEXT_QUESTION);
        if (followUp) {
            r.setContent(pickFollowupContent(ctx));
            r.setDifficulty(clamp(defaultDiff(ctx) + ctx.getDifficultyDelta()));
            r.setReason("mock:" + ctx.getReasonCode());
        } else {
            // 主问题序列在 start 时已组卷落库，NEXT_QUESTION 无需内容
            r.setDifficulty(clamp(defaultDiff(ctx) + ctx.getDifficultyDelta()));
            r.setReason("mock:" + ctx.getReasonCode());
        }
        r.setTargetSkills(ctx.getTargetSkills());
        log.debug("MockAiProvider.nextQuestion session round={} action={} reason={}",
                ctx.getRoundNo(), r.getAction(), r.getReason());
        return r;
    }

    /** 模板挑选：VAGUE 用方案 §6.5 指定话术；其余按追问次数轮转题目模板，用尽转通用池。 */
    private String pickFollowupContent(NextQuestionContext ctx) {
        if ("VAGUE".equals(ctx.getReasonCode())) {
            // 计划 §6.4 规则 2 的 Mock 实现：回答模糊 → 要求举例
            return "你的回答偏简略，能结合一个具体的例子说明吗？";
        }
        List<String> templates = ctx.getFollowupTemplates();
        if (templates != null && !templates.isEmpty()) {
            return templates.get(ctx.getFollowupCount() % templates.size());
        }
        return GENERIC_FOLLOWUPS.get(ctx.getFollowupCount() % GENERIC_FOLLOWUPS.size());
    }

    private int defaultDiff(NextQuestionContext ctx) {
        return ctx.getQuestionDifficulty() != null ? ctx.getQuestionDifficulty() : ctx.getDifficulty();
    }

    private int clamp(int d) {
        return Math.max(1, Math.min(5, d));
    }

    @Override
    public AiTurnEvaluation evaluateTurn(TurnEvaluationContext ctx) {
        // M3-3.1 实现规则评分引擎（key_points 命中率 → 六维分）
        throw new UnsupportedOperationException("M3 里程碑接入");
    }

    @Override
    public AiReport generateReport(ReportContext ctx) {
        // M3-3.2 实现结构化报告拼装
        throw new UnsupportedOperationException("M3 里程碑接入");
    }
}
