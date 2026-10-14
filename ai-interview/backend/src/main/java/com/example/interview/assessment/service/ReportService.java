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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 落库 → 回填 session.total_score → WS 推 REPORT_READY。
 * 触发点：finish 接口（含失败重试入口）、TurnEngine 轮次用尽、定时器 TIMEOUT。
 * 掌握度更新（MasteryUpdater）与推荐缓存排期 M5。
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

    /** 异步入口（计划 §4.4：报告生成不阻塞请求/定时器线程）。 */
    @Async("aiExecutor")
    public void generateAsync(Long sessionId) {
        try {
            generate(sessionId);
        } catch (Exception e) {
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

        AiReport aiReport = aiProvider.generateReport(ReportContext.builder()
                .sessionId(sessionId)
                .positionName(position != null ? position.getName() : "")
                .difficulty(s.getDifficulty())
                .roundCount(agg.rounds.size())
                .totalScore(agg.total)
                .turnEvaluations(agg.latestPerRound)
                .dimensionScores(agg.dimensions)
                .build());

        EvaluationReport report = new EvaluationReport();
        report.setSessionId(sessionId);
        report.setTotalScore(BigDecimal.valueOf(agg.total).setScale(2, RoundingMode.HALF_UP));
        report.setDimensionScores(agg.dimensions);
        report.setStrengths(aiReport.getStrengths());
        report.setWeaknesses(aiReport.getWeaknesses());
        report.setImprovementPlan(aiReport.getImprovementPlan());
        report.setReportText(joinText(aiReport));

        try {
            transactionTemplate.executeWithoutResult(tx -> {
                reportMapper.insert(report);
                InterviewSession upd = new InterviewSession();
                upd.setId(sessionId);
                upd.setTotalScore(report.getTotalScore());
                sessionMapper.updateById(upd);
            });
        } catch (DuplicateKeyException e) {
            // 并发生成（如轮次用尽与 finish 重试同时触发）：以先落库者为准
            log.info("报告并发写入，取已有报告 session={}", sessionId);
        }
        evaluationStore.clear(sessionId);
        EvaluationReport saved = findBySessionId(sessionId);
        if (saved != null) {
            wsHandler.push(sessionId, WsMessageType.REPORT_READY, Map.of("reportId", saved.getId()));
            log.info("报告生成完成 session={} reportId={} total={} rounds={}",
                    sessionId, saved.getId(), saved.getTotalScore(), agg.rounds.size());
        }
    }

    /** 报告读取（计划 §5.3 #17）：未生成 10004；归属本人或 ADMIN。 */
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
        Aggregation agg = aggregate(s, position, parseModel(position));
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
                .questionEvidence(agg.evidenceList)
                .createdAt(report.getCreatedAt())
                .build();
    }

    /** 已生成报告的 id；未生成返回 null（finish 幂等/重试判断用）。 */
    public Long reportIdOf(Long sessionId) {
        EvaluationReport report = findBySessionId(sessionId);
        return report != null ? report.getId() : null;
    }

    // ==================== 聚合 ====================

    /** 一轮评分的归组结果。 */
    private record RoundData(Long questionId, String askText, int difficulty, List<String> skillTags,
                             String joinedAnswers, List<AiTurnEvaluation> evaluations) {
    }

    /** 逐题评分 + 六维聚合 + 总分的一次性计算结果（生成与读取共用）。 */
    private record Aggregation(Map<Integer, RoundData> rounds, List<AiTurnEvaluation> latestPerRound,
                               List<QuestionEvidenceVO> evidenceList, Map<String, Double> dimensions,
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
        Map<Integer, RoundData> rounds = new TreeMap<>();
        List<ScoreCalculator.TurnScores> turnScores = new ArrayList<>();
        List<List<Double>> contentSequences = new ArrayList<>();
        List<QuestionEvidenceVO> evidenceList = new ArrayList<>();
        List<AiTurnEvaluation> latestPerRound = new ArrayList<>();

        for (Map.Entry<Integer, List<InterviewMessage>> e : byRound.entrySet()) {
            List<InterviewMessage> roundMsgs = e.getValue();
            if (roundMsgs.stream().noneMatch(m -> "USER".equals(m.getSpeaker()))) {
                continue; // 未作答的轮次不参与评分
            }
            RoundData round = buildRoundData(s, roundMsgs);
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
        return new Aggregation(rounds, latestPerRound, evidenceList, dimensions,
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
    private RoundData buildRoundData(InterviewSession s, List<InterviewMessage> roundMsgs) {
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
                        .question(question)
                        .roundMessages(roundMsgs.subList(0, indexOfNthUser(roundMsgs, i) + 1))
                        .knowledgeChunks(List.of())
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

    /** summary 与正文合并存 report_text 列（无独立列，9 表约束），读取时按首个空行拆分。 */
    private String joinText(AiReport report) {
        return (report.getSummary() != null ? report.getSummary() : "") + "\n\n"
                + (report.getReportText() != null ? report.getReportText() : "");
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    // ==================== 查询 ====================

    private EvaluationReport findBySessionId(Long sessionId) {
        return reportMapper.selectOne(Wrappers.<EvaluationReport>lambdaQuery()
                .eq(EvaluationReport::getSessionId, sessionId));
    }
}
