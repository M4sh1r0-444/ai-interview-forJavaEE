package com.example.interview.admin.dto;

import com.example.interview.position.dto.CompetencyModel;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 岗位评分权重调整请求（计划 §5.5 #30★）：写入
 * competency_model_json.scoreWeights；提供的权重组须完整且组内和=1。
 */
@Data
public class WeightsUpdateRequest {

    @NotNull(message = "scoreWeights 必填")
    private CompetencyModel.ScoreWeights scoreWeights;
}
