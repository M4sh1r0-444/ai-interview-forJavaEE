package com.example.interview.ai.prompt;

import com.example.interview.ai.model.AiTurnEvaluation;
import com.example.interview.ai.model.NextQuestionContext;
import com.example.interview.ai.model.ReportContext;
import com.example.interview.ai.model.TurnEvaluationContext;
import com.example.interview.interview.entity.InterviewMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prompt 拼装（计划 §4.3 / §6.2 第 5 步）：岗位能力模型 + 近 10 条历史 + 知识片段 +
 * 追问规则指令 + 输出 JSON Schema 说明。mock 模式下供 DEBUG 日志观测，
 * 真实 Provider（M4-4.6）将其作为用户消息发送。
 */
@Component
public class PromptBuilder {

    // ==================== nextQuestion（M2 已交付） ====================

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

        appendKnowledge(sb, ctx.getKnowledgeChunks());

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

    // ==================== evaluateTurn（M4-4.6） ====================

    public String buildEvaluateTurnPrompt(TurnEvaluationContext ctx) {
        StringBuilder sb = new StringBuilder(768);
        sb.append("你是技术面试评分官，请根据题目与候选人该轮全部回答，输出结构化评分。\n\n");
        sb.append("【题目】\n").append(ctx.getQuestion() != null ? ctx.getQuestion().getContent() : "").append('\n');
        if (ctx.getQuestion() != null && notEmpty(ctx.getQuestion().getKeyPoints())) {
            sb.append("关键得分点：").append(String.join("；", ctx.getQuestion().getKeyPoints())).append('\n');
        }
        if (ctx.getQuestion() != null && notEmpty(ctx.getQuestion().getDeductionPoints())) {
            sb.append("常见失分点：").append(String.join("；", ctx.getQuestion().getDeductionPoints())).append('\n');
        }

        appendKnowledge(sb, ctx.getKnowledgeChunks());

        if (ctx.getRoundMessages() != null) {
            sb.append("\n【该轮问答】\n");
            for (InterviewMessage m : ctx.getRoundMessages()) {
                if ("AI".equals(m.getSpeaker())) {
                    sb.append("面试官：").append(abbreviate(m.getContentText(), 200)).append('\n');
                } else if ("USER".equals(m.getSpeaker())) {
                    sb.append("候选人：").append(abbreviate(m.getContentText(), 800)).append('\n');
                }
            }
        }

        sb.append("""


                【输出要求】只输出一个 JSON 对象，结构如下：
                {"scores":{"technical":0-100,"depth":0-100,"logic":0-100,"match":0-100,"communication":0-100},
                "coveredPoints":["已覆盖的得分点原文"],"missingPoints":["未覆盖的得分点原文"],
                "evidence":["回答中支撑得分的原句，最多3句"],"reason":"一句话评分理由",
                "targetSkills":["题目技能编码"],"responseTooVague":false}
                评分锚点：优秀 85+，合格 70~85，薄弱 60~70，不合格 <60；按关键得分点覆盖率定 technical。
                """);
        return sb.toString();
    }

    // ==================== generateReport（M4-4.6） ====================

    public String buildReportPrompt(ReportContext ctx) {
        StringBuilder sb = new StringBuilder(768);
        sb.append("你是面试评估专家，请基于逐题评分与六维聚合分为候选人生成中文评估报告。\n\n");
        sb.append("【岗位】").append(ctx.getPositionName())
                .append("　难度档位 ").append(ctx.getDifficulty())
                .append("　完成轮次 ").append(ctx.getRoundCount())
                .append("　总分 ").append(ctx.getTotalScore()).append('\n');
        sb.append("\n【六维聚合分】\n");
        if (ctx.getDimensionScores() != null) {
            ctx.getDimensionScores().forEach((dim, score) -> sb.append(dim).append("：").append(score).append('\n'));
        }
        if (ctx.getTurnEvaluations() != null && !ctx.getTurnEvaluations().isEmpty()) {
            sb.append("\n【逐题评分摘要】\n");
            int i = 1;
            for (AiTurnEvaluation t : ctx.getTurnEvaluations()) {
                sb.append("第").append(i++).append("题 reason：")
                        .append(t.getReason() != null ? t.getReason() : "").append('\n');
                if (notEmpty(t.getMissingPoints())) {
                    sb.append("  缺失要点：").append(String.join("；", t.getMissingPoints())).append('\n');
                }
            }
        }

        sb.append("""


                【输出要求】只输出一个 JSON 对象，结构如下：
                {"summary":"一段话总评（含总分与各维度简评）",
                "strengths":["2~3条亮点，引用维度分或得分点证据"],
                "weaknesses":["2~3条不足，指向具体维度与缺失要点"],
                "improvementPlan":["3条可执行的练习建议，逐条对应不足"],
                "reportText":"完整评语，200~400字，面向学生口吻"}
                """);
        return sb.toString();
    }

    // ==================== 公共段 ====================

    /** 知识片段段（M4-4.4）：检索命中的 RAG 片段进入 Prompt。 */
    private void appendKnowledge(StringBuilder sb, List<String> knowledgeChunks) {
        if (notEmpty(knowledgeChunks)) {
            sb.append("\n【参考知识片段】\n");
            knowledgeChunks.forEach(chunk -> sb.append("- ").append(chunk).append('\n'));
        }
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
