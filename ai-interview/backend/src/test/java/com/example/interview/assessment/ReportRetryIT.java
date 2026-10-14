package com.example.interview.assessment;

import com.example.interview.ai.parser.AiFallbackException;
import com.example.interview.ai.provider.MockAiProvider;
import com.example.interview.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3-3.5 报告失败重试（计划 §9-M3 完成标准）：用 SpyBean 模拟“断开 AI”——
 * generateReport 首次调用抛错 → 报告缺失（10004）→ 再次 finish 触发重试 → 成功。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportRetryIT {

    private static final String GOOD_ANSWER =
            "缓存击穿指热点 key 过期瞬间大量请求同时打到数据库，常见对策是互斥锁保证只有一个线程回源、"
                    + "逻辑过期让旧值先行兜底，并把热点 key 的 TTL 加随机抖动错开过期时间 42。";

    @Autowired
    private MockMvc mockMvc;

    /** 真实 Mock 实现的间谍：仅让 generateReport 第 1 次调用失败，模拟真实 Provider 断连。 */
    @SpyBean
    private MockAiProvider mockAiProvider;

    @Test
    void failedReportGenerationRetriesViaFinish() throws Exception {
        doThrow(new AiFallbackException("模拟 AI 断连（M3-3.5 验证场景）"))
                .doCallRealMethod()
                .when(mockAiProvider).generateReport(any());

        // 驱动到可收卷状态：start → 一答一追问
        String token = registerAndLogin();
        long id = createAndStart(token);
        MvcResult answer = mockMvc.perform(post("/api/interviews/" + id + "/messages/text")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("roundNo", 0, "content", GOOD_ANSWER))))
                .andReturn();
        assertThat(code(body(answer))).isZero();
        awaitUntil("追问产出", () -> {
            try {
                return messageCount(token, id, "AI") >= 2;
            } catch (Exception e) {
                return false;
            }
        });

        // 收卷：首次报告生成因 AI 失败而无结果 → 10004
        MvcResult finish = mockMvc.perform(post("/api/interviews/" + id + "/finish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertThat(code(body(finish))).isZero();
        awaitUntil("FINISHED", () -> {
            try {
                return "FINISHED".equals(statusOf(token, id));
            } catch (Exception e) {
                return false;
            }
        });
        assertThat(reportCode(token, id)).isEqualTo(10004);

        // 重试入口（计划 §5.3 #17“允许 finish 后重试生成”）：再调 finish → 生成成功
        MvcResult retry = mockMvc.perform(post("/api/interviews/" + id + "/finish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> retryBody = body(retry);
        assertThat(code(retryBody)).isZero();
        @SuppressWarnings("unchecked")
        Map<String, Object> retryData = (Map<String, Object>) retryBody.get("data");
        assertThat(retryData.get("accepted")).isEqualTo(true);

        awaitUntil("重试生成成功", () -> {
            try {
                return reportCode(token, id) == 0;
            } catch (Exception e) {
                return false;
            }
        });
        MvcResult report = mockMvc.perform(get("/api/interviews/" + id + "/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body(report).get("data");
        assertThat(((Number) data.get("totalScore")).doubleValue()).isBetween(0.0, 100.0);
        assertThat((List<?>) data.get("improvementPlan")).isNotEmpty();
        // 幂等：报告已生成后再次 finish 不重复生成，直接返回 reportId
        MvcResult again = mockMvc.perform(post("/api/interviews/" + id + "/finish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> againData = (Map<String, Object>) body(again).get("data");
        assertThat(againData.get("reportId")).isNotNull();
    }

    // ==================== 工具 ====================

    private String registerAndLogin() throws Exception {
        String uname = "rt" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))));
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body(login).get("data");
        return (String) data.get("token");
    }

    private long createAndStart(String token) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("positionId", 1, "mode", "TEXT",
                                "difficulty", 3, "rounds", 5, "durationMinutes", 15))))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body(res).get("data");
        long id = ((Number) data.get("id")).longValue();
        mockMvc.perform(post("/api/interviews/" + id + "/start")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        return id;
    }

    private String statusOf(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/interviews/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body(res).get("data");
        return (String) data.get("status");
    }

    private long messageCount(String token, long id, String speaker) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/interviews/" + id + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> msgs = (List<Map<String, Object>>) body(res).get("data");
        return msgs.stream().filter(m -> speaker.equals(m.get("speaker"))).count();
    }

    private int reportCode(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/interviews/" + id + "/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return code(body(res));
    }

    private static void awaitUntil(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(MvcResult result) throws Exception {
        return JsonUtils.parse(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), Map.class);
    }

    private static int code(Map<String, Object> body) {
        return ((Number) body.get("code")).intValue();
    }
}
