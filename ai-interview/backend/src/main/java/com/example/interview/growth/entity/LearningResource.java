package com.example.interview.growth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 9：学习资源（计划 §3.2）。skill_code 与能力模型对齐，
 * 推荐引擎按 (position_id, skill_code, enabled) 匹配（计划 §6.7）。
 */
@Data
@TableName("learning_resource")
public class LearningResource {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long positionId;
    private String skillCode;
    private String title;
    /** ARTICLE / QUESTION / PRACTICE / PROJECT */
    private String resourceType;
    private String contentUrl;
    /** 1~5 */
    private Integer difficulty;
    private Integer enabled;
    private LocalDateTime createdAt;
}
