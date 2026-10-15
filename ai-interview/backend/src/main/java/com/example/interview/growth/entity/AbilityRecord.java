package com.example.interview.growth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 表 8：能力记录。只追加不更新（计划 §3.8）——每次面试结束为每个命中的
 * skill_code 插入一条；"当前掌握度" = 该 (user, position, skill) 按
 * evaluated_at 最新一条的 score。
 */
@Data
@TableName("ability_record")
public class AbilityRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long positionId;
    /** 对应能力模型 competency_model_json.skills[].skillCode */
    private String skillCode;
    /** 本次掌握度 0~100 */
    private BigDecimal score;
    /** 产生该记录的面试会话 */
    private Long sourceSessionId;
    private LocalDateTime evaluatedAt;
}
