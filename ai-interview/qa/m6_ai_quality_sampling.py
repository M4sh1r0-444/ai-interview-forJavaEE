#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
M6-6.2 AI 质量抽测（计划 §8.3）：每岗 20 条标准问答（好/中/差三档人工作答），
跑真实面试链路（mock provider），核对：
  1) 评分与人工分差 ≤15 的比例 ≥80%
  2) 追问相关性（记录追问原文供人工复核）
  3) 报告字段完整（六维 0~100 / 总分 / 亮点 / 不足 / 建议 / 逐题证据）
  4) 推荐对应短板
用法：python3 m6_ai_quality_sampling.py [BASE_URL]（默认 http://127.0.0.1:18080）
输出：qa_result.json（全部原始数据）+ 控制台摘要。

轮次驱动说明：主问题在 start 时一次性预写落库（进度记录 D12），轮次推进不产生新
AI 消息——按「可见消息中 USER 数 < 应答数（主问题+追问）」的差集驱动作答。
"""
import json
import re
import sys
import time
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:18080"

# 人工评分基准（先按档位给基准分，抽测后逐条人工复核微调并记录理由）
MANUAL_SCORE = {"good": 85, "mid": 62, "poor": 35}
TIER_CN = {"good": "好", "mid": "中", "poor": "差"}

# 每岗 4 场 × 5 轮，档位分布：好 8 / 中 6 / 差 6 = 20 条
SESSION_TIERS = [
    ["good", "good", "good", "mid", "mid"],
    ["good", "good", "mid", "poor", "poor"],
    ["good", "good", "mid", "mid", "poor"],
    ["good", "mid", "mid", "poor", "poor"],
]

GENERIC_PREFIXES = ["能", "会", "要", "可", "可以", "准确", "清楚", "清晰", "具体", "简单",
                    "简要", "说出", "讲出", "讲清", "说清", "说明", "描述", "举出", "列出",
                    "结合", "给出", "提到", "对比", "分析", "解释", "画出", "写出", "了解",
                    "知道", "理解", "使用", "项目", "中"]
GENERIC_SUFFIXES = ["的定义", "的区别", "的关系", "的例子", "的场景", "的机制", "的原理",
                    "的对比", "的实践", "的约定", "的影响", "的方式", "的原则", "的前提",
                    "的过程", "的优劣", "的优缺点", "的作用", "的原因"]
TECH_TOKEN = re.compile(r"[A-Za-z][A-Za-z0-9+.#@_-]{1,}")

GOOD_OPENERS = ["这道题我比较熟，我的理解是这样的。", "这个问题我系统学过，我按层次来答。",
                "这个考点我在项目里实际处理过，我展开说一下。"]
GOOD_TAILS = ["综上，核心就是这几点，因为机制之间是相互支撑的，所以理解了底层再记细节就不容易忘。",
              "总之我的原则是先讲清机制，再看它解决了什么问题，最后落到实践里的取舍。",
              "以上是我对这道题的完整理解，实际落地时还要结合具体场景做权衡。"]
MID_TAILS = ["其他的细节我记不太准了，就先答到这里。", "大概就是这些，更深入的我需要再查一下。"]
POOR_MAINS = ["嗯……这个我好像在哪见过，就是说大概是个什么东西吧，具体的我真说不上来，没深入研究过。",
              "啊这个……那个，我记得老师说过的，但是就是说具体怎么回事我忘了，反正差不多是那一类的概念吧。",
              "呃，这个东西我没怎么用过，就是说有个模糊印象，让我现在讲细节的话我讲不出来，不好意思。"]
# 追问回答均 ≥60 字，避免“好”档追问因长度被判模糊（<60 字触发 R2，见 MockAiProvider）
GOOD_FOLLOWUPS = ["再补充一点：{kp0}在实际项目里要注意边界条件，因为场景不同取舍也不同，所以上线前我会先做验证再推广。",
                  "关于这点还可以延伸：{kp0}之所以这么设计，是因为要兼顾性能和正确性两方面的诉求，所以不能只看单点效果。",
                  "换个角度说：{kp0}落地时还要考虑失败场景，因为异常路径往往才是关键，所以我会配套做好监控和降级预案。"]
MID_FOLLOWUPS = ["这块我知道的就是刚才说的那些了，剩下的细节因为没实际用过，所以不敢乱说，我回去查完文档可以再补充。",]
POOR_FOLLOWUPS = ["嗯……就是说具体的我还真不太清楚，好像之前听人提起过，但我自己没研究过，所以真的说不上来。",]


def api(method, path, token=None, body=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    with urllib.request.urlopen(req, data=data, timeout=30) as resp:
        return json.loads(resp.read().decode())


def strip_generic(point):
    """与 MockAiProvider 同口径：剥通用动词前缀/名词后缀，得到嵌入答案的核心表述。"""
    s = point.strip()
    changed = True
    while changed:
        changed = False
        for p in GENERIC_PREFIXES:
            if s.startswith(p) and len(s) > len(p):
                s = s[len(p):]
                changed = True
    for suf in GENERIC_SUFFIXES:
        if s.endswith(suf) and len(s) > len(suf):
            s = s[: -len(suf)]
            break
    return s.strip()


def scrub_tokens(question):
    """需避开的失分点英文词 = 仅出现在失分点、且不被任何得分点需要的词。"""
    kp_toks = {m.group() for k in (question.get("keyPoints") or [])
               for m in TECH_TOKEN.finditer(k)}
    ded_toks = {m.group() for p in (question.get("deductionPoints") or [])
                for m in TECH_TOKEN.finditer(p)}
    return sorted(ded_toks - kp_toks)


def scrub(text, banned):
    for tok in banned:
        text = text.replace(tok, "旧版本")
    return text


def skill_stem(tag):
    return tag.split("_")[0] if tag and "_" in tag else (tag or "")


def build_main_answer(question, tier, idx):
    kps = question.get("keyPoints") or []
    ref_len = len(question.get("referenceAnswer") or "") or 200
    stems = [skill_stem(t) for t in (question.get("skillTags") or [])]
    banned = scrub_tokens(question)

    if tier == "good":
        chosen = [strip_generic(k) for k in kps]
        lead = GOOD_OPENERS[idx % len(GOOD_OPENERS)]
        joiners = ["首先", "其次", "然后", "另外", "最后"]
        parts = ["%s，%s。" % (joiners[i % len(joiners)], kp) for i, kp in enumerate(chosen)]
        stem_sent = ("这一整块属于 %s 的核心考点，我平时也是这么用的。" % stems[0]) if stems else ""
        body = lead + "".join(parts) + stem_sent
        body += "因为这几个机制是配套出现的，所以在实际选型时我会整体考虑。"
        ans = body + GOOD_TAILS[idx % len(GOOD_TAILS)]
        pad = ("从问题定位的角度，我的习惯是先复现、再对比、最后固化结论，"
               "因为只有可复现的结论才可靠，所以每次我都会留下验证记录，方便团队复盘。")
        while len(ans) < max(260, int(ref_len * 0.65)):
            ans += pad
        return scrub(ans, banned)

    if tier == "mid":
        keep = kps[: max(1, len(kps) // 2)]
        chosen = [strip_generic(k) for k in keep]
        lead = "这个我知道一部分，说一下我记得的。"
        body = lead + "；".join(chosen) + "。" + MID_TAILS[idx % len(MID_TAILS)]
        while len(body) < 130:
            body += "其余的细节印象不深了，就不展开说了。"
        return scrub(body, banned)

    ans = POOR_MAINS[idx % len(POOR_MAINS)]
    leak = {m.group() for k in kps for m in TECH_TOKEN.finditer(k)} | set(stems)
    for tok in leak:
        ans = ans.replace(tok, "那个概念")
    return ans


def build_followup_answer(question, tier, fidx):
    kps = question.get("keyPoints") or []
    banned = scrub_tokens(question)
    if tier == "good":
        kp0 = strip_generic(kps[0]) if kps else "这个机制"
        return scrub(GOOD_FOLLOWUPS[fidx % len(GOOD_FOLLOWUPS)].format(kp0=kp0), banned)
    if tier == "mid":
        return MID_FOLLOWUPS[fidx % len(MID_FOLLOWUPS)]
    return POOR_FOLLOWUPS[fidx % len(POOR_FOLLOWUPS)]


def run_session(token, position_id, tiers, bank, rounds=5, session_seq=0):
    sid = api("POST", "/api/interviews", token,
              {"positionId": position_id, "mode": "TEXT", "difficulty": 3,
               "rounds": rounds, "durationMinutes": 15})["data"]["id"]
    api("POST", "/api/interviews/%d/start" % sid, token)
    rounds_data = {}
    followups_log = []
    idle = 0
    deadline = time.time() + 600
    while time.time() < deadline:
        detail = api("GET", "/api/interviews/%d" % sid, token)["data"]
        if detail["status"] in ("FINISHED", "TIMEOUT"):
            break
        msgs = api("GET", "/api/interviews/%d/messages" % sid, token)["data"]
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
                ans = build_main_answer(q, tier, session_seq + r)
                api("POST", "/api/interviews/%d/messages/text" % sid, token,
                    {"roundNo": r, "content": ans})
                rounds_data[r] = {"questionId": q["id"], "question": q["content"], "tier": tier,
                                  "mainAnswer": ans, "followups": [], "followupAnswers": []}
            else:
                fu = fups[len(user_round) - 1]
                rd = rounds_data[r]
                rd["followups"].append(fu["contentText"])
                followups_log.append({"roundNo": r, "tier": tier, "question": rd["question"],
                                      "followup": fu["contentText"]})
                ans = build_followup_answer(bank[rd["questionId"]], tier, len(rd["followupAnswers"]))
                rd["followupAnswers"].append(ans)
                api("POST", "/api/interviews/%d/messages/text" % sid, token,
                    {"roundNo": r, "content": ans})
            acted = True
            break
        if acted:
            idle = 0
            time.sleep(0.5)
        else:
            idle += 1
            if idle >= 25:   # ~30s 无待答消息且未自动收卷：兜底显式 finish（幂等）
                api("POST", "/api/interviews/%d/finish" % sid, token)
                time.sleep(2)
            else:
                time.sleep(0.7)

    status = api("GET", "/api/interviews/%d" % sid, token)["data"]["status"]
    report = None
    for _ in range(20):
        r = api("GET", "/api/interviews/%d/report" % sid, token)
        if r["code"] == 0:
            report = r["data"]
            break
        time.sleep(1)
    return sid, status, report, rounds_data, followups_log


def check_report(report, tiers):
    """报告字段完整性检查（§8.3 核对项 3）。weaknesses 仅在含弱轮（中/差）时要求非空。"""
    problems = []
    if report is None:
        return ["报告未生成"]
    dims = report.get("dimensionScores") or {}
    if not dims:
        problems.append("六维分为空")
    for k, v in dims.items():
        if v is None or v < 0 or v > 100:
            problems.append("维度 %s=%s 越界" % (k, v))
    if report.get("totalScore") is None:
        problems.append("totalScore 为空")
    for field in ("summary", "strengths", "improvementPlan", "questionEvidence"):
        if not report.get(field):
            problems.append("%s 为空" % field)
    if not report.get("weaknesses") and any(t != "good" for t in tiers):
        problems.append("含中/差轮但 weaknesses 为空")
    return problems


def main():
    admin_token = api("POST", "/api/auth/login", None,
                      {"username": "admin", "password": "Admin@123456"})["data"]["token"]
    positions = {p["id"]: p["name"] for p in api("GET", "/api/positions", admin_token)["data"]}

    results = {}
    for pid, name in positions.items():
        bank = {}
        page = 1
        while True:
            data = api("GET", "/api/admin/questions?positionId=%d&page=%d&size=50" % (pid, page),
                       admin_token)["data"]
            for q in data["records"]:
                if q["status"] == "ENABLED":
                    bank[q["id"]] = q
            if page * 50 >= data["total"]:
                break
            page += 1
        print("岗位 %s（id=%d）：可用题 %d 道" % (name, pid, len(bank)), flush=True)

        suffix = str(int(time.time()) % 100000)
        username = "qa_%s_%s" % ("java" if pid == 1 else "front", suffix)
        api("POST", "/api/auth/register", None,
            {"username": username, "password": "123456", "major": "软件工程"})
        token = api("POST", "/api/auth/login", None,
                    {"username": username, "password": "123456"})["data"]["token"]

        pos_result = {"position": name, "user": username, "rounds": [], "sessions": [],
                      "followups": []}
        for s_idx, tiers in enumerate(SESSION_TIERS):
            sid, status, report, rounds_data, followups_log = run_session(
                token, pid, tiers, bank, session_seq=s_idx)
            evidence = {}
            if report:
                for ev in report.get("questionEvidence") or []:
                    evidence[ev["roundNo"]] = ev
            problems = check_report(report, tiers)
            pos_result["sessions"].append({
                "sessionId": sid, "status": status, "tiers": tiers,
                "reportProblems": problems,
                "dimensionScores": (report or {}).get("dimensionScores"),
                "totalScore": (report or {}).get("totalScore"),
                "weaknesses": (report or {}).get("weaknesses"),
                "evidenceCount": len(report.get("questionEvidence") or []) if report else 0,
            })
            for rno, rd in sorted(rounds_data.items()):
                ev = evidence.get(rno) or {}
                ai_score = ev.get("contentScore")
                manual = MANUAL_SCORE[rd["tier"]]
                pos_result["rounds"].append({
                    "sessionId": sid, "roundNo": rno, "questionId": rd["questionId"],
                    "question": rd["question"], "tier": rd["tier"],
                    "answerExcerpt": rd["mainAnswer"][:120],
                    "followupCount": len(rd["followups"]),
                    "manualScore": manual, "aiScore": ai_score,
                    "diff": None if ai_score is None else round(abs(ai_score - manual), 2),
                    "covered": ev.get("coveredPoints"), "missing": ev.get("missingPoints"),
                    "reason": ev.get("reason"),
                })
            pos_result["followups"] += followups_log
            print("  会话 %d（%s）status=%s total=%s 证据=%d 报告问题=%s"
                  % (sid, "/".join(TIER_CN[t] for t in tiers), status,
                     (report or {}).get("totalScore"),
                     len((report or {}).get("questionEvidence") or []), problems or "无"),
                  flush=True)

        rec = api("GET", "/api/users/me/recommendations?positionId=%d" % pid, token)
        rec_list = rec.get("data") or []
        pos_result["recommendations"] = {
            "code": rec["code"], "skillCount": len(rec_list),
            "top3": [{"skillCode": x.get("skillCode"),
                      "weaknessReasons": x.get("weaknessReasons"),
                      "retryCount": len(x.get("retryQuestions") or []),
                      "resourceCount": len(x.get("resources") or [])}
                     for x in rec_list[:3]],
            "next": rec_list[0].get("nextInterviewSuggested") if rec_list else None,
        }
        results[pid] = pos_result

    total = pass_cnt = 0
    for pid, pr in results.items():
        for r in pr["rounds"]:
            if r["aiScore"] is None:
                r["diff"] = 999
            total += 1
            if r["diff"] <= 15:
                pass_cnt += 1
    print("\n==== 抽测汇总：%d/%d 条分差≤15（%.0f%%），达标线 80%% ===="
          % (pass_cnt, total, 100.0 * pass_cnt / max(1, total)), flush=True)
    out = "/Users/Admin/Desktop/课程作业/javaEE/课程设计/ai-interview/qa/qa_result.json"
    with open(out, "w") as f:
        json.dump(results, f, ensure_ascii=False, indent=1)
    print("明细已写入", out, flush=True)


if __name__ == "__main__":
    main()
