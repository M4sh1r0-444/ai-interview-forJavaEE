package com.example.interview.interview.rule;

import com.example.interview.question.entity.Question;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 追问规则引擎输入。coveredRatio/missingPoints/lastTwoScores 在 M2 运行期为 null
 * （逐题评分排期 M3），引擎对 null 容忍；单测可注入完整输入覆盖全部规则。
 */
@Data
@Builder
public class FollowupContext {

    /** 本轮最新一次候选人回答文本 */
    private String answerText;
    /** 当前主问题（含 keyPoints/deductionPoints/followupTemplates/skillTags） */
    private Question question;
    /** 当前主问题已追问次数 */
    private int followupCount;
    /** 同题主问题追问上限（app.interview.followup-limit，默认 3） */
    private int followupLimit;

    /** 得分点覆盖率（covered/keyPoints），M3 起由 evaluateTurn 提供 */
    private Double coveredRatio;
    /** 未覆盖的关键要点，M3 起由 evaluateTurn 提供 */
    private List<String> missingPoints;
    /** 最近两轮主问题得分，M3 起提供 */
    private List<Integer> lastTwoScores;
}
