package com.example.interview.growth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.assessment.dto.QuestionEvidenceVO;
import com.example.interview.assessment.entity.EvaluationReport;
import com.example.interview.assessment.mapper.EvaluationReportMapper;
import com.example.interview.assessment.service.ReportService;
import com.example.interview.auth.security.CurrentUser;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import com.example.interview.common.util.JsonUtils;
import com.example.interview.growth.dto.RecommendationVO;
import com.example.interview.growth.engine.RecommendationEngine;
import com.example.interview.growth.engine.WeaknessDetector;
import com.example.interview.growth.entity.AbilityRecord;
import com.example.interview.growth.entity.LearningResource;
import com.example.interview.growth.mapper.AbilityRecordMapper;
import com.example.interview.growth.mapper.LearningResourceMapper;
import com.example.interview.interview.entity.InterviewSession;
import com.example.interview.interview.mapper.InterviewSessionMapper;
import com.example.interview.interview.statemachine.InterviewStatus;
import com.example.interview.position.dto.CompetencyModel;
import com.example.interview.position.entity.Position;
import com.example.interview.position.mapper.PositionMapper;
import com.example.interview.position.service.PositionService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 个性化推荐（计划 §5.4 #19 / §6.7，方案 §8.3）。
 * M5-5.3 完整版：WeaknessDetector 四规则识别短板 → RecommendationEngine
 * 优先级排序（(100-掌握度)×技能权重）→ 资源匹配 + 错题重练（最近一场
 * missingPoints 命中题目）+ 下一次面试配置建议；结果落 Redis 缓存
 * （TTL 10 分钟，报告落库后经 ReportGeneratedEvent 预生成）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    /** 推荐缓存 TTL（计划 §6.6：报告链路预生成，TTL 10 分钟）。 */
    static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String CACHE_KEY = "rec:%d:%d";
    /** 规则④维度短板挂靠的技能（岗位能力模型的沟通表达技能）。 */
    private static final String COMMUNICATION_SKILL = "communication";

    private final AbilityRecordMapper abilityRecordMapper;
    private final LearningResourceMapper resourceMapper;
    private final PositionMapper positionMapper;
    private final PositionService positionService;
    private final InterviewSessionMapper sessionMapper;
    private final EvaluationReportMapper reportMapper;
    private final ReportService reportService;
    private final StringRedisTemplate redisTemplate;

    private final WeaknessDetector weaknessDetector = new WeaknessDetector();
    private final RecommendationEngine recommendationEngine = new RecommendationEngine();

    /** 用例 19：当前用户推荐（?positionId= 缺省解析逻辑见 resolvePosition）。 */
    public List<RecommendationVO> recommendations(Long positionId) {
        Long uid = CurrentUser.currentUserId();
        Position position = resolvePosition(uid, positionId);
        return cachedRecommendations(uid, position);
    }

    /** 报告落库后预生成缓存入口（异步线程无请求上下文，计划 §6.6）。 */
    public List<RecommendationVO> warmFor(Long userId, Long positionId) {
        Position position = positionMapper.selectById(positionId);
        if (position == null) {
            return List.of();
        }
        return cachedRecommendations(userId, position);
    }

    private List<RecommendationVO> cachedRecommendations(Long uid, Position position) {
        String key = CACHE_KEY.formatted(uid, position.getId());
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return JsonUtils.parse(cached, new TypeReference<List<RecommendationVO>>() {
                });
            }
        } catch (Exception e) {
            log.warn("推荐缓存读取失败，改走实时计算 key={}: {}", key, e.getMessage());
        }
        List<RecommendationVO> computed = compute(uid, position);
        try {
            redisTemplate.opsForValue().set(key, JsonUtils.toJson(computed), CACHE_TTL);
        } catch (Exception e) {
            log.warn("推荐缓存写入失败 key={}: {}", key, e.getMessage());
        }
        return computed;
    }

    // ==================== 组装与计算 ====================

    private List<RecommendationVO> compute(Long uid, Position position) {
        List<CompetencyModel.SkillItem> skills = positionService.parseModel(position).getSkills();
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        Map<String, List<Double>> historyBySkill = historyBySkill(uid, position.getId());
        List<InterviewSession> finished = finishedSessions(uid, position.getId());
        List<String> dimensionReasons = dimensionReasons(finished);
        LatestSessionEvidence evidence = latestSessionEvidence(uid, finished);

        List<RecommendationEngine.SkillCandidate> candidates = new ArrayList<>();
        for (CompetencyModel.SkillItem skill : skills) {
            if (skill.getSkillCode() == null) {
                continue;
            }
            String code = skill.getSkillCode();
            List<Double> history = historyBySkill.getOrDefault(code, List.of());
            List<String> reasons = new ArrayList<>(weaknessDetector.detectSkill(
                    new WeaknessDetector.SkillSnapshot(history, requiredLevelOf(skill),
                            evidence.missingPointQuestions.getOrDefault(code, 0))));
            // 规则④：communication/voice 维度短板挂到沟通表达技能（能力模型含该技能时）
            if (COMMUNICATION_SKILL.equals(code)) {
                reasons.addAll(dimensionReasons);
            }
            candidates.add(new RecommendationEngine.SkillCandidate(
                    code, skill.getSkillName(), skill.getWeight(), requiredLevelOf(skill),
                    history, reasons, List.of(), evidence.retryQuestions.getOrDefault(code, List.of())));
        }

        RecommendationEngine.Plan plan = recommendationEngine.plan(candidates);
        if (plan.top().isEmpty()) {
            return List.of();
        }
        Map<String, List<LearningResource>> bySkill = loadResources(position.getId(),
                plan.top().stream().map(r -> r.candidate().skillCode()).toList());
        String suggested = recommendationEngine.nextInterviewSuggested(position.getName(), plan);

        return plan.top().stream().map(r -> {
            String code = r.candidate().skillCode();
            List<LearningResource> matched = bySkill.getOrDefault(code, List.of());
            return RecommendationVO.builder()
                    .skillCode(code)
                    .skillName(r.candidate().skillName())
                    .priority(r.priority())
                    .weaknessReasons(reasonsWithFallback(r))
                    .resources(matched.stream()
                            .filter(res -> "ARTICLE".equals(res.getResourceType()) || "QUESTION".equals(res.getResourceType()))
                            .map(RecommendationService::toVO).toList())
                    .practices(matched.stream()
                            .filter(res -> "PRACTICE".equals(res.getResourceType()) || "PROJECT".equals(res.getResourceType()))
                            .map(RecommendationService::toVO).toList())
                    .retryQuestions(r.candidate().retryQuestions().stream()
                            .map(q -> RecommendationVO.RetryQuestionVO.builder()
                                    .questionId(q.questionId())
                                    .questionContent(q.questionContent())
                                    .missingPoints(q.missingPoints())
                                    .build()).toList())
                    .nextInterviewSuggested(suggested)
                    .build();
        }).toList();
    }

    /** 无规则命中时的兜底说明（保证 weaknessReasons 恒非空，接口契约）。 */
    private List<String> reasonsWithFallback(RecommendationEngine.RankedSkill ranked) {
        List<String> reasons = ranked.candidate().weaknessReasons();
        if (reasons != null && !reasons.isEmpty()) {
            return reasons;
        }
        return ranked.mastery() == null
                ? List.of("尚无该技能的面试考察记录，建议先安排一场摸底面试")
                : List.of("暂未命中短板规则，按优先级入选重点巩固（相对提升空间更大）");
    }

    /** (user, position) 每技能掌握度历史，按 (evaluatedAt, id) 升序。 */
    private Map<String, List<Double>> historyBySkill(Long uid, Long positionId) {
        List<AbilityRecord> records = abilityRecordMapper.selectList(
                Wrappers.<AbilityRecord>lambdaQuery()
                        .eq(AbilityRecord::getUserId, uid)
                        .eq(AbilityRecord::getPositionId, positionId)
                        .orderByAsc(AbilityRecord::getEvaluatedAt)
                        .orderByAsc(AbilityRecord::getId));
        return records.stream().collect(Collectors.groupingBy(AbilityRecord::getSkillCode,
                LinkedHashMap::new, Collectors.mapping(r -> r.getScore().doubleValue(), Collectors.toList())));
    }

    /** (user, position) 已出报告的会话（FINISHED/TIMEOUT），按会话时间升序。 */
    private List<InterviewSession> finishedSessions(Long uid, Long positionId) {
        return sessionMapper.selectList(Wrappers.<InterviewSession>lambdaQuery()
                .eq(InterviewSession::getUserId, uid)
                .eq(InterviewSession::getPositionId, positionId)
                .in(InterviewSession::getStatus, InterviewStatus.FINISHED.name(), InterviewStatus.TIMEOUT.name())
                .isNotNull(InterviewSession::getTotalScore)
                .orderByAsc(InterviewSession::getId));
    }

    /** 规则④：communication/voice 维度连续两次 <60 的理由（方案 §6.6 六维编码）。 */
    private List<String> dimensionReasons(List<InterviewSession> finished) {
        Map<String, List<Double>> dimensionScores = new LinkedHashMap<>();
        for (InterviewSession session : finished) {
            EvaluationReport report = reportMapper.selectOne(Wrappers.<EvaluationReport>lambdaQuery()
                    .eq(EvaluationReport::getSessionId, session.getId()));
            if (report == null || report.getDimensionScores() == null) {
                continue;
            }
            for (String dim : List.of("communication", "voice")) {
                Double score = report.getDimensionScores().get(dim);
                if (score != null) {
                    dimensionScores.computeIfAbsent(dim, k -> new ArrayList<>()).add(score);
                }
            }
        }
        List<String> reasons = new ArrayList<>();
        dimensionScores.forEach((dim, scores) ->
                weaknessDetector.detectDimension(new WeaknessDetector.DimensionSnapshot(dim, scores))
                        .ifPresent(reasons::add));
        return reasons;
    }

    /** 最近一场面试逐题证据 → 每技能 missingPoints 命中题数（规则③）+ 错题重练清单。 */
    private LatestSessionEvidence latestSessionEvidence(Long uid, List<InterviewSession> finished) {
        LatestSessionEvidence evidence = new LatestSessionEvidence();
        if (finished.isEmpty()) {
            return evidence;
        }
        Long latestSessionId = finished.get(finished.size() - 1).getId();
        for (QuestionEvidenceVO item : reportService.evidenceOf(latestSessionId)) {
            if (item.getMissingPoints() == null || item.getMissingPoints().isEmpty()
                    || item.getTargetSkills() == null) {
                continue;
            }
            for (String skill : item.getTargetSkills()) {
                if (skill == null || skill.isBlank()) {
                    continue;
                }
                evidence.missingPointQuestions.merge(skill, 1, Integer::sum);
                evidence.retryQuestions.computeIfAbsent(skill, k -> new ArrayList<>())
                        .add(new RecommendationEngine.RetryQuestion(item.getQuestionId(),
                                item.getQuestionContent(), item.getMissingPoints()));
            }
        }
        return evidence;
    }

    private Integer requiredLevelOf(CompetencyModel.SkillItem skill) {
        return skill.getRequiredLevel();
    }

    /** 岗位解析：显式指定（须存在）> 用户最近一条能力记录的岗位 > 第一个启用岗位。 */
    private Position resolvePosition(Long uid, Long positionId) {
        if (positionId != null) {
            Position p = positionMapper.selectById(positionId);
            if (p == null) {
                throw new BizException(ResultCode.NOT_FOUND_OR_FORBIDDEN);
            }
            return p;
        }
        AbilityRecord latest = abilityRecordMapper.selectOne(Wrappers.<AbilityRecord>lambdaQuery()
                .eq(AbilityRecord::getUserId, uid)
                .orderByDesc(AbilityRecord::getEvaluatedAt)
                .orderByDesc(AbilityRecord::getId)
                .last("LIMIT 1"));
        if (latest != null) {
            Position p = positionMapper.selectById(latest.getPositionId());
            if (p != null) {
                return p;
            }
        }
        Position fallback = positionMapper.selectOne(Wrappers.<Position>lambdaQuery()
                .eq(Position::getEnabled, 1).orderByAsc(Position::getId).last("LIMIT 1"));
        if (fallback == null) {
            throw new BizException(ResultCode.NOT_FOUND_OR_FORBIDDEN);
        }
        return fallback;
    }

    private Map<String, List<LearningResource>> loadResources(Long positionId, List<String> skillCodes) {
        if (skillCodes.isEmpty()) {
            return Map.of();
        }
        return resourceMapper.selectList(Wrappers.<LearningResource>lambdaQuery()
                        .eq(LearningResource::getPositionId, positionId)
                        .eq(LearningResource::getEnabled, 1)
                        .in(LearningResource::getSkillCode, skillCodes)
                        .orderByAsc(LearningResource::getId))
                .stream().collect(Collectors.groupingBy(LearningResource::getSkillCode));
    }

    private static RecommendationVO.ResourceVO toVO(LearningResource r) {
        return RecommendationVO.ResourceVO.builder()
                .id(r.getId())
                .title(r.getTitle())
                .resourceType(r.getResourceType())
                .contentUrl(r.getContentUrl())
                .difficulty(r.getDifficulty())
                .build();
    }

    /** 最近一场面试的证据聚合（规则③计数 + 错题重练）。 */
    private static class LatestSessionEvidence {
        final Map<String, Integer> missingPointQuestions = new LinkedHashMap<>();
        final Map<String, List<RecommendationEngine.RetryQuestion>> retryQuestions = new LinkedHashMap<>();
    }
}
