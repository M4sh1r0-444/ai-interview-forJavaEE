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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FIX-11 协议级错误：路径参数类型错误/未知路径/缺 multipart 部分/方法不支持/
 * Content-Type 不支持 → 400/404/405/415 + 对应 code，不再冒 500/HTTP 200。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProtocolErrorIT {

    @Autowired
    private MockMvc mockMvc;

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123456";

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(MvcResult r) throws Exception {
        return JsonUtils.parse(r.getResponse().getContentAsString(), Map.class);
    }

    @Test
    void pathVariableTypeMismatchIs400Not500() throws Exception {
        String token = login();
        MvcResult r = mockMvc.perform(get("/api/interviews/abc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(body(r).get("code")).isEqualTo(40001);
    }

    @Test
    void unknownPathIs404Json() throws Exception {
        String token = login();
        MvcResult r = mockMvc.perform(get("/api/not-exist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andReturn();
        assertThat(body(r).get("code")).isEqualTo(40400);
        assertThat(body(r).get("message")).isNotNull();
    }

    @Test
    void missingMultipartPartIs400() throws Exception {
        String token = login();
        // multipart 请求但缺少 file 部分（其余表单参数齐全）→ 400/40001，而非 500
        MvcResult r = mockMvc.perform(post("/api/interviews/999999/messages/audio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .param("roundNo", "0")
                        .param("asrText", "测试转写")
                        .param("durationMs", "3000"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(body(r).get("code")).isEqualTo(40001);
    }

    @Test
    void methodNotAllowedIs405() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed()).andReturn();
        assertThat(body(r).get("code")).isEqualTo(40501);
    }

    @Test
    void unsupportedMediaTypeIs415() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType()).andReturn();
        assertThat(body(r).get("code")).isEqualTo(41501);
    }

    @Test
    void adminUpdateStatusMissingFieldIs400NotHttp200() throws Exception {
        String token = login();
        // body 缺 status → HTTP 400/40001（与其他参数错误一致），不得 HTTP 200 返回 40001
        MvcResult r = mockMvc.perform(put("/api/admin/users/2/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(body(r).get("code")).isEqualTo(40001);
    }

    private String login() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("username", ADMIN, "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return (String) ((Map<String, Object>) body(r).get("data")).get("token");
    }
}
