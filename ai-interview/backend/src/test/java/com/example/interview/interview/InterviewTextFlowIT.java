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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 计划 §8.2 InterviewFlowIT 的 M2 部分（文字闭环）：建会话→start→含追问的多轮回答→
 * 轮次用尽 FINISHED；非法 start/回答被 10005 拒绝；未问到的主问题不暴露；
 * 越权 40401；abort/TIMEOUT 后终态不可再答题。评分与报告断言在 M3 补齐。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewTextFlowIT {

    private static final String GOOD_ANSWER =
            "缓存击穿指热点 key 过期瞬间大量请求同时打到数据库，常见对策是互斥锁保证只有一个线程回源、"
                    + "逻辑过期让旧值先行兜底，以及把热点 key 的 TTL 加随机抖动错开过期时间 42，"
                    + "同时配合接口限流与多级缓存降低数据库压力。";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InterviewTimerScheduler timerScheduler;

    // ==================== 工具 ====================

    private String registerAndLogin() throws Exception {
        String uname = "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))));
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> body = body(login);
        assert code(body) == 0;
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return (String) data.get("token");
    }

    private long createSession(String token, Map<String, Object> overrides) throws Exception {
        Map<String, Object> req = new HashMap<>(Map.of(
                "positionId", 1, "mode", "TEXT", "difficulty", 3,
                "rounds", 5, "durationMinutes", 15));
        req.putAll(overrides);
        MvcResult res = mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(req)))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> body = body(res);
        assert code(body) == 0;
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return ((Number) data.get("id")).longValue();
    }

    private Map<String, Object> start(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/interviews/" + id + "/start")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return body(res);
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
        Map<String, Object> body = body(res);
        assert code(body) == 0;
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return data;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> messages(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/interviews/" + id + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> body = body(res);
        assert code(body) == 0;
        return (List<Map<String, Object>>) body.get("data");
    }

    private long aiCountInRound(String token, long id, int roundNo) throws Exception {
        return messages(token, id).stream()
                .filter(m -> "AI".equals(m.get("speaker")) && ((Number) m.get("roundNo")).intValue() == roundNo)
                .count();
    }

    /** 异步轮询等待（aiExecutor 产出落库通常毫秒级，上限 10s）。 */
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
    void fullTextInterviewFlowWithFollowups() throws Exception {
        String token = registerAndLogin();
        long id = createSession(token, Map.of());

        // 建会话：CREATED、rounds=5
        Map<String, Object> d = detail(token, id);
        assertThat(d.get("status")).isEqualTo("CREATED");
        assertThat(d.get("plannedRounds")).isEqualTo(5);
        assertThat(d.get("currentRound")).isEqualTo(0);
        assertThat(d.get("positionName")).asString().contains("Java");

        // start：返回首题，状态 WAITING_USER（RUNNING 为事务内中间态不可见）
        Map<String, Object> startBody = start(token, id);
        assertThat(code(startBody)).isZero();
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) startBody.get("data");
        assertThat(first.get("speaker")).isEqualTo("AI");
        assertThat(first.get("roundNo")).isEqualTo(0);
        assertThat((String) first.get("contentText")).isNotBlank();
        assertThat(detail(token, id).get("status")).isEqualTo("WAITING_USER");
        assertThat(detail(token, id).get("remainSeconds")).isNotNull();

        // 非法 start（重复）→ 10005
        assertThat(code(start(token, id))).isEqualTo(10005);

        // 轮 0：连续 4 次"模糊回答"（<60 字无数字）→ 前 3 次各触发一次追问，第 4 次追问上限强制换题
        for (int i = 0; i < 4; i++) {
            Map<String, Object> resp = submitText(token, id, 0, "不太清楚");
            assertThat(code(resp)).isZero();
            @SuppressWarnings("unchecked")
            Map<String, Object> respData = (Map<String, Object>) resp.get("data");
            assertThat(respData.get("accepted")).isEqualTo(true);
            int expectedAi = Math.min(i + 2, 4); // 主问题 + 追问数（第 4 次封顶后不再新增）
            long expected = expectedAi;
            long finalI = i;
            awaitUntil("追问产出 round0 #" + finalI, () -> {
                try {
                    return aiCountInRound(token, id, 0) >= expected;
                } catch (Exception e) {
                    return false;
                }
            });
        }
        long finalId = id;
        awaitUntil("追问上限后换题", () -> {
            try {
                return ((Number) detail(token, finalId).get("currentRound")).intValue() == 1;
            } catch (Exception e) {
                return false;
            }
        });
        assertThat(aiCountInRound(token, id, 0)).isEqualTo(4); // 主问题 + 3 追问

        // 轮 1~4：正常回答两次（第 1 次默认追问一次，第 2 次进入下一题）
        for (int round = 1; round <= 4; round++) {
            assertThat(code(submitText(token, id, round, GOOD_ANSWER))).isZero();
            int r = round;
            awaitUntil("追问 round" + r, () -> {
                try {
                    return aiCountInRound(token, id, r) >= 2;
                } catch (Exception e) {
                    return false;
                }
            });
            assertThat(code(submitText(token, id, round, GOOD_ANSWER))).isZero();
            awaitUntil("进入下一轮 round" + r, () -> {
                try {
                    return ((Number) detail(token, finalId).get("currentRound")).intValue() == r + 1;
                } catch (Exception e) {
                    return false;
                }
            });
        }

        // 轮次用尽 → FINISHED，currentRound=plannedRounds，全部消息可见
        Map<String, Object> finished = detail(token, id);
        assertThat(finished.get("status")).isEqualTo("FINISHED");
        assertThat(finished.get("currentRound")).isEqualTo(5);
        assertThat(finished.get("finishedAt")).isNotNull();
        assertThat(finished.get("remainSeconds")).isNull();

        // 消息完整性：轮 0 = 主问题+3 追问+4 回答；轮 1~4 各 = 主问题+1 追问+2 回答
        List<Map<String, Object>> msgs = messages(token, id);
        assertThat(msgs.size()).isEqualTo(5 + 3 + 4 + 4 + 8);
        assertThat(msgs.stream().filter(m -> "AI".equals(m.get("speaker"))).count()).isEqualTo(12);
        assertThat(msgs.stream().filter(m -> "USER".equals(m.get("speaker"))).count()).isEqualTo(12);
        for (int r = 0; r < 5; r++) {
            final int round = r;
            List<Map<String, Object>> roundMsgs = msgs.stream()
                    .filter(m -> ((Number) m.get("roundNo")).intValue() == round).toList();
            int expectedAi = round == 0 ? 4 : 2;
            int expectedUser = round == 0 ? 4 : 2;
            assertThat(roundMsgs.stream().filter(m -> "AI".equals(m.get("speaker"))).count())
                    .as("轮 %d AI 消息数", round).isEqualTo(expectedAi);
            assertThat(roundMsgs.stream().filter(m -> "USER".equals(m.get("speaker"))).count())
                    .as("轮 %d USER 消息数", round).isEqualTo(expectedUser);
            // 轮内 AI/USER 交替：首条为主问题（followupIndex=0），追问 followupIndex 递增
            assertThat(roundMsgs.get(0).get("speaker")).isEqualTo("AI");
            assertThat(roundMsgs.get(0).get("followupIndex")).isEqualTo(0);
            for (int i = 0; i < roundMsgs.size(); i++) {
                String expectedSpeaker = (i % 2 == 0) ? "AI" : "USER";
                assertThat(roundMsgs.get(i).get("speaker"))
                        .as("轮 %d 第 %d 条应为 %s", round, i, expectedSpeaker)
                        .isEqualTo(expectedSpeaker);
            }
        }
        // 终态后不能再作答
        assertThat(code(submitText(token, id, 4, GOOD_ANSWER))).isEqualTo(10005);
    }

    @Test
    void futureQuestionsHiddenUntilAsked() throws Exception {
        String token = registerAndLogin();
        long id = createSession(token, Map.of());
        start(token, id);

        // 轮 0 进行中：学生视角只看到轮 0 的主问题，后续主问题不暴露
        List<Map<String, Object>> visible = messages(token, id);
        assertThat(visible).hasSize(1);
        assertThat(((Number) visible.get(0).get("roundNo")).intValue()).isZero();

        // 推进到轮 1 后：轮 1 主问题可见，轮 2~4 仍隐藏
        submitText(token, id, 0, GOOD_ANSWER);
        long finalId = id;
        awaitUntil("追问产出", () -> {
            try {
                return aiCountInRound(token, finalId, 0) >= 2;
            } catch (Exception e) {
                return false;
            }
        });
        submitText(token, id, 0, GOOD_ANSWER);
        awaitUntil("进入轮 1", () -> {
            try {
                return ((Number) detail(token, finalId).get("currentRound")).intValue() == 1;
            } catch (Exception e) {
                return false;
            }
        });
        List<Map<String, Object>> visibleNow = messages(token, id);
        int maxVisibleRound = visibleNow.stream()
                .mapToInt(m -> ((Number) m.get("roundNo")).intValue()).max().orElse(-1);
        assertThat(maxVisibleRound).isEqualTo(1);
        // ADMIN 视角可见全部：5 道主问题 + 轮 0 的 1 次追问
        String adminToken = adminLogin();
        List<Map<String, Object>> adminView = messages(adminToken, id);
        assertThat(adminView.stream().filter(m -> "AI".equals(m.get("speaker"))).count()).isEqualTo(6);
    }

    @Test
    void abortMakesSessionTerminal() throws Exception {
        String token = registerAndLogin();
        long id = createSession(token, Map.of());
        start(token, id);
        submitText(token, id, 0, GOOD_ANSWER);
        long finalId = id;
        awaitUntil("追问产出", () -> {
            try {
                return aiCountInRound(token, finalId, 0) >= 2;
            } catch (Exception e) {
                return false;
            }
        });

        MvcResult abortRes = mockMvc.perform(post("/api/interviews/" + id + "/abort")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertThat(code(body(abortRes))).isZero();
        assertThat(detail(token, id).get("status")).isEqualTo("ABORTED");

        // 终态不可再答题、不可再 abort
        assertThat(code(submitText(token, id, 0, GOOD_ANSWER))).isEqualTo(10005);
        MvcResult abortAgain = mockMvc.perform(post("/api/interviews/" + id + "/abort")
                        .header("Authorization", "Bearer " + token)).andReturn();
        assertThat(code(body(abortAgain))).isEqualTo(10005);
    }

    @Test
    void timeoutExpiresSessionAndRejectsAnswers() throws Exception {
        String token = registerAndLogin();
        long id = createSession(token, Map.of());
        start(token, id);
        // 注入已过期截止时间，触发到期自动 TIMEOUT（M2-2.7 完成标准）
        timerScheduler.register(id, LocalDateTime.now().minusSeconds(1));
        long finalId = id;
        awaitUntil("TIMEOUT", () -> {
            try {
                return "TIMEOUT".equals(detail(token, finalId).get("status"));
            } catch (Exception e) {
                return false;
            }
        });
        assertThat(detail(token, id).get("finishedAt")).isNotNull();
        assertThat(code(submitText(token, id, 0, GOOD_ANSWER))).isEqualTo(10005);
    }

    @Test
    void validationAndOwnership() throws Exception {
        String token = registerAndLogin();
        // rounds/difficulty/durationMinutes 越界 → 40002
        assertThat(testCreate(token, Map.of("rounds", 9))).isEqualTo(40002);
        assertThat(testCreate(token, Map.of("difficulty", 6))).isEqualTo(40002);
        assertThat(testCreate(token, Map.of("durationMinutes", 10))).isEqualTo(40002);
        // 未 start 直接答题 → 10005
        long id = createSession(token, Map.of());
        assertThat(code(submitText(token, id, 0, GOOD_ANSWER))).isEqualTo(10005);
        // 越权：他人读会话 → 40401
        String other = registerAndLogin();
        MvcResult cross = mockMvc.perform(get("/api/interviews/" + id)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isOk()).andReturn();
        assertThat(code(body(cross))).isEqualTo(40401);
    }

    private int testCreate(String token, Map<String, Object> overrides) throws Exception {
        Map<String, Object> req = new HashMap<>(Map.of(
                "positionId", 1, "mode", "TEXT", "difficulty", 3,
                "rounds", 5, "durationMinutes", 15));
        req.putAll(overrides);
        MvcResult res = mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(req))).andReturn();
        return code(body(res));
    }

    private String adminLogin() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("username", "admin", "password", "Admin@123456"))))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body(login).get("data");
        return (String) data.get("token");
    }
}
