package com.example.interview.auth.service;

import com.example.interview.auth.mapper.UserMapper;
import com.example.interview.common.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FIX-09 客户端 IP 解析：仅可信代理模式（app.security.trust-proxy=true）读取
 * Nginx 覆写的 X-Real-IP；X-Forwarded-For 第一段可被客户端伪造，一律不信任。
 */
class AuthServiceClientIpTest {

    private final AuthService authService = new AuthService(
            mock(UserMapper.class), mock(PasswordEncoder.class),
            mock(JwtUtils.class), mock(StringRedisTemplate.class));

    private HttpServletRequest request(String xff, String xRealIp, String remoteAddr) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn(xff);
        when(req.getHeader("X-Real-IP")).thenReturn(xRealIp);
        when(req.getRemoteAddr()).thenReturn(remoteAddr);
        return req;
    }

    @Test
    void directModeIgnoresForgedProxyHeaders() {
        ReflectionTestUtils.setField(authService, "trustProxy", false);
        // 直连模式：伪造 X-Real-IP / X-Forwarded-For 均不改变限流键
        assertThat(authService.clientIp(request("10.1.1.1, 172.16.0.1", "9.9.9.9", "127.0.0.1")))
                .isEqualTo("127.0.0.1");
    }

    @Test
    void trustedProxyModePrefersNginxRealIp() {
        ReflectionTestUtils.setField(authService, "trustProxy", true);
        assertThat(authService.clientIp(request("10.1.1.1", "203.0.113.9", "172.18.0.5")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void trustedProxyModeNeverTrustsForwardedFor() {
        ReflectionTestUtils.setField(authService, "trustProxy", true);
        // 无 X-Real-IP（非 Nginx 前置/直连）时退回 remote addr，不读可伪造的 XFF 第一段
        assertThat(authService.clientIp(request("198.51.100.7", null, "127.0.0.1")))
                .isEqualTo("127.0.0.1");
        assertThat(authService.clientIp(request("198.51.100.7", "  ", "192.168.1.3")))
                .isEqualTo("192.168.1.3");
    }
}
