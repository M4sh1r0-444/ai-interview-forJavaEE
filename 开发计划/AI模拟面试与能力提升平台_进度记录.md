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
| 当前阶段 | **后端 M6 ✅ 完成；前端 F3 ✅ 完成，F4 待开始** |
| 当前里程碑 | 后端 M6 ✅（2026-10-15）；前端 F3 ✅（2026-10-17）；下一里程碑 **F4 语音 + 成长 + 推荐 + 个人中心** |
| 整体进度 | 后端 6/6；前端 3/6（F1 ✅、F2 ✅、F3 ✅，F4~F6 ⬜） |
| 上次更新 | 2026-10-17 |
| 上次更新人 | ZCode（F3 报告与历史会话） |

### 里程碑状态

| 里程碑 | 内容（计划 §9） | 状态 | 开始日期 | 完成日期 |
|---|---|---|---|---|
| M1 | 基础工程 + 认证 + 岗位题库 | ✅ 已完成 | 2026-10-13 | 2026-10-13 |
| M2 | 文字面试闭环 | ✅ 已完成 | 2026-10-13 | 2026-10-13 |
| M3 | 评分 + 报告 + 历史 | ✅ 已完成 | 2026-10-14 | 2026-10-14 |
| M4 | 语音 + 知识库 RAG | ✅ 已完成 | 2026-10-14 | 2026-10-14 |
| M5 | 个性化 + 管理端补全 | ✅ 已完成 | 2026-10-15 | 2026-10-15 |
| M6 | 测试 + 部署 + 验收 | ✅ 已完成 | 2026-10-15 | 2026-10-15 |

### 前端里程碑状态（F1~F6，F1~F3 已完成、F4~F6 待开始）

> 任务明细与验收标准见 [《AI模拟面试与能力提升平台_前端开发计划.md》](AI模拟面试与能力提升平台_前端开发计划.md) §11；F1 任务粒度已回填，F2 起逐里程碑回填。

| 里程碑 | 内容 | 状态 | 开始日期 | 完成日期 |
|---|---|---|---|---|
| F1 | 工程基座 + 认证 + 应用外壳 | ✅ 已完成 | 2026-10-17 | 2026-10-17 |
| F2 | 面试主流程（文字 + WS + 倒计时） | ✅ 已完成 | 2026-10-17 | 2026-10-17 |
| F3 | 报告 + 历史 | ✅ 已完成 | 2026-10-17 | 2026-10-17 |
| F4 | 语音 + 成长 + 推荐 + 个人中心 | ⬜ 未开始 | — | — |
| F5 | 管理端 7 页 | ⬜ 未开始 | — | — |
| F6 | 质量 + 部署修复 + 验收 | ⬜ 未开始 | — | — |

> ⚠ 2026-10-17 复核：M6-6.3 的"静态页 200"只验证了 HTTP 状态码，**未校验页面内容**；`frontend-dist` 卷实际为 nginx 默认欢迎页（见 P9）。
> ✅ 2026-10-17 当日已修复（后端修复 FIX-02）：frontend 改为初始化容器，启动时把产物强制覆盖进卷；独立 compose 项目验证页面含产品名（新卷/旧卷双场景），P9 关闭。

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
| 7 | 接口调试脚本（M1 起） | `backend/api.http` | 2026-10-13 | 🔧 持续维护 | IDEA HTTP Client，与计划附录 C 同步；M2 追加用例 12~18（面试闭环 + WS），M3 追加 19~21（finish/report/history），M4 追加 22~25（音频回答/回放 + 管理端知识库 + Provider 观测），M5 追加 26~32（成长曲线 + 个性化推荐 + 管理端扩展五组） |
| 8 | 文字面试闭环模块（M2-2.1~2.7） | `backend/` interview/ai 包等 | 2026-10-13 | ✅ 已交付 | interview（实体/状态机/服务/TurnEngine/规则引擎/WS/定时器）+ ai（Mock/Prompt/解析容错）；`mvn test` 38 用例全绿 |
| 9 | 评分+报告+历史模块（M3-3.1~3.5） | `backend/` assessment/growth 包等 | 2026-10-14 | ✅ 已交付 | assessment（ScoreCalculator/ReportService/EvaluationReport/TurnEvaluationStore）+ growth（history 接口）+ Mock 评分引擎与报告拼装 + finish/重试/TIMEOUT 收卷；`mvn test` 56 用例全绿（M3 新增 18） |
| 10 | 语音+知识库RAG+真实Provider模块（M4-4.1~4.6） | `backend/` knowledge/interview.storage/ai 包等 | 2026-10-14 | ✅ 已交付 | knowledge（实体/FULLTEXT检索/KeywordExtractor/管理服务）+ AudioStorageService + 音频回答/回放接口 + VoiceMetricsCalculator（AUDIO 场 voice 维度）+ V5 种子 42 条 + OpenAiCompatibleProvider（三级容错/会话熔断）+ WebClientConfig；`mvn test` 85 用例全绿（M4 新增 29） |
| 11 | 掌握度+成长曲线+推荐模块（M5-5.1~5.2） | `backend/` growth 包 + V6 种子 | 2026-10-15 | ✅ 已交付 | AbilityRecord/LearningResource 实体与 Mapper、MasteryUpdater（0.7/0.3 折算+难度加权+幂等）、ReportService 挂接掌握度更新、growth 曲线接口、RecommendationService（优先级公式+资源匹配+下场建议）、V6 学习资源种子 28 条（每岗 14）；`mvn test` 97 用例全绿（M5 新增 12） |
| 12 | 短板识别+推荐引擎+管理端扩展模块（M5-5.3~5.4） | `backend/` growth/engine + admin 包等 | 2026-10-15 | ✅ 已交付 | WeaknessDetector 四规则 + RecommendationEngine（优先级/错题重练/通用计划）、RecommendationVO 增 retryQuestions、Redis 推荐缓存（TTL 10min + 报告事件预生成）、管理端 dashboard/positions(+weights)/resources/users 五组接口 + DashboardVO/PositionAdminVO 等 DTO；`mvn test` 114 用例全绿（5.3/5.4 新增 17） |
| 13 | 质量门禁+AI抽测+五服务部署（M6-6.1~6.3） | `backend/pom.xml`(JaCoCo) + `backend/Dockerfile` + `docker-compose.yml` + `nginx/` + `frontend/` 占位页 + `.env(.example)` + `qa/m6_ai_quality_sampling.py` + `开发计划/AI质量抽测记录_M6.md` | 2026-10-15 | ✅ 已交付 | `mvn verify` 全绿（114 用例）+ JaCoCo 行覆盖 89.0%（≥60% 门禁固化）；双岗各 20 条抽测 40/40 分差 ≤15；`docker compose up -d` 一键起五服务，Nginx 80 静态页/`/api` 全链路/`/ws` 101 全通（D27~D29） |
| 14 | Mock 离线演练套件（M6-6.4） | `ai-interview/docker-compose.offline.yml` + `qa/m6_offline_drill.sh` + `qa/m6_offline_drill.py` + `qa/offline_drill_{env,result}.json` | 2026-10-15 | ✅ 已交付 | 一键脚本：停 dev 栈→起离线栈（核心服务 internal 网络零外网出口 + nginx 边缘入口）→断网证明 5 项（AI_PROVIDER=mock、外网 IP/DNS 不通、内网正常、宿主机入口 200）→业务演练 13/13（文字+语音面试→六维报告→历史/成长/推荐→管理端→WS）→恢复 dev 栈（数据无损） |
| 15 | 验收自检表（M6-6.5） | `开发计划/AI模拟面试与能力提升平台_验收自检_M6.md` | 2026-10-15 | ✅ 已交付 | 计划 §12 对照表 15 条全部 ✅ 并附验证方式与实测证据（SQL 计数/DEBUG 日志/接口实录/IT 用例/离线演练留存） |
| 16 | 后端 README + 答辩演示脚本（M6-6.6） | `ai-interview/backend/README.md` + `ai-interview/答辩演示脚本.md` | 2026-10-15 | ✅ 已交付 | README 按新人 30 分钟路径组织（环境/快速开始/一场面试/测试/配置/模块/接口/联调契约/部署/FAQ）；演示脚本将方案 §19 15 步逐条映射为可执行命令（含故障预案） |
| 17 | **前端开发计划 v1.1（F1~F6 + 液态玻璃）** | `开发计划/AI模拟面试与能力提升平台_前端开发计划.md` | 2026-10-17 | ✅ 已交付（计划 v1.1） | 2750+ 行；v1.1 新增液态玻璃视觉规范（§6.7/§8.3.1/F1-3/R-F18/D38）；基于后端 39 REST + 1 WS 实测契约；19 页规格 + 架构/状态机/测试/部署/验收 + 12 项契约差异；F1~F6 预估 26.5 人日；同时登记 P9（产物卷缺陷）、P10（答辩脚本权重 payload 错误）、P11（开发库测试残留） |
| 18 | **前端 Vue3 工程（F1-1~F1-8）** | `ai-interview/frontend/` + `frontend/docs/F1-验收记录.md` + `frontend/docs/screenshots/f1/` | 2026-10-17 | ✅ 已交付（F1） | Vite 5 + Vue 3 + TS + Element Plus + Pinia + Vue Router + Axios；设计令牌 + 液态玻璃视觉基座（AppBackdrop/GlassPanel/Element 覆盖/降级）/三种布局/Auth store/路由守卫/登录注册/403/404/离线条/traceId；39 REST + 1 WS API/类型骨架；`lint/type-check/test:unit/build` 全绿（34 用例）；Vite `/api` 登录与 `/ws` 101 代理实测通过；功能冒烟 16 项 + 液态玻璃视觉/降级检查 + 4 档视口无横向滚动；11 张截图；原占位页备份 `docs/legacy-placeholder-index.html` |
| 19 | **前端 F2 面试主流程（F2-1~F2-8，文字）** | `frontend/src/{views/student,views/interview,components/interview,composables,stores,utils}` + `frontend/e2e/` + `frontend/docs/F2-验收记录.md` + `frontend/docs/screenshots/f2/` | 2026-10-17 | ✅ 已交付（F2） | 首页/岗位列表/详情/设置/房间 5 页替换占位；`stores/interview` 唯一事实源 + `useInterviewRoom` 编排 + 原生 WS 客户端（退避重连/4401/断线 2s 轮询）+ 倒计时校准 + 文字作答（IME/Ctrl+Enter/乐观气泡/草稿/10005 刷新）+ finish/abort 二次确认与终态；`lint/type-check` 0/0，单测 **91/91**（F2 新增 57，`round/draft` 100%、interview store 80.31%），coverage 59.32%，build 成功，Playwright E2E **6/6**（首页导航/首页接口降级/文字闭环+刷新/中止+终态刷新/跨标签页结束/WS 断线轮询）；1440/1024/768/390 无横向滚动，0 pageerror；12 张截图；测试数据已清理（demo01 历史 7 场） |
| 20 | **前端 F3 报告与历史（F3-1~F3-6）** | `frontend/src/{views/interview/ReportView.vue,views/growth/HistoryView.vue,stores/report.ts,components/report,components/charts,components/common/PaginationBar.vue,composables/useEchart.ts,composables/usePagination.ts,composables/useAudioReplay.ts,utils/report.ts,utils/echarts.ts}` + `frontend/e2e/report.spec.ts` + `frontend/docs/F3-验收记录.md` + `frontend/docs/screenshots/f3/` | 2026-10-17 | ✅ 已交付（F3） | 报告页/历史页替换占位；报告缓存 + 10004 轮询（1s→2s→3s，60s 上限，隐藏暂停）+ finish 幂等重试 + 40401/ABORTED/未结束空态 + WS REPORT_READY；SVG 总分环、TEXT 5 维/AUDIO 6 维雷达（ECharts 按需注册）、维度条、逐题证据折叠、受保护音频回放、亮点/不足/建议、推荐 Top2、打印；历史服务端分页 10/20/50 + 当前页状态筛选 + page/size/status URL 同步；`lint/type-check` 0/0，单测 **133/133**（27 文件，F3 新增 42），coverage **68.31%**，build 成功，Playwright E2E **10/10**（F2 6 + F3 4）；16 张截图；测试数据已清理（demo01 历史 7 场） |

> 后续产物（backend 工程、Flyway 脚本、docker-compose 等）交付时在此表**按序追加**，注明对应里程碑任务编号。前端产物的任务编号使用 F1~F6（见前端开发计划 §11），不占用 M 编号。

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

### M5 个性化 + 管理端补全 ✅（2026-10-15 完成）

| 任务 | 内容 | 状态 | 完成日期 | 验证记录/备注 |
|---|---|---|---|---|
| 5.1 | 掌握度更新 MasteryUpdater | ✅ | 2026-10-15 | MasteryUpdaterTest 11 用例通过（首次直取、旧70本次90→76、clamp、难度加权聚合 1.2/0.8→72、多技能标签、幂等/空输入/当前掌握度查询）。挂接 ReportService：报告事务提交后独立事务追加 ability_record（按 source_session_id 幂等，报告重试/并发/重启恢复路径均不重复折算）。curl（18080）实测两场面试（round0 恰同为 spring 题）→ ability_record 同技能两条 52.53→48.95（0.7×52.53+0.3×40.59 折算吻合）；dev/test 库均验证 |
| 5.2 | growth / recommendations 接口 | ✅ | 2026-10-15 | HistoryGrowthIT 2 用例全绿（新增 growthAndRecommendationsAfterTwoInterviews：两场后曲线点数=场数、records 升序、current=最新、技能名来自能力模型；recommendations 非空 ≤Top3 且优先级降序、资源/练习来自 V6、建议非空；未登录 401、新用户 growth 空/推荐走默认岗位）。curl 实测 growth 返回 spring 两点曲线、recommendations Top3=java_basics/concurrency_jvm/mysql（未考察技能按 0 参与 15.0 优先级居首）资源匹配 V6；V6 迁移 dev/test 库（每岗 14 条，类型 ARTICLE7/QUESTION4/PRACTICE2/PROJECT1） |
| 5.3 | 短板识别 + 推荐引擎 | ✅ | 2026-10-15 | WeaknessDetectorTest 5 用例通过（四规则各一 + 不误报：单次 55 不触发①、一次低分不触发④）；RecommendationEngineTest 3 用例通过（优先级排序含未考察按 0、短板技能资源/错题必现且理由挂靠、无短板输出通用计划不点名重点覆盖）。RecommendationIT 2 用例全链路：规则①注入 7 技能两条 <60 记录后接口可见"均低于 60"、Redis 键 rec:{uid}:{positionId} TTL∈(0,600s]、删键重算一致；糟糕回答一场 → 顶级推荐=答题技能（规则②"低于岗位要求"）+ retryQuestions 来自最近一场 missingPoints 命中题目。ReportService 增 evidenceOf 公共证据查询 + ReportGeneratedEvent → RecommendationCacheWarmer 报告落库后预生成缓存（异步线程内执行，失败不影响报告）；curl 实测 recommendations 含 retryQuestions 字段、Redis 出现 rec:* 键、权重为空时 fallback 理由恒非空 |
| 5.4 | 管理端扩展接口 ★ | ✅ | 2026-10-15 | SecurityIT 3 用例（新增，补齐计划 §8.2 清单）：无 token 401（9 路径）、学生访问全部管理端（含 M5 新增 dashboard/positions/weights/resources/users）403/code=40301、跨用户读面试 40401/ADMIN 放行。AdminExtendedIT 4 用例：dashboard 对照 SQL 实算（userCount/sessionCount/questionCount/avgScore 与 AVG 全等、近 7 日 7 条升序含今日、注册+开考后计数+1）；岗位维护（新增学生侧立即可见、权重调整持久化回读、组和≠1→40001、重复编码/技能权重和≠1→40001、停用后学生不可见且建面试 40401）；资源 CRUD（新增/列表过滤/修改/下架 enabled=0、类型·技能未对齐→40001、岗位不存在→40401）；用户启停（禁用登录 10003、恢复可登录、非法 status→40001、不存在→40401、自禁用→40001）。curl（18080）实测 dashboard（4 用户/8 场/61 题/均分 59.07/7 日趋势）、weights 0.35 写入回读、资源建删、禁用→10003→恢复 |

> M5 测试汇总：`mvn test` = **114 用例全绿**（M4 85 + M5-5.1/5.2 新增 12 + M5-5.3/5.4 新增 17：WeaknessDetectorTest 5、RecommendationEngineTest 3、RecommendationIT 2、SecurityIT 3、AdminExtendedIT 4）；测试类 25 个。

### M6 测试 + 部署 + 验收

| 任务 | 内容 | 状态 | 完成日期 | 验证记录/备注 |
|---|---|---|---|---|
| 6.1 | 全量测试（mvn verify 全绿） | ✅ | 2026-10-15 | `mvn verify` BUILD SUCCESS：114 用例 0 失败；JaCoCo 行覆盖 89.0%（2943 行覆盖 2619），BUNDLE ≥60% 门禁固化进 pom，核心包最低 interview.websocket 69.3% 全部达标（口径见 D27） |
| 6.2 | AI 质量抽测（人工 20 问答） | ✅ | 2026-10-15 | 双岗各 4 场 × 5 轮 = 40 条（好 16/中 12/差 12），**40/40 分差 ≤15（100%，达标线 80%）**；追问相关性/报告完整/建议对短板四项核对全过；记录表《AI质量抽测记录_M6.md》，脚本 qa/m6_ai_quality_sampling.py（执行方式见 D28） |
| 6.3 | Dockerfile + docker-compose.yml | ✅ | 2026-10-15 | backend 多阶段 Dockerfile + nginx.conf + frontend 占位页 + 五服务编排 + .env(.example)；`docker compose up -d` 一键起，Nginx 80：静态页 200、/api 全链路（登录→…→报告 64.46→推荐）、/ws 101 升级全通（落地细节见 D29） |
| 6.4 | Mock 离线演练 | ✅ | 2026-10-15 | `bash qa/m6_offline_drill.sh` 一键：断网证明 5 项（backend 容器内对外网 IP/DNS 均不通、AI_PROVIDER=mock、内网链路正常、宿主机经 Nginx 可访问）+ 业务演练 **13/13 项通过**：文字面试（Java 岗，75.66 分/追问 15 次）+ 语音面试（前端岗，含 voice 维度）→ 报告 → 历史/成长/推荐 → 管理端看板与知识片段新增 → WS QUESTION 推送；结果留存 qa/offline_drill_{env,result}.json；演练后 dev 栈自动恢复（数据无损）。断网模拟方式见 D30 |
| 6.5 | 验收自检（对照表 15 条） | ✅ | 2026-10-15 | 计划 §12 逐条 ✅ 并附验证方式与实测证据（双岗/题量/题型比例 SQL、追问消息表实录、知识检索 DEBUG 日志、报告六维与五要素实录、两场后成长曲线 5 条全 2 点、推荐 Top3 实录、管理端 IT、双模式、compose 一键+15 步映射），产出《AI模拟面试与能力提升平台_验收自检_M6.md》 |
| 6.6 | README + 答辩演示脚本 | ✅ | 2026-10-15 | backend/README.md（环境→快速开始→一场面试→测试门禁→配置→模块→接口与联调契约→部署→FAQ，新人 30 分钟启动路径）；ai-interview/答辩演示脚本.md（方案 §19 15 步逐条映射可执行命令 + 演示前准备清单 + 故障预案） |

### F1~F6 前端阶段（F1~F3 ✅，F4~F6 待开始）

> 任务编号、产出物、完成标准（DoD）见 [前端开发计划 §11](AI模拟面试与能力提升平台_前端开发计划.md)。F1~F3 已按任务粒度回填，详见下方任务表与《F1-验收记录》《F2-验收记录》《F3-验收记录》。

| 里程碑 | 任务数 | 状态 | 开工条件 | 备注 |
|---|---|---|---|---|
| F1 工程基座 + 认证 | 8（F1-1~F1-8） | ✅ 已完成（2026-10-17） | 本文交付即可开工 | 脚手架/规范/API/类型/守卫/登录注册 |
| F2 面试主流程（文字） | 8（F2-1~F2-8） | ✅ 已完成（2026-10-17） | F1 完成 | 首页/岗位/设置/房间/WS/倒计时/finish |
| F3 报告 + 历史 | 6（F3-1~F3-6） | ✅ 已完成（2026-10-17） | F2 完成 | 报告轮询/雷达/证据/历史 |
| F4 语音 + 成长 + 推荐 + 个人中心 | 7（F4-1~F4-7） | ⬜ 未开始 | F2、F3 完成 | 录音/ASR/回放/成长/推荐/资料 |
| F5 管理端 | 8（F5-1~F5-8） | ⬜ 未开始 | F1 完成；F3 可并行 | 看板/题库/岗位/权重/知识库/资源/用户 |
| F6 质量 + 部署 + 验收 | 6（F6-1~F6-6） | ⬜ 未开始 | F1~F5 完成 | 单测/E2E/契约/产物卷修复/文档/验收 |

> **前置阻塞**：F6-4 依赖 P9 修复（frontend 产物写入 `frontend-dist` 卷）；F6-6 依赖 P11（清理 `ACC_*` 测试岗位/数据）与 P10（修正答辩脚本权重 payload）。F1~F3 本身不依赖上述修复，已完成；F4 起继续。

#### F1 任务明细（2026-10-17 ✅）

| 任务 | 内容 | 状态 | 完成日期 | 验证记录 |
|---|---|---|---|---|
| F1-1 | Vite+Vue3+TS 脚手架、依赖、别名、env、.gitignore | ✅ | 2026-10-17 | Node 22.23.3；Vite 5 固定 5173 + `strictPort`；`@` 别名；`.env.development/.production/.example`；`npm run dev` 200、`vite build` 成功 |
| F1-2 | ESLint/Prettier/vue-tsc/Vitest 配置与 scripts | ✅ | 2026-10-17 | `lint` 0/0、`type-check` 0 error、`test:unit` 7 文件 34 用例全绿、`test:coverage` 通过；scripts 对齐计划 §3.7 |
| F1-3 | 设计令牌、液态玻璃视觉基座、Element Plus 覆盖、Auth/Student/Admin 布局、403/404 | ✅ | 2026-10-17 | `tokens.css`/`element-overrides.scss`/`global.scss`/`print.scss` + `AppBackdrop`/`GlassPanel`；玻璃层覆盖认证/Shell/错误页/公共组件/Element 浮层；`@supports`/reduced-transparency/打印白底降级；403/404 可达；4 档视口（1440/1024/768/390）无横向滚动 |
| F1-4 | http.ts + authSession.ts + types + 错误映射 + API 模块骨架 | ✅ | 2026-10-17 | 39 REST 全部函数骨架；BizError/401 单次事件/403 事件/blob 鉴权；8 个类型文件；单测覆盖拦截器 7 用例（HTTP 200+code、401 并发去重、网络错误、blob JSON） |
| F1-5 | auth store、路由表、守卫、启动 restore、按角色跳转 | ✅ | 2026-10-17 | 21 个视图路由（学生 12 + 管理 7 + 403/404）+ 3 个重定向（`/`、`/admin`、`/admin/evaluation`）；刷新保持登录；过期清 token；学生/管理员跳转、管理员回管理端、profile 例外；跨标签页 storage 同步 |
| F1-6 | 登录/注册页 + 表单校验 + 错误码处理 | ✅ | 2026-10-17 | demo01→`/home`、admin→`/admin/dashboard`；10002 就地提示、10003 弹窗+提示、42901 60s 倒计时、10001 用户名定位、密码不一致前端拦截；注册→登录预填 |
| F1-7 | 全局错误处理、离线条、traceId、路由懒加载兜底 | ✅ | 2026-10-17 | `AppErrorBoundary` + `app.config.errorHandler` + `unhandledrejection`；离线/在线事件条；`TraceIdText` 复制；`router.onError` 识别 chunk 失效并提示刷新 |
| F1-8 | API 联调冒烟（Vite 代理） | ✅ | 2026-10-17 | 5173 `/api/positions` 返回 40101、登录 code=0、`/api/users/me` code=0（Bearer 透传）；`/ws/interviews/21?token=…` open（101）；UI 冒烟 16 项通过、0 pageerror |

> F1 验收细节、截图与复现命令见 [`frontend/docs/F1-验收记录.md`](../ai-interview/frontend/docs/F1-验收记录.md)。

#### F2 任务明细（2026-10-17 ✅）

| 任务 | 内容 | 状态 | 完成日期 | 验证记录 |
|---|---|---|---|---|
| F2-1 | 首页仪表盘 | ✅ | 2026-10-17 | 欢迎区 + 4 快捷入口 + 最近面试 + 岗位入口 + 推荐预览；`Promise.allSettled` 分区降级；最近面试按状态给继续/报告/重开按钮；截图 01 |
| F2-2 | 岗位列表/详情 | ✅ | 2026-10-17 | `/positions` 2 个启用岗位；`/positions/1` 7 技能权重条、要求等级、权重和 100%、评分口径；`/positions/999` 40401 空态；CTA 到设置；截图 02/03 |
| F2-3 | 面试设置 + 能力检测 | ✅ | 2026-10-17 | TEXT/AUDIO 卡片；`useInterviewCapabilities` 检测 MediaRecorder/getUserMedia/Web Speech/webm MIME；难度 1~5；轮次 clamp 5~8（默认 6）；时长 15/30/45/60；防重复提交；E2E #2 |
| F2-4 | 房间骨架 + 数据加载 + 轮次算法 | ✅ | 2026-10-17 | `stores/interview` 唯一事实源；`utils/round.ts` 可作答算法（0-based/追问不推进/AI>USER/最后一条 AI）；CREATED 自动 start；WAITING/PROCESSING/TERMINAL 渲染；刷新恢复；E2E #2 |
| F2-5 | WS 客户端 + 重连 + 对账 | ✅ | 2026-10-17 | 原生 WS；1/2/4/8/15s × 8；4401 不重连；非法 JSON/未知 type 容错；事件后统一 refresh；断线 2s 轮询；E2E #4/#5 |
| F2-6 | 文字作答 + 乐观气泡 + 轮询 | ✅ | 2026-10-17 | Ctrl/Cmd+Enter + IME 保护；负 id 乐观气泡；服务端 ID/内容对账；失败保留文本不重发；10005/40002 自动刷新；草稿按轮次恢复；单测 + E2E #2 |
| F2-7 | 倒计时、状态徽标、finish/abort | ✅ | 2026-10-17 | TimerBadge（≤60s 警示）、WsStatusBadge（重连按钮）、RoundProgress（追问 n/3）、StatusTag；FinishConfirmDialog 展示已答题数/轮次/剩余；FINISHED/TIMEOUT/ABORTED 终态；截图 07~11 |
| F2-8 | 房间边界与 E2E 冒烟 | ✅ | 2026-10-17 | 刷新（E2E #2/#3）、WS 断线（E2E #5）、双击（store 单测只调 1 次 API）、他端结束（E2E #4）、超时（手工 session 6 + 后端 IT）、40401（手工）、首页接口降级（E2E #6）；Playwright **6/6** 全绿；4 档视口无横向滚动 |

> F2 验收细节、截图与复现命令见 [`frontend/docs/F2-验收记录.md`](../ai-interview/frontend/docs/F2-验收记录.md)。

#### F3 任务明细（2026-10-17 ✅）

| 任务 | 内容 | 状态 | 完成日期 | 验证记录 |
|---|---|---|---|---|
| F3-1 | 报告页壳 + 轮询/重试 | ✅ | 2026-10-17 | `stores/report` 缓存 + 10004 轮询（1s→2s→3s，60s）+ `POST finish` 幂等重试 + 40401/ABORTED/未结束空态 + WS REPORT_READY；单测 10 条、E2E #2/#3（10004 自动恢复、clock 快进 60s 后重试成功） |
| F3-2 | 总分环、雷达、维度条 | ✅ | 2026-10-17 | SVG 总分环（64.46/62.90）；ECharts 雷达按需注册（Radar/Tooltip/Legend/Canvas），TEXT 5 轴/AUDIO 6 轴，ResizeObserver；维度条固定顺序 + 色带；单测 10 条（ScoreRing 3/DimensionBars 4/Radar 3）+ 截图 01/04 |
| F3-3 | 逐题证据折叠 | ✅ | 2026-10-17 | 按 roundNo 升序、默认展开首题、可多开/折叠；得分点/失分点/证据句/理由；空数组降级；AUDIO 按 roundNo 对齐 audioPath 回放（单实例 blob，失败中文降级）；单测 5 条 + 截图 03/04b/04c |
| F3-4 | 亮点/不足/建议 + 资源预览 | ✅ | 2026-10-17 | 三栏独立空态；推荐 Top2 + 首条资源，失败不阻塞报告；CTA 重练/成长/推荐/打印；截图 02/01 |
| F3-5 | 历史列表 | ✅ | 2026-10-17 | `usePagination` 防竞态/越界回退；10/20/50；当前页状态筛选（全部/进行中/已完成/已中止）+ 标注；`page/size/status` URL 同步；FINISHED/TIMEOUT→报告、进行中→继续、ABORTED→重开；单测 9 条（usePagination 6/PaginationBar 3）+ E2E #4 + 截图 07~10/12 |
| F3-6 | 报告/历史 E2E | ✅ | 2026-10-17 | `e2e/report.spec.ts` 4 条：完整报告、10004 恢复、60s 重试、历史分页/筛选/URL；全套 F2 6 + F3 4 = **10/10**；helpers 扩展独立学生注册/登录/鉴权注入/状态轮询 |

---

## 4. 变更与决策记录

| 日期 | 类型 | 内容 | 影响范围 |
|---|---|---|---|
| 2026-10-13 | 决策 | 交付边界=仅后端 `ai-interview/backend`，前端不在后端计划范围（**后端阶段边界**；前端已由 2026-10-17 的 D31 另行立项，见前端开发计划） | 计划 §1.3 |
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
| 2026-10-15 | 决策 | **D22：掌握度口径与幂等**——命中技能 = 已作答轮题目 skill_tags（种子题均单标签，一题一技能）；技能本次得分 = 该技能相关轮"最新一次内容分"的难度加权均分（权重复用 ScoreCalculator.difficultyWeight）；applyForSession 以 source_session_id 存在性幂等（9 表无唯一约束兜底），报告重试/并发生成/重启恢复收卷均不重复折算；掌握度更新在报告事务提交后独立 @Transactional（计划 §4.4 原文口径） | 计划 §3.8 / §6.7 |
| 2026-10-15 | 偏差 | **D23：5.2 交付推荐基础版**——RecommendationService 按 §6.7 优先级公式 (100-掌握度)×技能权重 取 Top3 + learning_resource 匹配（resources=ARTICLE/QUESTION、practices=PRACTICE/PROJECT）+ 下一次面试配置建议；weaknessReasons 初版仅两条简单规则（未考察/低于 requiredLevel），四规则 WeaknessDetector + 错题重练 + Redis 推荐缓存（TTL 10min）随 5.3 交付；未考察技能按掌握度 0 参与优先级（完全未覆盖视为最需补齐）。V6 学习资源种子（每岗 14 条）随 5.2 交付——recommendations 资源匹配的数据依赖 | 计划 §6.6 / §6.7 / §9-M5 |
| 2026-10-15 | 说明 | **D24：growth/recommendations 的 positionId 参数语义**（计划 §5.4 只写 `?positionId=` 可选）——growth：可选过滤，缺省按 skillCode 跨岗位合并（技能序 = 涉及岗位能力模型序，技能名取先出现岗位）；recommendations：显式指定（不存在→40401）> 最近一条能力记录的岗位 > 首个启用岗位（新用户也有默认推荐） | 计划 §5.4 #18/#19 |
| 2026-10-15 | 偏差 | **D25：5.3 推荐契约扩展与规则落点**——RecommendationVO 增 `retryQuestions[{questionId,questionContent,missingPoints}]`（计划 §5.4 #19 未列，§6.7 明确要求错题重练输出）；规则④（communication/voice 维度连续两次<60）产生的理由挂靠能力模型的 `communication` 技能条目（维度短板无可独立输出的载体）；规则③/错题重练数据源 = 最近一场 FINISHED/TIMEOUT 会话的逐题证据，复用 ReportService.evidenceOf（评分快照已清则确定性重算，与报告读取同口径）；无任何规则命中时 weaknessReasons 输出兜底说明（未考察→摸底建议、已达标→相对提升），保证接口契约恒非空；全部候选无短板 → nextInterviewSuggested 切换通用计划文案（不点名重点覆盖、难度+1）。缓存：Redis 键 `rec:{uid}:{positionId}` TTL 10min，报告落库后经 ReportGeneratedEvent（Spring 事件，报告异步线程内同步消费）预生成；推荐缓存与报告解耦（事件失败仅 WARN） | 计划 §5.4 #19 / §6.6 / §6.7 |
| 2026-10-15 | 决策 | **D26：管理端扩展接口语义**（计划 §5.5 #27~31★ 未细化部分）——dashboard：场次=started_at 非空（CREATED 草稿不计）、平均分=全部出分场次总分均值、近 7 日趋势 sessionCount 按 started_at 日期、avgScore 按 finished_at 日期，聚合走 MyBatis-Plus selectMaps 静态列名表达式；岗位 PUT=部分更新（非 null 生效，name 仅新增必填防破坏部分更新语义），岗位不提供删除（被题库/历史引用）；weights PUT=按组合并（提供的组须完整且组内和=1±0.01，未提供的组保留）；资源 DELETE=下架（enabled→0，与推荐匹配口径一致），skillCode 强校验对齐岗位能力模型；用户启停禁止管理员禁用自己（40001 防锁死）；审计切面（admin.controller 包切点）自动覆盖全部新接口 | 计划 §5.5 / §7 |
| 2026-10-15 | 说明 | SecurityIT 落位 `common` 包（横切关注）：M1 期间以分散等价用例补验（D10），M5-5.4 补齐计划 §8.2 的独立 SecurityIT（无 token 401 / 学生 admin 403 / 跨用户 40401），403 覆盖面扩至 M5 全部新增管理端路径；另发现并修复共享测试库残留问题——AdminExtendedIT 中途失败会遗留启用岗位污染 PositionApiIT，已加 @AfterEach 自愈清理（tp% 岗位强制停用） | 计划 §8.2 |
| 2026-10-15 | 决策 | **D27：6.1 覆盖率口径**——JaCoCo 0.8.12 接入 pom（prepare-agent + verify 阶段 report/check），BUNDLE 行覆盖 ≥60% 作为门禁固化；统计范围 = `com.example.interview.**` 业务实现代码，剔除 dto/entity/mapper/config/主类（纯数据与装配类不参与核心质量口径）。实测 89.0%（2943 行覆盖 2619），核心包最低 interview.websocket 69.3%、ai.prompt 72.9%，全部达标 | 计划 §9-M6-6.1 |
| 2026-10-15 | 偏差 | **D28：6.2 抽测执行方式**——"人工作答"由脚本按三档标准构造（好=全 4 得分点+结构化分点+因果衔接；中=前 2 得分点+约 150 字；差=零要点模糊短答），人工评分基准 85/62/35 并逐条复核（覆盖 3/4 的中档复核 68 更贴切，仍按 62 保守记录）；每岗 4 场 × 5 轮 = 20 条（好 8/中 6/差 6），逐题对比报告 `questionEvidence[].contentScore`。抽测脚本发现并修正两处测试侧问题：轮次推进依赖"消息差集"而非新消息（D12 预写主问题，推进无新 AI 消息）、失分词清洗需排除得分点同名词。结果 **40/40 分差 ≤15（100%，达标线 80%）**，记录表见《AI质量抽测记录_M6.md》 | 计划 §8.3 / §9-M6-6.2 |
| 2026-10-15 | 偏差 | **D29：6.3 部署落地细节**——①frontend 为占位静态页（alpine 拷贝式构建容器，真实 Vue3 前端联调阶段替换为 node:20 构建，计划 §8.3 已界定前端不在后端范围）；②nginx depends_on frontend `service_completed_successfully`（防 nginx 默认页先初始化 frontend-dist 卷）；③backend `mem_limit: 768m`（§11-R2 落地）+ 容器内构建走阿里云 settings；④正式栈与 dev 栈同项目名共用 `mysql-data` 卷（dev 数据延续，**两个 mysql 禁止同时运行**，切换用 `docker compose down` / `up -d`）；⑤nginx /ws/ 增 `proxy_read_timeout 3600s`。验证：`up -d` 一键起五服务，80 端口静态页 200、/api 全链路（登录→岗位→建会话→start→作答→finish→报告 64.46 分→推荐）、/ws 101 升级全通 | 计划 §10 / §9-M6-6.3 |
| 2026-10-15 | 决策 | **D30：6.4 断网模拟方式**——宿主机不真断网（不影响其他项目与会话），以 compose override（docker-compose.offline.yml）把核心服务（mysql/redis/backend/frontend）置于 `internal: true` 网络实现零外网出口，nginx 另挂普通边缘网络仅作 80 端口入口（nginx 仅静态页+反代、自身不发起外部请求）；断网证据 = backend 容器内 `/dev/tcp` 对外网 IP 与域名均不通（实测 Network unreachable / bad address）+ `printenv AI_PROVIDER=mock`。演练经 Nginx 入口走与浏览器演示同路径；qa/m6_offline_drill.sh 一键完成 停 dev 栈→起离线栈→断网证明→全流程演练→收栈恢复 dev 栈（同项目名同卷，切换经 compose recreate，数据无损）。演练答案构造复用 6.2 抽测脚本的三档构造器（import 复用） | 计划 §9-M6-6.4 |
| 2026-10-17 | 决策 | **D31：前端开发计划立项**——按方案 §3.1 固定技术栈（Vue 3.4 + TypeScript + Vite 5 + Element Plus + Pinia + Axios + ECharts + 原生 WebSocket + MediaRecorder + Web Speech）实施；范围为学生端 12 页 + 管理端 7 页 + 工程/测试/部署/答辩，F1~F6 预估 26.5 人日；后端不改接口 | 前端计划 §1/§3/§11 |
| 2026-10-17 | 决策 | **D32：面试交互采用"WS 通知 + REST 对账"模型**——回答/finish 异步受理；WS 事件到达后必须重新拉取 detail+messages，按服务端消息 ID 去重；轮次以 `detail.currentRound` 为唯一依据（不用消息数推断）；报告 10004 视为正常态轮询 + finish 重试 | 前端计划 §2.3/§2.4/§5.7/§7 |
| 2026-10-17 | 决策 | **D33：管理端知识库与评分权重页按现有契约落地**——知识库只做"新增片段 + 重建索引"（后端无列表接口，页面明确说明，不新增后端接口）；方案 §11 的 `/admin/evaluation` 复用 `PUT /admin/positions/{id}/weights`，实现为评分权重页 | 前端计划 §5.15/§5.17/附录 D |
| 2026-10-17 | 决策 | **D34：前端产物部署链路修复与离线构建策略**——frontend 构建容器必须把 `dist` 写入共享 `frontend-dist` 卷（compose 为 frontend 增加该卷），验收以页面内容 + assets 200 为准；离线演练前预构建镜像/预装依赖，断网时不执行 `npm ci`；ASR 离线不可用时以文字模式/手工转写兜底 | 前端计划 §10.3/§10.5/§13 |
| 2026-10-17 | 决策 | **D35：F1 实现口径**——依赖按计划 `^` 范围解析为 Vue 3.5.x / Element Plus 2.14.x / Axios 1.20.x（Axios 响应泛型为条件类型，`request<T>()` 边界收窄）；unplugin-vue-components 生成的 `src/components.d.ts` 入库，保证全新克隆直接 `vue-tsc` 可过；F2~F5 的 17 个页面以统一 `PlaceholderView` 占据最终路由，F2 起逐页替换；原 `index.html` 备份为 `docs/legacy-placeholder-index.html`，Dockerfile 保持不动到 F6-4 | F1-1~F1-8 |
| 2026-10-17 | 决策 | **D36：F1 启动与角色跳转落地**——`main.ts` 在挂载前 await `auth.restore()` + `router.isReady()`，避免无权页面闪现；`GET /me` 网络失败保留 token 并标记“服务暂不可达”，仅 40101/10003 清空；管理员访问学生区回 `/admin/dashboard`（个人中心例外），学生访问管理区跳 `/403`；`/` 同时用 redirect 与守卫按角色纠正 | F1-5 |
| 2026-10-17 | 决策 | **D37：难度自适应口径（修复计划 D1，选 B 方案）**——R5/R6（连续低/高分）明确定义为「**追问难度/提示强度自适应**」：规则引擎产出的 difficultyDelta 只进入追问 Prompt 指令，主问题在 start 组卷时按目标难度一次性锁定、回答期间不漂移（原实现即如此；核实确认 difficultyDelta 从不影响选题）。README 首屏表述已由「难度自适应」改为「追问难度/提示强度自适应；主问题组卷时锁定」。若课程验收明确要求主问题动态换难度，另立 A 方案任务（预组多难度候选），不并入本轮 | FIX-13（Phase 3 文档收尾复核） |
| 2026-10-17 | 决策 | **D38：液态玻璃视觉体系（计划 v1.1）**——纯 CSS 三层结构：`AppBackdrop`（渐变 + 4 光斑 + 网格 + 颗粒）+ `GlassPanel`/`.card`/`.glass-surface`（backdrop-filter + 高光 + 多层阴影）+ Element Plus 浮层玻璃化；认证页品牌区/卡片、Shell 侧栏顶栏、403/404、公共状态组件全部接入；强制 `@supports`、`prefers-reduced-transparency`、`prefers-reduced-motion`、打印白底降级；不新增依赖/外部图片；F2~F5 新页面复用 | 计划 §6.7/§8.3.1/F1-3 |
| 2026-10-17 | 决策 | **D39：F2 房间交互口径**——房间以 `stores/interview` 为唯一事实源，`useInterviewRoom` 统一编排 WS/轮询/倒计时/终态；CREATED 进入房间即自动 start（先连 WS 再 start，start 返回首题兜底）；提交/finish 只当受理，AI 产出以 WS 通知 + REST 对账；FINISHED/TIMEOUT/ABORTED 停留在房间展示结果与报告/历史 CTA，不自动跳转（报告页归 F3，F3 落地后再接 10004 轮询与自动跳转）；AUDIO 会话在 F4 录音 UI 交付前以文字作答兜底并明确提示，不伪造 voice 维度；移动端不自动聚焦输入框，避免把题面顶出屏幕 | F2-4~F2-7 / 计划 §5.7/§7.5/§7.8 |
| 2026-10-17 | 决策 | **D40：F2 测试与数据口径**——单元/组件测试覆盖 round/draft/countdown/WS/capabilities/store 与 6 个关键组件；E2E 用 Playwright + 系统 Chrome，`globalSetup` 只登录一次 demo01 写 storageState（规避 5 次/分钟登录限流），用例内用 API 准备会话；F2 验收产生的 16+ 场测试会话通过 abort + SQL 删除 message/report/ability/session 清理，demo01 历史恢复 7 场；覆盖率阈值（70%）与 API 模块补齐留 F6-1 | F2-8 / 计划 §9.4/§14.2 |
| 2026-10-17 | 决策 | **D41：F3 报告页口径**——报告以 REST 为唯一事实源，`stores/report` 按 sessionId 缓存；10004 正常态轮询（1s→2s→3s，60s 上限，隐藏暂停/恢复立即补一次），60s 后允许 `POST finish` 幂等重试；WS REPORT_READY 仅作加速；TEXT 5 维、AUDIO 6 维，缺失 voice 不补 0；音频回放走受保护 blob + 单实例 Audio；推荐预览失败不阻塞报告；房间终态保持停留（不自动跳转），报告页自处理 10004；登出/切号清空报告缓存 | F3-1~F3-4 / 计划 §5.8/§7.7 |
| 2026-10-17 | 决策 | **D42：F3 历史与 E2E 数据口径**——历史服务端分页 10/20/50，状态筛选为当前页客户端筛选并明确标注；`page/size/status` 与 URL 同步（router.replace），越界页码回退最后一页；E2E 因登录限流 5 次/分/IP 改为串行执行，报告用例共享独立 E2E 用户、历史用例单独用户；测试数据用 `e2e_*` 前缀并在验收后清理，demo01 历史恢复 7 场 | F3-5/F3-6 / 计划 §5.9/§9.4/§9.7 |



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
| P7 | 2026-10-15 | Docker `internal: true` 网络上的发布端口宿主机不可达（curl 000），纯 internal 网络方案无法从宿主机驱动离线演练 | ✅ 已解决 | nginx 双网络：`networks: [edge, svc]`（edge 普通网络在前，80 端口发布在边缘网；svc 为 internal 承载服务间通信），宿主机恢复 200 访问；核心服务仍只挂 svc 保持零外网出口（方案见 D30） |
| P8 | 2026-10-15 | bash 脚本中 `$VAR` 后紧跟全角字符（如 `$BASE）`）会把多字节字符并入变量名，`set -u` 下报 unbound variable | ✅ 已解决 | 变量与中文之间一律 `${VAR}` 显式定界（qa/m6_offline_drill.sh 三处）；教训随 6.4 交付记录 |
| P9 | 2026-10-17 | **frontend-dist 卷未接前端产物**：`frontend` 服务未挂载该卷，Dockerfile 只把占位 index.html 拷进镜像内；nginx 挂载空卷后被初始化成 nginx 默认欢迎页。M6-6.3 只验证 HTTP 200，未验证内容；本次复核 80 端口实际为 `Welcome to nginx!`，frontend 容器 `Exited (0)` | ✅ 已修复（2026-10-17 后端修复 FIX-02，commit 16b6c51） | frontend 改为初始化容器：产物 COPY 到 /opt/frontend，compose 启动时 `rm -rf /site/* && cp -a /opt/frontend/. /site/` 强制覆盖卷（新卷/旧卷均生效）；独立 compose 项目验证页面含「AI 模拟面试与能力提升平台」+ /api 401 JSON。真实 Vue3 产物落地后仅替换 /opt/frontend 内容，编排不变 |
| P10 | 2026-10-17 | `答辩演示脚本.md` §14 的评分权重请求体为 `{"weights":{"technical":...}}`，与后端 `WeightsUpdateRequest`（`{"scoreWeights":{content/expression/total}}`）不一致；照脚本执行会 40001 | ⬜ 待修正（FIX-19 / F6-5，Phase 3） | 前端按真实契约调用；FIX-19/F6-5 修正演示脚本 §14 并补 curl 示例 |
| P11 | 2026-10-17 | dev 库存在测试残留：`GET /api/positions` 返回第 3 个启用岗位 `ACC_c4b462`（defaultRounds=100）；管理端题库/资源/用户含 `acc_*` 数据。演示选择岗位页会出现异常项 | ⬜ 待清理（FIX-19 / F6-6，Phase 3） | `ACC_*` 岗位与 `acc_*`/`race_*`/`dis_*` 用户已被 2026-10-17 验收检查会话清理（报告 §9，当前 2 岗位）；历史残留（61 题/45 知识/29 资源）仍在，演示前建议全新迁移库；qa/cleanup_e2e_data.sh 随 FIX-19 交付 |
| P12 | 2026-10-17 | 方案 §11 的 `/admin/evaluation` 无独立后端接口（只有岗位权重接口） | ✅ 已决策（D33） | 复用 `PUT /admin/positions/{id}/weights`，实现评分权重页；`/admin/evaluation` 重定向到岗位权重页 |
| P13 | 2026-10-17 | 方案 §10.5 只列 7 个管理接口，实际后端 18 个（新增岗位/权重/资源/用户/看板） | ✅ 已按实际契约记录（D31） | 前端按 39 REST 开发；管理端菜单覆盖 18 个接口对应能力；差异见前端计划 §2.5 |
| P14 | 2026-10-17 | jsdom 25 的 `Blob` 没有 `text()`，导致受保护音频的“HTTP 200 + JSON 业务错误”检测在单测中失效（真实 Chrome 具备该方法） | ✅ 已解决 | `fetchBlob` 增加 FileReader 兜底；单测覆盖成功 blob 与 40401 JSON 两条路径 |
| P15 | 2026-10-17 | F1 联调时登录限流（5 次/60s/IP）被密集 UI 用例触发；429 尝试同样计入 Redis INCR，但 TTL 只在首次计数设置，窗口结束后自动恢复 | ✅ 已确认（非缺陷） | 429 页面已实现 60s 倒计时并留证；联调脚本后续避免同一分钟密集登录，或等待窗口重置（同 P5） |
| P16 | 2026-10-17 | F1 的 `element-overrides.scss` 把 `.el-button--primary` 蓝色渐变无条件套到 `is-text/is-plain`，导致蓝字叠蓝底、按钮文字不可见（F2 首页/岗位/房间的“查看能力模型/查看全部历史/查看完整推荐”等按钮暴露） | ✅ 已修复（F2） | 增加 `.el-button--primary.is-plain/is-text/is-link` 的透明/浅色底与 hover 规则；F2 截图复验文字可见；后续 Element Plus 变体需同时检查 `element-overrides.scss` |
| P17 | 2026-10-17 | F2 E2E/手工验收在 demo01 名下累积 16+ 场测试会话，首页统计与截图出现 19 场、最近记录为 ABORTED，污染演示数据 | ✅ 已清理（F2） | 先 abort 活跃会话，再按 id 段删除 `interview_message/evaluation_report/ability_record/interview_session`；`demo01` 历史恢复 7 场；F6 交付 `qa/cleanup_e2e_data.sh` 并建议 E2E 使用独立测试用户 |
| P18 | 2026-10-17 | F3 E2E 首轮并发执行触发后端登录限流 429（5 次/分钟/IP，含 globalSetup 与独立学生登录） | ✅ 已解决（F3） | 报告用例改为串行 + 共享一个 E2E 用户，历史用例单独用户；全套 10 条 E2E 稳定通过；验收后删除 `e2e_*`/`shots2_*` 测试用户与会话，demo01 历史恢复 7 场；与 P5/P15 同源，F6 可在 E2E 环境放宽限流配置 |


> 开发中新遇到的问题在此追加；已解决的把状态改为 ✅ 并填解决方式。计划 §11 的 R1~R12 作为预置风险清单，触发时在此登记。

## 6. 环境状态备忘

| 组件 | 状态（2026-10-17 前端计划核对） | 备注 |
|---|---|---|
| JDK 17 | ✅ Temurin 17.0.20（`~/tools/jdk-17.0.20.1+1`，tarball 安装） | 运行/测试统一 `JAVA_HOME=~/tools/jdk-17.0.20.1+1/Contents/Home` |
| JDK 25 | 仍存在（系统默认） | 仅做兜底；编译由 `maven.compiler.release=17` 保证 |
| Maven | 3.9.16（~/tools/apache-maven-3.9.16） | settings.xml 已配阿里云镜像 |
| Docker | ✅ colima 运行中（实测可用内存 ~3.8G，非默认 2G） | 镜像经 daocloud 源拉取（D7）；M6 部署镜像已本地化（maven/temurin-jre/nginx/alpine） |
| aiinterview-mysql | ✅ healthy，端口 3306 | dev 库 **V7**（2026-10-17 验收修复迁移：question_evidence_json / planned_duration_minutes / uk_ability_source_skill）+ 演示数据；`ACC_*` 岗位已被验收检查会话清理，历史残留仍见 P11；测试库 ai_interview_test 同步 V7 |
| aiinterview-redis | ✅ healthy，宿主机端口 6380 | 6379 被其他项目占用（D6） |
| 本机 8080 | ⚠ 2026-10-17 检测空闲；历史 P6 曾被其他项目占用 | 当前 dev 后端跑在 18080（health 200）；前端代理默认 8080，可用 `.env.development.local` 指 18080 |
| 本机 18080 | ✅ 本项目 dev 后端（health 200） | 前端联调临时用；正式演示用 compose 80 |
| 本机 5173 | ✅ F1 起由 Vite dev 占用（`npm run dev`） | 固定端口 + `strictPort: true`；后端 18080 通过 `.env.development.local` 代理 |
| 本机 80 | ⚠ 2026-10-17 核对时由临时验收栈占用并返回 nginx 默认页，现已释放 | 前端 F6-4 修复产物卷后由本项目 compose 的 nginx 占用；修复前不可作为演示入口 |
| frontend 工程 | ✅ Vue3 + Vite + TS 工程（F3 完成，2026-10-17） | `npm install && npm run dev`；首页/岗位/设置/房间/报告/历史已替换占位页；F4/F5 其余页面仍为占位页；Dockerfile 仍为占位版，F6-4 替换并修复卷（P9） |
| Node / npm | v22.23.3 / npm 10.9.9（pnpm 12.11.2 可用） | F1 实测 412 packages 安装成功；计划按 Node 20 LTS 兼容写法，Docker 用 node:20-alpine |

**日常启动（M1 之后继续开发时）：**

```bash
cd ai-interview
docker compose -f docker-compose.dev.yml up -d          # 容器已常驻可跳过
export JAVA_HOME=~/tools/jdk-17.0.20.1+1/Contents/Home
cd backend && mvn spring-boot:run                        # dev profile，Redis 连 6380
# 若 8080 被占（P6）：mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
# 测试：mvn test（用 ai_interview_test 库 + 6380 Redis）；质量门禁：mvn verify（JaCoCo ≥60%）
# 账号：admin / Admin@123456；demo01 / 123456
# 前端（F1 已完成）：cd ../frontend && npm install && npm run dev   # http://127.0.0.1:5173
#   后端在 18080 时：echo 'VITE_PROXY_TARGET=http://127.0.0.1:18080' > .env.development.local
#   门禁：npm run lint && npm run type-check && npm run test:unit && npm run build
# 正式演示栈（M6-6.3）：先 docker compose -f docker-compose.dev.yml stop（防双 mysql 共卷），
#   再 docker compose up -d → Nginx http://127.0.0.1:80；用毕 docker compose down 后恢复 dev 栈
#   ✅ P9 已修复（FIX-02）：frontend 初始化容器启动时强制覆盖 frontend-dist 卷，80 端口页面正确
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
| 2026-10-15 | **M5 进行中（5.1/5.2 完成）**：任务 5.1、5.2 ✅（产物 #11 交付，api.http 增用例 26/27）；新增 growth 包 AbilityRecord/LearningResource 实体与 Mapper、MasteryUpdater（0.7/0.3 折算+难度加权+幂等）、RecommendationService、SkillGrowthVO/RecommendationVO，ReportService 挂接掌握度更新（报告事务后独立事务），GrowthService/GrowthController 补 growth 与 recommendations 接口，Flyway V6 种子学习资源 28 条（每岗 14）；`mvn test` 97 用例全绿（M5 新增 12：MasteryUpdaterTest 11、HistoryGrowthIT 增 1，测试类 20）；手工验证：curl（18080）两场面试→ability_record 两条（52.53→48.95 折算吻合）→growth 曲线/推荐 Top3 资源匹配；登记决策 D22~D24 | ZCode（M5 开发会话） |
| 2026-10-15 | **M5 完成**：任务 5.3、5.4 ✅（产物 #12 交付，api.http 增用例 28~32 并修订 27）；5.3 新增 growth/engine 双引擎（WeaknessDetector 四规则纯逻辑 + RecommendationEngine 优先级/错题重练/通用计划），RecommendationVO 增 retryQuestions，ReportService 增 evidenceOf 与 ReportGeneratedEvent、新增 RecommendationCacheWarmer（Redis TTL 10min 预生成），RecommendationService 重写接入双引擎+维度历史+最近一场证据+缓存；5.4 新增 admin 包 DashboardService/AdminPositionService/AdminResourceService/AdminUserService 与四个 Controller、DashboardVO/PositionAdminVO/PositionUpsertRequest/ResourceUpsertRequest/WeightsUpdateRequest；`mvn test` 114 用例全绿（5.3/5.4 新增 17：WeaknessDetectorTest 5、RecommendationEngineTest 3、RecommendationIT 2、SecurityIT 3、AdminExtendedIT 4，测试类 25）；手工验证：curl（18080）dashboard（4 用户/8 场/61 题/均分 59.07）、权重写入回读、资源建删、禁用登录 10003→恢复、Redis rec:* 缓存键；登记决策/偏差 D25~D26 及 SecurityIT 落位说明 | ZCode（M5 开发会话） |
| 2026-10-15 | **M6 进行中（6.1/6.2/6.3 完成）**：6.1 ✅ pom 接入 JaCoCo（≥60% 行覆盖门禁），`mvn verify` BUILD SUCCESS（114 用例 0 失败），覆盖率 89.0%、核心包全部 ≥60%；6.2 ✅ AI 质量抽测双岗各 20 条（好/中/差），**40/40 人工分差 ≤15（100%≥80% 达标）**，四项核对（分差/追问相关性/报告完整/建议对短板）全过，记录表产出《AI质量抽测记录_M6.md》，抽测脚本入库 qa/m6_ai_quality_sampling.py；6.3 ✅ backend/Dockerfile（多阶段+阿里云 settings）+ nginx/nginx.conf + frontend 占位页 + docker-compose.yml 五服务编排 + .env(.example)，`docker compose up -d` 一键起，Nginx 80 验证：静态页 200、/api 全链路（登录→岗位→会话→作答→finish→报告 64.46→推荐）、/ws 101 升级；验证后正式栈撤下、dev 栈已恢复（数据完好）；登记决策/偏差 D27~D29 | ZCode（M6 开发会话） |
| 2026-10-15 | **M6 完成（6.4/6.5/6.6 交付，开发计划全部完成）**：6.4 ✅ 离线演练套件（docker-compose.offline.yml + qa/m6_offline_drill.sh/.py），断网证明 5 项 + 业务演练 13/13 通过（文字 75.66 分/追问 15 次 + 语音含 voice → 报告 → 历史/成长/推荐 → 管理端 → WS），结果留存 qa/offline_drill_{env,result}.json，演练后 dev 栈自动恢复；6.5 ✅ 验收自检 15/15 全过并附实测证据，产出《AI模拟面试与能力提升平台_验收自检_M6.md》（本会话新证：SQL 题型比例/追问消息表/两场后成长曲线 5 条全 2 点/知识检索 DEBUG 日志/报告五要素实录）；6.6 ✅ backend/README.md（新人 30 分钟启动路径 + FAQ）+ ai-interview/答辩演示脚本.md（方案 §19 15 步逐条映射 + 故障预案）；登记决策 D30、问题 P7/P8（均解决）；产物清单补登 #13~#16 | ZCode（M6 开发会话） |
| 2026-10-17 | **前端开发计划交付（F1~F6 立项，未开工）**：产物 #17《AI模拟面试与能力提升平台_前端开发计划.md》（2700+ 行）；按后端实际 39 REST + 1 WS 重写契约基线，覆盖 19 个页面/路由、架构/类型/状态机/测试/部署/验收；预估 26.5 人日。**本次实测发现并登记**：P9 frontend-dist 卷未接产物（80 端口实际为 nginx 默认页，M6-6.3 只验证 200）、P10 答辩脚本 §14 权重 payload 与后端不一致、P11 dev 库 `ACC_*` 测试残留；新增 D31~D34（前端范围/异步对账模型/知识库与权重页降级/产物卷修复与离线构建），F1~F6 全部 ⬜ | ZCode（前端计划会话） |
| 2026-10-17 | **F1 完成（工程基座 + 认证 + 应用外壳）**：F1-1~F1-8 全部 ✅（产物 #18 交付）；Vite 5 + Vue 3 + TS + Element Plus + Pinia + Vue Router + Axios；设计令牌/三种布局/403/404、39 REST + 1 WS 类型与 API 骨架、auth store + 21 视图路由/3 重定向 + 角色守卫、登录注册（10001/10002/10003/42901 UI）、离线条/traceId/错误边界/懒加载兜底；`lint` 0/0、`type-check` 0 error、`test:unit` 34/34、`test:coverage` 通过、`vite build` 成功；5173 `/api` 登录与 `/ws` 101 代理实测通过；功能冒烟 16 项 + 4 档视口无横向滚动；证据留存 `frontend/docs/F1-验收记录.md` + 10 张截图；登记 D35/D36、P14/P15；F1 状态 ✅，下一里程碑 F2 | ZCode（F1 开发会话） |
| 2026-10-17 | **后端验收修复 Phase 0~2 完成（FIX-01~08，分支 fix/backend-acceptance-20261017）**：依据《后端验收检查报告_20261017》（P0×2/P1×6）与《后端修复计划_20261017》执行。**FIX-01**（0610eb1）真实 Provider 补发 Authorization Bearer 头 + stub 断言头/401 降级两用例；**FIX-02**（16b6c51）frontend 初始化容器强制覆盖 frontend-dist 卷（P9 关闭，新卷/旧卷双验证页面含产品名）；**FIX-03**（116a1a8）WS 心跳发 PingMessage、超时 4408 与鉴权 4401 区分、pong-timeout/heartbeat-interval 配置化（3 单测对旧码红灯 + WebSocketLifecycleIT 9s 存活）；**FIX-04**（0f32b24）WS/HTTP Origin 白名单配置化（prod 含 80 两源），并发现 HTTP CORS 全局拦截 WS 握手 → CORS 收窄 /api/**（WebSocketOriginIT 同源 101/5173 回归/外部 403）；**FIX-05**（bf616f2）TurnEngine applyFollowup/applyAdvance 先 CAS 后写入、失败回滚（TurnEngineTerminalRaceIT 4 用例对旧码红灯：finish/abort/timeout 交叉无终态后消息/轮次漂移/重复报告）；**FIX-06**（26ff069）Prompt 历史过滤未来主问题（PromptHistoryIT 红转绿）；**FIX-07**（e24caef）V7 迁移 + 报告逐题证据落库，getReport/evidenceOf 不再调 AI（ReportEvidenceIT 3 用例含旧报告回填一次）；**FIX-08**（1e9a9b8）时长落库 + recoverOnStartup 按真实时长 + InterviewRecoveryService 重投 PROCESSING_AI（SessionDurationRestartIT @DirtiesContext 真实重启 / ProcessingRecoveryIT 双投幂等）。V7（question_evidence_json、planned_duration_minutes、ability_record 去重+uk_ability_source_skill）先测试库后 dev 库，迁移前双库 mysqldump 备份至仓库外。Gate 2：JDK17 `mvn -B verify` **134/134 全绿**（基线 114 + 新增 20），JaCoCo 89.36%；compose 栈端到端：同源 WS 101 + 95.4s 存活（4×PING 回 PONG 不关闭）+ 外部 Origin 403 + 页面内容 + /api 401。登记 D37（难度口径 B 方案）；P9 关闭，P10/P11 转 Phase 3（FIX-19）；#6/#14 按 D5 改判"协议级通过、真实 key 待环境验证"（不宣称 15/15）。Phase 3/4（FIX-09~20）待确认后继续 | ZCode（后端验收修复会话） |
| 2026-10-17 | **F2 完成（面试主流程：文字 + WS + 倒计时）**：F2-1~F2-8 全部 ✅（产物 #19 交付）；新增首页/岗位列表/岗位详情/面试设置/面试房间 5 页，`stores/interview` + `useInterviewRoom` + 原生 `useWebSocket`（1/2/4/8/15s × 8 重连、4401 不重连、断线 2s 轮询）+ `useCountdown` + `useInterviewCapabilities` + `utils/round/draft` + 7 个 interview 组件；文字作答支持 IME、Ctrl/Cmd+Enter、乐观气泡、失败保留、10005 刷新、草稿恢复；finish/abort 二次确认与 FINISHED/TIMEOUT/ABORTED 终态；`lint/type-check` 0/0、单测 **91/91**（19 文件，F2 新增 57）、coverage 59.32%、build 成功、Playwright E2E **6/6**（首页导航/首页接口降级/文字闭环+刷新/中止+终态刷新/跨标签页结束/WS 断线轮询）；1440/1024/768/390 无横向滚动、0 pageerror；12 张截图；登记 D39/D40、P16（按钮样式修复）/P17（测试数据清理）；demo01 历史恢复 7 场；F2 ✅，下一里程碑 F3 报告+历史 | ZCode（F2 开发会话） |
| 2026-10-17 | **F1 视觉升级（计划 v1.1 液态玻璃）**：新增 `AppBackdrop`（渐变 + 4 光斑 + 网格 + 颗粒）与 `GlassPanel`（blur/saturate/高光/多层阴影）；改造认证页品牌区/表单卡片、Student/Admin Shell 侧栏顶栏、403/404、ErrorState/TraceIdText/LoadingSkeleton、Element Plus 输入/按钮/下拉/弹窗/MessageBox/离线提示条；新增玻璃设计令牌（§6.7）与视觉规范（§8.3.1）、F1-3 交付与验收项 26、R-F18、D38；提供 `@supports`/`prefers-reduced-transparency`/`prefers-reduced-motion`/打印白底降级，零新增依赖；11 张液态玻璃截图；`lint/type-check/test:unit/build` 复跑全绿 | ZCode（F1 视觉升级会话） |
| 2026-10-17 | **F3 完成（报告与历史）**：F3-1~F3-6 全部 ✅（产物 #20 交付）；新增报告页/历史页、`stores/report`（缓存 + 10004 轮询 1s→2s→3s/60s + finish 幂等重试 + 隐藏暂停/恢复 + 账号切换清空）、`useEchart`/`usePagination`/`useAudioReplay`、`utils/report`、ScoreRing/RadarScoreChart/DimensionBars/EvidenceAccordion/PaginationBar；历史服务端分页 10/20/50 + 当前页状态筛选 + page/size/status URL 同步；`lint/type-check` 0/0，单测 **133/133**（27 文件，F3 新增 42），coverage **68.31%**，build 成功（ReportView 懒加载 157.58KB gzip），Playwright E2E **10/10**（F2 6 + F3 4：完整报告/10004 自动恢复/clock 60s 重试/历史分页筛选）；16 张截图；测试数据清理后 demo01 历史 7 场；登记 D41/D42、P18；F3 ✅，下一里程碑 F4 | ZCode（F3 开发会话） |
