# AI 模拟面试与能力提升平台（ai-interview）

> **项目性质声明**：本项目是 **JavaEE 课程的课程设计（课程作业）**，用于课程学习与答辩演示，非商业产品。
> 开发周期：2026-10 起，按 6 个里程碑（M1~M6）推进；文档与进度对课程要求全程留痕。

---

## 1. 项目简介

面向学生求职场景的 **AI 模拟面试与能力提升平台**（当前交付后端）。学生选择目标岗位后，系统自动组卷并驱动大模型进行**多轮模拟面试**（支持动态追问、难度自适应），面试结束后产出**六维评估报告**，并基于能力短板给出**学习资源推荐与成长曲线**。

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
| 迁移 | Flyway | V1 建表 + V2~V5 种子数据 |
| 构建 | Maven 3.9 | |

## 3. 仓库结构

```text
├── README.md                    # 本文件
├── 开发计划/                     # 课程设计文档
│   ├── AI模拟面试与能力提升平台_系统实现方案.md   # 需求基线（1100+ 行）
│   ├── AI模拟面试与能力提升平台_后端开发计划.md   # 可执行开发计划（DDL/接口/里程碑）
│   └── AI模拟面试与能力提升平台_进度记录.md       # 唯一进度事实源（实时更新）
└── ai-interview/
    ├── docker-compose.dev.yml   # 开发期 MySQL 8 + Redis 7
    └── backend/                 # Spring Boot 后端（10 个业务包 + common）
        ├── pom.xml
        ├── api.http             # IDEA HTTP Client 调试脚本（与计划附录 C 同步）
        ├── uploads/             # 音频落盘目录（gitignore，运行期生成）
        └── src/
            ├── main/java/com/example/interview/
            │   ├── auth/        # 认证、用户、JWT、Security 接入
            │   ├── position/    # 岗位与能力模型
            │   ├── question/    # 题库实体/组卷
            │   ├── interview/   # 会话、状态机、TurnEngine、追问规则、WS、定时器、音频存储
            │   ├── ai/          # AiProvider 双实现（Mock/OpenAI 兼容）、PromptBuilder、JSON 容错解析
            │   ├── knowledge/   # 知识片段实体、FULLTEXT 检索、关键词词典、管理服务
            │   ├── assessment/  # 评分计算、语音指标、报告生成
            │   ├── growth/      # 掌握度、推荐（M5）、历史（M3）
            │   ├── admin/       # 管理端接口（题库、知识库）
            │   └── common/      # 统一响应、异常、配置、工具、日志
            └── main/resources/db/migration/  # Flyway V1~V5
```

## 4. 当前进度（4/6 里程碑）

> 实时进度以 [`开发计划/AI模拟面试与能力提升平台_进度记录.md`](开发计划/AI模拟面试与能力提升平台_进度记录.md) 为准。

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M1 | 基础工程 + 认证 + 岗位题库 | ✅ 2026-10-13 |
| M2 | 文字面试闭环（状态机/组卷/AI 编排/追问/WS/定时器） | ✅ 2026-10-13 |
| M3 | 评分 + 报告 + 历史 | ✅ 2026-10-14 |
| M4 | 语音回答 + 知识库 RAG + 真实大模型 Provider | ✅ 2026-10-14 |
| M5 | 个性化（掌握度/成长/推荐）+ 管理端补全 | ⬜ 未开始 |
| M6 | 全量测试 + Docker 部署 + 验收 | ⬜ 未开始 |

**当前已交付（M1~M4）：**

- REST 接口 25 个：认证与用户 5、岗位 3、面试 10（创建/start/文字回答/音频回答/音频回放/finish/abort/详情/消息/报告）、历史 1、管理端 6（题库 CRUD 4 + 知识片段新增/索引重建）；WebSocket 1 个。
- 种子数据：2 个岗位（Java 后端 / Web 前端）能力模型、各 30 道题（含参考答案、得分点、失分点、追问模板）、知识片段 42 条（每岗 21 条覆盖全部技能）、管理员与演示学生账号。
- 完整面试闭环：建会话 → start 出首题 → 文字/音频回答（异步受理 + 关键词提取 + 知识检索注入 Prompt）→ 规则引擎驱动追问/换题 → finish / 轮次用尽 / 超时收卷 → 六维评估报告（AUDIO 场含 voice）→ 历史查询，全程 WS 推送。
- 评估链路：逐题评分快照 + 难度加权聚合 + 岗位可调权重 + 应变分（追问回升幅度）；报告失败可经 finish 重试，重启后报告可确定性重建。
- AI 双模式：mock 全流程离线可演示；qwen 真实调用含三级容错（重试 → Mock 降级 → 会话锁定），缺 API Key 启动即显式失败。
- 测试：`mvn test` **85 个用例全绿**（19 个测试类：状态机、组卷、追问七规则、JSON 容错、评分公式、语音指标、关键词词典、真实 Provider 容错，及文字/报告/音频/知识库/认证/岗位/管理/WS 等 9 个集成测试类）。

**尚未实现**：掌握度与成长曲线、短板推荐（M5）、管理端扩展接口与统计看板（M5）、Docker 化部署与验收（M6）。`growth`/`recommendations` 等接口随 M5 交付。

## 5. 快速开始

前置：JDK 17、Maven 3.9、Docker（colima/Docker Desktop 均可）。

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

核心流程演示（详见 `ai-interview/backend/api.http`，用例 1~25）：

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
# 音频回答（mode=AUDIO 会话；转写由前端 Web Speech 完成后随表单上送）
# printf '\x1aE\xdf\xa3fake' > /tmp/a.webm
# curl -s -X POST $BASE/api/interviews/$SID/messages/audio -H "$AUTH" \
#   -F "file=@/tmp/a.webm;type=audio/webm" -F "roundNo=0" \
#   -F "asrText=缓存击穿可用互斥锁或逻辑过期缓解……" -F "durationMs=120000"
# WebSocket（Node 22 原生客户端，可收 QUESTION/FOLLOW_UP/TRANSCRIPT/TIMER/REPORT_READY）
# node -e 'const ws=new WebSocket(`ws://127.0.0.1:8080/ws/interviews/'$SID'?token='$TOKEN'`);ws.onmessage=e=>console.log(e.data)'
```

运行测试：`cd ai-interview/backend && mvn test`（使用独立测试库 `ai_interview_test`，不影响开发库；当前 85 用例全绿）。

**切换真实大模型**（可选，默认 mock 离线可演示）：

```bash
AI_PROVIDER=qwen AI_API_URL=https://dashscope.aliyuncs.com/compatible-mode/v1 \
AI_API_KEY=<your-key> AI_MODEL=qwen-plus mvn spring-boot:run
```

> 已接入三级容错（M4）：缺少 API Key/URL 时启动即失败（配置错误显式暴露）；运行期调用失败自动重试 1 次后降级 Mock，连续 3 次失败锁定该会话为 mock，面试流程不中断。

## 6. 约定与说明

- 统一响应 `{code, message, data, traceId}`；业务失败 HTTP 200 + code 区分，协议级错误（401/403/400/429/500）用 HTTP 状态码，错误码表见开发计划附录 A。
- 开发期 Redis 宿主机端口为 **6380**（本机 6379 被占用），与 `docker-compose.dev.yml` 一致。
- 该仓库为课程设计交付物，全部规划/开发/验证记录见 `开发计划/` 目录，进度以进度记录文档为唯一事实源。

---

> 课程：JavaEE 课程设计 · 项目：AI 模拟面试与能力提升平台 · 后端交付（Spring Boot 3.3 单体）
