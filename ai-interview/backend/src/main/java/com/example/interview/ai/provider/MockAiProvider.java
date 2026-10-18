package com.example.interview.ai.provider;

import com.example.interview.ai.model.AiNextQuestionResult;
import com.example.interview.ai.model.AiReport;
import com.example.interview.ai.model.AiTurnEvaluation;
import com.example.interview.ai.model.NextQuestionContext;
import com.example.interview.ai.model.ReportContext;
import com.example.interview.ai.model.TurnEvaluationContext;
import com.example.interview.question.entity.Question;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 离线 Mock 实现（计划 §1.3 / §6.8）：无网环境可完整演示。
 * nextQuestion 依据追问规则引擎的决策产出模板追问，模板用尽后循环通用追问池。
 * evaluateTurn = 规则评分引擎（计划 §6.6 Mock 评分规则：key_points 命中率→各维分，
 * 全部映射集中本类一处，答辩可解释）；generateReport = 结构化报告拼装。
 */
@Slf4j
@Component
public class MockAiProvider implements AiProvider {

    /** 通用追问池：题目模板用尽时循环取用（计划 §6.8 第 3 级）。 */
    private static final List<String> GENERIC_FOLLOWUPS = List.of(
            "能结合你做过的项目，举一个具体的例子说明吗？",
            "这个方案有什么潜在的风险或边界情况？你是怎么考虑取舍的？",
            "如果流量规模扩大十倍，你刚才的方案需要做哪些调整？");

    private static final Pattern SEGMENT_SPLIT = Pattern.compile(
            "[，,、；;：:？?！!。.（）()【】\\[\\]「」『』“”\"'‘’\\s/／·+＋|｜和与及或者]+");
    private static final Pattern TECH_TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9+.#@_-]{1,}");
    /** 得分点中的通用动词/修饰前缀与名词化后缀，剥离后剩余即匹配用内容词 */
    private static final String[] GENERIC_PREFIXES = {"能", "会", "要", "可", "可以", "准确", "清楚", "清晰",
            "具体", "简单", "简要", "说出", "讲出", "讲清", "说清", "说明", "描述", "举出", "列出", "结合",
            "给出", "提到", "对比", "分析", "解释", "画出", "写出", "了解", "知道", "理解", "使用", "项目", "中"};
    private static final String[] GENERIC_SUFFIXES = {"的定义", "的区别", "的关系", "的例子", "的场景",
            "的机制", "的原理", "的对比", "的实践", "的约定", "的影响", "的方式", "的原则", "的前提",
            "的过程", "的优劣", "的优缺点", "的作用", "的原因"};
    private static final Pattern FILLER = Pattern.compile("嗯|啊|呃|然后|就是说|那个|其实吧|反正");
    private static final Pattern ORDER_MARKER = Pattern.compile("第一|第二|第三|首先|其次|然后|最后|一是|二是|1[\\.、]|2[\\.、]|3[\\.、]");
    private static final Pattern CAUSAL_MARKER = Pattern.compile("因为|所以|因此|导致|由于|从而|进而");

    /** 六维中文名（报告文案用） */
    private static final Map<String, String> DIMENSION_NAMES = Map.of(
            "technical", "技术正确性", "depth", "知识深度", "logic", "逻辑严谨性",
            "match", "岗位匹配度", "communication", "沟通表达", "voice", "语音表现");

    /**
     * 内置技能关键词回退映射（FIX-14）：skillCode → 中文/英文关键词，回答命中任一即视为
     * 覆盖该技能（岗位能力模型未注入 skillNames 时使用；与 V2 种子两岗 7 技能对齐）。
     */
    private static final Map<String, List<String>> SKILL_KEYWORDS = Map.of(
            "java_basics", List.of("java", "基础", "集合"),
            "concurrency_jvm", List.of("并发", "jvm", "线程"),
            "spring", List.of("spring", "ioc", "aop", "事务"),
            "mysql", List.of("mysql", "索引", "sql"),
            "redis_mq", List.of("redis", "缓存", "mq"),
            "project_design", List.of("项目", "设计", "架构"),
            "communication", List.of("沟通", "表达"));

    // ==================== nextQuestion（M2 已交付） ====================

    @Override
    public AiNextQuestionResult nextQuestion(NextQuestionContext ctx) {
        AiNextQuestionResult r = new AiNextQuestionResult();
        boolean followUp = AiNextQuestionResult.ACTION_FOLLOW_UP.equals(ctx.getRequestedAction());
        r.setAction(followUp ? AiNextQuestionResult.ACTION_FOLLOW_UP
                : AiNextQuestionResult.ACTION_NEXT_QUESTION);
        if (followUp) {
            r.setContent(pickFollowupContent(ctx));
        }
        // 主问题序列在 start 时已组卷落库，NEXT_QUESTION 无需内容
        r.setDifficulty(clamp(defaultDiff(ctx) + ctx.getDifficultyDelta()));
        r.setReason("mock:" + ctx.getReasonCode());
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

    // ==================== evaluateTurn：规则评分引擎（M3-3.1） ====================

    /**
     * 逐题评分（计划 §6.6 Mock 评分规则）：
     * technical 由 key_points 命中率线性映射（0→40，1→90）再扣失分点命中；
     * depth 锚定参考答案长度比微调；logic 看结构信号；communication 看字数与语气词密度；
     * match 看技能标签命中率。输入为该轮全部问答（回答按序拼接），逐次追加评分可衡量回升。
     */
    @Override
    public AiTurnEvaluation evaluateTurn(TurnEvaluationContext ctx) {
        Question q = ctx.getQuestion();
        List<String> keyPoints = q != null ? q.getKeyPoints() : null;
        String answer = joinedUserText(ctx);
        String normalized = normalize(answer);

        List<String> covered = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        if (keyPoints != null && !keyPoints.isEmpty()) {
            for (String point : keyPoints) {
                (hitAnyTerm(point, normalized) ? covered : missing).add(point);
            }
        }
        double hitRatio = keyPoints == null || keyPoints.isEmpty() ? 0.5 : (double) covered.size() / keyPoints.size();
        int deductionHits = countDeductionHits(q, normalized);

        AiTurnEvaluation eval = new AiTurnEvaluation();
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("technical", clampScore(40 + 50 * hitRatio - 6.0 * deductionHits));
        scores.put("depth", clampScore(scores.get("technical") + lengthAdjust(answer, q)));
        scores.put("logic", clampScore(logicScore(answer)));
        scores.put("match", clampScore(matchScore(q, normalized, ctx.getSkillNames())));
        scores.put("communication", clampScore(communicationScore(answer)));
        eval.setScores(scores);
        eval.setCoveredPoints(covered);
        eval.setMissingPoints(missing);
        eval.setEvidence(evidenceSentences(answer, covered, normalized));
        eval.setReason(String.format("命中得分点 %d/%d，失分信号 %d 处，回答 %d 字",
                covered.size(), keyPoints != null ? keyPoints.size() : 0, deductionHits, answer.length()));
        eval.setTargetSkills(q != null ? q.getSkillTags() : null);
        eval.setResponseTooVague(answer.length() < 60);
        log.debug("MockAiProvider.evaluateTurn covered={}/{} deduction={} technical={}",
                covered.size(), keyPoints != null ? keyPoints.size() : 0, deductionHits,
                scores.get("technical"));
        return eval;
    }

    /** 该轮全部 USER 回答按序拼接（追问后的回答自然延长累积文本）。 */
    private String joinedUserText(TurnEvaluationContext ctx) {
        if (ctx.getRoundMessages() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        ctx.getRoundMessages().stream()
                .filter(m -> "USER".equals(m.getSpeaker()))
                .forEach(m -> sb.append(m.getContentText() != null ? m.getContentText() : "").append('\n'));
        return sb.toString().trim();
    }

    /** 中文匹配用归一化：去空白、小写。 */
    private String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * 得分点命中：把得分点切分为内容词（剥通用动词前缀/名词后缀，另抽英文技术词），
     * 任一内容词出现在回答中即视为覆盖（无分词器下的可解释近似，词典法排期 M4）。
     */
    private boolean hitAnyTerm(String keyPoint, String normalizedAnswer) {
        return extractTerms(keyPoint).stream().anyMatch(normalizedAnswer::contains);
    }

    private LinkedHashSet<String> extractTerms(String keyPoint) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        Matcher tech = TECH_TOKEN.matcher(keyPoint);
        while (tech.find()) {
            terms.add(tech.group().toLowerCase());
        }
        for (String segment : SEGMENT_SPLIT.split(keyPoint)) {
            String s = segment.trim();
            if (s.isEmpty()) {
                continue;
            }
            s = stripPrefixes(s);
            s = stripSuffixes(s);
            if (s.length() >= 2 && !isGeneric(s)) {
                terms.add(normalize(s));
            }
        }
        return terms;
    }

    private String stripPrefixes(String s) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String p : GENERIC_PREFIXES) {
                if (s.startsWith(p) && s.length() > p.length()) {
                    s = s.substring(p.length());
                    changed = true;
                }
            }
        }
        return s;
    }

    private String stripSuffixes(String s) {
        for (String suffix : GENERIC_SUFFIXES) {
            if (s.endsWith(suffix) && s.length() > suffix.length()) {
                return s.substring(0, s.length() - suffix.length());
            }
        }
        return s;
    }

    private boolean isGeneric(String s) {
        for (String p : GENERIC_PREFIXES) {
            if (p.equals(s)) {
                return true;
            }
        }
        return false;
    }

    /** 失分点命中（同追问引擎 R3 口径：失分点英文技术词全部出现）。 */
    private int countDeductionHits(Question q, String normalizedAnswer) {
        if (q == null || q.getDeductionPoints() == null || normalizedAnswer.isEmpty()) {
            return 0;
        }
        int hits = 0;
        for (String point : q.getDeductionPoints()) {
            Matcher m = TECH_TOKEN.matcher(point);
            boolean hasToken = false;
            boolean allHit = true;
            while (m.find()) {
                hasToken = true;
                if (!normalizedAnswer.contains(m.group().toLowerCase())) {
                    allHit = false;
                    break;
                }
            }
            if (hasToken && allHit) {
                hits++;
            }
        }
        return Math.min(hits, 3);
    }

    /** 深度微调：与参考答案长度比（<0.3 → −12，0.3~0.6 → 0，≥0.6 → +8；≥300 字再 +2）。 */
    private double lengthAdjust(String answer, Question q) {
        int refLen = q != null && q.getReferenceAnswer() != null ? q.getReferenceAnswer().length() : 200;
        double ratio = (double) answer.length() / Math.max(1, refLen);
        if (ratio >= 0.6) {
            return 8 + (answer.length() >= 300 ? 2 : 0);
        }
        return ratio >= 0.3 ? 0 : -12;
    }

    /** 逻辑分：结构信号（序号词/因果词/字数适中）映射。 */
    private double logicScore(String answer) {
        int len = answer.length();
        double score = 50;
        int orderHits = 0;
        Matcher m = ORDER_MARKER.matcher(answer);
        while (m.find()) {
            orderHits++;
        }
        if (orderHits >= 2) {
            score += 12;
        } else if (orderHits == 1) {
            score += 6;
        }
        if (CAUSAL_MARKER.matcher(answer).find()) {
            score += 12;
        }
        if (len >= 200 && len <= 800) {
            score += 12;
        }
        if (len < 60) {
            score -= 15;
        }
        return score;
    }

    /** 沟通分：字数适中加分，语气词密度扣分。 */
    private double communicationScore(String answer) {
        int len = answer.length();
        double score = 70;
        int fillers = 0;
        Matcher m = FILLER.matcher(answer);
        while (m.find()) {
            fillers++;
        }
        double density = fillers / Math.max(1.0, len / 100.0);
        if (density <= 0.5) {
            score += 12;
        } else if (density <= 1.5) {
            score += 4;
        } else if (density > 3) {
            score -= 15;
        }
        if (len < 30) {
            score -= 25;
        } else if (len < 100) {
            score -= 10;
        } else if (len <= 600) {
            score += 6;
        }
        return score;
    }

    /**
     * 匹配分（FIX-14）：技能标签三路命中——英文 stem（下划线前段，如 redis_mq→redis）|
     * 岗位能力模型技能名（含按分隔符拆分的可命中词）| 内置种子关键词，任一出现即覆盖，
     * 全中文回答不再因缺少英文 stem 被压到 55。
     */
    private double matchScore(Question q, String normalizedAnswer, Map<String, String> skillNames) {
        if (q == null || q.getSkillTags() == null || q.getSkillTags().isEmpty()) {
            return 70;
        }
        long hits = q.getSkillTags().stream()
                .filter(tag -> skillTagHit(tag, normalizedAnswer, skillNames))
                .count();
        return 55 + 45.0 * hits / q.getSkillTags().size();
    }

    private boolean skillTagHit(String tag, String normalizedAnswer, Map<String, String> skillNames) {
        String stem = (tag.contains("_") ? tag.substring(0, tag.indexOf('_')) : tag)
                .toLowerCase(java.util.Locale.ROOT);
        if (normalizedAnswer.contains(stem)) {
            return true;
        }
        String skillName = skillNames != null ? skillNames.get(tag) : null;
        if (skillName != null) {
            for (String part : skillName.split("[与/、，,；;\\s]+")) {
                if (part.length() >= 2 && normalizedAnswer.contains(part.toLowerCase(java.util.Locale.ROOT))) {
                    return true;
                }
            }
        }
        List<String> keywords = SKILL_KEYWORDS.get(tag);
        if (keywords != null) {
            for (String kw : keywords) {
                if (normalizedAnswer.contains(kw.toLowerCase(java.util.Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 证据句：命中得分点内容词的原句（最多 3 句，每句截断 80 字）。 */
    private List<String> evidenceSentences(String answer, List<String> covered, String normalized) {
        List<String> evidence = new ArrayList<>();
        if (covered.isEmpty()) {
            return evidence;
        }
        for (String sentence : answer.split("[。！？!?\n；;]")) {
            String ns = normalize(sentence);
            if (ns.isEmpty()) {
                continue;
            }
            boolean hit = false;
            for (String point : covered) {
                if (extractTerms(point).stream().anyMatch(ns::contains)) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                evidence.add(sentence.length() <= 80 ? sentence : sentence.substring(0, 80) + "…");
                if (evidence.size() >= 3) {
                    break;
                }
            }
        }
        return evidence;
    }

    private int clampScore(double v) {
        return (int) Math.round(Math.max(0, Math.min(100, v)));
    }

    // ==================== generateReport：结构化报告拼装（M3-3.2） ====================

    @Override
    public AiReport generateReport(ReportContext ctx) {
        Map<String, Double> dims = ctx.getDimensionScores() != null
                ? ctx.getDimensionScores() : Map.of();
        List<AiTurnEvaluation> turns = ctx.getTurnEvaluations() != null
                ? ctx.getTurnEvaluations() : List.of();

        AiReport report = new AiReport();
        report.setSummary(buildSummary(ctx, dims));
        report.setStrengths(buildStrengths(dims, turns));
        report.setWeaknesses(buildWeaknesses(dims, turns));
        report.setImprovementPlan(buildImprovementPlan(report.getWeaknesses(), dims));
        report.setReportText(buildReportText(ctx, dims, report));
        log.debug("MockAiProvider.generateReport turns={} dims={}", turns.size(), dims.keySet());
        return report;
    }

    private String buildSummary(ReportContext ctx, Map<String, Double> dims) {
        if (ctx.getTurnEvaluations() == null || ctx.getTurnEvaluations().isEmpty()) {
            return "本场面试未产生有效作答，建议重新开始一场完整面试。";
        }
        StringBuilder sb = new StringBuilder("【").append(ctx.getPositionName()).append("】模拟面试总分 ")
                .append(ctx.getTotalScore() != null ? ctx.getTotalScore() : 0).append(" 分：");
        List<String> parts = new ArrayList<>();
        dims.forEach((dim, score) -> parts.add(DIMENSION_NAMES.getOrDefault(dim, dim) + " "
                + trim(score) + " 分"));
        sb.append(String.join("、", parts)).append("。");
        return sb.toString();
    }

    private List<String> buildStrengths(Map<String, Double> dims, List<AiTurnEvaluation> turns) {
        List<String> strengths = new ArrayList<>();
        dims.entrySet().stream()
                .filter(e -> e.getValue() >= 78)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(2)
                .forEach(e -> strengths.add("「" + DIMENSION_NAMES.getOrDefault(e.getKey(), e.getKey())
                        + "」表现较好（" + trim(e.getValue()) + " 分）"));
        double coverage = avgCoverage(turns);
        if (coverage >= 0.7) {
            strengths.add("关键得分点覆盖较全（平均覆盖率 " + Math.round(coverage * 100) + "%）");
        }
        if (strengths.isEmpty()) {
            strengths.add("完整经历了全部轮次的追问与作答，面试耐力可圈可点");
        }
        return strengths;
    }

    private List<String> buildWeaknesses(Map<String, Double> dims, List<AiTurnEvaluation> turns) {
        List<String> weaknesses = new ArrayList<>();
        dims.entrySet().stream()
                .filter(e -> e.getValue() < 75)
                .sorted(Map.Entry.comparingByValue())
                .limit(2)
                .forEach(e -> weaknesses.add("「" + DIMENSION_NAMES.getOrDefault(e.getKey(), e.getKey())
                        + "」有待加强（" + trim(e.getValue()) + " 分）"));
        topMissingPoint(turns).ifPresent(p -> weaknesses.add("常见缺失要点：「" + p + "」"));
        return weaknesses;
    }

    private List<String> buildImprovementPlan(List<String> weaknesses, Map<String, Double> dims) {
        List<String> plan = new ArrayList<>();
        Map<String, String> advice = Map.of(
                "technical", "重刷该岗位高频面试题，整理易错概念对比表，每天 3 题口述练习。",
                "depth", "对每个考点补充底层机制与取舍：读官方文档或源码导读，回答时讲「为什么」。",
                "logic", "练习「结论—依据—例子」三段式作答，先总后分，用序号词组织要点。",
                "match", "对照岗位能力模型补齐高频技能词，作答时主动关联岗位真实场景。",
                "communication", "录音复盘自己的表达：减少语气词，控制每题 1~3 分钟的信息密度。",
                "voice", "放慢语速、减少长停顿，重要结论前稍作停顿组织语言。");
        dims.entrySet().stream()
                .filter(e -> e.getValue() < 75)
                .sorted(Map.Entry.comparingByValue())
                .limit(3)
                .forEach(e -> plan.add("针对「" + DIMENSION_NAMES.getOrDefault(e.getKey(), e.getKey())
                        + "」：" + advice.getOrDefault(e.getKey(), "针对性补强该维度。")));
        if (plan.isEmpty()) {
            plan.add("保持当前练习节奏，下一场尝试更高难度档位巩固优势。");
        }
        return plan;
    }

    private String buildReportText(ReportContext ctx, Map<String, Double> dims, AiReport report) {
        int rounds = ctx.getTurnEvaluations() != null ? ctx.getTurnEvaluations().size() : 0;
        StringBuilder sb = new StringBuilder(512);
        sb.append("本场为【").append(ctx.getPositionName()).append("】岗位的模拟面试，难度档位 ")
                .append(ctx.getDifficulty() != null ? ctx.getDifficulty() : 3)
                .append("，共完成 ").append(rounds).append(" 个主问题轮次。");
        if (rounds == 0) {
            sb.append("候选人未作答，报告仅供参考。");
            return sb.toString();
        }
        sb.append(report.getSummary());
        if (!report.getStrengths().isEmpty()) {
            sb.append("亮点：").append(String.join("；", report.getStrengths())).append("。");
        }
        if (!report.getWeaknesses().isEmpty()) {
            sb.append("不足：").append(String.join("；", report.getWeaknesses())).append("。");
        }
        sb.append("建议下一步：").append(String.join(" ", report.getImprovementPlan()));
        return sb.toString();
    }

    private double avgCoverage(List<AiTurnEvaluation> turns) {
        if (turns.isEmpty()) {
            return 0;
        }
        double sum = 0;
        int n = 0;
        for (AiTurnEvaluation t : turns) {
            int covered = t.getCoveredPoints() != null ? t.getCoveredPoints().size() : 0;
            int missing = t.getMissingPoints() != null ? t.getMissingPoints().size() : 0;
            if (covered + missing > 0) {
                sum += (double) covered / (covered + missing);
                n++;
            }
        }
        return n > 0 ? sum / n : 0;
    }

    /** 出现次数最多的缺失要点；不足两轮出现的算不上“常见”，不输出。 */
    private java.util.Optional<String> topMissingPoint(List<AiTurnEvaluation> turns) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        turns.stream()
                .filter(t -> t.getMissingPoints() != null)
                .flatMap(t -> t.getMissingPoints().stream())
                .forEach(p -> counts.merge(p, 1, Integer::sum));
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() >= 2)
                .map(Map.Entry::getKey);
    }

    private String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }
}
