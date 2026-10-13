package com.example.interview.position.dto;

import lombok.Data;

import java.util.List;

/**
 * competency_model_json 结构（计划 §3.5）。
 */
@Data
public class CompetencyModel {

    private List<SkillItem> skills;
    private ScoreWeights scoreWeights;

    @Data
    public static class SkillItem {
        private String skillCode;
        private String skillName;
        private String description;
        private Double weight;
        private Integer requiredLevel;
    }

    @Data
    public static class ScoreWeights {
        private DimensionWeights content;
        private DimensionWeights expression;
        private TotalWeights total;
    }

    @Data
    public static class DimensionWeights {
        private Double technical;
        private Double depth;
        private Double logic;
        private Double evidence;
        private Double match;
        private Double clarity;
        private Double fluency;
        private Double pace;
        private Double confidence;
        private Double completeness;
    }

    @Data
    public static class TotalWeights {
        private Double content;
        private Double expression;
        private Double adaptability;
        private Double match;
    }
}
