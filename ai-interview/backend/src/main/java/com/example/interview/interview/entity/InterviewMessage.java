package com.example.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 表 5：面试消息。AI/AI(追问)/USER/SYSTEM 共用一张表，按 round_no 归属主问题轮次。
 */
@Data
@TableName(value = "interview_message", autoResultMap = true)
public class InterviewMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    /** 所属主问题轮次（0-based） */
    private Integer roundNo;
    /** AI / USER / SYSTEM */
    private String speaker;
    /** TEXT / AUDIO（仅 USER 消息有值） */
    private String inputType;
    private String contentText;
    private String audioPath;
    private Integer audioDurationMs;
    /** 前端 Web Speech API 转写文本（M4 语音链路） */
    private String asrText;
    /** AI 消息关联的题目（主问题与追问同源） */
    private Long questionId;
    /** 后端从回答中提取的关键词（M4 KeywordExtractor 填充） */
    @TableField(value = "keywords_json", typeHandler = JacksonTypeHandler.class)
    private List<String> keywords;
    private LocalDateTime createdAt;
}
