package com.example.interview.growth;

import com.example.interview.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 计划 §8.2 HistoryGrowthIT（M3-3.4 history 断言 + M5-5.1/5.2 完成标准）：
 * 同用户多场面试 → history 条数/排序/字段；两场后 ability_record 有记录（growth
 * 曲线非空、点数=场数）；recommendations 非空且按优先级降序。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HistoryGrowthIT {

    private static final String GOOD_ANSWER =
            "缓存击穿指热点 key 过期瞬间大量请求同时打到数据库，常见对策是互斥锁保证只有一个线程回源、"
                    + "逻辑过期让旧值先行兜底，并把热点 key 的 TTL 加随机抖动错开过期时间 42。";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void historyListsStartedSessionsNewestFirst() throws Exception {
        String token = registerAndLogin();

        // 会话 A：完成一场并等报告回填总分
        long a = createAndStart(token);
        MvcResult answer = mockMvc.perform(post("/api/interviews/" + a + "/messages/text")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("roundNo", 0, "content", GOOD_ANSWER))))
                .andReturn();
        assertThat(code(body(answer))).isZero();
        awaitUntil("追问产出", () -> {
            try {
                return messages(token, a).stream()
                        .filter(m -> "AI".equals(m.get("speaker"))).count() >= 2;
            } catch (Exception e) {
                return false;
            }
        });
        mockMvc.perform(post("/api/interviews/" + a + "/finish")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        awaitUntil("报告回填总分", () -> {
            try {
                return totalScoreOf(token, a) != null;
            } catch (Exception e) {
                return false;
            }
        });

        // 会话 B：开始后中止（有 startedAt，无总分）
        long b = createAndStart(token);
        mockMvc.perform(post("/api/interviews/" + b + "/abort")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        // 会话 C：仅创建未 start（不入历史）
        mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("positionId", 1, "mode", "TEXT",
                                "difficulty", 3, "rounds", 5, "durationMinutes", 15))))
                .andExpect(status().isOk());

        Map<String, Object> page = history(token, null, null);
        assertThat(((Number) page.get("total")).intValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) page.get("records");
        assertThat(records).hasSize(2);
        // 时间倒序：B（新）在前
        assertThat(((Number) records.get(0).get("sessionId")).longValue()).isEqualTo(b);
        assertThat(records.get(0).get("status")).isEqualTo("ABORTED");
        assertThat(records.get(0).get("totalScore")).isNull();
        assertThat(((Number) records.get(1).get("sessionId")).longValue()).isEqualTo(a);
        Map<String, Object> itemA = records.get(1);
        assertThat(itemA.get("status")).isEqualTo("FINISHED");
        assertThat(((Number) itemA.get("totalScore")).doubleValue()).isBetween(0.0, 100.0);
        assertThat((String) itemA.get("positionName")).contains("Java");
        assertThat(itemA.get("mode")).isEqualTo("TEXT");
        assertThat(itemA.get("startedAt")).isNotNull();
        assertThat(itemA.get("finishedAt")).isNotNull();

        // 分页：size=1 第一页 1 条、total 仍为 2
        Map<String, Object> firstPage = history(token, 1, 1);
        assertThat(((Number) firstPage.get("total")).intValue()).isEqualTo(2);
        assertThat((List<?>) firstPage.get("records")).hasSize(1);

        // 未登录 401；新用户历史为空
        mockMvc.perform(get("/api/users/me/history")).andExpect(status().isUnauthorized());
        String fresh = registerAndLogin();
        assertThat(((Number) history(fresh, null, null).get("total")).intValue()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void growthAndRecommendationsAfterTwoInterviews() throws Exception {
        String token = registerAndLogin();

        // 两场各答一轮（round0 + 追问）后 finish → 报告落库触发掌握度更新，
        // 每场为 round0 题目技能写一条 ability_record（M5-5.1 完成标准：两场后有记录）
        finishOneRoundInterview(token);
        finishOneRoundInterview(token);
        awaitUntil("两场各写一条能力记录", () -> {
            try {
                return totalGrowthPoints(growth(token, null)) >= 2;
            } catch (Exception e) {
                return false;
            }
        });

        // growth：曲线非空、records 升序、current=最新一条、技能名来自能力模型
        List<Map<String, Object>> growth = growth(token, null);
        assertThat(growth).isNotEmpty();
        int totalPoints = 0;
        for (Map<String, Object> skill : growth) {
            assertThat(skill.get("skillCode")).isNotNull();
            assertThat(skill.get("skillName")).isNotNull();
            List<Map<String, Object>> records = (List<Map<String, Object>>) skill.get("records");
            assertThat(records).isNotEmpty();
            assertThat(skill.get("current")).isNotNull();
            assertThat(((Number) skill.get("current")).doubleValue())
                    .isEqualTo(((Number) records.get(records.size() - 1).get("score")).doubleValue());
            List<String> evaluatedAt = records.stream()
                    .map(r -> String.valueOf(r.get("evaluatedAt"))).toList();
            assertThat(evaluatedAt).isSorted();
            totalPoints += records.size();
        }
        // 每场恰好命中 1 个技能（种子题均为单技能标签）→ 两场两条记录
        assertThat(totalPoints).isEqualTo(2);
        // positionId 过滤等价（该用户只有岗位 1 的记录）
        assertThat(growth(token, 1L)).hasSameSizeAs(growth);

        // recommendations：非空、≤Top3、优先级降序、资源/练习来自 V6 种子、建议非空
        List<Map<String, Object>> recs = recommendations(token, null);
        assertThat(recs).isNotEmpty();
        assertThat(recs).hasSizeLessThanOrEqualTo(3);
        List<Double> priorities = recs.stream()
                .map(r -> ((Number) r.get("priority")).doubleValue()).toList();
        assertThat(priorities).isSortedAccordingTo(Comparator.reverseOrder());
        for (Map<String, Object> rec : recs) {
            assertThat(rec.get("skillCode")).isNotNull();
            assertThat((List<?>) rec.get("weaknessReasons")).isNotEmpty();
            int resources = ((List<?>) rec.get("resources")).size();
            int practices = ((List<?>) rec.get("practices")).size();
            assertThat(resources + practices).isPositive();
            assertThat((String) rec.get("nextInterviewSuggested")).isNotBlank();
        }

        // 未登录 401；新用户 growth 为空、recommendations 走默认岗位（首个启用岗位）
        mockMvc.perform(get("/api/users/me/growth")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me/recommendations")).andExpect(status().isUnauthorized());
        String fresh = registerAndLogin();
        assertThat(growth(fresh, null)).isEmpty();
        assertThat(recommendations(fresh, null)).isNotEmpty();
    }

    // ==================== 工具 ====================

    private String registerAndLogin() throws Exception {
        String uname = "hs" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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

    private Object totalScoreOf(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/interviews/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body(res).get("data");
        return data.get("totalScore");
    }

    /** 完成一场"答一轮（round0 + 追问）即 finish"的面试，等待报告回填总分。 */
    private void finishOneRoundInterview(String token) throws Exception {
        long id = createAndStart(token);
        MvcResult answer = mockMvc.perform(post("/api/interviews/" + id + "/messages/text")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("roundNo", 0, "content", GOOD_ANSWER))))
                .andReturn();
        assertThat(code(body(answer))).isZero();
        awaitUntil("追问产出", () -> {
            try {
                return messages(token, id).stream()
                        .filter(m -> "AI".equals(m.get("speaker"))).count() >= 2;
            } catch (Exception e) {
                return false;
            }
        });
        mockMvc.perform(post("/api/interviews/" + id + "/finish")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        awaitUntil("报告回填总分", () -> {
            try {
                return totalScoreOf(token, id) != null;
            } catch (Exception e) {
                return false;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> growth(String token, Long positionId) throws Exception {
        String query = positionId != null ? "?positionId=" + positionId : "";
        MvcResult res = mockMvc.perform(get("/api/users/me/growth" + query)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return (List<Map<String, Object>>) body(res).get("data");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> recommendations(String token, Long positionId) throws Exception {
        String query = positionId != null ? "?positionId=" + positionId : "";
        MvcResult res = mockMvc.perform(get("/api/users/me/recommendations" + query)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return (List<Map<String, Object>>) body(res).get("data");
    }

    private static int totalGrowthPoints(List<Map<String, Object>> growth) {
        return growth.stream()
                .mapToInt(s -> ((List<?>) s.get("records")).size())
                .sum();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> messages(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/interviews/" + id + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return (List<Map<String, Object>>) body(res).get("data");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> history(String token, Integer page, Integer size) throws Exception {
        String query = page != null ? "?page=" + page + "&size=" + size : "";
        MvcResult res = mockMvc.perform(get("/api/users/me/history" + query)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return (Map<String, Object>) body(res).get("data");
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
