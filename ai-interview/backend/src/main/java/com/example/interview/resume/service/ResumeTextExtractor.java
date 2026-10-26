package com.example.interview.resume.service;

import com.example.interview.common.config.ResumeProperties;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 简历文本抽取（简历计划 §6.3）：按文件类型分发 PDFBox / POI / 纯文本解码，
 * 入口先做魔数校验（内容与扩展名不符即解析失败）。纯函数：bytes + fileType → 规整文本，
 * 单测直接喂字节数组。全部失败均抛 {@code BizException(RESUME_PARSE_FAILED)} 并携带
 * 面向用户的原因文案，由 ResumeService 落库为 FAILED + fail_reason（上传接口仍 code=0）。
 */
@Slf4j
@Service
public class ResumeTextExtractor {

    public static final String MSG_TOO_SHORT = "未检测到可分析文本（不支持扫描件），请上传文本型 PDF 或 docx/md/txt";
    public static final String MSG_ENCRYPTED = "简历已加密，请去除密码后重传";
    public static final String MSG_CORRUPTED = "文件无法解析，请确认文件未损坏";
    public static final String MSG_MAGIC_MISMATCH = "文件内容与扩展名不符，请确认文件格式";
    public static final String MSG_OLD_DOC = "文件为 .doc 老格式或已加密的 Word 文档，请另存为未加密的 .docx";
    public static final String MSG_BAD_ENCODING = "不支持的文本编码，请使用 UTF-8 或 GBK 保存后重传";
    public static final String MSG_BINARY = "文件疑似二进制内容，不是纯文本简历";

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    /** OLE2 复合文档头（.doc / 加密 docx） */
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final Charset GBK = Charset.forName("GBK");
    /** 控制字符占比超过该阈值视为伪装成文本的二进制 */
    private static final double MAX_CONTROL_RATIO = 0.05;

    private final int minTextChars;

    @Autowired
    public ResumeTextExtractor(ResumeProperties properties) {
        this(properties.getMinTextChars());
    }

    /** 单测直接指定阈值，不起容器。 */
    ResumeTextExtractor(int minTextChars) {
        this.minTextChars = minTextChars;
    }

    /**
     * 抽取并规整文本；字符数低于 {@code app.resume.min-text-chars} 视为扫描件/空文档。
     *
     * @param fileType PDF / DOCX / MD / TXT（由 ResumeStorageService.fileTypeOf 产出）
     */
    public String extract(byte[] bytes, String fileType) {
        if (bytes == null || bytes.length == 0) {
            throw parseFailed(MSG_TOO_SHORT);
        }
        String raw = switch (fileType) {
            case "PDF" -> extractPdf(bytes);
            case "DOCX" -> extractDocx(bytes);
            case "MD", "TXT" -> decodeText(bytes);
            default -> throw new BizException(ResultCode.PARAM_INVALID, "不支持的简历类型：" + fileType);
        };
        String text = normalize(raw);
        if (text.length() < minTextChars) {
            throw parseFailed(MSG_TOO_SHORT);
        }
        return text;
    }

    // ==================== PDF ====================

    private String extractPdf(byte[] bytes) {
        if (!startsWith(bytes, PDF_MAGIC)) {
            throw parseFailed(MSG_MAGIC_MISMATCH);
        }
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (InvalidPasswordException e) {
            throw parseFailed(MSG_ENCRYPTED);
        } catch (Exception e) {
            log.info("PDF 抽取失败：{}", e.getMessage());
            throw parseFailed(MSG_CORRUPTED);
        }
    }

    // ==================== DOCX ====================

    /** 按文档流顺序遍历段落与表格（简历常见双栏/表格布局，表格单元格以空格拼接成行）。 */
    private String extractDocx(byte[] bytes) {
        if (startsWith(bytes, OLE2_MAGIC)) {
            throw parseFailed(MSG_OLD_DOC);
        }
        if (!startsWith(bytes, ZIP_MAGIC)) {
            throw parseFailed(MSG_MAGIC_MISMATCH);
        }
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (IBodyElement el : doc.getBodyElements()) {
                if (el instanceof XWPFParagraph p) {
                    sb.append(p.getText()).append('\n');
                } else if (el instanceof XWPFTable t) {
                    appendTable(sb, t);
                } else if (el instanceof XWPFSDT sdt && sdt.getContent() != null) {
                    sb.append(sdt.getContent().getText()).append('\n');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.info("DOCX 抽取失败：{}", e.getMessage());
            throw parseFailed(MSG_CORRUPTED);
        }
    }

    private static void appendTable(StringBuilder sb, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String text = cell.getText();
                if (text != null && !text.isBlank()) {
                    cells.add(text.trim());
                }
            }
            if (!cells.isEmpty()) {
                sb.append(String.join(" ", cells)).append('\n');
            }
        }
    }

    // ==================== MD / TXT ====================

    /** UTF-8 严格解码，失败退 GBK；再失败判编码不支持；控制字符占比过高判二进制伪装。 */
    private String decodeText(byte[] bytes) {
        String text = tryDecode(bytes, StandardCharsets.UTF_8);
        if (text == null) {
            text = tryDecode(bytes, GBK);
        }
        if (text == null) {
            throw parseFailed(MSG_BAD_ENCODING);
        }
        if (controlRatio(text) > MAX_CONTROL_RATIO) {
            throw parseFailed(MSG_BINARY);
        }
        return text;
    }

    private static String tryDecode(byte[] bytes, Charset charset) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    static double controlRatio(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        int control = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean allowed = c == '\n' || c == '\r' || c == '\t' || c == '\f' || c == '\u000B';
            if ((c < 0x20 && !allowed) || c == 0x7F) {
                control++;
            }
        }
        return (double) control / text.length();
    }

    // ==================== 工具 ====================

    /** 统一换行、去 BOM、折叠横向空白与多余空行；字符数以此结果计（char_count）。 */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n');
        s = s.replaceAll("[ \\t\\x0B\\f\\u00A0]+", " ");
        s = s.replaceAll(" *\\n *", "\n");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    private static boolean startsWith(byte[] bytes, byte[] magic) {
        return bytes.length >= magic.length
                && Arrays.equals(Arrays.copyOf(bytes, magic.length), magic);
    }

    private static BizException parseFailed(String message) {
        return new BizException(ResultCode.RESUME_PARSE_FAILED, message);
    }
}
