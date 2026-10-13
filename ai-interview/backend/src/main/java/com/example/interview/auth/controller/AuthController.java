package com.example.interview.auth.controller;

import com.example.interview.auth.dto.LoginRequest;
import com.example.interview.auth.dto.LoginResponse;
import com.example.interview.auth.dto.RegisterRequest;
import com.example.interview.auth.dto.UserVO;
import com.example.interview.auth.service.AuthService;
import com.example.interview.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 用例 1：注册。 */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest req) {
        return Result.ok(authService.register(req));
    }

    /** 用例 2：登录。 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                        HttpServletRequest request) {
        return Result.ok(authService.login(req, request));
    }

    /** 用例 3：登出（jti 进黑名单）。 */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return Result.ok();
    }
}
