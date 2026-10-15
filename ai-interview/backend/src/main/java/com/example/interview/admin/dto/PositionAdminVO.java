package com.example.interview.admin.dto;

import com.example.interview.position.dto.CompetencyModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端岗位视图（计划 §5.5 #28★）：比学生侧多 enabled/updatedAt，
 * 并带解析后的完整能力模型与评分权重（#30 调整结果可回读）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionAdminVO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer defaultRounds;
    private Integer enabled;
    private Integer skillCount;
    private List<CompetencyModel.SkillItem> skills;
    private CompetencyModel.ScoreWeights scoreWeights;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
