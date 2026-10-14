package com.example.interview.interview.storage;

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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 音频上传存储（计划 §4.3 / §6.9 / §7-5/6）：扩展名白名单 webm/wav/mp3 +
 * Content-Type 复核，UUID 命名防路径穿越，落盘 {app.upload-dir}/audio/yyyyMM/，
 * 返回相对路径（audio/yyyyMM/uuid.ext）存 interview_message.audio_path。
 * uploads 为工程外数据目录，不注册静态资源映射（音频经归属校验的接口读取）。
 */
@Slf4j
@Service
public class AudioStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("webm", "wav", "mp3");
    /** Content-Type 白名单前缀；octet-stream 放行（部分客户端不识别音频 mime） */
    private static final String AUDIO_PREFIX = "audio/";
    private static final String OCTET_STREAM = "application/octet-stream";

    private final Path uploadDir;

    public AudioStorageService(@Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * 校验并保存音频文件，返回相对路径（相对 upload-dir）。
     * 大小由 spring.servlet.multipart.max-file-size=10MB 全局限制（计划 §7-5）。
     */
    public String store(MultipartFile file) {
        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "音频格式仅支持 " + String.join("/", ALLOWED_EXTENSIONS));
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith(AUDIO_PREFIX)
                && !OCTET_STREAM.equals(contentType.toLowerCase(Locale.ROOT))) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "Content-Type 非 audio/*：" + contentType);
        }
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        // UUID 文件名：杜绝路径穿越与覆盖（计划 §7-6）
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = uploadDir.resolve("audio").resolve(month);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("音频已存储 {} ({} bytes)", target, file.getSize());
            return "audio/" + month + "/" + filename;
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "音频保存失败：" + e.getMessage());
        }
    }

    /** 相对路径 → 绝对路径（校验仍位于 upload-dir 内，防 ../ 穿越）。 */
    public Path resolve(String relativePath) {
        Path resolved = uploadDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(uploadDir)) {
            throw new BizException(ResultCode.PARAM_INVALID, "非法音频路径");
        }
        return resolved;
    }

    /** 按扩展名推断回放 Content-Type。 */
    public String contentTypeOf(String relativePath) {
        return switch (extensionOf(relativePath)) {
            case "webm" -> "audio/webm";
            case "wav" -> "audio/wav";
            case "mp3" -> "audio/mpeg";
            default -> "application/octet-stream";
        };
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
