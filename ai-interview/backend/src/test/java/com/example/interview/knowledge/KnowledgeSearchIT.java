package com.example.interview.knowledge;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 知识库检索（M4-4.3/4.5）：V5 种子可被「Redis 缓存」类回答召回；管理端新增片段
 * 立即可被检索；rebuild 可用；学生角色被 403 拦截（计划 §5.5）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KnowledgeSearchIT extends KnowledgeSearchSupport {

    @Autowired
    private MockMvc mockMvc;

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123456";

    // ==================== 工具（继承自 KnowledgeSearchSupport） ====================

    @Test
    void redisCacheAnswerRecallsKnowledgeChunks() {
        // 「Redis 缓存」类回答应召回缓存三兄弟/Redis 持久化类片段（计划 M4-4.3 完成标准）
        var chunks = search(1L, "+缓存 +redis");
        assertThat(chunks).isNotEmpty();
        assertThat(String.join("\n", chunks)).contains("缓存");

        // 岗位隔离：前端岗（2）检索不到 Java 岗片段关键词命中类内容
        var frontendChunks = search(2L, "+缓存 +redis");
        assertThat(frontendChunks).isEmpty();
    }

    @Test
    void fulltextRecallAcrossSeededSkills() {
        var jvmChunks = search(1L, "+JVM +内存");
        assertThat(jvmChunks).isNotEmpty();

        var vueChunks = search(2L, "+响应式");
        assertThat(vueChunks).isNotEmpty();

        var virtualList = search(2L, "+虚拟滚动");
        assertThat(virtualList).isNotEmpty();
    }

    @Test
    void adminCreateChunkImmediatelySearchableAndRebuild() throws Exception {
        String token = login(ADMIN, ADMIN_PASSWORD);
        String marker = "联调契约" + UUID.randomUUID().toString().substring(0, 6);

        Map<String, Object> req = Map.of(
                "positionId", 1,
                "title", "接口契约先行实践",
                "content", "接口契约先行：开发前用 OpenAPI 冻结接口定义，前端基于 mock 并行开发，"
                        + "后端实现完成后按文档对齐联调；字段命名、时间格式与错误码全部以契约为准，"
                        + "变更必须走评审与通知。这套实践能把联调问题消灭在编码之前。",
                "keywords", marker + ",接口契约,OpenAPI,mock,联调,错误码,字段命名,评审",
                "skillTags", "project_design");
        MvcResult created = mockMvc.perform(post("/api/admin/knowledge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(req)))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> body = body(created);
        assertThat(code(body)).isZero();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data.get("id")).isNotNull();
        assertThat(data.get("status")).isEqualTo("ENABLED");

        // 新增片段立即可被检索（计划 M4-4.5 完成标准）
        var chunks = search(1L, "+" + marker);
        assertThat(chunks).hasSize(1);

        // rebuild 正常返回
        MvcResult rebuild = mockMvc.perform(post("/api/admin/knowledge/rebuild")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertThat(code(body(rebuild))).isZero();

        // 学生无权访问（计划 §5.5：全部 ROLE_ADMIN → 40301/HTTP 403）
        String student = login("stu-" + UUID.randomUUID().toString().substring(0, 8), "123456");
        MvcResult forbidden = mockMvc.perform(post("/api/admin/knowledge")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(req)))
                .andExpect(status().isForbidden()).andReturn();
        assertThat(body(forbidden).get("code")).isEqualTo(40301);
    }

    @Test
    void createValidatesBadInput() throws Exception {
        String token = login(ADMIN, ADMIN_PASSWORD);
        MvcResult res = mockMvc.perform(post("/api/admin/knowledge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of(
                                "positionId", 1, "title", "t",
                                "content", "太短",
                                "keywords", "a,b", "skillTags", "x"))))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(code(body(res))).isEqualTo(40001);

        MvcResult badPosition = mockMvc.perform(post("/api/admin/knowledge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of(
                                "positionId", 999, "title", "有效标题",
                                "content", "内容".repeat(20),
                                "keywords", "关键词一,关键词二,关键词三,关键词四",
                                "skillTags", "java_basics"))))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(code(body(badPosition))).isEqualTo(40002);
    }

    @Test
    void noMatchReturnsEmptyList() {
        var chunks = search(1L, "+量子纠缠 +薛定谔");
        assertThat(chunks).isEmpty();
    }
}
