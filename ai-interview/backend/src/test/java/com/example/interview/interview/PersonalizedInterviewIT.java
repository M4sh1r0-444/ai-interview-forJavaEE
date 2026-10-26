package com.example.interview.interview;

import com.example.interview.ai.model.AiNextQuestionResult;
import com.example.interview.ai.model.AiQuestionPersonalization;
import com.example.interview.ai.model.AiReport;
import com.example.interview.ai.model.AiResumeAnalysis;
import com.example.interview.ai.model.AiTurnEvaluation;
import com.example.interview.ai.model.NextQuestionContext;
import com.example.interview.ai.model.QuestionPersonalizeContext;
import com.example.interview.ai.model.ReportContext;
import com.example.interview.ai.model.ResumeAnalysisContext;
import com.example.interview.ai.model.TurnEvaluationContext;
import com.example.interview.ai.provider.AiProvider;
import com.example.interview.ai.provider.MockAiProvider;
import com.example.interview.common.util.JsonUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.resume.ResumeFixtures;
import com.example.interview.resume.entity.Resume;
import com.example.interview.resume.mapper.ResumeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 简历计划 §8.2 PersonalizedInterviewIT（R3，协议 R-08/R-09/R-10）：
 * focus-skills 命中/缺口/零命中/无岗位；带 resumeId 创建（40401/10009 分支、data 新字段）、
 * 详情三新字段；start 首题含项目名改写且 questionId 保留；改写降级（Provider 抛错）面试
 * 仍正常（整场原题干）；跑完 5 轮报告含"简历中的…"对比条目；不带 resumeId 全链路回归。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.upload-dir=./target/test-uploads")
class PersonalizedInterviewIT {

    /** 无技能词的运营类简历（≥200 字符，供零命中分支；不含词典/岗位技能名） */
    private static final String NON_TECH_RESUME = """
            求职意向：市场营销专员
            2022.09 - 2026.06 某某大学 工商管理专业 本科，主修市场营销与消费者行为课程
            校园经历：担任学生社团宣传负责人，策划并执行十余场线下活动，累计参与人次三千以上；
            撰写活动宣传文案与推文八十余篇，运营社群成员两百余人，活动报名转化率提升明显；
            协调五个部门分工推进活动落地，负责供应商对接与预算核对，账目清晰无差错；
            曾获校级优秀学生干部称号，组织能力与文案功底得到师生认可，善于跨部门沟通协作。
            自我评价：做事有条理，能够独立完成从选题策划到复盘总结的完整闭环，抗压能力强。
            """;

    /** 主问题改写前缀标记（Mock 模板：项目名 + 技术栈） */
    private static final String PERSONALIZE_PREFIX = "结合你在《校园二手交易平台";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ResumeMapper resumeMapper;

    /** 可切换抛错的 Provider 桩：默认全量委托 Mock，降级用例打开 personalizeQuestions 抛错开关 */
    static volatile boolean throwPersonalize = false;

    @TestConfiguration
    static class SwitchableProviderConfig {
        @Bean
        @Primary
        AiProvider switchableProvider() {
            return new SwitchableProvider();
        }
    }

    static class SwitchableProvider implements AiProvider {
        private final MockAiProvider delegate = new MockAiProvider();

        @Override
        public AiNextQuestionResult nextQuestion(NextQuestionContext ctx) {
            return delegate.nextQuestion(ctx);
        }

        @Override
        public AiTurnEvaluation evaluateTurn(TurnEvaluationContext ctx) {
            return delegate.evaluateTurn(ctx);
        }

        @Override
        public AiReport generateReport(ReportContext ctx) {
            return delegate.generateReport(ctx);
        }

        @Override
        public AiResumeAnalysis analyzeResume(ResumeAnalysisContext ctx) {
            return delegate.analyzeResume(ctx);
        }

        @Override
        public AiQuestionPersonalization personalizeQuestions(QuestionPersonalizeContext ctx) {
            if (throwPersonalize) {
                throw new IllegalStateException("personalize provider down (测试注入)");
            }
            return delegate.personalizeQuestions(ctx);
        }
    }

    // ==================== 工具 ====================

    private String registerAndLogin() throws Exception {
        String uname = "pi" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))));
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body(login).get("data");
        return (String) data.get("token");
    }

    private long uploadResume(String token, String filename, String content, Long positionId) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/resumes")
                .file(new MockMultipartFile("file", filename, "text/markdown",
                        content.getBytes(StandardCharsets.UTF_8)));
        req.header("Authorization", "Bearer " + token);
        if (positionId != null) {
            req.param("positionId", String.valueOf(positionId));
        }
        MvcResult res = mockMvc.perform(req).andReturn();
        assertThat(code(body(res))).as("上传 %s：{}", filename, body(res).get("message")).isZero();
        return ((Number) data(res).get("id")).longValue();
    }

    /** 轮询详情至 ANALYZED（mock 分析秒级，上限 15s）。 */
    private void awaitAnalyzed(String token, long resumeId) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        String status = null;
        while (System.currentTimeMillis() < deadline) {
            MvcResult res = mockMvc.perform(get("/api/resumes/" + resumeId)
                    .header("Authorization", "Bearer " + token)).andReturn();
            status = (String) data(res).get("status");
            if ("ANALYZED".equals(status)) {
                return;
            }
            Thread.sleep(150);
        }
        throw new IllegalStateException("简历 " + resumeId + " 15s 内未 ANALYZED，当前 " + status);
    }

    private Map<String, Object> create(String token, Map<String, Object> overrides) throws Exception {
        Map<String, Object> req = new HashMap<>(Map.of(
                "positionId", 1, "mode", "TEXT", "difficulty", 3,
                "rounds", 5, "durationMinutes", 15));
        req.putAll(overrides);
        MvcResult res = mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(req)))
                .andExpect(status().isOk()).andReturn();
        return body(res);
    }

    private long createOk(String token, Map<String, Object> overrides) throws Exception {
        Map<String, Object> resp = create(token, overrides);
        assertThat(code(resp)).isZero();
        return ((Number) ((Map<String, Object>) resp.get("data")).get("id")).longValue();
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
        assertThat(code(body(res))).isZero();
        return data(res);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> messages(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/interviews/" + id + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return (List<Map<String, Object>>) ((Map<String, Object>) body(res)).get("data");
    }

    private Map<String, Object> focusSkills(String token, long id, Long positionId) throws Exception {
        String url = "/api/resumes/" + id + "/focus-skills"
                + (positionId != null ? "?positionId=" + positionId : "");
        MvcResult res = mockMvc.perform(get(url).header("Authorization", "Bearer " + token)).andReturn();
        return body(res);
    }

    private void pinResumeStatus(long id, String status) {
        resumeMapper.update(null, Wrappers.<Resume>lambdaUpdate()
                .eq(Resume::getId, id)
                .set(Resume::getStatus, status));
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
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(MvcResult result) throws Exception {
        return (Map<String, Object>) body(result).get("data");
    }

    private static int code(Map<String, Object> body) {
        return ((Number) body.get("code")).intValue();
    }

    // ==================== R-08：focus-skills ====================

    @Test
    void focusSkillsMatchGapAndZeroHit() throws Exception {
        String token = registerAndLogin();
        long resumeId = uploadResume(token, "简历.md", ResumeFixtures.CHINESE_RESUME_MD, 1L);
        awaitAnalyzed(token, resumeId);

        // 命中/缺口/占比结构（协议 R-08）
        Map<String, Object> resp = focusSkills(token, resumeId, null);
        assertThat(code(resp)).isZero();
        Map<String, Object> d = (Map<String, Object>) resp.get("data");
        assertThat(d.get("resumeId")).isEqualTo((int) resumeId);
        assertThat(d.get("positionId")).isEqualTo(1);
        assertThat((String) d.get("positionName")).contains("Java");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matched = (List<Map<String, Object>>) d.get("matched");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gaps = (List<Map<String, Object>>) d.get("gaps");
        assertThat(matched).isNotEmpty();
        assertThat(matched.stream().map(m -> m.get("skillCode")).toList())
                .contains("java_basics", "spring", "mysql", "redis_mq");
        assertThat(matched.stream().allMatch(m -> ((List<?>) m.get("matchedTerms")).size() >= 1)).isTrue();
        assertThat(gaps).extracting(g -> g.get("skillCode")).contains("project_design", "communication");
        double ratio = ((Number) d.get("hitRatio")).doubleValue();
        assertThat(ratio).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);

        // 换岗位（前端）：仅 Vue3 命中，其余为缺口——技能匹配按面试岗位能力模型
        Map<String, Object> fe = focusSkills(token, resumeId, 2L);
        assertThat(code(fe)).isZero();
        Map<String, Object> feData = (Map<String, Object>) fe.get("data");
        assertThat(feData.get("positionId")).isEqualTo(2);
        assertThat((List<Map<String, Object>>) feData.get("matched")).extracting(m -> m.get("skillCode"))
                .containsExactly("vue3");
        assertThat((List<Map<String, Object>>) feData.get("gaps")).hasSize(6);

        // 零命中：无技能词简历 → matched 空、gaps 全量、hitRatio=0（按通用策略组卷）
        long plainId = uploadResume(token, "运营.md", NON_TECH_RESUME, 1L);
        awaitAnalyzed(token, plainId);
        Map<String, Object> zero = focusSkills(token, plainId, null);
        assertThat(code(zero)).isZero();
        Map<String, Object> zeroData = (Map<String, Object>) zero.get("data");
        assertThat((List<?>) zeroData.get("matched")).isEmpty();
        assertThat((List<?>) zeroData.get("gaps")).hasSize(7);
        assertThat(((Number) zeroData.get("hitRatio")).doubleValue()).isZero();

        // 无岗位可用：未绑定岗位且不带 positionId → 400/40001
        long noPosId = uploadResume(token, "无岗位.md", NON_TECH_RESUME, null);
        awaitAnalyzed(token, noPosId);
        MvcResult noPos = mockMvc.perform(get("/api/resumes/" + noPosId + "/focus-skills")
                        .header("Authorization", "Bearer " + token)).andReturn();
        assertThat(noPos.getResponse().getStatus()).isEqualTo(400);
        assertThat(code(body(noPos))).isEqualTo(40001);

        // 他人简历 → 40401
        String other = registerAndLogin();
        assertThat(code(focusSkills(other, resumeId, null))).isEqualTo(40401);
    }

    // ==================== R-09/R-10：创建校验链与详情新字段 ====================

    @Test
    void createValidationChainAndDetailFields() throws Exception {
        String token = registerAndLogin();
        long resumeId = uploadResume(token, "简历.md", ResumeFixtures.CHINESE_RESUME_MD, 1L);
        awaitAnalyzed(token, resumeId);

        // 他人简历 → 40401（校验顺序：归属先于状态）
        String bob = registerAndLogin();
        long bobResume = uploadResume(bob, "bob.md", ResumeFixtures.CHINESE_RESUME_MD, 1L);
        awaitAnalyzed(bob, bobResume);
        assertThat(code(create(token, Map.of("resumeId", bobResume)))).isEqualTo(40401);

        // 状态非 ANALYZED → 10009（固定注入 ANALYZING）
        pinResumeStatus(resumeId, "ANALYZING");
        assertThat(code(create(token, Map.of("resumeId", resumeId)))).isEqualTo(10009);
        pinResumeStatus(resumeId, "ANALYZED");

        // 不存在的 resumeId → 40401
        assertThat(code(create(token, Map.of("resumeId", 999_999L)))).isEqualTo(40401);

        // 创建成功：data 含 resumeId/personalized（R-09 增量字段）
        Map<String, Object> created = create(token, Map.of("resumeId", resumeId));
        assertThat(code(created)).isZero();
        Map<String, Object> createdData = (Map<String, Object>) created.get("data");
        assertThat(((Number) createdData.get("id")).longValue()).isPositive();
        assertThat(((Number) createdData.get("resumeId")).longValue()).isEqualTo(resumeId);
        assertThat(createdData.get("personalized")).isEqualTo(true);

        // 详情三新字段（R-10）
        long sid = ((Number) createdData.get("id")).longValue();
        Map<String, Object> d = detail(token, sid);
        assertThat(((Number) d.get("resumeId")).longValue()).isEqualTo(resumeId);
        assertThat(d.get("resumeFileName")).isEqualTo("简历.md");
        assertThat(d.get("personalized")).isEqualTo(true);

        // 普通面试回归：不带 resumeId → personalized=false，resumeId 缺席，start 正常
        long plainSid = createOk(token, Map.of());
        Map<String, Object> plainDetail = detail(token, plainSid);
        assertThat(plainDetail.get("personalized")).isEqualTo(false);
        assertThat(plainDetail).doesNotContainKey("resumeId").doesNotContainKey("resumeFileName");
        assertThat(code(start(token, plainSid))).isZero();
    }

    // ==================== R3-3：start 改写与 questionId 保留 ====================

    @Test
    void startPersonalizesStemsAndKeepsQuestionIds() throws Exception {
        String token = registerAndLogin();
        long resumeId = uploadResume(token, "简历.md", ResumeFixtures.CHINESE_RESUME_MD, 1L);
        awaitAnalyzed(token, resumeId);
        long sid = createOk(token, Map.of("resumeId", resumeId));

        // start 返回首题：含项目名改写前缀（Mock 模板：项目 + 技术栈前2）
        Map<String, Object> startBody = start(token, sid);
        assertThat(code(startBody)).isZero();
        Map<String, Object> first = (Map<String, Object>) startBody.get("data");
        assertThat((String) first.get("contentText")).contains(PERSONALIZE_PREFIX).contains("Spring Boot");
        assertThat(first.get("questionId")).isNotNull();

        // 学生视角：未问到的主问题不暴露（既有语义不变），首题 questionId 指向题库原题
        List<Map<String, Object>> visible = messages(token, sid);
        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).get("questionId")).isNotNull();

        // ADMIN 视角可见全部主问题：均含改写前缀、questionId 非空
        String adminToken = adminLogin();
        List<Map<String, Object>> adminView = messages(adminToken, sid);
        List<Map<String, Object>> mainQuestions = adminView.stream()
                .filter(m -> "AI".equals(m.get("speaker"))
                        && ((Number) m.get("followupIndex")).intValue() == 0).toList();
        assertThat(mainQuestions).hasSize(5);
        assertThat(mainQuestions).allSatisfy(m -> {
            assertThat((String) m.get("contentText")).contains(PERSONALIZE_PREFIX);
            assertThat(m.get("questionId")).isNotNull();
        });
    }

    // ==================== R3-3：改写失败降级整场原题干 ====================

    @Test
    void personalizeFailureDegradesToOriginalStems() throws Exception {
        String token = registerAndLogin();
        long resumeId = uploadResume(token, "简历.md", ResumeFixtures.CHINESE_RESUME_MD, 1L);
        awaitAnalyzed(token, resumeId);
        long sid = createOk(token, Map.of("resumeId", resumeId));

        throwPersonalize = true;
        try {
            // Provider 抛错 → start 不报错，整场使用原题干（无改写前缀），questionId 照旧
            Map<String, Object> startBody = start(token, sid);
            assertThat(code(startBody)).isZero();
            Map<String, Object> first = (Map<String, Object>) startBody.get("data");
            assertThat((String) first.get("contentText")).doesNotContain(PERSONALIZE_PREFIX);
            assertThat(first.get("questionId")).isNotNull();
            assertThat(detail(token, sid).get("status")).isEqualTo("WAITING_USER");

            // 作答链路不受影响（TurnEngine 正常驱动）
            assertThat(code(submitText(token, sid, 0,
                    "缓存穿透：请求不存在的 key 全部打到数据库，用空值缓存与布隆过滤器兜底，QPS 下降 40%")))
                    .isZero();
            long finalSid = sid;
            awaitUntil("降级后追问/换题仍产出", () -> {
                try {
                    long aiCount = messages(token, finalSid).stream()
                            .filter(m -> "AI".equals(m.get("speaker"))
                                    && ((Number) m.get("roundNo")).intValue() == 0).count();
                    return aiCount >= 2;
                } catch (Exception e) {
                    return false;
                }
            });
        } finally {
            throwPersonalize = false;
        }
    }

    // ==================== R3-5：报告"简历 vs 实测"对比条目 ====================

    @Test
    void personalizedReportContainsResumeComparison() throws Exception {
        String token = registerAndLogin();
        long resumeId = uploadResume(token, "简历.md", ResumeFixtures.CHINESE_RESUME_MD, 1L);
        awaitAnalyzed(token, resumeId);
        long sid = createOk(token, Map.of("resumeId", resumeId));
        start(token, sid);

        // 全程模糊回答：相关题目内容分 <60 → weaknesses 出现"简历中自述掌握…"条目；
        // 每轮 4 答（R2 模糊追问 ×3 后 R7 强制换题），与 InterviewTextFlowIT 同节奏
        for (int round = 0; round <= 4; round++) {
            driveRoundUntilAdvanced(token, sid, round);
        }
        awaitUntil("FINISHED", () -> {
            try {
                return "FINISHED".equals(detail(token, sid).get("status"));
            } catch (Exception e) {
                return false;
            }
        });

        // 报告就绪后断言对比条目（前缀"简历中自述掌握"，前端按前缀渲染）
        awaitUntil("报告生成", () -> {
            try {
                MvcResult res = mockMvc.perform(get("/api/interviews/" + sid + "/report")
                        .header("Authorization", "Bearer " + token)).andReturn();
                return code(body(res)) == 0;
            } catch (Exception e) {
                return false;
            }
        });
        MvcResult reportRes = mockMvc.perform(get("/api/interviews/" + sid + "/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> report = data(reportRes);
        @SuppressWarnings("unchecked")
        List<String> weaknesses = (List<String>) report.get("weaknesses");
        @SuppressWarnings("unchecked")
        List<String> strengths = (List<String>) report.get("strengths");
        assertThat(weaknesses).anyMatch(w -> w.startsWith("简历中自述掌握") && w.contains("建议重点巩固"));
        assertThat(strengths).allMatch(s -> !s.startsWith("简历中自述掌握")); // 低分场无强项条目

        // 项目深挖追问注入（R3-4）：PROJECT 轮的追问（followupIndex>0）引用项目名
        List<Map<String, Object>> msgs = messages(token, sid);
        assertThat(msgs.stream()
                .filter(m -> "AI".equals(m.get("speaker"))
                        && ((Number) m.get("followupIndex")).intValue() > 0)
                .map(m -> (String) m.get("contentText")))
                .anyMatch(c -> c.contains("《校园二手交易平台"));
    }

    /** 驱动一轮直至换题（模糊回答 ×4：R2 追问 ×3 后 R7 强制换题；递增等待，口径同 InterviewTextFlowIT）。 */
    private void driveRoundUntilAdvanced(String token, long id, int round) throws Exception {
        for (int k = 0; k <= 4 && ((Number) detail(token, id).get("currentRound")).intValue() == round; k++) {
            long expected = aiCountInRound(token, id, round) + 1;
            assertThat(code(submitText(token, id, round, "不太清楚"))).isZero();
            int finalRound = round;
            awaitUntil("轮 " + round + " 第 " + k + " 答产出", () -> {
                try {
                    return ((Number) detail(token, id).get("currentRound")).intValue() > finalRound
                            || aiCountInRound(token, id, finalRound) >= expected;
                } catch (Exception e) {
                    return false;
                }
            });
        }
        assertThat(((Number) detail(token, id).get("currentRound")).intValue())
                .as("轮 %d 应已推进", round).isGreaterThan(round);
    }

    private long aiCountInRound(String token, long id, int roundNo) throws Exception {
        return messages(token, id).stream()
                .filter(m -> "AI".equals(m.get("speaker")) && ((Number) m.get("roundNo")).intValue() == roundNo)
                .count();
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
