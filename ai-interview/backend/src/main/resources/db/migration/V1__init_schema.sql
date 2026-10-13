-- ============================================================
-- V1：9 张表完整 DDL（计划 §3.2，字段与方案 §9 字段清单逐一对应）
-- ============================================================

-- ========== 1. 用户表（来自方案 §9.1，原文照抄 + 补充索引） ==========
CREATE TABLE sys_user (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    real_name     VARCHAR(50),
    role          VARCHAR(20)  NOT NULL DEFAULT 'STUDENT',   -- STUDENT / ADMIN
    major         VARCHAR(100),
    status        TINYINT      NOT NULL DEFAULT 1,           -- 1启用 0禁用
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 2. 岗位表 ==========
CREATE TABLE `position` (
    id                   BIGINT       PRIMARY KEY AUTO_INCREMENT,
    code                 VARCHAR(50)  NOT NULL UNIQUE,        -- JAVA_BACKEND / WEB_FRONTEND
    name                 VARCHAR(100) NOT NULL,
    description          VARCHAR(500),
    competency_model_json JSON,                               -- 能力模型 + 评分权重，结构见计划 §3.5
    default_rounds       INT          NOT NULL DEFAULT 6,     -- 默认主问题数 5~8
    enabled              TINYINT      NOT NULL DEFAULT 1,
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 3. 题目表 ==========
CREATE TABLE question (
    id                       BIGINT        PRIMARY KEY AUTO_INCREMENT,
    position_id              BIGINT        NOT NULL,
    type                     VARCHAR(20)   NOT NULL,          -- TECHNICAL/PROJECT/SCENARIO/BEHAVIORAL
    content                  VARCHAR(1000) NOT NULL,
    background               VARCHAR(1000),                   -- 题目背景描述（可空）
    difficulty               TINYINT       NOT NULL DEFAULT 3,-- 1~5
    skill_tags_json          JSON          NOT NULL,          -- ["redis","缓存"]
    reference_answer         TEXT,                           -- 参考答案
    key_points_json          JSON,                           -- 关键得分点
    deduction_points_json    JSON,                           -- 常见失分点
    followup_templates_json  JSON,                           -- 预设追问模板（AI不可用时的兜底）
    status                   VARCHAR(20)   NOT NULL DEFAULT 'ENABLED',  -- ENABLED/DISABLED
    version                  INT           NOT NULL DEFAULT 1,-- 管理端修改题目的乐观锁版本号
    created_at               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_question_select (position_id, type, difficulty, status),
    CONSTRAINT fk_question_position FOREIGN KEY (position_id) REFERENCES `position`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 4. 面试会话表 ==========
CREATE TABLE interview_session (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL,
    position_id     BIGINT        NOT NULL,
    mode            VARCHAR(20)   NOT NULL DEFAULT 'TEXT',    -- TEXT / AUDIO
    difficulty      TINYINT       NOT NULL DEFAULT 3,
    planned_rounds  INT           NOT NULL DEFAULT 6,         -- 计划主问题数 5~8
    current_round   INT           NOT NULL DEFAULT 0,         -- 当前主问题序号（0-based，追问不+1）
    status          VARCHAR(20)   NOT NULL DEFAULT 'CREATED', -- 状态机见计划 §6.1
    total_score     DECIMAL(5,2),                             -- 结束后回填
    started_at      DATETIME,
    finished_at     DATETIME,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_session_user (user_id, status),
    KEY idx_session_position (position_id),
    CONSTRAINT fk_session_user     FOREIGN KEY (user_id)    REFERENCES sys_user(id),
    CONSTRAINT fk_session_position FOREIGN KEY (position_id) REFERENCES `position`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 5. 面试消息表 ==========
CREATE TABLE interview_message (
    id                 BIGINT       PRIMARY KEY AUTO_INCREMENT,
    session_id         BIGINT       NOT NULL,
    round_no           INT          NOT NULL,                 -- 所属主问题轮次（0-based）
    speaker            VARCHAR(10)  NOT NULL,                 -- AI / USER / SYSTEM
    input_type         VARCHAR(10),                           -- TEXT / AUDIO（仅 USER 消息有值）
    content_text       TEXT,                                  -- 文字内容（AI问题或学生文字回答）
    audio_path         VARCHAR(255),                          -- 音频相对路径 uploads/audio/...
    audio_duration_ms  INT,
    asr_text           TEXT,                                  -- 前端 Web Speech API 转写文本
    question_id        BIGINT,                                -- AI 消息关联的题目/追问来源题
    keywords_json      JSON,                                  -- 后端从回答中提取的关键词（供检索与报告）
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_message_session (session_id, round_no, speaker),
    CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES interview_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 6. 知识片段表（轻量 RAG 数据源） ==========
CREATE TABLE knowledge_chunk (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    position_id BIGINT       NOT NULL,
    title       VARCHAR(200) NOT NULL,
    content     TEXT         NOT NULL,                        -- 300~800 字知识片段
    keywords    VARCHAR(500) NOT NULL,                        -- 逗号分隔；VARCHAR 而非 JSON，为建全文索引
    skill_tags  VARCHAR(200) NOT NULL,                        -- 逗号分隔技能编码，与能力模型 skill_code 对齐
    source_type VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',       -- MANUAL / DOC_SLICE
    status      VARCHAR(20)  NOT NULL DEFAULT 'ENABLED',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 中文全文检索：ngram 解析器 + 联合索引（MATCH 列必须同属一个索引）
    FULLTEXT KEY ft_chunk_text (content, keywords) WITH PARSER ngram,
    KEY idx_chunk_position (position_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 7. 评估报告表 ==========
CREATE TABLE evaluation_report (
    id                    BIGINT        PRIMARY KEY AUTO_INCREMENT,
    session_id            BIGINT        NOT NULL UNIQUE,
    total_score           DECIMAL(5,2)  NOT NULL,
    dimension_scores_json JSON          NOT NULL,            -- 六维得分 {"technical":82,...}
    strengths_json        JSON,                              -- 亮点数组
    weaknesses_json       JSON,                              -- 不足数组
    improvement_plan_json JSON,                              -- 改进建议/练习计划数组
    report_text           TEXT,                              -- AI 生成的整体评语
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_session FOREIGN KEY (session_id) REFERENCES interview_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 8. 能力记录表 ==========
CREATE TABLE ability_record (
    id                BIGINT        PRIMARY KEY AUTO_INCREMENT,
    user_id           BIGINT        NOT NULL,
    position_id       BIGINT        NOT NULL,
    skill_code        VARCHAR(50)   NOT NULL,                  -- 对应能力模型 skill_code
    score             DECIMAL(5,2)  NOT NULL,                  -- 该技能本次掌握度（0~100）
    source_session_id BIGINT,
    evaluated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ability_query (user_id, position_id, skill_code, evaluated_at),
    CONSTRAINT fk_ability_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 9. 学习资源表 ==========
CREATE TABLE learning_resource (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    position_id   BIGINT       NOT NULL,
    skill_code    VARCHAR(50)  NOT NULL,
    title         VARCHAR(200) NOT NULL,
    resource_type VARCHAR(20)  NOT NULL,                      -- ARTICLE / QUESTION / PRACTICE / PROJECT
    content_url   VARCHAR(500),
    difficulty    TINYINT      NOT NULL DEFAULT 2,
    enabled       TINYINT      NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_resource_match (position_id, skill_code, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
