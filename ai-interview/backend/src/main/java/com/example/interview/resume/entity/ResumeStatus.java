package com.example.interview.resume.entity;

/**
 * 简历状态机（简历方案 §3.5）：
 * <pre>
 * PENDING ──解析成功──→ ANALYZING ──AI成功──→ ANALYZED
 *    │                    │                      │
 *    └─解析失败→ FAILED ←─┘（AI 失败）            └─ reanalyze → ANALYZING（覆盖）
 *                   └─ reanalyze → ANALYZING
 * </pre>
 * PENDING 为上传事务内瞬态，接口侧基本不可见。
 */
public enum ResumeStatus {
    PENDING,
    ANALYZING,
    ANALYZED,
    FAILED;

    /** 可发起重分析的状态（简历协议 R-05）。 */
    public boolean canReanalyze() {
        return this == FAILED || this == ANALYZED;
    }
}
