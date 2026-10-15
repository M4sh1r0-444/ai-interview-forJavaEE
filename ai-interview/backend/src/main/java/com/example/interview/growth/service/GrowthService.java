package com.example.interview.growth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.auth.security.CurrentUser;
import com.example.interview.growth.dto.HistoryItemVO;
import com.example.interview.growth.dto.SkillGrowthVO;
import com.example.interview.growth.entity.AbilityRecord;
import com.example.interview.growth.mapper.AbilityRecordMapper;
import com.example.interview.interview.entity.InterviewSession;
import com.example.interview.interview.mapper.InterviewSessionMapper;
import com.example.interview.position.dto.CompetencyModel;
import com.example.interview.position.entity.Position;
import com.example.interview.position.mapper.PositionMapper;
import com.example.interview.position.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成长域服务（计划 §4.3 growth）。M3 交付 history；M5-5.2 交付 growth 曲线
 * （掌握度数据源 ability_record，M5-5.1 MasteryUpdater 追加写入）；
 * recommendations 见 RecommendationService；短板识别四规则引擎排期 M5-5.3。
 */
@Service
@RequiredArgsConstructor
public class GrowthService {

    private final InterviewSessionMapper sessionMapper;
    private final PositionMapper positionMapper;
    private final AbilityRecordMapper abilityRecordMapper;
    private final PositionService positionService;

    /**
     * 用例 18（计划 §5.4 #18）：按技能的成长曲线。positionId 可选过滤；
     * records 按 evaluated_at 升序（追加式记录即曲线数据源，计划 §3.8），
     * current = 最新一条 score；技能顺序按岗位能力模型序排列。
     */
    public List<SkillGrowthVO> growth(Long positionId) {
        Long uid = CurrentUser.currentUserId();
        List<AbilityRecord> records = abilityRecordMapper.selectList(
                Wrappers.<AbilityRecord>lambdaQuery()
                        .eq(AbilityRecord::getUserId, uid)
                        .eq(positionId != null, AbilityRecord::getPositionId, positionId)
                        .orderByAsc(AbilityRecord::getEvaluatedAt)
                        .orderByAsc(AbilityRecord::getId));
        if (records.isEmpty()) {
            return List.of();
        }
        Map<String, List<AbilityRecord>> bySkill = new LinkedHashMap<>();
        records.forEach(r -> bySkill.computeIfAbsent(r.getSkillCode(), k -> new ArrayList<>()).add(r));

        // 技能顺序与名称：涉及岗位的能力模型技能序（先出现的岗位优先）
        Map<String, String> skillNames = new LinkedHashMap<>();
        records.stream().map(AbilityRecord::getPositionId).distinct().sorted()
                .forEach(pid -> {
                    Position p = positionMapper.selectById(pid);
                    if (p == null) {
                        return;
                    }
                    List<CompetencyModel.SkillItem> skills = positionService.parseModel(p).getSkills();
                    if (skills != null) {
                        skills.forEach(s -> {
                            if (s.getSkillCode() != null) {
                                skillNames.putIfAbsent(s.getSkillCode(), s.getSkillName());
                            }
                        });
                    }
                });
        List<String> order = new ArrayList<>(skillNames.keySet());
        bySkill.keySet().forEach(code -> {
            if (!order.contains(code)) {
                order.add(code); // 模型外的历史技能（如岗位模型调整过）追加在后
            }
        });

        return order.stream().filter(bySkill::containsKey).map(code -> {
            List<AbilityRecord> rs = bySkill.get(code);
            return SkillGrowthVO.builder()
                    .skillCode(code)
                    .skillName(skillNames.get(code))
                    .records(rs.stream().map(r -> SkillGrowthVO.GrowthPoint.builder()
                            .evaluatedAt(r.getEvaluatedAt())
                            .score(r.getScore())
                            .build()).toList())
                    .current(rs.get(rs.size() - 1).getScore())
                    .build();
        }).toList();
    }

    /**
     * 当前用户的面试历史（分页，时间倒序）。只含 start 过的会话（started_at 非空），
     * CREATED 未开始的草稿会话不计入（计划 §5.4 #20）。
     */
    public Page<HistoryItemVO> history(int page, int size) {
        Long uid = CurrentUser.currentUserId();
        Page<InterviewSession> result = sessionMapper.selectPage(new Page<>(page, size),
                Wrappers.<InterviewSession>lambdaQuery()
                        .eq(InterviewSession::getUserId, uid)
                        .isNotNull(InterviewSession::getStartedAt)
                        .orderByDesc(InterviewSession::getId));
        Map<Long, String> positionNames = result.getRecords().stream()
                .map(InterviewSession::getPositionId)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), pid -> {
                    Position p = positionMapper.selectById(pid);
                    return p != null ? p.getName() : null;
                }));
        List<HistoryItemVO> items = result.getRecords().stream()
                .map(s -> HistoryItemVO.from(s, positionNames.get(s.getPositionId())))
                .toList();
        Page<HistoryItemVO> vo = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        vo.setRecords(items);
        return vo;
    }
}
