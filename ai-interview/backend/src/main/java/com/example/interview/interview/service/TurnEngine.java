package com.example.interview.interview.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.ai.model.AiNextQuestionResult;
import com.example.interview.ai.model.AiTurnEvaluation;
import com.example.interview.ai.model.NextQuestionContext;
import com.example.interview.ai.model.TurnEvaluationContext;
import com.example.interview.ai.prompt.PromptBuilder;
import com.example.interview.ai.provider.AiProvider;
import com.example.interview.assessment.engine.ScoreCalculator;
import com.example.interview.assessment.service.ReportService;
import com.example.interview.assessment.service.TurnEvaluationStore;
import com.example.interview.common.result.ResultCode;
import com.example.interview.interview.entity.InterviewMessage;
import com.example.interview.interview.entity.InterviewSession;
import com.example.interview.interview.mapper.InterviewMessageMapper;
import com.example.interview.interview.mapper.InterviewSessionMapper;
import com.example.interview.interview.rule.FollowupContext;
import com.example.interview.interview.rule.FollowupDecision;
import com.example.interview.interview.rule.FollowupRuleEngine;
import com.example.interview.interview.scheduler.InterviewTimerScheduler;
import com.example.interview.interview.statemachine.InterviewStatus;
import com.example.interview.interview.statemachine.InterviewStateMachine;
import com.example.interview.interview.websocket.InterviewWebSocketHandler;
import com.example.interview.interview.websocket.WsMessageType;
import com.example.interview.position.dto.CompetencyModel;
import com.example.interview.position.entity.Position;
import com.example.interview.position.mapper.PositionMapper;
import com.example.interview.question.entity.Question;
import com.example.interview.question.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一轮问答编排引擎（计划 §6.2，方案 §13.1 流程代码化）。
 *
 * <pre>
 * [同步事务段 由 InterviewService 完成] 校验归属/状态 → PROCESSING_AI → 存 USER 消息
 * [异步 aiExecutor 本类完成]
 *   evaluateTurn 逐题评分（快照入暂存，唤醒规则引擎 R1/R4/R5/R6 评分输入，D15）
 *   → 规则引擎决策 → PromptBuilder 拼 Prompt（DEBUG 日志）→ AiProvider.nextQuestion
 *   → 硬规则覆盖（追问≥3 强制换题）→ 追问落库并推 FOLLOW_UP / 推进轮次推 QUESTION
 *   / 轮次用尽 → FINISHED → ReportService 异步生成报告（M3）
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TurnEngine {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final QuestionMapper questionMapper;
    private final PositionMapper positionMapper;
    private final InterviewStateMachine stateMachine;
    private final FollowupRuleEngine ruleEngine;
    private final PromptBuilder promptBuilder;
    private final AiProvider aiProvider;
    private final InterviewWebSocketHandler wsHandler;
    private final InterviewTimerScheduler timerScheduler;
    private final TransactionTemplate transactionTemplate;
    private final InterviewService interviewService;
    private final ScoreCalculator scoreCalculator;
    private final TurnEvaluationStore evaluationStore;
    private final ReportService reportService;

    @Value("${app.interview.followup-limit:3}")
    private int followupLimit;

    /** Controller 在提交事务后调用（计划 §4.4 异步，专用 aiExecutor）。 */
    @Async("aiExecutor")
    public void processTurn(Long sessionId) {
        try {
            doProcess(sessionId);
        } catch (Exception e) {
            log.error("轮次处理异常 session={}，尝试回退 WAITING_USER", sessionId, e);
            recover(sessionId, e.getMessage());
        }
    }

    private void doProcess(Long sessionId) {
        InterviewSession s = sessionMapper.selectById(sessionId);
        if (s == null || !InterviewStatus.PROCESSING_AI.name().equals(s.getStatus())) {
            // 提交后被 abort/timeout 等并发变更，丢弃本轮 AI 产出
            log.info("跳过轮次处理 session={} 当前状态={}", sessionId,
                    s != null ? s.getStatus() : "null");
            return;
        }
        int round = s.getCurrentRound();

        // 当前轮消息与题目上下文
        List<InterviewMessage> roundMsgs = roundMessages(sessionId, round);
        InterviewMessage mainQuestion = roundMsgs.stream()
                .filter(m -> "AI".equals(m.getSpeaker())).findFirst()
                .orElseThrow(() -> new IllegalStateException("轮次缺少主问题消息 session=" + sessionId));
        Question question = questionMapper.selectById(mainQuestion.getQuestionId());
        String lastAnswer = reversedUserText(roundMsgs);
        int followupCount = (int) roundMsgs.stream().filter(m -> "AI".equals(m.getSpeaker())).count() - 1;

        // 逐题评分（M3-3.1）：累积口径（本轮全部问答）评分并快照入暂存，供报告与规则引擎复用
        AiTurnEvaluation evaluation = evaluateQuietly(sessionId, round, question, roundMsgs);

        // 规则引擎决策（计划 §6.4 七条规则；M3 起接入评分输入，D15 唤醒）
        FollowupContext ruleCtx = FollowupContext.builder()
                .answerText(lastAnswer)
                .question(question)
                .followupCount(followupCount)
                .followupLimit(followupLimit)
                .coveredRatio(coveredRatio(question, evaluation))
                .missingPoints(evaluation != null ? evaluation.getMissingPoints() : null)
                .lastTwoScores(lastTwoCompletedRoundScores(sessionId, round))
                .build();
        FollowupDecision decision = ruleEngine.decide(ruleCtx);

        // 拼装 AI 上下文与 Prompt（DEBUG 可观测，计划 §4.4）
        NextQuestionContext aiCtx = buildContext(s, question, roundMsgs, followupCount, lastAnswer, decision);
        String prompt = promptBuilder.buildNextQuestionPrompt(aiCtx);
        log.debug("session={} round={} prompt:\n{}", sessionId, round, prompt);

        // AiProvider 产出（mock 默认；真实 Provider + 三级容错 M4 接入）
        AiNextQuestionResult r = aiProvider.nextQuestion(aiCtx);

        // 硬规则：追问次数用尽强制换题（优先级高于 AI 输出，计划 §6.4 最后一行）
        boolean followUp = AiNextQuestionResult.ACTION_FOLLOW_UP.equals(r.getAction())
                && followupCount < followupLimit;

        if (followUp) {
            applyFollowup(s, round, question, followupCount, r);
        } else {
            applyAdvance(s, round);
        }
    }

    /** 落库追问消息 → PROCESSING_AI→WAITING_USER → WS 推 FOLLOW_UP（计划 §6.2 步骤 8a）。 */
    private void applyFollowup(InterviewSession s, int round, Question question,
                               int followupCount, AiNextQuestionResult r) {
        InterviewMessage m = new InterviewMessage();
        m.setSessionId(s.getId());
        m.setRoundNo(round);
        m.setSpeaker("AI");
        m.setContentText(r.getContent());
        m.setQuestionId(question != null ? question.getId() : null);
        Boolean committed = transactionTemplate.execute(tx -> {
            messageMapper.insert(m);
            return stateMachine.tryTransition(s.getId(),
                    InterviewStatus.PROCESSING_AI, InterviewStatus.WAITING_USER);
        });
        if (!Boolean.TRUE.equals(committed)) {
            log.info("追问落库后状态竞争失败，丢弃 session={}", s.getId());
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("roundNo", round);
        payload.put("followupCount", followupCount + 1);
        payload.put("content", r.getContent());
        payload.put("difficulty", r.getDifficulty() != null ? r.getDifficulty()
                : (question != null ? question.getDifficulty() : s.getDifficulty()));
        payload.put("targetSkills", r.getTargetSkills() != null ? r.getTargetSkills()
                : (question != null ? question.getSkillTags() : null));
        wsHandler.push(s.getId(), WsMessageType.FOLLOW_UP, payload);
        log.info("追问产出 session={} round={} 第{}次追问", s.getId(), round, followupCount + 1);
    }

    /** 推进主问题轮次：还有题 → QUESTION；用尽 → FINISHED（报告流程 M3 接入）。 */
    private void applyAdvance(InterviewSession s, int round) {
        int next = round + 1;
        if (next < s.getPlannedRounds()) {
            InterviewMessage nextQuestionMsg = mainQuestionOf(s.getId(), next);
            Question nextQuestion = questionMapper.selectById(nextQuestionMsg.getQuestionId());
            transactionTemplate.executeWithoutResult(tx -> {
                // 先推进 current_round（消息可见性跟随），再退出 PROCESSING_AI
                InterviewSession upd = new InterviewSession();
                upd.setId(s.getId());
                upd.setCurrentRound(next);
                sessionMapper.updateById(upd);
                stateMachine.tryTransition(s.getId(),
                        InterviewStatus.PROCESSING_AI, InterviewStatus.WAITING_USER);
            });
            wsHandler.push(s.getId(), WsMessageType.QUESTION,
                    interviewService.questionPayload(next, 0, nextQuestion));
            log.info("进入下一题 session={} round={} questionId={}",
                    s.getId(), next, nextQuestion.getId());
            return;
        }

        // 轮次用尽：PROCESSING_AI → FINISHED；current_round=plannedRounds 使全部消息可见
        transactionTemplate.executeWithoutResult(tx -> {
            InterviewSession upd = new InterviewSession();
            upd.setId(s.getId());
            upd.setCurrentRound(s.getPlannedRounds());
            sessionMapper.updateById(upd);
            stateMachine.tryTransition(s.getId(), InterviewStatus.PROCESSING_AI, InterviewStatus.FINISHED);
        });
        timerScheduler.cancel(s.getId());
        wsHandler.push(s.getId(), WsMessageType.PROGRESS, Map.of(
                "status", InterviewStatus.FINISHED.name(),
                "currentRound", s.getPlannedRounds(),
                "plannedRounds", s.getPlannedRounds()));
        // 报告异步生成（M3-3.2）：generateAsync 内部自校验状态，并发/已生成均幂等
        reportService.generateAsync(s.getId());
        log.info("面试轮次完成 session={} → FINISHED，报告生成已触发", s.getId());
    }

    /** 异常恢复：回退 WAITING_USER 让用户可重新作答，并推 ERROR（计划 §5.6）。 */
    private void recover(Long sessionId, String message) {
        try {
            stateMachine.transitionFromAnyOf(sessionId,
                    java.util.Set.of(InterviewStatus.PROCESSING_AI), InterviewStatus.WAITING_USER);
        } catch (Exception ignore) {
            // 会话已进入其他状态，交由 abort/timeout 收尾
        }
        wsHandler.push(sessionId, WsMessageType.ERROR, Map.of(
                "code", ResultCode.INTERNAL_ERROR.getCode(), "message", message));
    }

    /** 逐题评分：失败不阻断面试（规则引擎对 null 容忍），仅告警降级。 */
    private AiTurnEvaluation evaluateQuietly(Long sessionId, int round, Question question,
                                             List<InterviewMessage> roundMsgs) {
        try {
            AiTurnEvaluation evaluation = aiProvider.evaluateTurn(TurnEvaluationContext.builder()
                    .question(question)
                    .roundMessages(roundMsgs)
                    .knowledgeChunks(List.of()) // M4：KnowledgeSearchService 检索注入
                    .build());
            evaluationStore.append(sessionId, round, evaluation);
            return evaluation;
        } catch (Exception e) {
            log.warn("逐题评分失败，规则引擎降级为无评分输入 session={} round={}: {}",
                    sessionId, round, e.getMessage());
            return null;
        }
    }

    /** 得分点覆盖率；评分或得分点不可得时返回 null（引擎跳过相关规则）。 */
    private Double coveredRatio(Question question, AiTurnEvaluation evaluation) {
        if (question == null || question.getKeyPoints() == null || question.getKeyPoints().isEmpty()
                || evaluation == null) {
            return null;
        }
        int covered = evaluation.getCoveredPoints() != null ? evaluation.getCoveredPoints().size() : 0;
        int missing = evaluation.getMissingPoints() != null ? evaluation.getMissingPoints().size() : 0;
        return covered + missing > 0 ? (double) covered / (covered + missing) : null;
    }

    /**
     * 最近两个已完成轮次（round < 当前轮）的最终内容分，升序 [旧, 新]（R5/R6 输入）。
     * 内容分用默认权重换算（60/85 阈值本就是绝对口径）。
     */
    private List<Integer> lastTwoCompletedRoundScores(Long sessionId, int currentRound) {
        List<Integer> scores = new ArrayList<>(2);
        for (int r = currentRound - 1; r >= 0 && scores.size() < 2; r--) {
            List<AiTurnEvaluation> evals = evaluationStore.get(sessionId, r);
            if (evals != null && !evals.isEmpty()) {
                AiTurnEvaluation latest = evals.get(evals.size() - 1);
                scores.add(0, (int) Math.round(scoreCalculator.contentScore(latest.getScores(), null)));
            }
        }
        return scores;
    }

    private NextQuestionContext buildContext(InterviewSession s, Question question,
                                             List<InterviewMessage> roundMsgs, int followupCount,
                                             String lastAnswer, FollowupDecision decision) {
        Position position = positionMapper.selectById(s.getPositionId());
        List<String> positionSkills = parseSkillNames(position);

        List<InterviewMessage> recent = messageMapper.selectList(
                Wrappers.<InterviewMessage>lambdaQuery()
                        .eq(InterviewMessage::getSessionId, s.getId())
                        .orderByDesc(InterviewMessage::getId)
                        .last("LIMIT 10"));
        List<NextQuestionContext.HistoryMessage> history = new java.util.ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            InterviewMessage m = recent.get(i);
            history.add(NextQuestionContext.HistoryMessage.builder()
                    .speaker(m.getSpeaker())
                    .content(abbreviate(m.getContentText()))
                    .build());
        }

        return NextQuestionContext.builder()
                .positionName(position != null ? position.getName() : "")
                .positionSkills(positionSkills)
                .difficulty(s.getDifficulty())
                .roundNo(s.getCurrentRound())
                .plannedRounds(s.getPlannedRounds())
                .questionId(question != null ? question.getId() : null)
                .questionContent(question != null ? question.getContent() : "")
                .questionDifficulty(question != null ? question.getDifficulty() : null)
                .keyPoints(question != null ? question.getKeyPoints() : null)
                .deductionPoints(question != null ? question.getDeductionPoints() : null)
                .followupTemplates(question != null ? question.getFollowupTemplates() : null)
                .targetSkills(question != null ? question.getSkillTags() : null)
                .history(history)
                .knowledgeChunks(List.of()) // M4：KnowledgeSearchService 检索注入
                .lastUserAnswer(abbreviate(lastAnswer))
                .requestedAction(decision.getAction().name())
                .instruction(decision.getInstruction())
                .difficultyDelta(decision.getDifficultyDelta())
                .reasonCode(decision.getReasonCode())
                .followupCount(followupCount)
                .build();
    }

    private List<String> parseSkillNames(Position position) {
        if (position == null || position.getCompetencyModelJson() == null) {
            return List.of();
        }
        try {
            CompetencyModel model = com.example.interview.common.util.JsonUtils.parse(
                    position.getCompetencyModelJson(), CompetencyModel.class);
            if (model == null || model.getSkills() == null) {
                return List.of();
            }
            return model.getSkills().stream().map(CompetencyModel.SkillItem::getSkillName).toList();
        } catch (Exception e) {
            log.warn("能力模型解析失败 positionId={}: {}", position.getId(), e.getMessage());
            return List.of();
        }
    }

    private InterviewMessage mainQuestionOf(Long sessionId, int roundNo) {
        return messageMapper.selectList(Wrappers.<InterviewMessage>lambdaQuery()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .eq(InterviewMessage::getRoundNo, roundNo)
                        .eq(InterviewMessage::getSpeaker, "AI")
                        .orderByAsc(InterviewMessage::getId)
                        .last("LIMIT 1"))
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("缺少主问题消息 session=" + sessionId));
    }

    List<InterviewMessage> roundMessages(Long sessionId, int roundNo) {
        return messageMapper.selectList(Wrappers.<InterviewMessage>lambdaQuery()
                .eq(InterviewMessage::getSessionId, sessionId)
                .eq(InterviewMessage::getRoundNo, roundNo)
                .orderByAsc(InterviewMessage::getId));
    }

    private String reversedUserText(List<InterviewMessage> roundMsgs) {
        for (int i = roundMsgs.size() - 1; i >= 0; i--) {
            InterviewMessage m = roundMsgs.get(i);
            if ("USER".equals(m.getSpeaker())) {
                return m.getContentText() != null ? m.getContentText() : "";
            }
        }
        return "";
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 300 ? s : s.substring(0, 300) + "…";
    }
}
