package com.example.interview.resume.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 改进建议（简历协议 R-04 suggestions[] 元素 / 简历方案 §3.4）。
 * riskLevel：HIGH（维度分 &lt;40，必须修改）/ MEDIUM（40~70，建议优化）/ LOW（≥70 亮点保持）。
 * 既作 resume_analysis.suggestions_json 的落库结构，也直接作为接口返回元素。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SuggestionDTO {

    /** HIGH / MEDIUM / LOW */
    private String riskLevel;
    /** 六维 key：basic / education / skill / project / experience / expression */
    private String dimension;
    /** 一句话标题（≤20 字） */
    private String title;
    /** 问题描述（引用证据说明为什么是问题） */
    private String issue;
    /** 具体可执行的修改建议 */
    private String suggestion;
    /** 简历原文证据句（脱敏后），LOW 级亮点项可为空 */
    private String evidence;
}
