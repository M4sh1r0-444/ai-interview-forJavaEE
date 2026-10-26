package com.example.interview.assessment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.ai.model.AiReport;
import com.example.interview.ai.model.AiTurnEvaluation;
import com.example.interview.ai.model.ReportContext;
import com.example.interview.ai.model.TurnEvaluationContext;
import com.example.interview.ai.provider.AiProvider;
import com.example.interview.assessment.dto.QuestionEvidenceVO;
import com.example.interview.assessment.dto.ReportVO;
import com.example.interview.assessment.engine.ScoreCalculator;
import com.example.interview.assessment.engine.VoiceMetricsCalculator;
import com.example.interview.assessment.entity.EvaluationReport;
import com.example.interview.assessment.mapper.EvaluationReportMapper;
import com.example.interview.auth.security.CurrentUser;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import com.example.interview.common.util.JsonUtils;
import com.example.interview.growth.engine.MasteryUpdater;
import com.example.interview.interview.entity.InterviewMessage;
import com.example.interview.interview.entity.InterviewSession;
import com.example.interview.interview.mapper.InterviewMessageMapper;
import com.example.interview.interview.mapper.InterviewSessionMapper;
import com.example.interview.interview.statemachine.InterviewStatus;
import com.example.interview.interview.websocket.InterviewWebSocketHandler;
import com.example.interview.interview.websocket.WsMessageType;
import com.example.interview.position.dto.CompetencyModel;
import com.example.interview.position.entity.Position;
import com.example.interview.position.mapper.PositionMapper;
import com.example.interview.question.entity.Question;
import com.example.interview.question.mapper.QuestionMapper;
import com.example.interview.resume.dto.ResumeDigest;
import com.example.interview.resume.service.ResumeSkillMatcher;
import com.example.interview.resume.entity.Resume;
import com.example.interview.resume.mapper.ResumeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 报告生成与读取（计划 §6.6 / 方案 §13.2 finish 编排）：
 * 逐题评分（暂存缺漏轮次确定性重算）→ ScoreCalculator 六维聚合与总分 →
 * AiProvider.generateReport（真实 Provider 三级容错 M4 接入）→ evaluation_report
 * 落库 → 回填 session.total_score → MasteryUpdater 写 ability_record（M5-5.1）→
 * WS 推 REPORT_READY。
 * 触发点：finish 接口（含失败重试入口）、TurnEngine 轮次用尽、定时器 TIMEOUT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final QuestionMapper questionMapper;
    private final PositionMapper positionMapper;
    private final EvaluationReportMapper reportMapper;
    private final AiProvider aiProvider;
    private final ScoreCalculator calculator;
    private final VoiceMetricsCalculator voiceCalculator;
    private final TurnEvaluationStore evaluationStore;
    private final InterviewWebSocketHandler wsHandler;
    private final TransactionTemplate transactionTemplate;
    private final MasteryUpdater masteryUpdater;
    private final ApplicationEventPublisher eventPublisher;
    private final ResumeMapper resumeMapper;

    /** 异步入口（计划 §4.4：报告生成不阻塞请求/定时器线程）。 */
    @Async("aiExecutor")
    public void generateAsync(Long sessionId) {
        try {
            generate(sessionId);
        } catch (Exception e) {
            // FIX-16：失败路径同样清理评分暂存（重试时走确定性重算），防长期运行泄漏
            evaluationStore.clear(sessionId);
            log.error("报告生成失败 session={}，可通过重新 finish 触发重试", sessionId, e);
            wsHandler.push(sessionId, WsMessageType.ERROR, Map.of(
                    "code", ResultCode.REPORT_NOT_READY.getCode(),
                    "message", "报告生成失败，可重新调用 finish 接口重试"));
        }
    }

    /**
     * 生成并落库。仅 FINISHED/TIMEOUT 生成（ABORTED 不出报告）；已有报告幂等返回。
     */
    void generate(Long sessionId) {
        InterviewSession s = sessionMapper.selectById(sessionId);
        if (s == null) {
            return;
        }
        InterviewStatus status = InterviewStatus.valueOf(s.getStatus());
        if (status != InterviewStatus.FINISHED && status != InterviewStatus.TIMEOUT) {
            log.info("跳过报告生成 session={} status={}", sessionId, status);
            return;
        }
        EvaluationReport existing = findBySessionId(sessionId);
        if (existing != null) {
            wsHandler.push(sessionId, WsMessageType.REPORT_READY, Map.of("reportId", existing.getId()));
            return;
        }

        Position position = positionMapper.selectById(s.getPositionId());
        CompetencyModel model = parseModel(position);
        Aggregation agg = aggregate(s, position, model);

        ResumeDigest digest = resumeDigestOf(s);
        AiReport aiReport = aiProvider.generateReport(ReportContext.builder()
                .sessionId(sessionId)
                .userId(s.getUserId())
                .positionName(position != null ? position.getName() : "")
                .difficulty(s.getDifficulty())
                .roundCount(agg.rounds.size())
                .totalScore(agg.total)
                .turnEvaluations(agg.latestPerRound)
                .dimensionScores(agg.dimensions)
                .resumeDigest(digest) // R3-5：个性化报告注入简历要点
                .build());
        // R3-5"简历 vs 实测"对比（简历方案 §3.6⑤）：Mock 与真实 Provider 同规则，
        // 追加进既有 strengths/weaknesses（evaluation_report 不加列，前端按前缀渲染）
        appendResumeComparison(aiReport, digest, agg);

        EvaluationReport report = new EvaluationReport();
        report.setSessionId(sessionId);
        report.setTotalScore(BigDecimal.valueOf(agg.total).setScale(2, RoundingMode.HALF_UP));
        report.setDimensionScores(agg.dimensions);
        report.setStrengths(aiReport.getStrengths());
        report.setWeaknesses(aiReport.getWeaknesses());
        report.setImprovementPlan(aiReport.getImprovementPlan());
        report.setReportText(joinText(aiReport));
        report.setQuestionEvidence(agg.evidenceList()); // FIX-07：逐题证据随报告落库

        try {
            transactionTemplate.executeWithoutResult(tx -> {
                reportMapper.insert(report);
                InterviewSession upd = new InterviewSession();
                upd.setId(sessionId);
                upd.setTotalScore(report.getTotalScore());
                sessionMapper.updateById(upd);
            });
        } catch (DuplicateKeyException | DeadlockLoserDataAccessException e) {
            // 并发生成（如轮次用尽与 finish 重试同时触发）：以先落库者为准，
            // 下方统一 findBySessionId 加载已有报告并推 REPORT_READY（FIX-12）；
            // 掌握度由 uk_ability_source_skill + 逐条捕获冲突/死锁重试保证败者安全重入
            log.info("报告并发写入（{}），取已有报告 session={}", e.getClass().getSimpleName(), sessionId);
        }
        // 掌握度更新：报告事务提交后独立事务追加 ability_record（计划 §4.4/§6.6；
        // 以 source_session_id 幂等，并发生成路径不会重复折算）
        masteryUpdater.applyForSession(s.getUserId(), s.getPositionId(), sessionId,
                masteryUpdater.sessionSkillScores(agg.skillTurns));
        evaluationStore.clear(sessionId);
        // 败者路径下胜者可能尚未提交，短暂有界等待后取已有报告（FIX-12），正常路径立即命中
        EvaluationReport saved = waitForReport(sessionId, 2_000);
        if (saved != null) {
            wsHandler.push(sessionId, WsMessageType.REPORT_READY, Map.of("reportId", saved.getId()));
            log.info("报告生成完成 session={} reportId={} total={} rounds={}",
                    sessionId, saved.getId(), saved.getTotalScore(), agg.rounds.size());
        }
        // 推荐缓存预生成（计划 §6.6/§6.7：Redis TTL 10min）；监听器在当前异步线程执行
        eventPublisher.publishEvent(new ReportGeneratedEvent(sessionId, s.getUserId(), s.getPositionId()));
        // FIX-16：报告是该会话最后一次 AI 调用，释放 Provider 会话状态（失败计数/熔断锁）
        aiProvider.releaseSession(sessionId);
    }

    /** 报告读取（计划 §5.3 #17）：未生成 10004；归属本人或 ADMIN。FIX-07：读取不再调用 AI。 */
    public ReportVO getReport(Long sessionId) {
        InterviewSession s = sessionMapper.selectById(sessionId);
        if (s == null || (!CurrentUser.isAdmin() && !s.getUserId().equals(CurrentUser.currentUserId()))) {
            throw new BizException(ResultCode.NOT_FOUND_OR_FORBIDDEN);
        }
        EvaluationReport report = findBySessionId(sessionId);
        if (report == null) {
            throw new BizException(ResultCode.REPORT_NOT_READY,
                    "报告尚未生成：面试结束会自动生成，失败可重新调用 finish 重试");
        }
        Position position = positionMapper.selectById(s.getPositionId());
        String text = report.getReportText() != null ? report.getReportText() : "";
        int split = text.indexOf("\n\n");
        return ReportVO.builder()
                .reportId(report.getId())
                .sessionId(sessionId)
                .positionName(position != null ? position.getName() : null)
                .mode(s.getMode())
                .status(s.getStatus())
                .totalScore(report.getTotalScore())
                .dimensionScores(report.getDimensionScores())
                .summary(split > 0 ? text.substring(0, split) : text)
                .strengths(report.getStrengths())
                .weaknesses(report.getWeaknesses())
                .improvementPlan(report.getImprovementPlan())
                .reportText(split > 0 ? text.substring(split + 2) : "")
                .questionEvidence(evidenceForRead(s, position, report))
                .createdAt(report.getCreatedAt())
                .build();
    }

    /**
     * 读取侧证据来源（FIX-07）：优先报告落库快照；仅 V7 前旧报告（列为 NULL）
     * 按旧逻辑确定性重算一次并回填，避免每次读取都调用 AI Provider。
     */
    private List<QuestionEvidenceVO> evidenceForRead(InterviewSession s, Position position,
                                                     EvaluationReport report) {
        if (report.getQuestionEvidence() != null) {
            return report.getQuestionEvidence();
        }
        List<QuestionEvidenceVO> recomputed = aggregate(s, position, parseModel(position)).evidenceList();
        backfillEvidence(report.getId(), recomputed);
        return recomputed;
    }

    /** 旧报告证据回填（一次性行为，之后读取走快照）。 */
    private void backfillEvidence(Long reportId, List<QuestionEvidenceVO> evidence) {
        try {
            EvaluationReport upd = new EvaluationReport();
            upd.setId(reportId);
            upd.setQuestionEvidence(evidence);
            reportMapper.updateById(upd);
            log.info("旧报告证据已重算并回填 reportId={}", reportId);
        } catch (Exception e) {
            log.warn("旧报告证据回填失败 reportId={}：{}", reportId, e.getMessage());
        }
    }

    /** 已生成报告的 id；未生成返回 null（finish 幂等/重试判断用）。 */
    public Long reportIdOf(Long sessionId) {
        EvaluationReport report = findBySessionId(sessionId);
        return report != null ? report.getId() : null;
    }

    /**
     * 会话逐题证据（含 missingPoints/技能标签）：报告读取与推荐引擎共用
     * （M5-5.3 规则③与错题重练的数据源）。FIX-07：优先报告落库快照，不再隐式调 AI；
     * 仅旧报告（无快照）确定性重算一次并回填。
     */
    public List<QuestionEvidenceVO> evidenceOf(Long sessionId) {
        InterviewSession s = sessionMapper.selectById(sessionId);
        if (s == null) {
            return List.of();
        }
        EvaluationReport report = findBySessionId(sessionId);
        if (report != null && report.getQuestionEvidence() != null) {
            return report.getQuestionEvidence();
        }
        Position position = positionMapper.selectById(s.getPositionId());
        List<QuestionEvidenceVO> evidence = aggregate(s, position, parseModel(position)).evidenceList();
        if (report != null) {
            backfillEvidence(report.getId(), evidence);
        }
        return evidence;
    }

    // ==================== 聚合 ====================

    /** 一轮评分的归组结果。 */
    private record RoundData(Long questionId, String askText, int difficulty, List<String> skillTags,
                             String joinedAnswers, List<AiTurnEvaluation> evaluations) {
    }

    /** 逐题评分 + 六维聚合 + 总分的一次性计算结果（生成与读取共用）。 */
    private record Aggregation(Map<Integer, RoundData> rounds, List<AiTurnEvaluation> latestPerRound,
                               List<QuestionEvidenceVO> evidenceList, Map<String, Double> dimensions,
                               List<MasteryUpdater.SkillTurn> skillTurns,
                               double content, double expression, double adaptability, double total) {
    }

    private Aggregation aggregate(InterviewSession s, Position position, CompetencyModel model) {
        List<InterviewMessage> messages = messageMapper.selectList(
                Wrappers.<InterviewMessage>lambdaQuery()
                        .eq(InterviewMessage::getSessionId, s.getId())
                        .orderByAsc(InterviewMessage::getId));
        Map<Integer, List<InterviewMessage>> byRound = new TreeMap<>();
        for (InterviewMessage m : messages) {
            byRound.computeIfAbsent(m.getRoundNo(), k -> new ArrayList<>()).add(m);
        }

        CompetencyModel.DimensionWeights contentW = weightsOf(model, true);
        CompetencyModel.DimensionWeights expressionW = weightsOf(model, false);
        Map<String, String> skillNames = skillNameMapOf(model);
        Map<Integer, RoundData> rounds = new TreeMap<>();
        List<ScoreCalculator.TurnScores> turnScores = new ArrayList<>();
        List<List<Double>> contentSequences = new ArrayList<>();
        List<QuestionEvidenceVO> evidenceList = new ArrayList<>();
        List<AiTurnEvaluation> latestPerRound = new ArrayList<>();
        List<MasteryUpdater.SkillTurn> skillTurns = new ArrayList<>();

        for (Map.Entry<Integer, List<InterviewMessage>> e : byRound.entrySet()) {
            List<InterviewMessage> roundMsgs = e.getValue();
            if (roundMsgs.stream().noneMatch(m -> "USER".equals(m.getSpeaker()))) {
                continue; // 未作答的轮次不参与评分
            }
            RoundData round = buildRoundData(s, roundMsgs, skillNames);
            rounds.put(e.getKey(), round);

            List<AiTurnEvaluation> evals = round.evaluations();
            AiTurnEvaluation latest = evals.get(evals.size() - 1);
            latestPerRound.add(latest);
            turnScores.add(new ScoreCalculator.TurnScores(latest.getScores(),
                    ScoreCalculator.difficultyWeight(round.difficulty())));

            List<Double> seq = new ArrayList<>();
            for (AiTurnEvaluation eval : evals) {
                seq.add(calculator.contentScore(eval.getScores(), contentW));
            }
            contentSequences.add(seq);
            // 掌握度输入：该轮最新内容分 + 题目技能标签与难度权重（计划 §6.7）
            skillTurns.add(new MasteryUpdater.SkillTurn(round.skillTags(), turnScores.get(turnScores.size() - 1).weight(),
                    seq.get(seq.size() - 1)));
            evidenceList.add(QuestionEvidenceVO.builder()
                    .roundNo(e.getKey())
                    .questionId(round.questionId())
                    .questionContent(round.askText())
                    .answerExcerpt(round.joinedAnswers().length() <= 300 ? round.joinedAnswers()
                            : round.joinedAnswers().substring(0, 300) + "…")
                    .difficulty(round.difficulty())
                    .targetSkills(round.skillTags())
                    .scores(latest.getScores())
                    .contentScore(round2(seq.get(seq.size() - 1)))
                    .coveredPoints(latest.getCoveredPoints())
                    .missingPoints(latest.getMissingPoints())
                    .evidence(latest.getEvidence())
                    .reason(latest.getReason())
                    .build());
        }

        Map<String, Double> dimensions = calculator.aggregateDimensions(turnScores);
        double content = weightedAvgOfTurns(turnScores, t -> calculator.contentScore(t.scores(), contentW));
        double expression = weightedAvgOfTurns(turnScores, t -> calculator.expressionScore(t.scores(), expressionW));
        double adaptability = calculator.adaptabilityScore(contentSequences);
        double match = dimensions.getOrDefault("match", 60.0);
        CompetencyModel.TotalWeights totalW = model != null && model.getScoreWeights() != null
                ? model.getScoreWeights().getTotal() : null;
        double total = rounds.isEmpty() ? 0.0
                : calculator.totalScore(content, expression, adaptability, match, totalW);
        return new Aggregation(rounds, latestPerRound, evidenceList, dimensions, skillTurns,
                content, expression, adaptability, total);
    }

    private double weightedAvgOfTurns(List<ScoreCalculator.TurnScores> turns,
                                      java.util.function.ToDoubleFunction<ScoreCalculator.TurnScores> fn) {
        return calculator.weightedAverage(turns.stream()
                .map(t -> new double[]{fn.applyAsDouble(t), t.weight()})
                .toList());
    }

    /**
     * 单轮数据：主问题（该轮首条 AI 消息）定难度/技能；评分快照优先取运行期暂存，
     * 缺漏时按累积口径逐次重算（第 i 次评分对应前 i+1 条回答，与 TurnEngine 追加一致）。
     */
    private RoundData buildRoundData(InterviewSession s, List<InterviewMessage> roundMsgs,
                                     Map<String, String> skillNames) {
        InterviewMessage mainQuestion = roundMsgs.stream()
                .filter(m -> "AI".equals(m.getSpeaker()))
                .findFirst()
                .orElse(null);
        Question question = mainQuestion != null && mainQuestion.getQuestionId() != null
                ? questionMapper.selectById(mainQuestion.getQuestionId()) : null;
        int difficulty = question != null && question.getDifficulty() != null
                ? question.getDifficulty() : s.getDifficulty();
        List<String> answers = roundMsgs.stream()
                .filter(m -> "USER".equals(m.getSpeaker()))
                .map(m -> m.getContentText() != null ? m.getContentText() : "")
                .toList();

        List<AiTurnEvaluation> evaluations = evaluationStore.get(s.getId(), roundMsgs.get(0).getRoundNo());
        if (evaluations == null || evaluations.size() != answers.size()) {
            evaluations = new ArrayList<>();
            for (int i = 0; i < answers.size(); i++) {
                AiTurnEvaluation eval = aiProvider.evaluateTurn(TurnEvaluationContext.builder()
                        .sessionId(s.getId())
                        .userId(s.getUserId())
                        .question(question)
                        .roundMessages(roundMsgs.subList(0, indexOfNthUser(roundMsgs, i) + 1))
                        .knowledgeChunks(List.of())
                        .skillNames(skillNames)
                        .build());
                // AUDIO 场重算路径同样补 voice 维度（与 TurnEngine 运行期口径一致，M4-4.2）
                if (eval.getScores() != null && "AUDIO".equals(s.getMode())) {
                    voiceCalculator.applyVoiceDimension(eval.getScores(),
                            VoiceMetricsCalculator.joinedTranscript(roundMsgs),
                            VoiceMetricsCalculator.sumDurationMs(roundMsgs));
                }
                evaluations.add(eval);
            }
        }
        return new RoundData(mainQuestion != null ? mainQuestion.getQuestionId() : null,
                mainQuestion != null ? mainQuestion.getContentText() : "",
                difficulty, question != null ? question.getSkillTags() : null,
                String.join("\n", answers), evaluations);
    }

    /** 第 n 条（0-based）USER 消息在轮内消息列表中的下标。 */
    private int indexOfNthUser(List<InterviewMessage> roundMsgs, int n) {
        int seen = -1;
        for (int i = 0; i < roundMsgs.size(); i++) {
            if ("USER".equals(roundMsgs.get(i).getSpeaker()) && ++seen == n) {
                return i;
            }
        }
        return roundMsgs.size() - 1;
    }

    private CompetencyModel.DimensionWeights weightsOf(CompetencyModel model, boolean content) {
        if (model == null || model.getScoreWeights() == null) {
            return null;
        }
        return content ? model.getScoreWeights().getContent() : model.getScoreWeights().getExpression();
    }

    private CompetencyModel parseModel(Position position) {
        if (position == null || position.getCompetencyModelJson() == null) {
            return null;
        }
        try {
            return JsonUtils.parse(position.getCompetencyModelJson(), CompetencyModel.class);
        } catch (Exception e) {
            log.warn("能力模型解析失败 positionId={}: {}", position.getId(), e.getMessage());
            return null;
        }
    }

    /** skillCode → 技能名（FIX-14：重算路径同样注入评分上下文）。 */
    private Map<String, String> skillNameMapOf(CompetencyModel model) {
        if (model == null || model.getSkills() == null) {
            return Map.of();
        }
        Map<String, String> names = new TreeMap<>();
        model.getSkills().forEach(sk -> {
            if (sk.getSkillCode() != null && sk.getSkillName() != null) {
                names.put(sk.getSkillCode(), sk.getSkillName());
            }
        });
        return names;
    }

    /** summary 与正文合并存 report_text 列（无独立列，9 表约束），读取时按首个空行拆分。 */
    private String joinText(AiReport report) {
        return (report.getSummary() != null ? report.getSummary() : "") + "\n\n"
                + (report.getReportText() != null ? report.getReportText() : "");
    }

    // ==================== R3-5：简历 vs 实测对比（简历方案 §3.6⑤） ====================

    /** 个性化会话（resume_id 非空）加载 digest；缺失/解析失败返回 null（报告按普通口径）。 */
    private ResumeDigest resumeDigestOf(InterviewSession s) {
        if (s.getResumeId() == null) {
            return null;
        }
        Resume resume = resumeMapper.selectById(s.getResumeId());
        if (resume == null || resume.getDigestJson() == null) {
            log.warn("个性化会话简历摘要缺失 session={}，报告不追加对比条目", s.getId());
            return null;
        }
        try {
            return JsonUtils.parse(resume.getDigestJson(), ResumeDigest.class);
        } catch (Exception e) {
            log.warn("简历摘要解析失败 session={} resume={}：{}", s.getId(), s.getResumeId(), e.getMessage());
            return null;
        }
    }

    /**
     * "digest 技能 ↔ 相关题目内容分"映射：简历自述技能对应题目平均分 <60 → weaknesses
     * 追加"简历中自述掌握 {skill}，但相关题目表现偏弱（{score} 分），建议重点巩固"；
     * ≥80 → strengths 追加"简历中的 {skill} 经受住了追问验证"。每侧至多 2 条（取最极端），
     * 无相关题目（题库未覆盖该技能）的技能不输出。
     */
    private void appendResumeComparison(AiReport report, ResumeDigest digest, Aggregation agg) {
        if (digest == null || digest.getSkills() == null || digest.getSkills().isEmpty()
                || agg.skillTurns.isEmpty()
                || report.getWeaknesses() == null || report.getStrengths() == null) {
            return;
        }
        List<SkillComparison> comparisons = new ArrayList<>();
        for (ResumeDigest.Skill skill : digest.getSkills()) {
            if (skill == null || skill.getName() == null) {
                continue;
            }
            String normalized = ResumeSkillMatcher.normalize(skill.getName());
            List<double[]> related = new ArrayList<>(); // {score, weight}
            for (MasteryUpdater.SkillTurn turn : agg.skillTurns) {
                boolean hit = turn.skillTags() != null && turn.skillTags().stream()
                        .anyMatch(tag -> ResumeSkillMatcher.tagMatches(tag, normalized));
                if (hit) {
                    related.add(new double[]{turn.score(), turn.weight()});
                }
            }
            if (related.isEmpty()) {
                continue; // 本场未考察该技能，无从对比
            }
            double avg = calculator.weightedAverage(related);
            comparisons.add(new SkillComparison(skill.getName(), avg));
        }
        comparisons.sort(java.util.Comparator.comparingDouble(SkillComparison::score));
        comparisons.stream().limit(2)
                .filter(c -> c.score() < 60)
                .forEach(c -> report.getWeaknesses().add(
                        "简历中自述掌握 " + c.name() + "，但相关题目表现偏弱（"
                                + Math.round(c.score()) + " 分），建议重点巩固"));
        comparisons.stream()
                .filter(c -> c.score() >= 80)
                .sorted(java.util.Comparator.comparingDouble(SkillComparison::score).reversed())
                .limit(2)
                .forEach(c -> report.getStrengths().add(
                        "简历中的 " + c.name() + " 经受住了追问验证（相关题目平均 "
                                + Math.round(c.score()) + " 分）"));
        if (!comparisons.isEmpty()) {
            log.info("报告追加简历对比条目 session 相关技能={} 弱项≤{} 强项≤{}",
                    comparisons.size(),
                    report.getWeaknesses().size(), report.getStrengths().size());
        }
    }

    /** 技能对比中间结果（技能名 + 相关题目加权平均内容分）。 */
    private record SkillComparison(String name, double score) {
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    // ==================== 查询 ====================

    private EvaluationReport findBySessionId(Long sessionId) {
        return reportMapper.selectOne(Wrappers.<EvaluationReport>lambdaQuery()
                .eq(EvaluationReport::getSessionId, sessionId));
    }

    /** 最多等 timeoutMs 毫秒取该会话报告（100ms 轮询）；并发败者等胜者提交用，正常路径首轮命中。 */
    private EvaluationReport waitForReport(Long sessionId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        EvaluationReport report = findBySessionId(sessionId);
        while (report == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            report = findBySessionId(sessionId);
        }
        return report;
    }
}
