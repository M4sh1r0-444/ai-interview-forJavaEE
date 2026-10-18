package com.example.interview.auth;

import com.example.interview.common.util.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FIX-09 登录限流语义（决策 D2）：只统计失败尝试，成功登录清零计数；
 * 可信代理模式（trust-proxy=true）按 Nginx 覆写的 X-Real-IP 分桶，
 * 伪造 X-Forwarded-For 不能绕过；达到上限后第 N+1 次 429/42901。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.security.login-rate-limit-per-minute=2",
        "app.security.trust-proxy=true"
})
class LoginRateLimitIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String PASSWORD = "Rate@123456";

    @BeforeEach
    void clearRateLimitKeys() {
        Set<String> keys = Objects.requireNonNull(redisTemplate.keys("rl:login:*"));
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private MvcResult login(String username, String password, String xRealIp, String xff) throws Exception {
        var request = post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtils.toJson(Map.of("username", username, "password", password)));
        if (xRealIp != null) {
            request.header("X-Real-IP", xRealIp);
        }
        if (xff != null) {
            request.header("X-Forwarded-For", xff);
        }
        return mockMvc.perform(request).andReturn();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(MvcResult result) throws Exception {
        return JsonUtils.parse(result.getResponse().getContentAsString(), Map.class);
    }

    @Test
    void sixthFailureBlockedAndForgedXffCannotBypass() throws Exception {
        String user = "E2E_rl_" + System.nanoTime() % 100000;
        register(user);

        // 限额 2：第 1/2 次失败 → 10002（HTTP 200）
        assertThat(code(login(user, "wrong-password", "203.0.113.9", null))).isEqualTo(10002);
        assertThat(code(login(user, "wrong-password", "203.0.113.9", null))).isEqualTo(10002);
        // 第 3 次（N+1）→ 429/42901
        MvcResult blocked = login(user, "wrong-password", "203.0.113.9", null);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        assertThat(body(blocked).get("code")).isEqualTo(42901);

        // 伪造 X-Forwarded-For 换 IP 绕过：XFF 不被信任，仍按 X-Real-IP 分桶 → 依然 429
        MvcResult forged = login(user, "wrong-password", "203.0.113.9", "198.51.100.7");
        assertThat(forged.getResponse().getStatus()).isEqualTo(429);
        assertThat(body(forged).get("code")).isEqualTo(42901);
    }

    @Test
    void successfulLoginResetsFailureCount() throws Exception {
        String user = "E2E_rl_" + System.nanoTime() % 100000;
        register(user);
        String ip = "203.0.113.10";

        // 1 次失败后成功登录：计数清零（D2：正常用户不被误伤）
        assertThat(code(login(user, "wrong-password", ip, null))).isEqualTo(10002);
        assertThat(code(login(user, PASSWORD, ip, null))).isEqualTo(0);

        // 清零后重新允许 2 次失败，第 3 次才 429
        assertThat(code(login(user, "wrong-password", ip, null))).isEqualTo(10002);
        assertThat(code(login(user, "wrong-password", ip, null))).isEqualTo(10002);
        assertThat(login(user, "wrong-password", ip, null).getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void differentIpsHaveSeparateBuckets() throws Exception {
        String user = "E2E_rl_" + System.nanoTime() % 100000;
        register(user);
        // 203.0.113.11 打满限额
        assertThat(code(login(user, "wrong-password", "203.0.113.11", null))).isEqualTo(10002);
        assertThat(code(login(user, "wrong-password", "203.0.113.11", null))).isEqualTo(10002);
        assertThat(login(user, "wrong-password", "203.0.113.11", null)
                .getResponse().getStatus()).isEqualTo(429);
        // 另一 IP 不受影响（多用户不再共享 Nginx 容器 IP 全局限流）
        assertThat(code(login(user, "wrong-password", "203.0.113.12", null))).isEqualTo(10002);
    }

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of(
                                "username", username, "password", PASSWORD,
                                "realName", "E2E 限流用例", "major", "软件工程"))))
                .andExpect(status().isOk());
    }

    private int code(MvcResult result) throws Exception {
        return (Integer) body(result).get("code");
    }
}
