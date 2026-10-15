package com.example.interview.admin;

import com.example.interview.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M5-5.4 管理端扩展接口（计划 §5.5 #27~31★）：dashboard 数据正确
 * （对照 SQL 实算）、岗位/权重维护、学习资源 CRUD、用户启停。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminExtendedIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 测试中途失败也不残留启用的临时岗位（污染 PositionApiIT 的"恰好 2 岗位"断言）。 */
    @org.junit.jupiter.api.AfterEach
    void disableLeftoverTestPositions() {
        jdbcTemplate.update("UPDATE `position` SET enabled = 0 WHERE code LIKE 'tp%'");
    }

    // ==================== #27 dashboard 数据正确 ====================

    @Test
    @SuppressWarnings("unchecked")
    void dashboardMatchesSqlAggregates() throws Exception {
        String admin = adminToken();
        Map<String, Object> before = dashboard(admin);

        // 新注册 1 用户 + 开考 1 场（started_at 非空才计入场次）
        String token = registerAndLogin();
        long sessionId = createAndStart(token);

        Map<String, Object> after = dashboard(admin);
        assertThat(((Number) after.get("userCount")).longValue())
                .isEqualTo(((Number) before.get("userCount")).longValue() + 1);
        assertThat(((Number) after.get("sessionCount")).longValue())
                .isEqualTo(((Number) before.get("sessionCount")).longValue() + 1);

        // 计数与 SQL 实算一致（题量、用户数、已开始场次）
        assertThat(((Number) after.get("questionCount")).longValue())
                .isEqualTo(countSql("SELECT COUNT(*) FROM question"));
        assertThat(((Number) after.get("userCount")).longValue())
                .isEqualTo(countSql("SELECT COUNT(*) FROM sys_user"));
        assertThat(((Number) after.get("sessionCount")).longValue())
                .isEqualTo(countSql("SELECT COUNT(*) FROM interview_session WHERE started_at IS NOT NULL"));

        // 平均分 = 全部出分场次总分的 SQL 平均（保留 2 位）
        Double sqlAvg = jdbcTemplate.queryForObject(
                "SELECT AVG(total_score) FROM interview_session WHERE total_score IS NOT NULL", Double.class);
        Object avg = after.get("avgScore");
        if (sqlAvg != null) {
            assertThat(avg).isNotNull();
            assertThat(((Number) avg).doubleValue())
                    .isCloseTo(sqlAvg, org.assertj.core.data.Offset.offset(0.01));
        } else {
            assertThat(avg).isNull();
        }

        // 近 7 日趋势：7 条、日期升序、末日=今日；今日场次较操作前 +1
        List<Map<String, Object>> recent = (List<Map<String, Object>>) after.get("recent7Days");
        assertThat(recent).hasSize(7);
        List<String> dates = recent.stream().map(d -> String.valueOf(d.get("date"))).toList();
        assertThat(dates).isSorted();
        assertThat(dates.get(6)).isEqualTo(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        long todayBefore = ((Number) ((List<Map<String, Object>>) before.get("recent7Days")).get(6)
                .get("sessionCount")).longValue();
        assertThat(((Number) recent.get(6).get("sessionCount")).longValue()).isEqualTo(todayBefore + 1);
        for (Map<String, Object> day : recent) {
            assertThat(day.get("sessionCount")).isNotNull();
            Object dayAvg = day.get("avgScore");
            if (dayAvg != null) {
                assertThat(((Number) dayAvg).doubleValue()).isBetween(0.0, 100.0);
            }
        }
    }

    // ==================== #28★/#30★ 岗位与权重 ====================

    @Test
    @SuppressWarnings("unchecked")
    void positionMaintenanceAndWeights() throws Exception {
        String admin = adminToken();
        String code = "tp" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // 新增：2 技能、权重和=1
        Map<String, Object> createReq = Map.of(
                "code", code, "name", "测试岗-" + code, "description", "IT 用临时岗位",
                "defaultRounds", 5,
                "skills", List.of(
                        Map.of("skillCode", "skill_a", "skillName", "技能A", "description", "",
                                "weight", 0.6, "requiredLevel", 70),
                        Map.of("skillCode", "skill_b", "skillName", "技能B", "description", "",
                                "weight", 0.4, "requiredLevel", 60)));
        MvcResult created = mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(createReq)))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> createdData = (Map<String, Object>) body(created).get("data");
        assertThat(code(body(created))).isZero();
        long id = ((Number) createdData.get("id")).longValue();
        assertThat(((Number) createdData.get("skillCount")).intValue()).isEqualTo(2);
        assertThat(createdData.get("enabled")).isEqualTo(1);

        // 学生侧立即可见
        String token = registerAndLogin();
        MvcResult positions = mockMvc.perform(get("/api/positions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertThat((List<Map<String, Object>>) body(positions).get("data"))
                .anyMatch(p -> ((Number) p.get("id")).longValue() == id);

        // #30★ 权重调整：content 与 total 两组（组内和=1），expression 保留空
        Map<String, Object> weights = Map.of("scoreWeights", Map.of(
                "content", Map.of("technical", 0.40, "depth", 0.20, "logic", 0.20,
                        "evidence", 0.10, "match", 0.10),
                "total", Map.of("content", 0.60, "expression", 0.20,
                        "adaptability", 0.10, "match", 0.10)));
        MvcResult weighted = mockMvc.perform(put("/api/admin/positions/" + id + "/weights")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(weights)))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> weightsData = (Map<String, Object>) body(weighted).get("data");
        Map<String, Object> savedWeights = (Map<String, Object>) weightsData.get("scoreWeights");
        assertThat(((Number) ((Map<String, Object>) savedWeights.get("content")).get("technical"))
                .doubleValue()).isEqualTo(0.40);

        // 回读详情持久化正确
        MvcResult detail = mockMvc.perform(get("/api/admin/positions/" + id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> detailWeights =
                (Map<String, Object>) ((Map<String, Object>) body(detail).get("data")).get("scoreWeights");
        assertThat(((Number) ((Map<String, Object>) detailWeights.get("total")).get("content"))
                .doubleValue()).isEqualTo(0.60);

        // 权重和 ≠ 1 → 40001
        Map<String, Object> badWeights = Map.of("scoreWeights", Map.of(
                "content", Map.of("technical", 0.5, "depth", 0.1, "logic", 0.05,
                        "evidence", 0.03, "match", 0.02)));
        MvcResult bad = mockMvc.perform(put("/api/admin/positions/" + id + "/weights")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(badWeights)))
                .andReturn();
        assertThat(code(body(bad))).isEqualTo(40001);

        // 修改岗位：defaultRounds=6 → 生效
        Map<String, Object> updateReq = new HashMap<>();
        updateReq.put("name", "测试岗-改名");
        updateReq.put("defaultRounds", 6);
        MvcResult updated = mockMvc.perform(put("/api/admin/positions/" + id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(updateReq)))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> updatedData = (Map<String, Object>) body(updated).get("data");
        assertThat(((Number) updatedData.get("defaultRounds")).intValue()).isEqualTo(6);
        assertThat((String) updatedData.get("name")).isEqualTo("测试岗-改名");

        // 重复编码 / 技能权重和 ≠ 1 → 40001
        MvcResult dupCode = postPosition(admin, Map.of("code", code, "name", "dup",
                "skills", List.of(Map.of("skillCode", "x", "skillName", "X", "weight", 1.0))));
        assertThat(code(body(dupCode))).isEqualTo(40001);
        MvcResult badSkills = postPosition(admin, Map.of("code", code + "2", "name", "bad",
                "skills", List.of(
                        Map.of("skillCode", "x", "skillName", "X", "weight", 0.6),
                        Map.of("skillCode", "y", "skillName", "Y", "weight", 0.1))));
        assertThat(code(body(badSkills))).isEqualTo(40001);

        // 停用：学生侧不可见；该岗位不能再建面试（40401）
        mockMvc.perform(put("/api/admin/positions/" + id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("enabled", 0))))
                .andExpect(status().isOk());
        MvcResult afterDisable = mockMvc.perform(get("/api/positions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertThat((List<Map<String, Object>>) body(afterDisable).get("data"))
                .noneMatch(p -> ((Number) p.get("id")).longValue() == id);
        MvcResult interviewOnDisabled = mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("positionId", id, "mode", "TEXT",
                                "difficulty", 3, "rounds", 5, "durationMinutes", 15))))
                .andExpect(status().isOk()).andReturn();
        assertThat(code(body(interviewOnDisabled))).isEqualTo(40401);
    }

    // ==================== #29★ 学习资源 CRUD ====================

    @Test
    @SuppressWarnings("unchecked")
    void resourceCrudLifecycle() throws Exception {
        String admin = adminToken();

        // 新增（skillCode 对齐岗位 1 能力模型）
        Map<String, Object> createReq = Map.of(
                "positionId", 1, "skillCode", "spring",
                "title", "【IT】Spring 事务传播练习", "resourceType", "QUESTION",
                "difficulty", 3);
        MvcResult created = mockMvc.perform(post("/api/admin/resources")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(createReq)))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> data = (Map<String, Object>) body(created).get("data");
        assertThat(code(body(created))).isZero();
        long id = ((Number) data.get("id")).longValue();
        assertThat(data.get("enabled")).isEqualTo(1);

        // 列表过滤可见
        MvcResult list = mockMvc.perform(get("/api/admin/resources")
                        .queryParam("positionId", "1")
                        .queryParam("skillCode", "spring")
                        .queryParam("resourceType", "QUESTION")
                        .queryParam("size", "50")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> records = (List<Map<String, Object>>)
                ((Map<String, Object>) body(list).get("data")).get("records");
        assertThat(records).anyMatch(r -> ((Number) r.get("id")).longValue() == id);

        // 修改（全量替换）
        Map<String, Object> updateReq = new HashMap<>(createReq);
        updateReq.put("title", "【IT】Spring 事务传播练习（修订）");
        updateReq.put("difficulty", 4);
        MvcResult updated = mockMvc.perform(put("/api/admin/resources/" + id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(updateReq)))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> updatedData = (Map<String, Object>) body(updated).get("data");
        assertThat((String) updatedData.get("title")).contains("修订");
        assertThat(((Number) updatedData.get("difficulty")).intValue()).isEqualTo(4);

        // 非法入参：类型 / 技能未对齐模型 / 岗位不存在
        Map<String, Object> badType = new HashMap<>(createReq);
        badType.put("resourceType", "VIDEO");
        assertThat(code(body(postResource(admin, badType)))).isEqualTo(40001);
        Map<String, Object> badSkill = new HashMap<>(createReq);
        badSkill.put("skillCode", "not_in_model");
        assertThat(code(body(postResource(admin, badSkill)))).isEqualTo(40001);
        Map<String, Object> badPosition = new HashMap<>(createReq);
        badPosition.put("positionId", 999);
        assertThat(code(body(postResource(admin, badPosition)))).isEqualTo(40401);

        // 下架：enabled=0，列表仍可见（管理审计），推荐匹配排除由 enabled 过滤保证
        mockMvc.perform(delete("/api/admin/resources/" + id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        MvcResult afterDelete = mockMvc.perform(get("/api/admin/resources")
                        .queryParam("skillCode", "spring")
                        .queryParam("size", "100")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> left = (List<Map<String, Object>>)
                ((Map<String, Object>) body(afterDelete).get("data")).get("records");
        assertThat(left).anyMatch(r -> ((Number) r.get("id")).longValue() == id
                && ((Number) r.get("enabled")).intValue() == 0);
    }

    // ==================== #31★ 用户列表/启停 ====================

    @Test
    @SuppressWarnings("unchecked")
    void userListAndStatusToggle() throws Exception {
        String admin = adminToken();
        String uname = "stu" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        // 注册 → keyword 可检索，status=1
        register(uname);
        Map<String, Object> found = findUser(admin, uname);
        long uid = ((Number) found.get("id")).longValue();
        assertThat(((Number) found.get("status")).intValue()).isEqualTo(1);

        // 禁用 → 登录 10003；重新启用 → 登录成功
        mockMvc.perform(put("/api/admin/users/" + uid + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("status", 0))))
                .andExpect(status().isOk());
        assertThat(loginCode(uname)).isEqualTo(10003);
        mockMvc.perform(put("/api/admin/users/" + uid + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("status", 1))))
                .andExpect(status().isOk());
        assertThat(loginCode(uname)).isZero();

        // 非法 status / 不存在用户 / 不能禁用自己
        MvcResult badStatus = mockMvc.perform(put("/api/admin/users/" + uid + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("status", 2))))
                .andReturn();
        assertThat(code(body(badStatus))).isEqualTo(40001);
        MvcResult notFound = mockMvc.perform(put("/api/admin/users/999999/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("status", 0))))
                .andExpect(status().isOk()).andReturn();
        assertThat(code(body(notFound))).isEqualTo(40401);
        // 不能禁用当前登录的管理员（uid 从 token 解析，防锁死）
        long adminUid = ((Number) ((Map<String, Object>) body(
                mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + admin))
                        .andExpect(status().isOk()).andReturn()).get("data")).get("id")).longValue();
        MvcResult self = mockMvc.perform(put("/api/admin/users/" + adminUid + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("status", 0))))
                .andReturn();
        assertThat(code(body(self))).isEqualTo(40001);
    }

    // ==================== 工具 ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> dashboard(String admin) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        return (Map<String, Object>) body(res).get("data");
    }

    private MvcResult postPosition(String admin, Map<String, Object> req) throws Exception {
        // 校验失败走 HTTP 400 + code 40001（统一返回约定），此处只看响应体
        return mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(req)))
                .andReturn();
    }

    private MvcResult postResource(String admin, Map<String, Object> req) throws Exception {
        return mockMvc.perform(post("/api/admin/resources")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(req)))
                .andReturn();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findUser(String admin, String keyword) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/users")
                        .queryParam("keyword", keyword)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> records = (List<Map<String, Object>>)
                ((Map<String, Object>) body(res).get("data")).get("records");
        assertThat(records).hasSize(1);
        return records.get(0);
    }

    private void register(String uname) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))));
    }

    @SuppressWarnings("unchecked")
    private String registerAndLogin() throws Exception {
        String uname = "ae" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        register(uname);
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
                .andExpect(status().isOk()).andReturn();
        return (String) ((Map<String, Object>) body(login).get("data")).get("token");
    }

    private int loginCode(String uname) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
                .andExpect(status().isOk()).andReturn();
        return code(body(login));
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

    @SuppressWarnings("unchecked")
    private String adminToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("username", "admin", "password", "Admin@123456"))))
                .andExpect(status().isOk()).andReturn();
        return (String) ((Map<String, Object>) body(login).get("data")).get("token");
    }

    private long countSql(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
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
