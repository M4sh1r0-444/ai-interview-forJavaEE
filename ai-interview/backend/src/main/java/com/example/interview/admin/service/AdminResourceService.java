package com.example.interview.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.admin.dto.ResourceUpsertRequest;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import com.example.interview.growth.entity.LearningResource;
import com.example.interview.growth.mapper.LearningResourceMapper;
import com.example.interview.position.entity.Position;
import com.example.interview.position.mapper.PositionMapper;
import com.example.interview.position.service.PositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 管理端学习资源 CRUD（计划 §5.5 #29★）。skillCode 须对齐岗位能力模型
 * （计划 §3.2 learning_resource 约定）；删除=下架（enabled→0），
 * 与推荐引擎的 (position, skill, enabled) 匹配口径一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminResourceService {

    private static final Set<String> RESOURCE_TYPES = Set.of("ARTICLE", "QUESTION", "PRACTICE", "PROJECT");

    private final LearningResourceMapper resourceMapper;
    private final PositionMapper positionMapper;
    private final PositionService positionService;

    /** 列表（含已下架），id 倒序；positionId/skillCode/resourceType 可选过滤。 */
    public Page<LearningResource> page(Long positionId, String skillCode, String resourceType,
                                       int page, int size) {
        return resourceMapper.selectPage(Page.of(page, size),
                Wrappers.<LearningResource>lambdaQuery()
                        .eq(positionId != null, LearningResource::getPositionId, positionId)
                        .eq(skillCode != null && !skillCode.isBlank(), LearningResource::getSkillCode, skillCode)
                        .eq(resourceType != null && !resourceType.isBlank(),
                                LearningResource::getResourceType, resourceType)
                        .orderByDesc(LearningResource::getId));
    }

    public LearningResource create(ResourceUpsertRequest req) {
        Position position = validate(req);
        LearningResource resource = new LearningResource();
        copyFields(req, resource);
        resource.setEnabled(req.getEnabled() != null ? req.getEnabled() : 1);
        resourceMapper.insert(resource);
        log.info("管理端新增学习资源 id={} position={} skill={} type={}",
                resource.getId(), position.getCode(), req.getSkillCode(), req.getResourceType());
        return resourceMapper.selectById(resource.getId());
    }

    public LearningResource update(Long id, ResourceUpsertRequest req) {
        requireResource(id);
        validate(req);
        LearningResource resource = new LearningResource();
        resource.setId(id);
        copyFields(req, resource);
        resource.setEnabled(req.getEnabled());
        resourceMapper.updateById(resource);
        log.info("管理端修改学习资源 id={}", id);
        return resourceMapper.selectById(id);
    }

    /** 下架（enabled→0）：推荐匹配即刻排除；不物理删，保留运营审计轨迹。 */
    public void delete(Long id) {
        requireResource(id);
        LearningResource resource = new LearningResource();
        resource.setId(id);
        resource.setEnabled(0);
        resourceMapper.updateById(resource);
        log.info("管理端下架学习资源 id={}", id);
    }

    private Position validate(ResourceUpsertRequest req) {
        if (!RESOURCE_TYPES.contains(req.getResourceType())) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "resourceType 只能为 " + RESOURCE_TYPES);
        }
        Position position = positionMapper.selectById(req.getPositionId());
        if (position == null) {
            throw new BizException(ResultCode.NOT_FOUND_OR_FORBIDDEN, "岗位不存在");
        }
        List<String> codes = positionService.parseModel(position).getSkills() == null
                ? List.of()
                : positionService.parseModel(position).getSkills().stream()
                        .map(s -> s.getSkillCode()).toList();
        if (!codes.contains(req.getSkillCode())) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "skillCode 须对齐岗位能力模型：" + req.getSkillCode());
        }
        return position;
    }

    private void copyFields(ResourceUpsertRequest req, LearningResource resource) {
        resource.setPositionId(req.getPositionId());
        resource.setSkillCode(req.getSkillCode());
        resource.setTitle(req.getTitle());
        resource.setResourceType(req.getResourceType());
        resource.setContentUrl(req.getContentUrl());
        resource.setDifficulty(req.getDifficulty());
    }

    private void requireResource(Long id) {
        if (resourceMapper.selectById(id) == null) {
            throw new BizException(ResultCode.NOT_FOUND_OR_FORBIDDEN, "学习资源不存在");
        }
    }
}
