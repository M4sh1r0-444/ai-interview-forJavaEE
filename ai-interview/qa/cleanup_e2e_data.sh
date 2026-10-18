#!/usr/bin/env bash
# F6-6 / P17 / P21：清理 E2E、契约检查与历史冒烟测试残留，恢复可演示数据。
#
# 清理范围（只命中测试前缀，不触碰 demo01 种子数据）：
#   - sys_user：e2e_/acc_/race_/dis_/shots2_ 前缀账号
#   - interview_session：上述测试账号的会话 + demo01 在 M6 基线（id ≤ 21）之后新增的会话
#   - 关联的 interview_message / evaluation_report / ability_record
#   - position：E2E_/ACC_ 前缀岗位（测试岗位已停用/无引用）
#   - question：E2E- 前缀题目；恢复被历史测试误停用的种子题 61
#   - knowledge_chunk：E2E-/离线演练- 前缀片段
#   - learning_resource：E2E- 前缀与“冒烟：”前缀资源
#   - Redis：rec:* 推荐缓存、rl:login:* 限流计数
#
# 前置：开发栈 MySQL/Redis 容器已启动（docker-compose.dev.yml）；默认容器名见下。
# 用法：bash qa/cleanup_e2e_data.sh
#       MYSQL_CONTAINER=xxx REDIS_CONTAINER=yyy bash qa/cleanup_e2e_data.sh
set -euo pipefail

MYSQL_CONTAINER="${MYSQL_CONTAINER:-aiinterview-mysql}"
REDIS_CONTAINER="${REDIS_CONTAINER:-aiinterview-redis}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root123456}"
MYSQL_DATABASE="${MYSQL_DATABASE:-ai_interview}"

if ! docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER"; then
  echo "❌ MySQL 容器未运行：$MYSQL_CONTAINER（先 docker compose -f docker-compose.dev.yml up -d）" >&2
  exit 1
fi

mysql_exec() {
  docker exec -i "$MYSQL_CONTAINER" mysql --default-character-set=utf8mb4 \
    -uroot -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" "$@"
}

echo "== 清理前 =="
mysql_exec -N -e "
SELECT CONCAT('岗位 ', COUNT(*)) FROM position;
SELECT CONCAT('题目 ', COUNT(*)) FROM question;
SELECT CONCAT('资源 ', COUNT(*)) FROM learning_resource;
SELECT CONCAT('知识片段 ', COUNT(*)) FROM knowledge_chunk;
SELECT CONCAT('用户 ', COUNT(*)) FROM sys_user;
SELECT CONCAT('面试场次 ', COUNT(*)) FROM interview_session;
"

echo "== 删除测试会话及关联数据 =="
mysql_exec <<'SQL'
START TRANSACTION;

CREATE TEMPORARY TABLE tmp_e2e_users AS
SELECT id FROM sys_user
WHERE username LIKE 'e2e\_%'
   OR username LIKE 'acc\_%'
   OR username LIKE 'race\_%'
   OR username LIKE 'dis\_%'
   OR username LIKE 'shots2\_%';

CREATE TEMPORARY TABLE tmp_e2e_sessions AS
SELECT id FROM interview_session
WHERE user_id IN (SELECT id FROM tmp_e2e_users)
   OR (user_id = 2 AND id > 21);

DELETE FROM ability_record
WHERE source_session_id IN (SELECT id FROM tmp_e2e_sessions)
   OR user_id IN (SELECT id FROM tmp_e2e_users);

DELETE FROM evaluation_report
WHERE session_id IN (SELECT id FROM tmp_e2e_sessions);

DELETE FROM interview_message
WHERE session_id IN (SELECT id FROM tmp_e2e_sessions);

DELETE FROM interview_session
WHERE id IN (SELECT id FROM tmp_e2e_sessions);

DELETE FROM question
WHERE content LIKE 'E2E-%' OR content LIKE 'E2E\_%';

DELETE FROM knowledge_chunk
WHERE title LIKE 'E2E-%' OR title LIKE 'E2E\_%' OR title LIKE '离线演练-%';

DELETE FROM learning_resource
WHERE title LIKE 'E2E-%' OR title LIKE 'E2E\_%' OR title LIKE '冒烟：%';

DELETE FROM position
WHERE code LIKE 'E2E\_%' OR code LIKE 'ACC\_%';

DELETE FROM sys_user
WHERE id IN (SELECT id FROM tmp_e2e_users);

-- 历史 E2E 曾把种子题逻辑删；恢复为启用（版本不变，仅状态）
UPDATE question SET status = 'ENABLED' WHERE id = 61 AND status = 'DISABLED';

COMMIT;
SQL

if docker ps --format '{{.Names}}' | grep -qx "$REDIS_CONTAINER"; then
  echo "== 清理 Redis 推荐缓存与登录限流 =="
  KEYS=$(docker exec "$REDIS_CONTAINER" redis-cli --scan --pattern 'rec:*' || true)
  if [ -n "$KEYS" ]; then
    # shellcheck disable=SC2086
    docker exec "$REDIS_CONTAINER" redis-cli DEL $KEYS >/dev/null
  fi
  docker exec "$REDIS_CONTAINER" redis-cli DEL rl:login:127.0.0.1 rl:login:0:0:0:0:0:0:0:1 >/dev/null 2>&1 || true
fi

echo "== 清理后 =="
mysql_exec -N -e "
SELECT CONCAT('岗位 ', COUNT(*)) FROM position;
SELECT CONCAT('题目 ', COUNT(*)) FROM question;
SELECT CONCAT('资源 ', COUNT(*)) FROM learning_resource;
SELECT CONCAT('知识片段 ', COUNT(*)) FROM knowledge_chunk;
SELECT CONCAT('用户 ', COUNT(*)) FROM sys_user;
SELECT CONCAT('面试场次 ', COUNT(*)) FROM interview_session;
SELECT CONCAT('demo01 历史 ', COUNT(*)) FROM interview_session WHERE user_id = 2;
SELECT CONCAT('启用岗位 ', COUNT(*)) FROM position WHERE enabled = 1;
"

echo "✅ 清理完成。演示账号：demo01/123456、admin/Admin@123456"
