package com.example.interview.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 10：简历（V8，简历方案 §5）。status 为唯一事实源（见 ResumeStatus），
 * 逻辑删除走 @TableLogic：MyBatis-Plus 查询自动追加 deleted=0，删除即 UPDATE deleted=1，
 * 文件保留作审计。profile_json / digest_json 由 R2 分析链路写入（脱敏后 JSON）。
 */
@Data
@TableName("resume")
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 原始文件名 */
    private String fileName;
    /** 相对 upload-dir 的路径 resume/yyyyMM/uuid.ext */
    private String storedPath;
    /** PDF / DOCX / MD / TXT */
    private String fileType;
    private Long fileSize;
    /** 分析对照岗位（空=通用分析） */
    private Long positionId;
    /** 见 ResumeStatus */
    private String status;
    /** 失败原因，[PARSE]/[AI] 前缀区分（简历计划 §6.2） */
    private String failReason;
    /** 抽取到的文本字符数 */
    private Integer charCount;
    /** 结构化抽取 JSON（脱敏后；R2 写入） */
    private String profileJson;
    /** 个性化摘要 JSON（R2 写入，F2 消费） */
    private String digestJson;
    /** 默认简历（每用户至多 1 条） */
    private Integer isActive;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
