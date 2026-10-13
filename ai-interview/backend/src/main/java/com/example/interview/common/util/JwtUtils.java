package com.example.interview.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析（计划 §7-2）：HS256；claims uid/username/role/jti；exp 默认 24h。
 * secret 走环境变量 JWT_SECRET（≥32 字节）。
 */
@Component
public class JwtUtils {

    public static final String CLAIM_UID = "uid";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_ROLE = "role";

    private final String secret;
    @Getter
    private final long expireHours;
    private SecretKey key;

    public JwtUtils(@Value("${jwt.secret}") String secret,
                    @Value("${jwt.expire-hours:24}") long expireHours) {
        this.secret = secret;
        this.expireHours = expireHours;
    }

    @PostConstruct
    void init() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("jwt.secret 长度不足 32 字节，请通过 JWT_SECRET 环境变量配置");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(Long uid, String username, String role) {
        return generateToken(uid, username, role, Duration.ofHours(expireHours));
    }

    /** 供测试与特殊场景指定有效期。 */
    public String generateToken(Long uid, String username, String role, Duration ttl) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttl.toMillis());
        return Jwts.builder()
                .claim(CLAIM_UID, uid)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .subject(username)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /** 解析并验签，签名错误/过期抛 JwtException。 */
    public Claims parse(String token) throws JwtException {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    public Long getUid(Claims claims) {
        Object v = claims.get(CLAIM_UID);
        return v instanceof Number n ? n.longValue() : null;
    }

    public String getUsername(Claims claims) {
        return claims.get(CLAIM_USERNAME, String.class);
    }

    public String getRole(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }

    /** token 剩余有效毫秒数；已过期返回 0。 */
    public long remainingMillis(Claims claims) {
        return Math.max(0, claims.getExpiration().getTime() - System.currentTimeMillis());
    }
}
