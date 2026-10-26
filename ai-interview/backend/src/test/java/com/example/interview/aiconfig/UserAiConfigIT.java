package com.example.interview.aiconfig;

import com.example.interview.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V9 个人中心「AI 服务配置」IT：查/存/删全链路 + 协议护栏。覆盖：空配置 data=null；
 * 非法 URL 10011、缺字段 40001；保存后回显掩码（明文绝不出现）；api_key 落库为
 * v1: AES 密文；空 Key 更新保留旧密文；连通性测试不可达地址 ok=false 且不抛协议错；
 * 删除幂等、行真删、删后可重建；用户隔离；未登录 401。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAiConfigIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== 工具 ====================

    private record Auth(String token, String username) {
    }

    private Auth registerAndLogin() throws Exception {
        String uname = "ac" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))));
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
                .andExpect(status().isOk()).andReturn();
        return new Auth((String) data(login).get("token"), uname);
    }

    private MvcResult fetch(Auth a) throws Exception {
        return mockMvc.perform(get("/api/users/me/ai-config")
                .header("Authorization", "Bearer " + a.token())).andReturn();
    }

    private MvcResult save(Auth a, String json) throws Exception {
        return mockMvc.perform(put("/api/users/me/ai-config")
                .header("Authorization", "Bearer " + a.token())
                .contentType("application/json").content(json)).andReturn();
    }

    private MvcResult remove(Auth a) throws Exception {
        return mockMvc.perform(delete("/api/users/me/ai-config")
                .header("Authorization", "Bearer " + a.token())).andReturn();
    }

    private MvcResult test(Auth a, String json) throws Exception {
        return mockMvc.perform(post("/api/users/me/ai-config/test")
                .header("Authorization", "Bearer " + a.token())
                .contentType("application/json").content(json)).andReturn();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(MvcResult res) throws Exception {
        return JsonUtils.parse(res.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(MvcResult res) throws Exception {
        return (Map<String, Object>) body(res).get("data");
    }

    private static int code(Map<String, Object> body) {
        return ((Number) body.get("code")).intValue();
    }

    private int rowCount(Auth a) {
        Long uid = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, a.username());
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_ai_config WHERE user_id = ?", Integer.class, uid);
    }

    private Map<String, Object> rawRow(Auth a) {
        Long uid = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, a.username());
        return jdbcTemplate.queryForMap(
                "SELECT * FROM user_ai_config WHERE user_id = ?", uid);
    }

    // ==================== 查询/保存/回显 ====================

    @Test
    void lifecycleSaveMaskedEchoAndEncryptedAtRest() throws Exception {
        Auth a = registerAndLogin();

        // 空配置：code=0、data=null（@JsonInclude(NON_NULL) 省略）
        MvcResult empty = fetch(a);
        assertThat(empty.getResponse().getStatus()).isEqualTo(200);
        assertThat(code(body(empty))).isZero();
        assertThat(body(empty)).doesNotContainKey("data");

        // 非法 URL（ftp）→ 业务码 10011；缺 apiUrl → 400/40001；均未落库
        MvcResult badUrl = save(a, JsonUtils.toJson(
                Map.of("apiUrl", "ftp://a.com/v1", "apiKey", "sk-x", "model", "m")));
        assertThat(badUrl.getResponse().getStatus()).isEqualTo(200);
        assertThat(code(body(badUrl))).isEqualTo(10011);
        MvcResult blank = save(a, JsonUtils.toJson(Map.of("apiKey", "sk-x", "model", "m")));
        assertThat(blank.getResponse().getStatus()).isEqualTo(400);
        assertThat(code(body(blank))).isEqualTo(40001);
        assertThat(rowCount(a)).isZero();

        // 合法保存：回显掩码 + hasApiKey，明文绝不出现
        MvcResult saved = save(a, JsonUtils.toJson(Map.of(
                "apiUrl", "https://dashscope.aliyuncs.com/compatible-mode/v1/",
                "apiKey", "sk-test-1234567890abcd",
                "model", "qwen-plus")));
        assertThat(saved.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> vo = data(saved);
        assertThat(vo.get("hasApiKey")).isEqualTo(true);
        assertThat(vo.get("apiKeyMasked")).isEqualTo("sk-t****abcd");
        assertThat(vo.get("model")).isEqualTo("qwen-plus");
        assertThat(saved.getResponse().getContentAsString()).doesNotContain("sk-test-1234567890abcd");

        // GET 回显一致；URL 已去尾斜杠
        Map<String, Object> echo = data(fetch(a));
        assertThat(echo.get("apiKeyMasked")).isEqualTo("sk-t****abcd");
        assertThat(echo.get("apiUrl")).isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1");

        // 落库为 AES 密文（v1: 前缀，不含明文）
        Map<String, Object> row = rawRow(a);
        assertThat((String) row.get("api_key_cipher")).startsWith("v1:");
        assertThat((String) row.get("api_key_cipher")).doesNotContain("sk-test-1234567890abcd");
        String firstCipher = (String) row.get("api_key_cipher");

        // 空 Key 更新：换 URL/model，密文保持
        MvcResult updated = save(a, JsonUtils.toJson(Map.of(
                "apiUrl", "https://other.example.com/v1", "model", "deepseek-chat")));
        assertThat(code(body(updated))).isZero();
        Map<String, Object> row2 = rawRow(a);
        assertThat(row2.get("api_key_cipher")).isEqualTo(firstCipher);
        assertThat(row2.get("model")).isEqualTo("deepseek-chat");
        assertThat(data(fetch(a)).get("apiKeyMasked")).isEqualTo("sk-t****abcd");
    }

    @Test
    void connectivityTestUnreachableAddressReturnsOkFalse() throws Exception {
        Auth a = registerAndLogin();

        // 未保存且未填 Key → 10011
        MvcResult noKey = test(a, JsonUtils.toJson(
                Map.of("apiUrl", "https://a.com/v1", "model", "m")));
        assertThat(code(body(noKey))).isEqualTo(10011);

        // 端口 1 不可达（连接拒绝快速失败）→ code=0 + ok=false + 原因，无 latencyMs
        MvcResult res = test(a, JsonUtils.toJson(Map.of(
                "apiUrl", "http://127.0.0.1:1/v1", "apiKey", "sk-anything", "model", "qwen-plus")));
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> vo = data(res);
        assertThat(vo.get("ok")).isEqualTo(false);
        assertThat((String) vo.get("message")).isNotBlank();
        assertThat(vo).doesNotContainKey("latencyMs");

        // 用户隔离：另一个用户仍是空配置
        Auth other = registerAndLogin();
        assertThat(code(body(fetch(other)))).isZero();
        assertThat(body(fetch(other))).doesNotContainKey("data");
    }

    @Test
    void deleteIsIdempotentAndRowRemoved() throws Exception {
        Auth a = registerAndLogin();

        // 无配置时删除幂等成功
        assertThat(code(body(remove(a)))).isZero();

        save(a, JsonUtils.toJson(Map.of(
                "apiUrl", "https://a.com/v1", "apiKey", "sk-1234567890xy", "model", "m")));
        assertThat(rowCount(a)).isEqualTo(1);

        Map<String, Object> deleted = body(remove(a));
        assertThat(code(deleted)).isZero();
        assertThat(deleted.get("data")).isEqualTo(Map.of("deleted", true));
        assertThat(rowCount(a)).isZero();                      // 硬删（配置行无审计需求）
        assertThat(code(body(fetch(a)))).isZero();
        assertThat(body(fetch(a))).doesNotContainKey("data");

        // 删除后可重新保存（upsert 全新插入）
        assertThat(code(body(save(a, JsonUtils.toJson(Map.of(
                "apiUrl", "https://b.com/v1", "apiKey", "sk-9876543210ab", "model", "m2")))))).isZero();
        assertThat(rowCount(a)).isEqualTo(1);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me/ai-config")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/users/me/ai-config").contentType("application/json")
                        .content("{\"apiUrl\":\"https://a.com/v1\",\"model\":\"m\"}"))
                .andExpect(status().isUnauthorized());
    }
}
