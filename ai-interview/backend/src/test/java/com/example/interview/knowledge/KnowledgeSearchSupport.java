package com.example.interview.knowledge;

import com.example.interview.common.util.JsonUtils;
import com.example.interview.knowledge.service.KnowledgeSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 知识库集成测试公共支撑：子类继承本类的 Spring 上下文注解与注入字段。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class KnowledgeSearchSupport {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected KnowledgeSearchService searchService;

    /** 直接调用检索服务（FULLTEXT SQL 验证口径）。 */
    protected List<String> search(long positionId, String booleanQuery) {
        return searchService.search(positionId, booleanQuery);
    }

    /** 已有账号登录；不存在则先注册（学生角色）。 */
    protected String login(String username, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("username", username, "password", password))))
                .andReturn();
        Map<String, Object> body = body(res);
        if (code(body) != 0) {
            mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(JsonUtils.toJson(Map.of("username", username, "password", password))));
            res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(JsonUtils.toJson(Map.of("username", username, "password", password))))
                    .andExpect(status().isOk()).andReturn();
            body = body(res);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return (String) data.get("token");
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> body(MvcResult res) throws Exception {
        return JsonUtils.parse(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                Map.class);
    }

    protected static int code(Map<String, Object> body) {
        return ((Number) body.get("code")).intValue();
    }
}
