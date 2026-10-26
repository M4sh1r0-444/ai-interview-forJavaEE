#!/usr/bin/env bash
# verify_resume_feature.sh —— R4-2：简历分析与个性化面试 qwen 真实 Provider 冒烟（D5 口径）。
#
# 证明链（AI_PROVIDER=qwen 指向本地 OpenAI 兼容 stub，要求精确 Bearer 鉴权）：
#   ① 上传简历（qa/samples/resume_demo.md，positionId=1）→ 异步分析 ANALYZED
#      → R-04 analysis JSON 契约校验（六维 0~100/总分/建议 3~8 条风险降序/provider=qwen/stub 摘要标记）
#      → 详情 digest 无明文手机号/邮箱（PII 脱敏先于入库与送模型）
#   ② R-08 focus-skills 结构校验（matched/gaps/hitRatio）
#   ③ R-09/R-10 带 resumeId 创建 → start 首题含 stub 改写标记（真实 personalizeQuestions 链路，
#      非降级 Mock 模板）且 questionId 指向题库原题（评分基准不变）
#   ④ 跑完 5 轮 → finish → 报告摘要含 stub 标记（真实 generateReport）且含"简历中…"对比条目
#   ⑤ stub 侧断言每个请求 Authorization 精确等于 "Bearer <key>"
# 真实千问 key 的成功调用按 D5 另行人工取证（把 AI_API_URL/KEY 指向 dashscope 亦可复用本脚本流程）。
#
# 用法：bash qa/verify_resume_feature.sh
# 依赖：colima 在跑（dev MySQL 3306/Redis 6380）、JDK17、python3、18083/19092 端口空闲。
set -euo pipefail
cd "$(dirname "$0")/.."

STUB_PORT=19092
BACKEND_PORT=18083
STUB_KEY="stub-key-resume-123456"
STUB_DB="ai_interview_resume_stub"
SAMPLE="qa/samples/resume_demo.md"
STUB_LOG=/tmp/aiinterview_resume_stub_server.log
BACKEND_LOG=/tmp/aiinterview_resume_backend.log
AUTH_LOG=/tmp/aiinterview_resume_stub_auth.log
# enforcer 门禁要求 JDK 17；shell profile 的 JAVA_HOME 可能指向 25，仓库约定路径存在即强制使用
JAVA17_HOME="${JAVA17_HOME:-$HOME/tools/jdk-17.0.20.1+1/Contents/Home}"
if [ -d "$JAVA17_HOME" ]; then
  JAVA_HOME="$JAVA17_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
export JAVA_HOME

cleanup() {
  [ -n "${BACKEND_PID:-}" ] && kill "$BACKEND_PID" 2>/dev/null || true
  [ -n "${STUB_PID:-}" ] && kill "$STUB_PID" 2>/dev/null || true
  sleep 1
  docker exec aiinterview-mysql mysql -uroot -proot123456 -e "DROP DATABASE IF EXISTS ${STUB_DB};" >/dev/null 2>&1 || true
}
trap cleanup EXIT

[ -f "$SAMPLE" ] || { echo "❌ 样例简历缺失：$SAMPLE"; exit 1; }

# ---------- 1) 本地 OpenAI 兼容 stub：精确 Bearer；按 Prompt 类型返回带标记的合法 JSON ----------
python3 - "$STUB_PORT" "$STUB_KEY" > "$STUB_LOG" 2>&1 <<'PY' &
import json, re, sys
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT, KEY = int(sys.argv[1]), sys.argv[2]

class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8", "replace")
        auth = self.headers.get("Authorization")
        with open("/tmp/aiinterview_resume_stub_auth.log", "a") as f:
            f.write(auth + "\n")
        if auth != "Bearer " + KEY:
            self.send_response(401)
            self.end_headers()
            self.wfile.write(b'{"error":"unauthorized"}')
            return
        req = json.loads(body)
        prompt = req["messages"][1]["content"]
        if "简历评审专家" in prompt:            # analyzeResume（六维 + 建议 3~8 条）
            content = {
                "dimensionScores": {"basic": 88, "education": 80, "skill": 86,
                                    "project": 84, "experience": 72, "expression": 82},
                "strengths": ["【stub】简历亮点：技能栈覆盖岗位核心要求"],
                "suggestions": [
                    {"riskLevel": "MEDIUM", "dimension": "experience",
                     "title": "实习经历偏短", "issue": "仅一段两个月实习",
                     "suggestion": "补充可量化的产出与职责",
                     "evidence": "2025.07 - 2025.09 某互联网公司"},
                    {"riskLevel": "LOW", "dimension": "skill",
                     "title": "技能匹配良好", "issue": "覆盖核心技能",
                     "suggestion": "保持分层表述", "evidence": "熟悉 Java 基础、集合与并发"},
                    {"riskLevel": "LOW", "dimension": "project",
                     "title": "项目有量化成果", "issue": "无",
                     "suggestion": "保持 STAR 结构", "evidence": "压测峰值 500 QPS"}],
                "summary": "【stub】简历分析摘要标记：真实 Provider 六维契约校验通过"}
        elif "改写为结合候选人简历背景的个性化题干" in prompt:   # personalizeQuestions
            # 从 Prompt 题目列表解析 questionId（"ID 12（TECHNICAL）：…"），一一对应返回
            ids = re.findall(r"ID (\d+)（", prompt)
            content = {"items": [{"questionId": int(qid),
                                  "content": "【stub】结合你在《校园二手交易平台》项目中使用 Spring Boot 与 Redis 的经历——请作答本题。"}
                                 for qid in ids]}
        elif "评分官" in prompt:                # evaluateTurn（高分，保证简历对比条目落 strengths ≥80 档）
            content = {"scores": {"technical": 90, "depth": 86, "logic": 85,
                                  "match": 88, "communication": 85},
                       "coveredPoints": ["stub 覆盖点"], "missingPoints": [],
                       "evidence": ["stub 证据句"], "reason": "stub-eval",
                       "targetSkills": ["java_basics"], "responseTooVague": False}
        elif "评估专家" in prompt:              # generateReport
            content = {"summary": "【stub】报告摘要标记：个性化面试真实链路贯通",
                       "strengths": ["stub 亮点：回答覆盖核心得分点"],
                       "weaknesses": [], "improvementPlan": ["stub 建议：继续巩固岗位核心技能"],
                       "reportText": "stub 报告正文，仅证明真实调用链路贯通。"}
        else:                                   # nextQuestion（追问）
            content = {"action": "FOLLOW_UP", "content": "【stub】追问标记：请结合项目举例说明取舍。",
                       "difficulty": 3, "reason": "stub-question", "targetSkills": ["java_basics"]}
        resp = json.dumps({"choices": [{"message": {"role": "assistant",
                                                    "content": json.dumps(content, ensure_ascii=False)}}]},
                          ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(resp)))
        self.end_headers()
        self.wfile.write(resp)

HTTPServer(("127.0.0.1", PORT), H).serve_forever()
PY
STUB_PID=$!
rm -f "$AUTH_LOG"
sleep 1
echo "== 1) 简历功能 stub 已启动 :${STUB_PORT}（analyzeResume/personalize/评分/报告/追问 五路应答）"

# ---------- 2) 独立库 + Redis db6 起后端（qwen 模式指向 stub） ----------
docker exec aiinterview-mysql mysql -uroot -proot123456 -e "DROP DATABASE IF EXISTS ${STUB_DB};"
export JAVA_HOME
cd backend
AI_PROVIDER=qwen AI_API_URL="http://127.0.0.1:${STUB_PORT}/v1" AI_API_KEY="$STUB_KEY" \
SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/${STUB_DB}?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai" \
SPRING_DATA_REDIS_PORT=6380 SPRING_DATA_REDIS_DATABASE=6 \
SERVER_PORT=$BACKEND_PORT UPLOAD_DIR=/tmp/aiinterview_resume_uploads \
nohup mvn -q spring-boot:run > "$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!
cd ..
echo "== 2) 后端以 qwen 模式启动 :${BACKEND_PORT}（独立库 ${STUB_DB} / Redis db6）"

for i in $(seq 1 60); do
  curl -s -o /dev/null --max-time 3 "http://127.0.0.1:${BACKEND_PORT}/actuator/health" | grep -q UP && break
  sleep 2
done
curl -s --max-time 3 "http://127.0.0.1:${BACKEND_PORT}/actuator/health" | grep -q UP \
  || { echo "❌ 后端 120s 未就绪，见 $BACKEND_LOG"; exit 1; }
echo "== 3) 后端就绪（Flyway 全新迁移 V1~V9）"

# ---------- 3) 业务流 + JSON 契约校验 ----------
python3 - "$BACKEND_PORT" "$SAMPLE" <<'PY'
import json, sys, time, urllib.request, urllib.error

BASE = "http://127.0.0.1:%s" % int(sys.argv[1])
SAMPLE = sys.argv[2]
PASS = []

def check(name, ok, note=""):
    PASS.append(ok)
    print(("✅ " if ok else "❌ ") + name + (("：" + note) if note else ""))
    if not ok:
        raise SystemExit(1)

def api(method, path, token=None, obj=None, raw=None, ctype="application/json"):
    url = BASE + path
    data = raw if raw is not None else (json.dumps(obj).encode() if obj is not None else None)
    req = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", ctype)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())

# -- 注册 + 登录
user = "rsmk_%d" % int(time.time())
api("POST", "/api/auth/register", obj={"username": user, "password": "Resume@123456",
                                       "realName": "简历冒烟", "major": "软件工程"})
token = api("POST", "/api/auth/login", obj={"username": user, "password": "Resume@123456"})["data"]["token"]

# -- ① 上传 → 轮询 ANALYZED（分析走真实 Provider）
def upload_resume(token, path):
    boundary = "----rsmk%s" % int(time.time() * 1000)
    with open(path, "rb") as f:
        content = f.read()
    parts = []
    parts.append(("--%s\r\nContent-Disposition: form-data; name=\"file\"; filename=\"resume_demo.md\"\r\n"
                  "Content-Type: text/markdown\r\n\r\n" % boundary).encode() + content + b"\r\n")
    parts.append(("--%s\r\nContent-Disposition: form-data; name=\"positionId\"\r\n\r\n1\r\n" % boundary).encode())
    body = b"".join(parts) + ("--%s--\r\n" % boundary).encode()
    return api("POST", "/api/resumes", token, None, raw=body,
               ctype="multipart/form-data; boundary=" + boundary)

up = upload_resume(token, SAMPLE)
check("R-01 上传受理（code=0，status ∈ ANALYZING/ANALYZED）",
      up["code"] == 0 and up["data"]["status"] in ("ANALYZING", "ANALYZED"),
      "status=%s" % up["data"].get("status"))
rid = up["data"]["id"]

detail = None
for _ in range(30):
    detail = api("GET", "/api/resumes/%d" % rid, token)["data"]
    if detail["status"] in ("ANALYZED", "FAILED"):
        break
    time.sleep(2)
check("上传后异步分析至 ANALYZED（真实 Provider 链路）", detail["status"] == "ANALYZED",
      "status=%s failReason=%s" % (detail.get("status"), detail.get("failReason")))

# -- R-04 analysis JSON 契约
an = api("GET", "/api/resumes/%d/analysis" % rid, token)
check("R-04 analysis code=0", an["code"] == 0)
d = an["data"]
dims = d["dimensionScores"]
six = ["basic", "education", "skill", "project", "experience", "expression"]
check("六维齐全且各维 0~100 整数",
      all(k in dims and isinstance(dims[k], int) and 0 <= dims[k] <= 100 for k in six),
      json.dumps(dims, ensure_ascii=False))
check("总分 overallScore 0~100", isinstance(d["overallScore"], int) and 0 <= d["overallScore"] <= 100,
      "overallScore=%s" % d["overallScore"])
sg = d["suggestions"]
rank = {"HIGH": 0, "MEDIUM": 1, "LOW": 2}
check("建议 3~8 条、riskLevel 合法且按风险降序",
      3 <= len(sg) <= 8
      and all(s.get("riskLevel") in rank for s in sg)
      and all(rank[sg[i]["riskLevel"]] <= rank[sg[i + 1]["riskLevel"]] for i in range(len(sg) - 1)),
      "共 %d 条：%s" % (len(sg), "/".join(s["riskLevel"] for s in sg)))
check("provider=qwen（真实 Provider 产出，非降级 mock）", d.get("provider") == "qwen",
      "provider=%s" % d.get("provider"))
check("摘要含 stub 标记（真实调用链贯通）", "【stub】" in (d.get("summary") or ""))

# -- PII 脱敏：详情整体 JSON 不得出现明文手机号/邮箱
raw_detail = json.dumps(detail, ensure_ascii=False)
check("PII 已脱敏（明文手机号/邮箱不出现在详情/digest/profile）",
      "13812345678" not in raw_detail and "zhangsan@example.com" not in raw_detail)
check("digest 含识别出的技能（供个性化组卷用）",
      detail.get("digest") and len(detail["digest"].get("skills") or []) > 0,
      "skills=%d 项" % len((detail.get("digest") or {}).get("skills") or []))

# -- ② R-08 focus-skills
fs = api("GET", "/api/resumes/%d/focus-skills?positionId=1" % rid, token)["data"]
check("R-08 focus-skills 结构（matched/gaps/hitRatio∈[0,1]）",
      isinstance(fs.get("matched"), list) and isinstance(fs.get("gaps"), list)
      and isinstance(fs.get("hitRatio"), (int, float)) and 0 <= fs["hitRatio"] <= 1,
      "matched=%d gaps=%d hitRatio=%.2f" % (len(fs["matched"]), len(fs["gaps"]), fs["hitRatio"]))

# -- ③ R-09 带 resumeId 创建 → R-10 start 首题为真实改写
ci = api("POST", "/api/interviews", token,
         {"positionId": 1, "mode": "TEXT", "difficulty": 3, "rounds": 5,
          "durationMinutes": 15, "resumeId": rid})["data"]
check("R-09 创建成功且 resumeId/personalized 回显",
      ci.get("resumeId") == rid and ci.get("personalized") is True)
sid = ci["id"]
api("POST", "/api/interviews/%d/start" % sid, token)
msgs = api("GET", "/api/interviews/%d/messages" % sid, token)["data"]
main0 = [m for m in msgs if m["speaker"] == "AI" and m["roundNo"] == 0 and not m["followupIndex"]][0]
check("R-10 首题含 stub 改写标记（真实 personalizeQuestions，非降级模板）",
      "【stub】结合你在《校园二手交易平台》" in main0["contentText"])
check("首题 questionId 非空（评分基准不变）", main0.get("questionId") is not None,
      "questionId=%s" % main0.get("questionId"))

# -- ④ 跑完 5 轮（消息差集驱动：AI 主问题+追问数 > 用户已答数即作答）
ANSWER = ("这个问题我从三个层面回答：首先明确核心概念与适用场景；其次结合我在校园二手交易平台项目中"
          "使用 Spring Boot、MySQL 与 Redis 的实践说明落地方式，包括关键设计取舍与踩过的坑；"
          "最后给出量化结果，接口平均耗时由 320ms 降至 90ms，压测峰值 500 QPS。")
deadline, idle = time.time() + 420, 0
while time.time() < deadline:
    st = api("GET", "/api/interviews/%d" % sid, token)["data"]["status"]
    if st in ("FINISHED", "TIMEOUT"):
        break
    msgs = api("GET", "/api/interviews/%d/messages" % sid, token)["data"]
    acted = False
    for r in sorted({m["roundNo"] for m in msgs if m["speaker"] == "AI"}):
        ai_round = [m for m in msgs if m["speaker"] == "AI" and m["roundNo"] == r]
        user_n = len([m for m in msgs if m["speaker"] == "USER" and m["roundNo"] == r])
        expected = len([m for m in ai_round if not m["followupIndex"]]) \
                 + len([m for m in ai_round if m["followupIndex"]])
        if user_n < expected:
            r0 = api("POST", "/api/interviews/%d/messages/text" % sid, token,
                     {"roundNo": r, "content": ANSWER})
            if r0.get("code") != 0:
                raise SystemExit("❌ 回答被拒：%s" % r0)
            acted = True
            break
    if acted:
        idle = 0
        time.sleep(0.8)
    else:
        idle += 1
        if idle >= 20:
            api("POST", "/api/interviews/%d/finish" % sid, token)
            time.sleep(2)
        else:
            time.sleep(0.7)
st = api("GET", "/api/interviews/%d" % sid, token)["data"]["status"]
check("个性化面试收卷（FINISHED/TIMEOUT）", st in ("FINISHED", "TIMEOUT"), "status=%s" % st)

report = None
for _ in range(20):
    r = api("GET", "/api/interviews/%d/report" % sid, token)
    if r["code"] == 0:
        report = r["data"]
        break
    time.sleep(1)
check("报告生成（code=0）", report is not None)
check("报告摘要含 stub 标记（真实 generateReport 链路）",
      "【stub】" in (report.get("summary") or ""))
both = (report.get("strengths") or []) + (report.get("weaknesses") or [])
check("报告含「简历 vs 实测」对比条目（简历中…）",
      any("简历中" in s for s in both),
      "命中条目：" + next((s for s in both if "简历中" in s), "无"))

print()
print("==== verify_resume_feature：%d 项断言全部通过（上传→分析→个性化面试→报告 真实链路 + JSON 契约）====" % len(PASS))
PY

# ---------- 4) stub 侧鉴权断言 ----------
BAD_AUTH=$(grep -vc "^Bearer ${STUB_KEY}$" "$AUTH_LOG" || true)
TOTAL_AUTH=$(wc -l < "$AUTH_LOG" | tr -d ' ')
[ "$BAD_AUTH" = "0" ] && [ "$TOTAL_AUTH" -ge 3 ] \
  && echo "✅ stub 共收到 ${TOTAL_AUTH} 次调用，Authorization 全部精确为 Bearer <key>" \
  || { echo "❌ 鉴权头异常：total=${TOTAL_AUTH} bad=${BAD_AUTH}"; cat "$AUTH_LOG"; exit 1; }

echo
echo "==== verify_resume_feature：PASS（真实 Provider 冒烟，协议级 JSON 契约校验）===="
echo "（真实千问 key 的端到端取证按 D5 口径另行人工执行；本脚本换 AI_API_URL/KEY 即可复用）"
