package com.example.interview.ai.prompt;

import com.example.interview.ai.model.NextQuestionContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prompt 拼装（计划 §4.3 / §6.2 第 5 步）：岗位能力模型 + 近 10 条历史 + 知识片段 +
 * 追问规则指令 + 输出 JSON Schema 说明。M2 供 DEBUG 日志观测；M4 供真实 Provider 使用。
 */
@Component
public class PromptBuilder {

    public String buildNextQuestionPrompt(NextQuestionContext ctx) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("你是一名严格但友善的技术面试官，正在对【").append(ctx.getPositionName())
                .append("】岗位候选人进行中文模拟面试。\n\n");

        sb.append("【岗位能力模型】\n").append(String.join("、", ctx.getPositionSkills())).append('\n');

        sb.append("\n【面试进度】第 ").append(ctx.getRoundNo() + 1).append('/').append(ctx.getPlannedRounds())
                .append(" 轮，难度档位 ").append(ctx.getDifficulty()).append("。\n");

        sb.append("\n【当前主问题】\n").append(ctx.getQuestionContent()).append('\n');
        if (notEmpty(ctx.getKeyPoints())) {
            sb.append("关键得分点：").append(String.join("；", ctx.getKeyPoints())).append('\n');
        }
        if (notEmpty(ctx.getDeductionPoints())) {
            sb.append("常见失分点：").append(String.join("；", ctx.getDeductionPoints())).append('\n');
        }

        if (notEmpty(ctx.getKnowledgeChunks())) {
            sb.append("\n【参考知识片段】\n");
            ctx.getKnowledgeChunks().forEach(chunk -> sb.append("- ").append(chunk).append('\n'));
        }

        if (ctx.getHistory() != null && !ctx.getHistory().isEmpty()) {
            sb.append("\n【最近对话】\n");
            for (NextQuestionContext.HistoryMessage m : ctx.getHistory()) {
                sb.append(m.getSpeaker()).append("：").append(abbreviate(m.getContent(), 200)).append('\n');
            }
        }

        sb.append("\n【本轮指令】\n");
        if (ctx.getInstruction() != null && !ctx.getInstruction().isBlank()) {
            sb.append(ctx.getInstruction()).append('\n');
        }
        sb.append("同一主问题的追问次数已达 ").append(ctx.getFollowupCount()).append(" 次。\n");

        sb.append("""

                【输出要求】只输出一个 JSON 对象，不要任何解释或 markdown 围栏，结构如下：
                {"action":"FOLLOW_UP 或 NEXT_QUESTION","content":"追问文本（action=FOLLOW_UP 时必填，一至两句话，具体、有针对性）","difficulty":1-5 整数,"targetSkills":["技能编码"],"reason":"一句话决策理由"}
                规则：候选人回答缺少关键得分点或过于模糊时追问（action=FOLLOW_UP，最多追问 3 次后必须换题）；
                回答已充分或追问次数用尽时给出 action=NEXT_QUESTION（content 留空）。
                """);
        return sb.toString();
    }

    private boolean notEmpty(List<String> list) {
        return list != null && !list.isEmpty();
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
