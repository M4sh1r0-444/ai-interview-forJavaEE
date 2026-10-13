package com.example.interview.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 表 3：题目。JSON 列用 JacksonTypeHandler 映射为 List&lt;String&gt;（计划 §4.4）。
 */
@Data
@TableName(value = "question", autoResultMap = true)
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long positionId;
    /** TECHNICAL / PROJECT / SCENARIO / BEHAVIORAL */
    private String type;
    private String content;
    private String background;
    private Integer difficulty;
    @TableField(value = "skill_tags_json", typeHandler = JacksonTypeHandler.class)
    private List<String> skillTags;
    private String referenceAnswer;
    @TableField(value = "key_points_json", typeHandler = JacksonTypeHandler.class)
    private List<String> keyPoints;
    @TableField(value = "deduction_points_json", typeHandler = JacksonTypeHandler.class)
    private List<String> deductionPoints;
    @TableField(value = "followup_templates_json", typeHandler = JacksonTypeHandler.class)
    private List<String> followupTemplates;
    /** ENABLED / DISABLED（逻辑删） */
    private String status;
    /** 管理端修改题目的乐观锁版本号 */
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
