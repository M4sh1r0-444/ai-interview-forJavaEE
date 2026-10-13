package com.example.interview.position.dto;

import com.example.interview.position.dto.CompetencyModel.SkillItem;
import lombok.Builder;
import lombok.Data;

/** 岗位技能视图（计划 §5.2-8）。 */
@Data
@Builder
public class SkillVO {

    private String skillCode;
    private String skillName;
    private String description;
    private Double weight;
    private Integer requiredLevel;

    public static SkillVO from(SkillItem item) {
        return SkillVO.builder()
                .skillCode(item.getSkillCode())
                .skillName(item.getSkillName())
                .description(item.getDescription())
                .weight(item.getWeight())
                .requiredLevel(item.getRequiredLevel())
                .build();
    }
}
