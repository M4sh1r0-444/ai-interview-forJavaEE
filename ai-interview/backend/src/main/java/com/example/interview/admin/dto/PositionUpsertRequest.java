package com.example.interview.admin.dto;

import com.example.interview.position.dto.CompetencyModel;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 岗位新增/修改请求（计划 §5.5 #28★）。修改为部分更新语义：
 * 非 null 字段才生效（name 仅新增时必填，Service 校验）；skills 非空时
 * 整体替换能力模型技能（scoreWeights 未提供则保留原值）。
 */
@Data
public class PositionUpsertRequest {

    /** 新增必填 2~50 唯一；创建后不可改（修改时忽略） */
    @Size(min = 2, max = 50, message = "code 长度 2~50")
    private String code;
    @Size(max = 100, message = "name 最长 100 字")
    private String name;
    @Size(max = 500, message = "description 最长 500 字")
    private String description;
    /** 3~8，与面试 rounds 上下界一致 */
    private Integer defaultRounds;
    /** 1 启用 0 停用；缺省 1 */
    private Integer enabled;
    /** 能力模型技能清单（新增必填；权重和=1，见 AdminPositionService 校验） */
    private List<CompetencyModel.SkillItem> skills;
    /** 评分权重（可选，结构见计划 §3.5；组内权重和=1） */
    private CompetencyModel.ScoreWeights scoreWeights;
}
