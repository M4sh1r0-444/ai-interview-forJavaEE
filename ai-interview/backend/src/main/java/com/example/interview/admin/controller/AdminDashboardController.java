package com.example.interview.admin.controller;

import com.example.interview.admin.dto.DashboardVO;
import com.example.interview.admin.service.DashboardService;
import com.example.interview.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端仪表盘（计划 §5.5 #27）：用户数/场次/题量/平均分/近 7 日趋势。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public Result<DashboardVO> dashboard() {
        return Result.ok(dashboardService.dashboard());
    }
}
