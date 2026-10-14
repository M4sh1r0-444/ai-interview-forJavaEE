package com.example.interview.interview;

import com.example.interview.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
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
 * 计划 §8.2 AudioAnswerIT（M4-4.1/4.2）：AUDIO 会话 multipart 提交 → uploads 落盘、
 * 消息含 asr_text/audio_path/duration/keywords；非音频扩展名与 TEXT 会话提交被拒；
 * AUDIO 场报告含 voice 维度；受保护回放接口做归属校验。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.upload-dir=./target/test-uploads")
class AudioAnswerIT {

    /** 语速 180 字/分的示例转写（600 字 / 200 秒） */
    private static final String ASR_TEXT = ("缓存击穿指热点key过期瞬间大量请求同时打到数据库，"
            + "常见对策是互斥锁保证只有一个线程回源、逻辑过期让旧值先行兜底，"
            + "并把热点key的TTL加随机抖动错开过期时间，同时配合接口限流与多级缓存降低数据库压力。")
            .repeat(2);

    @Autowired
    private MockMvc mockMvc;

    // ==================== 工具 ====================

    private String registerAndLogin() throws Exception {
        String uname = "au" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))));
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(JsonUtils.toJson(Map.of("username", uname, "password", "123456"))))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body(login).get("data");
        return (String) data.get("token");
    }

    private long createSession(String token, String mode) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(JsonUtils.toJson(Map.of("positionId", 1, "mode", mode,
                                "difficulty", 3, "rounds", 5, "durationMinutes", 15))))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body(res).get("data");
        return ((Number) data.get("id")).longValue();
    }

    private Map<String, Object> submitAudio(String token, long id, int roundNo,
                                            String filename, String contentType,
                                            String asrText, Integer durationMs) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, contentType, new byte[]{1, 2, 3, 4, 5});
        MvcResult res = mockMvc.perform(multipart("/api/interviews/" + id + "/messages/audio")
                        .file(file)
                        .header("Authorization", "Bearer " + token)
                        .param("roundNo", String.valueOf(roundNo))
                        .param("asrText", asrText)
                        .param("durationMs", String.valueOf(durationMs)))
                .andReturn();
        return body(res);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> messages(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/interviews/" + id + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return (List<Map<String, Object>>) body(res).get("data");
    }

    private Map<String, Object> report(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/interviews/" + id + "/report")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        return body(res);
    }

    private Map<String, Object> detail(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/interviews/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body(res).get("data");
        return data;
    }

    /** lambda 安全版本：查询失败返回 -1（awaitUntil 重试）。 */
    private int reportCode(String token, long id) {
        try {
            return code(report(token, id));
        } catch (Exception e) {
            return -1;
        }
    }

    private String statusOf(String token, long id) {
        try {
            return (String) detail(token, id).get("status");
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(MvcResult res) throws Exception {
        return JsonUtils.parse(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                Map.class);
    }

    private static int code(Map<String, Object> body) {
        return ((Number) body.get("code")).intValue();
    }

    private static void awaitUntil(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("等待超时：" + what);
    }

    // ==================== 用例 ====================

    @Test
    void audioAnswerStoredAndFlowContinues() throws Exception {
        String token = registerAndLogin();
        long id = createSession(token, "AUDIO");
        mockMvc.perform(post("/api/interviews/" + id + "/start")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        Map<String, Object> resp = submitAudio(token, id, 0, "answer.webm",
                "audio/webm", ASR_TEXT, 200_000);
        assertThat(code(resp)).isZero();
        assertThat(resp.get("data")).isEqualTo(Map.of("accepted", true, "roundNo", 0));

        // AI 异步产出（追问或下一题）后回到 WAITING_USER
        awaitUntil("AI 产出", () -> "WAITING_USER".equals(statusOf(token, id)));

        Map<String, Object> userMsg = messages(token, id).stream()
                .filter(m -> "USER".equals(m.get("speaker"))).findFirst().orElseThrow();
        assertThat(userMsg.get("inputType")).isEqualTo("AUDIO");
        assertThat((String) userMsg.get("asrText")).startsWith("缓存击穿");
        assertThat(userMsg.get("audioDurationMs")).isEqualTo(200_000);
        String audioPath = (String) userMsg.get("audioPath");
        assertThat(audioPath).matches("audio/\\d{6}/[0-9a-f]{32}\\.webm");

        // uploads 下生成文件（测试目录 target/test-uploads）
        Path stored = Path.of("target/test-uploads").resolve(audioPath).normalize();
        assertThat(stored).exists();
        assertThat(Files.size(stored)).isEqualTo(5);

        // keywords_json 已由 KeywordExtractor 填充
        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) userMsg.get("keywords");
        assertThat(keywords).isNotEmpty();

        // 受保护回放：本人可读
        mockMvc.perform(get("/api/interviews/" + id + "/messages/" + userMsg.get("id") + "/audio")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 收卷出报告：AUDIO 场含 voice 维度（M4-4.2）
        mockMvc.perform(post("/api/interviews/" + id + "/finish")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        awaitUntil("报告生成", () -> reportCode(token, id) == 0);
        Map<String, Object> data = (Map<String, Object>) report(token, id).get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> dims = (Map<String, Object>) data.get("dimensionScores");
        assertThat(dims).containsKeys("technical", "depth", "logic", "match", "communication", "voice");
        assertThat(((Number) dims.get("voice")).doubleValue()).isBetween(0.0, 100.0);
    }

    @Test
    void invalidExtensionAndTextModeRejected() throws Exception {
        String token = registerAndLogin();
        long id = createSession(token, "AUDIO");
        mockMvc.perform(post("/api/interviews/" + id + "/start")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        // 非音频扩展名 → 40001，且状态不受影响（事务回滚，仍 WAITING_USER）
        assertThat(code(submitAudio(token, id, 0, "virus.exe",
                "application/octet-stream", ASR_TEXT, 200_000))).isEqualTo(40001);
        assertThat(detail(token, id).get("status")).isEqualTo("WAITING_USER");

        // Content-Type 复核：扩展名合法但 content-type 是 text/plain → 40001
        assertThat(code(submitAudio(token, id, 0, "a.wav",
                "text/plain", ASR_TEXT, 200_000))).isEqualTo(40001);

        // 缺 asrText / durationMs 越界 → 40001
        assertThat(code(submitAudio(token, id, 0, "a.wav",
                "audio/wav", " ", 200_000))).isEqualTo(40001);
        assertThat(code(submitAudio(token, id, 0, "a.wav",
                "audio/wav", ASR_TEXT, 0))).isEqualTo(40001);

        // TEXT 会话提交音频 → 40002
        String other = registerAndLogin();
        long textId = createSession(other, "TEXT");
        mockMvc.perform(post("/api/interviews/" + textId + "/start")
                .header("Authorization", "Bearer " + other)).andExpect(status().isOk());
        assertThat(code(submitAudio(other, textId, 0, "a.webm",
                "audio/webm", ASR_TEXT, 200_000))).isEqualTo(40002);

        // 越权用户读音频回放 → 40401
        MvcResult audioResp = mockMvc.perform(get("/api/interviews/" + id + "/messages/999999/audio")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertThat(code(body(audioResp))).isEqualTo(40401);
    }

    @Test
    void audioPlaybackForbiddenForOtherUser() throws Exception {
        String token = registerAndLogin();
        long id = createSession(token, "AUDIO");
        mockMvc.perform(post("/api/interviews/" + id + "/start")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        submitAudio(token, id, 0, "a.mp3", "audio/mpeg", ASR_TEXT, 200_000);
        awaitUntil("AI 产出", () -> "WAITING_USER".equals(statusOf(token, id)));

        Map<String, Object> userMsg = messages(token, id).stream()
                .filter(m -> "USER".equals(m.get("speaker"))).findFirst().orElseThrow();

        String stranger = registerAndLogin();
        MvcResult res = mockMvc.perform(get("/api/interviews/" + id + "/messages/"
                        + userMsg.get("id") + "/audio")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isOk()).andReturn();
        assertThat(code(body(res))).isEqualTo(40401);

        // 本人可读
        mockMvc.perform(get("/api/interviews/" + id + "/messages/" + userMsg.get("id") + "/audio")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
