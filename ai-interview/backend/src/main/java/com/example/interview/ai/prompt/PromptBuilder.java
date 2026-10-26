package com.example.interview.ai.prompt;

import com.example.interview.ai.model.AiTurnEvaluation;
import com.example.interview.ai.model.NextQuestionContext;
import com.example.interview.ai.model.QuestionPersonalizeContext;
import com.example.interview.ai.model.ReportContext;
import com.example.interview.ai.model.ResumeAnalysisContext;
import com.example.interview.ai.model.TurnEvaluationContext;
import com.example.interview.interview.entity.InterviewMessage;
import com.example.interview.resume.dto.ResumeDigest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

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

        // R3-4：个性化会话追加"候选人简历要点"段（digest 为 null 时不追加，Prompt 与基线逐字节一致）
        appendResumeDigest(sb, ctx.getResumeDigest());

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

    // ==================== analyzeResume（R2-3，简历方案 §4.2） ====================

    /** 简历多维分析 Prompt：脱敏全文 + 岗位能力模型 + 六维定义与分档标准 → 强约束 JSON。 */
    public String buildAnalyzeResumePrompt(ResumeAnalysisContext ctx) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("你是资深技术面试官与简历评审专家，请对候选人简历做多维评分与分级改进建议。\n\n");
        sb.append("【目标岗位】").append(ctx.getPositionName() != null ? ctx.getPositionName() : "通用软件研发（未指定岗位）")
                .append('\n');
        if (ctx.getPositionSkills() != null && !ctx.getPositionSkills().isEmpty()) {
            sb.append("【岗位能力模型】技能（权重）：");
            List<String> items = ctx.getPositionSkills().stream()
                    .map(s -> s.getName() + "（" + (s.getWeight() != null ? s.getWeight() : "-") + "）")
                    .toList();
            sb.append(String.join("、", items)).append('\n');
        }
        sb.append("\n【简历全文（已脱敏）】\n").append(ctx.getMaskedText()).append('\n');
        sb.append("""

                【六维评分定义（每维 0~100 整数，括号为总分权重）】
                basic 基础信息完整度（0.10）：姓名/联系方式/求职意向齐全，板块结构完整
                education 教育背景（0.10）：学历层次、专业相关度、成绩排名或核心课程
                skill 技能与岗位匹配（0.25）：岗位技能覆盖率、精通/熟悉/了解分层表述、无关技能堆砌扣分
                project 项目经历质量（0.25）：STAR 完整度、量化成果比例、复杂度信号（并发/性能/设计权衡）
                experience 实习与实践经历（0.10）：实习/竞赛/开源/证书与岗位相关度
                expression 表达与排版规范（0.20）：动词开头句比例、量化句比例、篇幅 800~4000 字、重复表述

                【风险分级标准】HIGH=对应维度分 <40（硬伤必须修改）；MEDIUM=40~69（短板建议优化）；LOW=≥70 的优势维度（保持亮点）

                【输出要求】只输出一个 JSON 对象，不要任何解释或 markdown 围栏，结构如下：
                {"dimensionScores":{"basic":0-100,"education":0-100,"skill":0-100,"project":0-100,"experience":0-100,"expression":0-100},
                "strengths":["亮点，最多5条"],
                "suggestions":[{"riskLevel":"HIGH/MEDIUM/LOW","dimension":"六维key","title":"一句话标题，20字内","issue":"问题描述，引用证据说明","suggestion":"具体可执行的修改建议","evidence":"简历原文证据句"}],
                "summary":"总评，500字内"}
                规则：suggestions 共 3~8 条，按风险从高到低排序；evidence 必须引用简历原文句子（找不到时填"无"）；
                不得编造简历中不存在的内容；所有文案使用中文。
                """);
        return sb.toString();
    }

    // ==================== personalizeQuestions（R3-3，简历方案 §4.2） ====================

    /** 题干改写 Prompt：岗位 + 简历摘要 + 题目列表（ID/题型/技能/原题干）→ 强约束 JSON。 */
    public String buildPersonalizePrompt(QuestionPersonalizeContext ctx) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("你是技术面试官，请把下列面试题改写为结合候选人简历背景的个性化题干。\n\n");
        sb.append("【目标岗位】").append(ctx.getPositionName() != null ? ctx.getPositionName() : "")
                .append('\n');
        ResumeDigest digest = ctx.getDigest();
        if (digest != null) {
            sb.append("\n【候选人简历摘要（已脱敏）】\n");
            List<String> skills = digest.getSkills() == null ? List.of()
                    : digest.getSkills().stream().limit(10)
                            .map(s -> s.getName() + (s.getLevel() != null ? "（" + s.getLevel() + "）" : ""))
                            .collect(Collectors.toList());
            if (!skills.isEmpty()) {
                sb.append("技能：").append(String.join("、", skills)).append('\n');
            }
            digestProjects(digest, 3).forEach(p -> sb.append("项目：").append(p).append('\n'));
            if (digest.getExperiences() != null && !digest.getExperiences().isEmpty()) {
                sb.append("经历：").append(digest.getExperiences().stream()
                                .limit(3)
                                .map(e -> (e.getOrg() != null ? e.getOrg() : "") + " "
                                        + (e.getRole() != null ? e.getRole() : ""))
                                .filter(s -> !s.isBlank())
                                .collect(Collectors.joining("；")))
                        .append('\n');
            }
        }
        sb.append("\n【题目列表】\n");
        for (QuestionPersonalizeContext.QuestionBrief q : ctx.getQuestions()) {
            sb.append("ID ").append(q.getQuestionId()).append("（").append(q.getType()).append("）：")
                    .append(abbreviate(q.getContent(), 300)).append('\n');
        }
        sb.append("""


                【输出要求】只输出一个 JSON 对象，不要任何解释或 markdown 围栏，结构如下：
                {"items":[{"questionId":题库ID,"content":"改写后的完整题干"}]}
                规则：每条 content 以"结合你在……的经历——"类前缀绑定候选人的项目/技能，再接原题考查点，长度 10~500 字；
                不得改变题目的考查点与难度，不得泄露参考答案或关键得分点；
                返回 items 必须与输入题目一一对应（questionId 完全一致，不多不少）；所有文案使用中文。
                """);
        return sb.toString();
    }

    // ==================== 公共段 ====================

    /**
     * 候选人简历要点段（R3-4，简历方案 §3.6④ 固定文案）：技术栈 + 主要项目（名+亮点），
     * 明示"追问可结合其项目深挖实现细节、技术选型原因、遇到的困难"；digest 为 null 不追加。
     */
    private void appendResumeDigest(StringBuilder sb, ResumeDigest digest) {
        if (digest == null) {
            return;
        }
        List<String> skills = digest.getSkills() == null ? List.of()
                : digest.getSkills().stream().limit(8).map(ResumeDigest.Skill::getName).toList();
        List<String> projects = digestProjects(digest, 2);
        sb.append("\n【候选人简历要点】");
        if (!skills.isEmpty()) {
            sb.append("技术栈 ").append(String.join("、", skills)).append("；");
        }
        if (!projects.isEmpty()) {
            sb.append("主要项目 ").append(String.join("；", projects)).append("。");
        } else {
            sb.append("项目信息未提供。");
        }
        sb.append("追问可结合其项目深挖实现细节、技术选型原因、遇到的困难。\n");
    }

    /** 项目条目文案：《名》（亮点：xxx）；无亮点句只保留书名号。 */
    private List<String> digestProjects(ResumeDigest digest, int limit) {
        if (digest.getProjects() == null || digest.getProjects().isEmpty()) {
            return List.of();
        }
        return digest.getProjects().stream()
                .limit(limit)
                .map(p -> {
                    String highlight = p.getHighlights() == null || p.getHighlights().isEmpty()
                            ? null : p.getHighlights().get(0);
                    return highlight != null
                            ? "《" + p.getName() + "》（亮点：" + abbreviate(highlight, 60) + "）"
                            : "《" + p.getName() + "》";
                })
                .toList();
    }

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
