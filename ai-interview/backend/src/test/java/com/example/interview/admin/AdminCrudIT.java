package com.example.interview.admin;

import com.example.interview.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 计划 §8.2 AdminCrudIT（M1 仅 CRUD 部分）：
 * 管理员建题→改题（带 version）→查列表→逻辑删；旧 version 再改 → 40003；
 * 学生访问管理端 → 403。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCrudIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void questionCrudFlow() throws Exception {
        String admin = adminToken();

        // 1. 新增
        Map<String, Object> createReq = Map.of(
                "positionId", 1,
                "type", "TECHNICAL",
                "content", "【IT】说说 G1 回收器的回收过程？",
                "difficulty", 4,
                "skillTags", List.of("concurrency_jvm"),
                "referenceAnswer", "G1 将堆分为 Region，并发标记后按停顿目标优先回收收益最高的区域。",
                "keyPoints", List.of("能说出 Region 与分代的关系", "能说出停顿预测模型"),
                "deductionPoints", List.of("把 G1 说成分代收集器的对立面"),
                "followupTemplates", List.of("G1 什么时候会退化成 Serial Old？"));
        MvcResult created = mockMvc.perform(post("/api/admin/questions")
                        .header("Authorization", "Bearer " + admin)
                        .contentType("application/json")
                        .content(JsonUtils.toJson(createReq)))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> createdBody = body(created);
        assertThat(code(createdBody)).isZero();
        @SuppressWarnings("unchecked")
        Map<String, Object> createdData = (Map<String, Object>) createdBody.get("data");
        long id = ((Number) createdData.get("id")).longValue();
        int version = ((Number) createdData.get("version")).intValue();
        assertThat(version).isEqualTo(1);
        assertThat(createdData.get("status")).isEqualTo("ENABLED");

        // 2. 正确 version 修改 → version+1
        Map<String, Object> updateReq = new java.util.HashMap<>(createReq);
        updateReq.put("content", "【IT】说说 G1 回收器的回收过程？（Mixed GC）");
        updateReq.put("version", version);
        MvcResult updated = mockMvc.perform(put("/api/admin/questions/" + id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType("application/json")
                        .content(JsonUtils.toJson(updateReq)))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> updatedBody = body(updated);
        assertThat(code(updatedBody)).isZero();
        assertThat(((Number) ((Map<String, Object>) updatedBody.get("data")).get("version")).intValue())
                .isEqualTo(2);

        // 3. 旧 version 再改 → 40003（乐观锁冲突）
        MvcResult conflict = mockMvc.perform(put("/api/admin/questions/" + id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType("application/json")
                        .content(JsonUtils.toJson(updateReq)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(code(body(conflict))).isEqualTo(40003);

        // 4. 列表可查到该题（过滤 TECHNICAL：种子 15 + 本例新增 1）
        MvcResult list = mockMvc.perform(get("/api/admin/questions")
                        .queryParam("positionId", "1")
                        .queryParam("type", "TECHNICAL")
                        .queryParam("page", "1")
                        .queryParam("size", "200")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> listBody = body(list);
        assertThat(code(listBody)).isZero();
        assertThat(((Number) ((Map<String, Object>) listBody.get("data")).get("total")).longValue())
                .isGreaterThanOrEqualTo(16);

        // 5. 逻辑删 → status=DISABLED
        mockMvc.perform(delete("/api/admin/questions/" + id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        MvcResult afterDel = mockMvc.perform(get("/api/admin/questions")
                        .queryParam("positionId", "1")
                        .queryParam("page", "1")
                        .queryParam("size", "500")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>)
                ((Map<String, Object>) body(afterDel).get("data")).get("records");
        Map<String, Object> row = records.stream()
                .filter(r -> ((Number) r.get("id")).longValue() == id)
                .findFirst().orElseThrow();
        assertThat(row.get("status")).isEqualTo("DISABLED");
    }

    @Test
    void studentForbidden() throws Exception {
        String uname = "stu" + System.nanoTime();
        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))));
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = ((Map<String, Object>) body(login).get("data")).get("token").toString();

        MvcResult forbidden = mockMvc.perform(get("/api/admin/questions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(code(body(forbidden))).isEqualTo(40301);
    }

    private String adminToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(JsonUtils.toJson(Map.of("username", "admin", "password", "Admin@123456"))))
                .andExpect(status().isOk())
                .andReturn();
        return ((Map<String, Object>) body(login).get("data")).get("token").toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(MvcResult result) throws Exception {
        return JsonUtils.parse(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), Map.class);
    }

    private static int code(Map<String, Object> body) {
        return ((Number) body.get("code")).intValue();
    }
}
