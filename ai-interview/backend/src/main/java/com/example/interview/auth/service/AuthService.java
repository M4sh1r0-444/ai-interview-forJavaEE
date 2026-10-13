package com.example.interview.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.auth.dto.LoginRequest;
import com.example.interview.auth.dto.LoginResponse;
import com.example.interview.auth.dto.RegisterRequest;
import com.example.interview.auth.dto.UserVO;
import com.example.interview.auth.entity.SysUser;
import com.example.interview.auth.mapper.UserMapper;
import com.example.interview.auth.security.JwtAuthenticationFilter;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import com.example.interview.common.util.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 注册（查重 + BCrypt）、登录（限流 + 校验 + 签发 JWT + 记录 jti）、
 * 登出（jti 加入黑名单直至过期）（计划 §4.3）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String LOGIN_RATE_PREFIX = "rl:login:";
    private static final String LOGIN_JTI_PREFIX = "login:jti:";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.security.login-rate-limit-per-minute:5}")
    private int loginRateLimit;

    public UserVO register(RegisterRequest req) {
        Long count = userMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, req.getUsername()));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRealName(req.getRealName());
        user.setMajor(req.getMajor());
        user.setRole("STUDENT");
        user.setStatus(1);
        userMapper.insert(user);
        log.info("用户注册成功 uid={} username={}", user.getId(), user.getUsername());
        return UserVO.from(user);
    }

    public LoginResponse login(LoginRequest req, HttpServletRequest request) {
        checkLoginRateLimit(clientIp(request));
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
        Claims claims = jwtUtils.parse(token);
        // 登录时记录 jti（有效期与 token 一致），便于观测在线 token
        redisTemplate.opsForValue().set(LOGIN_JTI_PREFIX + claims.getId(),
                String.valueOf(user.getId()), Duration.ofMillis(jwtUtils.remainingMillis(claims)));
        log.info("用户登录成功 uid={} username={}", user.getId(), user.getUsername());
        return LoginResponse.builder()
                .token(token)
                .expiresAt(claims.getExpiration().getTime())
                .user(UserVO.from(user))
                .build();
    }

    /** 登出：将当前 token 的 jti 加入黑名单，TTL=剩余有效期。 */
    public void logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return;
        }
        try {
            Claims claims = jwtUtils.parse(header.substring(7));
            long remain = jwtUtils.remainingMillis(claims);
            if (remain > 0) {
                redisTemplate.opsForValue().set(
                        JwtAuthenticationFilter.BLACKLIST_PREFIX + claims.getId(), "1",
                        Duration.ofMillis(remain));
                redisTemplate.delete(LOGIN_JTI_PREFIX + claims.getId());
            }
            log.info("用户登出 jti={} 剩余{}ms", claims.getId(), remain);
        } catch (Exception e) {
            // token 已失效时登出幂等处理
            log.debug("登出时 token 已失效: {}", e.getMessage());
        }
    }

    /** 登录限流：每 IP 每分钟 N 次（默认 5，Redis INCR，计划 §7 补充）。 */
    private void checkLoginRateLimit(String ip) {
        String key = LOGIN_RATE_PREFIX + ip;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(60));
        }
        if (count != null && count > loginRateLimit) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS);
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
