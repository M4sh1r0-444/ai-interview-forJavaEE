# AI 模拟面试与能力提升平台 · 前端（Vue 3 + TypeScript）

> 课程设计前端工程，按《前端开发计划》F1~F6 交付：学生端 12 页 + 管理端 7 页，含文字/语音面试、报告、成长、推荐、管理后台与液态玻璃视觉体系。
> 后端 API/契约与启动见 [`../backend/README.md`](../backend/README.md)；进度以 [`../../开发计划/AI模拟面试与能力提升平台_进度记录.md`](../../开发计划/AI模拟面试与能力提升平台_进度记录.md) 为准。

---

## 1. 技术栈与范围

| 层        | 选型                                                                     |
| --------- | ------------------------------------------------------------------------ |
| 框架      | Vue 3.5 + TypeScript（strict）+ Vite 5                                   |
| UI        | Element Plus（按需组件）+ 自研液态玻璃令牌（`AppBackdrop`/`GlassPanel`） |
| 状态/路由 | Pinia + Vue Router（角色守卫、懒加载）                                   |
| 请求      | Axios（统一 `code` 解包、401/403 事件、BizError、Blob 回放）             |
| 图表      | ECharts 5（雷达/柱线/成长曲线，按需注册）                                |
| 实时      | 原生 WebSocket + REST 对账（断线退避重连 + 轮询兜底）                    |
| 语音      | MediaRecorder（webm/opus）+ Web Speech API（失败可手工转写）             |
| 测试      | Vitest + @vue/test-utils + Playwright（系统 Chrome）                     |

页面：登录/注册/首页/岗位列表/岗位详情/面试设置/面试房间/面试报告/历史/成长/推荐/个人中心；管理端看板/岗位/评分权重/题库/知识库/学习资源/用户；403/404。

---

## 2. 30 分钟快速开始

### 2.1 前置

- Node.js **20+**（本机验证 Node 20.20 / npm 10；若依赖提示 `EBADENGINE` 为上游包的 engines 声明，不影响构建）
- 后端已按 [`../backend/README.md`](../backend/README.md) 启动（默认 `http://127.0.0.1:8080`，本机常用 `18080`）
- MySQL/Redis 开发容器：在 `ai-interview/` 下 `docker compose -f docker-compose.dev.yml up -d`

### 2.2 启动前端

```bash
cd ai-interview/frontend
npm ci                                  # 与 package-lock.json 一致；也可 npm install
cp .env.example .env.development.local  # 本地专用，已被 .gitignore 忽略
# 后端在 18080 时取消下一行注释；后端在 8080 可保持空/删除该文件
echo 'VITE_PROXY_TARGET=http://127.0.0.1:18080' > .env.development.local

npm run dev                             # http://127.0.0.1:5173
```

打开 `http://127.0.0.1:5173`，演示账号：

| 角色   | 账号     | 密码           |
| ------ | -------- | -------------- |
| 学生   | `demo01` | `123456`       |
| 管理员 | `admin`  | `Admin@123456` |

> `/api` 与 `/ws` 由 Vite 代理到 `VITE_PROXY_TARGET`；生产由 Nginx 同源反代。不要用 `file://` 或直连 18080 的方式打开页面（CORS/WS Origin 会不匹配）。

### 2.3 后端启动（最短路径）

```bash
cd ai-interview/backend
export JAVA_HOME=~/tools/jdk-17.0.20.1+1/Contents/Home   # 需 JDK 17
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
curl http://127.0.0.1:18080/actuator/health             # {"status":"UP"}
```

### 2.4 常用脚本

| 命令                                                          | 作用                                       | 门禁                       |
| ------------------------------------------------------------- | ------------------------------------------ | -------------------------- |
| `npm run dev`                                                 | Vite 开发服务器（5173，strictPort）        | —                          |
| `npm run build`                                               | `vue-tsc --noEmit && vite build` → `dist/` | 0 error                    |
| `npm run preview`                                             | 预览构建产物（无 /api、/ws 代理）          | —                          |
| `npm run lint`                                                | ESLint 9（0 warning）                      | 0/0                        |
| `npm run type-check`                                          | `vue-tsc --noEmit`（strict）               | 0 error                    |
| `npm run format:check`                                        | Prettier 校验                              | 全部通过                   |
| `npm run test:unit`                                           | Vitest 单测/组件测试                       | 全绿                       |
| `npm run test:coverage`                                       | 覆盖率 + 阈值                              | 核心目录行 ≥70%、分支 ≥60% |
| `npm run test:e2e`                                            | Playwright 全量 E2E                        | ≥6 条，当前 21 条          |
| `npm run check`                                               | lint + type-check + coverage + build       | 全绿                       |
| `npm run verify`                                              | `check` + E2E                              | 全绿                       |
| `npm run test:contract`（=`node scripts/contract-check.mjs`） | 39 个 REST 接口契约检查                    | 39/39 + 7 个错误码         |

覆盖率阈值固化在 `vite.config.ts` 的 `test.coverage.thresholds`；统计范围是 `src/api`、`src/stores`、`src/utils`（核心目录），组件测试单独执行但不计入该阈值。

---

## 3. 测试与 E2E 注意事项

### 3.1 单元/组件测试

```bash
npm run test:unit
npm run test:coverage
```

- 单测覆盖 API 包装、stores、composables、utils 与关键组件（消息气泡、作答器、录音、转写、计时、推荐/报告图表、权重编辑器等）。
- 图表组件通过 mock ECharts 断言 option；不依赖真实 canvas 像素。

### 3.2 E2E（Playwright + 系统 Chrome）

```bash
# 后端与 Vite 5173 必须先可访问（未启动时 Playwright 会自动 npm run dev）
cd ai-interview/frontend
npm run test:e2e
```

- `e2e/global-setup.ts` 会校验 `/actuator/health`，并只登录一次 demo01/admin 写入 `e2e/.auth/` storageState。
- 用例内用 API 创建独立 `e2e_*` 学生与会话，避免污染 demo01 的演示历史；管理端数据使用 `E2E-` 前缀。
- **登录限流**：后端默认 5 次/分钟/IP。E2E 并行执行 + 契约检查会超过该阈值，测试环境请用宽松值启动后端（Spring 宽松绑定）：

  ```bash
  APP_SECURITY_LOGIN_RATE_LIMIT_PER_MINUTE=1000 \
  mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
  ```

  生产/答辩默认仍为 5 次/分钟，用于演示 42901 限流提示。

- 失败时保留 `test-results/` 与 trace（`trace: 'on-first-retry'`）。
- 本机 macOS 的 `getUserMedia` 可能 5s 超时（P19）；`audio-flow.spec.ts` 通过 `addInitScript` 注入假 MediaStream/MediaRecorder 与 Web Speech stub，真实麦克风需在答辩机手工复验。

### 3.3 契约检查

```bash
npm run test:contract
# 指定后端：CONTRACT_BASE_URL=http://127.0.0.1:8080 node scripts/contract-check.mjs
```

- 覆盖 39 个 REST 接口关键 shape + 7 个错误码（10002/40101/40301/40001/10005/40401/10004）。
- 结果写入 `artifacts/contract-check.json`，任一断言失败退出码 1。
- 会创建 `e2e_contract_*`/`E2E_CONTRACT_*` 临时数据；结束后请执行 `bash ../qa/cleanup_e2e_data.sh`。

### 3.4 测试数据清理

```bash
cd ai-interview
bash qa/cleanup_e2e_data.sh
```

清理 E2E/契约/历史冒烟残留（`e2e_*`/`acc_*`/`race_*`/`dis_*`、`E2E-*` 题目/资源/知识、`E2E_*`/`ACC_*` 岗位、demo01 在 M6 基线之后新增的会话及关联报告/消息/能力记录），并清理 Redis `rec:*` 与登录限流键；执行后 `GET /api/positions` 应只剩 2 个种子岗位、demo01 历史 7 场。

---

## 4. 目录结构

```text
frontend/
├── src/
│   ├── api/            # 39 REST 包装 + http 拦截器 + BizError
│   ├── components/     # common/interview/report/charts/admin/layout
│   ├── composables/    # useWebSocket/useCountdown/useAudioRecorder/useSpeechRecognition/usePagination...
│   ├── layouts/        # AuthLayout/StudentLayout/AdminLayout
│   ├── router/         # 路由表、角色守卫、懒加载
│   ├── stores/         # auth/interview/report/ui（Pinia）
│   ├── styles/         # 设计令牌、液态玻璃、Element 覆盖、打印
│   ├── types/          # 后端契约类型（单一来源）
│   ├── utils/          # 格式化/评分色带/轮次/权重/URL/存储/音频/图表
│   └── views/          # auth/student/interview/growth/user/admin/error
├── e2e/                # Playwright：auth/f2-text-interview/report/audio-flow/admin
├── scripts/            # contract-check.mjs / f4-f5 截图脚本
├── docs/               # F1~F6 验收记录与截图
├── Dockerfile          # node:20 多阶段构建 → 初始化容器写 frontend-dist 卷
└── .dockerignore
```

---

## 5. 架构要点

### 5.1 数据流

```text
页面 → store（auth/interview/report）→ api 模块 → http.ts → 后端
                ↑                                   │
                └──── WS 事件（通知）+ REST 对账 ────┘
```

- `http.ts` 统一解包 `{code,message,data,traceId}`：`code=0` 返回 `data`；`code!=0` 抛 `BizError`；HTTP 401/403 分别触发登录失效/无权限事件。
- 房间以 `stores/interview` 为唯一事实源：WS 只做通知，收到 QUESTION/FOLLOW_UP/PROGRESS/REPORT_READY 后必须重新拉 `GET detail` + `GET messages` 对账，消息按服务端 id 去重。
- 轮次以 `detail.currentRound` 为准（0-based）；FINISHED 后等于 `plannedRounds`。不要用"新增消息数"推轮次。
- 文字/音频回答是异步受理：HTTP 只表示 USER 消息落库；AI 产出走 WS + 消息列表。
- finish 异步；报告未就绪返回 10004，是正常态：报告页轮询 + `finish` 幂等重试。
- 终态（FINISHED/ABORTED/TIMEOUT）停计时、关 WS、禁输入。

### 5.2 关键模块

| 模块                     | 说明                                                                    |
| ------------------------ | ----------------------------------------------------------------------- |
| `stores/auth`            | 登录持久化、启动 restore、过期清理、`fetchMe` 401 清理、登出            |
| `stores/interview`       | 房间状态、消息合并、提交锁、WS 事件应用、恢复                           |
| `stores/report`          | 报告缓存、10004 轮询（1s→2s→3s，60s 上限，隐藏暂停）、重试              |
| `useInterviewRoom`       | 房间编排：加载/start/计时/WS/轮询/终态跳转                              |
| `useWebSocket`           | 指数退避（上限 8 次）、4401 不重连、卸载关闭                            |
| `useAudioRecorder`       | 能力/权限检测、webm/opus、600s/10MB 上限、MediaStream 释放              |
| `useSpeechRecognition`   | final/interim、错误映射、onend 重启上限、手工转写兜底                   |
| `GlassPanel/AppBackdrop` | 液态玻璃基座；`@supports`、`prefers-reduced-transparency`、打印白底降级 |

---

## 6. 八条联调铁律（必须遵守）

1. **只看 code**：业务失败 HTTP 可能是 200；不要以 HTTP 200 判定成功。
2. **WS 不是事实源**：事件到达后必须 REST 对账，按服务端 id 去重。
3. **不用新消息数推轮次**：用 `detail.currentRound` + 可见消息判断"是否有待答 AI 消息"。
4. **轮次只取服务端值**：提交时 `roundNo = detail.currentRound`。
5. **一次只允许一个在途提交**：按钮禁用 + 请求锁；10005 用刷新消化。
6. **10004 是正常态**：报告页轮询 + 重试，不弹全局错误。
7. **终态即停止**：停计时、关 WS、禁输入。
8. **不渲染未过滤 HTML**：AI 文本只用 `{{ }}` + `pre-wrap`，禁止 `v-html`；token 不写日志/URL（WS query 除外，属后端既定设计）。

---

## 7. 环境变量

| 变量                | 默认                        | 说明                       |
| ------------------- | --------------------------- | -------------------------- |
| `VITE_APP_TITLE`    | `AI 模拟面试与能力提升平台` | 页面标题                   |
| `VITE_API_BASE_URL` | 空                          | 空=同源 `/api`；生产保持空 |
| `VITE_WS_BASE_URL`  | 空                          | 空=同源 `/ws`；生产保持空  |
| `VITE_PROXY_TARGET` | `http://127.0.0.1:8080`     | 仅 Vite dev 代理目标       |
| `VITE_ENABLE_AUDIO` | `true`                      | 语音入口开关               |

生产构建使用 `.env.production`（同源，`npm run build` 注入）。不要运行时读取未声明变量。

---

## 8. 构建与 Docker 部署（F6-4 / P9）

```bash
cd ai-interview
cp .env.example .env
docker compose -f docker-compose.dev.yml stop   # 正式栈与 dev 栈共用 mysql-data 卷，禁止双 mysql
docker compose up -d --build
```

链路：

```text
frontend（node:20 构建 dist → alpine 初始化容器）→ 写入 frontend-dist 卷
nginx（挂载同一卷 :ro）→ 80 端口真实 Vue 页面
```

验收必须校验**页面内容**而非仅 200：

```bash
curl -s http://127.0.0.1/ | grep -q "AI 模拟面试与能力提升平台" && echo FRONTEND_OK
ASSET=$(curl -s http://127.0.0.1/ | grep -o 'src="/assets/[^"]*"' | head -1 | cut -d'"' -f2)
curl -s -o /dev/null -w '%{http_code}\n' "http://127.0.0.1${ASSET}"       # 200
curl -sI -H 'Accept-Encoding: gzip' "http://127.0.0.1${ASSET}" | grep -i 'content-encoding'  # gzip
docker compose ps                                                           # frontend Exited(0)，nginx Up
```

Nginx 已启用静态 gzip、`index.html` no-cache、`/assets/` 长缓存；`/api`、`/ws` 不缓存，音频上传上限 12MB。

> 离线/网络受限：`node:20-alpine` 与 npm registry 需提前拉取。compose 默认通过 `NPM_REGISTRY` 使用 `https://registry.npmmirror.com`（可在 `.env` 覆盖）；若现场仍无法构建，退路是宿主机 `npm run build` 后用 `nginx:1.27-alpine` 直接 COPY `dist`（计划 §10.3 退路）。本机 Docker Hub 曾超时，可用镜像源 `docker.m.daocloud.io/library/node:20-alpine` 后 `docker tag` 为 `node:20-alpine`。

---

## 9. 常见问题

| 现象                   | 处置                                                                                         |
| ---------------------- | -------------------------------------------------------------------------------------------- |
| 5173 启动失败          | 端口被占；`lsof -i :5173` 后释放，或不要修改 strictPort（代理固定）                          |
| 登录 42901             | 登录限流 5 次/分/IP；等 60s，或测试环境用 `APP_SECURITY_LOGIN_RATE_LIMIT_PER_MINUTE=1000`    |
| `/api` 404/502         | 后端未启动或 `VITE_PROXY_TARGET` 端口不对；检查 `/actuator/health`                           |
| WS 连不上              | Vite 代理 `/ws` 必须 `ws:true`；生产 Nginx 已配置 Upgrade；检查 Origin 白名单                |
| 麦克风不可用           | 需 `127.0.0.1`/HTTPS + Chrome；不支持时用文字或手工转写                                      |
| 图表空白               | 容器需有尺寸；`useEchart` 已处理 resize/dispose；E2E 断言 canvas 可见                        |
| 80 端口是 nginx 默认页 | P9 回归；确认 frontend 容器写入卷且 `Exited(0)`，nginx 依赖 `service_completed_successfully` |
| E2E 429                | 见 §3.2，测试环境放宽登录限流；或等待 60s 后重跑                                             |

---

## 10. 文档索引

- 前端计划：[`../../开发计划/AI模拟面试与能力提升平台_前端开发计划.md`](../../开发计划/AI模拟面试与能力提升平台_前端开发计划.md)
- 验收记录：[`docs/F1-验收记录.md`](docs/F1-验收记录.md) ~ [`docs/F6-验收记录.md`](docs/F6-验收记录.md)
- 截图：[`docs/screenshots/`](docs/screenshots/)（f1~f6，f6 为答辩 15 步索引）
- 后端契约：[`../backend/README.md`](../backend/README.md)、[`../backend/api.http`](../backend/api.http)
- 答辩脚本：[`../答辩演示脚本.md`](../答辩演示脚本.md)

## 11. 已知限制

- 后端没有知识片段列表/编辑/删除接口：知识库页只做新增 + 重建索引，页面已明确说明（D33）。
- `/admin/evaluation` 是评分权重页别名，无独立后端接口（D33）。
- Web Speech 依赖网络与 Chrome/Edge；离线仅文字模式或手工转写（R-F1）。
- 真实麦克风在无头/部分 macOS 环境不可用，E2E 使用注入假设备（P19）；答辩机需手工复验一次。
- 前端不感知 AI Provider；Mock/qwen 由后端 `AI_PROVIDER` 切换。
- `npm audit --omit=dev` 有 1 个 ECharts moderate（GHSA-fgmj-fm8m-jvvx，XSS，暂无上游修复）；前端不使用 `v-html`、不把用户输入拼进 ECharts formatter，作为已记录风险接受（F6 验收记录 §8）。
