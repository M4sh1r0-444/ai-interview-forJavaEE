package com.example.interview.auth.security;

import com.example.interview.common.util.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 解析 Bearer Token → 装载认证信息；先查 Redis 黑名单（计划 §4.3）。
 * 解析失败不中断链路，交由授权规则返回 401。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String BLACKLIST_PREFIX = "bl:";

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtils.parse(token);
                String jti = claims.getId();
                Boolean blacklisted = redisTemplate.hasKey(BLACKLIST_PREFIX + jti);
                if (Boolean.TRUE.equals(blacklisted)) {
                    log.debug("token 已登出 jti={}", jti);
                } else {
                    Authentication existing = SecurityContextHolder.getContext().getAuthentication();
                    if (existing == null) {
                        UserDetails details = userDetailsService.loadUserByUsername(
                                jwtUtils.getUsername(claims));
                        if (details.isEnabled()) {
                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(
                                            details, null, details.getAuthorities());
                            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        }
                    }
                }
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("无效 token: {}", e.getMessage());
            } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
                log.debug("token 用户不存在或已禁用");
            }
        }
        chain.doFilter(request, response);
    }
}
