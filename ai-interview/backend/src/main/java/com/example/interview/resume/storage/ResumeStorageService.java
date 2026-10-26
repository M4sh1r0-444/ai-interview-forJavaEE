package com.example.interview.resume.storage;

import com.example.interview.common.config.ResumeProperties;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 简历文件存储（简历计划 §6.1 / §7-2，照抄 AudioStorageService 三查一 resolve）：
 * 扩展名白名单 pdf/docx/md/txt + Content-Type 复核（放行 octet-stream）+ 大小上限，
 * UUID 命名落盘 {app.upload-dir}/resume/yyyyMM/，返回相对路径存 resume.stored_path。
 * 文件内容魔数校验由 ResumeTextExtractor 在分发抽取时完成（内容与扩展名不符即解析失败）。
 * uploads 不注册静态资源映射，简历文件不提供公网直读。
 */
@Slf4j
@Service
public class ResumeStorageService {

    /** 与 spring.servlet.multipart.max-file-size 一致；MockMvc 不走 multipart 限流，此处兜底 */
    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/markdown",
            "text/x-markdown",
            "text/plain",
            "application/octet-stream");

    private final Path uploadDir;
    private final List<String> allowedTypes;

    public ResumeStorageService(@Value("${app.upload-dir:./uploads}") String uploadDir,
                                ResumeProperties properties) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.allowedTypes = properties.getAllowedTypes().stream()
                .map(t -> t.toLowerCase(Locale.ROOT)).toList();
    }

    /**
     * 按扩展名判定文件类型（PDF / DOCX / MD / TXT），白名单外抛 40001。
     * .doc 老格式单独提示"另存为 .docx"（协议 §1.3 范围边界）。
     */
    public String fileTypeOf(String filename) {
        String ext = extensionOf(filename);
        if (!allowedTypes.contains(ext)) {
            String hint = "doc".equals(ext) ? "，.doc 老格式请另存为 .docx 后重传" : "";
            throw new BizException(ResultCode.PARAM_INVALID,
                    "简历格式仅支持 " + String.join("/", allowedTypes) + hint);
        }
        return ext.toUpperCase(Locale.ROOT);
    }

    /**
     * 校验并保存简历文件，返回相对路径（resume/yyyyMM/uuid.ext）。
     * 校验顺序：非空 → 扩展名白名单 → Content-Type 复核 → 大小上限。
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "上传文件为空");
        }
        String ext = extensionOf(file.getOriginalFilename());
        fileTypeOf(file.getOriginalFilename());
        String contentType = file.getContentType();
        if (contentType != null) {
            String mime = contentType.toLowerCase(Locale.ROOT);
            int semi = mime.indexOf(';');
            if (semi >= 0) {
                mime = mime.substring(0, semi).trim();
            }
            if (!ALLOWED_CONTENT_TYPES.contains(mime)) {
                throw new BizException(ResultCode.PARAM_INVALID, "Content-Type 不受支持：" + contentType);
            }
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BizException(ResultCode.PARAM_INVALID, "上传文件超过大小限制（10MB）");
        }
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        // UUID 文件名：杜绝路径穿越与覆盖（计划 §7-6）
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = uploadDir.resolve("resume").resolve(month);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("简历已存储 {} ({} bytes)", target, file.getSize());
            return "resume/" + month + "/" + filename;
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "简历保存失败：" + e.getMessage());
        }
    }

    /** 相对路径 → 绝对路径（校验仍位于 upload-dir 内，防 ../ 穿越）。 */
    public Path resolve(String relativePath) {
        Path resolved = uploadDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(uploadDir)) {
            throw new BizException(ResultCode.PARAM_INVALID, "非法简历路径");
        }
        return resolved;
    }

    /** 读取已落盘简历字节（R2 异步分析重新抽取用；文件缺失视为解析失败）。 */
    public byte[] read(String relativePath) {
        try {
            return Files.readAllBytes(resolve(relativePath));
        } catch (IOException e) {
            throw new BizException(ResultCode.RESUME_PARSE_FAILED, "简历文件不存在或不可读");
        }
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
