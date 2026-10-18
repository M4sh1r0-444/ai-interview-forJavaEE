package com.example.interview.growth.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.common.result.Result;
import com.example.interview.growth.dto.HistoryItemVO;
import com.example.interview.growth.dto.RecommendationVO;
import com.example.interview.growth.dto.SkillGrowthVO;
import com.example.interview.growth.service.GrowthService;
import com.example.interview.growth.service.RecommendationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 成长域接口（计划 §5.4）。M3 交付 #20 history；M5-5.2 交付
 * #18 growth 与 #19 recommendations。分页参数校验见 FIX-11。
 */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@Validated
public class GrowthController {

    private final GrowthService growthService;
    private final RecommendationService recommendationService;

    /** 用例 18：按技能的成长曲线（?positionId= 可选过滤，records 升序）。 */
    @GetMapping("/growth")
    public Result<List<SkillGrowthVO>> growth(@RequestParam(required = false) Long positionId) {
        return Result.ok(growthService.growth(positionId));
    }

    /** 用例 19：个性化推荐（短板技能 Top3，按优先级降序；?positionId= 缺省取最近岗位）。 */
    @GetMapping("/recommendations")
    public Result<List<RecommendationVO>> recommendations(@RequestParam(required = false) Long positionId) {
        return Result.ok(recommendationService.recommendations(positionId));
    }

    /** 用例 20：我的面试历史（分页，时间倒序；page≥1、size 1~100，FIX-11）。 */
    @GetMapping("/history")
    public Result<Page<HistoryItemVO>> history(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 取值 1 起") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 取值 1~100")
            @Max(value = 100, message = "size 取值 1~100") int size) {
        return Result.ok(growthService.history(page, size));
    }
}
