package com.example.interview.ai.parser;

/**
 * AI 返回不可解析（三级容错第 3 级触发条件），调用方应降级到题目模板/Mock（计划 §6.8）。
 */
public class AiFallbackException extends RuntimeException {

    public AiFallbackException(String message) {
        super(message);
    }

    public AiFallbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
