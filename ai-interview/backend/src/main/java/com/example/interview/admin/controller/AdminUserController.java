package com.example.interview.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.admin.service.AdminUserService;
import com.example.interview.auth.dto.UserVO;
import com.example.interview.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端用户管理（计划 §5.5 #31★）。全部 ROLE_ADMIN。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 用例 #31★：用户列表（?keyword= 模糊 username/realName）。 */
    @GetMapping
    public Result<Page<UserVO>> list(@RequestParam(required = false) String keyword,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminUserService.page(keyword, page, size));
    }

    /** 用例 #31★：账号启停（{status: 0|1}；禁用后登录 10003）。 */
    @PutMapping("/{id}/status")
    public Result<UserVO> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null) {
            return Result.fail(com.example.interview.common.result.ResultCode.PARAM_INVALID, "status 必填");
        }
        return Result.ok(adminUserService.updateStatus(id, status));
    }
}
