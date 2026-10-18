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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FIX-11 分页校验：history 与管理端题目/岗位/资源/用户列表的 page/size
 * 加 @Min(1)/@Max(100)——page=0/负数/超大 size 返回 400/40001，不再裸奔全量返回。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaginationValidationIT {

    @Autowired
    private MockMvc mockMvc;

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123456";

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(MvcResult r) throws Exception {
        return JsonUtils.parse(r.getResponse().getContentAsString(), Map.class);
    }

    private String studentToken() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("username", "demo01", "password", "123456"))))
                .andExpect(status().isOk()).andReturn();
        return (String) ((Map<String, Object>) body(r).get("data")).get("token");
    }

    private String adminToken() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("username", ADMIN, "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return (String) ((Map<String, Object>) body(r).get("data")).get("token");
    }

    private void assertBadPage(String url, String token, String query) throws Exception {
        MvcResult r = mockMvc.perform(get(url + "?" + query)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(body(r).get("code")).as("query=%s 应 400/40001", query).isEqualTo(40001);
    }

    private void assertOk(String url, String token, String query) throws Exception {
        MvcResult r = mockMvc.perform(get(url + "?" + query)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertThat(body(r).get("code")).as("query=%s 应正常返回", query).isEqualTo(0);
    }

    @Test
    void historyPaginationValidated() throws Exception {
        String token = studentToken();
        assertBadPage("/api/users/me/history", token, "page=0&size=10");
        assertBadPage("/api/users/me/history", token, "page=-1&size=10");
        assertBadPage("/api/users/me/history", token, "page=1&size=-5");
        assertBadPage("/api/users/me/history", token, "page=1&size=1000000");
        assertOk("/api/users/me/history", token, "page=1&size=10");
        assertOk("/api/users/me/history", token, "page=2&size=100");
    }

    @Test
    void adminListPaginationValidated() throws Exception {
        String token = adminToken();
        assertBadPage("/api/admin/questions", token, "page=0");
        assertBadPage("/api/admin/positions", token, "size=999999");
        assertBadPage("/api/admin/resources", token, "page=-3&size=10");
        assertBadPage("/api/admin/users", token, "page=0&size=0");
        assertOk("/api/admin/questions", token, "page=1&size=10");
        assertOk("/api/admin/positions", token, "page=1&size=10");
        assertOk("/api/admin/resources", token, "page=1&size=10");
        assertOk("/api/admin/users", token, "page=1&size=10");
    }
}
