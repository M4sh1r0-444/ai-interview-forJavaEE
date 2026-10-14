package com.example.interview.interview;

import com.example.interview.common.util.JsonUtils;
import com.example.interview.interview.scheduler.InterviewTimerScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 计划 §8.2 InterviewFlowIT 的 M3 部分（评分+报告）：完整一场 → report 接口返回
 * 完整 ReportVO（六维 0~100、总分回填、亮点/不足/建议/逐题证据齐全）；
 * finish 中途收卷与 TIMEOUT 自动收卷同样产出报告；未生成 → 10004；越权 → 40401。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewReportFlowIT {

    private static final String GOOD_ANSWER =
            "缓存击穿指热点 key 过期瞬间大量请求同时打到数据库，常见对策是互斥锁保证只有一个线程回源、"
                    + "逻辑过期让旧值先行兜底，并把热点 key 的 TTL 加随机抖动错开过期时间 42，"
                    + "同时配合接口限流与多级缓存降低数据库压力。";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InterviewTimerScheduler timerScheduler;

    // ==================== 工具 ====================

    private String registerAndLogin() throws Exception {
        String uname = "rp" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))));
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body(login).get("data");
        return (String) data.get("token");
    }

    private long createSession(String token) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("positionId", 1, "mode", "TEXT",
                                "difficulty", 3, "rounds", 5, "durationMinutes", 15))))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body(res).get("data");
        return ((Number) data.get("id")).longValue();
    }

    private Map<String, Object> submitText(String token, long id, int roundNo, String content) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/interviews/" + id + "/messages/text")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("roundNo", roundNo, "content", content))))
                .andReturn();
        return body(res);
    }

    private Map<String, Object> detail(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/interviews/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body(res).get("data");
        return data;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> messages(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/interviews/" + id + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return (List<Map<String, Object>>) body(res).get("data");
    }

    private MvcResult reportResult(String token, long id) throws Exception {
        return mockMvc.perform(get("/api/interviews/" + id + "/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
    }

    private int reportCode(String token, long id) throws Exception {
        return code(body(reportResult(token, id)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> reportData(String token, long id) throws Exception {
        return (Map<String, Object>) body(reportResult(token, id)).get("data");
    }

    private int currentRound(String token, long id) throws Exception {
        return ((Number) detail(token, id).get("currentRound")).intValue();
    }

    private long aiCountInRound(String token, long id, int roundNo) throws Exception {
        return messages(token, id).stream()
                .filter(m -> "AI".equals(m.get("speaker")) && ((Number) m.get("roundNo")).intValue() == roundNo)
                .count();
    }

    /**
     * 驱动一轮直至换题：M3 起规则引擎接入评分输入（D15），得分点未覆盖（R1）或
     * 覆盖完整（R4）都会追问，追问上限 3 次后 R7 强制换题——每轮固定 4 次作答。
     */
    private void answerRoundUntilAdvanced(String token, long id, int round) throws Exception {
        for (int k = 0; k <= 4 && currentRound(token, id) == round; k++) {
            long expected = aiCountInRound(token, id, round) + 1;
            assertThat(code(submitText(token, id, round, GOOD_ANSWER))).isZero();
            int finalRound = round;
            awaitUntil("轮 " + round + " 第 " + k + " 答 AI 产出", () -> {
                try {
                    return aiCountInRound(token, id, finalRound) >= expected
                            || currentRound(token, id) > finalRound;
                } catch (Exception e) {
                    return false;
                }
            });
        }
        assertThat(currentRound(token, id)).as("轮 %d 应已推进", round).isGreaterThan(round);
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

    // ==================== 用例 ====================

    @Test
    void fullInterviewGeneratesCompleteReport() throws Exception {
        String token = registerAndLogin();
        long id = createSession(token);
        assertThat(code(body(postStart(token, id)))).isZero();

        for (int round = 0; round < 5; round++) {
            answerRoundUntilAdvanced(token, id, round);
        }
        long finalId = id;
        awaitUntil("FINISHED", () -> {
            try {
                return "FINISHED".equals(detail(token, finalId).get("status"));
            } catch (Exception e) {
                return false;
            }
        });
        awaitUntil("报告生成", () -> {
            try {
                return reportCode(token, finalId) == 0;
            } catch (Exception e) {
                return false;
            }
        });

        Map<String, Object> report = reportData(token, id);
        // 总分回填 0~100 且与会话一致
        double total = ((Number) report.get("totalScore")).doubleValue();
        assertThat(total).isBetween(0.0, 100.0);
        assertThat(((Number) detail(token, id).get("totalScore")).doubleValue()).isEqualTo(total);
        assertThat((String) report.get("positionName")).contains("Java");
        assertThat(report.get("status")).isEqualTo("FINISHED");

        // 六维均在 0~100（TEXT 场 5 维，无 voice）
        @SuppressWarnings("unchecked")
        Map<String, Object> dims = (Map<String, Object>) report.get("dimensionScores");
        assertThat(dims.keySet()).containsExactlyInAnyOrder(
                "technical", "depth", "logic", "match", "communication");
        dims.forEach((dim, v) -> assertThat(((Number) v).doubleValue())
                .as("维度 %s", dim).isBetween(0.0, 100.0));

        // 亮点/不足/建议/评语齐全（验收 #9）
        assertThat((List<?>) report.get("strengths")).isNotEmpty();
        assertThat((List<?>) report.get("weaknesses")).isNotEmpty();
        assertThat((List<?>) report.get("improvementPlan")).isNotEmpty();
        assertThat((String) report.get("summary")).isNotBlank();
        assertThat((String) report.get("reportText")).isNotBlank();

        // 逐题证据：5 轮各一条，字段完整
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) report.get("questionEvidence");
        assertThat(evidence).hasSize(5);
        for (int i = 0; i < evidence.size(); i++) {
            Map<String, Object> q = evidence.get(i);
            assertThat(((Number) q.get("roundNo")).intValue()).isEqualTo(i);
            assertThat((String) q.get("questionContent")).isNotBlank();
            assertThat((String) q.get("answerExcerpt")).contains("缓存");
            assertThat((String) q.get("reason")).isNotBlank();
            assertThat(((Number) q.get("contentScore")).doubleValue()).isBetween(0.0, 100.0);
            @SuppressWarnings("unchecked")
            Map<String, Object> scores = (Map<String, Object>) q.get("scores");
            assertThat(scores).containsKeys("technical", "depth", "logic", "match", "communication");
            int covered = ((List<?>) q.getOrDefault("coveredPoints", List.of())).size();
            int missing = ((List<?>) q.getOrDefault("missingPoints", List.of())).size();
            assertThat(covered + missing).as("轮 %d 得分点判定", i).isPositive();
        }

        // 越权：他人读报告 40401；终态后不能再作答
        String other = registerAndLogin();
        assertThat(code(body(reportResult(other, id)))).isEqualTo(40401);
        assertThat(code(submitText(token, id, 4, GOOD_ANSWER))).isEqualTo(10005);
    }

    @Test
    void finishMidInterviewStillGeneratesReport() throws Exception {
        String token = registerAndLogin();
        long id = createSession(token);
        postStart(token, id);
        assertThat(code(submitText(token, id, 0, GOOD_ANSWER))).isZero();
        long finalId = id;
        awaitUntil("追问产出", () -> {
            try {
                return aiCountInRound(token, finalId, 0) >= 2;
            } catch (Exception e) {
                return false;
            }
        });

        MvcResult finish = mockMvc.perform(post("/api/interviews/" + id + "/finish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> finishBody = body(finish);
        assertThat(code(finishBody)).isZero();
        @SuppressWarnings("unchecked")
        Map<String, Object> finishData = (Map<String, Object>) finishBody.get("data");
        assertThat(finishData.get("accepted")).isEqualTo(true);
        assertThat(finishData.get("status")).isEqualTo("FINISHED");
        assertThat(detail(token, id).get("status")).isEqualTo("FINISHED");
        assertThat(detail(token, id).get("remainSeconds")).isNull();

        awaitUntil("中途收卷报告生成", () -> {
            try {
                return reportCode(token, finalId) == 0;
            } catch (Exception e) {
                return false;
            }
        });
        Map<String, Object> report = reportData(token, id);
        assertThat(((Number) report.get("totalScore")).doubleValue()).isBetween(0.0, 100.0);
        // 仅轮 0 有作答 → 逐题证据只有 1 条
        assertThat((List<?>) report.get("questionEvidence")).hasSize(1);
        // CREATED 会话不能 finish（应先 start 或 abort）
        long created = createSession(token);
        MvcResult bad = mockMvc.perform(post("/api/interviews/" + created + "/finish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertThat(code(body(bad))).isEqualTo(10005);
    }

    @Test
    void timeoutAutoFinishGeneratesReport() throws Exception {
        String token = registerAndLogin();
        long id = createSession(token);
        postStart(token, id);
        assertThat(code(submitText(token, id, 0, GOOD_ANSWER))).isZero();
        // 注入过期截止时间 → 到期自动 TIMEOUT 并走 finish 报告流程（M3-3.3）
        timerScheduler.register(id, LocalDateTime.now().minusSeconds(1));
        long finalId = id;
        awaitUntil("TIMEOUT", () -> {
            try {
                return "TIMEOUT".equals(detail(token, finalId).get("status"));
            } catch (Exception e) {
                return false;
            }
        });
        awaitUntil("TIMEOUT 报告生成", () -> {
            try {
                return reportCode(token, finalId) == 0;
            } catch (Exception e) {
                return false;
            }
        });
        Map<String, Object> report = reportData(token, id);
        assertThat(report.get("status")).isEqualTo("TIMEOUT");
        assertThat(((Number) report.get("totalScore")).doubleValue()).isBetween(0.0, 100.0);
        assertThat((List<?>) report.get("questionEvidence")).hasSize(1);
    }

    @Test
    void reportNotReadyReturns10004() throws Exception {
        String token = registerAndLogin();
        long id = createSession(token);
        postStart(token, id);
        assertThat(code(submitText(token, id, 0, GOOD_ANSWER))).isZero();
        // 未 finish：报告不存在 → 10004
        assertThat(reportCode(token, id)).isEqualTo(10004);
        // ABORTED 不生成报告，同样 10004
        mockMvc.perform(post("/api/interviews/" + id + "/abort")
                .header("Authorization", "Bearer " + token));
        assertThat(reportCode(token, id)).isEqualTo(10004);
    }

    private MvcResult postStart(String token, long id) throws Exception {
        return mockMvc.perform(post("/api/interviews/" + id + "/start")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
    }
}
