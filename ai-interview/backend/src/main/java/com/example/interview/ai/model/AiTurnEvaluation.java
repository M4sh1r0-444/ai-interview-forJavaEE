package com.example.interview.ai.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AiProvider.evaluateTurn 输出（计划 §6.6 逐题评分 JSON 形态）。
 * M3 评分链路接入使用；scoreWeights 六维编码：technical/depth/logic/match/communication/voice。
 */
@Data
public class AiTurnEvaluation {

    /** 六维（或其子集）得分 0~100 */
    private Map<String, Integer> scores;
    private List<String> coveredPoints;
    private List<String> missingPoints;
    private List<String> evidence;
    private String reason;
    private List<String> targetSkills;
    /** AI 判断回答过于模糊（追问引擎规则 2 的 AI 侧输入） */
    private Boolean responseTooVague;
}
