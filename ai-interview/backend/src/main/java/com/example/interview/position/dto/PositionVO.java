package com.example.interview.position.dto;

import lombok.Builder;
import lombok.Data;

/** 岗位列表/详情视图（计划 §5.2）。 */
@Data
@Builder
public class PositionVO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer defaultRounds;
    /** 详情时返回：能力模型技能数 */
    private Integer skillCount;
}
