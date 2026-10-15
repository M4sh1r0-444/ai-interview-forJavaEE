#!/usr/bin/env bash
# M6-6.4 Mock 离线（断网）演练编排（计划 §9-M6-6.4 完成标准：无网环境完整走通 面试→报告→推荐）
#
# 做四件事：
#   1) 停 dev 栈（正式栈与其共用 mysql-data 卷，两个 mysql 禁止同时运行，见进度记录 D29-④）
#   2) 以离线编排起五服务栈：核心服务（mysql/redis/backend/frontend）在 internal 网络，
#      对外 TCP/DNS 全断；nginx 挂边缘网作唯一入口（宿主机 80 端口可访问）
#   3) 断网证明（backend 容器内 /dev/tcp 探测 + AI_PROVIDER 确认）→ python 演练全流程
#   4) 收离线栈、恢复 dev 栈（数据在同一 mysql-data 卷，全程无损）
#
# 用法：bash qa/m6_offline_drill.sh [BASE_URL]（默认 http://127.0.0.1）
# 产物：qa/offline_drill_env.json（断网证明）+ qa/offline_drill_result.json（业务链路结果）
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PROD="docker compose -f docker-compose.yml -f docker-compose.offline.yml"
DEV="docker compose -f docker-compose.dev.yml"
BASE="${1:-http://127.0.0.1}"
ENV_JSON="qa/offline_drill_env.json"
FAIL=0

say()  { printf '\n[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
ok()   { echo "  ✅ $*"; }
bad()  { echo "  ❌ $*"; FAIL=1; }

restore_dev() {
  say "收离线栈并恢复 dev 栈（数据共用 mysql-data 卷，无损）"
  $PROD down --remove-orphans >/dev/null 2>&1
  $DEV up -d >/dev/null 2>&1
  for _ in $(seq 1 30); do
    if docker exec aiinterview-mysql mysqladmin ping -h localhost -proot123456 >/dev/null 2>&1; then
      echo "  dev 栈已恢复（aiinterview-mysql healthy）"
      return 0
    fi
    sleep 2
  done
  echo "  ⚠ dev 栈恢复等待超时，请手动 docker compose -f docker-compose.dev.yml up -d 检查"
}
trap restore_dev EXIT

# ---------- 1) 停 dev 栈 ----------
say "1/5 停 dev 栈（防双 mysql 共用 mysql-data 卷）"
if docker ps --format '{{.Names}}' | grep -q '^aiinterview-mysql$'; then
  $DEV stop && ok "dev 栈已停（mysql/redis 容器保留）" || bad "dev 栈停止失败"
else
  ok "dev 栈本就未运行"
fi

# ---------- 2) 起离线栈 ----------
say "2/5 起离线五服务栈（核心服务 internal 网络 + nginx 边缘入口，镜像均已本地化）"
if $PROD up -d 2>&1 | grep -Ev '^(Network|Volume).*(Created|Running)$'; then
  ok "离线栈已启动"
else
  bad "离线栈启动失败"; exit 1
fi

say "等待 backend 就绪（健康探测：登录接口）"
READY=0
for _ in $(seq 1 60); do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 \
    -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
    -d '{"username":"demo01","password":"123456"}' 2>/dev/null)
  if [ "$CODE" = "200" ]; then READY=1; break; fi
  sleep 2
done
[ "$READY" = "1" ] && ok "backend 就绪（经 Nginx ${BASE}）" || { bad "backend 120s 未就绪"; exit 1; }

# ---------- 3) 断网证明 ----------
say "3/5 断网证明（容器内实测，结果写入 ${ENV_JSON}）"
PROVIDER=$($PROD exec -T backend printenv AI_PROVIDER 2>/dev/null | tr -d '\r\n')
[ "$PROVIDER" = "mock" ] && ok "AI_PROVIDER=mock（Mock 规则引擎，无外部 AI 依赖）" \
                       || bad "AI_PROVIDER=${PROVIDER}（应为 mock）"

EGRESS=0
$PROD exec -T backend timeout 6 bash -c '</dev/tcp/223.5.5.5/443' >/dev/null 2>&1 && EGRESS=1
[ "$EGRESS" = "0" ] && ok "backend → 外网 IP(223.5.5.5:443) TCP 不通（出口已断）" \
                  || bad "backend 外网 IP 仍可达，未真正断网！"

DNS_OK=0
$PROD exec -T backend timeout 6 bash -c '</dev/tcp/www.baidu.com/443' >/dev/null 2>&1 && DNS_OK=1
[ "$DNS_OK" = "0" ] && ok "backend → 外网域名 DNS/链路不通" || bad "backend 仍可解析并访问外网域名！"

INT_MYSQL=0; INT_REDIS=0; INT_NGINX=0
$PROD exec -T backend timeout 5 bash -c '</dev/tcp/mysql/3306' >/dev/null 2>&1 && INT_MYSQL=1
$PROD exec -T backend timeout 5 bash -c '</dev/tcp/redis/6379' >/dev/null 2>&1 && INT_REDIS=1
$PROD exec -T nginx wget -T 5 -q -O /dev/null http://backend:8080/actuator/health 2>/dev/null && INT_NGINX=1
[ "$INT_MYSQL$INT_REDIS$INT_NGINX" = "111" ] \
  && ok "内网链路正常（backend→mysql/redis，nginx→backend）" \
  || bad "内网链路异常 mysql=$INT_MYSQL redis=$INT_REDIS nginx→backend=$INT_NGINX"

HOST_HTTP=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE/")
[ "$HOST_HTTP" = "200" ] && ok "宿主机经 Nginx 入口可访问（静态页 200）" \
                      || bad "宿主机访问 Nginx 失败（HTTP ${HOST_HTTP}）"

cat > "$ENV_JSON" <<EOF
{
  "stack": "docker-compose.yml + docker-compose.offline.yml",
  "isolation": "svc 网络 internal=true（mysql/redis/backend/frontend 零外网出口）；nginx 另挂 edge 网络仅作 80 端口入口",
  "aiProvider": "$PROVIDER",
  "backendEgressBlocked": $([ "$EGRESS" = "0" ] && echo true || echo false),
  "backendDnsBlocked": $([ "$DNS_OK" = "0" ] && echo true || echo false),
  "internalMysqlOk": $INT_MYSQL,
  "internalRedisOk": $INT_REDIS,
  "nginxToBackendOk": $INT_NGINX,
  "hostViaNginxHttp": "$HOST_HTTP",
  "verifiedAt": "$(date '+%Y-%m-%d %H:%M:%S')"
}
EOF

# ---------- 4) 业务链路演练 ----------
say "4/5 离线全流程演练（面试→报告→推荐，经 Nginx 入口）"
if python3 qa/m6_offline_drill.py "$BASE"; then
  ok "离线演练全部通过"
else
  bad "离线演练存在失败项（详见 qa/offline_drill_result.json）"
fi

# ---------- 5) 汇总 ----------
say "5/5 汇总"
if [ "$FAIL" = "0" ]; then
  echo "==== M6-6.4 离线演练：PASS（断网证明 + 面试→报告→推荐全链路离线可用）===="
else
  echo "==== M6-6.4 离线演练：FAIL，见上方 ❌ 项 ===="
fi
exit "$FAIL"
