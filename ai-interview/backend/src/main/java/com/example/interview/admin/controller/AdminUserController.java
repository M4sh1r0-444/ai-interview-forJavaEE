package com.example.interview.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.admin.dto.StatusUpdateRequest;
import com.example.interview.admin.service.AdminUserService;
import com.example.interview.auth.dto.UserVO;
import com.example.interview.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户管理（计划 §5.5 #31★）。全部 ROLE_ADMIN。
 * 分页校验与 StatusUpdateRequest 见 FIX-11/FIX-18。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 用例 #31★：用户列表（?keyword= 模糊 username/realName；page≥1、size 1~100）。 */
    @GetMapping
    public Result<Page<UserVO>> list(@RequestParam(required = false) String keyword,
                                     @RequestParam(defaultValue = "1")
                                     @Min(value = 1, message = "page 取值 1 起") int page,
                                     @RequestParam(defaultValue = "10")
                                     @Min(value = 1, message = "size 取值 1~100")
                                     @Max(value = 100, message = "size 取值 1~100") int size) {
        return Result.ok(adminUserService.page(keyword, page, size));
    }

    /** 用例 #31★：账号启停（{status: 0|1}；禁用后登录 10003）。缺 status → 400/40001（FIX-11）。 */
    @PutMapping("/{id}/status")
    public Result<UserVO> updateStatus(@PathVariable Long id,
                                       @Valid @RequestBody StatusUpdateRequest req) {
        return Result.ok(adminUserService.updateStatus(id, req.getStatus()));
    }
}
