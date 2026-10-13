package com.example.interview.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 计划 §8.1：签发→解析回 uid/role；篡改签名抛异常；过期 token 抛异常。 */
class JwtUtilsTest {

    private static final String SECRET = "test-secret-key-32-bytes-minimum!!";
    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils(SECRET, 24);
        jwtUtils.init();
    }

    @Test
    void signAndParseRoundTrip() {
        String token = jwtUtils.generateToken(42L, "alice", "STUDENT");
        Claims claims = jwtUtils.parse(token);
        assertThat(jwtUtils.getUid(claims)).isEqualTo(42L);
        assertThat(jwtUtils.getUsername(claims)).isEqualTo("alice");
        assertThat(jwtUtils.getRole(claims)).isEqualTo("STUDENT");
        assertThat(claims.getId()).isNotBlank();
        assertThat(jwtUtils.remainingMillis(claims)).isPositive();
    }

    @Test
    void tamperedSignatureThrows() {
        String token = jwtUtils.generateToken(42L, "alice", "STUDENT");
        String tampered = token.substring(0, token.length() - 2) + "xy";
        assertThatThrownBy(() -> jwtUtils.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenThrows() {
        String token = jwtUtils.generateToken(42L, "alice", "STUDENT", Duration.ofSeconds(-10));
        assertThatThrownBy(() -> jwtUtils.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void secretTooShortFailsFast() {
        JwtUtils bad = new JwtUtils("short", 24);
        assertThatThrownBy(bad::init).isInstanceOf(IllegalStateException.class);
    }
}
