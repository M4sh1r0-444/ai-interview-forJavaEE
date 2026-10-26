package com.example.interview.ai.provider;

import com.example.interview.ai.model.AiNextQuestionResult;
import com.example.interview.ai.model.AiReport;
import com.example.interview.ai.model.AiResumeAnalysis;
import com.example.interview.ai.model.AiTurnEvaluation;
import com.example.interview.ai.model.NextQuestionContext;
import com.example.interview.ai.model.ReportContext;
import com.example.interview.ai.model.ResumeAnalysisContext;
import com.example.interview.ai.model.TurnEvaluationContext;
import com.example.interview.question.entity.Question;
import com.example.interview.resume.dto.ResumeProfile;
import com.example.interview.resume.dto.SuggestionDTO;
import com.example.interview.resume.service.ResumeProfileParser;
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

    // ==================== analyzeResume：规则评分引擎（简历计划 §6.6 / R2-2） ====================

    /** 简历六维中文名（与面试报告六维不同维度集，勿混用） */
    private static final Map<String, String> RESUME_DIM_NAMES = Map.of(
            "basic", "基础信息完整度", "education", "教育背景", "skill", "技能与岗位匹配",
            "project", "项目经历质量", "experience", "实习与实践经历", "expression", "表达与排版规范");
    private static final List<String> RESUME_DIM_ORDER =
            List.of("basic", "education", "skill", "project", "experience", "expression");

    /** 通用分析（无岗位）时 skill 覆盖率的对照基准（通用软件研发核心技能，计划 §6.6） */
    private static final List<String> GENERIC_CORE_SKILLS = List.of(
            "Java", "Spring Boot", "MySQL", "Redis", "并发编程", "数据结构", "算法",
            "计算机网络", "操作系统", "消息队列");

    private static final Pattern LEVEL_WORD = Pattern.compile("精通|熟练|熟悉|掌握|了解");
    private static final Pattern STAR_WORDS = Pattern.compile("负责|实现|优化|结果");
    private static final Pattern COMPLEXITY_WORDS = Pattern.compile("并发|压测|分布式|性能|海量");
    private static final Pattern RANK_WORDS = Pattern.compile("排名|前\\d+%|前\\d+名|GPA|主修课程|核心课程");
    private static final Pattern MAJOR_WORDS = Pattern.compile("计算机|软件|信息|数据|人工智能|智能|电子|网络|通信|自动化|数学");
    private static final Pattern INTERNSHIP_WORDS = Pattern.compile("实习");
    private static final Pattern CONTEST_WORDS = Pattern.compile("竞赛|蓝桥杯|ACM|开源|GitHub|Github|github");
    private static final Pattern CERT_WORDS = Pattern.compile("证书|CET|四六级|六级|软考");
    private static final Pattern ACTION_VERB = Pattern.compile(
            "^(?:负责|实现|设计|开发|优化|参与|完成|搭建|编写|维护|使用|采用|主导|推动|修复|分析|研究|组织|带领|独立|深入|Delivered|Responsible|Implemented|Optimized|Built|Fixed)");
    /** 脱敏命中标记（联系方式存在性信号，不还原原文） */
    private static final Pattern MASKED_CONTACT = Pattern.compile("\\*{3,}");

    /**
     * 简历多维分析（计划 §6.6 Mock 规则，与维度定义一一对应，clamp 0~100）：
     * 分档阈值（40/70）与建议数量约束由 ResumeScoreCalculator 统一锁定；
     * 同输入同输出（确定性），信号权重见各行注释。建议证据句取该维度板块首句（脱敏后）。
     */
    @Override
    public AiResumeAnalysis analyzeResume(ResumeAnalysisContext ctx) {
        String text = ctx.getMaskedText() == null ? "" : ctx.getMaskedText();
        ResumeProfile profile = ctx.getProfile() != null ? ctx.getProfile() : new ResumeProfile();
        Map<String, List<String>> sections = profile.getSectionLines() != null
                ? profile.getSectionLines() : Map.of();

        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("basic", clampScore(basicScore(text, profile, sections)));
        scores.put("education", clampScore(educationScore(text, sections)));
        scores.put("skill", clampScore(skillScore(text, profile, sections, ctx)));
        scores.put("project", clampScore(projectScore(text, sections)));
        scores.put("experience", clampScore(experienceScore(text, sections, ctx)));
        scores.put("expression", clampScore(expressionScore(text)));

        AiResumeAnalysis r = new AiResumeAnalysis();
        r.setProvider("mock");
        r.setDimensionScores(scores);
        r.setSuggestions(buildResumeSuggestions(scores, text, sections));
        r.setStrengths(buildResumeStrengths(scores));
        r.setSummary(buildResumeSummary(scores, ctx));
        log.debug("MockAiProvider.analyzeResume resume={} dims={}", ctx.getResumeId(), scores);
        return r;
    }

    /** basic：基础 40；姓名/联系方式（脱敏命中）/求职意向 各 +12；四大板块存在每块 +5。 */
    private double basicScore(String text, ResumeProfile profile, Map<String, List<String>> sections) {
        double score = 40;
        Map<String, String> basic = profile.getBasicInfo();
        if (basic != null && basic.containsKey("姓名")) {
            score += 12;
        }
        if (MASKED_CONTACT.matcher(text).find()) {
            score += 12;
        }
        if (basic != null && basic.containsKey("求职意向")) {
            score += 12;
        }
        int sectionBonus = 0;
        for (String key : List.of("EDUCATION", "SKILL", "PROJECT", "EXPERIENCE")) {
            if (sections.containsKey(key) && !sections.get(key).isEmpty()) {
                sectionBonus += 5;
            }
        }
        // RAW 板块：无标题简历按内容行数近似给板块分（≥8 行视为结构尚可）
        if (sections.containsKey("RAW") && sections.get("RAW").size() >= 8) {
            sectionBonus = Math.max(sectionBonus, 10);
        }
        return score + sectionBonus;
    }

    /** education：基础 40；博士+30/硕士+25/本科+15/大专+8；专业相关词 +15；排名或核心课程 +15。 */
    private double educationScore(String text, Map<String, List<String>> sections) {
        String eduText = String.join("\n", sections.getOrDefault("EDUCATION", List.of()));
        String scope = eduText.isBlank() ? text : eduText;
        double score = 40;
        if (scope.contains("博士")) {
            score += 30;
        } else if (scope.contains("硕士") || scope.contains("研究生")) {
            score += 25;
        } else if (scope.contains("本科")) {
            score += 15;
        } else if (scope.contains("大专") || scope.contains("专科")) {
            score += 8;
        }
        if (MAJOR_WORDS.matcher(scope).find()) {
            score += 15;
        }
        if (RANK_WORDS.matcher(scope).find()) {
            score += 15;
        }
        return score;
    }

    /** skill：基础 30；岗位（或通用核心）技能覆盖率 ×50；分层词出现 +10；堆砌（> 岗位技能×3）−10。 */
    private double skillScore(String text, ResumeProfile profile, Map<String, List<String>> sections,
                              ResumeAnalysisContext ctx) {
        double score = 30;
        List<String> baseline = skillBaseline(ctx);
        long covered = baseline.stream().filter(b -> anySkillCovers(profile, b)).count();
        double coverage = baseline.isEmpty() ? 0 : (double) covered / baseline.size();
        score += coverage * 50;
        String scope = String.join("\n", sections.getOrDefault("SKILL", List.of()));
        if (LEVEL_WORD.matcher(scope.isBlank() ? text : scope).find()) {
            score += 10;
        }
        int skillCount = profile.getSkills() != null ? profile.getSkills().size() : 0;
        List<ResumeAnalysisContext.PositionSkill> positionSkills = ctx.getPositionSkills();
        if (positionSkills != null && !positionSkills.isEmpty()
                && skillCount > positionSkills.size() * 3) {
            score -= 10;
        }
        return score;
    }

    /** 覆盖率对照基准：绑定岗位 → 能力模型技能名；通用分析 → 通用核心技能。 */
    private List<String> skillBaseline(ResumeAnalysisContext ctx) {
        if (ctx.getPositionSkills() != null && !ctx.getPositionSkills().isEmpty()) {
            return ctx.getPositionSkills().stream()
                    .map(ResumeAnalysisContext.PositionSkill::getName)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        return GENERIC_CORE_SKILLS;
    }

    /** 岗位技能被简历技能覆盖：归一化互为包含（"MySQL" 覆盖 "MySQL数据库"），
     *  或英文主词互为前缀（"Spring Boot" 覆盖 "Spring生态"，中英混排技能名）。 */
    private boolean anySkillCovers(ResumeProfile profile, String baselineSkill) {
        if (profile.getSkills() == null) {
            return false;
        }
        String target = ResumeProfileParser.normalizeForMatch(baselineSkill);
        String targetHead = asciiHead(target);
        return profile.getSkills().stream().anyMatch(s -> {
            String n = ResumeProfileParser.normalizeForMatch(s.getName());
            if (n.length() >= 2 && (target.contains(n) || n.contains(target))) {
                return true;
            }
            String head = asciiHead(n);
            return targetHead.length() >= 3 && head.length() >= 3
                    && (targetHead.startsWith(head) || head.startsWith(targetHead));
        });
    }

    /** 首段英文/数字主词（≥3 位才算有效词干，防 go/js 误配）。 */
    private static String asciiHead(String normalized) {
        Matcher m = Pattern.compile("[a-z0-9+#]{3,}").matcher(normalized);
        return m.find() ? m.group() : "";
    }

    /** project：基础 30；项目数 1→+15 / ≥2→+25；STAR 信号词每词 +8（封顶 24）；量化比例 ≥20%→+15 / ≥10%→+8；复杂度词 +6。 */
    private double projectScore(String text, Map<String, List<String>> sections) {
        List<String> lines = sections.getOrDefault("PROJECT", List.of());
        String scope = lines.isEmpty() ? text : String.join("\n", lines);
        double score = 30;
        int projects = countProjects(lines, scope);
        if (projects >= 2) {
            score += 25;
        } else if (projects == 1) {
            score += 15;
        }
        int starHits = 0;
        Matcher star = STAR_WORDS.matcher(scope);
        while (star.find()) {
            starHits++;
        }
        score += Math.min(starHits, 3) * 8.0;
        double quantified = quantifiedRatio(scope);
        if (quantified >= 0.20) {
            score += 15;
        } else if (quantified >= 0.10) {
            score += 8;
        }
        if (COMPLEXITY_WORDS.matcher(scope).find()) {
            score += 6;
        }
        return score;
    }

    /** 项目计数：PROJECT 板块的"项目名：/《》"分段数；无板块时按关键词粗估（0/1）。 */
    private int countProjects(List<String> projectLines, String scope) {
        int heads = 0;
        for (String line : projectLines) {
            if (line.matches("(?:[-*•·]\\s*)?(?:项目名[称]?|项目标题|Project)\\s*[:：].*")
                    || line.startsWith("《")) {
                heads++;
            }
        }
        if (heads > 0) {
            return heads;
        }
        return scope.contains("项目") ? 1 : 0;
    }

    /** experience：基础 40；实习 +25；竞赛/开源 +15；证书 +10；经历与岗位技能相关 +10。 */
    private double experienceScore(String text, Map<String, List<String>> sections,
                                   ResumeAnalysisContext ctx) {
        List<String> lines = sections.getOrDefault("EXPERIENCE", List.of());
        String scope = lines.isEmpty() ? text : String.join("\n", lines);
        double score = 40;
        if (INTERNSHIP_WORDS.matcher(scope).find()) {
            score += 25;
        }
        if (CONTEST_WORDS.matcher(scope).find()) {
            score += 15;
        }
        if (CERT_WORDS.matcher(scope).find()) {
            score += 10;
        }
        List<ResumeAnalysisContext.PositionSkill> positionSkills = ctx.getPositionSkills();
        if (positionSkills != null && !lines.isEmpty()
                && positionSkills.stream().map(ResumeAnalysisContext.PositionSkill::getName)
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(name -> ResumeProfileParser.normalizeForMatch(scope)
                                .contains(ResumeProfileParser.normalizeForMatch(name)))) {
            score += 10;
        }
        return score;
    }

    /** expression：基础 40；动词开头句比例 ≥30%→+15 / ≥10%→+8；量化句比例 +15/+8；字数 800~4000→+15 / 边缘带→+7；重复行 >5→−10。 */
    private double expressionScore(String text) {
        double score = 40;
        double verbStart = verbStartRatio(text);
        if (verbStart >= 0.30) {
            score += 15;
        } else if (verbStart >= 0.10) {
            score += 8;
        }
        double quantified = quantifiedRatio(text);
        if (quantified >= 0.20) {
            score += 15;
        } else if (quantified >= 0.10) {
            score += 8;
        }
        int len = text.length();
        if (len >= 800 && len <= 4000) {
            score += 15;
        } else if ((len >= 400 && len < 800) || (len > 4000 && len <= 8000)) {
            score += 7;
        }
        if (duplicateLines(text) > 5) {
            score -= 10;
        }
        return score;
    }

    /** 动词开头句比例（中英文动作动词，Mock 表达维度信号）。 */
    private double verbStartRatio(String text) {
        List<String> sentences = ResumeProfileParser.splitSentences(text);
        if (sentences.isEmpty()) {
            return 0;
        }
        long verbStart = sentences.stream().filter(s -> ACTION_VERB.matcher(s.strip()).find()).count();
        return (double) verbStart / sentences.size();
    }

    /** 量化句比例（含数字+单位的句子占比，方案 §4.3）。 */
    private double quantifiedRatio(String text) {
        List<String> sentences = ResumeProfileParser.splitSentences(text);
        if (sentences.isEmpty()) {
            return 0;
        }
        long quantified = sentences.stream().filter(ResumeProfileParser::isQuantified).count();
        return (double) quantified / sentences.size();
    }

    /** 完全重复的行数（去重后消失的行数，表达维度扣分信号）。 */
    private int duplicateLines(String text) {
        List<String> lines = text.lines().map(String::strip).filter(s -> !s.isEmpty()).toList();
        long distinct = lines.stream().distinct().count();
        return lines.size() - (int) distinct;
    }

    // ==================== 建议与文案（分级规则 §3.4：维度 <40 HIGH / 40~70 MEDIUM / Top2≥70 LOW） ====================

    private List<SuggestionDTO> buildResumeSuggestions(Map<String, Integer> scores, String text,
                                                       Map<String, List<String>> sections) {
        List<SuggestionDTO> suggestions = new ArrayList<>();
        for (String dim : RESUME_DIM_ORDER) {
            int score = scores.get(dim);
            if (score >= 70) {
                continue;
            }
            suggestions.add(SuggestionDTO.builder()
                    .riskLevel(score < 40 ? "HIGH" : "MEDIUM")
                    .dimension(dim)
                    .title(RESUME_SUGGESTION_TITLES.get(dim))
                    .issue(dimIssue(dim, text, sections))
                    .suggestion(RESUME_SUGGESTION_ADVICE.get(dim))
                    .evidence(sectionEvidence(dim, sections))
                    .build());
        }
        // LOW：分数最高的两个维度（≥70）作为亮点保持项
        scores.entrySet().stream()
                .filter(e -> e.getValue() >= 70)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(2)
                .forEach(e -> suggestions.add(SuggestionDTO.builder()
                        .riskLevel("LOW")
                        .dimension(e.getKey())
                        .title("「" + RESUME_DIM_NAMES.get(e.getKey()) + "」为优势维度")
                        .issue("该维度得分 " + e.getValue() + "，构成简历亮点")
                        .suggestion("保持该维度的现有写法；面试前确保相关表述经得起追问")
                        .evidence(sectionEvidence(e.getKey(), sections))
                        .build()));
        return suggestions;
    }

    private static final Map<String, String> RESUME_SUGGESTION_TITLES = Map.of(
            "basic", "基础信息待完善",
            "education", "教育背景信息不足",
            "skill", "技能与岗位匹配度低",
            "project", "项目经历质量待提升",
            "experience", "实践经历偏薄弱",
            "expression", "表达与排版待优化");

    private static final Map<String, String> RESUME_SUGGESTION_ADVICE = Map.of(
            "basic", "补齐姓名、求职意向与联系方式，并按教育/技能/项目/经历分板块组织简历",
            "education", "写明学历层次与专业，补充 GPA、专业排名或 2~3 门核心课程",
            "skill", "对照目标岗位能力模型补齐高频技能，用精通/熟悉/了解分层表述",
            "project", "按 STAR 结构改写项目（背景-任务-行动-量化结果），每个项目补充 1~2 个量化数据",
            "experience", "补充实习/竞赛/开源等实践经历；暂无可将课程项目按实习粒度展开（角色/周期/产出）",
            "expression", "多用动词开头句（负责/实现/优化），把职责改写为「动作+技术+结果」，控制篇幅 800~4000 字");

    /** issue 文案携带该维度的可解释信号（缺失要素/覆盖率/比例），答辩可复述。 */
    private String dimIssue(String dim, String text, Map<String, List<String>> sections) {
        String scope = String.join("\n", sections.getOrDefault(sectionKeyOf(dim), List.of()));
        String s = scope.isBlank() ? text : scope;
        return switch (dim) {
            case "basic" -> "姓名/联系方式/求职意向等要素不全，板块结构不完整（缺少标题分段的简历难以快速定位信息）";
            case "education" -> {
                List<String> missing = new ArrayList<>();
                if (!MAJOR_WORDS.matcher(s).find()) {
                    missing.add("专业信息");
                }
                if (!RANK_WORDS.matcher(s).find()) {
                    missing.add("排名或核心课程");
                }
                if (!s.contains("本科") && !s.contains("硕士") && !s.contains("博士") && !s.contains("大专")) {
                    missing.add("明确学历层次");
                }
                yield "教育背景缺少：" + String.join("、", missing);
            }
            case "skill" -> "技能表述与岗位能力模型的覆盖率不足，且缺少精通/熟悉/了解的分层表述";
            case "project" -> String.format("项目 STAR 信号不足（负责/实现/优化/结果），量化句比例 %.0f%%，缺少规模/性能等结果数据",
                    quantifiedRatio(s) * 100);
            case "experience" -> "缺少实习、竞赛、开源或证书等实践经历证据";
            case "expression" -> String.format("动词开头句比例 %.0f%%、量化句比例 %.0f%%，表述说服力不足（全文 %d 字符）",
                    verbStartRatio(text) * 100, quantifiedRatio(text) * 100, text.length());
            default -> "该维度存在短板";
        };
    }

    /** 维度 → 板块 key（证据句取该板块首句；expression 无对应板块取全文首句）。 */
    private static String sectionKeyOf(String dim) {
        return switch (dim) {
            case "basic" -> "BASIC";
            case "education" -> "EDUCATION";
            case "skill" -> "SKILL";
            case "project" -> "PROJECT";
            case "experience" -> "EXPERIENCE";
            default -> "RAW";
        };
    }

    private String sectionEvidence(String dim, Map<String, List<String>> sections) {
        List<String> lines = sections.get(sectionKeyOf(dim));
        if (lines == null || lines.isEmpty()) {
            lines = sections.get("RAW");
        }
        if (lines == null || lines.isEmpty()) {
            return "无";
        }
        return ResumeProfileParser.truncate(ResumeProfileParser.firstSentence(lines.get(0)), 80);
    }

    private List<String> buildResumeStrengths(Map<String, Integer> scores) {
        List<String> strengths = new ArrayList<>();
        scores.entrySet().stream()
                .filter(e -> e.getValue() >= 70)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .forEach(e -> strengths.add("「" + RESUME_DIM_NAMES.get(e.getKey())
                        + "」表现较好（" + e.getValue() + " 分）"));
        if (strengths.isEmpty()) {
            strengths.add("简历信息完整，已具备可评估的基础材料");
        }
        return strengths;
    }

    private String buildResumeSummary(Map<String, Integer> scores, ResumeAnalysisContext ctx) {
        String position = ctx.getPositionName() != null ? "对照「" + ctx.getPositionName() + "」岗位，"
                : "未绑定岗位（按通用软件研发标准），";
        List<Map.Entry<String, Integer>> sorted = scores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .toList();
        String low = sorted.get(0).getValue() < 70
                ? "主要短板在" + RESUME_DIM_NAMES.get(sorted.get(0).getKey()) : "各维度均达到合格线";
        String high = sorted.get(sorted.size() - 1).getValue() >= 70
                ? "优势在" + RESUME_DIM_NAMES.get(sorted.get(sorted.size() - 1).getKey()) : "";
        return position + low + (high.isBlank() ? "" : "，" + high)
                + "。建议优先处理高风险（HIGH）条目后重新上传评估。";
    }
}
