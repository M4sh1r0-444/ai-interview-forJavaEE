package com.example.interview.resume.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.ai.model.AiResumeAnalysis;
import com.example.interview.ai.model.ResumeAnalysisContext;
import com.example.interview.ai.provider.AiProvider;
import com.example.interview.common.config.ResumeProperties;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import com.example.interview.common.util.JsonUtils;
import com.example.interview.position.entity.Position;
import com.example.interview.position.mapper.PositionMapper;
import com.example.interview.position.dto.CompetencyModel;
import com.example.interview.resume.dto.ResumeDigest;
import com.example.interview.resume.dto.ResumeProfile;
import com.example.interview.resume.entity.Resume;
import com.example.interview.resume.entity.ResumeAnalysis;
import com.example.interview.resume.entity.ResumeStatus;
import com.example.interview.resume.mapper.ResumeAnalysisMapper;
import com.example.interview.resume.mapper.ResumeMapper;
import com.example.interview.resume.storage.ResumeStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 异步分析编排（简历计划 §6.2，R2-4）：ANALYZING → 读文件重抽取 → PII 脱敏 → 规则解析 →
 * AiProvider.analyzeResume（三级容错在 Provider 内部）→ ScoreCalculator 聚合 →
 * 事务内 delete 旧结果 + insert 新结果 + 回写 resume（profile/digest/status=ANALYZED）。
 * 任何异常 → FAILED + failReason（[PARSE]/[AI] 前缀，R-04 据此映射 10008/10007）。
 *
 * <p>触发时机：上传/重分析的 Controller 在事务提交后调用 {@link #analyze}（跨 Bean 代理，
 * 模式同 TurnEngine「Controller 在提交事务后调用」）；与面试轮次共用 aiExecutor（core4/max8），
 * 分析任务量受配额约束，不新建池。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAnalysisService {

    private final ResumeMapper resumeMapper;
    private final ResumeAnalysisMapper analysisMapper;
    private final PositionMapper positionMapper;
    private final ResumeStorageService storage;
    private final ResumeTextExtractor extractor;
    private final ResumeProfileParser parser;
    private final ResumeScoreCalculator calculator;
    private final AiProvider aiProvider;
    private final ResumeProperties properties;
    private final TransactionTemplate transactionTemplate;

    /**
     * 异步分析入口。状态守卫：仅 ANALYZING 继续（并发重分析被 10006 挡在入口、
     * 分析期间删除被 @TableLogic 过滤，此处为兜底），异常统一转 FAILED。
     */
    @Async("aiExecutor")
    public void analyze(Long resumeId) {
        Resume r;
        try {
            r = resumeMapper.selectById(resumeId);
        } catch (Exception e) {
            log.error("简历分析加载失败 id={}", resumeId, e);
            return;
        }
        if (r == null || !ResumeStatus.ANALYZING.name().equals(r.getStatus())) {
            log.info("跳过简历分析 id={}（记录不存在或状态非 ANALYZING）", resumeId);
            return;
        }
        try {
            analyzeInternal(r);
        } catch (Exception e) {
            log.warn("简历分析失败 id={}：{}", resumeId, e.getMessage());
            String prefix = e instanceof BizException bz && bz.getResultCode() == ResultCode.RESUME_PARSE_FAILED
                    ? ResumeService.PARSE_FAIL_PREFIX : ResumeService.AI_FAIL_PREFIX;
            markFailed(resumeId, prefix + readable(e));
        }
    }

    private void analyzeInternal(Resume r) {
        byte[] bytes = storage.read(r.getStoredPath());
        String text = extractor.extract(bytes, r.getFileType());
        String masked = parser.maskPii(text);
        ResumeProfile profile = parser.parse(masked, positionOf(r));

        String truncated = masked.length() > properties.getMaxAnalysisChars()
                ? masked.substring(0, properties.getMaxAnalysisChars()) : masked;
        AiResumeAnalysis ai = aiProvider.analyzeResume(ResumeAnalysisContext.builder()
                .userId(r.getUserId())
                .resumeId(r.getId())
                .maskedText(truncated)
                .profile(profile)
                .positionName(positionNameOf(r))
                .positionSkills(positionSkillsOf(r))
                .build());

        ResumeAnalysis result = calculator.aggregate(r.getId(), ai);
        ResumeDigest digest = parser.toDigest(profile);
        transactionTemplate.executeWithoutResult(tx -> {
            analysisMapper.delete(Wrappers.<ResumeAnalysis>lambdaQuery()
                    .eq(ResumeAnalysis::getResumeId, r.getId()));
            analysisMapper.insert(result);
            // 条件更新（WHERE status=ANALYZING）兜底并发：0 行即并发冲突，记 WARN 放弃
            int rows = resumeMapper.update(null, Wrappers.<Resume>lambdaUpdate()
                    .eq(Resume::getId, r.getId())
                    .eq(Resume::getStatus, ResumeStatus.ANALYZING.name())
                    .set(Resume::getStatus, ResumeStatus.ANALYZED.name())
                    .set(Resume::getFailReason, null)
                    .set(Resume::getProfileJson, JsonUtils.toJson(profile))
                    .set(Resume::getDigestJson, JsonUtils.toJson(digest)));
            if (rows == 0) {
                log.warn("简历状态条件更新未命中（并发冲突），放弃覆盖 id={}", r.getId());
            }
        });
        log.info("简历分析完成 id={} overall={} provider={} skills={} projects={}",
                r.getId(), result.getOverallScore(), result.getProvider(),
                profile.getSkills() != null ? profile.getSkills().size() : 0,
                profile.getProjects() != null ? profile.getProjects().size() : 0);
    }

    /** FAILED + failReason（截断 500）；条件更新 0 行说明并发/已删除，记 WARN 放弃。 */
    private void markFailed(Long resumeId, String reason) {
        int rows = resumeMapper.update(null, Wrappers.<Resume>lambdaUpdate()
                .eq(Resume::getId, resumeId)
                .eq(Resume::getStatus, ResumeStatus.ANALYZING.name())
                .set(Resume::getStatus, ResumeStatus.FAILED.name())
                .set(Resume::getFailReason, reason.length() <= 500 ? reason : reason.substring(0, 500)));
        if (rows == 0) {
            log.warn("简历失败落库条件更新未命中 id={}", resumeId);
        }
    }

    /** 首条可读信息（不含堆栈；BizException 取业务 message）。 */
    private String readable(Exception e) {
        String msg = e.getMessage();
        return msg != null && !msg.isBlank() ? msg : e.getClass().getSimpleName();
    }

    private Position positionOf(Resume r) {
        return r.getPositionId() != null ? positionMapper.selectById(r.getPositionId()) : null;
    }

    private String positionNameOf(Resume r) {
        Position p = positionOf(r);
        return p != null ? p.getName() : null;
    }

    /** 岗位能力模型技能（名+权重）；解析失败/缺失返回 null → 通用分析。 */
    private List<ResumeAnalysisContext.PositionSkill> positionSkillsOf(Resume r) {
        Position p = positionOf(r);
        if (p == null || p.getCompetencyModelJson() == null) {
            return null;
        }
        try {
            CompetencyModel model = JsonUtils.parse(p.getCompetencyModelJson(), CompetencyModel.class);
            if (model == null || model.getSkills() == null || model.getSkills().isEmpty()) {
                return null;
            }
            List<ResumeAnalysisContext.PositionSkill> skills = new ArrayList<>();
            for (CompetencyModel.SkillItem s : model.getSkills()) {
                if (s.getSkillName() != null) {
                    skills.add(ResumeAnalysisContext.PositionSkill.builder()
                            .name(s.getSkillName())
                            .weight(s.getWeight())
                            .build());
                }
            }
            return skills.isEmpty() ? null : skills;
        } catch (Exception e) {
            log.warn("岗位能力模型解析失败，按通用分析 resume={} position={}", r.getId(), r.getPositionId());
            return null;
        }
    }
}
