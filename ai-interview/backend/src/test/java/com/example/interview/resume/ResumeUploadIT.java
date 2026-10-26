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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 简历计划 §8.2 ResumeUploadIT（R1 范围 + R2 异步分析接通，协议 R-01/02/03/06/07）：
 * 四格式上传同步解析 → ANALYZING → 异步分析（mock 秒级）→ 轮询至 ANALYZED → 详情含
 * analysis/digest；扫描件/加密/短文本 → FAILED + failReason（无异步触发）；
 * .doc / Content-Type / 缺 file / 非法岗位 → 400/40001；列表分页倒序、用户隔离与
 * overallScore（ANALYZED 后）；详情归属 40401；逻辑删除后不可见；默认简历仅 ANALYZED
 * 可设且互斥；每日配额 10010 且删除释放额度。
 * R2 起上传即异步分析，"进行中"瞬态不再稳定可断言——一律轮询到终态后断言；
 * ANALYZING 分支语义由 ResumeAnalysisFlowIT 以状态固定方式覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.upload-dir=./target/test-uploads")
class ResumeUploadIT {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ResumeMapper resumeMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== 工具 ====================

    private String registerAndLogin() throws Exception {
        String uname = "rs" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))));
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
                .andExpect(status().isOk()).andReturn();
        return (String) data(login).get("token");
    }

    private MvcResult upload(String token, String filename, String contentType, byte[] bytes,
                             Long positionId) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/resumes")
                .file(new MockMultipartFile("file", filename, contentType, bytes));
        req.header("Authorization", "Bearer " + token);
        if (positionId != null) {
            req.param("positionId", String.valueOf(positionId));
        }
        return mockMvc.perform(req).andReturn();
    }

    private Map<String, Object> uploadOk(String token, String filename, String contentType,
                                         byte[] bytes, Long positionId) throws Exception {
        MvcResult res = upload(token, filename, contentType, bytes, positionId);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> body = body(res);
        assertThat(code(body)).as("upload %s", filename).isZero();
        return data(res);
    }

    private long uploadMd(String token) throws Exception {
        Map<String, Object> d = uploadOk(token, "简历.md", "text/markdown",
                ResumeFixtures.CHINESE_RESUME_MD.getBytes(StandardCharsets.UTF_8), 1L);
        return ((Number) d.get("id")).longValue();
    }

    /** 轮询详情至期望状态（mock 分析秒级完成；上限 15s 防环境抖动），返回 data。 */
    private Map<String, Object> awaitStatus(String token, long id, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        Map<String, Object> d = null;
        while (System.currentTimeMillis() < deadline) {
            MvcResult res = mockMvc.perform(get("/api/resumes/" + id)
                    .header("Authorization", "Bearer " + token)).andReturn();
            assertThat(code(body(res))).isZero();
            d = data(res);
            if (expected.equals(d.get("status"))) {
                return d;
            }
            Thread.sleep(150);
        }
        assertThat(d).as("简历 %s 未在 15s 内进入 %s（当前 %s）", id, expected, d.get("status")).isNotNull();
        throw new IllegalStateException("unreachable");
    }

    /** 直接改库固定状态（ANALYZING 语义分支的确定性注入，用毕还原）。 */
    private void pinStatus(long id, String status) {
        resumeMapper.update(null, Wrappers.<Resume>lambdaUpdate()
                .eq(Resume::getId, id)
                .set(Resume::getStatus, status));
    }

    private Map<String, Object> list(String token, int page, int size) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/resumes?page=" + page + "&size=" + size)
                .header("Authorization", "Bearer " + token)).andReturn();
        return body(res);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> records(String token) throws Exception {
        Map<String, Object> body = list(token, 1, 50);
        assertThat(code(body)).isZero();
        return (List<Map<String, Object>>) ((Map<String, Object>) body.get("data")).get("records");
    }

    private Map<String, Object> detail(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/resumes/" + id)
                .header("Authorization", "Bearer " + token)).andReturn();
        return body(res);
    }

    private Map<String, Object> setActive(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(put("/api/resumes/" + id + "/active")
                .header("Authorization", "Bearer " + token)).andReturn();
        return body(res);
    }

    private Map<String, Object> remove(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(delete("/api/resumes/" + id)
                .header("Authorization", "Bearer " + token)).andReturn();
        return body(res);
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

    // ==================== R-01：四格式上传 → 异步分析 → ANALYZED ====================

    @Test
    void uploadFourFormatsThenAnalyzedWithAnalysisAndDigest() throws Exception {
        String token = registerAndLogin();
        byte[] gbkTxt = ResumeFixtures.CHINESE_RESUME_MD.getBytes(Charset.forName("GBK"));

        Map<String, Object> md = uploadOk(token, "张三-简历.md", "text/markdown",
                ResumeFixtures.CHINESE_RESUME_MD.getBytes(StandardCharsets.UTF_8), 1L);
        Map<String, Object> txt = uploadOk(token, "resume.txt", "application/octet-stream", gbkTxt, null);
        Map<String, Object> docx = uploadOk(token, "resume.docx", DOCX_MIME, ResumeFixtures.chineseDocx(), 1L);
        Map<String, Object> pdf = uploadOk(token, "john.pdf", "application/pdf", ResumeFixtures.englishPdf(), 2L);

        for (Map<String, Object> d : List.of(md, txt, docx, pdf)) {
            // 上传响应为 ANALYZING（解析成功）或异步已完成 ANALYZED（mock 秒级，竞态合法）
            assertThat(d.get("status")).as("%s", d.get("fileName")).isIn("ANALYZING", "ANALYZED");
            assertThat(((Number) d.get("charCount")).intValue()).isGreaterThanOrEqualTo(200);
            assertThat(((Number) d.get("id")).longValue()).isPositive();
            assertThat(((Number) d.get("fileSize")).longValue()).isPositive();
            assertThat(d).doesNotContainKey("failReason");
            assertThat(d.get("createdAt")).isNotNull();
        }
        assertThat(md.get("fileType")).isEqualTo("MD");
        assertThat(md.get("fileName")).isEqualTo("张三-简历.md");
        assertThat(md.get("positionId")).isEqualTo(1);
        assertThat(txt.get("fileType")).isEqualTo("TXT");
        assertThat(txt).doesNotContainKey("positionId");           // 通用分析
        assertThat(docx.get("fileType")).isEqualTo("DOCX");
        assertThat(pdf.get("fileType")).isEqualTo("PDF");
        assertThat(pdf.get("positionId")).isEqualTo(2);

        // R2 异步分析：四格式全部轮询至 ANALYZED，详情含 analysis/digest（协议 R-03）
        for (Map<String, Object> d : List.of(md, txt, docx, pdf)) {
            long id = ((Number) d.get("id")).longValue();
            Map<String, Object> detail = awaitStatus(token, id, "ANALYZED");
            assertThat(detail).containsKeys("analysis", "digest");
            @SuppressWarnings("unchecked")
            Map<String, Object> analysis = (Map<String, Object>) detail.get("analysis");
            assertThat(((Number) analysis.get("overallScore")).intValue()).isBetween(0, 100);
            assertThat(analysis.get("dimensionScores")).isNotNull();
            assertThat((List<?>) analysis.get("suggestions")).hasSizeBetween(3, 8);
        }

        // 文件 UUID 落盘 target/test-uploads/resume/yyyyMM/，四种扩展名各至少一个
        Path dir = Path.of("target/test-uploads/resume",
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")));
        assertThat(dir).isDirectory();
        try (Stream<Path> files = Files.list(dir)) {
            List<String> names = files.map(p -> p.getFileName().toString()).toList();
            for (String ext : List.of(".md", ".txt", ".docx", ".pdf")) {
                assertThat(names).anyMatch(n -> n.matches("[0-9a-f]{32}\\" + ext));
            }
        }
    }

    @Test
    void parseFailuresReturnCodeZeroWithFailedStatus() throws Exception {
        String token = registerAndLogin();

        Map<String, Object> scan = uploadOk(token, "扫描件.pdf", "application/pdf", ResumeFixtures.blankPdf(), 1L);
        assertThat(scan.get("status")).isEqualTo("FAILED");
        assertThat((String) scan.get("failReason")).startsWith("[PARSE]").contains("未检测到可分析文本");
        assertThat(scan.get("charCount")).isEqualTo(0);

        Map<String, Object> enc = uploadOk(token, "加密.pdf", "application/pdf", ResumeFixtures.encryptedPdf(), null);
        assertThat(enc.get("status")).isEqualTo("FAILED");
        assertThat((String) enc.get("failReason")).contains("加密");

        Map<String, Object> shortTxt = uploadOk(token, "short.txt", "text/plain",
                "张三 Java 后端".getBytes(StandardCharsets.UTF_8), null);
        assertThat(shortTxt.get("status")).isEqualTo("FAILED");

        Map<String, Object> fake = uploadOk(token, "fake.pdf", "application/pdf", ResumeFixtures.chineseDocx(), null);
        assertThat(fake.get("status")).isEqualTo("FAILED");
        assertThat((String) fake.get("failReason")).contains("不符");

        // 失败态详情：failReason 透出，无 analysis/digest/profile（FAILED 不触发异步）
        long id = ((Number) scan.get("id")).longValue();
        Map<String, Object> detail = detail(token, id);
        assertThat(code(detail)).isZero();
        @SuppressWarnings("unchecked")
        Map<String, Object> d = (Map<String, Object>) detail.get("data");
        assertThat(d.get("status")).isEqualTo("FAILED");
        assertThat(d).containsKey("failReason").doesNotContainKeys("analysis", "digest", "profile");

        // 失败记录同样占列表位置（用户可见并可重传/重试）
        assertThat(records(token)).extracting(r -> r.get("status"))
                .containsOnly("FAILED");
    }

    @Test
    void invalidRequestsRejectedWith40001() throws Exception {
        String token = registerAndLogin();
        byte[] content = ResumeFixtures.CHINESE_RESUME_MD.getBytes(StandardCharsets.UTF_8);

        // .doc 老格式
        MvcResult doc = upload(token, "old.doc", "application/msword", content, null);
        assertThat(doc.getResponse().getStatus()).isEqualTo(400);
        assertThat(code(body(doc))).isEqualTo(40001);
        assertThat((String) body(doc).get("message")).contains("docx");

        // 扩展名合法但 Content-Type 不在白名单
        MvcResult png = upload(token, "a.pdf", "image/png", ResumeFixtures.englishPdf(), null);
        assertThat(png.getResponse().getStatus()).isEqualTo(400);
        assertThat(code(body(png))).isEqualTo(40001);

        // 缺 file part
        MvcResult missing = mockMvc.perform(multipart("/api/resumes")
                .param("positionId", "1")
                .header("Authorization", "Bearer " + token)).andReturn();
        assertThat(missing.getResponse().getStatus()).isEqualTo(400);
        assertThat(code(body(missing))).isEqualTo(40001);

        // 空文件
        MvcResult empty = upload(token, "empty.md", "text/markdown", new byte[0], null);
        assertThat(empty.getResponse().getStatus()).isEqualTo(400);
        assertThat(code(body(empty))).isEqualTo(40001);

        // 岗位不存在
        MvcResult badPos = upload(token, "a.md", "text/markdown", content, 99999L);
        assertThat(badPos.getResponse().getStatus()).isEqualTo(400);
        assertThat(code(body(badPos))).isEqualTo(40001);

        // 以上均未落库
        assertThat(records(token)).isEmpty();

        // 未登录 → 401/40101
        mockMvc.perform(multipart("/api/resumes")
                        .file(new MockMultipartFile("file", "a.md", "text/markdown", content)))
                .andExpect(status().isUnauthorized());
    }

    // ==================== R-02 / R-03：列表与详情 ====================

    @Test
    void listPagedDescAndIsolatedPerUser() throws Exception {
        String alice = registerAndLogin();
        String bob = registerAndLogin();
        long first = uploadMd(alice);
        long second = uploadMd(alice);   // 同秒内 created_at 相同时按 id 倒序兜底
        awaitStatus(alice, first, "ANALYZED");
        awaitStatus(alice, second, "ANALYZED");

        Map<String, Object> page = list(alice, 1, 10);
        assertThat(code(page)).isZero();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) page.get("data");
        assertThat(data).containsKeys("records", "total", "size", "current", "pages");
        assertThat(((Number) data.get("total")).intValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        assertThat(records).extracting(r -> ((Number) r.get("id")).longValue())
                .containsExactly(second, first);
        Map<String, Object> item = records.get(0);
        assertThat(item.get("fileName")).isEqualTo("简历.md");
        assertThat(item.get("fileType")).isEqualTo("MD");
        assertThat(item.get("positionId")).isEqualTo(1);
        assertThat(item.get("positionName")).isEqualTo("Java 后端开发工程师");
        assertThat(item.get("status")).isEqualTo("ANALYZED");
        assertThat(item.get("isActive")).isEqualTo(false);
        // R2：ANALYZED 后列表含 overallScore（0~100）
        assertThat(((Number) item.get("overallScore")).intValue()).isBetween(0, 100);

        // 分页切片
        @SuppressWarnings("unchecked")
        Map<String, Object> p2 = (Map<String, Object>) list(alice, 2, 1).get("data");
        assertThat(((Number) p2.get("pages")).intValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> p2records = (List<Map<String, Object>>) p2.get("records");
        assertThat(p2records).hasSize(1);
        assertThat(((Number) p2records.get(0).get("id")).longValue()).isEqualTo(first);

        // 用户隔离
        assertThat(records(bob)).isEmpty();

        // 分页越界 → 400/40002（协议 §1）
        for (String q : List.of("page=0&size=10", "page=1&size=51", "page=1&size=0")) {
            MvcResult bad = mockMvc.perform(get("/api/resumes?" + q)
                    .header("Authorization", "Bearer " + alice)).andReturn();
            assertThat(bad.getResponse().getStatus()).as(q).isEqualTo(400);
            assertThat(code(body(bad))).as(q).isEqualTo(40002);
        }
    }

    @Test
    void detailOwnershipHiddenAs40401() throws Exception {
        String alice = registerAndLogin();
        String bob = registerAndLogin();
        long id = uploadMd(alice);
        Map<String, Object> d = awaitStatus(alice, id, "ANALYZED");

        assertThat(d.get("id")).isEqualTo((int) id);
        assertThat(d.get("fileName")).isEqualTo("简历.md");
        assertThat(d.get("positionName")).isEqualTo("Java 后端开发工程师");
        assertThat(d.get("status")).isEqualTo("ANALYZED");
        assertThat(((Number) d.get("charCount")).intValue()).isGreaterThanOrEqualTo(200);
        assertThat(d.get("isActive")).isEqualTo(false);
        assertThat(d).containsKeys("createdAt", "updatedAt", "fileSize", "analysis", "digest", "profile")
                .doesNotContainKey("failReason");

        // 他人 / 不存在：统一 40401（HTTP 200，不暴露存在性）
        assertThat(code(detail(bob, id))).isEqualTo(40401);
        assertThat(code(detail(alice, 999_999L))).isEqualTo(40401);
        assertThat(code(setActive(bob, id))).isEqualTo(40401);
        assertThat(code(remove(bob, id))).isEqualTo(40401);
        // 他人操作不改变本人数据
        assertThat(records(alice)).hasSize(1);
    }

    // ==================== R-07：逻辑删除 ====================

    @Test
    void logicalDeleteHidesResume() throws Exception {
        String token = registerAndLogin();
        long id = uploadMd(token);
        long keep = uploadMd(token);
        awaitStatus(token, id, "ANALYZED");
        awaitStatus(token, keep, "ANALYZED");

        Map<String, Object> del = remove(token, id);
        assertThat(code(del)).isZero();
        assertThat(del.get("data")).isEqualTo(Map.of("id", (int) id, "deleted", true));

        assertThat(records(token)).extracting(r -> ((Number) r.get("id")).longValue())
                .containsExactly(keep);
        assertThat(code(detail(token, id))).isEqualTo(40401);
        assertThat(code(remove(token, id))).isEqualTo(40401);          // 重复删除
        assertThat(code(setActive(token, id))).isEqualTo(40401);

        // 行仍在库中（deleted=1，文件保留审计）；@TableLogic 使普通查询自动过滤
        Map<String, Object> raw = jdbcTemplate.queryForMap(
                "SELECT deleted, is_active, stored_path FROM resume WHERE id = ?", id);
        assertThat(((Number) raw.get("deleted")).intValue()).isEqualTo(1);
        assertThat(Path.of("target/test-uploads").resolve((String) raw.get("stored_path"))).exists();
        assertThat(resumeMapper.selectById(id)).isNull();
    }

    // ==================== R-06：默认简历 ====================

    @Test
    void setActiveRequiresAnalyzedAndIsExclusive() throws Exception {
        String token = registerAndLogin();
        long r1 = uploadMd(token);
        long r2 = uploadMd(token);
        awaitStatus(token, r1, "ANALYZED");
        awaitStatus(token, r2, "ANALYZED");

        // 非 ANALYZED（固定注入 ANALYZING）不可设默认 → 400/40001
        pinStatus(r1, "ANALYZING");
        MvcResult notReady = mockMvc.perform(put("/api/resumes/" + r1 + "/active")
                .header("Authorization", "Bearer " + token)).andReturn();
        assertThat(notReady.getResponse().getStatus()).isEqualTo(400);
        assertThat(code(body(notReady))).isEqualTo(40001);
        pinStatus(r1, "ANALYZED");

        Map<String, Object> a1 = setActive(token, r1);
        assertThat(code(a1)).isZero();
        assertThat(a1.get("data")).isEqualTo(Map.of("id", (int) r1, "isActive", true));
        assertThat(activeIds(token)).containsExactly(r1);

        // 幂等
        assertThat(code(setActive(token, r1))).isZero();
        assertThat(activeIds(token)).containsExactly(r1);

        // 切换到 r2：r1 自动取消
        assertThat(code(setActive(token, r2))).isZero();
        assertThat(activeIds(token)).containsExactly(r2);
        @SuppressWarnings("unchecked")
        Map<String, Object> d1 = (Map<String, Object>) detail(token, r1).get("data");
        assertThat(d1.get("isActive")).isEqualTo(false);

        // 删除默认简历后无默认
        assertThat(code(remove(token, r2))).isZero();
        assertThat(activeIds(token)).isEmpty();
    }

    private List<Long> activeIds(String token) throws Exception {
        return records(token).stream()
                .filter(r -> Boolean.TRUE.equals(r.get("isActive")))
                .map(r -> ((Number) r.get("id")).longValue())
                .toList();
    }

    // ==================== R1-5：配额 ====================

    @Test
    void dailyQuotaExceededThenReleasedByDelete() throws Exception {
        String token = registerAndLogin();
        long firstId = -1;
        for (int i = 0; i < 10; i++) {
            long id = uploadMd(token);
            if (firstId < 0) {
                firstId = id;
            }
        }
        MvcResult over = upload(token, "简历.md", "text/markdown",
                ResumeFixtures.CHINESE_RESUME_MD.getBytes(StandardCharsets.UTF_8), 1L);
        assertThat(over.getResponse().getStatus()).isEqualTo(200);        // 业务失败 HTTP 200
        assertThat(code(body(over))).isEqualTo(10010);
        assertThat((String) body(over).get("message")).contains("今日");
        assertThat(records(token)).hasSize(10);

        // 逻辑删除释放额度（当日计数仅统计 deleted=0）
        assertThat(code(remove(token, firstId))).isZero();
        assertThat(uploadMd(token)).isPositive();
        assertThat(records(token)).hasSize(10);
    }
}
