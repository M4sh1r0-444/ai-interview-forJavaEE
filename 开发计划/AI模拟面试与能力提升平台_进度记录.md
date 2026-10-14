# AI模拟面试与能力提升平台 —— 进度记录

> 本文档是项目的**唯一进度事实源**，实时记录完成进度与已交付产物。
> 基线文档：《AI模拟面试与能力提升平台_后端开发计划.md》（下称"计划"），任务编号与计划 §9 逐一对应。

## 维护规则

1. **何时更新**：每完成一个任务立即勾选状态；每次开发会话结束前更新"总体概览""产物清单""更新历史"三处；发现新问题/新决策当场登记。
2. **状态标记**：`⬜ 未开始` / `🔧 进行中` / `✅ 已完成` / `⏸ 暂停(注明原因)` / `❌ 取消(注明原因)`。
3. **完成标准**：任务勾选 ✅ 前必须先满足计划中该任务的"完成标准"（可运行验证），验证方式记入备注列。
4. **偏差处理**：实际实现与计划不一致时，不修改计划文档，在"变更与决策记录"登记偏差及原因，后续以本文档为准。

---

## 1. 总体概览

| 项 | 当前值 |
|---|---|
| 当前阶段 | M4 开发完成（待进入 M5） |
| 当前里程碑 | M4 ✅（2026-10-14 完成） |
| 整体进度 | 计划阶段 100%；开发阶段 4/6 里程碑（M1 ✅、M2 ✅、M3 ✅、M4 ✅） |
| 上次更新 | 2026-10-14 |
| 上次更新人 | ZCode（M4 开发会话） |

### 里程碑状态

| 里程碑 | 内容（计划 §9） | 状态 | 开始日期 | 完成日期 |
|---|---|---|---|---|
| M1 | 基础工程 + 认证 + 岗位题库 | ✅ 已完成 | 2026-10-13 | 2026-10-13 |
| M2 | 文字面试闭环 | ✅ 已完成 | 2026-10-13 | 2026-10-13 |
| M3 | 评分 + 报告 + 历史 | ✅ 已完成 | 2026-10-14 | 2026-10-14 |
| M4 | 语音 + 知识库 RAG | ✅ 已完成 | 2026-10-14 | 2026-10-14 |
| M5 | 个性化 + 管理端补全 | ⬜ 未开始 | | |
| M6 | 测试 + 部署 + 验收 | ⬜ 未开始 | | |

---

## 2. 已交付产物清单

| # | 产物 | 路径 | 交付日期 | 状态 | 说明 |
|---|---|---|---|---|---|
| 1 | 系统实现方案（需求基线） | `AI模拟面试与能力提升平台_系统实现方案.md` | —（项目启动时已有） | ✅ 基线冻结 | 全部需求的唯一来源，1105 行 |
| 2 | 后端开发计划 v1.0 | `AI模拟面试与能力提升平台_后端开发计划.md` | 2026-10-13 | ✅ 已交付 | 1235 行；9 张表完整 DDL、31 接口清单、M1~M6 任务分解、验收对照表 |
| 3 | 进度记录（本文档） | `AI模拟面试与能力提升平台_进度记录.md` | 2026-10-13 | 🔧 持续维护 | |
| 4 | 开发期容器编排（M1-1.1） | `ai-interview/docker-compose.dev.yml` | 2026-10-13 | ✅ 已交付 | MySQL 8.0（3306）+ Redis 7（宿主机 6380），两容器 healthy |
| 5 | 后端 Maven 工程（M1-1.2~1.7） | `ai-interview/backend/` | 2026-10-13 | ✅ 已交付 | Spring Boot 3.3.12 单体；common/auth/position/question/admin 五包；`mvn test` 全绿（10 用例） |
| 6 | Flyway 脚本 V1~V4（M1-1.3/1.8/1.9） | `backend/src/main/resources/db/migration/` | 2026-10-13 | ✅ 已交付 | V1 建 9 表；V2 账号+2 岗位+能力模型；V3/V4 两岗位各 30 题种子（已迁移验证） |
| 7 | 接口调试脚本（M1 起） | `backend/api.http` | 2026-10-13 | 🔧 持续维护 | IDEA HTTP Client，与计划附录 C 同步；M2 追加用例 12~18（面试闭环 + WS），M3 追加 19~21（finish/report/history），M4 追加 22~25（音频回答/回放 + 管理端知识库 + Provider 观测） |
| 8 | 文字面试闭环模块（M2-2.1~2.7） | `backend/` interview/ai 包等 | 2026-10-13 | ✅ 已交付 | interview（实体/状态机/服务/TurnEngine/规则引擎/WS/定时器）+ ai（Mock/Prompt/解析容错）；`mvn test` 38 用例全绿 |
| 9 | 评分+报告+历史模块（M3-3.1~3.5） | `backend/` assessment/growth 包等 | 2026-10-14 | ✅ 已交付 | assessment（ScoreCalculator/ReportService/EvaluationReport/TurnEvaluationStore）+ growth（history 接口）+ Mock 评分引擎与报告拼装 + finish/重试/TIMEOUT 收卷；`mvn test` 56 用例全绿（M3 新增 18） |
| 10 | 语音+知识库RAG+真实Provider模块（M4-4.1~4.6） | `backend/` knowledge/interview.storage/ai 包等 | 2026-10-14 | ✅ 已交付 | knowledge（实体/FULLTEXT检索/KeywordExtractor/管理服务）+ AudioStorageService + 音频回答/回放接口 + VoiceMetricsCalculator（AUDIO 场 voice 维度）+ V5 种子 42 条 + OpenAiCompatibleProvider（三级容错/会话熔断）+ WebClientConfig；`mvn test` 85 用例全绿（M4 新增 29） |

> 后续产物（backend 工程、Flyway 脚本、docker-compose 等）交付时在此表**按序追加**，注明对应里程碑任务编号。

---

## 3. 里程碑任务明细

### M1 基础工程 + 认证 + 岗位题库 ✅（2026-10-13 完成）

| 任务 | 内容 | 状态 | 完成日期 | 验证记录/备注 |
|---|---|---|---|---|
| 1.1 | Compose 起 MySQL/Redis | ✅ | 2026-10-13 | `docker compose -f docker-compose.dev.yml ps`：aiinterview-mysql / aiinterview-redis 均 healthy；Redis 宿主机端口改 6380（见决策 D6） |
| 1.2 | 工程骨架（pom/yml/主类/common 包） | ✅ | 2026-10-13 | `mvn spring-boot:run`（JDK 17）启动 8.4s 无错；`GET /actuator/health` = {"status":"UP"}；common 包含 Result/ResultCode/BizException/GlobalExceptionHandler/TraceIdFilter/JwtUtils/JsonUtils/SecurityConfig/MybatisPlusConfig/AuditLogAspect |
| 1.3 | Flyway V1 建表 + V2 账号/岗位 | ✅ | 2026-10-13 | 启动自动迁移 V1~V4 成功；`SHOW TABLES` 业务表=9 张；V2 含 admin/demo01 账号与 2 岗位（技能权重和=1，JSON_TABLE 校验通过） |
| 1.4 | Security + JWT + 黑名单 + 限流 | ✅ | 2026-10-13 | 附录 C 用例 1~3 通过（注册/登录/岗位，curl 全流程）；用例 4~5 依赖 M2 interview 模块，M1 以等价安全用例补验：登出后旧 token 401、无 token 401、篡改 token 401、学生访问 admin 403、限流 42901（5 次/分/IP）、禁用账号 10003 |
| 1.5 | auth 接口 5 个 | ✅ | 2026-10-13 | AuthFlowIT 2 用例通过（注册→登录→me→PUT me→logout→401；重复注册 10001/错误密码 10002/参数 40001） |
| 1.6 | position 接口 3 个 | ✅ | 2026-10-13 | PositionApiIT 2 用例通过（两岗位可见；每岗 skills 权重和=1；未登录 401）；curl 验证列表/详情(skillCount=7)/技能清单 |
| 1.7 | 题库 CRUD 4 个 | ✅ | 2026-10-13 | AdminCrudIT CRUD 部分通过：建题(version=1)→带 version 改题(→2)→旧 version 冲突 40003→列表分页→逻辑删 status=DISABLED；学生 403 |
| 1.8 | 种子题 V3（Java 岗 30 题） | ✅ | 2026-10-13 | 断言 SQL：position 1 共 30 题（TECHNICAL 15/PROJECT 6/SCENARIO 6/BEHAVIORAL 3）；全部含参考答案(≥150字)/得分点(≥3)/失分点(≥2)/追问模板(≥2) |
| 1.9 | 种子题 V4（前端岗 30 题） | ✅ | 2026-10-13 | 同上，position 2 共 30 题，比例 15/6/6/3，字段完整性同上通过 |

> 测试汇总：`mvn test` = 10 用例全绿（JwtUtilsTest 4、AuthFlowIT 2、PositionApiIT 2、AdminCrudIT 2）。

### M2 文字面试闭环 ✅（2026-10-13 完成）

| 任务 | 内容 | 状态 | 完成日期 | 验证记录/备注 |
|---|---|---|---|---|
| 2.1 | interview 实体/建会话/start + 状态机 | ✅ | 2026-10-13 | curl：建会话(CREATED)→start 返回首题（附背景合并题干）；重复 start 10005；状态机条件更新乐观锁（casStatus SQL）；RUNNING 为事务内中间态外部不可见 |
| 2.2 | 组卷 assemblePaper | ✅ | 2026-10-13 | PaperAssemblerTest 4 用例通过（rounds 5~8 比例 ±1、难度均值差 ≤1、行为题不在首位、题库不足 TECHNICAL 补位、技能贪心覆盖）；IT 全流程 5 轮跑通 |
| 2.3 | MockAiProvider + PromptBuilder + 解析容错 | ✅ | 2026-10-13 | AiResponseParserTest 5 用例通过（正常/围栏/半截/前后缀文本/无 JSON）；mock 追问按 followupTemplates 轮转 + 通用池循环，联调中持续产出（IT 12 轮回答验证）；qwen 显式回退 mock（决策 D14） |
| 2.4 | TurnEngine 文字回答编排 | ✅ | 2026-10-13 | curl + IT：POST 文字回答受理 {accepted:true}→异步 aiExecutor 产出追问落库→状态回 WAITING_USER；PROCESSING_AI 竞争（abort/timeout）时丢弃产出；异常恢复回退 WAITING_USER + WS ERROR |
| 2.5 | 追问规则引擎（7 条规则） | ✅ | 2026-10-13 | FollowupRuleEngineTest 9 用例通过（七条规则各一 + 长回答模糊变体 + 默认策略）；追问≥3 强制换题在 InterviewTextFlowIT 联调可见（轮 0 连续模糊回答：3 次追问后第 4 次强制进入轮 1） |
| 2.6 | WebSocket 推送 | ✅ | 2026-10-13 | Node 22 原生 WebSocket 实测依次收到 QUESTION（首题）/FOLLOW_UP（追问）/TIMER（remainSeconds=1199）；WebSocketPushIT 2 用例通过（推送链路 + 无效 token close 4401）；连接鉴权=验签+黑名单+归属；30s PING/60s 踢除心跳 |
| 2.7 | 定时器与 abort | ✅ | 2026-10-13 | InterviewTextFlowIT.timeout：注入过期截止时间→自动 TIMEOUT（finishedAt 回填）→答题 10005；abort 终态后答题/再 abort 均 10005（curl 复验）；重启恢复按 started_at+60min 上界重挂定时器 |

> M2 测试汇总：`mvn test` = **38 用例全绿**（M1 10 + M2 28：InterviewStatusTest 3、PaperAssemblerTest 4、FollowupRuleEngineTest 9、AiResponseParserTest 5、InterviewTextFlowIT 5、WebSocketPushIT 2）。测试类数 10 个，达成计划"单元 ≥10 测试类"下限的类数口径（含集成 6 个）。

### M3 评分 + 报告 + 历史 ✅（2026-10-14 完成）

| 任务 | 内容 | 状态 | 完成日期 | 验证记录/备注 |
|---|---|---|---|---|
| 3.1 | 逐题评分 + 六维聚合 | ✅ | 2026-10-14 | ScoreCalculatorTest 11 用例通过（手算样例 78.28 误差≤0.01、缺失维度权重归一化、岗位自定义权重生效、clamp 0~100、难度加权聚合 0.6~1.4、应变分映射）；MockAiProvider.evaluateTurn 规则评分引擎（key_points 命中率→technical、长度比→depth、结构信号→logic、语气词密度→communication、技能标签→match）；TurnEngine 每答评分入 TurnEvaluationStore，评分输入接入追问规则引擎（D15 唤醒，联调验证每轮 R1/R4 追问×3 后 R7 换题） |
| 3.2 | 报告生成与落库 | ✅ | 2026-10-14 | InterviewReportFlowIT.fullInterviewGeneratesCompleteReport：完整一场 → report 返回完整 ReportVO（五维均 0~100、totalScore 回填 session、亮点/不足/建议/评语/逐题证据含 covered/missing/证据句/理由）；WebSocketPushIT.finishPushesProgressAndReportReady：finish → WS 依次收 PROGRESS(FINISHED)/REPORT_READY{reportId}；curl 冒烟（18080）跑通 finish→report 63.77 分→history |
| 3.3 | finish / TIMEOUT 自动收卷 | ✅ | 2026-10-14 | InterviewReportFlowIT：中途 finish 与注入过期定时器 TIMEOUT 均自动生成报告（TIMEOUT 报告含轮 0 逐题证据）；CREATED 会话 finish 10005；重启恢复路径实测——昨日遗留 WAITING_USER 会话（id=2）启动恢复自动 TIMEOUT 并出报告 55.66 分；InterviewTextFlowIT 5 用例适配新规则动态后全绿 |
| 3.4 | history 接口 | ✅ | 2026-10-14 | HistoryGrowthIT.historyListsStartedSessionsNewestFirst：完成场（FINISHED+总分）+中止场共 2 条按时间倒序，CREATED 未开始会话不入历史，分页 total=2/size=1 正确，未登录 401、新用户空历史；curl 冒烟 history 返回 3 条（含 M2 遗留 ABORTED/TIMEOUT 场） |
| 3.5 | 报告失败重试 | ✅ | 2026-10-14 | ReportRetryIT：@SpyBean 使 generateReport 首调抛 AiFallbackException（模拟断开 AI）→ report 10004 → 再次 POST finish（重试入口）→ 报告生成成功 code=0 → 再 finish 幂等直接返回 reportId；未 finish/ABORTED 场 report 10004（InterviewReportFlowIT.reportNotReadyReturns10004） |

> M3 测试汇总：`mvn test` = **56 用例全绿**（M2 38 + M3 新增 18：ScoreCalculatorTest 11、InterviewReportFlowIT 4、ReportRetryIT 1、HistoryGrowthIT 1、WebSocketPushIT 增 1）；测试类 14 个。六维中 voice 维度排期 M4（AUDIO 场语音指标）。

### M4 语音 + 知识库 RAG ✅（2026-10-14 完成）

| 任务 | 内容 | 状态 | 完成日期 | 验证记录/备注 |
|---|---|---|---|---|
| 4.1 | 音频上传与存储 | ✅ | 2026-10-14 | AudioAnswerIT 3 用例通过：AUDIO 会话 multipart 提交（webm/wav/mp3 白名单 + Content-Type 复核 + UUID 命名落盘 `uploads/audio/yyyyMM/`）→ 消息含 inputType/asrText/audioPath/audioDurationMs/keywords；非音频扩展名、text/plain、缺 asrText、durationMs 越界均 40001 且状态不受影响；TEXT 会话提交 40002；受保护回放接口本人 200/他人 40401（D21）；超 10MB 由 multipart 全局限制 + GlobalExceptionHandler → 40001。curl（18080）实测落盘 `audio/202610/<uuid>.webm`、回放 200（35 bytes，audio/webm）、TRANSCRIPT 推送按计划 §5.6 |
| 4.2 | 语音指标计算 | ✅ | 2026-10-14 | VoiceMetricsCalculatorTest 8 用例通过（600 字/200 秒 → 语速 180；空转写/零时长不除零；停顿标记计数；满速带 90~100；过慢/过快封顶 60；无时长中性 55；分段线性边界值）。TurnEngine 运行期与 ReportService 重算路径均对 AUDIO 场注入 voice 维度；curl AUDIO 场报告 dimensionScores 含 voice=60.0（语速极端区封顶生效，决策 D19） |
| 4.3 | 知识检索（V5 种子 + FULLTEXT） | ✅ | 2026-10-14 | V5__seed_knowledge_chunks.sql 两岗位各 21 条（7 技能 × 3，content 300~800 字、keywords 8+ 个、skill_tags 对齐能力模型），dev/test 库 Flyway 自动迁移。SQL 手验（utf8mb4 客户端）：`+缓存 +击穿`/`+互斥锁 +逻辑过期` 召回「缓存穿透、击穿、雪崩的区别与对策」为 Top1（score 12/33）；岗位隔离（岗位 2 查 `+缓存 +redis` 为空）；KnowledgeSearchIT 5 用例含种子召回/无匹配空列表 |
| 4.4 | 知识片段注入 Prompt | ✅ | 2026-10-14 | KeywordExtractor（词典法：能力模型技能名/编码 + 知识库 keywords 聚合、最长匹配去重、英文技术词正则、停用词与单字过滤、TopN10、BOOLEAN 查询词 >4 取前 4）→ keywords_json 入库（文字/音频回答统一）→ TurnEngine 检索 Top5 注入 evaluateTurn 与 nextQuestion 上下文。DEBUG 日志实测：`知识检索 positionId=1 query=+readview +可重复读 +mvcc +版本链 命中 2/5 条`，prompt 含【参考知识片段】《…》；KeywordExtractorTest 8 用例通过 |
| 4.5 | admin knowledge 接口 + rebuild | ✅ | 2026-10-14 | POST /api/admin/knowledge（校验 title/content≤8000/keywords/skillTags、岗位存在性，落库 sourceType=MANUAL）+ 词典即时刷新；POST /api/admin/knowledge/rebuild（OPTIMIZE TABLE）。curl 实测新增片段（id=43）后提交事务类回答立即召回 2 条（含新片段）；学生 403（HTTP 403/code 40301）；参数缺失 40001、岗位不存在 40002 |
| 4.6 | 真实 Provider（OpenAiCompatible） | ✅ | 2026-10-14 | OpenAiCompatibleProvider：WebClient POST {api-url}/chat/completions（system 限定只输出 JSON + response_format=json_object + temperature 0.3，超时 ai.timeout）；三级容错=失败重试 1 次（退避 1s）→ Mock 降级 → 连续 3 次失败锁会话（sessionId 熔断）。OpenAiCompatibleProviderTest 5 用例（JDK HttpServer 模拟）：正常解析/围栏剥壳/5xx 重试降级/坏 JSON 连续 3 次锁定（第 4 次不再打服务端）/报告降级。实测：qwen 缺 AI_API_URL/KEY 启动即失败（信息明确，D20）；不可达端点跑完一场（真实调用失败→WARN 降级 mock→面试照常出追问）；AiConfig 移除 D14 的 mock 回退 |

### M5 个性化 + 管理端补全

| 任务 | 内容 | 状态 | 完成日期 | 验证记录/备注 |
|---|---|---|---|---|
| 5.1 | 掌握度更新 MasteryUpdater | ⬜ | | 验证：MasteryUpdaterTest |
| 5.2 | growth / recommendations 接口 | ⬜ | | 验证：HistoryGrowthIT 全绿 |
| 5.3 | 短板识别 + 推荐引擎 | ⬜ | | 验证：对应单测 |
| 5.4 | 管理端扩展接口 ★ | ⬜ | | 验证：SecurityIT 403 拦截 |

### M6 测试 + 部署 + 验收

| 任务 | 内容 | 状态 | 完成日期 | 验证记录/备注 |
|---|---|---|---|---|
| 6.1 | 全量测试（mvn verify 全绿） | ⬜ | | 记录测试数/覆盖率 |
| 6.2 | AI 质量抽测（人工 20 问答） | ⬜ | | 分差 ≤15 比例 ≥80% |
| 6.3 | Dockerfile + docker-compose.yml | ⬜ | | 验证：一键 up 全系统 |
| 6.4 | Mock 离线演练 | ⬜ | | 验证：断网完整流程 |
| 6.5 | 验收自检（对照表 15 条） | ⬜ | | 计划 §12 逐条勾选 |
| 6.6 | README + 答辩演示脚本 | ⬜ | | 按方案 §19 15 步 |

---

## 4. 变更与决策记录

| 日期 | 类型 | 内容 | 影响范围 |
|---|---|---|---|
| 2026-10-13 | 决策 | 交付边界=仅后端 `ai-interview/backend`，前端不在计划范围 | 计划 §1.3 |
| 2026-10-13 | 决策 | MySQL 8 + Redis 7 由 Docker Compose 提供（本机 colima）；不上本机安装 | 计划 §2.2 |
| 2026-10-13 | 决策 | AI 双实现，默认 `ai.provider=mock` 离线可演示；环境变量切真实千问 | 计划 §6.8 |
| 2026-10-13 | 决策 | 管理员操作日志走 SLF4J 应用日志（audit.log），不建第 10 张表，严守 9 张表约束 | 计划 §7 |
| 2026-10-13 | 决策 | 业务失败统一 HTTP 200 + code 区分错误，仅协议级错误用 HTTP 状态码（需与前端联调前确认） | 计划 附录 A |
| 2026-10-13 | 偏差 | **D6：开发期 Redis 宿主机端口固定 6380**（compose 映射 6380:6379，application-dev.yml 同步）。原因：本机 6379 已被其他项目的 `interview-redis` 容器占用，不改他人环境 | 计划 §2.2 / 附录 B |
| 2026-10-13 | 偏差 | **D7：镜像经 daocloud 加速源拉取后重打标签**（`docker.m.daocloud.io/library/mysql:8.0` → `mysql:8.0`）。原因：Docker Hub 直连超时；compose 文件保持计划原文不动 | 计划 §2.2 |
| 2026-10-13 | 偏差 | **D8：JDK 17 以 Adoptium tarball 装至 `~/tools/jdk-17.0.20.1+1`**（brew cask 需交互式 sudo，不可行）。运行统一 `JAVA_HOME=~/tools/jdk-17.0.20.1+1/Contents/Home`，编译仍用 release=17 | 计划 §2.1 |
| 2026-10-13 | 偏差 | **D9：集成测试复用 dev 容器**（MySQL 独立测试库 `ai_interview_test` + `createDatabaseIfNotExist=true`，Redis 6380），不用 Testcontainers。原因：Testcontainers 需额外配置 colima socket，且 colima 2C/2G 起双 MySQL 有 OOM 风险（计划 §11 R2）；测试有效性等价（仍是真实 MySQL 8） | 计划 §8.2 |
| 2026-10-13 | 偏差 | **D10：M1 验证口径调整**——计划 1.4 的"附录 C 用例 1~5"中用例 4（创建面试）/5（start）依赖 M2 才开发的 interview 模块，M1 以等价安全用例补验（登出 401/篡改 token 401/403/限流/禁用账号），用例 4~5 留待 M2 完成时验证 | 计划 §9-1.4 |
| 2026-10-13 | 决策 | 登录限流阈值做成配置 `app.security.login-rate-limit-per-minute`（默认 5），集成测试放宽为 1000 防用例互相干扰 | 计划 §7 补充 |
| 2026-10-13 | 决策 | 种子账号：admin/Admin@123456（管理员）、demo01/123456（演示学生），BCrypt 散列生成后本地验证通过写入 V2；Maven surefire 增配 `**/*IT.java` 使集成测试随 `mvn test` 执行 | 计划 §3.4 / §8.2 |
| 2026-10-13 | 决策 | **D11：文字回答异步契约**——POST messages/text 受理即返 `{accepted:true,roundNo}`，AI 追问/下一题经 WS 推送 + messages 接口获取（计划 §5.3#11 允许的异步形态）；前端契约自 M2 固定，M4 接真实 Provider 不再变更 | 计划 §5.3-11 / §4.4 |
| 2026-10-13 | 决策 | **D12：主问题序列在 start 事务内一次性写入 interview_message**（round_no 0..N-1，此后不重新组卷防漂移）；学生/ADMIN 视角按 `round_no > current_round` 隐藏未问到的主问题防偷题，followupIndex 由消息序推导不落库 | 计划 §6.3 |
| 2026-10-13 | 决策 | **D13：durationMinutes 不落库**（严守 9 表约束），start 时注册内存定时器；重启按 `started_at+最大时长60min` 上界恢复（超界直接收卷）；到期转 TIMEOUT 后报告生成于 M3 接入 | 计划 §5.3-9 / §6.1 |
| 2026-10-13 | 决策 | **D14：ai.provider 非 mock 时（qwen）显式 WARN 并回退 MockAiProvider**，真实 OpenAiCompatibleProvider 排期 M4-4.6；M2 交付 Mock + PromptBuilder + AiResponseParser（三级容错的第一级防线） | 计划 §6.8 / §9-M4 |
| 2026-10-13 | 决策 | **D15：追问规则 R5/R6（连续两轮低/高分调难度）M2 运行期休眠**——评分输入（lastTwoScores/coveredRatio/missingPoints）排期 M3 evaluateTurn；引擎与单测已按方案 §6.5 七条规则完整交付 | 计划 §6.4 |
| 2026-10-14 | 决策 | **D16：finish 异步契约**——返回 `{accepted:true, status, reportId}`，偏离计划 §5.3#13 的同步 `{reportId}`。理由：报告生成 @Async 防阻塞（计划 §4.4/R12），reportId 经 WS REPORT_READY / finish 幂等调用 / GET report 获取；FINISHED/TIMEOUT 且报告缺失时再调 finish 即重试入口（M3-3.5） | 计划 §5.3-13 |
| 2026-10-14 | 决策 | **D17：逐题评分结果不落库**（严守 9 表约束）——进程内 TurnEvaluationStore 按 (session, round) 暂存累积评分快照（供应变得分），报告读取时缺漏轮次用 Mock 引擎确定性重算；真实 Provider 的 Redis 报告缓存排期 M4。AiReport.summary 与 reportText 合并存 report_text 列（无独立列），读取按首个空行拆分 | 计划 §6.6 / §3.1 |
| 2026-10-14 | 偏差 | **D18：history 返回 `Page<HistoryItemVO>`**（records+total，与管理端题库列表分页契约一致），计划 §5.4#20 写的是裸数组；未 start（CREATED）的会话不入历史（started_at 非空才计入） | 计划 §5.4-20 |
| 2026-10-14 | 说明 | **D15 于 M3 落地**：TurnEngine 每答先 evaluateTurn 再喂规则引擎（coveredRatio/missingPoints/lastTwoScores 全接入）。因引擎既定优先级 R1（缺得分点即追问）先于 R5/R6，通用回答下换题恒由追问上限 R7 触发；R5/R6 在题目无得分点数据等边角场景运行期可达，七条规则行为仍由 FollowupRuleEngineTest 全覆盖 | 计划 §6.4 |
| 2026-10-14 | 偏差 | **D19：pauseCount 表单参数接受但不落库**——interview_message 无 pause_count 列且 9 表 DDL 冻结，停顿次数由转写中的省略号/破折号/显式标记推断（正则，先长后短防 `——` 双计）。VoiceMetricsCalculator：语速=字数÷分钟、语气词密度/百字、停顿按每 2 分钟折算；voice=语速×0.5+停顿×0.2+语气词×0.3，语速 <120 或 >320 字/分时封顶 60（语速为一票否决项）；无时长/无转写中性 55 | 计划 §5.3-12 / §6.6 |
| 2026-10-14 | 偏差 | **D20：D14 于 M4 落地**——ai.provider=qwen 真实启用 OpenAiCompatibleProvider（WebClient），缺 AI_API_URL/AI_API_KEY 启动即抛错（配置错误显式暴露，优于静默降级）；运行期三级容错：重试 1 次（1s 退避）→ Mock 降级 → 连续 3 次失败会话锁定 mock。NextQuestionContext/TurnEvaluationContext/ReportContext 增 sessionId 字段支撑会话级熔断；PromptBuilder 补 evaluateTurn/generateReport 两套 Prompt | 计划 §6.8 / §9-M4-4.6 |
| 2026-10-14 | 偏差 | **D21：MessageVO 补 audioPath 字段**（计划 §4.3 MessageVO 未列，音频回答验证/前端展示需要）；计划 §6.9 的"可选回放接口"交付：GET /api/interviews/{id}/messages/{mid}/audio（归属本人/ADMIN，FileSystemResource 流式 + Content-Type 按扩展名） | 计划 §4.3 / §6.9 |
| 2026-10-14 | 说明 | KeywordExtractor 检索/匹配用字符串列名 QueryWrapper（非 lambda），脱离 Spring 容器可运行，纯单测无需起 MyBatis-Plus 上下文；词典为 volatile 快照整体替换（refresh 由管理端新增触发 + 首次使用懒加载） | 计划 §6.5 |

## 5. 问题与风险跟踪

| # | 日期 | 问题描述 | 状态 | 解决方式/备注 |
|---|---|---|---|---|
| R1 | 2026-10-13 | 本机仅 JDK 25，Spring Boot 3.3 的 CGLIB/ByteBuddy 可能不识别 class file 69 | ✅ 已解决 | 按 D8 安装 Temurin 17 tarball（`~/tools/jdk-17.0.20.1+1`），编译运行测试全流程用 JDK 17，未触发 ByteBuddy 兼容问题 |
| R2 | 2026-10-13 | colima 默认 2C/2G 对 MySQL 8 偏紧 | ⚠ 待观察 | M1 期间单 MySQL + Redis 运行正常；D9 决定集成测试不起第二个 MySQL，降低触发概率；若后续 OOM 再 `colima start --cpu 4 --memory 6` |
| P1 | 2026-10-13 | Docker Hub 直连超时，mysql/redis 镜像拉取失败 | ✅ 已解决 | 走 daocloud 加速源拉取并重打标签（D7） |
| P2 | 2026-10-13 | 本机 6379 端口已被其他项目 `interview-redis` 容器占用，计划默认端口冲突 | ✅ 已解决 | 开发期 Redis 固定映射宿主机 6380（D6），compose 与 application-dev.yml 保持一致 |
| P3 | 2026-10-13 | V3/V4 种子 SQL 的 JSON 字段内含未转义双引号，且批量修复脚本误伤 `","` 数组分隔符导致元素合并 | ✅ 已解决 | 内嵌引号统一改「」；还原被误替换分隔符后用 Python json.loads 全量校验 JSON 字面量，重建库重新迁移并断言（30 题/比例/字段完整性全过） |
| P4 | 2026-10-13 | MockMvc 读取响应默认 ISO-8859-1 导致中文断言乱码 | ✅ 已解决 | 测试 `body()` 统一 `getContentAsString(UTF_8)` |
| P5 | 2026-10-13 | M2 手工验证时登录返回 42901：集成测试大量登录把共享 Redis（6380）的限流键 `rl:login:127.0.0.1` 打满，dev 应用（限 5/分）沿用同一计数 | ✅ 已解决 | 限流键 TTL 60s，等窗口重置即可；非代码缺陷，联调时知悉此现象（测试与开发共用 Redis 时先跑测试会短暂触发限流） |
| P6 | 2026-10-14 | 本机 8080 端口被**其他项目**（`~/Desktop/Agent/AI-interview/self-interview-learning`，Gradle/Spring AI 应用，PID 随启动变化）占用，本项目 dev 后端无法按默认端口启动 | ⚠ 环境问题（非本项目缺陷） | 不动他人环境：冒烟/联调临时用 `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080`；若该服务系本人可自行停用则恢复 8080。测试不受影响（RANDOM_PORT/MockMvc） |
| R3 | 2026-10-14 | 预置风险：ngram token=2，单字/单英文查询不命中 FULLTEXT | ✅ 已验证防护 | KeywordExtractor 过滤词长 <2 与停用词、查询词超 4 取前 4；V5 内容均为完整技术词。手验时另发现：docker exec mysql 客户端默认字符集非 utf8mb4 会把中文查询条件转码失配（M4 SQL 手验需加 `--default-character-set=utf8mb4`），应用侧 JDBC 无此问题 |

> 开发中新遇到的问题在此追加；已解决的把状态改为 ✅ 并填解决方式。计划 §11 的 R1~R12 作为预置风险清单，触发时在此登记。

## 6. 环境状态备忘

| 组件 | 状态（2026-10-14 M4 完成时检测） | 备注 |
|---|---|---|
| JDK 17 | ✅ Temurin 17.0.20（`~/tools/jdk-17.0.20.1+1`，tarball 安装） | 运行/测试统一 `JAVA_HOME=~/tools/jdk-17.0.20.1+1/Contents/Home` |
| JDK 25 | 仍存在（系统默认） | 仅做兜底；编译由 `maven.compiler.release=17` 保证 |
| Maven | 3.9.16（~/tools/apache-maven-3.9.16） | settings.xml 已配阿里云镜像 |
| Docker | ✅ colima 运行中 | 镜像经 daocloud 源拉取（D7） |
| aiinterview-mysql | ✅ healthy，端口 3306 | dev 库已迁移至 V5（42 条知识片段）；测试库 ai_interview_test 同步 V5 |
| aiinterview-redis | ✅ healthy，宿主机端口 6380 | 6379 被其他项目占用（D6） |
| 本机 8080 | ⚠ 被其他项目占用（P6） | dev 后端临时换 `--server.port=18080`；测试不受影响 |
| Node | v22.23.3 / npm 10.9.9 | 仅前端用，超出本计划范围 |

**日常启动（M1 之后继续开发时）：**

```bash
cd ai-interview
docker compose -f docker-compose.dev.yml up -d          # 容器已常驻可跳过
export JAVA_HOME=~/tools/jdk-17.0.20.1+1/Contents/Home
cd backend && mvn spring-boot:run                        # dev profile，Redis 连 6380
# 若 8080 被占（P6）：mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
# 测试：mvn test（用 ai_interview_test 库 + 6380 Redis）
# 账号：admin / Admin@123456；demo01 / 123456
```

> 每次环境变化（装 JDK 17、起容器、改 colima 资源等）更新此表并注明日期。

---

## 7. 文档更新历史

| 日期 | 更新内容 | 更新人 |
|---|---|---|
| 2026-10-13 | 创建本文档；登记初始状态：计划阶段完成（产物 #1 #2），M1~M6 全部未开始，决策 5 条，环境检测 1 次 | ZCode |
| 2026-10-13 | **M1 完成**：任务 1.1~1.9 全部 ✅（产物 #4~#7 交付）；安装 Temurin 17；起 dev 容器；应用启动+Flyway V1~V4 迁移+9 表断言通过；附录 C 认证用例与安全边界 curl 全通过；`mvn test` 10 用例全绿；登记偏差 D6~D10、问题 P1~P4（均解决）、R1 解决 | ZCode（M1 开发会话） |
| 2026-10-13 | **M2 完成**：任务 2.1~2.7 全部 ✅（产物 #8 交付，api.http 增用例 12~18）；新增 interview（实体/状态机/TurnEngine/规则引擎/WS/定时器）与 ai（Mock/Prompt/解析容错）模块，无新增 Flyway 脚本；`mvn test` 38 用例全绿（M2 新增 28）；手工验证：curl 建会话→start 首题→文字回答追问落库→abort 终态、Node WS 实测 QUESTION/FOLLOW_UP/TIMER；登记决策 D11~D15、问题 P5 | ZCode（M2 开发会话） |
| 2026-10-14 | **M3 完成**：任务 3.1~3.5 全部 ✅（产物 #9 交付，api.http 增用例 19~21）；新增 assessment（ScoreCalculator/ReportService/EvaluationReport/TurnEvaluationStore/ReportVO）与 growth（history）模块，MockAiProvider 补全 evaluateTurn 规则评分引擎与 generateReport 报告拼装，TurnEngine 接入评分输入（D15 唤醒）、finish/轮次用尽/TIMEOUT 三处触发报告生成，无新增 Flyway 脚本；`mvn test` 56 用例全绿（M3 新增 18，测试类 14）；手工验证：curl（18080，P6）finish→report 完整 ReportVO（63.77 分）→history、启动恢复自动收卷昨日遗留会话并出报告；登记决策 D16~D18、问题 P6 | ZCode（M3 开发会话） |
| 2026-10-14 | **M4 完成**：任务 4.1~4.6 全部 ✅（产物 #10 交付，api.http 增用例 22~25）；新增 knowledge（实体/FULLTEXT Mapper/KeywordExtractor/KnowledgeSearchService/KnowledgeAdminService）与 interview.storage（AudioStorageService）模块、VoiceMetricsCalculator、OpenAiCompatibleProvider + WebClientConfig + AiChatResponse，Flyway V5 种子 42 条知识片段，TurnEngine/InterviewService/ReportService 接入关键词提取、知识检索注入 Prompt 与 AUDIO 场 voice 维度，MessageVO 补 audioPath（D21）；`mvn test` 85 用例全绿（M4 新增 29：VoiceMetricsCalculatorTest 8、KeywordExtractorTest 8、OpenAiCompatibleProviderTest 5、AudioAnswerIT 3、KnowledgeSearchIT 5，测试类 19）；手工验证：SQL 手验 FULLTEXT 召回（utf8mb4 客户端）、curl 音频全流程（落盘/回放/报告含 voice）、新增知识片段即被检索、qwen 缺 key fail-fast 与不可达端点自动回 mock；登记决策 D19~D21、风险 R3 验证 | ZCode（M4 开发会话） |
