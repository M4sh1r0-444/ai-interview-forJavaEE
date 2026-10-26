-- ============================================================
-- V8：简历分析与个性化面试（简历方案 §5 / 简历计划 §3）
--   新增 2 张表（resume、resume_analysis，总量 11）+ interview_session 加 1 可空列。
--   基线 9 张表结构不做其他变更；存量库从 V7 增量执行，NULL 列对历史行透明。
-- ============================================================

-- ========== 10. 简历主表 ==========
CREATE TABLE resume (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    file_name     VARCHAR(255) NOT NULL COMMENT '原始文件名',
    stored_path   VARCHAR(500) NOT NULL COMMENT '相对 upload-dir 的存储路径 resume/yyyyMM/uuid.ext',
    file_type     VARCHAR(10)  NOT NULL COMMENT 'PDF / DOCX / MD / TXT',
    file_size     BIGINT       NOT NULL COMMENT '字节',
    position_id   BIGINT       NULL COMMENT '分析对照岗位（空=通用分析）',
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / ANALYZING / ANALYZED / FAILED',
    fail_reason   VARCHAR(500) NULL COMMENT '失败原因（[PARSE]/[AI] 前缀区分解析失败与分析失败）',
    char_count    INT          NOT NULL DEFAULT 0 COMMENT '抽取到的文本字符数',
    profile_json  JSON         NULL COMMENT '结构化抽取（脱敏后，不含联系方式）',
    digest_json   JSON         NULL COMMENT '个性化摘要（F2 消费：skills/projects/experiences/education）',
    is_active     TINYINT      NOT NULL DEFAULT 0 COMMENT '默认简历，每用户至多 1 条',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：文件保留审计，列表/引用不可见',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_resume_user (user_id, deleted),
    KEY idx_resume_position (position_id),
    CONSTRAINT fk_resume_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历';

-- ========== 11. 简历分析结果（一份简历至多一条，重分析事务内 delete + insert 覆盖） ==========
CREATE TABLE resume_analysis (
    id                    BIGINT      PRIMARY KEY AUTO_INCREMENT,
    resume_id             BIGINT      NOT NULL,
    provider              VARCHAR(20) NOT NULL COMMENT '实际产出结果的 Provider：mock / qwen',
    overall_score         INT         NOT NULL COMMENT '总分 0~100（六维加权四舍五入）',
    dimension_scores_json JSON        NOT NULL COMMENT '六维 {basic,education,skill,project,experience,expression}',
    strengths_json        JSON        NOT NULL COMMENT '亮点数组（≤5 条）',
    suggestions_json      JSON        NOT NULL COMMENT '[{riskLevel,dimension,title,issue,suggestion,evidence}] 3~8 条，HIGH 优先',
    summary_text          TEXT        NULL COMMENT '总评（≤500 字）',
    created_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_analysis_resume (resume_id),
    CONSTRAINT fk_analysis_resume FOREIGN KEY (resume_id) REFERENCES resume(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历分析结果';

-- ========== 既有表：面试会话关联简历（可空；空=普通面试，基线行为不变） ==========
ALTER TABLE interview_session
    ADD COLUMN resume_id BIGINT NULL COMMENT '个性化面试来源简历，空=普通面试',
    ADD KEY idx_session_resume (resume_id);
