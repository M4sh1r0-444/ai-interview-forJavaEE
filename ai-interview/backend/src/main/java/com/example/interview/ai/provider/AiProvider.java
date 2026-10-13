package com.example.interview.ai.provider;

import com.example.interview.ai.model.AiNextQuestionResult;
import com.example.interview.ai.model.AiReport;
import com.example.interview.ai.model.AiTurnEvaluation;
import com.example.interview.ai.model.NextQuestionContext;
import com.example.interview.ai.model.ReportContext;
import com.example.interview.ai.model.TurnEvaluationContext;

/**
 * 大模型能力接口（方案 §6.6 三个方法）。实现：MockAiProvider（离线默认）、
 * OpenAiCompatibleProvider（M4 接入）。
 */
public interface AiProvider {

    /** 产出下一轮交互：追问或换下一题（决策由追问规则引擎指令 + 模型共同决定）。 */
    AiNextQuestionResult nextQuestion(NextQuestionContext ctx);

    /** 逐题评分（M3 链路接入）。 */
    AiTurnEvaluation evaluateTurn(TurnEvaluationContext ctx);

    /** 生成整体报告（M3 链路接入）。 */
    AiReport generateReport(ReportContext ctx);
}
