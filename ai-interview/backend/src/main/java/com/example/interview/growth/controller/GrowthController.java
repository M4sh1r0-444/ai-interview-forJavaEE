package com.example.interview.growth.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.common.result.Result;
import com.example.interview.growth.dto.HistoryItemVO;
import com.example.interview.growth.service.GrowthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成长域接口（计划 §5.4）。M3 交付 #20 history；
 * #18 growth 与 #19 recommendations 排期 M5。
 */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class GrowthController {

    private final GrowthService growthService;

    /** 用例 20：我的面试历史（分页，时间倒序）。 */
    @GetMapping("/history")
    public Result<Page<HistoryItemVO>> history(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return Result.ok(growthService.history(page, size));
    }
}
