# ai-interview backend —— AI 模拟面试与能力提升平台（后端）

Spring Boot 3.3 单体后端：**多轮 AI 模拟面试（文字/语音）→ 六维评估报告 → 能力成长与个性化推荐**。
本 README 面向第一次接手的新同学，目标：**从零开始 30 分钟内把系统跑起来并完成一场面试**。

> 课程文档与全程开发/验证记录在仓库根 `开发计划/` 目录（系统实现方案 = 需求基线、后端开发计划 = 任务分解、进度记录 = 唯一进度事实源）。本文件只讲"怎么跑、怎么测、怎么部署、怎么排错"。

---

## 1. 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | `maven.compiler.release=17`；更高版本 JDK 编译目标仍是 17，但运行建议直接用 17（ByteBuddy/CGLIB 兼容） |
| Maven | 3.9+ | 国内网络建议配阿里云镜像（本仓库 `docker-maven-settings.xml` 有一份参考） |
| Docker | 任意可运行 compose 的环境 | 提供 MySQL 8 + Redis 7；Docker Hub 直连超时可换镜像加速源 |
| Node | ≥ 18（可选） | 仅手工验证 WebSocket 时用（原生 `WebSocket` 客户端）；不装不影响开发 |

> 本项目开发机约定（供参考，其他机器按自身环境调整）：JDK 17 装在 `~/tools/jdk-17.0.20.1+1`，运行前 `export JAVA_HOME=~/tools/jdk-17.0.20.1+1/Contents/Home`；本机 6379 被别的项目占用，开发期 Redis 固定映射 **6380**（`application-dev.yml` 与 `docker-compose.dev.yml` 已一致）。

## 2. 快速开始（约 5 分钟）

```bash
# 0) 进入后端工程（仓库根为 ai-interview/）
cd ai-interview

# 1) 起开发依赖：MySQL 8（3306）+ Redis 7（宿主机 6380）
docker compose -f docker-compose.dev.yml up -d
docker ps          # 等 aiinterview-mysql / aiinterview-redis 变成 (healthy)

# 2) 启动后端（dev profile：Flyway 自动建 9 张表并灌种子 V2~V6）
cd backend
mvn spring-boot:run

# 3) 健康检查（另开终端）
curl http://127.0.0.1:8080/actuator/health        # {"status":"UP"}

# 4) 登录验证（种子账号见下）
curl -s -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"demo01","password":"123456"}'
```

种子账号（V2 迁移自带）：

| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `Admin@123456` | 管理员（可访问 /api/admin/**） |
| `demo01` | `123456` | 学生（演示用） |

**8080 被占用时**：`mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080`（后续 curl 换端口即可）。

## 3. 10 分钟跑通一场完整面试

推荐直接用 IDEA 打开 `backend/api.http`（32 组用例，按里程碑编号，与开发计划附录 C 同步），或按下面的最小链路手工走：

```bash
BASE=http://127.0.0.1:8080
# 登录取 token
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"demo01","password":"123456"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
AUTH="Authorization: Bearer $TOKEN"

# 建会话（Java 岗，文字面试，5 轮）→ start 出首题
SID=$(curl -s -X POST $BASE/api/interviews -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"positionId":1,"mode":"TEXT","difficulty":3,"rounds":5,"durationMinutes":15}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
curl -s -X POST $BASE/api/interviews/$SID/start -H "$AUTH"

# 提交回答（异步受理：AI 追问稍后落库，轮询 messages 或连 WS 获取）
curl -s -X POST $BASE/api/interviews/$SID/messages/text -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"roundNo":0,"content":"缓存击穿指热点key过期瞬间大量请求直达数据库；对策有互斥锁、逻辑过期、热点永不过期加异步刷新。首先讲清三件套的区别：穿透是查不存在的key、击穿是热点过期、雪崩是大量key同时过期。其次……"}'
sleep 2 && curl -s $BASE/api/interviews/$SID/messages -H "$AUTH"   # 看 AI 追问/下一题

#（重复作答直到 5 轮用尽自动收卷，或直接）结束 → 取报告 → 看成长闭环
curl -s -X POST $BASE/api/interviews/$SID/finish -H "$AUTH"
sleep 3 && curl -s $BASE/api/interviews/$SID/report -H "$AUTH"      # 六维分+总分+逐题证据
curl -s "$BASE/api/users/me/history?page=1&size=10" -H "$AUTH"      # 历史
curl -s "$BASE/api/users/me/growth?positionId=1" -H "$AUTH"         # 成长曲线（报告落库自动折算掌握度）
curl -s "$BASE/api/users/me/recommendations?positionId=1" -H "$AUTH" # 短板识别 + 学习资源/错题重练

# WebSocket（可选，Node ≥18 一行收推送：QUESTION/FOLLOW_UP/TRANSCRIPT/TIMER/REPORT_READY…）
node -e 'const ws=new WebSocket(`ws://127.0.0.1:8080/ws/interviews/'$SID'?token='$TOKEN'`);ws.onmessage=e=>console.log(e.data)'
```

语音面试：把会话 `mode` 换成 `"AUDIO"`，回答改提交 `multipart/form-data` 到 `/messages/audio`
（字段：`file`（webm/wav/mp3，≤10MB）+ `roundNo` + `asrText`（前端 Web Speech 转写文本）+ `durationMs`）；
报告的 `dimensionScores` 会多出 `voice` 维度（语速/停顿/语气词换算）。

## 4. 运行测试与质量门禁

```bash
cd backend
mvn test      # 134 个用例（33 个测试类）：单测 + 集成测试（@SpringBootTest + MockMvc）
mvn verify    # 全量质量门禁：测试 + JaCoCo 行覆盖率 ≥60% 检查（核心业务代码口径，当前 89.4%）
```

- 集成测试使用**独立测试库** `ai_interview_test`（`createDatabaseIfNotExist`，同一 MySQL 容器、不污染 dev 库）与 6380 Redis；
  所以跑测试前 dev 容器需要在跑。
- 覆盖率报告：`target/site/jacoco/index.html`。
- 测试清单（分组）：状态机/组卷/追问七规则/解析容错/评分公式/语音指标/关键词词典/真实 Provider 容错（Bearer 鉴权头/401 降级）/掌握度/短板/推荐引擎 + 认证/岗位/文字面试闭环/报告/音频/知识库/安全边界/历史成长/推荐/管理端 + 验收修复 IT（WS 心跳存活、WS Origin、终态竞态、Prompt 历史过滤、报告证据持久化、时长重启恢复、处理中会话恢复）共 20 个集成测试类。

## 5. 配置项

全部走环境变量覆盖（`application.yml` 内有默认值，开发开箱即用）：

| 环境变量 | 默认 | 说明 |
|---|---|---|
| `DB_HOST` / `MYSQL_PASSWORD` | 127.0.0.1 / root123456 | 数据源（库名固定 `ai_interview`） |
| `REDIS_HOST` / `REDIS_PORT` | 127.0.0.1 / 6379 | dev profile 会用 6380 |
| `JWT_SECRET` | dev 专用默认值 | 生产必须换（≥32 字节） |
| `AI_PROVIDER` | `mock` | `mock`=离线规则引擎（无网可完整演示）；`qwen`=真实大模型 |
| `AI_API_URL` / `AI_API_KEY` / `AI_MODEL` | 空 / 空 / qwen-plus | qwen 模式必填 URL+KEY，否则**启动即失败**（显式暴露配置错误）；运行期失败自动重试 1 次→降级 mock→连续 3 次锁定该会话 |
| `UPLOAD_DIR` | `./uploads` | 音频落盘目录（prod 指向卷 `/app/uploads`） |
| `APP_WEBSOCKET_ALLOWED_ORIGINS` | dev：5173 两源；prod：含 `http://127.0.0.1`、`http://localhost` | WS 握手 Origin 白名单（逗号分隔，FIX-04；生产经 Nginx 80 入口必含同源两源） |
| `APP_CORS_ALLOWED_ORIGINS` | 同上 | HTTP CORS 白名单（仅作用于 `/api/**`） |

WS 心跳参数（FIX-03）：`app.websocket.heartbeat-interval-ms`（默认 30000）与 `app.websocket.pong-timeout-ms`（默认 60000）；
心跳超时关闭码 4408，与鉴权失败 4401 区分。

切换真实大模型示例：

```bash
AI_PROVIDER=qwen AI_API_URL=https://dashscope.aliyuncs.com/compatible-mode/v1 \
AI_API_KEY=<your-key> AI_MODEL=qwen-plus mvn spring-boot:run
```

## 6. 工程结构与模块职责

```text
src/main/java/com/example/interview/
├── auth/         # 注册/登录/登出、JWT 签发与黑名单、用户资料、Security 配置、登录限流
├── position/     # 岗位与能力模型（7 技能 × 权重，学生侧只读）
├── question/     # 题库实体与组卷（题型比例 + 难度带 + 技能贪心覆盖）
├── interview/    # 核心：会话/状态机（条件更新乐观锁）、TurnEngine 编排、追问规则引擎、
│                 #   WebSocket 推送、超时定时器与重启恢复、音频存储
├── ai/           # AiProvider 双实现（MockAiProvider 规则引擎 / OpenAiCompatibleProvider WebClient）、
│                 #   PromptBuilder、AiResponseParser（JSON 围栏容错 + 重试 + 降级）
├── knowledge/    # 知识片段、ngram FULLTEXT 检索、词典法关键词提取（管理端新增即时刷新词典）
├── assessment/   # 逐题评分快照、六维聚合（岗位可调权重）、语音指标、报告生成与落库
├── growth/       # 掌握度更新（0.7/0.3 折算 + 幂等）、历史、成长曲线、短板识别 + 推荐引擎
├── admin/        # 管理端：题库/知识库/岗位与权重/学习资源/用户/统计看板（ROLE_ADMIN + 审计日志）
└── common/       # 统一响应 {code,message,data,traceId}、异常与错误码、JWT 工具、切面、配置
src/main/resources/db/migration/   # Flyway V1 建表 + V2~V6 种子（账号/岗位/60 题/42 知识片段/28 学习资源）
api.http                           # IDEA HTTP Client 用例 32 组（与开发计划附录 C 同步）
```

## 7. 接口概览（39 REST + 1 WS）

| 分组 | 前缀 | 接口 |
|---|---|---|
| 认证 | `/api/auth` | 注册 / 登录 / 登出（POST ×3） |
| 用户 | `/api/users` | GET/PUT `me` |
| 岗位 | `/api/positions` | 列表 / 详情 / `/{id}/skills` |
| 面试 | `/api/interviews` | 建会话、start、文字回答、音频回答、音频回放、finish、abort、详情、消息、报告（10 个） |
| 成长 | `/api/users/me` | history / growth / recommendations |
| 管理端 | `/api/admin` | 题库 CRUD 4、知识片段新增+索引重建 2、岗位列表/详情/新增/修改/权重 5、学习资源 CRUD 4、用户列表/启停 2、看板 1（共 18 个） |
| WebSocket | `/ws/interviews/{id}?token=` | 下行 QUESTION / FOLLOW_UP / TRANSCRIPT / TIMER / PROGRESS / REPORT_READY / ERROR；30s 心跳 |

统一契约：业务失败 **HTTP 200 + code** 区分（0=成功），协议级错误用 HTTP 状态码（401/403/400/429/500）；错误码表见开发计划附录 A。

**前端联调要点（异步契约）**：
- 文字/音频回答与 finish 均为**异步受理**：接口立即返回 `{accepted:true,...}`，AI 追问、下一题、报告经 WS 推送 + `GET messages` / `GET report` 轮询获取；报告未就绪时 report 返回 code=10004，finish 可幂等重试。
- 会话主问题在 start 时一次性预写（`round_no` 0..N-1），前端按「可见 AI 消息中 USER 未答数」驱动界面，勿用"新消息数"判断轮次推进。
- 音频转写由前端 Web Speech 完成后随表单上送（后端不做 ASR）。

## 8. 正式部署（Docker 五服务）

```bash
cd ai-interview
cp .env.example .env                 # 按需改 MYSQL_PASSWORD / JWT_SECRET / AI_*（默认 mock）
docker compose up -d --build         # mysql + redis + backend(多阶段构建) + frontend(构建期) + nginx
# 入口：http://127.0.0.1（静态页 + /api 反代 + /ws 升级）
docker compose down                  # 收栈（卷保留）
```

- 正式栈与 dev 栈**共用 mysql-data 卷**：切换前先 `docker compose -f docker-compose.dev.yml stop`，用完 `docker compose down` 再恢复 dev 栈，两个 mysql 禁止同时跑。
- **离线（断网）演练**（答辩演示第 15 步）：

```bash
bash qa/m6_offline_drill.sh         # 一键：切栈 → 断网证明 → 面试→报告→推荐全流程 → 恢复 dev 栈
```

## 9. 常见问题（按实际踩坑整理）

| 现象 | 原因与处理 |
|---|---|
| 起后端报 6379 连接失败 | 本机 6379 被其他项目占用——本项目 dev 固定用 **6380**；确认 `docker-compose.dev.yml` 的 redis 在跑 |
| 登录返回 42901 | 登录限流（默认 5 次/分/IP）；跑完大量测试后立刻手工登录会触发，等 60s 窗口重置 |
| 8080 起不来 | 端口被占：换 `--server.port=18080`（不影响测试，测试用 RANDOM_PORT） |
| mysql/redis 镜像拉取超时 | Docker Hub 直连问题：经镜像加速源拉取后重打官方 tag |
| `docker compose up` 后 backend 反复重启 | 检查 `.env`：qwen 模式缺 `AI_API_URL/KEY` 会启动即失败（故意的 fail-fast）；演示用 mock |
| 集成测试失败提示库冲突 | 确认 dev 容器在跑（测试用同一 MySQL 实例的 `ai_interview_test` 库）；测试互踢残留可重跑一次 |
| mvn verify 覆盖率不过 | JaCoCo 门禁 ≥60%（行）当前 89%，若新增大量未测代码需补测试或检查排除配置 |

## 10. 相关文档

- 需求基线与最终验收标准：`开发计划/AI模拟面试与能力提升平台_系统实现方案.md`
- 可执行开发计划（DDL/接口/里程碑/风险）：`开发计划/AI模拟面试与能力提升平台_后端开发计划.md`
- 进度与决策/偏差记录（唯一事实源）：`开发计划/AI模拟面试与能力提升平台_进度记录.md`
- AI 质量抽测记录（M6-6.2，40/40 分差 ≤15）：`开发计划/AI质量抽测记录_M6.md`
- 答辩演示脚本（15 步）：`ai-interview/答辩演示脚本.md`
