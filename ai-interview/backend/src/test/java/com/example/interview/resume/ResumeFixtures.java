package com.example.interview.resume;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 简历测试夹具（简历计划 §8）：在内存中用 PDFBox / POI 生成四格式样本字节，
 * 单测与 IT 共用，不依赖外部样例文件。PDF 标准 14 字体不支持 CJK，PDF 样本用英文。
 */
public final class ResumeFixtures {

    private ResumeFixtures() {
    }

    /** 中文 Markdown 简历（约 600 字，含手机/邮箱供 R2 脱敏用例） */
    public static final String CHINESE_RESUME_MD = """
            # 张三
            求职意向：Java 后端开发工程师
            手机：13812345678  邮箱：zhangsan@example.com

            ## 教育背景
            2022.09 - 2026.06  某某大学  计算机科学与技术  本科
            主修课程：数据结构、操作系统、计算机网络、数据库原理；GPA 3.6/4.0，专业排名前 15%

            ## 专业技能
            - 熟悉 Java 基础、集合与并发，了解 JVM 内存模型与常用调优参数
            - 熟悉 Spring Boot、MyBatis-Plus，掌握 RESTful 接口设计与统一异常处理
            - 熟悉 MySQL 索引与事务，了解 Redis 缓存与分布式锁
            - 了解 Vue3、Docker 基本使用

            ## 项目经历
            项目名称：校园二手交易平台（2025.03 - 2025.06）
            技术栈：Spring Boot、MySQL、Redis、Vue3
            - 负责订单模块与秒杀下单链路，使用 Redis 预减库存 + 消息队列异步落库，压测峰值 500 QPS
            - 下单接口平均耗时由 320ms 降至 90ms；实现基于 JWT 的无状态鉴权与接口限流

            ## 实习经历
            2025.07 - 2025.09  某互联网公司  后端开发实习生
            参与内部运营后台开发，完成 12 个接口与 3 张报表，修复线上缓存穿透问题

            ## 荣誉奖项
            蓝桥杯 Java 组省二等奖；校级优秀学生
            """;

    /** 英文简历行（PDF 用；合计远超 200 字符） */
    public static final List<String> ENGLISH_RESUME_LINES = List.of(
            "John Doe - Java Backend Developer",
            "Email: john.doe@example.com  Phone: 13812345678",
            "Education: B.Sc. Computer Science, Example University, 2022-2026, GPA 3.6/4.0",
            "Skills: Java, Spring Boot, MyBatis-Plus, MySQL, Redis, Docker, Vue3",
            "Project: Campus Second-hand Trading Platform (2025.03-2025.06)",
            "Responsible for the order module and flash-sale flow using Redis stock pre-deduction,",
            "peak throughput reached 500 QPS under load testing, latency reduced from 320ms to 90ms.",
            "Internship: Backend Developer Intern at Example Internet Company, 2025.07-2025.09",
            "Delivered 12 REST APIs and 3 reports, fixed a production cache penetration issue.");

    /** 文本型 PDF（英文，Helvetica） */
    public static byte[] pdfWithText(List<String> lines) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.setLeading(14f);
                cs.newLineAtOffset(50, 780);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            return save(doc);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] englishPdf() {
        return pdfWithText(ENGLISH_RESUME_LINES);
    }

    /** 无文本层的空白页 PDF（模拟扫描件） */
    public static byte[] blankPdf() {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            return save(doc);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 设置了用户口令的加密 PDF（无口令打开抛 InvalidPasswordException） */
    public static byte[] encryptedPdf() {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            AccessPermission ap = new AccessPermission();
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-pwd", "user-pwd", ap);
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            return save(doc);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** docx：若干段落 + 一张表格（表格行按单元格顺序给出） */
    public static byte[] docx(List<String> paragraphs, List<List<String>> tableRows) {
        try (XWPFDocument doc = new XWPFDocument()) {
            for (String p : paragraphs) {
                doc.createParagraph().createRun().setText(p);
            }
            if (tableRows != null && !tableRows.isEmpty()) {
                int cols = tableRows.get(0).size();
                XWPFTable table = doc.createTable(tableRows.size(), cols);
                for (int i = 0; i < tableRows.size(); i++) {
                    for (int j = 0; j < cols; j++) {
                        table.getRow(i).getCell(j).setText(tableRows.get(i).get(j));
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 中文 docx 简历：段落取 Markdown 行，并附一张"技能表"表格 */
    public static byte[] chineseDocx() {
        List<String> paragraphs = CHINESE_RESUME_MD.lines()
                .map(l -> l.replaceFirst("^#+\\s*", "").replaceFirst("^-\\s*", ""))
                .filter(l -> !l.isBlank())
                .toList();
        return docx(paragraphs, List.of(
                List.of("技能", "程度"),
                List.of("Spring Boot", "熟悉"),
                List.of("Redis", "了解")));
    }

    private static byte[] save(PDDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }
}
