# AI 模拟面试与能力提升平台（ai-interview）

> **项目性质声明**：本项目是 **JavaEE 课程的课程设计（课程作业）**，用于课程学习与答辩演示，非商业产品。
> 开发周期：2026-10 起，按后端 M1~M6 + 前端 F1~F6 + 增量功能 R1~R4（简历分析与个性化面试）推进；文档与进度对课程要求全程留痕。

---

## 1. 项目简介

面向学生求职场景的 **AI 模拟面试与能力提升平台**（当前交付后端 M1~M6 + 增量 R1~R4 + 前端 F1~F6）。学生选择目标岗位后，系统自动组卷并驱动大模型进行**多轮模拟面试**（支持动态追问、追问难度/提示强度自适应；主问题在组卷时按目标难度一次锁定、回答期间不漂移），面试结束后产出**六维评估报告**，并基于能力短板给出**学习资源推荐与成长曲线**。增量功能支持**上传简历 → PII 脱敏解析 → AI 多维评分与分级改进建议 → 个性化面试**（按简历技能偏好组卷、题干结合项目经历改写、追问深挖项目细节、报告"简历 vs 实测"对比）。

业务闭环：

```text
选择岗位 → 题库组卷 → AI 多轮面试 → 动态追问
        → 多维评估 → 生成报告 → 学习推荐 → 能力成长记录
增量：上传简历 → 脱敏解析 → 六维分析（改进建议）
        → 个性化面试（偏好组卷/题干改写/项目深挖/简历实测对比）
```

核心能力（后端）：

- **面试状态机**：CREATED → RUNNING → WAITING_USER ⇄ PROCESSING_AI → FINISHED/ABORTED/TIMEOUT，数据库状态为唯一事实源，条件更新乐观锁防并发（双击重复提交天然被拒）。
- **组卷算法**：按题型比例（技术 50% / 项目 20% / 场景 20% / 行为 10%）+ 难度带 + 技能覆盖贪心抽样；主问题序列在 start 时一次性落库，杜绝"答题中换题"。
- **动态追问规则引擎**：方案定义的 7 条追问规则（缺失要点追问、模糊回答要求举例、失分点边界追问、覆盖充分升难度、连续低分降难度、连续高分深挖、同题追问 ≥3 强制换题），M3 起接入逐题评分输入。其中"升/降难度"作用于**追问难度与提示强度**（difficultyDelta 进入追问指令），主问题难度在 start 组卷时锁定、回答期间不漂移（决策 D37，B 方案）。
- **多维评估与报告**：逐题评分（关键得分点覆盖率规则引擎）→ 难度加权六维聚合（technical/depth/logic/match/communication/voice）→ 总分（内容×0.5+表达×0.3+应变×0.1+匹配×0.1，岗位可调权重）→ 报告含分数/证据/亮点/不足/建议与逐题明细；finish 幂等兼报告失败重试入口，TIMEOUT 自动收卷。
- **语音面试链路**：音频回答 multipart 受理（webm/wav/mp3 白名单 + UUID 落盘），前端 Web Speech 转写后与文字同一分析链路；语速/停顿/语气词指标换算 voice 维度；受保护音频回放。
- **知识库轻量 RAG**：回答经词典法关键词提取（能力模型 + 知识库 keywords 聚合、最长匹配）→ MySQL ngram FULLTEXT 检索 Top5 知识片段 → 注入评分与出题 Prompt；管理端新增片段即被检索。
- **AI 双实现**：`MockAiProvider` 离线规则实现（默认，无网可完整演示）+ `OpenAiCompatibleProvider`（OpenAI 兼容 API，如通义千问 qwen-plus）；三级容错：JSON 容错解析 → 失败重试 1 次 → Mock 降级 → 连续 3 次失败会话锁定 mock；**用户级 AI 配置（V9）**：每人在个人中心配置自己的 OpenAI 兼容端点（apiKey AES-256-GCM 密文落库），经 RoutingAiProvider 按用户路由即时生效，无配置回退平台默认。
- **简历分析与个性化面试（增量 R1~R4）**：四格式（pdf/docx/md/txt）上传 → 魔数/白名单校验 → 文本抽取 → PII 先脱敏再入库送模型 → 规则解析（板块/技能/项目）→ AI 六维评分（basic/education/skill/project/experience/expression）+ 高/中/低风险分级建议（3~8 条带证据句）；个性化面试四环节：组卷带内优先命中简历技能（PROJECT 保底）、题干批量改写结合项目经历（失败降级整场原题干、questionId 与评分基准不变）、追问注入简历要点（项目深挖）、报告"简历 vs 实测"对比条目；focus-skills 重点技能预览（matched/gaps/hitRatio）；配额 20 份/每日 10 次。
- **实时推送**：WebSocket `/ws/interviews/{id}`，下行 QUESTION / FOLLOW_UP / TRANSCRIPT / TIMER / PROGRESS / REPORT_READY / ERROR 消息；query token 鉴权 + 心跳。
- **安全**：BCrypt 密码、JWT 无状态认证（Redis 黑名单）、登录限流、接口权限（STUDENT/ADMIN）、面试记录归属校验、统一错误码。

## 2. 技术栈

| 层 | 技术 | 说明 |
|---|---|---|
| JDK | Java 17 | `maven.compiler.release=17` |
| 框架 | Spring Boot 3.3.12 / Spring MVC | 单体后端 |
| 安全 | Spring Security 6 + JWT (jjwt 0.12) | 无状态认证 + Redis 黑名单/限流 |
| ORM | MyBatis-Plus 3.5.7 | boot3 starter；JSON 列 JacksonTypeHandler |
| 数据库 | MySQL 8.0（utf8mb4 + FULLTEXT ngram） | 11 张业务表（基线 9 + 简历 2，另有 `interview_session` 可空加列） |
| 缓存 | Redis 7 | token 黑名单、限流、推荐缓存 |
| 实时 | Spring WebSocket | 面试过程推送 |
| AI | OpenAI 兼容 Chat API / Mock | 默认 `ai.provider=mock` 离线可演示；qwen 模式含三级容错；用户级端点路由（V9） |
| HTTP 客户端 | Spring WebClient | 调用大模型（30s 超时、API Key 日志脱敏） |
| 文档解析 | Apache POI 5.3 / PDFBox 3.0 | 简历 docx/pdf 文本抽取（md/txt 走 JDK 内置解码） |
| 前端 | Vue 3.5 + TypeScript + Vite 5 + Element Plus + Pinia + Vue Router + Axios + ECharts | F1 基座/认证/布局 + F2 面试主流程 + F3 报告与历史 + F4 语音/成长/推荐/个人中心 + F5 管理端 7 页 + F6 质量/部署/验收，全部交付 |
| 迁移 | Flyway | V1 建表 + V2~V6 种子 + V7 报告证据/时长 + V8 简历功能 + V9 用户级 AI 配置 |
| 构建 | Maven 3.9 | |

## 3. 仓库结构

```text
├── README.md                    # 本文件
├── 开发计划/                     # 课程设计文档（2026-10-27 起整合为 4 份）
│   ├── AI模拟面试与能力提升平台_开发方案.md       # 系统设计唯一来源：基线方案 + 简历方案 + 接口协议融为一体（51 REST / 12 表 / 验收标准，附章节对照表）
│   ├── AI模拟面试与能力提升平台_开发计划.md       # 执行计划三篇：后端 M1~M6 / 前端 F1~F6 / 简历后端 R1~R4（原文保留）
│   ├── AI模拟面试与能力提升平台_验收与质量记录.md  # M6 验收自检 v1/v2、AI 抽测、检查报告、修复计划与验证（时间线六篇）
│   └── AI模拟面试与能力提升平台_进度记录.md       # 唯一进度事实源（实时更新）
└── ai-interview/
    ├── docker-compose.dev.yml       # 开发期 MySQL 8 + Redis 7
    ├── docker-compose.yml           # 正式五服务编排（mysql/redis/backend/frontend/nginx）
    ├── docker-compose.offline.yml   # 离线（断网）演练叠加编排（M6-6.4）
    ├── .env(.example)               # 部署环境变量（AI_PROVIDER/AI_API_KEY 等）
    ├── nginx/nginx.conf             # 静态页 + /api 反代 + /ws 升级
    ├── frontend/                    # Vue3 工程（F1~F6 已完成）：Vite + TS + Element Plus + ECharts；README/验收/截图
    ├── 答辩演示脚本.md              # 方案 §19 15 步逐条映射 + 简历环节第 16 步（M6-6.6/R4-4）
    ├── qa/                          # 质量与演练脚本（AI 抽测 / 离线演练 / 简历功能冒烟 + 样例简历 + 结果留存）
    └── backend/                     # Spring Boot 后端（12 个业务包 + common）
        ├── README.md                # 新人 30 分钟启动文档（M6-6.6，R4 收口更新）
        ├── pom.xml
        ├── api.http                 # IDEA HTTP Client 调试脚本（108 组，与计划附录 C / 简历协议同步）
        ├── uploads/                 # 音频/简历落盘目录（gitignore，运行期生成）
        └── src/
            ├── main/java/com/example/interview/
            │   ├── auth/        # 认证、用户、JWT、Security 接入
            │   ├── position/    # 岗位与能力模型
            │   ├── question/    # 题库实体/组卷
            │   ├── interview/   # 会话、状态机、TurnEngine、追问规则、WS、定时器、音频存储、个性化集成
            │   ├── resume/      # 简历上传/四格式抽取/PII 脱敏解析/异步六维分析/技能匹配（R1~R3）
            │   ├── aiconfig/    # 用户级 AI 服务配置（V9）
            │   ├── ai/          # AiProvider 双实现（Mock/OpenAI 兼容）+ 用户路由、PromptBuilder、JSON 容错解析
            │   ├── knowledge/   # 知识片段实体、FULLTEXT 检索、关键词词典、管理服务
            │   ├── assessment/  # 评分计算、语音指标、报告生成（含简历对比条目）
            │   ├── growth/      # 掌握度更新、成长曲线、个性化推荐、历史
            │   ├── admin/       # 管理端接口（题库、知识库、岗位、资源、用户、看板）
            │   └── common/      # 统一响应、异常、配置、工具、日志
            └── main/resources/db/migration/  # Flyway V1~V9
```

## 4. 当前进度（后端 M1~M6 + 增量 R1~R4 全部完成；前端 6/6，F6 已完成）

> 实时进度以 [`开发计划/AI模拟面试与能力提升平台_进度记录.md`](开发计划/AI模拟面试与能力提升平台_进度记录.md) 为准。
> 系统设计（需求基线 + 简历分析与个性化面试 + 接口协议）见 [`开发计划/AI模拟面试与能力提升平台_开发方案.md`](开发计划/AI模拟面试与能力提升平台_开发方案.md)；
> 执行计划（后端 M1~M6 / 前端 F1~F6 / 简历后端 R1~R4 三篇）见 [`开发计划/AI模拟面试与能力提升平台_开发计划.md`](开发计划/AI模拟面试与能力提升平台_开发计划.md)。

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M1 | 基础工程 + 认证 + 岗位题库 | ✅ 2026-10-13 |
| M2 | 文字面试闭环（状态机/组卷/AI 编排/追问/WS/定时器） | ✅ 2026-10-13 |
| M3 | 评分 + 报告 + 历史 | ✅ 2026-10-14 |
| M4 | 语音回答 + 知识库 RAG + 真实大模型 Provider | ✅ 2026-10-14 |
| M5 | 个性化（掌握度/成长/推荐）+ 管理端补全 | ✅ 2026-10-15 |
| M6 | 全量测试 + Docker 部署 + 验收 | ✅ 2026-10-15 |
| F1 | 前端工程基座 + 认证 + 应用外壳（含液态玻璃） | ✅ 2026-10-17 |
| F2 | 前端面试主流程（首页/岗位/设置/文字房间/WS/倒计时/finish-abort） | ✅ 2026-10-17 |
| F3 | 前端报告与历史（总分环/雷达/逐题证据/10004 轮询/历史分页） | ✅ 2026-10-17 |
| F4 | 前端语音 + 成长 + 推荐 + 个人中心（录音/转写/音频回放/成长曲线/推荐/资料） | ✅ 2026-10-18 |
| F5 | 前端管理端 7 页（看板/岗位/权重/题库/知识库/资源/用户） | ✅ 2026-10-18 |
| F6 | 前端质量 + 部署修复 + 验收（覆盖率/E2E/契约/产物卷/gzip/文档） | ✅ 2026-10-18 |
| V9 | 用户级 AI 服务配置全栈（个人中心 AI 服务配置 + RoutingAiProvider 按用户路由） | ✅ 2026-10-26 |
| R1 | 简历上传与解析闭环（V8 两表、四格式抽取、上传/列表/详情/默认/删除、配额） | ✅ 2026-10-26 |
| R2 | 简历分析（PII 脱敏解析、analyzeResume 双实现、异步编排状态机、R-04/R-05） | ✅ 2026-10-26 |
| R3 | 个性化面试（resumeId 校验链、偏好组卷、题干改写降级、追问/报告注入、R-08~R-10） | ✅ 2026-10-26 |
| R4 | 测试与收口（全量 verify 287/287 + 覆盖率 90.36%、qwen 冒烟脚本、文档/演示收口） | ✅ 2026-10-26 |

**当前已交付（M1~M6 全部）：**

- REST 接口 51 个：认证 3、用户与 AI 配置 6（me 2 + ai-config 4）、岗位 3、简历 8（R-01~R-08）、面试 10（创建/start/文字回答/音频回答/音频回放/finish/abort/详情/消息/报告，创建支持可选 resumeId）、成长域 3（历史/成长曲线/个性化推荐）、管理端 18（题库 CRUD 4、知识片段新增/索引重建 2、岗位列表/详情/新增/修改/评分权重 5、学习资源 CRUD 4、用户列表/启停 2、统计看板 1）；WebSocket 1 个。
- 种子数据：2 个岗位（Java 后端 / Web 前端）能力模型、各 30 道题（含参考答案、得分点、失分点、追问模板）、知识片段 42 条（每岗 21 条覆盖全部技能）、学习资源 28 条（每岗 14 条，覆盖 ARTICLE/QUESTION/PRACTICE/PROJECT）、管理员与演示学生账号。
- 完整面试闭环：建会话 → start 出首题 → 文字/音频回答（异步受理 + 关键词提取 + 知识检索注入 Prompt）→ 规则引擎驱动追问/换题 → finish / 轮次用尽 / 超时收卷 → 六维评估报告（AUDIO 场含 voice）→ 历史查询，全程 WS 推送。
- 评估链路：逐题评分快照 + 难度加权聚合 + 岗位可调权重 + 应变分（追问回升幅度）；报告失败可经 finish 重试，重启后报告可确定性重建。
- 个性化成长（M5）：报告落库自动更新能力掌握度（首次直取、此后 旧×0.7 + 本次×0.3，同会话幂等）→ 按技能的成长曲线（records 升序 + current）→ 个性化推荐（短板识别四规则 + 优先级 (100-掌握度)×权重 Top3 + 学习资源/练习 + 错题重练 retryQuestions + 下一次面试配置建议；Redis 缓存报告事件预生成，TTL 10min）。
- 管理端补全（M5）：统计看板（用户数/面试场次/岗位题量/平均分/近 7 日趋势）、岗位与评分权重维护（组内和=1 校验）、学习资源 CRUD（技能对齐校验、下架逻辑删）、用户启停（禁用登录拦截、防自禁用）。
- AI 双模式：mock 全流程离线可演示；qwen 真实调用含三级容错（重试 → Mock 降级 → 会话锁定），缺 API Key 启动即显式失败。
- 测试：`mvn test` **287 个用例全绿**（58 个测试类，其中 30 个集成测试类：状态机、组卷（含简历偏好）、追问七规则、JSON 容错、评分公式、语音指标、关键词词典、真实 Provider 容错（含 Bearer 鉴权头/401 降级）、掌握度折算、短板四规则、推荐引擎、WS 心跳、简历四格式抽取/PII 脱敏/六维聚合/改写契约、文字/报告/音频/知识库/认证/岗位/管理/安全边界/历史成长/推荐/管理端扩展/简历上传分析流/个性化面试/用户级 AI 配置 + 验收修复 IT）。
- 质量与部署（M6 6.1~6.3 + 2026-10-17 验收修复 + R4-1 复核）：`mvn verify` 全绿 + JaCoCo 行覆盖率 **90.36%**（核心包全部 ≥60%，check 门禁固化进 pom）；AI 质量抽测 **40/40 条人工分差 ≤15（100%，达标线 80%）**，记录见 [`开发计划/AI模拟面试与能力提升平台_验收与质量记录.md`](开发计划/AI模拟面试与能力提升平台_验收与质量记录.md)；五服务 Docker 编排（mysql/redis/backend/frontend/nginx）一键启动，Nginx 80 端口 `/api` 反代 + `/ws` 升级全通。
  > 2026-10-17 验收修复（FIX-02）：`frontend` 改为初始化容器，启动时把产物强制覆盖进 `frontend-dist` 卷——80 端口页面内容含产品名（新卷/旧卷均验证），不再是默认页（P9 已修复）。
- 验收与文档（M6 6.4~6.6）：**Mock 断网离线演练 13/13 项通过**（核心服务 internal 网络零外网出口，容器内实测外网 IP/DNS 不通；断网下完整走通 文字+语音面试 → 报告 → 历史/成长/推荐 → 管理端 → WebSocket，一键复跑 `bash ai-interview/qa/m6_offline_drill.sh`）；验收自检见 [`开发计划/AI模拟面试与能力提升平台_验收与质量记录.md`](开发计划/AI模拟面试与能力提升平台_验收与质量记录.md)（2026-10-17 验收复核后：#15 已修复，#6/#14 降为"协议级通过、真实 key 待环境验证"，未取得真实 key 前不宣称 15/15）；新人 30 分钟启动文档（[`ai-interview/backend/README.md`](ai-interview/backend/README.md)）与 15 步答辩演示脚本（[`ai-interview/答辩演示脚本.md`](ai-interview/答辩演示脚本.md)）。

**前端 F1（2026-10-17）**：Vue3 工程 + 认证/守卫 + Student/Admin/Auth 三布局 + 403/404 + 液态玻璃视觉基座（`AppBackdrop`/`GlassPanel`/Element Plus 玻璃化/打印与无障碍降级）；`lint/type-check/34 单测/build` 全绿，验收记录见 [`frontend/docs/F1-验收记录.md`](ai-interview/frontend/docs/F1-验收记录.md)。

**前端 F2（2026-10-17）**：首页/岗位列表/岗位详情/面试设置/文字面试房间 5 页替换占位；`stores/interview` 唯一事实源 + `useInterviewRoom` 编排 + 原生 WS（指数退避、4401、断线轮询）+ 倒计时校准 + 文字作答（IME/Ctrl+Enter/乐观气泡/草稿/10005 刷新）+ finish/abort 二次确认与终态；`lint/type-check/91 单测/coverage/build` 全绿，Playwright E2E **6/6**（含首页接口降级、跨标签页结束与 WS 断线轮询），验收记录与 12 张截图见 [`frontend/docs/F2-验收记录.md`](ai-interview/frontend/docs/F2-验收记录.md)。

**前端 F3（2026-10-17）**：报告页与历史页替换占位；`stores/report` 报告缓存 + 10004 轮询（1s→2s→3s，60s 上限，隐藏暂停）+ `POST finish` 幂等重试 + 40401/ABORTED/未结束空态；总分环（SVG）、5/6 维雷达（ECharts 按需注册）、维度条、逐题证据折叠与受保护音频回放、亮点/不足/建议、推荐 Top2 预览、打印；历史服务端分页 10/20/50 + 当前页状态筛选 + `page/size/status` URL 同步；`lint/type-check` 0/0，单测 **133/133**（27 文件，F3 新增 42）、coverage **68.31%**、build 成功，Playwright E2E **10/10**（F2 6 + F3 4，含 10004 自动恢复、60s 重试 finish 幂等、历史分页/筛选），验收记录与 16 张截图见 [`frontend/docs/F3-验收记录.md`](ai-interview/frontend/docs/F3-验收记录.md)。

**前端 F4（2026-10-18）**：语音 + 成长 + 推荐 + 个人中心 4 域替换占位；`useAudioRecorder`（权限/能力检测、webm/opus、600s/10MB 上限、MediaStream 释放）+ `useSpeechRecognition`（final/interim、错误映射、重启上限、手工/离线降级）+ 房间语音面板（录音/试听/转写编辑/音频提交/受保护回放）+ `GrowthView`（time 轴多技能曲线、岗位切换、空态）+ `RecommendationView`（Top3/理由/资源/错题/下场建议/外链安全）+ `ProfileView`（资料回显保存/退出/不展示密码）；`lint/type-check` 0/0，单测 **176/176**（35 文件，F4 新增 43）、coverage **72.13%**、build 成功，Playwright E2E **15/15**（F2 6 + F3 4 + F4 5：录音→转写→音频提交→voice 报告→回放、ASR 断网手工转写、成长/岗位切换、推荐、个人中心），验收记录与 9 张截图见 [`frontend/docs/F4-验收记录.md`](ai-interview/frontend/docs/F4-验收记录.md)。

**前端 F5（2026-10-18）**：管理端 7 页（看板/岗位与技能权重/评分权重/题库/知识库/学习资源/用户）替换占位；看板四卡 + 近 7 日柱线组合（null 均分断点、双轴）；题库 CRUD + 筛选分页 URL 同步 + 40003 冲突恢复 + 逻辑删；岗位/技能权重/三组评分权重实时校验与回读；资源 CRUD/下架、用户搜索/启停/自禁用保护、知识库新增/重建（明确无列表接口）；管理端菜单/路由守卫/`/admin/evaluation` 别名与移动端提示；`lint/type-check/format:check` 0/0，单测 **214/214**（40 文件，F5 新增 38）、coverage **76.54%**、build 成功，Playwright E2E **20/20**（F2 6 + F3 4 + F4 5 + F5 5：权限 403、看板、题库 40003、岗位权重、资源/用户、知识库），验收记录与 10 张截图见 [`frontend/docs/F5-验收记录.md`](ai-interview/frontend/docs/F5-验收记录.md)。

**前端 F6（2026-10-18）**：质量、部署与验收收口。**F6-1** 新增 API 模块契约单测（39 函数 + FormData/Blob/cleanParams），单测 **231/231**（41 文件）、核心目录行覆盖 **90.09%** / 分支 **82.85%**，`test:coverage` 固化 70%/60% 阈值；**F6-2** Playwright E2E **21/21**（F1 认证 1 + F2 6 + F3 4 + F4 5 + F5 5，49.2s），新增 E2E-01 登录/错误密码/角色跳转/登出；**F6-3** `scripts/contract-check.mjs` 覆盖 **39/39 REST + 7/7 错误码**，报告 `frontend/artifacts/contract-check.json`；**F6-4** `frontend/Dockerfile` 改为 node:20 多阶段构建 + 产物写入 `frontend-dist` 卷，Nginx 增加 gzip/no-cache/长缓存：80 端口页面含产品名、JS/CSS 200 且 `Content-Encoding: gzip`、`/api` 登录与 `/ws` 101 全通，frontend `Exited (0)`、nginx Up，离线叠加栈可启动（核心服务外网不通）；**F6-5** 交付 `frontend/README.md`（30 分钟上手/八条铁律/部署/FAQ）、更新根 README 与答辩脚本（P10 权重 payload 修正为 `scoreWeights` 完整三组）、新增 `docs/screenshots/f6/` 15 张答辩索引图；**F6-6** `qa/cleanup_e2e_data.sh` 清理后 `GET /positions` 仅 2 个种子岗位、demo01 历史 7 场、启用岗位 2；`npm run check` 全绿（lint/type-check/coverage/build），`npm audit --omit=dev` 仅 1 个 ECharts moderate（无可用修复，已记录并避免未过滤 HTML 注入）。验收记录见 [`frontend/docs/F6-验收记录.md`](ai-interview/frontend/docs/F6-验收记录.md)。

**简历分析与个性化面试（增量 R1~R4，2026-10-26 后端全部交付）**：新增 `resume` 包（四格式上传解析/PII 脱敏/异步六维分析/技能匹配，REST 8 个）与 Flyway V8（`resume`/`resume_analysis` 两表 + `interview_session.resume_id`）；个性化面试四环节——组卷带内优先命中简历技能（PROJECT 保底、零命中回退）、题干批量改写结合项目经历（all-or-nothing 降级、questionId 与评分基准不变）、追问注入简历要点（Mock 项目深挖池）、报告"简历 vs 实测"对比条目；focus-skills 重点技能预览；配额 20 份/日 10 次；`mvn verify` **287/287 全绿**、行覆盖 **90.36%**；真实 Provider 冒烟 `bash ai-interview/qa/verify_resume_feature.sh`（本地 stub 校验 Bearer + JSON 契约 + 真实链路标记，18 项断言 + 43 次鉴权断言全过）；答辩演示脚本第 16 步（API 演示，mock 离线可完整跑通）。前端页面另行排期。

**后续工作**：后端基线 M1~M6、增量 R1~R4、前端 F1~F6 已全部交付；简历功能前端页面按协议文档另行排期。答辩前按 [`答辩演示脚本`](ai-interview/答辩演示脚本.md) 复跑一次正式栈与演示数据清理（`bash ai-interview/qa/cleanup_e2e_data.sh`）。真实大模型 qwen 需自备 key 现场验证（默认 mock 离线可演示；简历链路可用 `qa/verify_resume_feature.sh` 一键冒烟）。

## 5. 快速开始

前置：JDK 17、Maven 3.9、Docker（colima/Docker Desktop 均可）。JDK 检查（FIX-17：构建带 enforcer 门禁，JDK 25 会在构建最开始明确报错）：

```bash
echo $JAVA_HOME && "$JAVA_HOME/bin/java" -version   # 应为 17.x；否则：
export JAVA_HOME=~/tools/jdk-17.0.20.1+1/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH"
```
**完整上手文档（30 分钟路径：环境 → 起服务 → 跑一场面试 → 测试 → 部署 → FAQ）见 [`ai-interview/backend/README.md`](ai-interview/backend/README.md)**；答辩现场走 [`ai-interview/答辩演示脚本.md`](ai-interview/答辩演示脚本.md)。以下为最短路径：

```bash
# 1) 起开发依赖（MySQL 3306 + Redis 6380）
cd ai-interview
docker compose -f docker-compose.dev.yml up -d

# 2) 启动后端（dev profile，Flyway 自动建表+种子）
cd backend
mvn spring-boot:run

# 3) 健康检查
curl http://127.0.0.1:8080/actuator/health
```

种子账号：`admin / Admin@123456`（管理员）、`demo01 / 123456`（演示学生）。

前端（F1~F6 已交付，v1.1 液态玻璃；30 分钟上手见 [`ai-interview/frontend/README.md`](ai-interview/frontend/README.md)，验收记录见 [`F6-验收记录`](ai-interview/frontend/docs/F6-验收记录.md)）：

```bash
cd ai-interview/frontend
npm ci
npm run dev       # http://127.0.0.1:5173；/api 与 /ws 代理到 127.0.0.1:8080
# 若后端跑在 18080：echo 'VITE_PROXY_TARGET=http://127.0.0.1:18080' > .env.development.local
# 质量门禁：npm run check（lint + type-check + coverage + build）
# E2E（需后端可达；globalSetup 校验 health 并单次登录 demo01/admin；F1 1 + F2 6 + F3 4 + F4 5 + F5 5 = 21 条）
# E2E/契约并行登录会超过默认 5 次/分限流，测试环境请加：APP_SECURITY_LOGIN_RATE_LIMIT_PER_MINUTE=1000
npm run test:e2e
# 契约检查（39 REST + 7 错误码，输出 artifacts/contract-check.json）
npm run test:contract
```

核心流程演示（详见 `ai-interview/backend/api.http`，用例 1~32）：

```bash
BASE=http://127.0.0.1:8080
# 登录
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"demo01","password":"123456"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
AUTH="Authorization: Bearer $TOKEN"
# 建会话 → 开始（返回首题）
SID=$(curl -s -X POST $BASE/api/interviews -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"positionId":1,"mode":"TEXT","difficulty":3,"rounds":5,"durationMinutes":15}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
curl -s -X POST $BASE/api/interviews/$SID/start -H "$AUTH"
# 提交回答（受理后 AI 追问异步落库；知识检索命中片段会注入 Prompt，DEBUG 日志可观测）
curl -s -X POST $BASE/api/interviews/$SID/messages/text -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"roundNo":0,"content":"缓存击穿是热点key过期瞬间大量请求直达数据库，可用互斥锁或逻辑过期缓解 42。"}'
# 消息 / 详情
curl -s $BASE/api/interviews/$SID/messages -H "$AUTH"
curl -s $BASE/api/interviews/$SID -H "$AUTH"
# 结束 → 报告（六维评分 + 逐题证据；报告异步生成，稍候）→ 历史
curl -s -X POST $BASE/api/interviews/$SID/finish -H "$AUTH"
sleep 2 && curl -s $BASE/api/interviews/$SID/report -H "$AUTH"
curl -s "$BASE/api/users/me/history?page=1&size=10" -H "$AUTH"
# 成长曲线（报告落库时自动写能力掌握度：首次直取，此后 旧×0.7+本次×0.3）与个性化推荐
curl -s "$BASE/api/users/me/growth?positionId=1" -H "$AUTH"
curl -s "$BASE/api/users/me/recommendations?positionId=1" -H "$AUTH"
# 简历上传 → 异步六维分析（R1~R4；四格式白名单，PII 先脱敏再入库送模型）
# RID=$(curl -s -X POST $BASE/api/resumes -H "$AUTH" \
#   -F "file=@../qa/samples/resume_demo.md;type=text/markdown" -F "positionId=1" \
#   | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
# sleep 2 && curl -s $BASE/api/resumes/$RID/analysis -H "$AUTH"    # 六维/总分/分级建议/证据句
# curl -s "$BASE/api/resumes/$RID/focus-skills?positionId=1" -H "$AUTH"  # matched/gaps/hitRatio
# 个性化面试：建会话时带 "resumeId":$RID → start 首题结合简历项目改写（questionId 与评分基准不变）
# 音频回答（mode=AUDIO 会话；转写由前端 Web Speech 完成后随表单上送）
# printf '\x1aE\xdf\xa3fake' > /tmp/a.webm
# curl -s -X POST $BASE/api/interviews/$SID/messages/audio -H "$AUTH" \
#   -F "file=@/tmp/a.webm;type=audio/webm" -F "roundNo=0" \
#   -F "asrText=缓存击穿可用互斥锁或逻辑过期缓解……" -F "durationMs=120000"
# WebSocket（Node 22 原生客户端，可收 QUESTION/FOLLOW_UP/TRANSCRIPT/TIMER/REPORT_READY）
# node -e 'const ws=new WebSocket(`ws://127.0.0.1:8080/ws/interviews/'$SID'?token='$TOKEN'`);ws.onmessage=e=>console.log(e.data)'
```

运行测试：`cd ai-interview/backend && mvn test`（使用独立测试库 `ai_interview_test`，不影响开发库；当前 287 用例全绿）。全量质量门禁：`mvn verify`（enforcer 限定 JDK [17,25) + JaCoCo 行覆盖率检查，核心代码 ≥60% 门禁固化，当前 90.36%，报告见 `backend/target/site/jacoco/`）。

**切换真实大模型**（可选，默认 mock 离线可演示）：

```bash
AI_PROVIDER=qwen AI_API_URL=https://dashscope.aliyuncs.com/compatible-mode/v1 \
AI_API_KEY=<your-key> AI_MODEL=qwen-plus mvn spring-boot:run
```

> 已接入三级容错（M4）：缺少 API Key/URL 时启动即失败（配置错误显式暴露）；运行期调用失败自动重试 1 次后降级 Mock，连续 3 次失败锁定该会话为 mock，面试流程不中断。

**正式部署（M6-6.3，五服务一键启动）**：

```bash
cd ai-interview
cp .env.example .env          # 按需改 MYSQL_PASSWORD / JWT_SECRET / AI_*（默认 mock）
docker compose up -d --build  # mysql + redis + backend(多阶段构建) + frontend(node:20 构建 + 产物卷) + nginx
# 演示入口：http://127.0.0.1:80（真实 Vue 页面 + /api 反代 + /ws 升级；页面含产品名，P9 已修复）
docker compose down           # 收栈（卷保留；正式栈与 dev 栈共用 mysql-data 卷，勿同时运行两个 mysql）
```

> **离线（断网）演练（M6-6.4，答辩演示第 15 步）**：`bash ai-interview/qa/m6_offline_drill.sh` 一键完成 切栈 → 断网证明（核心服务零外网出口）→ 面试→报告→推荐全流程 → 恢复 dev 栈；结果留存 `ai-interview/qa/offline_drill_{env,result}.json`。
>
> AI 质量抽测（M6-6.2）：`python3 ai-interview/qa/m6_ai_quality_sampling.py http://127.0.0.1:8080`（需先起后端；结果与记录表见 `开发计划/AI模拟面试与能力提升平台_验收与质量记录.md`）。

## 6. 约定与说明

- 统一响应 `{code, message, data, traceId}`；业务失败 HTTP 200 + code 区分，协议级错误（401/403/400/429/500）用 HTTP 状态码，错误码表见开发计划附录 A。
- 开发期 Redis 宿主机端口为 **6380**（本机 6379 被占用），与 `docker-compose.dev.yml` 一致。
- 该仓库为课程设计交付物，全部规划/开发/验证记录见 `开发计划/` 目录，进度以进度记录文档为唯一事实源。

---

> 课程：JavaEE 课程设计 · 项目：AI 模拟面试与能力提升平台 · 后端交付（Spring Boot 3.3 单体）
