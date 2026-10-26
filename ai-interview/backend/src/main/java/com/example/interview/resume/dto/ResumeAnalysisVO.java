package com.example.interview.resume.dto;

import com.example.interview.resume.entity.ResumeAnalysis;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 简历分析结果（简历协议 R-04 data；R-03 详情内嵌 analysis 结构相同）。
 * dimensionScores 六维 0~100；suggestions 3~8 条按 HIGH→MEDIUM→LOW 排序。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResumeAnalysisVO {

    private Long resumeId;
    /** mock / qwen（实际产出结果的 Provider） */
    private String provider;
    private Integer overallScore;
    /** {basic, education, skill, project, experience, expression} */
    private Map<String, Integer> dimensionScores;
    /** 亮点（≤5 条） */
    private List<String> strengths;
    private List<SuggestionDTO> suggestions;
    /** 总评（≤500 字） */
    private String summary;
    private LocalDateTime createdAt;

    public static ResumeAnalysisVO from(ResumeAnalysis a) {
        return ResumeAnalysisVO.builder()
                .resumeId(a.getResumeId())
                .provider(a.getProvider())
                .overallScore(a.getOverallScore())
                .dimensionScores(a.getDimensionScores())
                .strengths(a.getStrengths())
                .suggestions(a.getSuggestions())
                .summary(a.getSummaryText())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
