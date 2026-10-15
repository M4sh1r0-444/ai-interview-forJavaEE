#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
M6-6.4 Mock 离线（断网）演练驱动（计划 §9-M6-6.4 完成标准：无网环境完整走通
面试 → 报告 → 推荐）。

前置：由 qa/m6_offline_drill.sh 编排——dev 栈已停、离线栈
（docker-compose.yml + docker-compose.offline.yml，核心服务 internal 网络、AI_PROVIDER=mock）
已起且断网证明通过。本脚本只负责业务链路验证，经 Nginx 80 入口访问（与浏览器演示同路径）。

覆盖链路（对应方案 §19 演示 15 步的后端等价）：
  登录 → 岗位 → 双岗位各一场面试（文字=Java 岗 / 音频=前端岗，含追问）
  → 六维报告（AUDIO 场含 voice）→ 历史 → 成长曲线 → 个性化推荐
  → 管理端（看板 + 知识片段维护）→ WebSocket 推送。

用法：python3 m6_offline_drill.py [BASE_URL]（默认 http://127.0.0.1）
输出：qa/offline_drill_result.json + 控制台摘要；退出码 0=全部通过。

答案构造复用 6.2 抽测脚本（m6_ai_quality_sampling.py）的三档构造器，保证
mock 评分引擎可解析（得分点覆盖 → technical 等维度有区分度）。
"""
import json
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import m6_ai_quality_sampling as core  # noqa: E402  复用 api()/三档答案构造/run_session

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1"
core.BASE = BASE
RESULT_PATH = os.path.join(HERE, "offline_drill_result.json")

CHECKS = []  # (名称, 是否通过, 说明)


def check(name, ok, note=""):
    CHECKS.append({"name": name, "pass": bool(ok), "note": note})
    print("  %s %s%s" % ("✅" if ok else "❌", name, ("——" + note) if note else ""), flush=True)
    return ok


def post_audio(sid, token, round_no, asr_text, duration_ms):
    """multipart 提交音频回答（与前端契约一致：file + roundNo + asrText + durationMs）。"""
    boundary = "----offlineDrillBoundary7MA4YWxk"
    audio = b"\x1aE\xdf\xa3" + b"OFFLINE-DRILL-FAKE-AUDIO-" * 40   # ~2KB 假 webm 载荷
    parts = []
    for name, value in (("roundNo", str(round_no)), ("asrText", asr_text),
                        ("durationMs", str(duration_ms))):
        parts.append(("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n"
                      % (boundary, name, value)).encode())
    parts.append(("--%s\r\nContent-Disposition: form-data; name=\"file\"; "
                  "filename=\"answer.webm\"\r\nContent-Type: audio/webm\r\n\r\n"
                  % boundary).encode() + audio + b"\r\n")
    body = b"".join(parts) + ("--%s--\r\n" % boundary).encode()
    req = core.urllib.request.Request(
        BASE + "/api/interviews/%d/messages/audio" % sid, data=body, method="POST")
    req.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
    req.add_header("Authorization", "Bearer " + token)
    with core.urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())


def run_audio_session(token, position_id, bank, tiers, session_seq=0):
    """AUDIO 场：驱动逻辑与 core.run_session 相同（消息差集推进），作答改走音频上传；
    asrText 用三档构造器生成，durationMs 按约 200 字/分 折算使语速落在正常带。"""
    sid = core.api("POST", "/api/interviews", token,
                   {"positionId": position_id, "mode": "AUDIO", "difficulty": 3,
                    "rounds": len(tiers), "durationMinutes": 15})["data"]["id"]
    core.api("POST", "/api/interviews/%d/start" % sid, token)
    rounds_data, followups, audio_count = {}, [], 0
    idle, deadline = 0, time.time() + 420
    while time.time() < deadline:
        detail = core.api("GET", "/api/interviews/%d" % sid, token)["data"]
        if detail["status"] in ("FINISHED", "TIMEOUT"):
            break
        msgs = core.api("GET", "/api/interviews/%d/messages" % sid, token)["data"]
        acted = False
        for r in sorted({m["roundNo"] for m in msgs if m["speaker"] == "AI"}):
            if r >= len(tiers):
                continue
            ai_round = [m for m in msgs if m["speaker"] == "AI" and m["roundNo"] == r]
            user_round = [m for m in msgs if m["speaker"] == "USER" and m["roundNo"] == r]
            mains = [m for m in ai_round if not m["followupIndex"]]
            fups = sorted((m for m in ai_round if m["followupIndex"]), key=lambda m: m["id"])
            expected = (1 if mains else 0) + len(fups)
            if not mains or len(user_round) >= expected:
                continue
            tier = tiers[r]
            if not user_round:
                q = bank[mains[0]["questionId"]]
                ans = core.build_main_answer(q, tier, session_seq + r)
                rounds_data[r] = {"questionId": q["id"], "question": q["content"], "tier": tier,
                                  "followups": []}
            else:
                fu = fups[len(user_round) - 1]
                rd = rounds_data[r]
                rd["followups"].append(fu["contentText"])
                followups.append({"roundNo": r, "tier": tier, "followup": fu["contentText"]})
                ans = core.build_followup_answer(bank[rd["questionId"]], tier,
                                                 len(rd["followups"]))
            r0 = post_audio(sid, token, r, ans, max(60000, int(len(ans) * 300)))
            if r0.get("code") != 0:
                raise RuntimeError("音频回答被拒: %s" % r0)
            audio_count += 1
            acted = True
            break
        if acted:
            idle = 0
            time.sleep(0.6)
        else:
            idle += 1
            if idle >= 25:
                core.api("POST", "/api/interviews/%d/finish" % sid, token)
                time.sleep(2)
            else:
                time.sleep(0.7)

    status = core.api("GET", "/api/interviews/%d" % sid, token)["data"]["status"]
    report = None
    for _ in range(20):
        r = core.api("GET", "/api/interviews/%d/report" % sid, token)
        if r["code"] == 0:
            report = r["data"]
            break
        time.sleep(1)
    return sid, status, report, rounds_data, followups, audio_count


def ws_probe(token):
    """经 Nginx /ws 建立 WebSocket，start 后应收到首题 QUESTION 推送（node 22 原生客户端）。"""
    sid = core.api("POST", "/api/interviews", token,
                   {"positionId": 1, "mode": "TEXT", "difficulty": 3,
                    "rounds": 5, "durationMinutes": 15})["data"]["id"]
    url = "%s/ws/interviews/%d?token=%s" % (BASE.replace("http", "ws"), sid, token)
    js = ("const ws=new WebSocket(%s);ws.onmessage=e=>{const m=JSON.parse(e.data);"
          "console.log('WS_EVENT '+m.type);if(m.type==='QUESTION'){console.log('WS_OK');"
          "setTimeout(()=>process.exit(0),300)}};ws.onclose=c=>console.log('WS_CLOSED '+c.code);"
          "setTimeout(()=>{console.log('WS_TIMEOUT');process.exit(3)},20000);"
          % json.dumps(url))
    try:
        proc = subprocess.Popen(["node", "-e", js], stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True)
    except FileNotFoundError:
        core.api("POST", "/api/interviews/%d/abort" % sid, token)
        return None, "", "node 不可用，跳过"
    time.sleep(1.5)
    core.api("POST", "/api/interviews/%d/start" % sid, token)
    try:
        out, _ = proc.communicate(timeout=25)
    except subprocess.TimeoutExpired:
        proc.kill()
        out, _ = proc.communicate()
    core.api("POST", "/api/interviews/%d/abort" % sid, token)
    return proc.returncode == 0 and "WS_OK" in out, out.strip(), ""


def load_bank(admin_token, position_id):
    bank, page = {}, 1
    while True:
        data = core.api("GET", "/api/admin/questions?positionId=%d&page=%d&size=50"
                        % (position_id, page), admin_token)["data"]
        for q in data["records"]:
            if q["status"] == "ENABLED":
                bank[q["id"]] = q
        if page * 50 >= data["total"]:
            return bank
        page += 1


def main():
    print("== M6-6.4 Mock 离线演练（经 Nginx 入口 %s）==" % BASE, flush=True)

    admin_token = core.api("POST", "/api/auth/login", None,
                           {"username": "admin", "password": "Admin@123456"})["data"]["token"]
    positions = core.api("GET", "/api/positions", admin_token)["data"]
    check("岗位列表返回 2 个岗位", len(positions) == 2,
          " / ".join("%s(id=%d)" % (p["name"], p["id"]) for p in positions))
    pid_java, pid_front = positions[0]["id"], positions[1]["id"]

    username = "offline_%d" % (int(time.time()) % 1000000)
    core.api("POST", "/api/auth/register", None,
             {"username": username, "password": "123456", "major": "软件工程"})
    token = core.api("POST", "/api/auth/login", None,
                     {"username": username, "password": "123456"})["data"]["token"]
    print("演练账号：%s" % username, flush=True)

    banks = {pid: load_bank(admin_token, pid) for pid in (pid_java, pid_front)}

    # ---- 文字面试（Java 岗，5 轮 好/好/中/中/差，追问自然产生）----
    print("[1/7] 文字面试（Java 岗）", flush=True)
    tiers_text = ["good", "good", "mid", "mid", "poor"]
    sid_text, st_text, rep_text, _, fu_text = core.run_session(
        token, pid_java, tiers_text, banks[pid_java], session_seq=0)
    check("文字面试完整走完（FINISHED）", st_text == "FINISHED",
          "sessionId=%d status=%s" % (sid_text, st_text))
    check("动态追问离线可用", len(fu_text) >= 1, "追问 %d 次" % len(fu_text))
    ok_rep = rep_text is not None and rep_text.get("totalScore") is not None \
        and rep_text.get("dimensionScores") and rep_text.get("questionEvidence")
    check("六维评估报告生成", bool(ok_rep),
          ("总分 %.2f，维度 %s，逐题证据 %d 条" % (rep_text["totalScore"],
             "/".join("%s=%.0f" % kv for kv in sorted(rep_text["dimensionScores"].items())),
             len(rep_text["questionEvidence"]))) if ok_rep else "报告缺失")

    # ---- 音频面试（前端岗，5 轮 好/好/中/中/差；rounds 下限 5）----
    print("[2/7] 语音面试（前端岗）", flush=True)
    sid_audio, st_audio, rep_audio, _, fu_audio, n_audio = run_audio_session(
        token, pid_front, banks[pid_front], ["good", "good", "mid", "mid", "poor"],
        session_seq=9)
    check("语音面试完整走完（FINISHED）", st_audio == "FINISHED",
          "sessionId=%d status=%s，音频提交 %d 次" % (sid_audio, st_audio, n_audio))
    has_voice = bool(rep_audio) and rep_audio.get("dimensionScores", {}).get("voice") is not None
    check("AUDIO 场报告含 voice 维度", has_voice,
          "voice=%.0f" % rep_audio["dimensionScores"]["voice"] if has_voice else "无 voice")

    # ---- 历史与成长 ----
    print("[3/7] 历史 / 成长曲线", flush=True)
    history = core.api("GET", "/api/users/me/history?page=1&size=10", token)["data"]
    st_counts = {}
    for h in history.get("records") or []:
        st_counts[h.get("status")] = st_counts.get(h.get("status"), 0) + 1
    check("历史记录 2 场", (history.get("total") or 0) >= 2, "total=%s %s"
          % (history.get("total"), st_counts))
    growth = core.api("GET", "/api/users/me/growth?positionId=%d" % pid_java, token)["data"] or []
    max_pts = max((len(g.get("records") or []) for g in growth), default=0)
    check("成长曲线非空（报告自动折算掌握度）", len(growth) >= 1,
          "技能曲线 %d 条，最多 %d 个点" % (len(growth), max_pts))

    # ---- 个性化推荐 ----
    print("[4/7] 个性化推荐", flush=True)
    recs = core.api("GET", "/api/users/me/recommendations?positionId=%d" % pid_java,
                    token).get("data") or []
    res_cnt = sum(len(x.get("resources") or []) for x in recs)
    retry_cnt = sum(len(x.get("retryQuestions") or []) for x in recs)
    check("推荐 Top3（短板技能）非空", 1 <= len(recs) <= 3,
          "；".join("%s(优先级%.1f)" % (x.get("skillCode"), x.get("priority", -1))
                    for x in recs[:3]) or "空")
    check("推荐含学习资源与理由", res_cnt >= 1 and all(x.get("weaknessReasons") for x in recs),
          "资源 %d 条、错题重练 %d 条、下场建议=%s"
          % (res_cnt, retry_cnt, recs[0].get("nextInterviewSuggested") if recs else None))

    # ---- 管理端 ----
    print("[5/7] 管理端（看板 + 知识维护）", flush=True)
    dash = core.api("GET", "/api/admin/dashboard", admin_token)["data"]
    check("管理端看板可用", dash.get("userCount", 0) >= 2 and dash.get("sessionCount", 0) >= 2,
          "用户 %d / 场次 %d / 题量 %d / 均分 %.2f"
          % (dash.get("userCount"), dash.get("sessionCount"),
             dash.get("questionCount"), dash.get("avgScore") or 0))
    kbi = {"positionId": pid_java, "title": "离线演练-知识片段-%d" % (int(time.time()) % 100000),
           "content": "离线演练验证：管理端在无网环境仍可维护知识片段。MySQL ngram 全文检索"
                      "不依赖外部服务，新增片段即时进入关键词词典并可被面试提示词检索注入。",
           "keywords": "离线演练,知识片段,全文检索,ngram,断网",
           "skillTags": "mysql"}
    kb = core.api("POST", "/api/admin/knowledge", admin_token, kbi)
    check("管理端新增知识片段", kb.get("code") == 0, kb.get("message") or "")

    # ---- WebSocket ----
    print("[6/7] WebSocket 推送", flush=True)
    ws_ok, ws_out, ws_note = ws_probe(token)
    if ws_ok is None:
        check("WebSocket 首题推送", True, ws_note + "（跳过项不计失败）")
    else:
        check("WebSocket 首题推送", ws_ok, (ws_out.splitlines() or [""])[0])

    # ---- 汇总 ----
    print("[7/7] 汇总", flush=True)
    env = {}
    env_path = os.path.join(HERE, "offline_drill_env.json")
    if os.path.exists(env_path):
        with open(env_path) as f:
            env = json.load(f)
    passed = sum(1 for c in CHECKS if c["pass"])
    print("\n==== 离线演练结果：%d/%d 项通过 ====" % (passed, len(CHECKS)), flush=True)
    result = {"base": BASE, "user": username, "passed": passed, "total": len(CHECKS),
              "ok": passed == len(CHECKS), "checks": CHECKS, "envProofs": env,
              "sessions": {
                  "text": {"sessionId": sid_text, "status": st_text,
                           "totalScore": (rep_text or {}).get("totalScore"),
                           "dimensionScores": (rep_text or {}).get("dimensionScores"),
                           "followups": fu_text},
                  "audio": {"sessionId": sid_audio, "status": st_audio,
                            "totalScore": (rep_audio or {}).get("totalScore"),
                            "dimensionScores": (rep_audio or {}).get("dimensionScores"),
                            "audioUploads": n_audio, "followups": fu_audio}},
              "growth": {"curves": len(growth), "maxPoints": max_pts},
              "recommendations": {"top": [x.get("skillCode") for x in recs[:3]],
                                  "resourceCount": res_cnt, "retryCount": retry_cnt}}
    with open(RESULT_PATH, "w") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    print("明细已写入 %s" % RESULT_PATH, flush=True)
    sys.exit(0 if result["ok"] else 1)


if __name__ == "__main__":
    main()
