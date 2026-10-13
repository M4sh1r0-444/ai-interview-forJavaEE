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
- **动态追问规则引擎**：方案定义的 7 条追问规则（缺失要点追问、模糊回答要求举例、失分点边界追问、覆盖充分升难度、连续低分降难度、连续高分深挖、同题追问 ≥3 强制换题）。
- **AI 双实现**：`MockAiProvider` 离线规则实现（默认，无网可完整演示）+ OpenAI 兼容 Provider（真实大模型，如通义千问 qwen-plus），JSON 容错解析与三级降级。
- **实时推送**：WebSocket `/ws/interviews/{id}`，下行 QUESTION / FOLLOW_UP / TIMER / PROGRESS / REPORT_READY / ERROR 消息；query token 鉴权 + 心跳。
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
| AI | OpenAI 兼容 Chat API / Mock | 默认 `ai.provider=mock` 离线可演示 |
| 迁移 | Flyway | V1 建表 + V2~V4 种子数据 |
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
        └── src/
            ├── main/java/com/example/interview/
            │   ├── auth/        # 认证、用户、JWT、Security 接入
            │   ├── position/    # 岗位与能力模型
            │   ├── question/    # 题库实体/组卷
            │   ├── interview/   # 会话、状态机、TurnEngine、追问规则、WS、定时器
            │   ├── ai/          # AiProvider 双实现、PromptBuilder、JSON 容错解析
            │   ├── knowledge/   # 知识片段 + FULLTEXT 检索（M4）
            │   ├── assessment/  # 评分、报告（M3）
            │   ├── growth/      # 掌握度、推荐（M5）
            │   ├── admin/       # 管理端接口
            │   └── common/      # 统一响应、异常、配置、工具、日志
            └── main/resources/db/migration/  # Flyway V1~V4
```

## 4. 当前进度（2/6 里程碑）

> 实时进度以 [`开发计划/AI模拟面试与能力提升平台_进度记录.md`](开发计划/AI模拟面试与能力提升平台_进度记录.md) 为准。

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M1 | 基础工程 + 认证 + 岗位题库 | ✅ 2026-10-13 |
| M2 | 文字面试闭环（状态机/组卷/AI 编排/追问/WS/定时器） | ✅ 2026-10-13 |
| M3 | 评分 + 报告 + 历史 | ⬜ 未开始 |
| M4 | 语音回答 + 知识库 RAG + 真实大模型 Provider | ⬜ 未开始 |
| M5 | 个性化（掌握度/成长/推荐）+ 管理端补全 | ⬜ 未开始 |
| M6 | 全量测试 + Docker 部署 + 验收 | ⬜ 未开始 |

**当前已交付（M1+M2）：**

- REST 接口 15 个：认证与用户 5、岗位 3、面试 6（创建/start/文字回答/abort/详情/消息）、管理端题库 CRUD 4；WebSocket 1 个。
- 种子数据：2 个岗位（Java 后端 / Web 前端）能力模型、各 30 道题（含参考答案、得分点、失分点、追问模板）、管理员与演示学生账号。
- 完整文字面试闭环：建会话 → start 出首题 → 提交回答（异步受理）→ 规则引擎驱动追问/换题 → 轮次用尽 FINISHED / 中止 ABORTED / 超时 TIMEOUT，全程 WS 推送。
- 测试：`mvn test` **38 个用例全绿**（单元测试 12 + 集成测试 26：状态机、组卷、追问七规则、JSON 容错、文字闭环 IT、WebSocket IT、M1 认证/岗位/管理 IT）。

**尚未实现**：评分与报告（M3）、语音回答与知识检索（M4）、成长与推荐（M5）、Docker 化部署（M6）。`finish`/`report`/`audio` 等接口随对应里程碑交付。

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

核心流程演示（详见 `ai-interview/backend/api.http`）：

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
# 提交回答（受理后 AI 追问异步落库）
curl -s -X POST $BASE/api/interviews/$SID/messages/text -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"roundNo":0,"content":"缓存击穿是热点key过期瞬间大量请求直达数据库，可用互斥锁或逻辑过期缓解 42。"}'
# 消息 / 详情 / 中止
curl -s $BASE/api/interviews/$SID/messages -H "$AUTH"
curl -s $BASE/api/interviews/$SID -H "$AUTH"
curl -s -X POST $BASE/api/interviews/$SID/abort -H "$AUTH"
# WebSocket（Node 22 原生客户端，可收 QUESTION/FOLLOW_UP/TIMER）
# node -e 'const ws=new WebSocket(`ws://127.0.0.1:8080/ws/interviews/'$SID'?token='$TOKEN'`);ws.onmessage=e=>console.log(e.data)'
```

运行测试：`cd ai-interview/backend && mvn test`（使用独立测试库 `ai_interview_test`，不影响开发库）。

**切换真实大模型**（可选，默认 mock 离线可演示）：

```bash
AI_PROVIDER=qwen AI_API_URL=https://dashscope.aliyuncs.com/compatible-mode/v1 \
AI_API_KEY=<your-key> AI_MODEL=qwen-plus mvn spring-boot:run
```

> 注：真实 Provider（含三级容错）排期在 M4 里程碑接入，当前配置 qwen 会显式告警并回退 Mock。

## 6. 约定与说明

- 统一响应 `{code, message, data, traceId}`；业务失败 HTTP 200 + code 区分，协议级错误（401/403/400/429/500）用 HTTP 状态码，错误码表见开发计划附录 A。
- 开发期 Redis 宿主机端口为 **6380**（本机 6379 被占用），与 `docker-compose.dev.yml` 一致。
- 该仓库为课程设计交付物，全部规划/开发/验证记录见 `开发计划/` 目录，进度以进度记录文档为唯一事实源。

---

> 课程：JavaEE 课程设计 · 项目：AI 模拟面试与能力提升平台 · 后端交付（Spring Boot 3.3 单体）
