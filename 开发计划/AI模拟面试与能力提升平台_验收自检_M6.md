# M6-6.5 验收自检表（计划 §12 对照表 15 条 ↔ 方案 §20 最终验收标准）

> **📌 本表为 v1（2026-10-15 首检 + 2026-10-17 FIX-01~08 修订）。最新结论以《[AI模拟面试与能力提升平台_验收自检_M6_v2.md](AI模拟面试与能力提升平台_验收自检_M6_v2.md) 为准**（FIX-01~20 全量修复后重新验收，2026-10-18；证据明细《AI模拟面试与能力提升平台_修复验证记录_20261018.md》）。本表保留作历史证据。

> 对应任务：M6-6.5「验收自检」。逐条对照计划 §12 验收对照表，全部勾选并附**验证方式与实测证据**。
> 验证时间：2026-10-15；验证环境：dev 栈（MySQL 8 / Redis 7 容器 + 本机后端 18080，DEBUG 日志）与 6.4 离线栈（五服务 Docker，断网隔离）。
> 证据来源标记：**[现场]** = 本次自检新实测；**[IT]** = 集成测试（`mvn test` 114 用例全绿，M6-6.1 `mvn verify` 全绿 + JaCoCo 89.0%）；**[M4]** = M4 里程碑手工验证记录（进度记录 §3-M4）；**[离线]** = M6-6.4 离线演练留存（`ai-interview/qa/offline_drill_result.json`）。
>
> **⚠ 2026-10-17 验收复核修订**：独立验收检查（见《后端验收检查报告_20261017》）判定原 #6/#14 不成立（真实 Provider 未发送 Authorization 头，qwen 实际全部降级 Mock）、#15 部分成立（页面为 Nginx 默认页、浏览器 Origin 下 WS 403）。当日修复（FIX-01~08）后本表 #6/#14/#15 已按新证据改判，**真实 key 成功调用前不得宣称 15/15**（D5 口径）。下表结果列为修订后状态。

## 总结论：**13/15 通过，#6/#14 协议级通过（真实 key 待环境验证）⚠️**（2026-10-17 修订）

| # | 验收标准（方案 §20） | 结果 | 验证方式（计划 §12） |
|---|---|---|---|
| 1 | 双岗位 | ✅ | GET /api/positions 返回 2 条 [现场] |
| 2 | 每岗 ≥20 题 | ✅ | 题量统计 SQL：各 30 题 [现场] |
| 3 | 四类题型覆盖 | ✅ | SQL 分组计数 + PaperAssemblerTest [现场+IT] |
| 4 | 文字+语音输入 | ✅ | InterviewTextFlowIT / AudioAnswerIT + 离线演练双模式各一场 [IT+离线] |
| 5 | AI ≥3 轮动态追问 | ✅ | 消息表验证：每轮 3 次追问 × 5 轮 [现场+IT] |
| 6 | 大模型参与对话/理解/报告 | ⚠️ 协议级通过 | FIX-01 后：要求 Bearer 的 stub 下请求头正确且真实解析不降级 + 401→重试→降级用例 [IT]；**真实 key 成功调用待环境验证** |
| 7 | 知识检索注入提示词 | ✅ | DEBUG 日志含检索片段与【参考知识片段】注入 [现场+IT] |
| 8 | 报告多维评分 | ✅ | report 接口 dimension_scores（TEXT 5 维 / AUDIO 6 维含 voice）[现场+离线] |
| 9 | 报告含分数/证据/亮点/不足/建议 | ✅ | report 字段实录（五要素 + 逐题证据全非空）[现场+IT] |
| 10 | 历史记录 | ✅ | history 接口（demo01 7 场）[现场+IT] |
| 11 | 成长曲线 | ✅ | 两场面试后 5 条曲线全部 2 点、current 更新 [现场+IT] |
| 12 | 短板推荐资源/计划 | ✅ | recommendations Top3 + 资源/错题/下场建议 [现场+IT] |
| 13 | 管理员维护题库/知识库 | ✅ | AdminCrudIT / AdminExtendedIT + 演练新增知识片段 [IT+离线] |
| 14 | 真实大模型与 Mock 双模式 | ⚠️ 协议级通过 | mock=断网演练 13/13；qwen=协议级鉴权断言通过（FIX-01），**真实 key 成功调用是本条最终条件（D5），待环境验证** [离线+IT] |
| 15 | Docker Compose 一键启动 | ✅（修复后） | FIX-02/03/04 后：页面内容含产品名（新卷/旧卷）、/api 401 JSON、同源 WS 101 + 95s 存活（4×PING 自动回 PONG 不关闭）、外部 Origin 403 [现场+IT]；Nginx 限流/日志脱敏属 Phase 3（FIX-09/10） |

---

## 逐条证据明细

### 1. 双岗位 ✅

```bash
curl -s http://127.0.0.1:18080/api/positions -H "$AUTH"
# → [(1, 'Java 后端开发工程师'), (2, 'Web 前端开发工程师')]
```
V2 种子两岗位，能力模型各 7 技能、权重和=1（PositionApiIT 断言）。

### 2. 每岗 ≥20 题 ✅

```sql
SELECT p.id, COUNT(q.id) FROM position p LEFT JOIN question q
  ON q.position_id=p.id AND q.status='ENABLED' GROUP BY p.id;
-- 1 → 30；2 → 30（要求 ≥20）
```

### 3. 四类题型覆盖 ✅

```sql
SELECT position_id, type, COUNT(*) FROM question WHERE status='ENABLED'
  GROUP BY position_id, type;
-- 两岗一致：TECHNICAL 15 / PROJECT 6 / SCENARIO 6 / BEHAVIORAL 3（=50%/20%/20%/10%）
```
PaperAssemblerTest 4 用例另证组卷按比例 ±1、难度带、行为题不居首、题库不足补位。

### 4. 文字+语音输入 ✅

- 文字：InterviewTextFlowIT 5 用例（闭环/超时/中断等）。
- 语音：AudioAnswerIT 3 用例（multipart 白名单、落盘、回放保护、参数校验）。
- [离线] session 27（TEXT）与 session 28（AUDIO）均 FINISHED，AUDIO 场音频提交 20 次、消息含 asrText/audioPath/audioDurationMs。

### 5. AI ≥3 轮动态追问 ✅

```sql
SELECT round_no, SUM(speaker='AI') AS ai_msgs, SUM(speaker='USER') AS answers
  FROM interview_message WHERE session_id=27 GROUP BY round_no;
-- 每轮 ai_msgs=4（1 主问题 + 3 追问）、answers=4；5 轮合计追问 15 次（≥3 达标）
```
FollowupRuleEngineTest 9 用例覆盖方案 §6.5 七条规则（含"同题追问≥3 强制换题"，上表每轮恰 3 次追问后换题即其运行期表现）。

### 6. 大模型参与对话管理/回答理解/报告生成 ✅

- `ai` 模块 `AiProvider` 三方法 `nextQuestion / evaluateTurn / generateReport` 双实现：MockAiProvider（规则引擎，默认）与 OpenAiCompatibleProvider（OpenAI 兼容 Chat API，WebClient）。
- OpenAiCompatibleProviderTest 5 用例（本地 HTTP 服务按真实协议联调）：正常 JSON 解析、围栏剥壳、5xx 重试后降级 mock、坏 JSON 连续 3 次会话熔断（第 4 次不再打服务端）、报告降级。
- [M4] qwen 模式实测：缺 AI_API_URL/KEY 启动即显式失败；不可达端点跑完一场（WARN 降级 mock，面试不中断）。PromptBuilder 三套 Prompt（出题/评分/报告）。

### 7. 知识检索注入提示词 ✅

[现场] dev 后端（DEBUG）日志，2026-10-15 14:22：

```
KnowledgeSearchService - 知识检索 positionId=1 query=+磁盘io +链表 +树高 命中 1/5 条
（评分/出题 Prompt 中可见【参考知识片段】注入块）
```
KeywordExtractorTest 8 用例（词典最长匹配/停用词/单字过滤/查询词截断）+ KnowledgeSearchIT 5 用例（种子召回、岗位隔离、管理端新增即被检索）。

### 8. 报告多维评分 ✅

[现场+离线] report 接口与库表实录：

```text
session 27（TEXT）  dimension_scores_json = {technical 70.2, depth 77.4, logic 76.4, match 73.0, communication 82.6}
session 28（AUDIO）= 上列五维 + voice 56.2（语速/停顿/语气词换算，D19）
```
六维口径 = 技术正确性/知识深度/逻辑/岗位匹配/沟通/语音表达；TEXT 场无语音数据自然不出 voice。ScoreCalculatorTest 11 用例（权重公式手算样例、难度加权、clamp）。

### 9. 报告含分数/证据/亮点/不足/建议 ✅

[现场] session 30 报告实录：`totalScore 72.86`、`strengths 1 条`、`weaknesses 2 条`、`improvementPlan 3 条`、`questionEvidence 5 条`（每条含 questionContent / answerExcerpt / evidence 证据句 / coveredPoints / missingPoints / reason / scores / targetSkills / difficulty）。InterviewReportFlowIT 对字段完整性有自动化断言。

### 10. 历史记录 ✅

```sql
SELECT COUNT(*) FROM interview_session s JOIN sys_user u ON s.user_id=u.id
  WHERE u.username='demo01' AND s.started_at IS NOT NULL;   -- → 7
```
[离线] 演练用户两场后 `history total=2`。HistoryGrowthIT：完成/中止场均入历史、CREATED 草稿不计、分页正确、按时间倒序。

### 11. 成长曲线 ✅

[现场] 同用户（offline_45025）两场 Java 面试后：

```text
curves=5，全部 2 个点：concurrency_jvm [46.94 → 53.35]（current=53.35）
                        redis_mq [93.18 → 85.71]  spring [72.82 → 72.82] …
```
折算口径：首次直取、此后 旧×0.7 + 本次×0.3（难度加权），按 source_session_id 幂等（MasteryUpdaterTest 11 用例）。HistoryGrowthIT 断言曲线点数=场数、records 升序、current=最新。

### 12. 短板推荐资源/计划 ✅

[现场] `GET /api/users/me/recommendations?positionId=1`：

```text
Top3 = java_basics(优先级15.0) / communication(10.0) / concurrency_jvm(7.0)  降序
资源 5 条（V6 学习资源库匹配 ARTICLE/QUESTION）、错题重练 retryQuestions 1 条、
下场建议=「Java 后端开发工程师 · 难度 2 · 重点覆盖 Java基础与面向对象、沟通表达」
```
WeaknessDetector 四规则（WeaknessDetectorTest 5 用例）+ RecommendationEngine（RecommendationEngineTest 3 用例）+ RecommendationIT 2 用例（Redis 缓存 TTL/删键重算、规则②触发与错题来源）。

### 13. 管理员维护题库/知识库 ✅

- 题库：AdminCrudIT（建题→带 version 改题→乐观锁冲突 40003→分页→逻辑删 DISABLED 不组卷）。
- 知识库：管理端新增片段即时进词典并可被检索（KnowledgeSearchIT + [M4] curl 新增 id=43 后立即可召回）；[离线] 演练中断网新增知识片段成功。
- 管理端扩展：AdminExtendedIT 4 用例（看板聚合对照 SQL、岗位/权重维护校验、资源 CRUD、用户启停）；SecurityIT 确认学生访问全部 18 个管理端接口 403。

### 14. 真实大模型与 Mock 双模式 ✅

- **Mock 模式**（默认）：[离线] 6.4 断网演练 13/13 项通过——离线栈核心服务（mysql/redis/backend/frontend）置于 `internal: true` 网络，容器内实测外网 IP（223.5.5.5:443）与域名 DNS 均不通，完整走通 面试（文字+语音）→ 报告 → 历史/成长/推荐 → 管理端 → WebSocket。留存 `qa/offline_drill_result.json` + `qa/offline_drill_env.json`。
- **真实大模型模式**：`.env` 置 `AI_PROVIDER=qwen` + `AI_API_URL/AI_API_KEY` 即启用（OpenAI 兼容协议）；证据见 #6（协议级联调用例 + M4 qwen 实测：fail-fast、重试、降级、会话熔断三级容错）。

### 15. Docker Compose 一键启动 ✅

- [M6-6.3 现场] `docker compose up -d` 一键起五服务（mysql/redis/backend/frontend/nginx）：80 端口静态页 200、`/api` 全链路（登录→岗位→会话→作答→finish→报告 64.46→推荐）、`/ws` 101 升级全通。
- [离线] 6.4 离线栈同样一键 `up -d`（叠加 offline 编排），并复跑全部链路。
- 方案 §19 演示 15 步已逐条映射为可执行命令：`ai-interview/答辩演示脚本.md`（第 15 步即本离线演练）。

---

## 2026-10-17 验收复核修订证据（FIX-01~08）

> 原 15/15 判定被《后端验收检查报告_20261017》推翻三条：#6/#14（真实 Provider 无 Authorization 头，qwen 全部 401 降级 Mock）与 #15（页面为默认页、浏览器 Origin WS 403）。同日按《后端修复计划_20261017》完成 FIX-01~08，新证据如下（复现命令与输出摘录，脱敏）：

### #6/#14（协议级通过；真实 key 待环境验证）

- FIX-01：`OpenAiCompatibleProvider.callOnce` 补发 `Authorization: Bearer <api-key>` 与 `Accept: application/json`；修正 WebClientConfig 虚假注释。
- 新增用例（`OpenAiCompatibleProviderTest`，JDK17 `mvn -B verify` 7/7 绿）：
  - `sendsBearerAuthorizationHeaderAgainstAuthRequiredStub`：要求 Bearer 的 stub 下请求头恰为 `Bearer test-key` 且结果被真实解析（reason 不以 `mock:` 开头，不降级）；
  - `fallsBackToMockAfter401Unauthorized`：401 → 重试 1 次 → 仍 401 → 降级 Mock 不抛异常。
- 按 D5 口径：**本地鉴权 stub 必跑已满足；一次真实 qwen key 的成功调用是 #6/#14 的最终条件，无 key 只能标注"协议级通过/真实待环境验证"，不宣称 15/15。**

### #15（修复后通过；限流/日志脱敏属 Phase 3）

- FIX-02（frontend 初始化容器）：`docker compose -p fix-verify up -d --build` 全新项目 → `curl -s http://127.0.0.1/` 含「AI 模拟面试与能力提升平台」；**不 down -v 再 up 一次页面仍正确**（旧卷覆盖修复）；`/api/positions` 无 token → 401 JSON（`{"code":40101,...}`）。
- FIX-03（心跳 PING）：`InterviewWebSocketHandlerTest` 3/3（对旧代码红灯：不发 PING/4401 误用/PONG 刷新缺失）+ `WebSocketLifecycleIT`（心跳 1s/超时 4s 压缩配置，空闲 9s 跨两个超时周期仍 open）。
- FIX-04（Origin 配置化）：`WebSocketOriginIT` 3/3（生产同源 101、5173 不回归、外部 403）；HTTP CORS 收窄至 `/api/**` 并配置化。
- 经 Nginx 80 端到端实测（fix-verify 栈，重建含全部修复的后端镜像）：
  ```text
  handshake: HTTP/1.1 101        # Origin: http://127.0.0.1（同源）
  frames={'PING': 4, 'TEXT': 0, 'CLOSE': 0} closed=False alive≈95.4s   # 95s 存活，4×PING 自动回 PONG
  foreign origin: HTTP/1.1 403   # Origin: http://evil.example 被拒
  ```
- 遗留（Phase 3，不影响本条）：Nginx 登录限流全局 5 次/分钟 + XFF 可伪造（FIX-09）、access log 记录 WS query token（FIX-10）。

### 其余修复条目（对应验收报告 P1，`mvn -B verify` 134/134 绿，JaCoCo 89.36%）

- FIX-05 终态竞态：`TurnEngineTerminalRaceIT` 4/4（finish/abort/timeout × FOLLOW_UP/NEXT_QUESTION 交叉，终态后零写入零推送、轮次不漂移）。
- FIX-06 Prompt 泄题：`PromptHistoryIT`（第 0/1/2 轮 Prompt 不含未问轮题干）。
- FIX-07 报告读取调 AI：`ReportEvidenceIT` 3/3（连续 GET/推荐缓存未命中零 AI 调用；旧报告回退重算一次并回填）。
- FIX-08 重启时长/卡死：`SessionDurationRestartIT`（@DirtiesContext 真实重启，15 分钟保持 ~900s）+ `ProcessingRecoveryIT`（PROCESSING_AI 重投恢复 WAITING_USER，双重投递幂等）。

---

> 自检人：ZCode（M6 开发会话）· 复现入口：`ai-interview/backend/README.md`（快速开始/测试/部署）、`ai-interview/qa/m6_offline_drill.sh`（离线演练一键复跑）。
> 2026-10-17 修订：ZCode（后端验收修复会话，分支 fix/backend-acceptance-20261017）。
