package com.example.interview.growth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.auth.security.CurrentUser;
import com.example.interview.growth.dto.HistoryItemVO;
import com.example.interview.interview.entity.InterviewSession;
import com.example.interview.interview.mapper.InterviewSessionMapper;
import com.example.interview.position.entity.Position;
import com.example.interview.position.mapper.PositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成长域服务（计划 §4.3 growth）。M3 交付 history；growth 曲线、掌握度与
 * 推荐接口（MasteryUpdater/WeaknessDetector/RecommendationEngine）排期 M5。
 */
@Service
@RequiredArgsConstructor
public class GrowthService {

    private final InterviewSessionMapper sessionMapper;
    private final PositionMapper positionMapper;

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
