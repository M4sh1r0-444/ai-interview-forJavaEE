package com.example.interview.ai.model;

import lombok.Data;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * personalizeQuestions 的输出（简历方案 §4.1，R3-3）：
 * items 与输入题目一一对应（questionId 完全一致）。
 * all-or-nothing 契约由 {@link #isValidFor} 统一校验：
 * Provider 侧校验失败降级 Mock，Service 侧复核失败整场使用原题干。
 */
@Data
public class AiQuestionPersonalization {

    private List<Item> items;

    @Data
    public static class Item {
        private Long questionId;
        private String content;
    }

    /** 改写题干长度约束（协议 §4.2：每条 10~500 字） */
    public static final int MIN_CONTENT_CHARS = 10;
    public static final int MAX_CONTENT_CHARS = 500;

    /**
     * questionId 集合与输入完全一致（数量相等且无非法/重复 ID）、
     * 每条 content 10~500 字（去首尾空白后计长）。
     */
    public boolean isValidFor(Collection<Long> expectedQuestionIds) {
        if (expectedQuestionIds == null || items == null || items.size() != expectedQuestionIds.size()) {
            return false;
        }
        Set<Long> expected = new HashSet<>(expectedQuestionIds);
        for (Item item : items) {
            if (item == null || item.getQuestionId() == null || !expected.contains(item.getQuestionId())) {
                return false;
            }
            String c = item.getContent();
            if (c == null || c.strip().length() < MIN_CONTENT_CHARS || c.length() > MAX_CONTENT_CHARS) {
                return false;
            }
        }
        return true;
    }
}
