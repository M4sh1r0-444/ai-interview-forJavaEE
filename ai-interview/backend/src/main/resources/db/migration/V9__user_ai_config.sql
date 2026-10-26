-- ============================================================
-- V9：用户级 AI 服务配置（个人中心「AI 服务配置」子页面）
--   新增 1 张表（user_ai_config，总量 12）：每用户至多一条，
--   保存用户自己的 OpenAI 兼容服务接入信息；api_key 以 AES-GCM
--   密文落库（v1:base64(iv+ct)，密钥 app.cipher-key / APP_CIPHER_KEY）。
--   存在即生效：RoutingAiProvider 优先路由到该端点，无配置走平台默认。
-- ============================================================

-- ========== 12. 用户级 AI 服务配置 ==========
CREATE TABLE user_ai_config (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    api_url         VARCHAR(500) NOT NULL COMMENT 'OpenAI 兼容接口地址（如 https://dashscope.aliyuncs.com/compatible-mode/v1）',
    api_key_cipher  VARCHAR(1000) NOT NULL COMMENT 'API Key 密文（AES-256-GCM，格式 v1:base64(iv+ct)）',
    model           VARCHAR(100) NOT NULL COMMENT '模型名（如 qwen-plus / deepseek-chat）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_ai_config (user_id),
    CONSTRAINT fk_user_ai_config_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户级 AI 服务配置（每人至多一条，存在即生效）';
