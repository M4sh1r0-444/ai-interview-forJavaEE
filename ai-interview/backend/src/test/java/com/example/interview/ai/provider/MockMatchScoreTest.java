package com.example.interview.ai.provider;

import com.example.interview.ai.model.TurnEvaluationContext;
import com.example.interview.interview.entity.InterviewMessage;
import com.example.interview.question.entity.Question;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIX-14 Mock match 维度中文技能名支持：英文 stem / 岗位能力模型技能名 /
 * 内置种子关键词任一命中即覆盖——全中文高分回答 match≥75，不再被压到 55。
 */
class MockMatchScoreTest {

    private final MockAiProvider provider = new MockAiProvider();

    private Question question(List<String> skillTags, List<String> keyPoints) {
        Question q = new Question();
        q.setId(1L);
        q.setContent("介绍一个你负责过的项目及其架构设计");
        q.setDifficulty(3);
        q.setSkillTags(skillTags);
        q.setKeyPoints(keyPoints);
        q.setReferenceAnswer("参考答案".repeat(60));
        return q;
    }

    private TurnEvaluationContext ctx(Question q, String answer, Map<String, String> skillNames) {
        InterviewMessage user = new InterviewMessage();
        user.setSpeaker("USER");
        user.setContentText(answer);
        return TurnEvaluationContext.builder()
                .sessionId(1L)
                .question(q)
                .roundMessages(List.of(user))
                .skillNames(skillNames)
                .build();
    }

    @Test
    void chineseAnswerCoveringAllPointsScoresHighMatch() {
        // 验收报告 5.6 的实测反例：覆盖 4/4 得分点、结构化 331 字中文回答，match 被压到 55
        Question q = question(List.of("project_design", "communication"), List.of(
                "项目背景与目标", "架构设计", "技术选型理由", "上线效果与数据"));
        String answer = "第一，项目背景：我负责电商订单中台，目标统一多渠道订单履约。"
                + "第二，架构设计：采用分层架构与事件驱动，订单创建后经消息队列异步解耦库存与物流。"
                + "第三，技术选型理由：消息队列选 RocketMQ 考虑事务消息与堆积能力，缓存用 Redis 抗读。"
                + "第四，上线效果：订单处理峰值从每秒八百提升到三千，可用性达到 99.95%。";
        var eval = provider.evaluateTurn(ctx(q, answer, null));
        assertThat(eval.getCoveredPoints()).hasSize(4);
        assertThat(eval.getMissingPoints()).isEmpty();
        assertThat(eval.getScores().get("match"))
                .as("全中文覆盖全部得分点的回答 match 不应低于 75").isGreaterThanOrEqualTo(75);
    }

    @Test
    void skillNameFromContextCountsAsHit() {
        // 优选项：岗位能力模型注入的技能名（含按分隔符拆分的可命中词）
        Question q = question(List.of("custom_skill"), List.of("要点一"));
        Map<String, String> skillNames = Map.of("custom_skill", "低代码平台/组件编排");
        String answer = "我们基于低代码平台做了表单引擎，通过组件编排让业务同学自助搭建流程，"
                + "要点一的内容我也完整覆盖了，具体包括背景、方案、效果三部分展开说明。";
        var hit = provider.evaluateTurn(ctx(q, answer, skillNames));
        assertThat(hit.getScores().get("match")).isEqualTo(100);

        var miss = provider.evaluateTurn(ctx(q,
                "这个我不太了解，没有接触过相关内容，无法展开说明具体细节。", skillNames));
        assertThat(miss.getScores().get("match")).isEqualTo(55);
    }

    @Test
    void unknownSkillCodeFallsBackToEnglishStem() {
        Question q = question(List.of("kubernetes_eks"), List.of("要点一"));
        String answer = "我们在 kubernetes 集群上做发布，要点一：滚动更新、就绪探针、"
                + "副本数控制，配合 CI 流水线实现快速回滚，稳定性明显提升。";
        var hit = provider.evaluateTurn(ctx(q, answer, null));
        assertThat(hit.getScores().get("match")).isEqualTo(100);

        var miss = provider.evaluateTurn(ctx(q,
                "我不了解这个领域，没有实际操作过，无法给出更多说明。", null));
        assertThat(miss.getScores().get("match")).isEqualTo(55);
    }

    @Test
    void englishTechAnswerNotRegressed() {
        Question q = question(List.of("redis_mq", "mysql"), List.of("缓存", "索引"));
        String answer = "We use Redis for caching hot keys with logical expiration, "
                + "and MySQL covering index optimization for slow queries; "
                + "缓存与索引 both handled, with detailed metrics and trade-offs explained.";
        var eval = provider.evaluateTurn(ctx(q, answer, null));
        assertThat(eval.getScores().get("match")).isGreaterThanOrEqualTo(80);
    }
}
