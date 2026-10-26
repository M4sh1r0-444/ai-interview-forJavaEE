package com.example.interview.resume.storage;

import com.example.interview.common.config.ResumeProperties;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 简历计划 §7-2 ResumeStorageService（R1-3）：扩展名白名单、Content-Type 复核、
 * 空文件/超限拒绝、UUID 落盘路径、resolve 防穿越。
 */
class ResumeStorageServiceTest {

    @TempDir
    Path tempDir;

    private ResumeStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new ResumeStorageService(tempDir.toString(), new ResumeProperties());
    }

    @Test
    void fileTypeMappedFromExtensionCaseInsensitive() {
        assertThat(storage.fileTypeOf("简历.pdf")).isEqualTo("PDF");
        assertThat(storage.fileTypeOf("Resume.DOCX")).isEqualTo("DOCX");
        assertThat(storage.fileTypeOf("a.b.md")).isEqualTo("MD");
        assertThat(storage.fileTypeOf("notes.txt")).isEqualTo("TXT");
    }

    @Test
    void disallowedExtensionRejectedAndDocGetsSaveAsHint() {
        assertParamInvalid(() -> storage.fileTypeOf("virus.exe"), "仅支持");
        assertParamInvalid(() -> storage.fileTypeOf("noext"), "仅支持");
        assertParamInvalid(() -> storage.fileTypeOf(null), "仅支持");
        assertParamInvalid(() -> storage.fileTypeOf("old.doc"), "另存为 .docx");
    }

    @Test
    void storeWritesUuidNamedFileUnderResumeMonthDir() throws Exception {
        byte[] content = "# 张三\n熟悉 Spring Boot".getBytes(StandardCharsets.UTF_8);
        String path = storage.store(new MockMultipartFile("file", "张三-简历.md", "text/markdown", content));

        assertThat(path).matches("resume/\\d{6}/[0-9a-f]{32}\\.md");
        Path stored = storage.resolve(path);
        assertThat(stored).exists();
        assertThat(stored.startsWith(tempDir.toAbsolutePath().normalize())).isTrue();
        assertThat(Files.readAllBytes(stored)).isEqualTo(content);
    }

    @Test
    void contentTypeWhitelistWithOctetStreamAndNullAllowed() {
        byte[] content = {1, 2, 3};
        assertThat(storage.store(new MockMultipartFile("file", "a.pdf", "application/pdf", content))).endsWith(".pdf");
        assertThat(storage.store(new MockMultipartFile("file", "a.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content))).endsWith(".docx");
        assertThat(storage.store(new MockMultipartFile("file", "a.md", "application/octet-stream", content))).endsWith(".md");
        assertThat(storage.store(new MockMultipartFile("file", "a.txt", "text/plain; charset=utf-8", content))).endsWith(".txt");
        assertThat(storage.store(new MockMultipartFile("file", "a.txt", null, content))).endsWith(".txt");

        assertParamInvalid(() -> storage.store(new MockMultipartFile("file", "a.pdf", "image/png", content)),
                "Content-Type");
        assertParamInvalid(() -> storage.store(new MockMultipartFile("file", "a.md", "audio/webm", content)),
                "Content-Type");
    }

    @Test
    void emptyFileAndOversizeRejected() {
        assertParamInvalid(() -> storage.store(new MockMultipartFile("file", "a.md", "text/plain", new byte[0])),
                "为空");
        assertParamInvalid(() -> storage.store(null), "为空");
        byte[] oversize = new byte[(int) ResumeStorageService.MAX_FILE_BYTES + 1];
        assertParamInvalid(() -> storage.store(new MockMultipartFile("file", "big.txt", "text/plain", oversize)),
                "大小限制");
    }

    @Test
    void resolveRejectsPathTraversal() {
        assertParamInvalid(() -> storage.resolve("../../etc/passwd"), "非法");
        assertParamInvalid(() -> storage.resolve("resume/202610/../../../x.pdf"), "非法");
        assertThat(storage.resolve("resume/202610/abc.pdf"))
                .isEqualTo(tempDir.toAbsolutePath().normalize().resolve("resume/202610/abc.pdf"));
    }

    @Test
    void readReturnsStoredBytesAndMissingFileIsParseFailure() {
        byte[] content = "hello resume".getBytes(StandardCharsets.UTF_8);
        String path = storage.store(new MockMultipartFile("file", "a.txt", "text/plain", content));
        assertThat(storage.read(path)).isEqualTo(content);
        assertThatThrownBy(() -> storage.read("resume/209901/missing.txt"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(ResultCode.RESUME_PARSE_FAILED);
    }

    private static void assertParamInvalid(Runnable action, String messagePart) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    BizException biz = (BizException) e;
                    assertThat(biz.getResultCode()).isEqualTo(ResultCode.PARAM_INVALID);
                    assertThat(biz.getMessage()).contains(messagePart);
                });
    }
}
