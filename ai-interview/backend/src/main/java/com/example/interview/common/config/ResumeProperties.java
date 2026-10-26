package com.example.interview.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 简历功能配置（简历计划 §2.2 / 附录B）：格式白名单、解析文本下限、送模型上限、
 * 每用户保留与每日上传配额。max-count-per-user / daily-upload-limit 支持环境变量覆盖。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.resume")
public class ResumeProperties {

    /** 扩展名白名单（小写） */
    private List<String> allowedTypes = List.of("pdf", "docx", "md", "txt");
    /** 抽取字符数低于此值判解析失败（扫描件/空文档） */
    private int minTextChars = 200;
    /** 送模型文本上限（超出截断；char_count 仍记全量） */
    private int maxAnalysisChars = 20000;
    /** 每用户保留上限（未删除计数） */
    private int maxCountPerUser = 20;
    /** 每用户每日上传上限（当日零点起、未删除计数） */
    private int dailyUploadLimit = 10;
}
