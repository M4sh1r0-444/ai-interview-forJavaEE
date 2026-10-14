package com.example.interview.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * OpenAI 兼容 Chat API 响应（计划 §1.2：qwen DashScope 兼容模式），
 * 仅映射需要的字段，忽略其余（容错）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiChatResponse {

    private String id;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    public String firstContent() {
        if (choices == null || choices.isEmpty() || choices.get(0).getMessage() == null) {
            return null;
        }
        return choices.get(0).getMessage().getContent();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Message message;
        private String finishReason;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
