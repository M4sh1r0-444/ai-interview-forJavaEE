#!/usr/bin/env bash
# verify_compose_acceptance.sh —— FIX-20：全新 Compose project 端到端验收（D6：独立 project/卷）。
#
# 覆盖：页面内容断言（非仅 200）/ /api 401/登录/岗位 / 浏览器同源 WS 101 + 95s 存活
#（自动回 PONG，证明 FIX-03 心跳）/ Nginx 入口登录限流（5 次失败后 429、伪造 XFF 不绕过、
# 成功登录清零，FIX-09/D2）/ access+error 日志无 token（FIX-10）。
#
# 用法：bash qa/verify_compose_acceptance.sh
# 前置：colima 在跑、80 端口空闲、.env 就绪（AI_PROVIDER=mock）。全程使用独立 project，结束 down -v。
set -uo pipefail
cd "$(dirname "$0")/.."

PROJECT="fix-verify-$(date +%s)"
BASE="http://127.0.0.1"
PASS=0; FAIL=0
ok()  { echo "✅ $*"; PASS=$((PASS+1)); }
bad() { echo "❌ $*"; FAIL=$((FAIL+1)); }

echo "== 1) 全新 Compose project：${PROJECT}（独立卷，等 frontend 产物写入） =="
docker compose -p "$PROJECT" -f docker-compose.yml up -d --build >/dev/null 2>&1 \
  || { echo "❌ compose 启动失败"; exit 1; }
READY=0
for _ in $(seq 1 60); do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE/" 2>/dev/null)
  [ "$CODE" = "200" ] && READY=1 && break
  sleep 2
done
[ "$READY" = "1" ] && ok "五服务就绪（frontend Exited(0) 产物已入卷，nginx 80 可达）" \
                || { bad "120s 未就绪"; docker compose -p "$PROJECT" -f docker-compose.yml ps; }

BACKEND_UP=0
for _ in $(seq 1 60); do
  curl -s --max-time 5 "$BASE/api/positions" 2>/dev/null | head -c 200 | grep -q '"code":40101' && BACKEND_UP=1 && break
  sleep 2
done
[ "$BACKEND_UP" = "1" ] && ok "backend 经 Nginx 反代就绪（无 token → 40101）" \
                     || bad "backend 120s 未就绪（/api 持续非 40101）"

# ---------- 2) 页面内容断言（P9 教训：默认欢迎页也是 200） ----------
PAGE=$(curl -s --max-time 5 "$BASE/" | head -c 6000)
echo "$PAGE" | grep -q "AI 模拟面试与能力提升平台" \
  && ok "静态页内容含产品名（非 Nginx 默认页）" || bad "静态页不含产品名（疑似默认页）"
ASSET=$(echo "$PAGE" | grep -o 'src="/assets/[^"]*"' | head -1 | cut -d'"' -f2)
[ -n "$ASSET" ] && curl -s -o /dev/null -w '' --max-time 5 "$BASE$ASSET" \
  && curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE$ASSET" | grep -q 200 \
  && ok "构建产物可访问（$ASSET → 200）" || bad "assets 不可达：$ASSET"

# ---------- 3) /api 鉴权与基本链路 ----------
NOAUTH=$(curl -s --max-time 5 "$BASE/api/positions")
echo "$NOAUTH" | grep -q '"code":40101' \
  && ok "/api 无 token → 401 JSON（code=40101）" || bad "无 token 未返回 40101：$NOAUTH"

TOKEN=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"demo01","password":"123456"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["token"])' 2>/dev/null)
[ -n "$TOKEN" ] && ok "demo01 登录成功（成功登录同时清零限流计数，D2）" || bad "登录失败"

POS_COUNT=$(curl -s "$BASE/api/positions" -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["data"]))' 2>/dev/null)
[ "$POS_COUNT" = "2" ] && ok "岗位列表 2 条（全新迁移库）" || bad "岗位数异常：$POS_COUNT"

# ---------- 4) 浏览器同源 WS 101 + 95s 存活（自动回 PONG） ----------
SID=$(curl -s -X POST "$BASE/api/interviews" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"positionId":1,"mode":"TEXT","difficulty":3,"rounds":5,"durationMinutes":15}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["id"])' 2>/dev/null)
curl -s -o /dev/null -X POST "$BASE/api/interviews/$SID/start" -H "Authorization: Bearer $TOKEN"
echo "== WS 会话 $SID 已 start，Node/Python 客户端以同源 Origin 连接 95s =="
WS_RESULT=$(python3 - "$BASE" "$SID" "$TOKEN" <<'PY'
import base64, json, os, socket, sys, time

base, sid, token = sys.argv[1], sys.argv[2], sys.argv[3]
host = base.split('//')[1]
path = f"/ws/interviews/{sid}?token={token}"
key = base64.b64encode(os.urandom(16)).decode()
req = (f"GET {path} HTTP/1.1\r\nHost: {host}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
       f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\nOrigin: {base}\r\n\r\n")
s = socket.create_connection((host, 80), timeout=10)
s.sendall(req.encode())
buf = b""
while b"\r\n\r\n" not in buf:
    chunk = s.recv(4096)
    if not chunk:
        print(json.dumps({"handshake": False, "reason": "closed before headers"})); sys.exit(0)
    buf += chunk
head, _, buf = buf.partition(b"\r\n\r\n")
status = head.split(b"\r\n")[0].decode()
if " 101 " not in status:
    print(json.dumps({"handshake": False, "reason": status})); sys.exit(0)

def send_frame(op, payload=b""):
    mask = os.urandom(4)
    masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
    ln = len(payload)
    hdr = bytes([0x80 | op, 0x80 | ln]) if ln < 126 else bytes([0x80 | op, 0x80 | 126]) + ln.to_bytes(2, "big")
    s.sendall(hdr + mask + masked)

def parse(buf):
    out = []
    while True:
        if len(buf) < 2: break
        op = buf[0] & 0x0F
        masked = buf[1] & 0x80
        ln = buf[1] & 0x7F
        off = 2
        if ln == 126:
            if len(buf) < 4: break
            ln = int.from_bytes(buf[2:4], "big"); off = 4
        elif ln == 127:
            if len(buf) < 10: break
            ln = int.from_bytes(buf[2:10], "big"); off = 10
        if masked: off += 4
        if len(buf) < off + ln: break
        out.append((op, buf[off:off + ln]))
        buf = buf[off + ln:]
    return out, buf

text_msgs = pings = pongs_sent = 0
closed_by_server = False
s.settimeout(95)
deadline = time.time() + 95
while time.time() < deadline:
    try:
        data = s.recv(4096)
    except socket.timeout:
        break
    if not data:
        closed_by_server = True; break
    frames, buf = parse(buf + data)
    for op, payload in frames:
        if op == 8:
            closed_by_server = True
        elif op == 9:
            pings += 1
            send_frame(0xA, payload)   # 浏览器语义：自动回 PONG（同 payload）
            pongs_sent += 1
        elif op == 1:
            text_msgs += 1
    if closed_by_server:
        break
print(json.dumps({"handshake": True, "alive95s": not closed_by_server,
                  "textMsgs": text_msgs, "pings": pings, "pongsSent": pongs_sent}))
PY
)
echo "$WS_RESULT"
H101=$(echo "$WS_RESULT" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("handshake"))' 2>/dev/null)
[ "$H101" = "True" ] && ok "同源 Origin（http://127.0.0.1）WS 握手 101" || bad "WS 握手失败：$WS_RESULT"
ALIVE=$(echo "$WS_RESULT" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("alive95s"))' 2>/dev/null)
PINGS=$(echo "$WS_RESULT" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("pings",0))' 2>/dev/null)
[ "$ALIVE" = "True" ] && [ "$PINGS" -ge 2 ] \
  && ok "WS 空闲 95s 存活（收到并回 ${PINGS} 个 PING，未被 4408 关闭）" \
  || bad "WS 95s 存活失败（alive=${ALIVE}, pings=${PINGS}）"

# ---------- 5) 登录限流（经 Nginx：X-Real-IP=宿主网关 IP 同桶；上限 5 次失败） ----------
for i in 1 2; do
  curl -s -o /dev/null -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
    -d '{"username":"demo01","password":"wrong-password"}'
done
# 成功登录一次（清零，D2）
curl -s -o /dev/null -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"demo01","password":"123456"}'
# 再连续失败 5 次 → 第 5 次（累计第 5 次失败）后下一次应 429
CODES=""
for i in 1 2 3 4 5 6; do
  C=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/login" \
    -H 'Content-Type: application/json' -d '{"username":"demo01","password":"wrong-password"}')
  CODES="$CODES $C"
done
echo "  连续失败 6 次的 HTTP 状态：$CODES"
echo "$CODES" | tr ' ' '\n' | sort | uniq -c | grep -q "^ *1 *429" \
  && ok "第 5 次失败后第 6 次 429（只统计失败，成功登录已清零）" || bad "限流序列异常：$CODES"
FORGED=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' -H 'X-Forwarded-For: 198.51.100.7' \
  -d '{"username":"demo01","password":"wrong-password"}')
[ "$FORGED" = "429" ] && ok "伪造 X-Forwarded-For 不能绕过（后端只信 Nginx 的 X-Real-IP）" \
                    || bad "伪造 XFF 绕过成功（HTTP ${FORGED}）"

# ---------- 6) 日志无 token（FIX-10） ----------
FAKE_TOKEN="FAKE-JWT-e2e-should-never-appear"
curl -s -o /dev/null --max-time 5 "$BASE/ws/interviews/1?token=$FAKE_TOKEN"
sleep 1
NGINX_ID=$(docker compose -p "$PROJECT" -f docker-compose.yml ps -q nginx)
LOGS=$(docker logs "$NGINX_ID" 2>&1)
echo "$LOGS" | grep -q "$FAKE_TOKEN" \
  && bad "Nginx 日志出现明文 token（FIX-10 失效）" \
  || ok "Nginx 全部日志（access+error）不含 token"
echo "$LOGS" | grep -q '"GET /ws/interviews/1"' \
  && ok "access log 仍可审计（safe 格式：方法+URI+状态）" || bad "safe 格式日志缺失"

# ---------- 7) 收尾 ----------
echo "== 清理独立 project（down -v） =="
docker compose -p "$PROJECT" -f docker-compose.yml down -v >/dev/null 2>&1
echo
echo "==== verify_compose_acceptance：PASS=${PASS} FAIL=${FAIL} ===="
[ "$FAIL" = "0" ] || exit 1
