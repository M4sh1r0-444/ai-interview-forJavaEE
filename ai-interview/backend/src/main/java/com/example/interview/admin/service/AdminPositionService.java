package com.example.interview.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.admin.dto.PositionAdminVO;
import com.example.interview.admin.dto.PositionUpsertRequest;
import com.example.interview.admin.dto.WeightsUpdateRequest;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import com.example.interview.common.util.JsonUtils;
import com.example.interview.position.dto.CompetencyModel;
import com.example.interview.position.entity.Position;
import com.example.interview.position.mapper.PositionMapper;
import com.example.interview.position.service.PositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端岗位维护（计划 §5.5 #28★/#30★）：岗位与能力模型增改、
 * 评分权重调整（写 competency_model_json.scoreWeights，方案 §7.2
 * "权重保存到数据库中，可以按岗位调整"）。岗位被题库/历史引用，不提供删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPositionService {

    private static final double WEIGHT_SUM_TOLERANCE = 0.01;

    private final PositionMapper positionMapper;
    private final PositionService positionService;

    /** 全量岗位（含停用），id 升序。 */
    public Page<PositionAdminVO> page(int page, int size) {
        Page<Position> p = positionMapper.selectPage(Page.of(page, size),
                Wrappers.<Position>lambdaQuery().orderByAsc(Position::getId));
        Page<PositionAdminVO> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    public PositionAdminVO detail(Long id) {
        return toVO(requirePosition(id));
    }

    public PositionAdminVO create(PositionUpsertRequest req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new BizException(ResultCode.PARAM_INVALID, "code: 新增岗位时必填");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ResultCode.PARAM_INVALID, "name: 新增岗位时必填");
        }
        if (positionMapper.selectCount(Wrappers.<Position>lambdaQuery()
                .eq(Position::getCode, req.getCode())) > 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "岗位编码已存在：" + req.getCode());
        }
        validateSkills(req.getSkills());
        validateWeights(req.getScoreWeights());

        CompetencyModel model = new CompetencyModel();
        model.setSkills(req.getSkills());
        model.setScoreWeights(req.getScoreWeights());

        Position position = new Position();
        position.setCode(req.getCode());
        position.setName(req.getName());
        position.setDescription(req.getDescription());
        position.setDefaultRounds(req.getDefaultRounds() != null ? req.getDefaultRounds() : 5);
        position.setEnabled(req.getEnabled() != null ? req.getEnabled() : 1);
        position.setCompetencyModelJson(JsonUtils.toJson(model));
        positionMapper.insert(position);
        log.info("管理端新增岗位 id={} code={}", position.getId(), position.getCode());
        return toVO(positionMapper.selectById(position.getId()));
    }

    /** 部分更新：非 null 标量生效；skills 非空整体替换（保留原 scoreWeights）。 */
    public PositionAdminVO update(Long id, PositionUpsertRequest req) {
        Position exist = requirePosition(id);
        if (req.getName() != null && req.getName().isBlank()) {
            throw new BizException(ResultCode.PARAM_INVALID, "name 不能为空串");
        }
        Position upd = new Position();
        upd.setId(id);
        upd.setName(req.getName());
        upd.setDescription(req.getDescription());
        if (req.getDefaultRounds() != null) {
            validateRounds(req.getDefaultRounds());
            upd.setDefaultRounds(req.getDefaultRounds());
        }
        if (req.getEnabled() != null) {
            upd.setEnabled(req.getEnabled());
        }
        if (req.getSkills() != null) {
            validateSkills(req.getSkills());
            CompetencyModel model = positionService.parseModel(exist);
            model.setSkills(req.getSkills());
            if (req.getScoreWeights() != null) {
                validateWeights(req.getScoreWeights());
                model.setScoreWeights(req.getScoreWeights());
            }
            upd.setCompetencyModelJson(JsonUtils.toJson(model));
        } else if (req.getScoreWeights() != null) {
            validateWeights(req.getScoreWeights());
            CompetencyModel model = positionService.parseModel(exist);
            model.setScoreWeights(req.getScoreWeights());
            upd.setCompetencyModelJson(JsonUtils.toJson(model));
        }
        positionMapper.updateById(upd);
        log.info("管理端修改岗位 id={}", id);
        return toVO(positionMapper.selectById(id));
    }

    /** 用例 #30★：评分权重调整（提供的组须完整、组内和=1，未提供的组保留原值）。 */
    public PositionAdminVO updateWeights(Long id, WeightsUpdateRequest req) {
        Position exist = requirePosition(id);
        CompetencyModel.ScoreWeights incoming = req.getScoreWeights();
        CompetencyModel parsed = positionService.parseModel(exist);
        CompetencyModel.ScoreWeights merged = parsed.getScoreWeights() != null
                ? parsed.getScoreWeights() : new CompetencyModel.ScoreWeights();
        if (incoming == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "scoreWeights 必填");
        }
        if (incoming.getContent() != null) {
            validateContentGroup(incoming.getContent());
            merged.setContent(incoming.getContent());
        }
        if (incoming.getExpression() != null) {
            validateExpressionGroup(incoming.getExpression());
            merged.setExpression(incoming.getExpression());
        }
        if (incoming.getTotal() != null) {
            validateTotalGroup(incoming.getTotal());
            merged.setTotal(incoming.getTotal());
        }
        if (merged.getContent() == null && merged.getExpression() == null && merged.getTotal() == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "至少提供 content/expression/total 一组权重");
        }

        CompetencyModel model = positionService.parseModel(exist);
        model.setScoreWeights(merged);
        Position upd = new Position();
        upd.setId(id);
        upd.setCompetencyModelJson(JsonUtils.toJson(model));
        positionMapper.updateById(upd);
        log.info("管理端调整评分权重 positionId={}", id);
        return toVO(positionMapper.selectById(id));
    }

    /** 各权重组校验：组内完整、单项 0~1、和=1（计划 §3.5 结构）。 */
    private void validateContentGroup(CompetencyModel.DimensionWeights g) {
        requireGroupSum("content", List.of(
                g.getTechnical(), g.getDepth(), g.getLogic(), g.getEvidence(), g.getMatch()));
    }

    private void validateExpressionGroup(CompetencyModel.DimensionWeights g) {
        requireGroupSum("expression", List.of(
                g.getClarity(), g.getFluency(), g.getPace(), g.getConfidence(), g.getCompleteness()));
    }

    private void validateTotalGroup(CompetencyModel.TotalWeights g) {
        requireGroupSum("total", List.of(
                g.getContent(), g.getExpression(), g.getAdaptability(), g.getMatch()));
    }

    private void requireGroupSum(String group, List<Double> values) {
        for (int i = 0; i < values.size(); i++) {
            Double v = values.get(i);
            if (v == null) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "scoreWeights." + group + " 第 " + (i + 1) + " 项权重必填（组内须完整）");
            }
            if (v < 0 || v > 1) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "scoreWeights." + group + " 第 " + (i + 1) + " 项权重取值 0~1");
            }
        }
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1.0) > WEIGHT_SUM_TOLERANCE) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    String.format("scoreWeights.%s 组内权重和须为 1（当前 %.2f）", group, sum));
        }
    }

    private void validateSkills(List<CompetencyModel.SkillItem> skills) {
        if (skills == null || skills.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "skills: 能力模型至少包含 1 个技能");
        }
        double sum = 0;
        for (CompetencyModel.SkillItem skill : skills) {
            if (skill.getSkillCode() == null || skill.getSkillCode().isBlank()
                    || skill.getSkillName() == null || skill.getSkillName().isBlank()) {
                throw new BizException(ResultCode.PARAM_INVALID, "每个技能须包含 skillCode 与 skillName");
            }
            if (skill.getWeight() == null || skill.getWeight() <= 0 || skill.getWeight() > 1) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "技能 " + skill.getSkillCode() + " 的 weight 取值 (0,1]");
            }
            if (skill.getRequiredLevel() != null && (skill.getRequiredLevel() < 0 || skill.getRequiredLevel() > 100)) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "技能 " + skill.getSkillCode() + " 的 requiredLevel 取值 0~100");
            }
            sum += skill.getWeight();
        }
        if (Math.abs(sum - 1.0) > WEIGHT_SUM_TOLERANCE) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    String.format("技能权重和须为 1（当前 %.2f，计划 §3.5）", sum));
        }
    }

    private void validateWeights(CompetencyModel.ScoreWeights weights) {
        if (weights == null) {
            return;
        }
        if (weights.getContent() != null) {
            validateContentGroup(weights.getContent());
        }
        if (weights.getExpression() != null) {
            validateExpressionGroup(weights.getExpression());
        }
        if (weights.getTotal() != null) {
            validateTotalGroup(weights.getTotal());
        }
    }

    private void validateRounds(Integer rounds) {
        if (rounds < 3 || rounds > 8) {
            throw new BizException(ResultCode.PARAM_INVALID, "defaultRounds 取值 3~8");
        }
    }

    private Position requirePosition(Long id) {
        Position position = positionMapper.selectById(id);
        if (position == null) {
            throw new BizException(ResultCode.NOT_FOUND_OR_FORBIDDEN, "岗位不存在");
        }
        return position;
    }

    private PositionAdminVO toVO(Position p) {
        CompetencyModel model = positionService.parseModel(p);
        List<CompetencyModel.SkillItem> skills = model.getSkills();
        return PositionAdminVO.builder()
                .id(p.getId())
                .code(p.getCode())
                .name(p.getName())
                .description(p.getDescription())
                .defaultRounds(p.getDefaultRounds())
                .enabled(p.getEnabled())
                .skillCount(skills == null ? 0 : skills.size())
                .skills(skills)
                .scoreWeights(model.getScoreWeights())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
