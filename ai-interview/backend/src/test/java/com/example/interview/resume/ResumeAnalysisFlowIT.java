package com.example.interview.resume;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.common.util.JsonUtils;
import com.example.interview.resume.entity.Resume;
import com.example.interview.resume.mapper.ResumeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 简历计划 §8.2 ResumeAnalysisFlowIT（R2，协议 R-04/R-05）：上传 → 异步分析 → ANALYZED，
 * analysis 接口契约（六维/总分/建议 3~8 条风险分组排序/证据句）；ANALYZING 期 10006；
 * FAILED 按 fail_reason 前缀映射 10008（[PARSE]）/10007（[AI]）；reanalyze 覆盖旧结果
 * （delete+insert，新行替换）且 ANALYZING 中重复调用被 10006 挡住；PII 不入 profile/digest。
 * "进行中"瞬态（mock 分析毫秒级完成）用状态固定注入保证确定性。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.upload-dir=./target/test-uploads")
class ResumeAnalysisFlowIT {

    private static final Map<String, Integer> RISK_ORDER = Map.of("HIGH", 0, "MEDIUM", 1, "LOW", 2);
    private static final List<String> DIMENSIONS =
            List.of("basic", "education", "skill", "project", "experience", "expression");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ResumeMapper resumeMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== 工具 ====================

    private String registerAndLogin() throws Exception {
        String uname = "ra" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))));
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
                .andReturn();
        return token(login);
    }

    @SuppressWarnings("unchecked")
    private static String token(MvcResult login) throws Exception {
        Map<String, Object> body = body(login);
        return (String) ((Map<String, Object>) body.get("data")).get("token");
    }

    private long uploadMd(String token, long positionId) throws Exception {
        MvcResult res = mockMvc.perform(multipart("/api/resumes")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "简历.md",
                                "text/markdown", ResumeFixtures.CHINESE_RESUME_MD.getBytes(StandardCharsets.UTF_8)))
                        .param("positionId", String.valueOf(positionId))
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        Map<String, Object> body = body(res);
        assertThat(code(body)).as("上传响应 %s", body).isZero();
        return ((Number) ((Map<?, ?>) body.get("data")).get("id")).longValue();
    }

    private Map<String, Object> analysis(String token, long id) throws Exception {
        return body(mockMvc.perform(get("/api/resumes/" + id + "/analysis")
                .header("Authorization", "Bearer " + token)).andReturn());
    }

    private Map<String, Object> reanalyze(String token, long id) throws Exception {
        return body(mockMvc.perform(post("/api/resumes/" + id + "/reanalyze")
                .header("Authorization", "Bearer " + token)).andReturn());
    }

    /** 轮询详情至期望状态（mock 秒级），返回 data。 */
    private Map<String, Object> awaitStatus(String token, long id, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        Map<String, Object> d = Map.of();
        while (System.currentTimeMillis() < deadline) {
            d = data(mockMvc.perform(get("/api/resumes/" + id)
                    .header("Authorization", "Bearer " + token)).andReturn());
            if (expected.equals(d.get("status"))) {
                return d;
            }
            Thread.sleep(150);
        }
        throw new IllegalStateException("简历 " + id + " 未在 15s 内进入 " + expected + "（当前 " + d.get("status") + "）");
    }

    private void pinStatus(long id, String status, String failReason) {
        resumeMapper.update(null, Wrappers.<Resume>lambdaUpdate()
                .eq(Resume::getId, id)
                .set(Resume::getStatus, status)
                .set(failReason != null, Resume::getFailReason, failReason)
                .set(failReason == null, Resume::getFailReason, null));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(MvcResult res) throws Exception {
        return JsonUtils.parse(res.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(MvcResult res) throws Exception {
        return (Map<String, Object>) body(res).get("data");
    }

    private static int code(Map<String, Object> body) {
        return ((Number) body.get("code")).intValue();
    }

    // ==================== R-04：analysis 契约与 10006 ====================

    @Test
    void analysisContractAndNotReadyMapping() throws Exception {
        String alice = registerAndLogin();
        String bob = registerAndLogin();
        long id = uploadMd(alice, 1L);
        awaitStatus(alice, id, "ANALYZED");

        Map<String, Object> res = analysis(alice, id);
        assertThat(code(res)).isZero();
        @SuppressWarnings("unchecked")
        Map<String, Object> d = (Map<String, Object>) res.get("data");
        assertThat(((Number) d.get("resumeId")).longValue()).isEqualTo(id);
        assertThat(d.get("provider")).isEqualTo("mock");
        assertThat(((Number) d.get("overallScore")).intValue()).isBetween(0, 100);

        @SuppressWarnings("unchecked")
        Map<String, Object> dims = (Map<String, Object>) d.get("dimensionScores");
        assertThat(dims).containsOnlyKeys(DIMENSIONS);
        dims.forEach((dim, v) -> assertThat((Number) v).as(dim).isNotNull());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) d.get("suggestions");
        assertThat(suggestions).hasSizeBetween(3, 8);
        List<Integer> levels = suggestions.stream()
                .map(s -> RISK_ORDER.get((String) s.get("riskLevel"))).toList();
        assertThat(levels).as("HIGH→MEDIUM→LOW 排序").isSorted();
        for (Map<String, Object> s : suggestions) {
            assertThat((String) s.get("riskLevel")).isIn("HIGH", "MEDIUM", "LOW");
            assertThat((String) s.get("dimension")).isIn(DIMENSIONS);
            assertThat((String) s.get("title")).isNotBlank();
            assertThat((String) s.get("issue")).isNotBlank();
            assertThat((String) s.get("suggestion")).isNotBlank();
        }
        assertThat((List<?>) d.get("strengths")).isNotEmpty();
        assertThat((String) d.get("summary")).isNotBlank();

        // ANALYZING 期 → 10006（状态固定注入，确定性）
        pinStatus(id, "ANALYZING", null);
        Map<String, Object> notReady = analysis(alice, id);
        assertThat(code(notReady)).isEqualTo(10006);
        assertThat((String) notReady.get("message")).contains("进行中");
        pinStatus(id, "ANALYZED", null);

        // 他人/不存在 → 40401
        assertThat(code(analysis(bob, id))).isEqualTo(40401);
        assertThat(code(analysis(alice, 999_999L))).isEqualTo(40401);
    }

    // ==================== PII：脱敏内容不落 profile/digest ====================

    @Test
    void maskedTextStoredWithoutRawPii() throws Exception {
        String token = registerAndLogin();
        long id = uploadMd(token, 1L);
        awaitStatus(token, id, "ANALYZED");

        Map<String, Object> raw = jdbcTemplate.queryForMap(
                "SELECT profile_json, digest_json FROM resume WHERE id = ?", id);
        for (String column : List.of("profile_json", "digest_json")) {
            // 契约红线：结构化 JSON 只存脱敏后内容且不含联系方式字段（原文手机/邮箱零残留）
            String json = String.valueOf(raw.get(column));
            assertThat(json).as(column).doesNotContain("13812345678", "zhangsan@example.com");
        }
    }

    // ==================== R-05：覆盖重分析与并发挡板 ====================

    @Test
    void reanalyzeOverwritesResultAndGuardsConcurrentCalls() throws Exception {
        String token = registerAndLogin();
        long id = uploadMd(token, 1L);
        awaitStatus(token, id, "ANALYZED");

        Map<String, Object> oldRow = jdbcTemplate.queryForMap(
                "SELECT id FROM resume_analysis WHERE resume_id = ?", id);

        // ANALYZING 中重复调用 → 10006（不并发重分析）
        pinStatus(id, "ANALYZING", null);
        Map<String, Object> concurrent = reanalyze(token, id);
        assertThat(code(concurrent)).isEqualTo(10006);
        pinStatus(id, "ANALYZED", null);

        // 正常重分析：ANALYZING → 异步覆盖 → 新行替换旧行（resume_id 唯一）
        Map<String, Object> again = reanalyze(token, id);
        assertThat(code(again)).isZero();
        @SuppressWarnings("unchecked")
        Map<String, Object> againData = (Map<String, Object>) again.get("data");
        assertThat(againData.get("status")).isEqualTo("ANALYZING");
        awaitStatus(token, id, "ANALYZED");

        Map<String, Object> newRow = jdbcTemplate.queryForMap(
                "SELECT id, provider, overall_score FROM resume_analysis WHERE resume_id = ?", id);
        assertThat(((Number) newRow.get("id")).longValue())
                .as("delete + insert 覆盖旧结果")
                .isNotEqualTo(((Number) oldRow.get("id")).longValue());
        assertThat(((Number) jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM resume_analysis WHERE resume_id = ?", Long.class, id)).longValue())
                .isEqualTo(1);
        assertThat(String.valueOf(newRow.get("provider"))).isEqualTo("mock");
        assertThat(((Number) newRow.get("overall_score")).intValue()).isBetween(0, 100);
        // 覆盖后 digest 仍在（F2 契约字段随最新分析刷新）
        Map<String, Object> detail = awaitStatus(token, id, "ANALYZED");
        assertThat(detail).containsKey("digest");
    }

    // ==================== FAILED 映射与恢复 ====================

    @Test
    void parseFailedMaps10008AiFailedMaps10007AndBothRecoverable() throws Exception {
        String token = registerAndLogin();

        // [PARSE]：扫描件 → FAILED → analysis 10008；reanalyze 重抽仍失败
        MvcResult scan = mockMvc.perform(multipart("/api/resumes")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "扫描件.pdf",
                                "application/pdf", ResumeFixtures.blankPdf()))
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        long scanId = ((Number) data(scan).get("id")).longValue();
        awaitStatus(token, scanId, "FAILED");

        Map<String, Object> parseFailed = analysis(token, scanId);
        assertThat(code(parseFailed)).isEqualTo(10008);
        assertThat((String) parseFailed.get("message")).contains("未检测到可分析文本");

        Map<String, Object> retryScan = reanalyze(token, scanId);
        assertThat(code(retryScan)).isZero();
        awaitStatus(token, scanId, "FAILED");       // 文件本身无文本层，重抽仍失败
        assertThat(code(analysis(token, scanId))).isEqualTo(10008);

        // [AI]：分析失败态 → 10007；reanalyze 恢复成功
        long mdId = uploadMd(token, 1L);
        awaitStatus(token, mdId, "ANALYZED");
        pinStatus(mdId, "FAILED", "[AI] 模拟分析失败");
        Map<String, Object> aiFailed = analysis(token, mdId);
        assertThat(code(aiFailed)).isEqualTo(10007);
        assertThat((String) aiFailed.get("message")).contains("模拟分析失败");

        Map<String, Object> recovered = reanalyze(token, mdId);
        assertThat(code(recovered)).isZero();
        Map<String, Object> detail = awaitStatus(token, mdId, "ANALYZED");
        assertThat(detail).doesNotContainKey("failReason");
        assertThat(code(analysis(token, mdId))).isZero();
    }
}
