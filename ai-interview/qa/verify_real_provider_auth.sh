#!/usr/bin/env bash
# verify_real_provider_auth.sh —— FIX-20：本地要求 Bearer 的 stub 冒烟（D5：stub 必跑）。
#
# 证明链：AI_PROVIDER=qwen 指向本地 stub（要求 Authorization: Bearer <key>）→
#   提交一次回答 → 逐题评分/追问/报告三路径全部由 stub 产出（消息与报告含 stub 标记）→
#   stub 侧断言每个请求的 Authorization 精确等于 "Bearer <key>"。
# 真实 qwen key 的成功调用是 15/15 的最终条件（另行人工执行，不写入本脚本/仓库）。
#
# 用法：bash qa/verify_real_provider_auth.sh
# 依赖：colima 在跑（dev MySQL 3306/Redis 6380）、JDK17、python3、18082/19091 端口空闲。
set -euo pipefail
cd "$(dirname "$0")/.."

STUB_PORT=19091
BACKEND_PORT=18082
STUB_KEY="stub-key-e2e-123456"
STUB_DB="ai_interview_stub"
STUB_LOG=/tmp/aiinterview_stub_server.log
BACKEND_LOG=/tmp/aiinterview_stub_backend.log
JAVA_HOME="${JAVA_HOME:-$HOME/tools/jdk-17.0.20.1+1/Contents/Home}"

cleanup() {
  [ -n "${BACKEND_PID:-}" ] && kill "$BACKEND_PID" 2>/dev/null || true
  [ -n "${STUB_PID:-}" ] && kill "$STUB_PID" 2>/dev/null || true
  sleep 1
  docker exec aiinterview-mysql mysql -uroot -proot123456 -e "DROP DATABASE IF EXISTS ${STUB_DB};" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# ---------- 1) 本地鉴权 stub：要求精确 Bearer；按 Prompt 类型返回可断言标记 ----------
python3 - "$STUB_PORT" "$STUB_KEY" > "$STUB_LOG" 2>&1 <<'PY' &
import json, sys
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT, KEY = int(sys.argv[1]), sys.argv[2]
AUTH_SEEN = []

class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8", "replace")
        auth = self.headers.get("Authorization")
        AUTH_SEEN.append(auth)
        with open("/tmp/aiinterview_stub_auth.log", "a") as f:
            f.write(auth + "\n")
        if auth != "Bearer " + KEY:
            self.send_response(401)
            self.end_headers()
            self.wfile.write(b'{"error":"unauthorized"}')
            return
        req = json.loads(body)
        prompt = req["messages"][1]["content"]
        if "评分官" in prompt:      # evaluateTurn
            content = {"scores": {"technical": 82, "depth": 78, "logic": 76, "match": 80, "communication": 79},
                       "coveredPoints": ["stub 覆盖点"], "missingPoints": [], "evidence": ["stub 证据句"],
                       "reason": "stub-eval", "targetSkills": ["java_basics"], "responseTooVague": False}
        elif "评估专家" in prompt:   # generateReport
            content = {"summary": "【stub】报告摘要标记：协议级鉴权成功",
                       "strengths": ["stub 亮点"], "weaknesses": [], "improvementPlan": ["stub 建议"],
                       "reportText": "stub 报告正文，仅证明真实调用链路贯通。"}
        else:                        # nextQuestion（技术面试官）
            content = {"action": "FOLLOW_UP", "content": "【stub】追问内容标记：请结合项目举例说明。",
                       "difficulty": 3, "reason": "stub-question", "targetSkills": ["java_basics"]}
        resp = json.dumps({"choices": [{"message": {"role": "assistant", "content": json.dumps(content, ensure_ascii=False)}}]},
                          ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(resp)))
        self.end_headers()
        self.wfile.write(resp)

HTTPServer(("127.0.0.1", PORT), H).serve_forever()
PY
STUB_PID=$!
rm -f /tmp/aiinterview_stub_auth.log
sleep 1
echo "== 1) 鉴权 stub 已启动 :${STUB_PORT}（要求 Bearer 精确匹配）"

# ---------- 2) 独立库 + 独立 Redis db 起后端（qwen 模式指向 stub） ----------
docker exec aiinterview-mysql mysql -uroot -proot123456 -e "DROP DATABASE IF EXISTS ${STUB_DB};"
export JAVA_HOME
cd backend
AI_PROVIDER=qwen AI_API_URL="http://127.0.0.1:${STUB_PORT}/v1" AI_API_KEY="$STUB_KEY" \
SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/${STUB_DB}?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai" \
SPRING_DATA_REDIS_PORT=6380 SPRING_DATA_REDIS_DATABASE=5 \
SERVER_PORT=$BACKEND_PORT UPLOAD_DIR=/tmp/aiinterview_stub_uploads \
nohup mvn -q spring-boot:run > "$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!
cd ..
echo "== 2) 后端以 qwen 模式启动 :${BACKEND_PORT}（独立库 ${STUB_DB} / Redis db5）"

for i in $(seq 1 60); do
  curl -s -o /dev/null --max-time 3 "http://127.0.0.1:${BACKEND_PORT}/actuator/health" | grep -q UP && break
  sleep 2
done
curl -s --max-time 3 "http://127.0.0.1:${BACKEND_PORT}/actuator/health" | grep -q UP || { echo "❌ 后端 120s 未就绪，见 $BACKEND_LOG"; exit 1; }
echo "== 3) 后端就绪（Flyway 全新迁移）"

B="http://127.0.0.1:${BACKEND_PORT}"
USER="stub_$(date +%s)"
TOKEN=$(curl -s -X POST "$B/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"password\":\"Stub@123456\",\"realName\":\"stub 冒烟\",\"major\":\"软件工程\"}" >/dev/null; \
  curl -s -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"password\":\"Stub@123456\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["token"])')
AUTH="Authorization: Bearer $TOKEN"

SID=$(curl -s -X POST "$B/api/interviews" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"positionId":1,"mode":"TEXT","difficulty":3,"rounds":5,"durationMinutes":15}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["id"])')
curl -s -X POST "$B/api/interviews/$SID/start" -H "$AUTH" >/dev/null
echo "== 4) 会话 $SID 已开始，提交一次回答"

ANSWER="Spring Bean 生命周期包括实例化、属性注入、Aware 回调、BeanPostProcessor 前置处理、afterPropertiesSet、自定义 init 方法、销毁回调；作用域有 singleton 与 prototype 等。我会结合项目中 Bean 初始化顺序问题举例说明排查过程。"
curl -s -X POST "$B/api/interviews/$SID/messages/text" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"roundNo\":0,\"content\":\"$ANSWER\"}" >/dev/null

# ---------- 3) 断言：追问消息由 stub 产出（真实链路，非 Mock 降级） ----------
FOLLOWUP_OK=0
for i in $(seq 1 15); do
  sleep 2
  if curl -s "$B/api/interviews/$SID/messages" -H "$AUTH" \
      | grep -q "【stub】追问内容标记"; then FOLLOWUP_OK=1; break; fi
done
[ "$FOLLOWUP_OK" = "1" ] && echo "✅ evaluateTurn+nextQuestion 经真实 Provider（stub）产出追问【stub】追问内容标记" \
                      || { echo "❌ 未见 stub 追问标记（可能降级 Mock），见 $BACKEND_LOG"; exit 1; }

# ---------- 4) 断言：报告摘要由 stub 产出 ----------
curl -s -X POST "$B/api/interviews/$SID/finish" -H "$AUTH" >/dev/null
REPORT_OK=0
for i in $(seq 1 15); do
  sleep 2
  if curl -s "$B/api/interviews/$SID/report" -H "$AUTH" \
      | grep -q "【stub】报告摘要标记"; then REPORT_OK=1; break; fi
done
[ "$REPORT_OK" = "1" ] && echo "✅ generateReport 经真实 Provider（stub）产出报告摘要标记" \
                      || { echo "❌ 报告未含 stub 摘要标记，见 $BACKEND_LOG"; exit 1; }

# ---------- 5) 断言：stub 收到的每个请求 Authorization 精确正确 ----------
BAD_AUTH=$(grep -vc "^Bearer ${STUB_KEY}$" /tmp/aiinterview_stub_auth.log || true)
TOTAL_AUTH=$(wc -l < /tmp/aiinterview_stub_auth.log | tr -d ' ')
[ "$BAD_AUTH" = "0" ] && [ "$TOTAL_AUTH" -ge 3 ] \
  && echo "✅ stub 共收到 ${TOTAL_AUTH} 次调用，Authorization 全部精确为 Bearer <key>" \
  || { echo "❌ 鉴权头异常：total=${TOTAL_AUTH} bad=${BAD_AUTH}"; cat /tmp/aiinterview_stub_auth.log; exit 1; }

echo
echo "==== verify_real_provider_auth：PASS（协议级鉴权 + 三路径真实调用链路贯通）===="
echo "（真实 qwen key 成功调用为 15/15 #6/#14 最终条件，按 D5 另行人工取证）"
