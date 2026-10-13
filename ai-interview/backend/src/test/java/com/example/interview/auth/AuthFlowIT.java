package com.example.interview.auth;

import com.example.interview.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 计划 §8.2 AuthFlowIT：注册→登录→me→PUT me→logout→再访问；
 * 各步 code=0；logout 后旧 token 401；重复注册 10001。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIT {

    @Autowired
    private MockMvc mockMvc;

    private static String username() {
        return "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @Test
    void fullAuthFlow() throws Exception {
        String uname = username();

        // 1. 注册成功
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(JsonUtils.toJson(Map.of(
                                "username", uname, "password", "123456",
                                "major", "软件工程", "realName", "集成测试"))))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> regBody = body(reg);
        assertThat(code(regBody)).isZero();
        assertThat(regBody.get("data")).isNotNull();

        // 2. 重复注册 → 10001
        MvcResult dup = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(code(body(dup))).isEqualTo(10001);

        // 3. 登录
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> loginBody = body(login);
        assertThat(code(loginBody)).isZero();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) loginBody.get("data");
        String token = (String) data.get("token");
        assertThat(token).isNotBlank();
        assertThat(data.get("expiresAt")).isNotNull();

        // 4. GET /me
        MvcResult me = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(code(body(me))).isZero();

        // 5. PUT /me（改姓名/专业，不能改 username/role）
        MvcResult upd = mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(JsonUtils.toJson(Map.of("realName", "新名字", "major", "计算机科学"))))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> updBody = body(upd);
        assertThat(code(updBody)).isZero();
        assertThat(updBody.toString()).contains("新名字");

        // 6. logout
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 7. 旧 token 再访问 → 401 + 40101
        MvcResult after = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(code(body(after))).isEqualTo(40101);
    }

    @Test
    void wrongPasswordAndValidation() throws Exception {
        String uname = username();
        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))));

        // 密码错误 → 10002
        MvcResult bad = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "wrong-pwd"))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(code(body(bad))).isEqualTo(10002);

        // 参数校验 → 400 + 40001
        MvcResult invalid = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(JsonUtils.toJson(Map.of("username", "ab", "password", "123456"))))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(code(body(invalid))).isEqualTo(40001);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(MvcResult result) throws Exception {
        return JsonUtils.parse(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), Map.class);
    }

    private static int code(Map<String, Object> body) {
        return ((Number) body.get("code")).intValue();
    }
}
