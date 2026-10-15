package com.example.interview.common;

import com.example.interview.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 计划 §8.2 SecurityIT：无 token 401 / 学生访问 admin 403 /
 * 跨用户读面试 40401（M5-5.4 完成标准：SecurityIT 确认 403 拦截，
 * 覆盖 M5 新增的全部管理端扩展接口）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void noTokenIsUnauthorized() throws Exception {
        for (String url : new String[]{
                "/api/users/me", "/api/users/me/growth", "/api/users/me/recommendations",
                "/api/interviews", "/api/admin/questions", "/api/admin/dashboard",
                "/api/admin/positions", "/api/admin/resources", "/api/admin/users"}) {
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40101));
        }
    }

    @Test
    void studentIsForbiddenOnAllAdminEndpoints() throws Exception {
        String student = registerAndLogin();
        String body = JsonUtils.toJson(Map.of("status", 1));
        // M5-5.4 扩展接口 + 既有管理端接口，全部 403/code=40301
        for (String url : new String[]{
                "/api/admin/dashboard", "/api/admin/positions", "/api/admin/positions/1",
                "/api/admin/resources", "/api/admin/users"}) {
            mockMvc.perform(get(url).header("Authorization", "Bearer " + student))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40301));
        }
        mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/positions/1/weights")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/resources")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/users/2/status")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        // 学生侧成长接口本人可用（鉴权边界不受影响）
        mockMvc.perform(get("/api/users/me/recommendations")
                        .header("Authorization", "Bearer " + student))
                .andExpect(status().isOk());
    }

    @Test
    void crossUserInterviewAccessIs40401AndAdminAllowed() throws Exception {
        String owner = registerAndLogin();
        MvcResult created = mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("positionId", 1, "mode", "TEXT",
                                "difficulty", 3, "rounds", 5, "durationMinutes", 15))))
                .andExpect(status().isOk()).andReturn();
        long sessionId = ((Number) ((Map<String, Object>) body(created).get("data")).get("id")).longValue();

        String other = registerAndLogin();
        // 他人访问：HTTP 200 + code 40401（归属校验，计划 §7-4）
        assertThat(codeOfGet(other, "/api/interviews/" + sessionId)).isEqualTo(40401);
        assertThat(codeOfGet(other, "/api/interviews/" + sessionId + "/messages")).isEqualTo(40401);
        assertThat(codeOfGet(other, "/api/interviews/" + sessionId + "/report")).isEqualTo(40401);
        // ADMIN 放行；本人可读
        String admin = adminToken();
        assertThat(codeOfGet(admin, "/api/interviews/" + sessionId)).isZero();
        assertThat(codeOfGet(owner, "/api/interviews/" + sessionId)).isZero();
    }

    // ==================== 工具 ====================

    private int codeOfGet(String token, String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return ((Number) body(result).get("code")).intValue();
    }

    @SuppressWarnings("unchecked")
    private String registerAndLogin() throws Exception {
        String uname = "sec" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))));
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
                .andExpect(status().isOk()).andReturn();
        return (String) ((Map<String, Object>) body(login).get("data")).get("token");
    }

    @SuppressWarnings("unchecked")
    private String adminToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("username", "admin", "password", "Admin@123456"))))
                .andExpect(status().isOk()).andReturn();
        return (String) ((Map<String, Object>) body(login).get("data")).get("token");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(MvcResult result) throws Exception {
        return JsonUtils.parse(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), Map.class);
    }
}
