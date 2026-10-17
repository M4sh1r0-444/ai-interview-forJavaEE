-- V7__acceptance_fix.sql（验收修复统一迁移，FIX-07/08/12）
-- 前置：迁移已在测试库 ai_interview_test 验证；dev 库已 mysqldump 备份。

-- 1) 报告逐题证据持久化（FIX-07）：生成时快照落库，读取不再调用 AI Provider；
--    V7 之前的旧报告为 NULL，读取回退确定性重算一次并回填。
ALTER TABLE evaluation_report
  ADD COLUMN question_evidence_json JSON NULL COMMENT '逐题证据快照（QuestionEvidenceVO[]）';

-- 2) 面试时长持久化（FIX-08）：创建时落库用户选择的时长，start 与重启恢复均读该列；
--    历史会话默认 30（原 15/60 选择无法恢复，文档注明）。
ALTER TABLE interview_session
  ADD COLUMN planned_duration_minutes INT NOT NULL DEFAULT 30 COMMENT '用户选择的面试时长';

-- 3) 掌握度幂等（FIX-12 前置）：先清理历史重复（保留最新 evaluated_at / id），再加唯一键；
--    source_session_id IS NULL 的记录不参与唯一冲突（MySQL 唯一索引允许多个 NULL）。
DELETE a1 FROM ability_record a1
JOIN ability_record a2
  ON a1.source_session_id = a2.source_session_id
 AND a1.skill_code = a2.skill_code
 AND (a1.evaluated_at < a2.evaluated_at
      OR (a1.evaluated_at = a2.evaluated_at AND a1.id < a2.id));

ALTER TABLE ability_record
  ADD UNIQUE KEY uk_ability_source_skill (source_session_id, skill_code);
