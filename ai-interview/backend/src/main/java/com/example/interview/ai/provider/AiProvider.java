package com.example.interview.ai.provider;

import com.example.interview.ai.model.AiNextQuestionResult;
import com.example.interview.ai.model.AiQuestionPersonalization;
import com.example.interview.ai.model.AiReport;
import com.example.interview.ai.model.AiResumeAnalysis;
import com.example.interview.ai.model.AiTurnEvaluation;
import com.example.interview.ai.model.NextQuestionContext;
import com.example.interview.ai.model.QuestionPersonalizeContext;
import com.example.interview.ai.model.ReportContext;
import com.example.interview.ai.model.ResumeAnalysisContext;
import com.example.interview.ai.model.TurnEvaluationContext;

/**
 * 大模型能力接口（方案 §6.6 三个方法 + 简历方案 §4.1 analyzeResume）。实现：
 * MockAiProvider（离线默认）、OpenAiCompatibleProvider（M4 接入）、RoutingAiProvider（V9 路由）。
 */
public interface AiProvider {

    /** 产出下一轮交互：追问或换下一题（决策由追问规则引擎指令 + 模型共同决定）。 */
    AiNextQuestionResult nextQuestion(NextQuestionContext ctx);

    /** 逐题评分（M3 链路接入）。 */
    AiTurnEvaluation evaluateTurn(TurnEvaluationContext ctx);

    /** 生成整体报告（M3 链路接入）。 */
    AiReport generateReport(ReportContext ctx);

    /**
     * F1 简历多维分析（R2-2）：六维评分 + 分级建议 + 亮点/总评。输入已 PII 脱敏；
     * 真实 Provider 内部沿用三级容错（重试 1 次 → 降级 Mock 规则），产出方在结果
     * provider 字段自报；两实现显式实现本方法（防漏实现静默失效，简历计划 §4.3）。
     */
    AiResumeAnalysis analyzeResume(ResumeAnalysisContext ctx);

    /**
     * F2 主问题题干个性化改写（R3-3，简历方案 §4.1）：一次批量改写本场主问题，
     * 仅绑定候选人背景、保留考查点，questionId 完全不变（评分基准 key_points 不变）。
     * 真实 Provider 失败链：重试 1 次 → 降级 Mock 模板；调用方（InterviewService.start）
     * 对最终失败降级整场原题干（all-or-nothing，WARN 不打断 start）。两实现显式实现
     * 本方法（防漏实现静默失效，简历计划 §4.3）。
     */
    AiQuestionPersonalization personalizeQuestions(QuestionPersonalizeContext ctx);

    /**
     * 会话终态/报告完成后释放该会话的 Provider 内部状态（FIX-16：
     * 真实 Provider 的失败计数与会话熔断锁）。默认无状态实现可忽略。
     */
    default void releaseSession(Long sessionId) {
        // 无状态 Provider（Mock）无需处理
    }
}
