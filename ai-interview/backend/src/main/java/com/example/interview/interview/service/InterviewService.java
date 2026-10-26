package com.example.interview.interview.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.ai.model.AiQuestionPersonalization;
import com.example.interview.ai.model.QuestionPersonalizeContext;
import com.example.interview.ai.provider.AiProvider;
import com.example.interview.assessment.service.ReportService;
import com.example.interview.assessment.service.TurnEvaluationStore;
import com.example.interview.auth.security.CurrentUser;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import com.example.interview.common.util.JsonUtils;
import com.example.interview.interview.dto.CreateInterviewRequest;
import com.example.interview.interview.dto.InterviewDetailVO;
import com.example.interview.interview.dto.MessageVO;
import com.example.interview.interview.dto.TextAnswerRequest;
import com.example.interview.interview.entity.InterviewMessage;
import com.example.interview.interview.entity.InterviewSession;
import com.example.interview.interview.mapper.InterviewMessageMapper;
import com.example.interview.interview.mapper.InterviewSessionMapper;
import com.example.interview.interview.scheduler.InterviewTimerScheduler;
import com.example.interview.interview.storage.AudioStorageService;
import com.example.interview.interview.statemachine.InterviewStatus;
import com.example.interview.interview.statemachine.InterviewStateMachine;
import com.example.interview.interview.websocket.InterviewWebSocketHandler;
import com.example.interview.interview.websocket.WsMessageType;
import com.example.interview.knowledge.service.KeywordExtractor;
import com.example.interview.position.entity.Position;
import com.example.interview.position.mapper.PositionMapper;
import com.example.interview.question.entity.Question;
import com.example.interview.question.service.QuestionService;
import com.example.interview.resume.dto.ResumeDigest;
import com.example.interview.resume.entity.Resume;
import com.example.interview.resume.entity.ResumeStatus;
import com.example.interview.resume.mapper.ResumeMapper;
import com.example.interview.resume.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试会话服务（计划 §4.3/§5.3）：创建 / start（组卷+落库首题）/ 文字与音频回答受理 /
 * finish（收卷并触发报告，兼报告失败重试入口）/ abort / 详情 / 消息查询 / 音频回放读取。
 * 所有状态转换经状态机乐观锁；AI 产出由 TurnEngine 异步完成，报告由 ReportService 异步生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final PositionMapper positionMapper;
    private final QuestionService questionService;
    private final InterviewStateMachine stateMachine;
    private final InterviewTimerScheduler timerScheduler;
    private final InterviewWebSocketHandler wsHandler;
    private final ReportService reportService;
    private final KeywordExtractor keywordExtractor;
    private final AudioStorageService audioStorage;
    private final TurnEvaluationStore evaluationStore;
    private final AiProvider aiProvider;
    private final ResumeService resumeService;
    private final ResumeMapper resumeMapper;

    @Value("${app.interview.min-rounds:5}")
    private int minRounds;
    @Value("${app.interview.max-rounds:8}")
    private int maxRounds;
    @Value("${app.interview.min-duration-minutes:15}")
    private int minDurationMinutes;
    @Value("${app.interview.max-duration-minutes:60}")
    private int maxDurationMinutes;
    @Value("${app.interview.default-duration-minutes:30}")
    private int defaultDurationMinutes;

    // ==================== 用例 9：创建会话 ====================

    @Transactional
    public InterviewDetailVO create(CreateInterviewRequest req) {
        validateRange(req);
        Position position = positionMapper.selectById(req.getPositionId());
        if (position == null || position.getEnabled() == null || position.getEnabled() != 1) {
            throw new BizException(ResultCode.NOT_FOUND_OR_FORBIDDEN, "岗位不存在或未启用");
        }
        if (req.getMode() == null || req.getMode().isBlank()) {
            req.setMode("TEXT");
        }
        if (!"TEXT".equals(req.getMode()) && !"AUDIO".equals(req.getMode())) {
            throw new BizException(ResultCode.PARAM_OUT_OF_RANGE, "mode 只能为 TEXT 或 AUDIO");
        }
        // R3-1 个性化校验链（协议 §4 R-09）：归属 40401 → 状态非 ANALYZED 10009；
        // 简历绑定岗位与面试岗位允许不同（digest 与岗位无关，技能匹配按面试岗位能力模型）
        Long resumeId = null;
        if (req.getResumeId() != null) {
            Resume resume = resumeService.checkOwnership(req.getResumeId());
            if (!ResumeStatus.ANALYZED.name().equals(resume.getStatus())) {
                throw new BizException(ResultCode.RESUME_NOT_ANALYZED);
            }
            resumeId = resume.getId();
        }
        InterviewSession s = new InterviewSession();
        s.setUserId(CurrentUser.currentUserId());
        s.setPositionId(req.getPositionId());
        s.setMode(req.getMode());
        s.setDifficulty(req.getDifficulty());
        s.setPlannedRounds(req.getRounds());
        s.setPlannedDurationMinutes(req.getDurationMinutes()); // FIX-08：时长落库（V7），重启不丢
        s.setCurrentRound(0);
        s.setStatus(InterviewStatus.CREATED.name());
        s.setResumeId(resumeId);
        sessionMapper.insert(s);
        log.info("创建面试会话 id={} user={} position={} mode={} rounds={} duration={}min resume={} personalized={}",
                s.getId(), s.getUserId(), s.getPositionId(), s.getMode(),
                req.getRounds(), req.getDurationMinutes(), resumeId, resumeId != null);
        return detail(s.getId());
    }

    // ==================== 用例 10：开始面试 ====================

    /**
     * CREATED→RUNNING（组卷并写入全部主问题消息）→WAITING_USER，返回首题。
     * 事务内完成，避免并发观察者看到"RUNNING 但无题目"的中间态。
     * R3：个性化会话（resume_id 非空）在此增加三步（简历计划 §6.7）：加载 digest →
     * 偏好组卷 → 批量改写题干（all-or-nothing，失败整场原题干）；question_id 与
     * 评分基准完全不变。
     */
    @Transactional
    public MessageVO start(Long id) {
        InterviewSession s = loadForMutation(id);
        if (!stateMachine.tryTransition(id, InterviewStatus.CREATED, InterviewStatus.RUNNING)) {
            throw new BizException(ResultCode.STATUS_CONFLICT, "面试已开始或已结束，无法重复 start");
        }
        ResumeDigest digest = resumeDigestOf(s.getResumeId());
        List<Question> paper = questionService.assemblePaper(
                s.getPositionId(), s.getDifficulty(), s.getPlannedRounds(), digest);
        if (paper.isEmpty()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "岗位题库无可用题目，无法开始面试");
        }
        if (paper.size() < s.getPlannedRounds()) {
            log.warn("题库不足计划轮次 session={} planned={} actual={}",
                    id, s.getPlannedRounds(), paper.size());
            s.setPlannedRounds(paper.size());
            sessionMapper.updateById(s);
        }
        Map<Long, String> personalized = s.getResumeId() == null
                ? Map.of() : personalizeQuietly(s, digest, paper);
        // 主问题序列一次性落库（round_no=0..N-1），此后不再组卷，避免题目漂移（计划 §6.3）；
        // 改写文本存 content_text，question_id 照旧关联（评分基准 key_points 不变）
        for (int i = 0; i < paper.size(); i++) {
            messageMapper.insert(aiQuestionMessage(s.getId(), i, paper.get(i),
                    personalized.get(paper.get(i).getId())));
        }
        stateMachine.transition(id, InterviewStatus.RUNNING, InterviewStatus.WAITING_USER);

        // FIX-08：时长读库（V7 planned_duration_minutes）；异常兜底默认值
        int duration = s.getPlannedDurationMinutes() != null
                ? s.getPlannedDurationMinutes() : defaultDurationMinutes;
        Question first = paper.get(0);
        String firstAsk = composeAskText(first, personalized.get(first.getId()));
        afterCommit(() -> {
            timerScheduler.register(id, LocalDateTime.now().plusMinutes(duration));
            wsHandler.push(id, WsMessageType.QUESTION, questionPayload(0, 0, first, firstAsk));
        });
        log.info("面试开始 session={} rounds={} 首题 questionId={} personalized={}",
                id, paper.size(), first.getId(), !personalized.isEmpty());

        InterviewMessage firstMsg = roundMessages(id, 0).get(0);
        return MessageVO.from(firstMsg, 0);
    }

    /**
     * R3-3 题干批量改写 + 服务侧复核；任何失败降级整场原题干（all-or-nothing，
     * 可测可解释），不报错不打断 start。digest 缺失（解析失败/字段空）同样跳过改写。
     */
    private Map<Long, String> personalizeQuietly(InterviewSession s, ResumeDigest digest,
                                                 List<Question> paper) {
        if (digest == null) {
            log.warn("个性化会话简历摘要缺失 session={} resume={}，题干不做改写", s.getId(), s.getResumeId());
            return Map.of();
        }
        try {
            Position position = positionMapper.selectById(s.getPositionId());
            QuestionPersonalizeContext ctx = QuestionPersonalizeContext.builder()
                    .sessionId(s.getId())
                    .userId(s.getUserId())
                    .positionName(position != null ? position.getName() : "")
                    .digest(digest)
                    .questions(paper.stream().map(q -> QuestionPersonalizeContext.QuestionBrief.builder()
                            .questionId(q.getId())
                            .type(q.getType())
                            .skillTags(q.getSkillTags())
                            .content(q.getContent())
                            .build()).toList())
                    .build();
            AiQuestionPersonalization r = aiProvider.personalizeQuestions(ctx);
            if (r == null || !r.isValidFor(paper.stream().map(Question::getId).toList())) {
                throw new IllegalStateException("改写结果 questionId/长度复核未通过");
            }
            Map<Long, String> contents = new HashMap<>();
            r.getItems().forEach(i -> contents.put(i.getQuestionId(), i.getContent().strip()));
            log.info("个性化题干改写成功 session={} 题数={}", s.getId(), contents.size());
            return contents;
        } catch (Exception e) {
            log.warn("题干个性化失败，整场使用原题干 session={}：{}", s.getId(), e.getMessage());
            return Map.of();
        }
    }

    /** resume.digest_json → ResumeDigest（R3）；缺失/解析失败返回 null，按无 digest 继续（WARN）。 */
    private ResumeDigest resumeDigestOf(Long resumeId) {
        if (resumeId == null) {
            return null;
        }
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null || resume.getDigestJson() == null) {
            log.warn("简历摘要缺失 resume={} session 关联不受影响，按无 digest 组卷", resumeId);
            return null;
        }
        try {
            return JsonUtils.parse(resume.getDigestJson(), ResumeDigest.class);
        } catch (Exception e) {
            log.warn("简历摘要解析失败 resume={}：{}，按无 digest 组卷", resumeId, e.getMessage());
            return null;
        }
    }

    // ==================== 用例 11：文字回答受理 ====================

    /**
     * 同步事务段（计划 §6.2 步骤 1~3）：归属与状态校验、WAITING_USER→PROCESSING_AI、
     * 保存 USER 消息并写入提取关键词（keywords_json，供检索与报告）。
     * AI 编排由 Controller 触发 TurnEngine 异步执行（事务提交后启动）。
     */
    @Transactional
    public Map<String, Object> submitTextAnswer(Long id, TextAnswerRequest req) {
        InterviewSession s = loadForMutation(id);
        if (!InterviewStatus.WAITING_USER.name().equals(s.getStatus())) {
            throw new BizException(ResultCode.STATUS_CONFLICT,
                    "当前状态 " + s.getStatus() + " 不可提交回答");
        }
        if (!s.getCurrentRound().equals(req.getRoundNo())) {
            throw new BizException(ResultCode.PARAM_OUT_OF_RANGE,
                    "roundNo 应为当前轮次 " + s.getCurrentRound());
        }
        stateMachine.transition(id, InterviewStatus.WAITING_USER, InterviewStatus.PROCESSING_AI);
        InterviewMessage m = new InterviewMessage();
        m.setSessionId(id);
        m.setRoundNo(req.getRoundNo());
        m.setSpeaker("USER");
        m.setInputType("TEXT");
        m.setContentText(req.getContent());
        m.setKeywords(keywordExtractor.extract(req.getContent()));
        messageMapper.insert(m);
        log.info("收到文字回答 session={} round={} length={} keywords={}",
                id, req.getRoundNo(), req.getContent().length(), m.getKeywords());
        Map<String, Object> resp = new HashMap<>();
        resp.put("accepted", true);
        resp.put("roundNo", req.getRoundNo());
        return resp;
    }

    // ==================== 用例 12：音频回答受理（M4） ====================

    /**
     * 音频回答（计划 §5.3 #12 / §6.9）：转写在前端完成，后端校验格式大小 →
     * 落盘 uploads → 以 asrText 为 content_text 进入与文字同一分析流程；语音指标
     * 用 durationMs/字数计算（pauseCount 表单参数受 9 表约束不落库，停顿由转写标记推断）。
     * 提交后推 TRANSCRIPT 回显确认（计划 §5.6）。
     */
    @Transactional
    public Map<String, Object> submitAudioAnswer(Long id, MultipartFile file, int roundNo,
                                                 String asrText, Integer durationMs, Integer pauseCount) {
        if (asrText == null || asrText.isBlank() || asrText.length() > 5000) {
            throw new BizException(ResultCode.PARAM_INVALID, "asrText 必填且长度 1~5000");
        }
        if (durationMs == null || durationMs < 1 || durationMs > 600_000) {
            throw new BizException(ResultCode.PARAM_INVALID, "durationMs 必填且取值 1~600000 毫秒");
        }
        if (pauseCount != null && (pauseCount < 0 || pauseCount > 999)) {
            throw new BizException(ResultCode.PARAM_INVALID, "pauseCount 取值 0~999");
        }
        InterviewSession s = loadForMutation(id);
        if (!"AUDIO".equals(s.getMode())) {
            throw new BizException(ResultCode.PARAM_OUT_OF_RANGE, "仅 mode=AUDIO 会话可提交音频回答");
        }
        if (!InterviewStatus.WAITING_USER.name().equals(s.getStatus())) {
            throw new BizException(ResultCode.STATUS_CONFLICT,
                    "当前状态 " + s.getStatus() + " 不可提交回答");
        }
        if (!s.getCurrentRound().equals(roundNo)) {
            throw new BizException(ResultCode.PARAM_OUT_OF_RANGE,
                    "roundNo 应为当前轮次 " + s.getCurrentRound());
        }
        String audioPath = audioStorage.store(file); // 校验失败抛 40001，事务回滚不落消息

        stateMachine.transition(id, InterviewStatus.WAITING_USER, InterviewStatus.PROCESSING_AI);
        InterviewMessage m = new InterviewMessage();
        m.setSessionId(id);
        m.setRoundNo(roundNo);
        m.setSpeaker("USER");
        m.setInputType("AUDIO");
        m.setContentText(asrText);
        m.setAsrText(asrText);
        m.setAudioPath(audioPath);
        m.setAudioDurationMs(durationMs);
        m.setKeywords(keywordExtractor.extract(asrText));
        messageMapper.insert(m);
        afterCommit(() -> wsHandler.push(id, WsMessageType.TRANSCRIPT, Map.of(
                "messageId", m.getId(), "asrText", asrText)));
        log.info("收到音频回答 session={} round={} bytes={} durationMs={}ms 转写{}字 path={}",
                id, roundNo, file.getSize(), durationMs, asrText.length(), audioPath);
        Map<String, Object> resp = new HashMap<>();
        resp.put("accepted", true);
        resp.put("roundNo", roundNo);
        return resp;
    }

    /** 音频回放（计划 §6.9 可选项）：归属本人/ADMIN 可读（loadForRead 统一校验），流式返回落盘文件。 */
    public AudioResource loadAudio(Long sessionId, Long messageId) {
        loadForRead(sessionId);
        InterviewMessage m = messageMapper.selectById(messageId);
        if (m == null || !m.getSessionId().equals(sessionId) || m.getAudioPath() == null) {
            throw new BizException(ResultCode.NOT_FOUND_OR_FORBIDDEN, "音频消息不存在");
        }
        return new AudioResource(audioStorage.resolve(m.getAudioPath()),
                audioStorage.contentTypeOf(m.getAudioPath()));
    }

    /** 回放资源：文件路径 + Content-Type。 */
    public record AudioResource(java.nio.file.Path path, String contentType) {
    }

    // ==================== 用例 13：结束并生成报告 ====================

    /**
     * 主动收卷（计划 §5.3 #13）：WAITING_USER/PROCESSING_AI → FINISHED，事务提交后
     * 异步生成报告。FINISHED/TIMEOUT 幂等：已生成报告直接返回 reportId；未生成
     * （上次生成失败）则重新触发生成——即 M3-3.5 的重试入口。
     * RUNNING 为事务内中间态外部不可见；CREATED/ABORTED 不可 finish（用 abort）。
     */
    @Transactional
    public Map<String, Object> finish(Long id) {
        InterviewSession s = loadForMutation(id);
        InterviewStatus from = InterviewStatus.valueOf(s.getStatus());
        Long existingReportId = reportService.reportIdOf(id);

        if (from == InterviewStatus.FINISHED || from == InterviewStatus.TIMEOUT) {
            if (existingReportId == null) {
                afterCommit(() -> reportService.generateAsync(id));
                log.info("报告重试触发 session={} status={}", id, from);
            }
            return finishResponse(from.name(), existingReportId);
        }
        if (from != InterviewStatus.WAITING_USER && from != InterviewStatus.PROCESSING_AI) {
            throw new BizException(ResultCode.STATUS_CONFLICT,
                    "当前状态 " + from + " 不能结束（未开始的面试请使用 abort）");
        }
        stateMachine.transitionFromAnyOf(id,
                InterviewStatus.sourcesOf(InterviewStatus.FINISHED), InterviewStatus.FINISHED);
        afterCommit(() -> {
            timerScheduler.cancel(id);
            wsHandler.push(id, WsMessageType.PROGRESS, Map.of(
                    "status", InterviewStatus.FINISHED.name(),
                    "currentRound", s.getCurrentRound(),
                    "plannedRounds", s.getPlannedRounds()));
            reportService.generateAsync(id);
        });
        log.info("面试主动收卷 session={} from={} answeredRounds={}", id, from, s.getCurrentRound());
        return finishResponse(InterviewStatus.FINISHED.name(), null);
    }

    private Map<String, Object> finishResponse(String status, Long reportId) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("accepted", true);
        resp.put("status", status);
        resp.put("reportId", reportId);
        return resp;
    }

    // ==================== 用例 14：中止 ====================

    @Transactional
    public InterviewDetailVO abort(Long id) {
        InterviewSession s = loadForMutation(id);
        InterviewStatus from = InterviewStatus.valueOf(s.getStatus());
        if (from.isTerminal()) {
            throw new BizException(ResultCode.STATUS_CONFLICT, "面试已结束（" + from + "），无需中止");
        }
        stateMachine.transition(id, from, InterviewStatus.ABORTED);
        afterCommit(() -> {
            timerScheduler.cancel(id);
            // FIX-16：中止会话不出报告，立即释放评分暂存与 Provider 会话状态
            evaluationStore.clear(id);
            aiProvider.releaseSession(id);
            wsHandler.push(id, WsMessageType.PROGRESS, Map.of(
                    "status", InterviewStatus.ABORTED.name(),
                    "currentRound", s.getCurrentRound(),
                    "plannedRounds", s.getPlannedRounds()));
        });
        log.info("面试中止 session={} from={}", id, from);
        return detail(id);
    }

    // ==================== 用例 15/16：查询 ====================

    /** 归属本人或 ADMIN 可读（计划 §5.3 #15）。R3-6：个性化会话附简历文件名。 */
    public InterviewDetailVO detail(Long id) {
        InterviewSession s = loadForRead(id);
        Position p = positionMapper.selectById(s.getPositionId());
        String resumeFileName = null;
        if (s.getResumeId() != null) {
            Resume resume = resumeMapper.selectById(s.getResumeId());
            resumeFileName = resume != null ? resume.getFileName() : null;
        }
        return InterviewDetailVO.from(s, p != null ? p.getName() : null,
                timerScheduler.remainSeconds(id), resumeFileName);
    }

    /**
     * 消息列表（按时间序）：未问到的主问题（roundNo > currentRound 的 AI 消息）
     * 对学生隐藏防偷题；ADMIN 可见全部。
     */
    public List<MessageVO> messages(Long id, Integer roundNo) {
        InterviewSession s = loadForRead(id);
        boolean admin = CurrentUser.isAdmin();
        List<InterviewMessage> list = messageMapper.selectList(
                Wrappers.<InterviewMessage>lambdaQuery()
                        .eq(InterviewMessage::getSessionId, id)
                        .eq(roundNo != null, InterviewMessage::getRoundNo, roundNo)
                        .orderByAsc(InterviewMessage::getId));
        Map<Integer, Integer> aiIndexByRound = new HashMap<>();
        List<MessageVO> result = new ArrayList<>(list.size());
        for (InterviewMessage m : list) {
            Integer followupIndex = null;
            if ("AI".equals(m.getSpeaker())) {
                followupIndex = aiIndexByRound.merge(m.getRoundNo(), 1, Integer::sum) - 1;
                if (!admin && m.getRoundNo() > s.getCurrentRound()) {
                    continue; // 未问到的主问题不暴露
                }
            }
            result.add(MessageVO.from(m, followupIndex));
        }
        return result;
    }

    // ==================== 内部工具 ====================

    private void validateRange(CreateInterviewRequest req) {
        if (req.getDifficulty() < 1 || req.getDifficulty() > 5) {
            throw new BizException(ResultCode.PARAM_OUT_OF_RANGE, "difficulty 取值 1~5");
        }
        if (req.getRounds() < minRounds || req.getRounds() > maxRounds) {
            throw new BizException(ResultCode.PARAM_OUT_OF_RANGE,
                    "rounds 取值 " + minRounds + "~" + maxRounds);
        }
        if (req.getDurationMinutes() < minDurationMinutes
                || req.getDurationMinutes() > maxDurationMinutes) {
            throw new BizException(ResultCode.PARAM_OUT_OF_RANGE,
                    "durationMinutes 取值 " + minDurationMinutes + "~" + maxDurationMinutes);
        }
    }

    /** 变更类操作仅限会话归属人（ADMIN 也不代答，计划 §5.3"归属本人"）。 */
    private InterviewSession loadForMutation(Long id) {
        InterviewSession s = sessionMapper.selectById(id);
        if (s == null || !s.getUserId().equals(CurrentUser.currentUserId())) {
            throw new BizException(ResultCode.NOT_FOUND_OR_FORBIDDEN);
        }
        return s;
    }

    /** 读取类操作归属本人或 ADMIN（不暴露存在性，统一 40401）。 */
    private InterviewSession loadForRead(Long id) {
        InterviewSession s = sessionMapper.selectById(id);
        if (s == null || (!CurrentUser.isAdmin() && !s.getUserId().equals(CurrentUser.currentUserId()))) {
            throw new BizException(ResultCode.NOT_FOUND_OR_FORBIDDEN);
        }
        return s;
    }

    /** AI 主问题消息：背景与题干（或改写题干）合并为提问文本。 */
    private InterviewMessage aiQuestionMessage(Long sessionId, int roundNo, Question q, String personalizedContent) {
        InterviewMessage m = new InterviewMessage();
        m.setSessionId(sessionId);
        m.setRoundNo(roundNo);
        m.setSpeaker("AI");
        m.setContentText(composeAskText(q, personalizedContent));
        m.setQuestionId(q.getId());
        return m;
    }

    /** 提问文本 = 背景（可空）+ 题干；personalizedContent 非空时替换题干（R3-3）。 */
    static String composeAskText(Question q) {
        return composeAskText(q, null);
    }

    static String composeAskText(Question q, String personalizedContent) {
        String content = personalizedContent != null && !personalizedContent.isBlank()
                ? personalizedContent : q.getContent();
        return q.getBackground() != null && !q.getBackground().isBlank()
                ? q.getBackground() + "\n" + content
                : content;
    }

    Map<String, Object> questionPayload(int roundNo, int followupCount, Question q) {
        return questionPayload(roundNo, followupCount, q, composeAskText(q));
    }

    /** content 非空时以落库文本为准（个性化改写后的题干，R3-3），保持 WS 推送与消息表一致。 */
    Map<String, Object> questionPayload(int roundNo, int followupCount, Question q, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("roundNo", roundNo);
        payload.put("followupCount", followupCount);
        payload.put("content", content != null && !content.isBlank() ? content : composeAskText(q));
        payload.put("difficulty", q.getDifficulty());
        payload.put("targetSkills", q.getSkillTags());
        return payload;
    }

    /** 当前轮次全部消息（时间序）。 */
    List<InterviewMessage> roundMessages(Long sessionId, int roundNo) {
        return messageMapper.selectList(Wrappers.<InterviewMessage>lambdaQuery()
                .eq(InterviewMessage::getSessionId, sessionId)
                .eq(InterviewMessage::getRoundNo, roundNo)
                .orderByAsc(InterviewMessage::getId));
    }

    /** 事务提交后再推送 WS / 注册定时器，避免读取方看到未提交数据。 */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
