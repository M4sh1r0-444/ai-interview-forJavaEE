package com.example.interview.growth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.common.util.JsonUtils;
import com.example.interview.growth.entity.AbilityRecord;
import com.example.interview.growth.mapper.AbilityRecordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M5-5.3 推荐引擎全链路：短板规则①/②在接口可见；错题重练来自最近一场
 * 面试 missingPoints；推荐结果落 Redis 缓存（TTL 10 分钟）且删键后可重算。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationIT {

    /** 模糊回答：keyPoints 全不命中 → 内容分低 + missingPoints 非空。 */
    private static final String BAD_ANSWER = "这个问题我不太了解，平时没有深入研究过，说不出具体的内容，只能凭感觉猜一下。";

    private static final List<String> POSITION1_SKILLS = List.of(
            "java_basics", "concurrency_jvm", "spring", "mysql",
            "redis_mq", "project_design", "communication");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AbilityRecordMapper abilityRecordMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void rule1VisibleInRecommendationsAndCacheWorks() throws Exception {
        String token = registerAndLogin(null);
        long uid = userIdOf(token);

        // 7 个技能各插入两条 <60 记录 → 规则①全部命中（真实掌握度数据源等价注入）
        for (String skill : POSITION1_SKILLS) {
            insertRecord(uid, skill, "55.00");
            insertRecord(uid, skill, "52.00");
        }

        List<Map<String, Object>> recs = recommendations(token, 1L);
        assertThat(recs).hasSize(3);
        for (Map<String, Object> rec : recs) {
            assertThat((String) rec.get("skillCode")).isIn(POSITION1_SKILLS);
            List<String> reasons = (List<String>) rec.get("weaknessReasons");
            assertThat(reasons).anyMatch(r -> r.contains("均低于 60"));
            assertThat((List<?>) rec.get("retryQuestions")).isNotNull();
        }
        List<Double> priorities = recs.stream()
                .map(r -> ((Number) r.get("priority")).doubleValue()).toList();
        assertThat(priorities).isSortedAccordingTo(java.util.Comparator.reverseOrder());

        // 缓存命中形态：键存在、TTL ∈ (0, 600s]（计划 §6.6：TTL 10 分钟）
        String key = "rec:" + uid + ":1";
        assertThat(redisTemplate.hasKey(key)).isTrue();
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(600);

        // 删键后走实时重算，结论一致
        redisTemplate.delete(key);
        assertThat(recommendations(token, 1L)).hasSize(3);
        assertThat(redisTemplate.hasKey(key)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void retryQuestionsComeFromLatestFinishedSession() throws Exception {
        String token = registerAndLogin(null);
        long uid = userIdOf(token);

        // 一场"答一轮糟糕回答即 finish"的面试 → 报告落库触发掌握度更新
        long sessionId = createAndStart(token);
        MvcResult answer = mockMvc.perform(post("/api/interviews/" + sessionId + "/messages/text")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("roundNo", 0, "content", BAD_ANSWER))))
                .andReturn();
        assertThat(code(body(answer))).isZero();
        awaitUntil("追问产出", () -> {
            try {
                return aiMessageCount(token, sessionId) >= 2;
            } catch (Exception e) {
                return false;
            }
        });
        mockMvc.perform(post("/api/interviews/" + sessionId + "/finish")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        awaitUntil("报告回填总分", () -> {
            try {
                return totalScoreOf(token, sessionId) != null;
            } catch (Exception e) {
                return false;
            }
        });

        // 真实面试写出的技能记录（round0 题目技能）
        AbilityRecord real = abilityRecordMapper.selectList(Wrappers.<AbilityRecord>lambdaQuery()
                        .eq(AbilityRecord::getUserId, uid))
                .get(0);
        String answeredSkill = real.getSkillCode();
        assertThat(((Number) real.getScore()).doubleValue()).isLessThan(70.0);

        // 其余技能注入高分记录（95 ≥ requiredLevel，无短板）→ 答题技能以绝对优先级居首
        for (String skill : POSITION1_SKILLS) {
            if (!skill.equals(answeredSkill)) {
                insertRecord(uid, skill, "95.00");
            }
        }
        // 报告落库时已预热缓存（事件），注入数据后删键强制重算
        redisTemplate.delete("rec:" + uid + ":1");

        List<Map<String, Object>> recs = recommendations(token, 1L);
        Map<String, Object> top = recs.get(0);
        assertThat(top.get("skillCode")).isEqualTo(answeredSkill);
        // 规则②：糟糕回答 → 掌握度低于岗位要求
        assertThat((List<String>) top.get("weaknessReasons"))
                .anyMatch(r -> r.contains("低于岗位要求"));
        // 错题重练：最近一场该题 missingPoints 命中
        List<Map<String, Object>> retry = (List<Map<String, Object>>) top.get("retryQuestions");
        assertThat(retry).isNotEmpty();
        assertThat(retry.get(0).get("questionId")).isNotNull();
        assertThat((List<?>) retry.get(0).get("missingPoints")).isNotEmpty();
    }

    // ==================== 工具 ====================

    @SuppressWarnings("unchecked")
    private String registerAndLogin(String prefix) throws Exception {
        String uname = (prefix != null ? prefix : "rc") + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))));
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> data = (Map<String, Object>) body(login).get("data");
        return (String) data.get("token");
    }

    private long userIdOf(String token) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return ((Number) ((Map<String, Object>) body(me).get("data")).get("id")).longValue();
    }

    private long createAndStart(String token) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("positionId", 1, "mode", "TEXT",
                                "difficulty", 3, "rounds", 5, "durationMinutes", 15))))
                .andExpect(status().isOk()).andReturn();
        long id = ((Number) ((Map<String, Object>) body(res).get("data")).get("id")).longValue();
        mockMvc.perform(post("/api/interviews/" + id + "/start")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        return id;
    }

    private void insertRecord(long uid, String skill, String score) {
        AbilityRecord record = new AbilityRecord();
        record.setUserId(uid);
        record.setPositionId(1L);
        record.setSkillCode(skill);
        record.setScore(new BigDecimal(score));
        abilityRecordMapper.insert(record);
    }

    private Object totalScoreOf(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/interviews/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return ((Map<String, Object>) body(res).get("data")).get("totalScore");
    }

    private long aiMessageCount(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/interviews/" + id + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return ((List<Map<String, Object>>) body(res).get("data")).stream()
                .filter(m -> "AI".equals(m.get("speaker"))).count();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> recommendations(String token, Long positionId) throws Exception {
        String query = positionId != null ? "?positionId=" + positionId : "";
        MvcResult res = mockMvc.perform(get("/api/users/me/recommendations" + query)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return (List<Map<String, Object>>) body(res).get("data");
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
