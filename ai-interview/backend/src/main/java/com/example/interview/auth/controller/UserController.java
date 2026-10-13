package com.example.interview.auth.controller;

import com.example.interview.auth.dto.UserUpdateRequest;
import com.example.interview.auth.dto.UserVO;
import com.example.interview.auth.security.CurrentUser;
import com.example.interview.auth.service.UserService;
import com.example.interview.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 用例 4：个人信息。 */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.getMe(CurrentUser.currentUserId()));
    }

    /** 用例 5：修改个人资料（不允许改 username/role）。 */
    @PutMapping("/me")
    public Result<UserVO> updateMe(@Valid @RequestBody UserUpdateRequest req) {
        return Result.ok(userService.updateMe(CurrentUser.currentUserId(), req));
    }
}
