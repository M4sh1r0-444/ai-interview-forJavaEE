package com.example.interview.resume.service;

import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import com.example.interview.resume.ResumeFixtures;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 简历计划 §8.1 ResumeTextExtractorTest（R1-3）：四格式抽取、魔数不符、加密 PDF、
 * 损坏文件、短文本（扫描件）、二进制伪装 txt、编码不支持、空白规整。纯字节输入，不落盘。
 */
class ResumeTextExtractorTest {

    private final ResumeTextExtractor extractor = new ResumeTextExtractor(200);

    // ==================== 四格式抽取 ====================

    @Test
    void pdfTextLayerExtracted() {
        String text = extractor.extract(ResumeFixtures.englishPdf(), "PDF");
        assertThat(text).contains("Java Backend Developer")
                .contains("500 QPS")
                .contains("cache penetration");
        assertThat(text.length()).isGreaterThanOrEqualTo(200);
    }

    @Test
    void docxParagraphsAndTableCellsExtractedInOrder() {
        String text = extractor.extract(ResumeFixtures.chineseDocx(), "DOCX");
        assertThat(text).contains("求职意向：Java 后端开发工程师")
                .contains("校园二手交易平台")
                .contains("Spring Boot 熟悉")   // 表格单元格以空格拼接成行
                .contains("Redis 了解");
        // 表格位于段落之后（按文档流顺序）
        assertThat(text.indexOf("蓝桥杯")).isLessThan(text.indexOf("Spring Boot 熟悉"));
    }

    @Test
    void markdownUtf8Decoded() {
        byte[] bytes = ResumeFixtures.CHINESE_RESUME_MD.getBytes(StandardCharsets.UTF_8);
        String text = extractor.extract(bytes, "MD");
        assertThat(text).startsWith("# 张三").contains("## 项目经历");
    }

    @Test
    void txtGbkFallbackWhenNotUtf8() {
        byte[] gbk = ResumeFixtures.CHINESE_RESUME_MD.getBytes(Charset.forName("GBK"));
        // 中文 GBK 字节不是合法 UTF-8，应退回 GBK 解码且不出现乱码
        String text = extractor.extract(gbk, "TXT");
        assertThat(text).contains("求职意向：Java 后端开发工程师").doesNotContain("\uFFFD");
    }

    @Test
    void utf8WithBomStripped() {
        byte[] body = ResumeFixtures.CHINESE_RESUME_MD.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        assertThat(extractor.extract(withBom, "TXT")).startsWith("# 张三");
    }

    // ==================== 护栏：魔数 / 加密 / 损坏 ====================

    @Test
    void pdfMagicMismatchRejected() {
        // docx（ZIP）字节冒充 .pdf
        assertParseFailed(() -> extractor.extract(ResumeFixtures.chineseDocx(), "PDF"),
                ResumeTextExtractor.MSG_MAGIC_MISMATCH);
    }

    @Test
    void docxMagicMismatchRejected() {
        // pdf 字节冒充 .docx
        assertParseFailed(() -> extractor.extract(ResumeFixtures.englishPdf(), "DOCX"),
                ResumeTextExtractor.MSG_MAGIC_MISMATCH);
    }

    @Test
    void oldDocFormatRejectedWithSaveAsHint() {
        byte[] ole2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1,
                0, 0, 0, 0, 0, 0, 0, 0};
        assertParseFailed(() -> extractor.extract(ole2, "DOCX"), ResumeTextExtractor.MSG_OLD_DOC);
    }

    @Test
    void encryptedPdfRejected() {
        assertParseFailed(() -> extractor.extract(ResumeFixtures.encryptedPdf(), "PDF"),
                ResumeTextExtractor.MSG_ENCRYPTED);
    }

    @Test
    void corruptedPdfRejected() {
        byte[] broken = "%PDF-1.7\nthis is not a real pdf body".getBytes(StandardCharsets.US_ASCII);
        assertParseFailed(() -> extractor.extract(broken, "PDF"), ResumeTextExtractor.MSG_CORRUPTED);
    }

    @Test
    void corruptedDocxRejected() {
        byte[] broken = {0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        assertParseFailed(() -> extractor.extract(broken, "DOCX"), ResumeTextExtractor.MSG_CORRUPTED);
    }

    // ==================== 护栏：短文本 / 二进制 / 编码 ====================

    @Test
    void blankPdfTreatedAsScanAndRejected() {
        assertParseFailed(() -> extractor.extract(ResumeFixtures.blankPdf(), "PDF"),
                ResumeTextExtractor.MSG_TOO_SHORT);
    }

    @Test
    void shortTextRejected() {
        byte[] bytes = "张三 Java 后端 熟悉 Spring Boot".getBytes(StandardCharsets.UTF_8);
        assertParseFailed(() -> extractor.extract(bytes, "TXT"), ResumeTextExtractor.MSG_TOO_SHORT);
        assertParseFailed(() -> extractor.extract(new byte[0], "MD"), ResumeTextExtractor.MSG_TOO_SHORT);
    }

    @Test
    void whitespacePaddingDoesNotPassMinChars() {
        String padded = "姓名 张三" + "\n\n\n\n".repeat(100) + " ".repeat(300) + "\t".repeat(100);
        assertParseFailed(() -> extractor.extract(padded.getBytes(StandardCharsets.UTF_8), "TXT"),
                ResumeTextExtractor.MSG_TOO_SHORT);
    }

    @Test
    void binaryDisguisedAsTxtRejected() {
        // 大量 NUL/控制字节：UTF-8 与 GBK 均可解码，但控制字符占比远超 5%
        byte[] binary = new byte[600];
        byte[] prefix = "resume".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(prefix, 0, binary, 0, prefix.length);
        for (int i = prefix.length; i < binary.length; i++) {
            binary[i] = (byte) (i % 7 == 0 ? 0x01 : 0x00);
        }
        assertParseFailed(() -> extractor.extract(binary, "TXT"), ResumeTextExtractor.MSG_BINARY);
    }

    @Test
    void unsupportedEncodingRejected() {
        // 0xFF 既非合法 UTF-8 也非 GBK 首字节
        byte[] bytes = new byte[400];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 2 == 0 ? 0xFF : 0xFE);
        }
        assertParseFailed(() -> extractor.extract(bytes, "MD"), ResumeTextExtractor.MSG_BAD_ENCODING);
    }

    @Test
    void unknownFileTypeIsParamInvalid() {
        assertThatThrownBy(() -> extractor.extract("x".getBytes(StandardCharsets.UTF_8), "EXE"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(ResultCode.PARAM_INVALID);
    }

    // ==================== 规整 ====================

    @Test
    void normalizeCollapsesWhitespaceAndLineBreaks() {
        String raw = "\uFEFF姓名：  张三\t\t后端\r\n\r\n\r\n\r\n技能： Java  \r\n";
        assertThat(ResumeTextExtractor.normalize(raw)).isEqualTo("姓名： 张三 后端\n\n技能： Java");
        assertThat(ResumeTextExtractor.normalize(null)).isEmpty();
    }

    @Test
    void controlRatioCounted() {
        assertThat(ResumeTextExtractor.controlRatio("")).isZero();
        assertThat(ResumeTextExtractor.controlRatio("ab\n\t")).isZero();           // 换行/制表允许
        assertThat(ResumeTextExtractor.controlRatio("a\u0000b\u0001")).isEqualTo(0.5);
    }

    // ==================== 工具 ====================

    private static void assertParseFailed(Runnable action, String expectedMessage) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    BizException biz = (BizException) e;
                    assertThat(biz.getResultCode()).isEqualTo(ResultCode.RESUME_PARSE_FAILED);
                    assertThat(biz.getMessage()).isEqualTo(expectedMessage);
                });
    }
}
