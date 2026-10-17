# AI 模拟面试与能力提升平台（ai-interview）

> **项目性质声明**：本项目是 **JavaEE 课程的课程设计（课程作业）**，用于课程学习与答辩演示，非商业产品。
> 开发周期：2026-10 起，按后端 M1~M6 + 前端 F1~F6 推进；文档与进度对课程要求全程留痕。

---

## 1. 项目简介

面向学生求职场景的 **AI 模拟面试与能力提升平台**（当前交付后端 M1~M6 + 前端 F2）。学生选择目标岗位后，系统自动组卷并驱动大模型进行**多轮模拟面试**（支持动态追问、追问难度/提示强度自适应；主问题在组卷时按目标难度一次锁定、回答期间不漂移），面试结束后产出**六维评估报告**，并基于能力短板给出**学习资源推荐与成长曲线**。

业务闭环：

```text
选择岗位 → 题库组卷 → AI 多轮面试 → 动态追问
        → 多维评估 → 生成报告 → 学习推荐 → 能力成长记录
```

核心能力（后端）：

- **面试状态机**：CREATED → RUNNING → WAITING_USER ⇄ PROCESSING_AI → FINISHED/ABORTED/TIMEOUT，数据库状态为唯一事实源，条件更新乐观锁防并发（双击重复提交天然被拒）。
- **组卷算法**：按题型比例（技术 50% / 项目 20% / 场景 20% / 行为 10%）+ 难度带 + 技能覆盖贪心抽样；主问题序列在 start 时一次性落库，杜绝"答题中换题"。
- **动态追问规则引擎**：方案定义的 7 条追问规则（缺失要点追问、模糊回答要求举例、失分点边界追问、覆盖充分升难度、连续低分降难度、连续高分深挖、同题追问 ≥3 强制换题），M3 起接入逐题评分输入。
- **多维评估与报告**：逐题评分（关键得分点覆盖率规则引擎）→ 难度加权六维聚合（technical/depth/logic/match/communication/voice）→ 总分（内容×0.5+表达×0.3+应变×0.1+匹配×0.1，岗位可调权重）→ 报告含分数/证据/亮点/不足/建议与逐题明细；finish 幂等兼报告失败重试入口，TIMEOUT 自动收卷。
- **语音面试链路**：音频回答 multipart 受理（webm/wav/mp3 白名单 + UUID 落盘），前端 Web Speech 转写后与文字同一分析链路；语速/停顿/语气词指标换算 voice 维度；受保护音频回放。
- **知识库轻量 RAG**：回答经词典法关键词提取（能力模型 + 知识库 keywords 聚合、最长匹配）→ MySQL ngram FULLTEXT 检索 Top5 知识片段 → 注入评分与出题 Prompt；管理端新增片段即被检索。
- **AI 双实现**：`MockAiProvider` 离线规则实现（默认，无网可完整演示）+ `OpenAiCompatibleProvider`（OpenAI 兼容 API，如通义千问 qwen-plus）；三级容错：JSON 容错解析 → 失败重试 1 次 → Mock 降级 → 连续 3 次失败会话锁定 mock。
- **实时推送**：WebSocket `/ws/interviews/{id}`，下行 QUESTION / FOLLOW_UP / TRANSCRIPT / TIMER / PROGRESS / REPORT_READY / ERROR 消息；query token 鉴权 + 心跳。
- **安全**：BCrypt 密码、JWT 无状态认证（Redis 黑名单）、登录限流、接口权限（STUDENT/ADMIN）、面试记录归属校验、统一错误码。

## 2. 技术栈

| 层 | 技术 | 说明 |
|---|---|---|
| JDK | Java 17 | `maven.compiler.release=17` |
| 框架 | Spring Boot 3.3.12 / Spring MVC | 单体后端 |
| 安全 | Spring Security 6 + JWT (jjwt 0.12) | 无状态认证 + Redis 黑名单/限流 |
| ORM | MyBatis-Plus 3.5.7 | boot3 starter；JSON 列 JacksonTypeHandler |
| 数据库 | MySQL 8.0（utf8mb4 + FULLTEXT ngram） | 9 张业务表 |
| 缓存 | Redis 7 | token 黑名单、限流 |
| 实时 | Spring WebSocket | 面试过程推送 |
| AI | OpenAI 兼容 Chat API / Mock | 默认 `ai.provider=mock` 离线可演示；qwen 模式含三级容错 |
| HTTP 客户端 | Spring WebClient | 调用大模型（30s 超时、API Key 日志脱敏） |
| 前端 | Vue 3.5 + TypeScript + Vite 5 + Element Plus + Pinia + Vue Router + Axios | F1 基座/认证/布局 + F2 面试主流程（首页/岗位/设置/文字房间/WS/倒计时）已交付；F3~F6 按计划推进 |
| 迁移 | Flyway | V1 建表 + V2~V6 种子数据 |
| 构建 | Maven 3.9 | |

## 3. 仓库结构

```text
├── README.md                    # 本文件
├── 开发计划/                     # 课程设计文档
│   ├── AI模拟面试与能力提升平台_系统实现方案.md   # 需求基线（1100+ 行）
│   ├── AI模拟面试与能力提升平台_后端开发计划.md   # 后端可执行开发计划（DDL/接口/里程碑）
│   ├── AI模拟面试与能力提升平台_前端开发计划.md   # 前端可执行开发计划（F1~F6、19 页、契约/测试/部署）
│   └── AI模拟面试与能力提升平台_进度记录.md       # 唯一进度事实源（实时更新）
└── ai-interview/
    ├── docker-compose.dev.yml       # 开发期 MySQL 8 + Redis 7
    ├── docker-compose.yml           # 正式五服务编排（mysql/redis/backend/frontend/nginx）
    ├── docker-compose.offline.yml   # 离线（断网）演练叠加编排（M6-6.4）
    ├── .env(.example)               # 部署环境变量（AI_PROVIDER/AI_API_KEY 等）
    ├── nginx/nginx.conf             # 静态页 + /api 反代 + /ws 升级
    ├── frontend/                    # Vue3 工程（F2 已完成）：Vite + TS + Element Plus；F3~F6 按计划推进
    ├── 答辩演示脚本.md              # 方案 §19 15 步逐条映射（M6-6.6）
    ├── qa/                          # 质量与演练脚本（AI 抽测 / 离线演练 + 结果留存）
    └── backend/                     # Spring Boot 后端（10 个业务包 + common）
        ├── README.md                # 新人 30 分钟启动文档（M6-6.6）
        ├── pom.xml
        ├── api.http                 # IDEA HTTP Client 调试脚本（与计划附录 C 同步）
        ├── uploads/                 # 音频落盘目录（gitignore，运行期生成）
        └── src/
            ├── main/java/com/example/interview/
            │   ├── auth/        # 认证、用户、JWT、Security 接入
            │   ├── position/    # 岗位与能力模型
            │   ├── question/    # 题库实体/组卷
            │   ├── interview/   # 会话、状态机、TurnEngine、追问规则、WS、定时器、音频存储
            │   ├── ai/          # AiProvider 双实现（Mock/OpenAI 兼容）、PromptBuilder、JSON 容错解析
            │   ├── knowledge/   # 知识片段实体、FULLTEXT 检索、关键词词典、管理服务
            │   ├── assessment/  # 评分计算、语音指标、报告生成
            │   ├── growth/      # 掌握度更新、成长曲线、个性化推荐、历史
            │   ├── admin/       # 管理端接口（题库、知识库、岗位、资源、用户、看板）
            │   └── common/      # 统一响应、异常、配置、工具、日志
            └── main/resources/db/migration/  # Flyway V1~V6
```

## 4. 当前进度（后端 6/6；前端 F2 已完成）

> 实时进度以 [`开发计划/AI模拟面试与能力提升平台_进度记录.md`](开发计划/AI模拟面试与能力提升平台_进度记录.md) 为准。
> 前端执行计划见 [`开发计划/AI模拟面试与能力提升平台_前端开发计划.md`](开发计划/AI模拟面试与能力提升平台_前端开发计划.md)。

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
| F3~F6 | Vue3 业务页面（报告/历史/语音/成长/推荐/个人中心/管理端/质量部署） | 🔧 F3 待开始 |

**当前已交付（M1~M6 全部）：**

- REST 接口 39 个：认证与用户 5、岗位 3、面试 10（创建/start/文字回答/音频回答/音频回放/finish/abort/详情/消息/报告）、成长域 3（历史/成长曲线/个性化推荐）、管理端 18（题库 CRUD 4、知识片段新增/索引重建 2、岗位列表/详情/新增/修改/评分权重 5、学习资源 CRUD 4、用户列表/启停 2、统计看板 1）；WebSocket 1 个。
- 种子数据：2 个岗位（Java 后端 / Web 前端）能力模型、各 30 道题（含参考答案、得分点、失分点、追问模板）、知识片段 42 条（每岗 21 条覆盖全部技能）、学习资源 28 条（每岗 14 条，覆盖 ARTICLE/QUESTION/PRACTICE/PROJECT）、管理员与演示学生账号。
- 完整面试闭环：建会话 → start 出首题 → 文字/音频回答（异步受理 + 关键词提取 + 知识检索注入 Prompt）→ 规则引擎驱动追问/换题 → finish / 轮次用尽 / 超时收卷 → 六维评估报告（AUDIO 场含 voice）→ 历史查询，全程 WS 推送。
- 评估链路：逐题评分快照 + 难度加权聚合 + 岗位可调权重 + 应变分（追问回升幅度）；报告失败可经 finish 重试，重启后报告可确定性重建。
- 个性化成长（M5）：报告落库自动更新能力掌握度（首次直取、此后 旧×0.7 + 本次×0.3，同会话幂等）→ 按技能的成长曲线（records 升序 + current）→ 个性化推荐（短板识别四规则 + 优先级 (100-掌握度)×权重 Top3 + 学习资源/练习 + 错题重练 retryQuestions + 下一次面试配置建议；Redis 缓存报告事件预生成，TTL 10min）。
- 管理端补全（M5）：统计看板（用户数/面试场次/岗位题量/平均分/近 7 日趋势）、岗位与评分权重维护（组内和=1 校验）、学习资源 CRUD（技能对齐校验、下架逻辑删）、用户启停（禁用登录拦截、防自禁用）。
- AI 双模式：mock 全流程离线可演示；qwen 真实调用含三级容错（重试 → Mock 降级 → 会话锁定），缺 API Key 启动即显式失败。
- 测试：`mvn test` **134 个用例全绿**（33 个测试类：状态机、组卷、追问七规则、JSON 容错、评分公式、语音指标、关键词词典、真实 Provider 容错（含 Bearer 鉴权头/401 降级）、掌握度折算、短板四规则、推荐引擎、WS 心跳，及文字/报告/音频/知识库/认证/岗位/管理/安全边界/历史成长/推荐/管理端扩展 + 验收修复 IT（WS 存活/WS Origin/终态竞态/Prompt 历史过滤/报告证据持久化/时长重启恢复/处理中恢复）共 **20 个集成测试类**）。
- 质量与部署（M6 6.1~6.3 + 2026-10-17 验收修复）：`mvn verify` 全绿 + JaCoCo 行覆盖率 **89.4%**（核心包全部 ≥60%，check 门禁固化进 pom）；AI 质量抽测 **40/40 条人工分差 ≤15（100%，达标线 80%）**，记录见 [`开发计划/AI质量抽测记录_M6.md`](开发计划/AI质量抽测记录_M6.md)；五服务 Docker 编排（mysql/redis/backend/frontend/nginx）一键启动，Nginx 80 端口 `/api` 反代 + `/ws` 升级全通。
  > 2026-10-17 验收修复（FIX-02）：`frontend` 改为初始化容器，启动时把产物强制覆盖进 `frontend-dist` 卷——80 端口页面内容含产品名（新卷/旧卷均验证），不再是默认页（P9 已修复）。
- 验收与文档（M6 6.4~6.6）：**Mock 断网离线演练 13/13 项通过**（核心服务 internal 网络零外网出口，容器内实测外网 IP/DNS 不通；断网下完整走通 文字+语音面试 → 报告 → 历史/成长/推荐 → 管理端 → WebSocket，一键复跑 `bash ai-interview/qa/m6_offline_drill.sh`）；验收自检见 [`开发计划/AI模拟面试与能力提升平台_验收自检_M6.md`](开发计划/AI模拟面试与能力提升平台_验收自检_M6.md)（2026-10-17 验收复核后：#15 已修复，#6/#14 降为"协议级通过、真实 key 待环境验证"，未取得真实 key 前不宣称 15/15）；新人 30 分钟启动文档（[`ai-interview/backend/README.md`](ai-interview/backend/README.md)）与 15 步答辩演示脚本（[`ai-interview/答辩演示脚本.md`](ai-interview/答辩演示脚本.md)）。

**前端 F1（2026-10-17）**：Vue3 工程 + 认证/守卫 + Student/Admin/Auth 三布局 + 403/404 + 液态玻璃视觉基座（`AppBackdrop`/`GlassPanel`/Element Plus 玻璃化/打印与无障碍降级）；`lint/type-check/34 单测/build` 全绿，验收记录见 [`frontend/docs/F1-验收记录.md`](ai-interview/frontend/docs/F1-验收记录.md)。

**前端 F2（2026-10-17）**：首页/岗位列表/岗位详情/面试设置/文字面试房间 5 页替换占位；`stores/interview` 唯一事实源 + `useInterviewRoom` 编排 + 原生 WS（指数退避、4401、断线轮询）+ 倒计时校准 + 文字作答（IME/Ctrl+Enter/乐观气泡/草稿/10005 刷新）+ finish/abort 二次确认与终态；`lint/type-check/91 单测/coverage/build` 全绿，Playwright E2E **6/6**（含首页接口降级、跨标签页结束与 WS 断线轮询），验收记录与 12 张截图见 [`frontend/docs/F2-验收记录.md`](ai-interview/frontend/docs/F2-验收记录.md)。

**后续工作**：按 [`前端开发计划`](开发计划/AI模拟面试与能力提升平台_前端开发计划.md) 继续 F3（报告/历史）→ F4（语音/成长/推荐/个人中心）→ F5（管理端）→ F6（质量/部署/验收）；F6-4 修复前端产物卷（P9）、F6-5 修正演示脚本（P10）、F6-6 清理开发库测试残留（P11）；其余后端开发计划任务已全部交付。

## 5. 快速开始

前置：JDK 17、Maven 3.9、Docker（colima/Docker Desktop 均可）。
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

前端（F2 已交付，v1.1 液态玻璃；详见 [`ai-interview/frontend/docs/F2-验收记录.md`](ai-interview/frontend/docs/F2-验收记录.md)）：

```bash
cd ai-interview/frontend
npm install
npm run dev       # http://127.0.0.1:5173；/api 与 /ws 代理到 127.0.0.1:8080
# 若后端跑在 18080：echo 'VITE_PROXY_TARGET=http://127.0.0.1:18080' > .env.development.local
# 质量门禁：npm run lint && npm run type-check && npm run test:coverage && npm run build
# F2 E2E（需后端可达；globalSetup 会校验 health 并单次登录 demo01）：
npm run test:e2e
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
# 音频回答（mode=AUDIO 会话；转写由前端 Web Speech 完成后随表单上送）
# printf '\x1aE\xdf\xa3fake' > /tmp/a.webm
# curl -s -X POST $BASE/api/interviews/$SID/messages/audio -H "$AUTH" \
#   -F "file=@/tmp/a.webm;type=audio/webm" -F "roundNo=0" \
#   -F "asrText=缓存击穿可用互斥锁或逻辑过期缓解……" -F "durationMs=120000"
# WebSocket（Node 22 原生客户端，可收 QUESTION/FOLLOW_UP/TRANSCRIPT/TIMER/REPORT_READY）
# node -e 'const ws=new WebSocket(`ws://127.0.0.1:8080/ws/interviews/'$SID'?token='$TOKEN'`);ws.onmessage=e=>console.log(e.data)'
```

运行测试：`cd ai-interview/backend && mvn test`（使用独立测试库 `ai_interview_test`，不影响开发库；当前 114 用例全绿）。全量质量门禁：`mvn verify`（含 JaCoCo 行覆盖率检查，核心代码 ≥60% 门禁固化，当前 89.0%，报告见 `backend/target/site/jacoco/`）。

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
docker compose up -d --build  # mysql + redis + backend(多阶段构建) + frontend(构建期) + nginx
# 演示入口：http://127.0.0.1:80（静态页 + /api 反代 + /ws 升级）
docker compose down           # 收栈（卷保留；正式栈与 dev 栈共用 mysql-data 卷，勿同时运行两个 mysql）
```

> **离线（断网）演练（M6-6.4，答辩演示第 15 步）**：`bash ai-interview/qa/m6_offline_drill.sh` 一键完成 切栈 → 断网证明（核心服务零外网出口）→ 面试→报告→推荐全流程 → 恢复 dev 栈；结果留存 `ai-interview/qa/offline_drill_{env,result}.json`。
>
> AI 质量抽测（M6-6.2）：`python3 ai-interview/qa/m6_ai_quality_sampling.py http://127.0.0.1:8080`（需先起后端；结果与记录表见 `开发计划/AI质量抽测记录_M6.md`）。

## 6. 约定与说明

- 统一响应 `{code, message, data, traceId}`；业务失败 HTTP 200 + code 区分，协议级错误（401/403/400/429/500）用 HTTP 状态码，错误码表见开发计划附录 A。
- 开发期 Redis 宿主机端口为 **6380**（本机 6379 被占用），与 `docker-compose.dev.yml` 一致。
- 该仓库为课程设计交付物，全部规划/开发/验证记录见 `开发计划/` 目录，进度以进度记录文档为唯一事实源。

---

> 课程：JavaEE 课程设计 · 项目：AI 模拟面试与能力提升平台 · 后端交付（Spring Boot 3.3 单体）
