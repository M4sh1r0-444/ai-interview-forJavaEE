package com.example.interview.interview.rule;

import com.example.interview.question.entity.Question;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态追问规则引擎（计划 §6.4，方案 §6.5 七条规则逐条落地）。纯逻辑组件，单测覆盖七条规则。
 *
 * <p>优先级（高 → 低）：</p>
 * <ol>
 *   <li>R7 追问 ≥3 → 强制换题（硬规则，代码强制，优先级高于一切追问触发）</li>
 *   <li>R1 缺少关键知识点（missingPoints 非空）→ 追问缺失内容</li>
 *   <li>R3 回答含失分点关键词 → 追问错误边界/后果</li>
 *   <li>R2 回答模糊（&lt;60 字或无数字且无技术词）→ 要求举例</li>
 *   <li>R4 得分点覆盖 ≥80% → 追问难度+1，追问替代方案/取舍</li>
 *   <li>R5 连续两轮低分(&lt;60) → 换题并降难度，回到基础主题</li>
 *   <li>R6 连续两轮高分(≥85) → 换题并升难度，转入项目深挖/系统设计</li>
 *   <li>默认：首个追问位（followupCount=0）追问一次深化，否则换题</li>
 * </ol>
 * <p>R5/R6 的输入 lastTwoScores 在 M2 运行期为空（评分排期 M3），规则休眠；单测注入分数验证。</p>
 */
@Slf4j
@Component
public class FollowupRuleEngine {

    /** 技术词启发：英文标识符（IOC、HashMap、B+ 树等），兼作失分点关键词提取 */
    private static final Pattern TECH_TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9+.#-]{1,}");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final int VAGUE_MIN_CHARS = 60;

    public FollowupDecision decide(FollowupContext ctx) {
        // R7 同题追问超过上限 → 下一题（硬规则，计划 §6.4 最后一行）
        if (ctx.getFollowupCount() >= ctx.getFollowupLimit()) {
            return FollowupDecision.of(FollowupDecision.Action.NEXT_QUESTION,
                    FollowupDecision.R_CAP_REACHED, "同题追问次数已用尽，进入下一题。", 0);
        }

        String answer = ctx.getAnswerText() != null ? ctx.getAnswerText() : "";

        // R1 缺少关键知识点 → 就缺失内容追问
        if (ctx.getMissingPoints() != null && !ctx.getMissingPoints().isEmpty()) {
            return FollowupDecision.of(FollowupDecision.Action.FOLLOW_UP,
                    FollowupDecision.R_MISSING_POINT,
                    "候选人未覆盖关键要点【" + String.join("、", ctx.getMissingPoints())
                            + "】，请就缺失内容追问。", 0);
        }

        // R3 技术错误（失分点关键词命中）→ 问边界/后果
        if (hitDeductionPoint(ctx.getQuestion(), answer)) {
            return FollowupDecision.of(FollowupDecision.Action.FOLLOW_UP,
                    FollowupDecision.R_DEDUCTION,
                    "候选人回答疑似出现常见错误，请指出其错误的边界与后果并追问。", 0);
        }

        // R2 回答模糊 → 要求举例（计划 §6.4 规则 2 的判定）
        if (isVague(answer)) {
            return FollowupDecision.of(FollowupDecision.Action.FOLLOW_UP,
                    FollowupDecision.R_VAGUE, "候选人回答过于简略/模糊，请要求其结合具体例子展开。", 0);
        }

        // R4 回答完整（覆盖率 ≥80%）→ 提难度，追问替代方案/取舍
        if (ctx.getCoveredRatio() != null && ctx.getCoveredRatio() >= 0.8) {
            return FollowupDecision.of(FollowupDecision.Action.FOLLOW_UP,
                    FollowupDecision.R_GOOD_COVERAGE,
                    "候选人回答覆盖了主要得分点，请提高难度，追问替代方案或技术取舍。", 1);
        }

        // R5 连续两轮低分 → 降难度，回到基础主题
        if (lowScoreStreak(ctx.getLastTwoScores())) {
            return FollowupDecision.of(FollowupDecision.Action.NEXT_QUESTION,
                    FollowupDecision.R_LOW_SCORE_STREAK,
                    "候选人连续两轮得分偏低，下一题回到基础主题并降低难度。", -1);
        }

        // R6 连续两轮高分 → 项目深挖/系统设计
        if (highScoreStreak(ctx.getLastTwoScores())) {
            return FollowupDecision.of(FollowupDecision.Action.NEXT_QUESTION,
                    FollowupDecision.R_HIGH_SCORE_STREAK,
                    "候选人连续两轮高分，下一题转入项目深挖或系统设计并提高难度。", 1);
        }

        // 默认策略：每题主问题先追问一次深化（保证演示链路可见追问），之后换题
        if (ctx.getFollowupCount() == 0) {
            return FollowupDecision.of(FollowupDecision.Action.FOLLOW_UP,
                    FollowupDecision.R_DEFAULT, "请就该回答中值得深挖的点继续追问一次。", 0);
        }
        return FollowupDecision.of(FollowupDecision.Action.NEXT_QUESTION,
                FollowupDecision.R_DEFAULT, "回答已处理完毕，进入下一题。", 0);
    }

    /** 回答 <60 字，或既无数字也无英文技术词 → 模糊（计划 §6.4 规则 2）。 */
    boolean isVague(String answer) {
        String text = answer.trim();
        if (text.length() < VAGUE_MIN_CHARS) {
            return true;
        }
        boolean hasDigit = DIGIT.matcher(text).find();
        boolean hasTechToken = TECH_TOKEN.matcher(text).find();
        return !hasDigit && !hasTechToken;
    }

    /**
     * 失分点命中（R3）：失分点中的英文技术词全部出现在回答里视为命中
     * （中文短语无分词器不可靠匹配，完整词典法排期 M4 KeywordExtractor）。
     */
    boolean hitDeductionPoint(Question question, String answer) {
        if (question == null || question.getDeductionPoints() == null || answer.isBlank()) {
            return false;
        }
        for (String point : question.getDeductionPoints()) {
            Matcher m = TECH_TOKEN.matcher(point);
            boolean hasToken = false;
            boolean allHit = true;
            while (m.find()) {
                hasToken = true;
                if (!answer.contains(m.group())) {
                    allHit = false;
                    break;
                }
            }
            if (hasToken && allHit) {
                log.debug("失分点命中: {}", point);
                return true;
            }
        }
        return false;
    }

    private boolean lowScoreStreak(List<Integer> lastTwoScores) {
        return lastTwoScores != null && lastTwoScores.size() >= 2
                && lastTwoScores.get(lastTwoScores.size() - 2) < 60
                && lastTwoScores.get(lastTwoScores.size() - 1) < 60;
    }

    private boolean highScoreStreak(List<Integer> lastTwoScores) {
        return lastTwoScores != null && lastTwoScores.size() >= 2
                && lastTwoScores.get(lastTwoScores.size() - 2) >= 85
                && lastTwoScores.get(lastTwoScores.size() - 1) >= 85;
    }
}
