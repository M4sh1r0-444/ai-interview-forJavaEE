package com.example.interview.ai.parser;

import com.example.interview.common.util.JsonUtils;
import org.springframework.stereotype.Component;

/**
 * AI 返回 JSON 容错解析（计划 §6.8 三级容错的第 1 级防线，风险 R9）：
 * 剥离 ```json 围栏 → 截取首个花括号平衡块 → Jackson 反序列化。
 * 任一步失败抛 {@link AiFallbackException}，由调用方走"重试 1 次 → 题目模板 → Mock 降级"。
 *
 * <p>花括号平衡扫描为简化实现（不感知字符串字面量内的花括号），
 * 覆盖大模型返回 markdown 围栏/前后缀文本的常见场景。</p>
 */
@Component
public class AiResponseParser {

    /**
     * @param raw  大模型原始返回文本
     * @param type 目标类型（字段校验由 Jackson + FAIL_ON_UNKNOWN_PROPERTIES=false 完成，
     *             必填字段校验由调用方在业务层进行）
     * @throws AiFallbackException 无法解析出合法 JSON
     */
    public <T> T parse(String raw, Class<T> type) {
        String json = extractJsonBlock(raw);
        try {
            return JsonUtils.parse(json, type);
        } catch (Exception e) {
            throw new AiFallbackException("AI 返回 JSON 反序列化失败: " + e.getMessage(), e);
        }
    }

    /** 提取 JSON 文本：优先剥围栏，其次截首个平衡 {...} 块；失败抛 AiFallbackException。 */
    String extractJsonBlock(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AiFallbackException("AI 返回为空");
        }
        String text = raw.trim();

        // 1. markdown 围栏：```json ... ``` 或 ``` ... ```
        if (text.contains("```")) {
            int start = text.indexOf("```");
            int lineBreak = text.indexOf('\n', start);
            int end = text.indexOf("```", lineBreak + 1);
            if (lineBreak >= 0 && end > lineBreak) {
                text = text.substring(lineBreak + 1, end).trim();
            }
        }

        // 2. 截取首个花括号平衡块（剥离围栏外的说明文字）
        int open = text.indexOf('{');
        if (open < 0) {
            throw new AiFallbackException("AI 返回中不含 JSON 对象");
        }
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(open, i + 1);
                }
            }
        }
        // 半截 JSON：扫到末尾深度未归零
        throw new AiFallbackException("AI 返回 JSON 不完整（花括号不平衡）");
    }
}
