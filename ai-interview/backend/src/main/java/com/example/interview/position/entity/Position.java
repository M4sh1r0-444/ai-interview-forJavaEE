package com.example.interview.position.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 2：岗位。`position` 是 MySQL 保留字，@TableName 反引号包裹（计划 §3.1/R4）。
 */
@Data
@TableName("`position`")
public class Position {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String description;
    /** 能力模型 + 评分权重 JSON（结构见计划 §3.5），Service 层解析 */
    private String competencyModelJson;
    private Integer defaultRounds;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
