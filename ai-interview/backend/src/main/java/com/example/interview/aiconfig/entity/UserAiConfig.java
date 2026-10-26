package com.example.interview.aiconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 12：用户级 AI 服务配置（V9，个人中心「AI 服务配置」）。每用户至多一条
 * （uk_user_ai_config），存在即生效：RoutingAiProvider 优先路由到该端点。
 * api_key 只存 AES-GCM 密文，任何出参仅回掩码。
 */
@Data
@TableName("user_ai_config")
public class UserAiConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** OpenAI 兼容接口地址（不含 /chat/completions 后缀） */
    private String apiUrl;
    /** API Key 密文（v1:base64(iv+ct)） */
    private String apiKeyCipher;
    /** 模型名（如 qwen-plus） */
    private String model;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
