package com.example.interview.resume.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.auth.security.CurrentUser;
import com.example.interview.common.config.ResumeProperties;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import com.example.interview.common.util.JsonUtils;
import com.example.interview.position.dto.CompetencyModel;
import com.example.interview.position.entity.Position;
import com.example.interview.position.mapper.PositionMapper;
import com.example.interview.resume.dto.FocusSkillsVO;
import com.example.interview.resume.dto.ResumeAnalysisVO;
import com.example.interview.resume.dto.ResumeDetailVO;
import com.example.interview.resume.dto.ResumeDigest;
import com.example.interview.resume.dto.ResumeItemVO;
import com.example.interview.resume.dto.UploadResumeVO;
import com.example.interview.resume.entity.Resume;
import com.example.interview.resume.entity.ResumeAnalysis;
import com.example.interview.resume.entity.ResumeStatus;
import com.example.interview.resume.mapper.ResumeAnalysisMapper;
import com.example.interview.resume.mapper.ResumeMapper;
import com.example.interview.resume.storage.ResumeStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 简历受理与查询（简历计划 §6.1 / §6.9）：上传（配额 → 岗位 → 落盘 → 同步抽取 → 落库）、
 * 分页列表、详情、默认简历、逻辑删除、归属校验。所有按 id 操作先匹配 user_id，
 * 不存在/他人/已删除统一 40401（不暴露存在性）；ADMIN 亦按本人数据处理（协议 §1）。
 * 异步 AI 分析（ANALYZING → ANALYZED/FAILED）由 R2 的 ResumeAnalysisService 在事务提交后接管。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    /** fail_reason 前缀：解析失败（上传同步阶段），R-04 据此映射 10008 */
    public static final String PARSE_FAIL_PREFIX = "[PARSE] ";
    /** fail_reason 前缀：AI 分析失败（R2 异步阶段），R-04 据此映射 10007 */
    public static final String AI_FAIL_PREFIX = "[AI] ";

    static final int MAX_PAGE_SIZE = 50;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ResumeMapper resumeMapper;
    private final ResumeAnalysisMapper analysisMapper;
    private final PositionMapper positionMapper;
    private final ResumeStorageService storage;
    private final ResumeTextExtractor extractor;
    private final ResumeProperties properties;
    private final ResumeAnalysisService analysisService;

    // ==================== R-01：上传 ====================

    /**
     * 上传受理（简历计划 §6.1）。"文件级失败"（解析失败）不走 4xx：上传本身成功，
     * 落库 FAILED + fail_reason，返回 code=0 + data.status=FAILED；仅请求本身不合法
     * （白名单外、超限、缺参、配额）才抛业务异常。
     */
    @Transactional
    public UploadResumeVO upload(MultipartFile file, Long positionId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "缺少简历文件");
        }
        Long uid = CurrentUser.currentUserId();
        checkQuota(uid);
        if (positionId != null) {
            validatePosition(positionId);
        }
        String fileType = storage.fileTypeOf(file.getOriginalFilename());
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "读取上传文件失败");
        }
        String storedPath = storage.store(file);

        Resume r = new Resume();
        r.setUserId(uid);
        r.setFileName(safeFileName(file.getOriginalFilename()));
        r.setStoredPath(storedPath);
        r.setFileType(fileType);
        r.setFileSize(file.getSize());
        r.setPositionId(positionId);
        r.setIsActive(0);
        r.setCharCount(0);
        try {
            String text = extractor.extract(bytes, fileType);
            r.setStatus(ResumeStatus.ANALYZING.name());
            r.setCharCount(text.length());
        } catch (BizException e) {
            if (e.getResultCode() != ResultCode.RESUME_PARSE_FAILED) {
                throw e;
            }
            r.setStatus(ResumeStatus.FAILED.name());
            r.setFailReason(truncate(PARSE_FAIL_PREFIX + e.getMessage(), 500));
        }
        resumeMapper.insert(r);
        log.info("简历上传 id={} user={} type={} size={} status={} chars={} position={}",
                r.getId(), uid, fileType, file.getSize(), r.getStatus(), r.getCharCount(), positionId);
        // R2-4：ANALYZING 的异步分析由 Controller 在本事务提交后触发（见 ResumeController.triggerAnalysis）
        return UploadResumeVO.from(resumeMapper.selectById(r.getId()));
    }

    // ==================== R-02：列表 ====================

    /** 当前用户未删除简历，createdAt 倒序；page ≥1、size 1~50（越界 40002，协议 §1）。 */
    public Page<ResumeItemVO> page(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BizException(ResultCode.PARAM_OUT_OF_RANGE,
                    "page 取值 1 起、size 取值 1~" + MAX_PAGE_SIZE);
        }
        Long uid = CurrentUser.currentUserId();
        Page<Resume> result = resumeMapper.selectPage(new Page<>(page, size),
                Wrappers.<Resume>lambdaQuery()
                        .eq(Resume::getUserId, uid)
                        .orderByDesc(Resume::getCreatedAt)
                        .orderByDesc(Resume::getId));
        List<Resume> records = result.getRecords();
        Map<Long, String> positionNames = positionNamesOf(records);
        Map<Long, Integer> scores = overallScoresOf(records);
        List<ResumeItemVO> items = records.stream()
                .map(r -> ResumeItemVO.from(r, positionNames.get(r.getPositionId()), scores.get(r.getId())))
                .toList();
        Page<ResumeItemVO> vo = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        vo.setRecords(items);
        return vo;
    }

    // ==================== R-03：详情 ====================

    /** 详情（轮询端点）：ANALYZED 时内嵌 analysis / digest / profile。 */
    public ResumeDetailVO detail(Long id) {
        Resume r = checkOwnership(id);
        String positionName = r.getPositionId() != null ? positionNameOf(r.getPositionId()) : null;
        ResumeAnalysisVO analysis = null;
        if (ResumeStatus.ANALYZED.name().equals(r.getStatus())) {
            ResumeAnalysis a = analysisMapper.selectOne(Wrappers.<ResumeAnalysis>lambdaQuery()
                    .eq(ResumeAnalysis::getResumeId, id));
            analysis = a != null ? ResumeAnalysisVO.from(a) : null;
        }
        return ResumeDetailVO.from(r, positionName, analysis);
    }

    // ==================== R-04：分析结果 ====================

    /**
     * 仅分析结果（协议 R-04）：ANALYZED → 数据；ANALYZING/PENDING → 10006；
     * FAILED → 按 fail_reason 前缀映射 [PARSE]→10008 / [AI]→10007（message 透出可读原因）。
     */
    public ResumeAnalysisVO analysisOf(Long id) {
        Resume r = checkOwnership(id);
        ResumeStatus status = ResumeStatus.valueOf(r.getStatus());
        switch (status) {
            case ANALYZED -> {
                ResumeAnalysis a = analysisMapper.selectOne(Wrappers.<ResumeAnalysis>lambdaQuery()
                        .eq(ResumeAnalysis::getResumeId, id));
                if (a == null) {
                    throw new BizException(ResultCode.RESUME_ANALYSIS_FAILED, "分析结果缺失，可重新发起分析");
                }
                return ResumeAnalysisVO.from(a);
            }
            case ANALYZING, PENDING -> throw new BizException(ResultCode.RESUME_NOT_READY,
                    "简历解析/分析进行中，请稍后查看");
            case FAILED -> {
                String reason = r.getFailReason() != null ? r.getFailReason() : "";
                String message = reason.startsWith(PARSE_FAIL_PREFIX)
                        ? reason.substring(PARSE_FAIL_PREFIX.length())
                        : reason.startsWith(AI_FAIL_PREFIX)
                        ? reason.substring(AI_FAIL_PREFIX.length()) : reason;
                if (reason.startsWith(PARSE_FAIL_PREFIX)) {
                    throw new BizException(ResultCode.RESUME_PARSE_FAILED,
                            message.isBlank() ? "简历解析失败，请重新上传" : message);
                }
                throw new BizException(ResultCode.RESUME_ANALYSIS_FAILED,
                        message.isBlank() ? "简历分析失败，可重试" : message);
            }
            default -> throw new BizException(ResultCode.INTERNAL_ERROR, "未知简历状态：" + r.getStatus());
        }
    }

    // ==================== R-05：重新分析 ====================

    /**
     * 重分析（协议 R-05）：FAILED（重试）/ ANALYZED（如切换 Provider 后重评）→ ANALYZING
     * 并清空旧失败原因；ANALYZING/PENDING 中重复调用 → 10006（入口挡并发）。
     * 异步分析由 Controller 在本事务提交后触发（跨 Bean 代理保证 @Async 生效）。
     * 旧 analysis 行在分析成功事务内 delete + insert 覆盖。
     */
    @Transactional
    public UploadResumeVO reanalyze(Long id) {
        Resume r = checkOwnership(id);
        ResumeStatus status = ResumeStatus.valueOf(r.getStatus());
        if (status == ResumeStatus.ANALYZING || status == ResumeStatus.PENDING) {
            throw new BizException(ResultCode.RESUME_NOT_READY, "简历分析进行中，请勿重复发起");
        }
        resumeMapper.update(null, Wrappers.<Resume>lambdaUpdate()
                .eq(Resume::getId, id)
                .set(Resume::getStatus, ResumeStatus.ANALYZING.name())
                .set(Resume::getFailReason, null));
        log.info("简历重分析进入 ANALYZING id={} user={} 上次状态={}", id, r.getUserId(), status);
        return UploadResumeVO.from(resumeMapper.selectById(id));
    }

    // ==================== R-06：默认简历 ====================

    /** 仅 ANALYZED 简历可设为默认（否则 40001）；每用户至多一份，原默认自动取消。 */
    @Transactional
    public Map<String, Object> setActive(Long id) {
        Resume r = checkOwnership(id);
        if (!ResumeStatus.ANALYZED.name().equals(r.getStatus())) {
            throw new BizException(ResultCode.PARAM_INVALID, "仅已完成分析的简历可设为默认简历");
        }
        resumeMapper.update(null, Wrappers.<Resume>lambdaUpdate()
                .eq(Resume::getUserId, r.getUserId())
                .eq(Resume::getIsActive, 1)
                .ne(Resume::getId, id)
                .set(Resume::getIsActive, 0));
        if (r.getIsActive() == null || r.getIsActive() != 1) {
            r.setIsActive(1);
            resumeMapper.updateById(r);
        }
        log.info("设为默认简历 id={} user={}", id, r.getUserId());
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("isActive", true);
        return resp;
    }

    // ==================== R-07：逻辑删除 ====================

    /** 逻辑删除（deleted=1）：文件保留审计；列表与新面试引用立即不可见，历史会话不级联。 */
    @Transactional
    public Map<String, Object> delete(Long id) {
        Resume r = checkOwnership(id);
        if (r.getIsActive() != null && r.getIsActive() == 1) {
            r.setIsActive(0);
            resumeMapper.updateById(r);
        }
        resumeMapper.deleteById(id); // @TableLogic → UPDATE deleted=1
        log.info("简历逻辑删除 id={} user={}", id, r.getUserId());
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("deleted", true);
        return resp;
    }

    // ==================== R-08：个性化重点考察预览 ====================

    /**
     * 纯规则计算（协议 R-08，简历方案 §3.6⑥）：digest.skills ∩ 岗位能力模型 →
     * 命中技能（含权重与简历证据）+ 缺口技能 + 权重占比 hitRatio。positionId 可选，
     * 缺省用简历绑定岗位；两者皆空 → 40001。无 AI、无副作用，供面试设置页展示。
     * digest 缺失（未分析完成）按空技能处理：matched 空、gaps 全量、hitRatio=0。
     */
    public FocusSkillsVO focusSkills(Long id, Long positionId) {
        Resume r = checkOwnership(id);
        Long pid = positionId != null ? positionId : r.getPositionId();
        if (pid == null) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "未指定 positionId 且简历未绑定岗位，无法计算重点技能");
        }
        Position p = positionMapper.selectById(pid);
        if (p == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "岗位不存在：positionId=" + pid);
        }
        List<CompetencyModel.SkillItem> modelSkills = competencySkillsOf(p);
        ResumeDigest digest = digestOf(r);

        List<FocusSkillsVO.MatchedSkill> matched = new ArrayList<>();
        List<FocusSkillsVO.GapSkill> gaps = new ArrayList<>();
        double hitWeight = 0;
        double totalWeight = 0;
        for (CompetencyModel.SkillItem s : modelSkills) {
            List<String> terms = matchedTerms(s.getSkillName(), digest);
            double weight = s.getWeight() != null ? s.getWeight() : 0;
            totalWeight += weight;
            if (terms.isEmpty()) {
                gaps.add(FocusSkillsVO.GapSkill.builder()
                        .skillCode(s.getSkillCode()).skillName(s.getSkillName()).build());
            } else {
                hitWeight += weight;
                matched.add(FocusSkillsVO.MatchedSkill.builder()
                        .skillCode(s.getSkillCode()).skillName(s.getSkillName())
                        .weight(s.getWeight()).matchedTerms(terms).build());
            }
        }
        matched.sort((a, b) -> Double.compare(
                b.getWeight() != null ? b.getWeight() : 0, a.getWeight() != null ? a.getWeight() : 0));
        double hitRatio = totalWeight > 0 ? hitWeight / totalWeight : 0;
        log.info("重点技能预览 resume={} position={} matched={} gaps={} hitRatio={}",
                id, pid, matched.size(), gaps.size(), hitRatio);
        return FocusSkillsVO.builder()
                .resumeId(id)
                .positionId(pid)
                .positionName(p.getName())
                .matched(matched)
                .gaps(gaps)
                .hitRatio(hitRatio)
                .build();
    }

    /** digest 解析（缺失/失败返回 null，按空技能处理）。 */
    private ResumeDigest digestOf(Resume r) {
        if (r.getDigestJson() == null) {
            return null;
        }
        try {
            return JsonUtils.parse(r.getDigestJson(), ResumeDigest.class);
        } catch (Exception e) {
            log.warn("简历摘要解析失败，按空技能预览 id={}：{}", r.getId(), e.getMessage());
            return null;
        }
    }

    /** 岗位能力模型技能列表；模型缺失/解析失败返回空列表（matched 空、gaps 空、ratio 0）。 */
    private List<CompetencyModel.SkillItem> competencySkillsOf(Position p) {
        if (p.getCompetencyModelJson() == null) {
            return List.of();
        }
        try {
            CompetencyModel model = JsonUtils.parse(p.getCompetencyModelJson(), CompetencyModel.class);
            return model == null || model.getSkills() == null ? List.of() : model.getSkills();
        } catch (Exception e) {
            log.warn("岗位能力模型解析失败，按空技能预览 positionId={}：{}", p.getId(), e.getMessage());
            return List.of();
        }
    }

    /** 简历中体现该岗位技能的原文表述（digest 技能名，按词典序稳定）。 */
    private List<String> matchedTerms(String positionSkillName, ResumeDigest digest) {
        if (digest == null || digest.getSkills() == null || positionSkillName == null) {
            return List.of();
        }
        return digest.getSkills().stream()
                .filter(s -> s != null && s.getName() != null)
                .map(s -> s.getName())
                .filter(name -> ResumeSkillMatcher.nameMatches(positionSkillName, name))
                .distinct()
                .toList();
    }

    // ==================== 归属与配额 ====================

    /** 归属校验统一入口：不存在 / 已删除 / 他人 → 40401（@TableLogic 已过滤 deleted=1）。 */
    public Resume checkOwnership(Long id) {
        Resume r = resumeMapper.selectById(id);
        if (r == null || !r.getUserId().equals(CurrentUser.currentUserId())) {
            throw new BizException(ResultCode.NOT_FOUND_OR_FORBIDDEN);
        }
        return r;
    }

    /**
     * 配额（简历计划 §6.9）：保留 = 未删除计数 ≥ max-count-per-user → 10010；
     * 每日 = 未删除且 created_at ≥ 当日零点（Asia/Shanghai）计数 ≥ daily-upload-limit → 10010。
     * 逻辑删除同时释放两类额度；量级小，直接 COUNT 不走 Redis。
     */
    void checkQuota(Long uid) {
        long retained = resumeMapper.selectCount(Wrappers.<Resume>lambdaQuery()
                .eq(Resume::getUserId, uid));
        if (retained >= properties.getMaxCountPerUser()) {
            throw new BizException(ResultCode.RESUME_QUOTA_EXCEEDED,
                    "简历保留数量已达上限（" + properties.getMaxCountPerUser() + " 份），请先删除旧简历");
        }
        LocalDateTime dayStart = LocalDate.now(ZONE).atStartOfDay();
        long today = resumeMapper.selectCount(Wrappers.<Resume>lambdaQuery()
                .eq(Resume::getUserId, uid)
                .ge(Resume::getCreatedAt, dayStart));
        if (today >= properties.getDailyUploadLimit()) {
            throw new BizException(ResultCode.RESUME_QUOTA_EXCEEDED,
                    "今日上传次数已达上限（" + properties.getDailyUploadLimit() + " 次），请明日再试");
        }
    }

    // ==================== 内部工具 ====================

    /** 分析对照岗位须存在且启用（与基线管理端校验同型，40001）。 */
    private void validatePosition(Long positionId) {
        Position p = positionMapper.selectById(positionId);
        if (p == null || p.getEnabled() == null || p.getEnabled() != 1) {
            throw new BizException(ResultCode.PARAM_INVALID, "岗位不存在或未启用：positionId=" + positionId);
        }
    }

    private String positionNameOf(Long positionId) {
        Position p = positionMapper.selectById(positionId);
        return p != null ? p.getName() : null;
    }

    private Map<Long, String> positionNamesOf(List<Resume> records) {
        Map<Long, String> names = new HashMap<>();
        records.stream()
                .map(Resume::getPositionId)
                .filter(pid -> pid != null)
                .distinct()
                .forEach(pid -> names.put(pid, positionNameOf(pid))); // 允许 null（岗位已不存在）
        return names;
    }

    /** ANALYZED 简历的总分（resume_id 唯一，批量一次查询）。 */
    private Map<Long, Integer> overallScoresOf(List<Resume> records) {
        List<Long> ids = records.stream()
                .filter(r -> ResumeStatus.ANALYZED.name().equals(r.getStatus()))
                .map(Resume::getId)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return analysisMapper.selectList(Wrappers.<ResumeAnalysis>lambdaQuery()
                        .in(ResumeAnalysis::getResumeId, ids))
                .stream()
                .collect(Collectors.toMap(ResumeAnalysis::getResumeId, ResumeAnalysis::getOverallScore, (a, b) -> a));
    }

    /** 去掉客户端可能带的目录前缀，并限制长度（file_name VARCHAR(255)）。 */
    private static String safeFileName(String original) {
        String name = original == null ? "resume" : original;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return truncate(name.isBlank() ? "resume" : name, 255);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
