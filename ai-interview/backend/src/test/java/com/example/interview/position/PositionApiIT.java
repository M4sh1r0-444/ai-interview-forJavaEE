package com.example.interview.position;

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
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 计划 §8.2 PositionApiIT：两岗位可见；skills 含 weight 求和=1。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PositionApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listDetailAndSkills() throws Exception {
        // 列表：两个种子岗位
        MvcResult list = mockMvc.perform(get("/api/positions")
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> listBody = body(list);
        assertThat(code(listBody)).isZero();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> positions = (List<Map<String, Object>>) listBody.get("data");
        assertThat(positions).hasSize(2);

        // 详情 + skills：每个岗位技能权重之和 = 1
        for (Map<String, Object> p : positions) {
            Long id = ((Number) p.get("id")).longValue();
            MvcResult detail = mockMvc.perform(get("/api/positions/" + id)
                            .header("Authorization", "Bearer " + studentToken()))
                    .andExpect(status().isOk())
                    .andReturn();
            Map<String, Object> detailBody = body(detail);
            assertThat(code(detailBody)).isZero();
            assertThat(((Number) ((Map<String, Object>) detailBody.get("data")).get("skillCount")).intValue())
                    .isGreaterThanOrEqualTo(7);

            MvcResult skills = mockMvc.perform(get("/api/positions/" + id + "/skills")
                            .header("Authorization", "Bearer " + studentToken()))
                    .andExpect(status().isOk())
                    .andReturn();
            Map<String, Object> skillsBody = body(skills);
            assertThat(code(skillsBody)).isZero();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> skillList = (List<Map<String, Object>>) skillsBody.get("data");
            double sum = skillList.stream()
                    .mapToDouble(s -> ((Number) s.get("weight")).doubleValue())
                    .sum();
            assertThat(sum).isCloseTo(1.0, within(1e-6));
        }
    }

    @Test
    void unauthenticatedRejected() throws Exception {
        mockMvc.perform(get("/api/positions"))
                .andExpect(status().isUnauthorized());
    }

    /** 注册临时学生并登录拿 token。 */
    private String studentToken() throws Exception {
        String uname = "pos" + System.nanoTime();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/register")
                        .contentType("application/json")
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login")
                        .contentType("application/json")
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
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
