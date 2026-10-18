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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 注册（查重 + BCrypt）、登录（限流 + 校验 + 签发 JWT + 记录 jti）、
 * 登出（jti 加入黑名单直至过期）（计划 §4.3）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String LOGIN_FAIL_PREFIX = "rl:login:fail:";
    private static final String LOGIN_JTI_PREFIX = "login:jti:";

    /** 失败计数 INCR + 首次设置过期一体化（Lua 原子，避免 key 永不过期，FIX-09）。 */
    private static final DefaultRedisScript<Long> FAIL_INCR_SCRIPT = new DefaultRedisScript<>("""
            local c = redis.call('INCR', KEYS[1])
            if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return c
            """, Long.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.security.login-rate-limit-per-minute:5}")
    private int loginRateLimit;

    /** 可信代理模式（FIX-09）：true 时读取 Nginx 覆写的 X-Real-IP；dev 直连默认 false。 */
    @Value("${app.security.trust-proxy:false}")
    private boolean trustProxy;

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
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // FIX-18：查重后并发窗口内同名注册——唯一键冲突转 10001，不再冒 500
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }
        log.info("用户注册成功 uid={} username={}", user.getId(), user.getUsername());
        return UserVO.from(user);
    }

    public LoginResponse login(LoginRequest req, HttpServletRequest request) {
        String failKey = LOGIN_FAIL_PREFIX + clientIp(request);
        if (failureCount(failKey) >= loginRateLimit) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS);
        }
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            recordLoginFailure(failKey);
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
        // 成功登录清零该 IP 失败计数（FIX-09 决策 D2：只统计失败，正常用户不被误伤）
        redisTemplate.delete(failKey);
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

    /** 登录限流（FIX-09/D2）：每 IP 每分钟 N 次**失败**尝试，Lua 原子计数。 */
    private void recordLoginFailure(String key) {
        Long count = redisTemplate.execute(FAIL_INCR_SCRIPT, List.of(key), "60");
        log.debug("登录失败计数 ip-key={} count={}", key, count);
    }

    private int failureCount(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 客户端 IP（FIX-09）：仅可信代理模式（app.security.trust-proxy，prod/Compose 置 true）
     * 读取 Nginx 用 $remote_addr 覆写的 X-Real-IP；X-Forwarded-For 第一段可被客户端
     * 伪造，任何模式下都不信任。dev 直连走 getRemoteAddr()。
     */
    String clientIp(HttpServletRequest request) {
        if (trustProxy) {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
