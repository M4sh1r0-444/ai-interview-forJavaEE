package com.example.interview.position.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import com.example.interview.common.util.JsonUtils;
import com.example.interview.position.dto.CompetencyModel;
import com.example.interview.position.dto.PositionVO;
import com.example.interview.position.dto.SkillVO;
import com.example.interview.position.entity.Position;
import com.example.interview.position.mapper.PositionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/** 岗位查询 + competency_model_json 解析（计划 §4.3）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionMapper positionMapper;

    /** 用例 6：启用岗位列表。 */
    public List<PositionVO> listEnabled() {
        List<Position> positions = positionMapper.selectList(
                Wrappers.<Position>lambdaQuery().eq(Position::getEnabled, 1).orderByAsc(Position::getId));
        return positions.stream().map(p -> PositionVO.builder()
                        .id(p.getId()).code(p.getCode()).name(p.getName())
                        .description(p.getDescription()).defaultRounds(p.getDefaultRounds())
                        .build())
                .toList();
    }

    /** 用例 7：详情（含技能数）。 */
    public PositionVO detail(Long id) {
        Position p = requireEnabled(id);
        return PositionVO.builder()
                .id(p.getId()).code(p.getCode()).name(p.getName())
                .description(p.getDescription()).defaultRounds(p.getDefaultRounds())
                .skillCount(parseModel(p).getSkills() == null ? 0 : parseModel(p).getSkills().size())
                .build();
    }

    /** 用例 8：技能清单。 */
    public List<SkillVO> skills(Long id) {
        Position p = requireEnabled(id);
        CompetencyModel model = parseModel(p);
        if (model.getSkills() == null) {
            return List.of();
        }
        return model.getSkills().stream().map(SkillVO::from).toList();
    }

    /** 供其他模块复用：解析能力模型（解析失败按空模型降级）。 */
    public CompetencyModel parseModel(Position p) {
        if (p.getCompetencyModelJson() == null || p.getCompetencyModelJson().isBlank()) {
            return new CompetencyModel();
        }
        try {
            return JsonUtils.parse(p.getCompetencyModelJson(), CompetencyModel.class);
        } catch (Exception e) {
            log.warn("岗位 {} 能力模型 JSON 解析失败: {}", p.getId(), e.getMessage());
            return new CompetencyModel();
        }
    }

    private Position requireEnabled(Long id) {
        Position p = positionMapper.selectById(id);
        if (p == null || p.getEnabled() == null || p.getEnabled() != 1) {
            throw new BizException(ResultCode.NOT_FOUND_OR_FORBIDDEN);
        }
        return p;
    }
}
