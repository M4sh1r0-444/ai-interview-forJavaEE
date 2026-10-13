package com.example.interview.interview.websocket;

/**
 * WebSocket 下行消息类型（计划 §5.6 / 方案 §10.6）。
 */
public enum WsMessageType {
    /** 主问题 */
    QUESTION,
    /** 追问 */
    FOLLOW_UP,
    /** 语音转写回显（M4） */
    TRANSCRIPT,
    /** 剩余时间推送 */
    TIMER,
    /** 面试进度（轮次推进 / 终态通知） */
    PROGRESS,
    /** 报告就绪（M3） */
    REPORT_READY,
    /** 错误/降级提示 */
    ERROR
}
