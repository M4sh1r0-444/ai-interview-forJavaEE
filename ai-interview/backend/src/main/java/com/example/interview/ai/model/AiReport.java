package com.example.interview.ai.model;

import lombok.Data;

import java.util.List;

/**
 * AiProvider.generateReport 输出（计划 §6.6 报告生成 JSON 形态）。M3 接入使用。
 */
@Data
public class AiReport {

    private String summary;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> improvementPlan;
    private String reportText;
}
