package com.example.interview.ai.model;

/**
 * 一次 AI 调用的目标端点（值对象）：OpenAI 兼容接口地址 + API Key + 模型名。
 * 全局默认端点由 AiProperties 派生（AiConfig 装配），用户级端点由
 * UserAiConfigService.resolveEndpoint 解密得到（V9）。同时作为
 * AiProviderFactory 的缓存键——record 的 equals/hashCode 即配置组合。
 */
public record AiEndpoint(String apiUrl, String apiKey, String model) {
}
