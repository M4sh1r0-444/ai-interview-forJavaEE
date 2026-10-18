package com.example.interview.admin;

import com.example.interview.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FIX-18 管理端入参校验：defaultRounds/enabled 越界、题目 positionId 不存在、
 * 并发同名注册唯一键冲突 → 400/40001、400/40002、10001，不再 500/裸奔成功。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminValidationIT {

    @Autowired
    private MockMvc mockMvc;

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123456";

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(MvcResult r) throws Exception {
        return JsonUtils.parse(r.getResponse().getContentAsString(), Map.class);
    }

    private String adminToken() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("username", ADMIN, "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return (String) ((Map<String, Object>) body(r).get("data")).get("token");
    }

    @Test
    void positionDefaultRoundsAndEnabledValidated() throws Exception {
        String token = adminToken();
        // defaultRounds=100（create 未校验的历史缺口）→ 400/40001
        MvcResult badRounds = mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of(
                                "code", "E2E_POS_BAD", "name", "坏轮次岗位",
                                "defaultRounds", 100,
                                "skills", List.of(Map.of(
                                        "skillCode", "java_basics", "skillName", "Java基础", "weight", 1.0))))))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(body(badRounds).get("code")).isEqualTo(40001);

        // enabled=5 → 400/40001（可写入非法值导致"非启用"语义漂移）
        MvcResult badEnabled = mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of(
                                "code", "E2E_POS_EN", "name", "坏启用岗位",
                                "enabled", 5,
                                "skills", List.of(Map.of(
                                        "skillCode", "java_basics", "skillName", "Java基础", "weight", 1.0))))))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(body(badEnabled).get("code")).isEqualTo(40001);
    }

    @Test
    void resourceEnabledValidated() throws Exception {
        String token = adminToken();
        MvcResult bad = mockMvc.perform(post("/api/admin/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of(
                                "positionId", 1, "skillCode", "spring", "title", "E2E 坏启用资源",
                                "resourceType", "ARTICLE", "difficulty", 3, "enabled", 2))))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(body(bad).get("code")).isEqualTo(40001);
    }

    @Test
    void questionPositionMustExistOnCreateAndUpdate() throws Exception {
        String token = adminToken();
        // create：positionId=99999 不存在 → 400/40002（原 FK 冲突冒 500/50001）
        MvcResult badCreate = mockMvc.perform(post("/api/admin/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of(
                                "positionId", 99999, "type", "TECHNICAL",
                                "content", "E2E 坏岗位题目：内容长度满足四个字符以上".repeat(2),
                                "difficulty", 3, "skillTags", List.of("java_basics")))))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(body(badCreate).get("code")).isEqualTo(40002);

        // update：把已有题目（种子 id=1，version 从列表接口取）改成不存在岗位 → 同样 400/40002
        MvcResult seed = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/admin/questions").queryParam("positionId", "1")
                        .queryParam("page", "1").queryParam("size", "100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> page = (Map<String, Object>) body(seed).get("data");
        Map<String, Object> data = ((List<Map<String, Object>>) page.get("records")).stream()
                .filter(q -> ((Number) q.get("id")).longValue() == 1L).findFirst().orElseThrow();
        MvcResult badUpdate = mockMvc.perform(put("/api/admin/questions/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of(
                                "positionId", 99999, "type", data.get("type"),
                                "content", "E2E 更新坏岗位题目：内容长度满足四个字符以上",
                                "difficulty", 3, "skillTags", List.of("java_basics"),
                                "version", data.get("version")))))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(body(badUpdate).get("code")).isEqualTo(40002);
    }

    @Test
    void concurrentRegisterSameUsernameYields10001ForLosers() throws Exception {
        String username = "E2E_race_" + System.nanoTime() % 100000;
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger usernameExists = new AtomicInteger();
        Callable<Void> task = () -> {
            barrier.await();
            MvcResult r = mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JsonUtils.toJson(Map.of(
                                    "username", username, "password", "Race@123456",
                                    "realName", "并发注册", "major", "软件工程"))))
                    .andReturn();
            int code = (Integer) body(r).get("code");
            if (code == 0) {
                success.incrementAndGet();
            } else if (code == 10001) {
                usernameExists.incrementAndGet();
            }
            return null;
        };
        List<Future<Void>> futures;
        try {
            futures = pool.invokeAll(java.util.stream.IntStream.range(0, threads)
                    .mapToObj(i -> task).toList());
        } finally {
            pool.shutdown();
        }
        for (Future<Void> f : futures) {
            f.get(); // 不得抛异常（原唯一键冲突冒 500）
        }
        assertThat(success.get()).as("恰好一个注册成功").isEqualTo(1);
        assertThat(usernameExists.get()).as("其余返回 10001（用户名已存在）").isEqualTo(threads - 1);
    }

    @Test
    void userStatusValueValidated() throws Exception {
        String token = adminToken();
        // status=5（FIX-11 StatusUpdateRequest 已拦 null；这里补非法值 0/1 之外）
        MvcResult bad = mockMvc.perform(put("/api/admin/users/2/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":5}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(body(bad).get("code")).isEqualTo(40001);
    }
}
