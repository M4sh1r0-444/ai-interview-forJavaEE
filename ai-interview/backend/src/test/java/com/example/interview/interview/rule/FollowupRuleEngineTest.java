package com.example.interview.interview.rule;

import com.example.interview.question.entity.Question;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 计划 §8.1 FollowupRuleEngineTest：方案 §6.5 七条规则各一用例。
 * good 为"合格回答"：≥60 字、含数字、不命中失分点（不含 IOC+DI 组合等失分关键词）。
 */
class FollowupRuleEngineTest {

    private final FollowupRuleEngine engine = new FollowupRuleEngine();
    private final AtomicLong idGen = new AtomicLong(1);

    private static final String GOOD_ANSWER =
            "IOC 控制反转指对象的创建与依赖装配职责交给容器，依赖注入是其具体实现方式，"
                    + "容器托管 Bean 的生命周期，并通过 @Autowired 按类型完成注入，整体降低了对象间耦合 42。";

    private Question question() {
        Question q = new Question();
        q.setId(idGen.getAndIncrement());
        q.setContent("什么是 Spring IOC？");
        q.setDifficulty(3);
        q.setKeyPoints(List.of("控制反转与依赖注入的关系", "容器托管对象生命周期", "@Autowired 例子"));
        q.setDeductionPoints(List.of("把 IOC 说成 DI 的同义词且无解释", "无法举出任何实际使用场景"));
        q.setSkillTags(List.of("spring"));
        return q;
    }

    private FollowupContext.FollowupContextBuilder baseCtx() {
        return FollowupContext.builder()
                .question(question())
                .followupCount(0)
                .followupLimit(3);
    }

    /** 规则 7：同题追问 ≥3 → 强制换题（硬规则，优先级最高，压过规则 1）。 */
    @Test
    void rule7_capReachedForcesNextQuestion() {
        FollowupDecision d = engine.decide(baseCtx()
                .answerText(GOOD_ANSWER)
                .followupCount(3)
                .missingPoints(List.of("容器托管对象生命周期"))
                .build());
        assertThat(d.getAction()).isEqualTo(FollowupDecision.Action.NEXT_QUESTION);
        assertThat(d.getReasonCode()).isEqualTo(FollowupDecision.R_CAP_REACHED);
    }

    /** 规则 1：缺少关键知识点 → 追问缺失内容。 */
    @Test
    void rule1_missingPointsTriggerFollowup() {
        FollowupDecision d = engine.decide(baseCtx()
                .answerText(GOOD_ANSWER)
                .missingPoints(List.of("容器托管对象生命周期"))
                .build());
        assertThat(d.getAction()).isEqualTo(FollowupDecision.Action.FOLLOW_UP);
        assertThat(d.getReasonCode()).isEqualTo(FollowupDecision.R_MISSING_POINT);
        assertThat(d.getInstruction()).contains("容器托管对象生命周期");
    }

    /** 规则 3：回答含失分点关键词（IOC 与 DI 是同义词的说法）→ 问边界/后果。 */
    @Test
    void rule3_deductionHitAsksBoundary() {
        FollowupDecision d = engine.decide(baseCtx()
                .answerText("IOC 和 DI 是同一个东西的两种说法，都是指把对象的创建交给 Spring 容器完成，"
                        + "比如 Service 里注入 Mapper 就可以了 789。")
                .build());
        assertThat(d.getAction()).isEqualTo(FollowupDecision.Action.FOLLOW_UP);
        assertThat(d.getReasonCode()).isEqualTo(FollowupDecision.R_DEDUCTION);
    }

    /** 规则 2：回答模糊（短回答）→ 要求举例。 */
    @Test
    void rule2_vagueAnswerAsksExample() {
        FollowupDecision d = engine.decide(baseCtx().answerText("不太清楚").build());
        assertThat(d.getAction()).isEqualTo(FollowupDecision.Action.FOLLOW_UP);
        assertThat(d.getReasonCode()).isEqualTo(FollowupDecision.R_VAGUE);
    }

    /** 规则 2 补充：长回答但无数字且无技术词也算模糊。 */
    @Test
    void rule2_longButNoSpecificsIsVague() {
        FollowupDecision d = engine.decide(baseCtx()
                .answerText("这个概念挺重要的，项目中经常会用到，总的来说好处很多，"
                        + "面试的时候按照套路回答即可，平时多用自然就熟悉了，"
                        + "真的没什么特别需要注意的地方。")
                .build());
        assertThat(d.getReasonCode()).isEqualTo(FollowupDecision.R_VAGUE);
    }

    /** 规则 4：覆盖率 ≥80% → 追问难度 +1，追问替代方案/取舍。 */
    @Test
    void rule4_goodCoverageRaisesDifficulty() {
        FollowupDecision d = engine.decide(baseCtx()
                .answerText(GOOD_ANSWER)
                .coveredRatio(0.9)
                .build());
        assertThat(d.getAction()).isEqualTo(FollowupDecision.Action.FOLLOW_UP);
        assertThat(d.getReasonCode()).isEqualTo(FollowupDecision.R_GOOD_COVERAGE);
        assertThat(d.getDifficultyDelta()).isEqualTo(1);
    }

    /** 规则 5：连续两轮低分(<60) → 换题并降难度；单次低分不触发（不误报）。 */
    @Test
    void rule5_lowScoreStreakLowersDifficulty() {
        FollowupDecision d = engine.decide(baseCtx()
                .answerText(GOOD_ANSWER)
                .lastTwoScores(List.of(55, 58))
                .build());
        assertThat(d.getAction()).isEqualTo(FollowupDecision.Action.NEXT_QUESTION);
        assertThat(d.getReasonCode()).isEqualTo(FollowupDecision.R_LOW_SCORE_STREAK);
        assertThat(d.getDifficultyDelta()).isEqualTo(-1);

        FollowupDecision single = engine.decide(baseCtx()
                .answerText(GOOD_ANSWER)
                .lastTwoScores(List.of(80, 55))
                .build());
        assertThat(single.getReasonCode()).isNotEqualTo(FollowupDecision.R_LOW_SCORE_STREAK);
    }

    /** 规则 6：连续两轮高分(≥85) → 项目深挖并升难度。 */
    @Test
    void rule6_highScoreStreakDigsProject() {
        FollowupDecision d = engine.decide(baseCtx()
                .answerText(GOOD_ANSWER)
                .lastTwoScores(List.of(88, 90))
                .build());
        assertThat(d.getAction()).isEqualTo(FollowupDecision.Action.NEXT_QUESTION);
        assertThat(d.getReasonCode()).isEqualTo(FollowupDecision.R_HIGH_SCORE_STREAK);
        assertThat(d.getDifficultyDelta()).isEqualTo(1);
    }

    /** 默认策略：首答正常 → 追问一次深化；已有追问 → 换题。 */
    @Test
    void defaultPolicyFollowupOnceThenAdvance() {
        FollowupDecision first = engine.decide(baseCtx().answerText(GOOD_ANSWER).followupCount(0).build());
        assertThat(first.getAction()).isEqualTo(FollowupDecision.Action.FOLLOW_UP);
        assertThat(first.getReasonCode()).isEqualTo(FollowupDecision.R_DEFAULT);

        FollowupDecision second = engine.decide(baseCtx().answerText(GOOD_ANSWER).followupCount(1).build());
        assertThat(second.getAction()).isEqualTo(FollowupDecision.Action.NEXT_QUESTION);
    }
}
