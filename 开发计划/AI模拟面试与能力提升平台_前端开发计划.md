# AI模拟面试与能力提升平台 —— 前端开发计划

> **文档性质**：本文是 **JavaEE 课程设计《AI 模拟面试与能力提升平台》的前端开发执行计划**，用于把《系统实现方案》§3.1/§10/§11/§16.3/§19/§20 与已完成的后端交付（39 个 REST 接口 + 1 个 WebSocket）拆解为可执行、可验证、可勾选的前端任务。
>
> **本文是计划与验收基线**；正式实现自 F1 起，F1 已于 2026-10-17 交付，进度以《AI模拟面试与能力提升平台_进度记录.md》为唯一事实源。
>
> **版本**：v1.1（2026-10-17）
> **v1.1 变更**：新增「液态玻璃（Liquid Glass）」视觉体系——§6.7 玻璃设计令牌、§8.3.1 规范、F1-3 基座交付与验收项、R-F18 风险与降级；F1 已按 v1.0 计划完成，F2~F6 继续沿用本视觉基线。
> **契约基线**：后端 M6 收官提交 `4ddcca7`（39 REST + 1 WS，`mvn verify` 114 用例全绿，JaCoCo 89.0%）
> **上游文档**：
> - 需求基线：[`AI模拟面试与能力提升平台_系统实现方案.md`](AI模拟面试与能力提升平台_系统实现方案.md)
> - 后端执行计划：[`AI模拟面试与能力提升平台_后端开发计划.md`](AI模拟面试与能力提升平台_后端开发计划.md)
> - 后端上手与联调契约：[`../ai-interview/backend/README.md`](../ai-interview/backend/README.md)
> - 进度与决策记录：[`AI模拟面试与能力提升平台_进度记录.md`](AI模拟面试与能力提升平台_进度记录.md)
> - 答辩演示脚本：[`../ai-interview/答辩演示脚本.md`](../ai-interview/答辩演示脚本.md)
>
> **下游产物**：`ai-interview/frontend/`（Vue3 工程）、前端 README、Playwright E2E 报告、答辩 UI 演示路径、课程报告截图。
>
> **契约原则**：前端类型与行为 **以后端实际实现为准**，不以方案文档的早期示意为准；本计划 §2.5 已列出 12 处必须注意的差异，附录 D 记录待确认项。

---

## 目录

- [1. 项目背景、范围与总览](#1-项目背景范围与总览)
- [2. 契约基线与联调铁律](#2-契约基线与联调铁律)
- [3. 技术栈与工程环境](#3-技术栈与工程环境)
- [4. 信息架构、路由与权限](#4-信息架构路由与权限)
- [5. 页面与功能规格](#5-页面与功能规格)
- [6. 前端架构设计](#6-前端架构设计)
- [7. 关键流程与状态机设计](#7-关键流程与状态机设计)
- [8. UI/UX 与视觉规范](#8-uiux-与视觉规范)
- [9. 测试计划](#9-测试计划)
- [10. 工程化、构建与部署](#10-工程化构建与部署)
- [11. 里程碑与任务分解（F1~F6）](#11-里程碑与任务分解f1f6)
- [12. 验收标准与答辩路径](#12-验收标准与答辩路径)
- [13. 风险、问题与应对](#13-风险问题与应对)
- [14. 交付物清单与文档更新规则](#14-交付物清单与文档更新规则)
- [15. 附录](#15-附录)

---

## 1. 项目背景、范围与总览

### 1.1 项目与业务闭环

面向学生求职场景的 **AI 模拟面试与能力提升平台**。学生在 Web 端选择目标岗位后，系统自动组卷并驱动大模型完成多轮模拟面试（文字/语音、动态追问、难度自适应），结束后产出六维评估报告，并给出学习资源、错题重练与成长曲线。

```text
选择岗位 → 能力模型 → 面试设置 → 多轮面试（文字/语音 + 动态追问）
        → 六维评估报告（分数/证据/亮点/不足/建议）
        → 学习推荐 + 错题重练 + 能力成长曲线 → 下一场面试
```

后端 M1~M6 已全部交付；**前端是当前唯一未交付的主链路产物**：`ai-interview/frontend/` 目前只有一个占位 `index.html` 和一个构建容器 Dockerfile（M6-6.3 的占位实现），正式 Vue3 工程尚未创建。

### 1.2 前端在本项目中的价值（课程设计视角）

| 价值点 | 说明 |
|---|---|
| 完整业务闭环 | 让答辩从"curl 演示后端"升级为"浏览器完整走通一场面试"，符合方案 §19 演示流程 |
| 技术栈覆盖 | Vue 3 + TypeScript + Vite + Element Plus + Pinia + Axios + ECharts，满足课程对前端工程化的考核 |
| 实时交互能力 | WebSocket 推送、倒计时、AI 追问、异步受理结果对账，是本项目的技术亮点 |
| 多媒体能力 | MediaRecorder 录音 + Chrome Web Speech 转写 + 受保护音频回放，体现浏览器 API 运用 |
| 数据可视化 | 六维雷达图、七技能成长折线、近 7 日面试趋势柱线组合，支撑报告与看板 |
| 视觉体系 | 液态玻璃（背景光斑 + 玻璃面板 + Element Plus 玻璃化 + 打印/无障碍降级），纯 CSS 不增依赖 |
| 工程化与质量 | TypeScript 严格模式、Vitest 单测、Playwright E2E、构建产物体积门禁，形成可复查证据 |

### 1.3 交付边界

| 类别 | 范围 |
|---|---|
| **本期交付（In Scope）** | 学生端 12 个页面（登录/注册/首页/岗位列表/岗位详情/面试设置/面试房间/报告/历史/成长/推荐/个人中心）+ 管理端 7 个页面（看板/岗位/题库/知识库/学习资源/用户/评分权重）+ 应用外壳与 403/404 + 液态玻璃视觉体系 + API/状态/图表/音频/WS 基础设施 + 单元/组件/E2E 测试 + 前端 README + Docker 构建与 Nginx 静态托管修复 + 答辩 UI 演示路径 |
| **不在本期（Out of Scope）** | 后端业务与接口修改；真实语音识别（ASR 由浏览器 Web Speech 完成，后端只接收转写文本）；短信/邮件/第三方登录；支付/会员；移动端 App/小程序；多语言；深色模式；微前端；服务端渲染；修改后端 9 张表结构 |
| **依赖后端既有契约** | 39 个 REST + 1 个 WS，路径/字段/错误码严格按 §2.2；若确需新增接口（如知识库列表），单独立"后端变更任务"并先改契约，不在前端阶段私自假设 |
| **允许修改的工程文件** | `ai-interview/frontend/**`；为修复静态产物托管，允许调整 `ai-interview/frontend/Dockerfile`、`ai-interview/docker-compose.yml` 中 frontend/nginx 相关段落、`ai-interview/nginx/nginx.conf` 的 gzip/缓存等非业务配置 |

### 1.4 既有资产与开发前提

| 项 | 现状（2026-10-17 实测/文档核对） | 对前端的意义 |
|---|---|---|
| 后端服务 | 运行中，`http://127.0.0.1:18080/actuator/health` = 200（dev profile；8080 当前空闲，历史 P6 曾占用） | 前端联调代理目标需可配置，默认 8080、本机可用 18080 |
| 接口规模 | 39 REST + 1 WS；统一响应 `{code,message,data,traceId}` | 全部页面均可基于真实接口开发，无需 mock 后端（除单测外） |
| 测试账号 | `demo01 / 123456`（学生，已有多场历史/成长数据）、`admin / Admin@123456`（管理员） | 演示与手工验收直接可用 |
| 种子数据 | 2 个岗位、各 30 题、42 条知识片段、28 条学习资源；`demo01` 有历史 | 报告/成长/推荐页面有真实数据可展示 |
| 前端现状 | ✅ F1 Vue3 工程已交付（Vite+TS+Element Plus+液态玻璃基座，2026-10-17）；`frontend/Dockerfile` 仍为占位拷贝式 | F2 起在现有工程上迭代；F6-4 替换 Dockerfile 并修复 frontend-dist 产物卷 |
| 工具链 | Node **v22.23.3**、npm **10.9.9**、pnpm 12.11.2 可用；Docker/colima 可用 | 计划按 Node 20 LTS 兼容写法，本机 22 可直接开发 |
| 开发依赖 | MySQL 3306、Redis 6380 容器 healthy（dev 栈） | 后端可直接启动，前端只关心 8080/18080 |
| 端口 | 5173 空闲；8080 空闲；18080 被本后端占用；80 核对时由临时验收栈占用并返回 nginx 默认页（现已释放） | 前端 dev 固定 5173；不要假设 80 是自己的前端入口 |
| 实测数据卫生 | `GET /api/positions` 当前返回 **3 个**启用岗位，其中 `ACC_c4b462`（defaultRounds=100）是测试残留；管理端题库/资源/用户也含 `acc_*` 测试数据 | 演示前需清理/停用测试岗位；面试设置页必须把轮次钳制到 5~8 |

> **重要现状缺陷（P9）**：现有 `frontend` 构建容器只把 `index.html` 拷进 **镜像内** 的 `/usr/share/nginx/html`，但 `docker-compose.yml` 的 frontend 服务 **没有挂载 `frontend-dist` 卷**；nginx 挂载的空卷被 Docker 用 nginx 镜像默认页初始化。本次核对时的一套临时验收栈（容器名 `aiinterview-accept-*`）的 80 端口返回 `Welcome to nginx!`，frontend 容器 `Exited (0)`。也就是说 M6-6.3 验证只检查了 HTTP 200，没有检查页面内容。**正式 Vue3 前端接入时必须同时修复产物写入卷的问题（F6-4，方案见 §10.3）**，否则答辩现场仍会看到 nginx 默认页。

### 1.5 六条工程原则

1. **契约优先（Contract First）**：所有请求/响应先落到 `src/types/api.ts`，页面不直接拼接后端字段名；契约变更先改类型与契约测试。
2. **异步优先（Async First）**：回答与 finish 都是"受理 + 异步产出"，WS 只是通知，REST 才是事实源；任何页面都必须有轮询/刷新兜底。
3. **可降级（Graceful Degradation）**：Web Speech 不支持、麦克风被拒、WS 断开、报告 10004、无数据、限流 42901 都必须有明确 UI，而不是白屏或重复提交。
4. **离线可演示（Offline Capable）**：不依赖 CDN/外部字体/外部图标库；依赖全部打包；语音识别离线不可用时允许手工转写；断网演练优先文字模式。
5. **单一事实源（Single Source of Truth）**：服务端状态（会话状态、消息、报告）不在前端发明；前端只维护派生 UI 状态（加载/提交中/自动滚动/录音中）。
6. **质量门禁（Quality Gate）**：`type-check + lint + unit + build + E2E` 五道门禁全部通过才允许标记任务 ✅。

### 1.6 术语、状态标记与任务编号

| 术语/标记 | 含义 |
|---|---|
| F1~F6 | 前端里程碑，与后端 M1~M6 平行编号，不复用 M 编号 |
| ⬜ 未开始 / 🔧 进行中 / ✅ 已完成 / ⏸ 暂停 / ❌ 取消 | 与进度记录一致的状态标记 |
| DoD | Definition of Done，任务完成标准（可运行验证 + 证据） |
| 事实源 | 后端数据库/接口返回；前端不得用本地推断覆盖 |
| WS | WebSocket，地址 `/ws/interviews/{sessionId}?token=<jwt>` |
| ASR | 浏览器 Web Speech API 的语音转写（后端不做 ASR） |
| 受理契约 | `POST messages/text` 与 `messages/audio`、`finish` 立即返回 `accepted`，结果异步产出 |
| 轮次 | 主问题序号 `roundNo`，**0-based**；追问不增加 `roundNo` |

### 1.7 计划总览

| 里程碑 | 内容 | 主要页面/模块 | 预估人日 | 依赖 |
|---|---|---|---|---|
| **F1** | 工程基座 + 认证 + 应用外壳 | 脚手架、规范、API 层、类型、Auth 布局、登录、注册、路由守卫、403/404、设计令牌、液态玻璃视觉基座 | 3.5 | 无（可立即开始） |
| **F2** | 面试主流程（文字） | 首页、岗位列表/详情、面试设置、面试房间（文字）、WS 客户端、倒计时、finish/abort | 5.5 | F1 |
| **F3** | 报告与历史 | 报告页（轮询/重试/雷达/逐题证据）、历史列表 | 3.5 | F2 |
| **F4** | 语音 + 成长 + 推荐 + 个人中心 | 录音、Web Speech、音频提交/回放、成长曲线、个性化推荐、资料编辑 | 5.0 | F2、F3 |
| **F5** | 管理端 | 管理外壳、看板、题库、岗位与权重、知识库、学习资源、用户 | 5.5 | F1、F3（复用图表/表格） |
| **F6** | 质量、部署与验收 | 单测/组件/E2E、契约检查、Docker 产物卷修复、gzip、前端 README、答辩脚本、截图、验收自检 | 3.5 | F1~F5 |
| **合计** |  |  | **26.5 人日**（另留 20% 缓冲约 5 人日，单人约 6 周；双人可压缩到 3~4 周） |  |

> 估算口径：1 人日 = 6 小时有效编码/调试；不含等待后端环境、答辩排练。课程设计可按周投入调整，但 **F1→F2→F3 顺序不可颠倒**，F4/F5 可在 F2/F3 后并行。

---

## 2. 契约基线与联调铁律

### 2.1 统一响应、错误码与空值约定

所有接口返回：

```json
{ "code": 0, "message": "成功", "data": {}, "traceId": "ed88aeec984e4682" }
```

- **业务失败**：HTTP 200 + `code != 0`（如 10002/10003/10004/10005/40003/40401）；**协议级失败**：HTTP 400/401/403/429/500 + 对应 `code`（40001/40101/40301/42901/50001）。
- `Result` 与 `RecommendationVO` 使用 `@JsonInclude(NON_NULL)`：空字段可能 **被省略**；其他 VO 默认会输出 `null`。前端类型统一用可选字段 `?:`，并做空值兜底，绝不假设字段一定存在。
- `data` 为空的成功响应（logout/delete）可能省略 `data`；前端包装为 `null`。
- 时间字段为 **无时区** 的 `LocalDateTime` 字符串（如 `2026-10-17T16:46:57`），按本地时间展示；不做 UTC 转换。
- `BigDecimal` 分数序列化为 JSON number（如 64.46），类型用 `number`，展示时 `toFixed(2)`。

**错误码 → 前端动作映射：**

| code | HTTP | 含义 | 前端动作 |
|---|---|---|---|
| 0 | 200 | 成功 | 正常渲染 |
| 10001 | 200 | 用户名已存在 | 注册表单 username 字段就地报错 |
| 10002 | 200 | 用户名或密码错误 | 登录表单错误提示，不区分账号/密码 |
| 10003 | 200 | 账号已禁用 | 登录页 alert：联系管理员；不跳转 |
| 10004 | 200 | 报告尚未生成 | 报告页继续轮询；允许重新调用 finish 重试；不弹全局错误 |
| 10005 | 200 | 面试状态冲突 | 重新拉取 detail/messages；提示"状态已变化，已刷新" |
| 40001 | 400 | 参数校验失败 | 表单字段错误；保留用户输入 |
| 40002 | 400 | 参数超出范围 | 设置页字段错误；回到可修改状态 |
| 40003 | 200 | 乐观锁版本冲突 | 管理端刷新最新数据，提示合并/重试；保留表单草稿 |
| 40401 | 200 | 资源不存在/无权限 | 详情页显示 404 空态；面试房间回历史页 |
| 40101 | 401 | 未登录/Token 失效 | 清空登录态 → `/login?redirect=<当前路径>` |
| 40301 | 403 | 角色不足 | 跳 `/403`（不暴露具体资源信息） |
| 42901 | 429 | 请求过于频繁 | 登录页 60s 倒计时；其他页面提示稍后再试 |
| 50001 | 500 | 服务器错误 | 错误态 + traceId；提供"重试"按钮 |
| 网络错误/超时 | — | 断网/服务不可达 | 顶部离线条 + 重试；面试房间保留本地草稿 |

### 2.2 接口清单（39 REST + 1 WS）

> 路径与后端实现逐字一致；`★` 为方案 §10 未列出、但后端已实现的管理端扩展接口。
> "前端函数"是计划中的 `src/api/*.ts` 导出名，F1-4 按此落地。

#### 2.2.1 认证与用户（5）

| # | 接口 | 权限 | 前端函数 | 用途 |
|---|---|---|---|---|
| 1 | POST `/api/auth/register` | 匿名 | `register()` | 注册（返回 UserVO，不返回 token） |
| 2 | POST `/api/auth/login` | 匿名 | `login()` | 登录（返回 token/expiresAt/user） |
| 3 | POST `/api/auth/logout` | 登录 | `logout()` | 登出（jti 进 Redis 黑名单） |
| 4 | GET `/api/users/me` | 登录 | `getMe()` | 当前用户（启动校验/个人中心） |
| 5 | PUT `/api/users/me` | 登录 | `updateMe()` | 修改 realName/major |

#### 2.2.2 岗位（3）

| # | 接口 | 权限 | 前端函数 | 用途 |
|---|---|---|---|---|
| 6 | GET `/api/positions` | 登录 | `listPositions()` | 启用岗位列表（无 skillCount） |
| 7 | GET `/api/positions/{id}` | 登录 | `getPosition(id)` | 岗位详情（含 skillCount） |
| 8 | GET `/api/positions/{id}/skills` | 登录 | `getPositionSkills(id)` | 技能/权重/要求等级 |

#### 2.2.3 面试（10）

| # | 接口 | 权限 | 前端函数 | 用途/关键契约 |
|---|---|---|---|---|
| 9 | POST `/api/interviews` | 登录 | `createInterview()` | 建会话，返回 `InterviewDetailVO`（CREATED） |
| 10 | POST `/api/interviews/{id}/start` | 本人 | `startInterview()` | 返回首题 `MessageVO`；WS 同时推 QUESTION；重复 start → 10005 |
| 11 | POST `/api/interviews/{id}/messages/text` | 本人 | `submitTextAnswer()` | **异步受理** `{accepted,roundNo}`；AI 产出走 WS |
| 12 | POST `/api/interviews/{id}/messages/audio` | 本人 | `submitAudioAnswer()` | multipart：`file` + `roundNo` + `asrText` + `durationMs` + `pauseCount?`；异步受理 |
| 13 | GET `/api/interviews/{id}/messages/{messageId}/audio` | 本人/ADMIN | `fetchAudioBlob()` | 受保护音频回放（binary） |
| 14 | POST `/api/interviews/{id}/finish` | 本人 | `finishInterview()` | **异步受理** `{accepted,status,reportId}`；兼报告重试入口 |
| 15 | POST `/api/interviews/{id}/abort` | 本人 | `abortInterview()` | 中止，返回 `InterviewDetailVO`（ABORTED） |
| 16 | GET `/api/interviews/{id}` | 本人/ADMIN | `getInterview(id)` | 详情（含 `remainSeconds`） |
| 17 | GET `/api/interviews/{id}/messages?roundNo=` | 本人/ADMIN | `getMessages()` | 消息列表；**学生看不到未问到的主问题** |
| 18 | GET `/api/interviews/{id}/report` | 本人/ADMIN | `getReport()` | 报告；未生成 → 10004 |

> 编号 9~18 共 10 个；方案 §10.3 只列了 9 个，第 13 个受保护回放是 M4 D21 的增补。

#### 2.2.4 成长（3）

| # | 接口 | 权限 | 前端函数 | 用途 |
|---|---|---|---|---|
| 19 | GET `/api/users/me/history?page=&size=` | 登录 | `getHistory()` | 分页历史（Page；含进行中/中止/超时） |
| 20 | GET `/api/users/me/growth?positionId=` | 登录 | `getGrowth()` | 每技能成长曲线（records 升序 + current） |
| 21 | GET `/api/users/me/recommendations?positionId=` | 登录 | `getRecommendations()` | 短板 Top3 + 资源/练习/错题/下场建议 |

#### 2.2.5 管理端（18，全部 ROLE_ADMIN）

| # | 接口 | 前端函数 | 用途/关键契约 |
|---|---|---|---|
| 22 | GET `/api/admin/questions` | `adminListQuestions()` | `?positionId=&type=&difficulty=&page=&size=`；**无 status 过滤** |
| 23 | POST `/api/admin/questions` | `adminCreateQuestion()` | 核心字段必填；返回 QuestionAdminVO |
| 24 | PUT `/api/admin/questions/{id}` | `adminUpdateQuestion()` | 必须带 `version`；冲突 → 40003 |
| 25 | DELETE `/api/admin/questions/{id}` | `adminDeleteQuestion()` | 逻辑删（status=DISABLED） |
| 26 | POST `/api/admin/knowledge` | `adminCreateKnowledge()` | keywords/skillTags 为逗号分隔字符串 |
| 27 | POST `/api/admin/knowledge/rebuild` | `adminRebuildKnowledge()` | 重建 FULLTEXT 索引；返回 `{rebuilt:true}` |
| 28 | GET `/api/admin/dashboard` | `adminDashboard()` | 用户数/场次/题量/均分/近 7 日趋势 |
| 29★ | GET `/api/admin/positions` | `adminListPositions()` | Page；含停用岗位与完整能力模型 |
| 30★ | GET `/api/admin/positions/{id}` | `adminGetPosition()` | 含 skills + scoreWeights + enabled |
| 31★ | POST `/api/admin/positions` | `adminCreatePosition()` | code/name/skills 必填；技能权重和=1 |
| 32★ | PUT `/api/admin/positions/{id}` | `adminUpdatePosition()` | **部分更新**：仅非 null 字段生效；code 不可改 |
| 33★ | PUT `/api/admin/positions/{id}/weights` | `adminUpdateWeights()` | `scoreWeights` 三组；提供的组须完整、组内和=1 |
| 34★ | GET `/api/admin/resources` | `adminListResources()` | `?positionId=&skillCode=&resourceType=&page=&size=`；含已下架 |
| 35★ | POST `/api/admin/resources` | `adminCreateResource()` | skillCode 必须对齐岗位能力模型 |
| 36★ | PUT `/api/admin/resources/{id}` | `adminUpdateResource()` | **全量替换**语义，字段与新增一致 |
| 37★ | DELETE `/api/admin/resources/{id}` | `adminDeleteResource()` | 下架 enabled=0，非物理删 |
| 38★ | GET `/api/admin/users` | `adminListUsers()` | `?keyword=&page=&size=`；keyword 模糊 username/realName |
| 39★ | PUT `/api/admin/users/{id}/status` | `adminUpdateUserStatus()` | body `{status: 0 或 1}`；禁止禁用自己 |

#### 2.2.6 WebSocket（1）

| 项 | 约定 |
|---|---|
| 地址 | `ws(s)://<host>/ws/interviews/{sessionId}?token=<jwt>`（浏览器 WS 不能自定义 Header，token 走 query） |
| 鉴权 | 连接时验签 + Redis 黑名单 + 会话归属；失败立即 `close(4401)` |
| 方向 | 下行-only；客户端文本消息被忽略；心跳由服务端 30s PING / 浏览器自动 PONG |
| 消息 | `{type, payload, ts}`，type ∈ QUESTION / FOLLOW_UP / TRANSCRIPT / TIMER / PROGRESS / REPORT_READY / ERROR |
| 多连接 | 同一会话允许多连接（刷新页面）；推送遍历全部连接 |

### 2.3 五个必须理解的异步契约

1. **回答是异步的**：`POST messages/text|audio` 成功只表示"已受理并落库 USER 消息"，不代表 AI 已产出；AI 追问/下一题经 WS + `GET messages` 获取。HTTP 响应形如 `{accepted:true, roundNo:0}`。
2. **finish 是异步的**：返回 `{accepted:true,status:"FINISHED",reportId:null}`；报告由后台线程生成，经 WS `REPORT_READY` 通知；未就绪时 `GET report` 返回 **10004**。再次调用 finish 是官方允许的重试入口（幂等）。
3. **主问题在 start 时一次性预写**：`round_no=0..N-1` 的 AI 主问题消息在 start 事务内全部落库；`GET messages` 对学生隐藏 `roundNo > currentRound` 的主问题（防偷题）。因此**不能用"新消息数量"推断轮次**（README §7 明确提示）。
4. **轮次是 0-based 的**：`currentRound` 表示"当前主问题序号"；追问不递增；FINISHED 后 `currentRound = plannedRounds`，使全部消息可见。
5. **报告可能一直失败**：报告生成失败时 WS 推 ERROR（code 10004）；前端报告页必须提供"重新生成"按钮（调用 finish），不能只转圈。

### 2.4 WebSocket 事件与前端动作

| type | payload | 触发时机 | 前端动作 |
|---|---|---|---|
| `QUESTION` | `{roundNo, followupCount:0, content, difficulty, targetSkills[]}` | start 后 / 推进下一题 | 刷新 detail+messages（权威）；乐观显示；自动滚动；播报 |
| `FOLLOW_UP` | `{roundNo, followupCount:1..3, content, difficulty, targetSkills[]}` | AI 追问落库后 | 同上；显示"追问 N/3" |
| `TRANSCRIPT` | `{messageId, asrText}` | 音频回答受理后 | 用 messageId 对齐/替换本地转写气泡 |
| `TIMER` | `{remainSeconds}` | 每 30s；最后 60s 每 10s；到期推 0 | 校准本地倒计时；最后 60s 警示 |
| `PROGRESS` | `{status, currentRound, plannedRounds}` | 推进下一题完成、finish/abort/TIMEOUT | 终态：停计时/停输入/跳报告或历史；否则刷新 detail |
| `REPORT_READY` | `{reportId}` | 报告落库后 | 报告页立即拉取；房间显示"查看报告" |
| `ERROR` | `{code, message}` | AI 编排异常/报告失败 | 可恢复错误：提示 + 拉取 detail；不可恢复：引导结束/重试 |

> **实现原则**：WS 事件是"通知 + 可选乐观渲染"，**每次收到事件都触发一次 `GET detail` + `GET messages` 对账**；以服务端消息 ID 去重。不要仅把 WS payload 追加进列表，否则刷新/重连/丢事件时状态会漂移。

### 2.5 契约差异与现状核对（必须写进代码注释/README）

| # | 差异/现状 | 前端对策 |
|---|---|---|
| 1 | 方案 §10.5 只列 7 个管理接口；后端实际 18 个（新增岗位/权重/资源/用户/看板） | 按 18 个开发，管理端菜单覆盖全部 |
| 2 | 方案 §11 有 `/admin/evaluation`，后端没有独立评分接口，只有 `PUT /admin/positions/{id}/weights` | 将该页实现为"评分权重"页：选岗位 + 三组权重编辑，复用 `adminUpdateWeights` |
| 3 | 后端**没有知识片段列表接口**，只有新增与重建索引 | 知识库页按"新增片段 + 重建索引 + 成功回显"实现；页面明确说明暂无列表能力；如需列表另立后端任务 |
| 4 | 题库列表**没有 status 参数** | 不做"仅看启用"的跨页筛选；表格显示 DISABLED 标签；按 positionId/type/difficulty 筛选 |
| 5 | finish 在方案 §5.3#13 写同步 `{reportId}`，实际是异步受理（D16） | 统一按异步处理：轮询 report + REPORT_READY + finish 重试 |
| 6 | `GET messages` 对学生隐藏未来主问题（D12） | 房间只渲染返回的消息；不要请求未来 round 或假设题目已可见 |
| 7 | `pauseCount` 表单参数被后端接受但不落库（D19） | 前端可估算并上送，但不依赖它回显；语音指标以 durationMs + 转写为准 |
| 8 | `currentRound` 0-based；FINISHED 后等于 plannedRounds | 展示"第 currentRound+1 题 / 共 N 题"；完成态显示 N/N |
| 9 | 岗位 `defaultRounds` 管理端允许 3~8，但建面试 rounds 只允许 5~8 | 设置页默认值 **clamp 到 [5,8]**；提示"岗位默认 6 题"等 |
| 10 | 方案称"六维"；TEXT 场报告只有 5 个维度（无 voice） | 雷达图按实际维度渲染；TEXT 场显示"语音维度仅语音面试采集" |
| 11 | 方案 §3.1 浏览器语音要求 Chrome；Web Speech 依赖网络，断网时不可用 | 语音页做能力检测 + 手工转写兜底；断网演练优先文字模式 |
| 12 | `答辩演示脚本.md` §14 的权重请求体写成 `{"weights":{...}}`，与后端 `{"scoreWeights":{...}}` 不一致 | 前端按真实契约调用；F6 修正演示脚本文档（P10） |

**实测环境补充**：

- `GET /api/positions` 当前返回 3 个启用岗位（含 `ACC_c4b462`，`defaultRounds=100`）；`GET /api/admin/questions` 含 DISABLED 题与 `acc_*` 测试数据；`GET /api/admin/resources` 含已下架的冒烟数据。**F6 验收前必须清理/停用测试岗位**，否则答辩选择岗位页会出现异常项。
- `GET /api/users/me/history` 的 `Page` 字段实测为 `{records,total,size,current,pages}`，前端类型按此定义。
- `GET /api/admin/positions/{id}` 的 `scoreWeights` 三组均包含**全部字段**（未使用的为 null），编辑时必须原样保留/提交完整组。
- `GET /api/interviews/{id}/messages` 实测 AI 消息的 `inputType`、`audioPath` 等为 null；`followupIndex` 0=主问题，1+=追问。
- 报告 `QuestionEvidenceVO.evidence`、`coveredPoints` 等可能为空数组（Mock 低分答案常见），页面必须显示"无"的空态而不是报错。

### 2.6 八条联调铁律（写进前端 README）

1. **只看 code**：业务错误 HTTP 200，不要把 HTTP 200 当成功；统一在 axios 响应拦截器判断 `code === 0`。
2. **WS 不是事实源**：事件到达后必须 REST 对账；消息按服务端 ID 去重。
3. **不用"新消息数"推轮次**：用 `detail.currentRound` + 可见消息计算"是否有待答 AI 消息"。
4. **轮次只取服务端值**：提交时 `roundNo = detail.currentRound`，不要用消息计数。
5. **一次只允许一个在途提交**：按钮禁用 + 请求锁；双开/双击导致的 10005 通过刷新消化。
6. **10004 是正常态**：报告页轮询 + 重试，不要当失败弹窗。
7. **终态即停止**：FINISHED/ABORTED/TIMEOUT 后停计时、关 WS、禁输入；跳报告或历史。
8. **不渲染未过滤 HTML**：AI 文本用 `{{ }}` + `white-space: pre-wrap`，禁止 `v-html`；token 不进日志/URL 参数（WS query 除外，属后端既定设计）。

---

## 3. 技术栈与工程环境

### 3.1 固定技术栈（方案 §3.1 原样继承，不引入替代框架）

| 类别 | 选型 | 版本基线 | 用途与说明 |
|---|---|---|---|
| 语言 | TypeScript | 5.x（strict） | 全量类型；禁止 `any` 出现在业务代码（边界处用 `unknown` + 收窄） |
| 框架 | Vue 3 | 3.4.x | Composition API + `<script setup lang="ts">` |
| 构建 | Vite | 5.x | 开发服务器 + 生产构建；`@` 别名指向 `src` |
| UI | Element Plus | 2.x | 表单、表格、弹窗、布局、消息；中文 locale |
| 状态 | Pinia | 2.x | auth/position/interview/report/growth 等 store |
| 路由 | Vue Router | 4.x | 路由表、懒加载、守卫、meta |
| HTTP | Axios | 1.x | 统一实例、拦截器、blob/multipart |
| 图表 | ECharts | 5.x | 雷达图、折线图、柱线组合；按需注册 |
| 实时 | 原生 WebSocket | 浏览器 | 面试推送；不用 Socket.IO（后端是原生 WS） |
| 录音 | MediaRecorder API | 浏览器 | 录制 webm/opus |
| 转写 | Chrome Web Speech API | 浏览器 | `webkitSpeechRecognition`，zh-CN |
| 时间 | dayjs | 1.x（Element Plus 已依赖） | 日期格式化/倒计时计算；不额外引入 moment |
| 单测 | Vitest + @vue/test-utils + jsdom | 最新兼容版 | store/composable/组件测试 |
| E2E | Playwright | 最新兼容版 | 浏览器全链路；Chromium 为主 |
| 规范 | ESLint + Prettier + vue-tsc | 最新兼容版 | 提交前门禁 |

**版本策略**：`package.json` 使用 `^` 范围 + **提交 `package-lock.json`** 锁定；创建时以 Node 20 LTS 兼容为准（本机 Node 22 可直接跑）。不引入 Tailwind、Ant Design Vue、Nuxt、Socket.IO、Vuex、jQuery 等替代栈，避免偏离方案 §3.1。

### 3.2 浏览器与设备矩阵

| 浏览器 | 文字面试 | 语音面试 | 说明 |
|---|---|---|---|
| Chrome ≥ 110 / Edge ≥ 110（桌面） | ✅ | ✅（允许麦克风 + 联网） | **答辩推荐环境**；Web Speech 与 MediaRecorder 均支持 |
| Chrome（`http://localhost`/`127.0.0.1`） | ✅ | ✅ | 视为安全上下文；局域网 IP/HTTP 下语音 API 可能被禁用 |
| Firefox / Safari 桌面 | ✅ | ⚠ 录制/转写能力不完整 | 设置页检测后提示"请使用 Chrome/Edge 完成语音面试"；可回退文字模式 |
| 移动端浏览器 | ✅ 基础浏览 | ❌ 不承诺 | 学生端核心页做响应式；管理端不承诺移动端 |

- 目标分辨率：**1440×900**（答辩）、1366×768、1920×1080；最小支持宽度 1024（管理端表格可横向滚动）。
- 触摸/键盘：所有表单可键盘操作；面试房间提供 `Ctrl/Cmd + Enter` 提交、`Esc` 关闭弹窗。

### 3.3 本地开发环境

```bash
# 1) 后端依赖（若未起）
cd ai-interview
docker compose -f docker-compose.dev.yml up -d      # MySQL 3306 + Redis 6380

# 2) 后端（二选一；本机当前 18080 已有实例）
export JAVA_HOME=~/tools/jdk-17.0.20.1+1/Contents/Home
cd backend && mvn spring-boot:run                   # 8080
# 或：mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080

# 3) 前端（F1 创建后）
cd ai-interview/frontend
npm install
npm run dev                                         # http://127.0.0.1:5173
```

- **dev 代理**：Vite 将 `/api` 与 `/ws` 代理到 `VITE_PROXY_TARGET`（默认 `http://127.0.0.1:8080`，本机 18080 用 `.env.development.local` 覆盖）。
- **端口固定**：`server.port=5173` + `strictPort: true`。后端 CORS 只放行 `http://localhost:5173` 与 `http://127.0.0.1:5173`；用代理后同源，不依赖 CORS。
- **不要同时起两套前端**：5173 被占时直接报错退出，避免代理指向错误进程。

`.env.development`（提交仓库）：

```bash
VITE_APP_TITLE=AI 模拟面试与能力提升平台
VITE_API_BASE_URL=                 # 空 = 走 Vite 同源代理
VITE_WS_BASE_URL=                  # 空 = 由 location 推导 + /ws
VITE_PROXY_TARGET=http://127.0.0.1:8080
VITE_ENABLE_AUDIO=true             # 能力检测仍会二次校验
```

`.env.development.local`（本机专用，gitignore，可选）：

```bash
VITE_PROXY_TARGET=http://127.0.0.1:18080
```

`.env.production`：

```bash
VITE_APP_TITLE=AI 模拟面试与能力提升平台
VITE_API_BASE_URL=                 # Nginx 同源反代 /api
VITE_WS_BASE_URL=                  # Nginx 同源 /ws
VITE_ENABLE_AUDIO=true
```

### 3.4 工程初始化（F1-1 执行）

```bash
cd ai-interview
npm create vite@5 frontend -- --template vue-ts
cd frontend
npm install element-plus @element-plus/icons-vue pinia vue-router axios echarts dayjs
npm install -D vitest @vitest/coverage-v8 jsdom @vue/test-utils @playwright/test \
  eslint @eslint/js eslint-plugin-vue typescript-eslint prettier \
  vue-tsc unplugin-vue-components sass
npx playwright install chromium      # E2E 浏览器（联网时预装）
```

> Prettier 原生支持 `.vue` 解析，无需额外插件；Vue API 使用显式 `import`，不引入 `unplugin-auto-import` 的隐式全局，便于课程讲解与阅读。

> 若目录已有 `index.html`/`Dockerfile`，先备份再创建；保留原 `Dockerfile` 到 F6 统一替换，避免 F1 就破坏现有占位部署。

### 3.5 目录结构（目标态）

```text
ai-interview/frontend/
├── index.html
├── package.json
├── package-lock.json
├── vite.config.ts
├── tsconfig.json
├── tsconfig.node.json
├── eslint.config.js
├── .prettierrc.json
├── .env.development
├── .env.production
├── .env.example
├── Dockerfile                      # F6 替换为 node:20 多阶段 + 产物写卷
├── README.md                       # F6 交付：30 分钟上手
├── docs/
│   ├── F1-验收记录.md              # F1 验收/截图/复现（2026-10-17）
│   └── screenshots/f1/             # F1 验收截图（v1.1 液态玻璃版）
├── e2e/
│   ├── fixtures/
│   ├── helpers/
│   ├── student-flow.spec.ts
│   ├── audio-flow.spec.ts
│   ├── report.spec.ts
│   ├── admin.spec.ts
│   └── auth.spec.ts
├── public/
│   └── favicon.svg
└── src/
    ├── main.ts
    ├── App.vue
    ├── env.d.ts
    ├── api/
    │   ├── http.ts                 # axios 实例 + 拦截器 + BizError
    │   ├── auth.ts
    │   ├── position.ts
    │   ├── interview.ts
    │   ├── growth.ts
    │   ├── admin.ts
    │   └── index.ts
    ├── types/
    │   ├── api.ts                  # ApiResult/PageResult/错误码
    │   ├── auth.ts
    │   ├── position.ts
    │   ├── interview.ts
    │   ├── report.ts
    │   ├── growth.ts
    │   ├── admin.ts
    │   └── ws.ts
    ├── router/
    │   ├── index.ts
    │   └── guards.ts
    ├── stores/
    │   ├── auth.ts
    │   ├── position.ts
    │   ├── interview.ts
    │   ├── report.ts
    │   ├── growth.ts
    │   └── ui.ts
    ├── composables/
    │   ├── useAsync.ts
    │   ├── usePagination.ts
    │   ├── useCountdown.ts
    │   ├── useWebSocket.ts
    │   ├── useInterviewRoom.ts
    │   ├── useAudioRecorder.ts
    │   ├── useSpeechRecognition.ts
    │   ├── useEchart.ts
    │   ├── usePermission.ts
    │   └── useUnsavedGuard.ts
    ├── layouts/
    │   ├── AuthLayout.vue
    │   ├── StudentLayout.vue
    │   └── AdminLayout.vue
    ├── views/
    │   ├── auth/LoginView.vue, RegisterView.vue
    │   ├── student/HomeView.vue, PositionListView.vue, PositionDetailView.vue
    │   ├── interview/SetupView.vue, RoomView.vue, ReportView.vue
    │   ├── growth/HistoryView.vue, GrowthView.vue, RecommendationView.vue
    │   ├── user/ProfileView.vue
    │   ├── admin/DashboardView.vue, PositionAdminView.vue, WeightsView.vue
    │   ├── admin/QuestionAdminView.vue, KnowledgeAdminView.vue
    │   ├── admin/ResourceAdminView.vue, UserAdminView.vue
    │   └── error/NotFoundView.vue, ForbiddenView.vue
    ├── components/
    │   ├── common/AppBackdrop.vue, GlassPanel.vue     # 液态玻璃背景层 / 通用玻璃容器
    │   ├── common/AppPageHeader.vue, EmptyState.vue, ErrorState.vue, LoadingSkeleton.vue
    │   ├── common/StatusTag.vue, SkillTag.vue, ScoreBadge.vue, TraceIdText.vue
    │   ├── charts/RadarScoreChart.vue, GrowthLineChart.vue, TrendBarLineChart.vue
    │   ├── interview/MessageBubble.vue, QuestionCard.vue, AnswerComposer.vue
    │   ├── interview/AudioRecorder.vue, TranscriptPanel.vue, TimerBadge.vue, WsStatusBadge.vue
    │   ├── interview/RoundProgress.vue, FinishConfirmDialog.vue
    │   ├── report/ScoreRing.vue, DimensionBars.vue, EvidenceAccordion.vue
    │   ├── admin/JsonArrayEditor.vue, SkillWeightsEditor.vue, ScoreWeightsEditor.vue
    │   └── admin/PaginationBar.vue, AdminTableToolbar.vue
    ├── utils/
    │   ├── format.ts               # 日期/分数/时长/枚举标签
    │   ├── score.ts                # 分数色带/维度名
    │   ├── storage.ts              # 命名空间/localStorage 安全封装
    │   ├── wsUrl.ts
    │   └── constants.ts
    └── styles/
        ├── tokens.css              # 颜色/间距/字体 + 液态玻璃材质令牌
        ├── element-overrides.scss  # Element Plus 玻璃化 + 降级
        ├── global.scss             # 基础样式 + .card/.glass-surface + 无障碍降级
        └── print.scss              # 打印强制白底、关闭 blur
```

### 3.6 编码约定

| 项 | 约定 |
|---|---|
| 组件 | 单文件组件，`<script setup lang="ts">`；Props/Emits 显式类型；`defineOptions({ name })` |
| 命名 | 组件 PascalCase；composable `useXxx`；store `useXxxStore`；API 函数动宾式；CSS 类 BEM-ish + scoped |
| 类型 | 后端 DTO 统一放 `src/types`；页面局部类型就近定义；禁止 `as any`；接口返回 `unknown` 由 `http.ts` 收窄 |
| 状态 | 服务端数据放 store 或页面局部；不把可推导值重复存 store；不持久化非 auth 数据 |
| 请求 | 所有请求经 `http.ts`；页面不直接 `axios`；同一资源避免并发重复请求（可选请求去重） |
| 错误 | 业务错误抛 `BizError`；页面捕获后按错误码处理；全局只做兜底 toast |
| 样式 | 设计令牌 CSS 变量；禁止魔法色值散落；Element Plus 变量覆盖集中在一个文件；液态玻璃材质统一 `GlassPanel`/`.glass-surface`，禁止业务组件自行拼接 blur/阴影魔法值 |
| 提交 | Conventional Commits：`feat(frontend): ...` / `fix(frontend): ...` / `test(frontend): ...` / `docs(frontend): ...` |
| 注释 | 异步契约、WS 对账、状态机分支、浏览器兼容降级必须有注释；不写"什么"写"为什么" |

### 3.7 npm scripts（门禁固化）

```json
{
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc --noEmit && vite build",
    "preview": "vite preview --port 4173",
    "type-check": "vue-tsc --noEmit",
    "lint": "eslint . --max-warnings=0",
    "format": "prettier --write .",
    "test:unit": "vitest run",
    "test:unit:watch": "vitest",
    "test:coverage": "vitest run --coverage",
    "test:e2e": "playwright test",
    "test:e2e:ui": "playwright test --ui",
    "check": "npm run lint && npm run type-check && npm run test:coverage && npm run build",
    "verify": "npm run check && npm run test:e2e"
  }
}
```

**门禁标准（F6 固化）**：

- `type-check` 0 error；`lint` 0 error/0 warning；`build` 成功。
- 覆盖率（`src/api`、`src/stores`、`src/composables`、`src/utils`）**行 ≥ 70%、分支 ≥ 60%**；视图组件不计入阈值但需有组件测试。
- E2E 全绿（至少 6 条，见 §9.4）。
- 首屏入口 JS（gzip）≤ 300KB；任一异步 chunk ≤ 250KB；ECharts 只在图表页加载。

### 3.8 依赖与体积预算

| 项 | 策略 |
|---|---|
| Element Plus | 组件按需自动导入（`unplugin-vue-components` + ElementPlusResolver）；CSS 可全量引入以保证稳定性，gzip 约 40KB |
| ECharts | `echarts/core` + 按需注册 Radar/Line/Bar、Grid/Tooltip/Legend/Title/Radar 组件、CanvasRenderer；不放首屏 |
| 图标 | `@element-plus/icons-vue` 按需 import |
| 字体 | 系统字体栈（PingFang SC / Microsoft YaHei / system-ui），**不引外部字体** |
| 图片 | 仅 favicon/简单 SVG；不引远程图片，保证断网可演示 |
| 工具库 | 不引 lodash/moment；dayjs 已随 Element Plus；日期/分数格式化自写 6 个函数 |
| CDN | 禁止；所有资源进 bundle |
| 液态玻璃 | 纯 CSS（tokens + AppBackdrop + GlassPanel + Element 覆盖）；不引玻璃拟态库、不引外部图片/视频；不增加首屏 JS 依赖 |

---

## 4. 信息架构、路由与权限

### 4.1 角色与能力矩阵

| 能力 | 学生 STUDENT | 管理员 ADMIN |
|---|---|---|
| 登录/登出/改资料 | ✅ | ✅ |
| 岗位浏览 | ✅ | ✅（只读） |
| 创建/进行/中止面试 | ✅ | ❌（前端不提供入口） |
| 查看面试报告 | ✅（本人） | ✅（后端放行，但前端不设入口） |
| 历史/成长/推荐 | ✅ | ❌（前端不提供入口） |
| 管理端全部页面 | ❌ | ✅ |
| 访问对方区域 | 跳 403 | 跳 `/admin/dashboard` |

> 后端对面试/成长接口只要求"已登录"，没有 `STUDENT` 注解；前端按产品语义做角色隔离，避免管理员误建面试污染数据。ADMIN 直接访问学生路由时重定向到管理端首页。

### 4.2 路由表

| 路径 | name | 视图 | 布局 | 权限 | 说明 |
|---|---|---|---|---|---|
| `/` | — | 重定向 | — | — | 已登录：学生 `/home`、管理员 `/admin/dashboard`；未登录 `/login` |
| `/login` | `login` | LoginView | AuthLayout | 匿名 | 已登录访问则跳默认首页 |
| `/register` | `register` | RegisterView | AuthLayout | 匿名 | 注册成功跳登录 |
| `/home` | `home` | HomeView | StudentLayout | STUDENT | 首页仪表盘 |
| `/positions` | `positions` | PositionListView | StudentLayout | STUDENT | 岗位选择 |
| `/positions/:id` | `position-detail` | PositionDetailView | StudentLayout | STUDENT | 岗位详情/能力模型 |
| `/interview/setup` | `interview-setup` | SetupView | StudentLayout | STUDENT | `?positionId=` 必填；缺省跳岗位列表 |
| `/interview/:id` | `interview-room` | RoomView | StudentLayout（专注模式可隐藏侧栏） | STUDENT | 面试房间 |
| `/interview/:id/report` | `interview-report` | ReportView | StudentLayout | STUDENT | 报告 |
| `/history` | `history` | HistoryView | StudentLayout | STUDENT | 历史（分页） |
| `/growth` | `growth` | GrowthView | StudentLayout | STUDENT | 成长曲线 |
| `/recommendations` | `recommendations` | RecommendationView | StudentLayout | STUDENT | 学习推荐 |
| `/profile` | `profile` | ProfileView | StudentLayout | STUDENT | 个人中心（管理员也可访问，见守卫规则） |
| `/admin` | — | 重定向 `/admin/dashboard` | — | ADMIN | — |
| `/admin/dashboard` | `admin-dashboard` | DashboardView | AdminLayout | ADMIN | 看板 |
| `/admin/positions` | `admin-positions` | PositionAdminView | AdminLayout | ADMIN | 岗位与能力模型 |
| `/admin/positions/:id/weights` | `admin-position-weights` | WeightsView | AdminLayout | ADMIN | 评分权重 |
| `/admin/evaluation` | `admin-evaluation` | 重定向 | — | ADMIN | 兼容方案 §11，重定向到 `/admin/positions?tab=weights` |
| `/admin/questions` | `admin-questions` | QuestionAdminView | AdminLayout | ADMIN | 题库 |
| `/admin/knowledge` | `admin-knowledge` | KnowledgeAdminView | AdminLayout | ADMIN | 知识片段新增/重建 |
| `/admin/resources` | `admin-resources` | ResourceAdminView | AdminLayout | ADMIN | 学习资源 |
| `/admin/users` | `admin-users` | UserAdminView | AdminLayout | ADMIN | 用户启停 |
| `/403` | `forbidden` | ForbiddenView | 共享 | 登录 | 无权限 |
| `/:pathMatch(.*)*` | `not-found` | NotFoundView | 共享 | 任意 | 404 |

- 所有页面组件懒加载：`() => import('@/views/...')`；图表页单独 chunk。
- `meta: { title, requiresAuth, roles, layout, keepAlive }`；`afterEach` 设置 `document.title`。
- `/interview/:id` 支持刷新恢复；`/interview/:id/report` 可直接分享给本人（无公开分享）。

### 4.3 布局与导航

| 布局 | 结构 | 用途 |
|---|---|---|
| `AuthLayout` | 左侧品牌区（产品名 + 3 条卖点 + 装饰网格）+ 右侧表单卡片；移动端上下堆叠 | 登录/注册 |
| `StudentLayout` | 顶栏（Logo、面包屑/页面标题、用户下拉）+ 左侧菜单（首页/岗位/历史/成长/推荐/个人中心）+ 内容区；可折叠 | 学生端 |
| `AdminLayout` | 顶栏（管理控制台标识、返回学生端? 不提供、用户下拉）+ 左侧菜单（看板/岗位/题库/知识库/资源/用户/权重）+ 内容区 | 管理端 |
| 专注模式 | 面试房间隐藏左侧菜单、仅保留顶部退出；报告页正常布局 | 减少干扰 |

菜单项与图标：

- 学生：首页 `HomeFilled`、岗位 `Briefcase`、历史 `Clock`、成长 `TrendCharts`、推荐 `MagicStick`、个人 `User`。
- 管理：看板 `DataBoard`、岗位 `OfficeBuilding`、题库 `Document`、知识库 `Collection`、资源 `Link`、用户 `UserFilled`、权重 `Odometer`。

### 4.4 启动、守卫与登录态

**启动序列（App 挂载前）**：

```text
读 localStorage(ai-interview:auth)
  ├─ 无 token → 进入路由；受保护路由重定向 /login
  └─ 有 token
       ├─ expiresAt 已过期 → 清空 → /login?redirect=...
       └─ 未过期 → GET /api/users/me
             ├─ 成功 → 写入 user → 继续
             └─ 40101/10003/网络失败（区别处理）
                   ├─ 40101 → 清空 → /login
                   └─ 网络失败 → 保留 token，进入页面但显示"无法连接服务"重试
```

- `requiresAuth` 路由守卫：无 token → `/login?redirect=encodeURIComponent(to.fullPath)`；登录成功后只允许跳转以 `/` 开头的内部路径，防开放重定向。
- 角色守卫：`meta.roles` 不含当前角色 → `/403`；已登录用户访问 `/login` → 默认首页。
- 管理员访问学生路由 → `/admin/dashboard`（`/profile` 例外，允许管理员修改自己的资料）。
- 路由离开守卫：面试房间在"录音中/提交中"时 `onBeforeRouteLeave` 确认；未完成的面试不静默丢弃，提示可直接离开（服务端定时器会收卷）。
- 401 全局处理：响应拦截器清 auth + 跳登录，携带 `redirect`；多个并发 401 只跳一次（标志位）。

### 4.5 URL 状态约定

| 页面 | 查询参数 | 说明 |
|---|---|---|
| `/interview/setup` | `positionId` | 必填；缺省回岗位列表 |
| `/history` | `page`,`size` | 与服务端分页同步；刷新保持 |
| `/growth`、`/recommendations` | `positionId` | 缺省取第一个启用岗位；切换写回 URL |
| `/admin/questions` | `positionId`,`type`,`difficulty`,`page`,`size` | 与服务端筛选一致 |
| `/admin/resources` | `positionId`,`skillCode`,`resourceType`,`page`,`size` | 同上 |
| `/admin/users` | `keyword`,`page`,`size` | 同上 |
| `/admin/positions` | `page`,`size`,`tab` | `tab=weights` 时定位到权重入口 |
| `/login` | `redirect` | 仅接受站内路径 |

### 4.6 403/404 与异常兜底

- 403：说明"当前账号无权访问该页面"，按钮"返回首页"（按角色跳学生/管理端）+ "退出登录"。
- 404：说明"页面或面试记录不存在"，按钮"返回首页"。
- 全局 `app.config.errorHandler` + `onErrorCaptured`：记录错误、显示可重试的错误卡片，不让白屏。
- 路由懒加载失败（发版后旧 chunk）：捕获 `Failed to fetch dynamically imported module`，提示刷新。

---

## 5. 页面与功能规格

> 每个页面均按"目标 → 数据源 → 结构 → 交互/状态 → 异常 → 验收"描述；验收条目在 F 阶段逐条对照勾选。
> 所有列表页统一具备：加载骨架、空态、错误态（含 traceId）、分页、刷新按钮；所有表单统一具备：必填校验、提交中禁用、后端错误就地展示、成功后反馈。

### 5.1 登录页 `/login`

| 项 | 规格 |
|---|---|
| 目标 | 学生/管理员统一入口；登录后按角色进入不同首页 |
| 数据源 | `POST /api/auth/login`（返回 token/expiresAt/user） |
| 结构 | 左侧品牌区（产品名、3 条卖点、装饰网格）；右侧卡片：账号、密码、登录按钮、注册链接、演示账号提示 |
| 字段 | `username` 必填（4~50，前端仅必填即可，避免与注册规则混淆）；`password` 必填（6~32）；密码可见切换 |
| 交互 | 提交：`authStore.login()` → 写 localStorage → `GET /me` 可由登录响应直接使用 → 跳 `redirect` 或角色默认页；回车提交 |
| 错误 | 10002 表单下方统一提示；10003 alert；42901 按钮 60s 倒计时并显示"尝试过于频繁"；网络错误保留输入 |
| 安全 | 不勾选"记住密码"；可"记住用户名"（localStorage 单独键，不含 token）；日志不打印密码/token |
| 验收 | demo01 登录 → `/home`；admin 登录 → `/admin/dashboard`；错误密码 → 10002；刷新后仍登录；登出后回登录页 |

### 5.2 注册页 `/register`

| 项 | 规格 |
|---|---|
| 目标 | 新学生注册（后端固定 STUDENT） |
| 数据源 | `POST /api/auth/register` |
| 字段 | `username` 4~50；`password` 6~32；`confirmPassword` 必须一致；`realName` ≤50 可选；`major` ≤100 可选 |
| 交互 | 提交成功 → 提示"注册成功，请登录" → 跳 `/login?username=<预填>`；不自动登录（后端不返回 token） |
| 错误 | 10001 → username 字段错误"用户名已存在"；40001 → 展示后端 message；密码确认不一致前端拦截 |
| 验收 | 注册成功；重复用户名提示；密码不一致拦截；注册后可直接登录 |

### 5.3 首页 `/home`

| 项 | 规格 |
|---|---|
| 目标 | 学生进入后的导航与最近状态总览 |
| 数据源 | `GET /me`；`GET /positions`；`GET /history?page=1&size=5`；可选 `GET /recommendations?positionId=<第一个岗位>` |
| 结构 | 欢迎区（姓名 + 一句话）；四个快捷卡（开始面试/历史/成长/推荐）；"最近面试"卡片（最近一条历史，含状态、分数、时间、查看/继续按钮）；"岗位入口"两卡；"推荐预览"（Top1 技能 + 理由 + 跳推荐） |
| 交互 | 开始面试：若只有一个岗位 → 直接 `/interview/setup?positionId=1`；多个 → 岗位列表；最近面试按状态给按钮：进行中→继续房间，FINISHED/TIMEOUT→报告，ABORTED→重新开始 |
| 状态 | 新用户无历史 → 欢迎空态 + 主 CTA；推荐接口失败不影响其他卡片（`Promise.allSettled`） |
| 验收 | 新用户/有历史用户均能正常显示；最近面试按钮路由正确；接口部分失败不白屏 |

### 5.4 岗位列表 `/positions`

| 项 | 规格 |
|---|---|
| 目标 | 展示启用岗位，进入岗位详情或开始设置 |
| 数据源 | `GET /positions` |
| 结构 | 页面标题 + 说明；岗位卡片网格（名称、code、描述、默认题数、`查看能力模型`、`开始面试`）；底部说明"题目覆盖技术/项目/场景/行为四类" |
| 交互 | 卡片点击进详情；开始面试带 `positionId` 去设置页；刷新按钮 |
| 状态 | 空列表（管理员停用了所有岗位）→ "暂无可面试岗位，请联系管理员"；错误→重试 |
| 验收 | 两个种子岗位正常显示；停用岗位不出现；点击跳转正确；测试残留岗位（`ACC_*`）在数据清理后不出现 |

### 5.5 岗位详情 `/positions/:id`

| 项 | 规格 |
|---|---|
| 目标 | 讲清岗位能力模型与评分权重，支撑"选择岗位"决策 |
| 数据源 | `GET /positions/{id}`；`GET /positions/{id}/skills` |
| 结构 | 头部（名称/code/描述/默认题数/技能数）；能力模型表（技能名、编码、说明、权重、要求等级）；权重横向条（ECharts Bar 或 CSS 条）；评分说明卡片（内容 50/表达 30/应变 10/匹配 10，可被岗位权重覆盖）；CTA"开始面试" |
| 交互 | 返回列表；开始面试带 positionId；权重和展示（后端保证=1，前端仍显示校验信息） |
| 异常 | 40401（岗位不存在/已停用）→ 空态 + 返回列表；技能为空 → 提示"能力模型配置中"并禁用开始 |
| 验收 | 7 项技能与权重正确；权重可视化比例正确；40401 有降级；CTA 到设置页 |

### 5.6 面试设置 `/interview/setup?positionId=`

| 项 | 规格 |
|---|---|
| 目标 | 配置一次面试并创建会话，进入房间 |
| 数据源 | `GET /positions/{id}`、`GET /positions/{id}/skills`；`POST /interviews` |
| 字段 | 岗位（只读展示，可切换）；方式（TEXT/AUDIO 卡片，默认 TEXT）；难度 1~5（分段/滑块，默认 3，显示标签入门/基础/中等/进阶/挑战）；轮次 5~8（默认 `clamp(position.defaultRounds,5,8)`）；时长 15~60 分钟（默认 15，选项 15/30/45/60） |
| 语音能力 | 挂载时检测 `MediaRecorder`、`getUserMedia`、`SpeechRecognition`；不支持则禁用 AUDIO 卡片并提示原因；支持 ASR 缺失但 MediaRecorder 可用时，允许 AUDIO 并提示"语音识别不可用，可手工填写转写文本" |
| 交互 | 提交：`POST /interviews` → 成功后 `router.push({name:'interview-room', params:{id}, state:{autoStart:true}})`；防重复提交；失败保留表单 |
| 错误 | 40002 范围错误就地提示；40401 岗位不可用 → 返回列表；409? 无；50001 → 重试 |
| 验收 | 默认值正确（含 clamp）；三种方式/难度/轮次/时长边界不可越界；创建成功进房间；重复点击只建一个会话；后端不可用时有错误提示 |

> 设计决策：**创建与 start 分离**。设置页只创建（CREATED），房间挂载后自动 start（首次），这样房间能先建立 WS、再收首题；start 失败可在房间内重试/中止，不丢会话 ID。

### 5.7 面试房间 `/interview/:id`

#### 5.7.1 目标与必需元素（对照方案 §11）

必须包含：当前岗位和难度、面试倒计时、当前 AI 问题、文字输入框、录音按钮、实时转写区域、当前轮次、结束面试按钮、网络与录音状态提示。

页面结构（桌面 1440）：

```text
┌──────────────────────────────────────────────────────────────┐
│ 顶栏：岗位名 · 难度 · 状态徽标 · 剩余 12:34 · WS 已连接 · 退出 │
├───────────────────────────────┬──────────────────────────────┤
│ 对话区（滚动）                 │ 作答区（固定/粘性）           │
│  AI 主问题/追问气泡            │ 轮次 2/5 · 追问 1/3           │
│  USER 文字/音频气泡            │ ┌ 文字输入 (5000) ┐           │
│  AI 思考中…                    │ │ Ctrl+Enter 提交 │           │
│                                │ └──────────────────┘           │
│                                │ 或 语音：● 录音 转写/编辑      │
│                                │ [结束面试] [中止]              │
└───────────────────────────────┴──────────────────────────────┘
```

移动端单列：对话在上，作答区吸底。

#### 5.7.2 数据与初始化

| 步骤 | 动作 |
|---|---|
| 1 | 读取路由 `id`，`GET /interviews/{id}` 拿 detail；`GET /messages` 拿全部可见消息 |
| 2 | 若 status=CREATED 且 `state.autoStart`：先连 WS，再 `POST start`；start 返回首题用于兜底渲染；失败提示重试/中止 |
| 3 | 若 status=WAITING_USER/PROCESSING_AI：直接恢复；若 PROCESSING_AI，显示"AI 分析中"并启动轮询 |
| 4 | 若 status=FINISHED/TIMEOUT：跳报告页；若 ABORTED：显示已中止 + 回历史 |
| 5 | 若 40401：显示"记录不存在或无权限"，回历史 |
| 6 | 监听 `visibilitychange`/`online`：回到前台或恢复网络时重新拉 detail + messages 对账 |

#### 5.7.3 轮次与"是否可作答"算法（核心）

```ts
const round = detail.currentRound
const roundMessages = messages.filter(m => m.roundNo === round)
const aiCount = roundMessages.filter(m => m.speaker === 'AI').length
const userCount = roundMessages.filter(m => m.speaker === 'USER').length
const awaitingAnswer =
  detail.status === 'WAITING_USER' &&
  aiCount > userCount &&                       // 最后一条 AI 提问尚未被回答
  messages.at(-1)?.speaker === 'AI'            // 双保险
const canSubmit = awaitingAnswer && !submitting && !finishing
const submitRoundNo = detail.currentRound      // 永远用服务端轮次
const displayRound = detail.status === 'FINISHED' || detail.status === 'TIMEOUT'
  ? detail.plannedRounds
  : Math.min(detail.currentRound + 1, detail.plannedRounds)
```

- **禁止**用"消息总数变化"推轮次（主问题在 start 时已预写、未来题被隐藏、追问不增轮次）。
- 追问计数：当前轮 AI 消息数 - 1（clamp 0~3）；用 `followupIndex` 也可，但以消息为准。
- 若 detail 与 messages 不一致（例如刚收到 WS 事件）：先刷新，再渲染；刷新期间保留旧内容避免闪烁。

#### 5.7.4 文字作答

| 项 | 规格 |
|---|---|
| 输入 | `el-input type=textarea`，`maxlength=5000`、`show-word-limit`；Enter 换行，`Ctrl/Cmd+Enter` 提交；IME 组合输入期间不触发快捷键 |
| 提交 | 置 `submitting`；乐观插入 USER 气泡（pending）；`POST messages/text {roundNo,content}`；成功后标记 sent；失败回滚为 failed 并保留文本 |
| 等待 AI | 置 `aiThinking`；启动轮询（1s→2s→3s，最多 60s）；收到 WS QUESTION/FOLLOW_UP/PROGRESS 立即刷新 detail+messages 并停止轮询 |
| 禁用条件 | 非 awaitingAnswer、submitting、finishing、终态、WS 重连中（可输入但不可提交） |
| 草稿 | 按 `sessionId:roundNo` 存 localStorage，提交成功/轮次推进后清除；刷新恢复 |
| 错误 | 10005/40002 → 刷新 detail+messages，提示"状态已更新"；50001/网络 → 保留文本，按钮可重试；不得自动重发（避免重复回答） |

#### 5.7.5 语音作答（AUDIO 模式）

**录音**：

- 首次点击录音时请求 `getUserMedia({audio:true})`；拒绝 → 提示开启权限 + 提供"切换文字面试"说明（需新建 TEXT 会话）。
- 选择 MIME：优先 `audio/webm;codecs=opus`，其次 `audio/webm`；不支持 → 禁用录音并提示浏览器限制。
- `MediaRecorder` 分片收集（`ondataavailable`），停止后合并 `Blob`；扩展名 `.webm`。
- 计时器显示录音时长；单次最长 10 分钟（后端 durationMs ≤ 600000）；大小 >10MB 时停止并提示。
- 停止后保留 blob 预览（`<audio controls>` 本地 objectURL），可重录。

**转写**：

- `SpeechRecognition`（`webkitSpeechRecognition`）：`lang='zh-CN'`、`continuous=true`、`interimResults=true`；final 结果累加，interim 单独显示。
- 录音结束/识别结束：停止识别；`onend` 时若仍在录音则重启（最多 3 次，防死循环）；`onerror` 映射：
  - `not-allowed`/`service-not-allowed` → 权限/服务不可用，提示手填；
  - `no-speech` → 提示"未识别到语音，可重录或手填"；
  - `network` → 提示"语音识别需要网络，可手填转写后提交"（断网演练的关键路径）。
- 转写区域：实时显示 final + interim；停止后允许编辑（`textarea`），并提示"可修正识别错误"。

**提交**：

- 校验：转写非空（1~5000）、durationMs 1~600000、blob 存在且 ≤10MB。
- `FormData`：`file`（`audio/webm`）、`roundNo`、`asrText`、`durationMs`、`pauseCount?`（由识别停顿/静音段估算，可为空）。
- `POST messages/audio`，`onUploadProgress` 显示百分比；成功后按文字同样的等待/对账流程。
- 提交后清空录音 blob/转写/计时；失败保留以便重试。

**回放**：

- 报告页/房间历史消息：对 `inputType=AUDIO` 的消息显示"回放"；点击 `GET /interviews/{id}/messages/{mid}/audio`（`responseType:'blob'`，带 Authorization），`URL.createObjectURL` 播放；组件卸载/切换时 `revokeObjectURL`。
- 不使用 `<audio :src="/api/...">`（无法带 Authorization 头）。

**降级**：

- 不支持 Web Speech 但支持录音：允许录音 + 手工填写转写（页面明确提示"本浏览器不支持自动转写"）。
- 不支持录音：AUDIO 模式禁用；建议回岗位设置选 TEXT。
- 完全断网：ASR 必失败；手工转写 + 录音仍可提交给本地后端（离线栈）。

#### 5.7.6 倒计时与 WS 状态

**倒计时**：

- 初始值取 detail.remainSeconds；null → 不显示（未 start/已终态）。
- 本地每秒递减，收到 TIMER 用服务端值校准（不要累加差值）。
- ≤60s 变红/闪烁 + 文字提示；到 0 禁用提交，提示"已到时间，正在收卷"，等待 PROGRESS/刷新 detail。
- 页面从后台恢复时重新拉 detail 校准（浏览器会节流定时器）。

**WS 状态徽标**：

| 状态 | 展示 | 行为 |
|---|---|---|
| CONNECTING | 连接中… | 输入可用；提交前无需等 WS（REST 可独立工作） |
| OPEN | 实时连接 | 正常 |
| RECONNECTING | 重连中（第 n 次） | 顶部提示；继续轮询保证可用 |
| CLOSED_4401 | 登录已失效 | 清 auth，跳登录（保留 redirect） |
| CLOSED_OTHER | 实时连接断开，使用轮询 | 显示"重连"按钮；自动指数退避 1/2/4/8/15s，最多 8 次 |
| ERROR | 红色提示 | 显示可恢复错误；按事件处理 |

#### 5.7.7 结束与中止

- **结束面试**：`el-popconfirm`/对话框二次确认"结束后将生成报告，不能再作答"；确认后 `POST finish`（禁用按钮，显示"报告生成中"）；轮询 report（10004 正常）；成功后跳报告或显示"查看报告"。
- finish 返回 `reportId=null` 属正常；不要显示"失败"。
- 若轮次用尽自动 FINISHED：WS PROGRESS → 刷新 detail → 显示"面试已结束，正在生成报告"→ 跳报告。
- 若 TIMEOUT：显示"时间到，系统已自动收卷"，同样跳报告。
- **中止面试**：确认"中止后不生成报告，记录将标记为已中止"；`POST abort`；成功后跳历史页。
- 录音中/提交中点击结束：先确认停止录音并取消未完成提交；不强制中止。

#### 5.7.8 刷新、断线、并发与边界

| 场景 | 处理 |
|---|---|
| 刷新页面 | detail+messages 重建；录音/转写丢失（提示过）；倒计时以 remainSeconds 为准 |
| WS 断线 | 轮询 detail+messages（2s）；重连成功后立即对账；不重复渲染 |
| 回答提交后 60s 无反馈 | 刷新 detail：若已推进/终态按服务端；若仍 PROCESSING_AI，显示"AI 响应较慢，可继续等待或结束面试" |
| 双击提交 | 按钮禁用 + submitting 锁；若仍触发 10005，按状态刷新 |
| 会话被另一标签页结束 | 本页 WS PROGRESS/刷新 detail 后跳报告/历史 |
| 会话超时 | 同 TIMEOUT 处理；不弹错误 |
| 40401 | 回历史并提示记录不存在 |
| 令牌过期 | WS 4401/HTTP 401 → 登录页带回跳 |
| 离开页面 | 录音中/提交中确认；直接离开不中止会话（服务端定时器会收卷），返回时从历史继续 |

#### 5.7.9 房间验收标准

1. CREATED 进入 → 自动 start → 显示首题、岗位、难度、轮次、倒计时。
2. 文字回答 → 受理后 3s 内看到 AI 追问或下一题（Mock 模式），轮次/追问标签正确。
3. 连续追问 3 次后强制换下一题（后端行为），UI 不串轮。
4. 语音回答 → 录音、转写、提交、报告 voice 维度可用；ASR 不可用时手工转写可提交。
5. 终态（finish/abort/timeout）→ 停计时、禁输入、跳转正确、WS 关闭。
6. 断网/WS 断开 → 轮询仍可推进；恢复后不重复消息。
7. 刷新任意中间态 → 恢复一致；不出现重复提交或丢失当前问题。
8. 方案 §11 九项必需元素全部可见（逐项检查）。

### 5.8 面试报告 `/interview/:id/report`

| 项 | 规格 |
|---|---|
| 目标 | 让每个分数可解释：总分、维度、逐题证据、亮点/不足/建议、资源 |
| 数据源 | `GET /interviews/{id}/report`（核心）；`GET /interviews/{id}`（日期/时长/状态）；`GET /messages`（AUDIO 回放）；`GET /users/me/recommendations?positionId=`（资源预览，可失败） |
| 结构 | ① 头部：岗位、模式、日期、用时（finishedAt-startedAt）、题数、状态；② 总分环 + 等级文案；③ 六维/五维雷达 + 维度条；④ 总评；⑤ 亮点/不足/改进建议三栏；⑥ 逐题证据折叠面板（题目/回答摘要/得分/覆盖率/缺失点/证据句/理由/音频回放）；⑦ 推荐资源（Top3 技能 + 资源/练习）；⑧ 操作：打印/导出、重练同岗位、看成长、看推荐 |
| 分数 | `totalScore` 保留 2 位；维度分 `toFixed(1)`；色带：<60 红、60~74 橙、75~84 蓝、≥85 绿 |
| 雷达 | TEXT 5 维（technical/depth/logic/match/communication）；AUDIO 6 维 + voice；缺失维度不补 0，图例注明"文本面试不采集语音维度" |
| 逐题 | `questionEvidence` 按 roundNo 升序；默认展开第 1 题；`scores` 显示小徽标；`coveredPoints` 绿、`missingPoints` 红；`evidence` 为空显示"未提取到证据句"；`reason` 必有 |
| 轮询 | 进入先 GET report；10004 → 1s、2s、3s…最多 60s；同时监听 WS REPORT_READY（若 WS 已关则纯轮询）；超时显示"生成较慢/可重试" |
| 重试 | 按钮"重新生成报告" → `POST finish`（幂等）→ 继续轮询；失败 ERROR 提示原因 |
| 音频回放 | 行内"回放"按钮，blob URL；一次只允许一个播放，切换自动暂停 |
| 推荐 | 报告页只做预览（Top1~2 技能 + 首条资源），失败不阻塞报告；完整推荐跳 `/recommendations?positionId=` |
| 打印 | `window.print()` + `print.scss`：隐藏侧栏/按钮，分页不截断证据卡片；可选"另存为 PDF"（浏览器能力） |
| 异常 | 10004 轮询；40401 → 404 空态；ABORTED/无报告 → "该面试已中止，无报告"；网络错误 → 重试 |
| 验收 | 完整报告要素齐全；AUDIO 有 voice；逐题数与面试轮次一致；10004 可自动恢复；打印版式正常；所有 CTA 路由正确 |

### 5.9 历史面试 `/history`

| 项 | 规格 |
|---|---|
| 目标 | 查看所有已开始场次（含进行中/中止/超时），继续或看报告 |
| 数据源 | `GET /users/me/history?page=&size=` |
| 结构 | 筛选条（状态客户端筛选：全部/进行中/已完成/已中止；仅当前页，标注）；表格/卡片：岗位、模式、难度、轮次（current/planned）、状态、总分、开始时间、操作 |
| 操作 | 进行中（WAITING_USER/PROCESSING_AI/RUNNING）→ `继续面试`；FINISHED/TIMEOUT → `查看报告`；ABORTED → `重新开始`（同岗位设置）；CREATED 不在历史 |
| 分页 | `page/size` 同步 URL；size 10/20/50；total 显示 |
| 状态 | 无历史 → 空态 + 开始面试；加载失败重试；状态标签颜色 |
| 验收 | 分页正确；各状态操作正确；abort 场不显示报告按钮；时间/分数格式正确 |

### 5.10 能力成长 `/growth`

| 项 | 规格 |
|---|---|
| 目标 | 按技能展示掌握度随场次的变化 |
| 数据源 | `GET /positions`；`GET /users/me/growth?positionId=` |
| 结构 | 顶部岗位选择 + 说明（掌握度=首次直取，之后旧×0.7+本次×0.3）；ECharts 多折线（x=日期，y=0~100，每技能一条）；技能卡片列表（当前掌握度、变化量、记录点数）；空态 |
| 交互 | 切换岗位重新拉取并写 URL；图例点击显隐；tooltip 显示日期/分数；图表 resize（侧栏折叠/窗口） |
| 数据 | `records` 升序；`current` = 最新；同日多场按时间点显示；日期解析 `new Date(evaluatedAt)`（本地时区） |
| 异常 | 无记录 → 空态"先完成一场面试"；接口失败 → 错误卡片；岗位停用 → 回退第一个启用岗位 |
| 验收 | 多技能多折线正确；切换岗位正确；空态/错误态；ECharts 懒加载；内存无泄漏（dispose） |

### 5.11 学习推荐 `/recommendations`

| 项 | 规格 |
|---|---|
| 目标 | 把报告短板转成可执行的学习/练习/重练计划 |
| 数据源 | `GET /positions`；`GET /users/me/recommendations?positionId=` |
| 结构 | 岗位选择；Top3 技能卡（技能名、优先级、短板理由列表、资源列表、练习列表、错题重练、下场建议）；底部"按建议开始下一场" |
| 交互 | 资源外链 `target=_blank rel=noopener noreferrer`；无 contentUrl 显示"站内练习/题目"不跳转；错题点击展开题干与缺失点；"开始下一场"→ 设置页带岗位与建议难度（可从 `nextInterviewSuggested` 解析，解析失败只带岗位） |
| 数据 | 优先级降序；未考察技能显示"尚无记录，建议摸底"；资源/练习/错题可能为空；`nextInterviewSuggested` 可能仅部分卡片有 |
| 异常 | 新用户也有默认推荐（后端 D24）；失败重试；缓存 TTL 10min，页面提供"刷新"（重新请求，可能仍命中缓存） |
| 验收 | Top3 理由/资源/错题显示；空数组优雅降级；外链安全；开始下一场路由正确 |

### 5.12 个人中心 `/profile`

| 项 | 规格 |
|---|---|
| 目标 | 查看账号信息、编辑资料、退出登录 |
| 数据源 | `GET /users/me`；`PUT /users/me {realName,major}`；`POST /auth/logout` |
| 结构 | 账号卡（用户名、角色、状态、注册信息不含密码）；资料表单（真实姓名、专业）；保存；退出登录；说明"用户名/角色不可修改，密码修改暂未开放" |
| 交互 | 保存成功刷新；未变更禁用保存；退出确认；清理 auth + 跳登录 |
| 异常 | 40001/50001 提示；401 跳登录 |
| 验收 | 修改后回显；退出后 token 失效；不展示密码散列 |

### 5.13 管理端看板 `/admin/dashboard`

| 项 | 规格 |
|---|---|
| 目标 | 一屏看清运营数据与近 7 日趋势 |
| 数据源 | `GET /admin/dashboard` |
| 结构 | 四张统计卡（用户数、面试场次、题目数、平均分）；ECharts 柱线组合（柱=场次，线=平均分，右轴）；刷新按钮；说明口径（场次=已开始；均分=已出分场次） |
| 数据 | `avgScore` 可能 null → 显示"—"；近 7 日 `avgScore` 为 null 的日期折线断点；日期升序 |
| 异常 | 加载骨架；失败重试；无数据不报错 |
| 验收 | 数字与接口一致；图表双轴正确；null 处理；刷新更新 |

### 5.14 管理端岗位 `/admin/positions`

| 项 | 规格 |
|---|---|
| 目标 | 维护岗位、能力模型（技能/权重/要求等级）与启用状态 |
| 数据源 | `GET /admin/positions`（分页）；`GET /admin/positions/{id}`；`POST`；`PUT /{id}` |
| 结构 | 表格：ID/编码/名称/默认题数/技能数/状态/更新时间/操作（编辑、评分权重、查看）；新增按钮；分页 |
| 新建/编辑 | 表单：code（新增必填 2~50，创建后只读）、name（≤100）、description（≤500）、defaultRounds（3~8）、enabled（开关）；技能编辑器（动态行：skillCode、skillName、description、weight、requiredLevel；至少 1 行，权重和=1±0.01，实时显示合计；requiredLevel 0~100）；评分权重可选（默认不展开） |
| 交互 | 保存：新增 POST / 编辑 PUT；成功刷新；`skills` 非空整体替换；编辑不传 code；不提供删除（被题目/历史引用） |
| 校验 | 前端与后端同规则；权重和不为 1 时禁用保存并显示差多少；skillCode 唯一（前端提示重复）；defaultRounds 3~8 |
| 错误 | 40001 字段错误；40401 岗位不存在；保存后回读一致 |
| 验收 | 新增岗位学生端立即可见；禁用后学生端不可见；技能/权重保存回读；校验提示准确；无删除按钮 |

### 5.15 评分权重 `/admin/positions/:id/weights` 与 `/admin/evaluation`

| 项 | 规格 |
|---|---|
| 目标 | 按岗位调整内容/表达/总分权重组（方案 §7.2 "权重保存数据库，可按岗位调整"） |
| 数据源 | `GET /admin/positions/{id}`；`PUT /admin/positions/{id}/weights` |
| 结构 | 岗位选择（来自列表）；三组权重卡片：content（technical/depth/logic/evidence/match）、expression（clarity/fluency/pace/confidence/completeness）、total（content/expression/adaptability/match）；每组实时求和 + 校验；"恢复默认"（前端默认值）；保存；公式说明 |
| 交互 | 加载已有 `scoreWeights`（三组均含全部字段，未用为 null）；保存时提交**完整三组**；成功回读；仅影响后续面试（历史报告不变） |
| 校验 | 每组 5/4 项全部必填、0~1、和=1±0.01；任一组不完整禁用保存；错误就地提示 |
| 兼容 | `/admin/evaluation` 重定向到 `/admin/positions?tab=weights`，避免方案 §11 路由 404 |
| 验收 | 组和校验；保存回读；历史报告不变；页面说明清晰 |

### 5.16 管理端题库 `/admin/questions`

| 项 | 规格 |
|---|---|
| 目标 | 题目 CRUD（逻辑删），维护参考答案/得分点/失分点/追问模板 |
| 数据源 | `GET /admin/positions`（筛选用）；`GET /admin/questions`；POST/PUT/DELETE |
| 结构 | 筛选条（岗位、题型、难度、刷新）；表格：ID/岗位/题型/题干（截断）/难度/技能标签/状态/版本/更新时间/操作；分页；新增按钮 |
| 表单 | positionId、type（TECHNICAL/PROJECT/SCENARIO/BEHAVIORAL）、content（4~1000）、background（≤1000）、difficulty（1~5）、skillTags（1~10，标签输入）、referenceAnswer（≤4000）、keyPoints（≤10）、deductionPoints（≤10）、followupTemplates（≤10）；数组用"每行一条"编辑器；`version` 仅编辑时携带 |
| 交互 | 新增成功刷新；编辑保存后 version+1；删除二次确认（逻辑删 DISABLED）；筛选/分页同步 URL |
| 冲突 | 40003 → 提示"数据已被他人修改"，重新拉取该题并保留用户草稿，用户确认后再保存 |
| 错误 | 40001/40401；学生 token 不会出现于本页（40301 跳 403） |
| 验收 | CRUD 全通；逻辑删后不参与组卷（后端保证）；version 冲突可恢复；筛选分页正确；长文本展示不破版 |

### 5.17 管理端知识库 `/admin/knowledge`

| 项 | 规格 |
|---|---|
| 目标 | 新增知识片段并重建 FULLTEXT 索引（后端无列表接口，页面按现有契约降级） |
| 数据源 | `GET /admin/positions`；`POST /admin/knowledge`；`POST /admin/knowledge/rebuild` |
| 结构 | 说明卡（知识片段如何参与 RAG：关键词→FULLTEXT Top5→注入评分/出题 Prompt）；新增表单：岗位、标题（2~200）、内容（10~8000）、keywords（逗号分隔 4~500）、skillTags（逗号分隔，需对齐岗位技能）、difficulty（1~5 可选）；提交；"重建索引"按钮；成功回显最近提交片段（标题/ID/岗位/技能） |
| 交互 | 保存成功提示"已进入检索词典，下一场面试即可命中"；重建索引二次确认；重建结果 `{rebuilt:true}` 提示；技能标签用岗位技能多选生成逗号串，降低错填 |
| 限制 | 页面明确提示"当前版本后端未提供知识片段列表/编辑/删除接口；如需列表能力请另行立项后端变更" |
| 错误 | 40001 字段错误（含岗位不存在 40401）；重建失败可重试 |
| 验收 | 新增后成功回显；关键词/技能格式正确；重建成功；说明与实际能力一致（不出现假列表） |

### 5.18 管理端学习资源 `/admin/resources`

| 项 | 规格 |
|---|---|
| 目标 | 维护推荐引擎使用的 ARTICLE/QUESTION/PRACTICE/PROJECT 资源 |
| 数据源 | `GET /admin/resources`；`GET /admin/positions/{id}`（技能下拉）；POST/PUT/DELETE |
| 结构 | 筛选条（岗位、技能、类型）；表格：ID/岗位/技能/标题/类型/难度/状态（启用/下架）/创建时间/操作（编辑、下架）；分页；新增 |
| 表单 | positionId、skillCode（必须属于岗位技能）、title（≤200）、resourceType（四类）、contentUrl（≤500，可选）、difficulty（1~5）、enabled（默认 1） |
| 交互 | 新增/编辑全量提交；下架 = DELETE（enabled=0），二次确认；已下架可编辑重新启用；筛选/分页同步 URL |
| 错误 | 40001（技能不对齐/类型非法）、40401；更新后回读 |
| 验收 | CRUD（含下架）全通；技能下拉随岗位变化；下架后推荐不再命中（后端保证）；状态展示正确 |

### 5.19 管理端用户 `/admin/users`

| 项 | 规格 |
|---|---|
| 目标 | 查看用户、启用/禁用账号 |
| 数据源 | `GET /admin/users?keyword=&page=&size=`；`PUT /admin/users/{id}/status` |
| 结构 | 搜索框（用户名/真实姓名）；表格：ID/用户名/真实姓名/角色/专业/状态/操作；分页；当前登录账号标记"当前" |
| 交互 | 状态开关：启用↔禁用，二次确认；成功后刷新；禁用自己时开关禁用（后端也会 40001）；无新增/删除 |
| 错误 | 40001/40401；40301 跳 403 |
| 验收 | 搜索/分页；禁用后该用户登录返回 10003；自己不可禁用；角色标签正确 |

### 5.20 异常页 `/403`、`/404`

| 页面 | 内容 | 操作 |
|---|---|---|
| 403 | 图标 + "无权限访问" + 说明（角色不足） | 返回角色首页、退出登录 |
| 404 | 图标 + "页面/记录不存在" + 说明 | 返回首页、去历史 |
| 全局错误卡 | 错误码 + message + traceId（可复制） + 重试 | 重试、返回 |

---

## 6. 前端架构设计

### 6.1 分层与依赖规则

```mermaid
flowchart TB
    V[views 页面] --> C[components 组件]
    V --> S[stores 状态]
    V --> H[composables 组合式函数]
    C --> H
    S --> A[api 接口层]
    H --> A
    A --> T[types 契约类型]
    S --> T
    C --> T
    H --> T
    A --> U[utils 工具]
    C --> U
    S --> R[router]
    V --> R
```

**依赖规则（Code Review 检查点）**：

1. `views` 可以依赖 `components/stores/composables/api/types/utils`；**`api` 不得依赖 `views/stores/components`**。
2. `http.ts` 不导入 Pinia store（防循环依赖），只从 `utils/authSession.ts` 读写 token，并通过事件通知登录失效。
3. 组件不直接调用 axios；页面不直接操作 localStorage（统一 `utils/storage.ts`）。
4. 一个页面只允许一个"数据编排入口"（`useXxxPage` 或页面 `onMounted`）；子组件通过 props/emits 通信。
5. 服务端 DTO 类型只在 `types` 定义一次；组件不得声明同名局部接口。

### 6.2 API 层设计

#### 6.2.1 axios 实例与拦截器

```ts
// src/api/http.ts（示意，F1-4 落地）
import axios, { AxiosError } from 'axios'
import { getToken, notifyAuthExpired } from '@/utils/authSession'
import type { ApiResult } from '@/types/api'

export class BizError extends Error {
  constructor(
    public readonly code: number,
    message: string,
    public readonly traceId?: string,
    public readonly httpStatus?: number,
  ) {
    super(message)
    this.name = 'BizError'
  }
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 20_000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (res) => {
    // blob 响应（音频）直接返回
    if (res.config.responseType === 'blob') return res
    const body = res.data as ApiResult<unknown>
    if (body?.code === 0) return body.data
    throw new BizError(body.code, body.message ?? '请求失败', body.traceId, res.status)
  },
  (err: AxiosError) => {
    const status = err.response?.status
    const body = err.response?.data as ApiResult<unknown> | undefined
    if (status === 401 || body?.code === 40101) {
      notifyAuthExpired() // 全局只跳一次登录
    }
    if (body?.code != null) {
      throw new BizError(body.code, body.message ?? err.message, body.traceId, status)
    }
    throw new BizError(-1, '网络异常，请检查后端服务后重试', undefined, status)
  },
)
```

> **循环依赖**：`http.ts` 不 import `useAuthStore`；`utils/authSession.ts` 提供 `getToken/setToken/clearToken/notifyAuthExpired`（内部用 `window` 事件），`App.vue` 监听 `auth:expired` 后调用 store 清空并跳登录。

#### 6.2.2 API 函数规范

```ts
// src/api/interview.ts（示意）
export async function createInterview(payload: CreateInterviewPayload): Promise<InterviewDetailVO> {
  return http.post('/api/interviews', payload)
}
export async function submitTextAnswer(id: number, payload: TextAnswerPayload) {
  return http.post<unknown, SubmitAccepted>(`/api/interviews/${id}/messages/text`, payload)
}
export async function fetchAudioBlob(id: number, messageId: number): Promise<Blob> {
  const res = await http.get(`/api/interviews/${id}/messages/${messageId}/audio`, {
    responseType: 'blob',
  })
  const blob = res.data as Blob
  // 业务失败时后端仍可能返回 HTTP 200 + JSON（code=40401 等）
  if (blob.type.includes('application/json')) {
    const body = JSON.parse(await blob.text()) as ApiResult<unknown>
    throw new BizError(body.code, body.message ?? '音频读取失败', body.traceId)
  }
  return blob
}
```

- 所有函数返回 **data 本体**（不是 `ApiResult`），错误统一抛 `BizError`。
- 分页函数返回 `PageResult<T>`；`records` 可为空数组，`total` 为 0。
- 查询参数只传有值的字段（`undefined`/空串不拼进 URL）；数组参数用重复 key 或后端约定（本项目无数组查询参数）。
- 上传函数接收 `FormData`，超时 60s，暴露 `onUploadProgress`。
- 不自动重试任何 POST；GET 的重试由页面/轮询控制；取消用 `AbortController`（轮询、路由卸载）。

#### 6.2.3 请求超时与取消策略

| 请求 | 超时 | 取消/重试 |
|---|---|---|
| 登录/注册/资料 | 20s | 不自动重试；用户手动 |
| 岗位/历史/成长/推荐/报告 | 20s | 页面重试按钮；轮询用独立 AbortController |
| 创建/start/文字回答/finish/abort | 30s | 不自动重试；提交锁；10005 走刷新 |
| 音频上传 | 60s | 不自动重试；失败保留 blob/转写 |
| 音频回放 | 60s | 失败提示；blob URL 清理 |
| 管理端 CRUD | 20s | 40003 刷新；不自动重试 |

#### 6.2.4 错误呈现策略

- **表单错误**（40001/40002/10001/10002）：就地字段/表单下方，保留输入。
- **状态冲突**（10005/40003）：顶部持久提示 + 自动刷新数据；不吞掉用户草稿。
- **权限/登录**（40101/40301）：全局跳转；页面不重复弹窗。
- **可恢复业务**（10004/42901）：页面内提示 + 倒计时/轮询。
- **未知错误**（50001/网络）：`ErrorState` 组件 + traceId + 重试；不显示堆栈。
- 全局 toast 只用于成功反馈和无法就地展示的错误；同一错误 3 秒内去重。

### 6.3 类型系统（`src/types`）

```ts
export interface ApiResult<T> {
  code: number
  message?: string
  data?: T
  traceId?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export type Role = 'STUDENT' | 'ADMIN'
export type InterviewMode = 'TEXT' | 'AUDIO'
export type InterviewStatus =
  | 'CREATED' | 'RUNNING' | 'WAITING_USER' | 'PROCESSING_AI'
  | 'FINISHED' | 'ABORTED' | 'TIMEOUT'
export type QuestionType = 'TECHNICAL' | 'PROJECT' | 'SCENARIO' | 'BEHAVIORAL'
export type ResourceType = 'ARTICLE' | 'QUESTION' | 'PRACTICE' | 'PROJECT'
export type WsEventType = 'QUESTION' | 'FOLLOW_UP' | 'TRANSCRIPT'
  | 'TIMER' | 'PROGRESS' | 'REPORT_READY' | 'ERROR'

export interface SubmitAccepted {
  accepted: boolean
  roundNo: number
}
export interface FinishAccepted {
  accepted: boolean
  status: InterviewStatus
  reportId: number | null
}
```

> 完整类型定义见附录 B。**字段空值规则**：凡后端可能返回 null 或省略的字段，类型一律 `?: T | null`；页面使用 `?? fallback`。不要用非空断言 `!` 处理接口字段。

### 6.4 Pinia 状态设计

| Store | state | actions | 持久化 | 说明 |
|---|---|---|---|---|
| `auth` | token, expiresAt, user, initialized | login, logout, fetchMe, restore, clear, isStudent/isAdmin | localStorage `ai-interview:auth`（不含密码） | 唯一持久化 store |
| `position` | list, listLoading, lastFetchedAt, current, skillsCache | fetchList(force), fetchDetail(id), fetchSkills(id) | 否 | 2 个岗位，缓存 5 分钟；管理端修改后失效 |
| `interview` | detail, messages, wsState, aiThinking, submitting, finishing, pollTimer, optimistic | load(id), refresh(), start(), submitText(), submitAudio(), finish(), abort(), applyWsEvent(), reset() | 否（草稿单独 localStorage） | 房间页唯一事实源；页面卸载 reset |
| `report` | cache: Map<sessionId, ReportVO>, polling | fetch(id), pollUntilReady(id), retry(id), clear(id) | 否 | 避免返回报告页重复加载 |
| `ui` | sidebarCollapsed, globalOffline, routeLoading | toggleSidebar, setOffline | 否 | 纯 UI |

**auth store 关键点**：

```ts
const STORAGE_KEY = 'ai-interview:auth'
interface PersistedAuth { token: string; expiresAt: number; user: UserVO }

async function login(username: string, password: string) {
  const res = await api.login({ username, password })
  token.value = res.token
  expiresAt.value = res.expiresAt
  user.value = res.user
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ token: res.token, expiresAt: res.expiresAt, user: res.user }))
}
function clear() {
  token.value = ''; expiresAt.value = 0; user.value = null
  localStorage.removeItem(STORAGE_KEY)
}
function isExpired() { return !expiresAt.value || Date.now() >= expiresAt.value }
```

- 启动 `restore()`：解析 localStorage（try/catch）→ 过期则 clear → 未过期调 `fetchMe()`；`fetchMe` 失败分 40101（clear）与网络错误（保留，设置 `serverUnavailable`）。
- 登出：先调 `POST /auth/logout`（失败也继续）→ clear → 跳登录。
- 多标签页：监听 `storage` 事件；他页登出则本页清 auth 并跳登录。

### 6.5 Composables 设计

| Composable | 签名/返回 | 职责 | 关键约束 |
|---|---|---|---|
| `useAsync` | `{ data, loading, error, run, reset }` | 统一异步状态，支持 `immediate`、`onSuccess` | 卸载后不写状态（isUnmounted） |
| `usePagination` | `{ page, size, total, records, loading, load, change }` | 分页 + 查询参数同步 | 防止快速翻页竞态（请求序号） |
| `useCountdown` | `{ remain, running, start, sync, stop }` | 倒计时；`sync(seconds)` 用服务端值 | 使用 `setInterval` 1s + visibilitychange 校准；卸载清理 |
| `useWebSocket` | `{ state, connect, close, reconnect }` | 原生 WS 封装；指数退避；4401 处理 | 不自动在终态重连；事件回调异常不破坏连接 |
| `useInterviewRoom` | 组合上述 + store | 房间编排：加载、start、提交、轮询、WS 事件、终态 | 所有事件先对账；单一在途请求 |
| `useAudioRecorder` | `{ supported, recording, elapsedMs, blob, start, stop, reset, error }` | MediaRecorder 录制/计时/大小检查 | 停止后释放 MediaStream；重复 start 防护 |
| `useSpeechRecognition` | `{ supported, listening, finalText, interimText, start, stop, reset, error }` | Web Speech 封装、错误映射、自动重启 | 最多重启 3 次；权限拒绝不循环重试 |
| `useEchart` | `{ el, setOption, resize, dispose }` | 初始化/更新/ResizeObserver/销毁 | 组件卸载必须 dispose；option 变更用 `notMerge` 控制 |
| `usePermission` | `{ hasRole, isStudent, isAdmin }` | 角色判断 | 只做 UI 显隐，服务端仍有权限校验 |
| `useUnsavedGuard` | `{ confirmLeave }` | 录音/提交/草稿离开确认 | 与路由守卫/`beforeunload` 配合 |

### 6.6 组件清单（关键 props/emits）

| 组件 | Props | Emits | 说明 |
|---|---|---|---|
| `AppBackdrop` | — | — | 全屏液态玻璃背景（渐变/光斑/网格/颗粒），纯装饰 |
| `GlassPanel` | tone?, hoverable?, padded? | — | 液态玻璃容器；tone=default/strong/subtle/brand，统一 blur/高光/降级 |
| `AppPageHeader` | title, description, backTo? | — | 页面标题/返回/右侧 slot |
| `StatusTag` | status, type? | — | 面试/题目/用户/资源状态统一颜色与文案 |
| `SkillTag` | code, name? | — | 技能标签，tooltip 显示编码 |
| `EmptyState` | title, description?, actionText? | action | 空态插画（CSS/SVG 本地） |
| `ErrorState` | code?, message, traceId?, retryText? | retry | 错误卡片 + 复制 traceId |
| `LoadingSkeleton` | rows, type | — | 列表/卡片/图表骨架 |
| `MessageBubble` | message: MessageVO, pending? | replay | AI/USER 气泡；AUDIO 显示转写 + 回放按钮 |
| `QuestionCard` | content, roundNo, followupIndex, difficulty, skills | — | 当前问题高亮卡 |
| `AnswerComposer` | disabled, maxLength, submitting, mode | submit-text, switch-audio | 文字输入 + 快捷键 + 字数 |
| `AudioRecorder` | disabled, maxMs | recorded(blob, durationMs, pauseCount), error | 录音/重录/计时/大小校验 |
| `TranscriptPanel` | supported, finalText, interimText, editable | update:text | 实时字幕/编辑/错误提示 |
| `TimerBadge` | remainSeconds?, warningThreshold=60 | expire | mm:ss；≤60s 红色 |
| `WsStatusBadge` | state, attempt | reconnect | 实时连接状态 |
| `RoundProgress` | current, planned, followupCount | — | 第 x/N 题、追问 n/3 |
| `FinishConfirmDialog` | modelValue, loading | confirm, cancel | 结束确认 |
| `ScoreRing` | score, size? | — | 总分环（CSS/SVG，非 ECharts） |
| `DimensionBars` | scores: Record<string, number> | — | 维度条 + 色带 |
| `RadarScoreChart` | scores, mode | — | ECharts 雷达；TEXT 5 维 |
| `GrowthLineChart` | series | — | ECharts 多折线 |
| `TrendBarLineChart` | days | — | 看板柱线组合 |
| `EvidenceAccordion` | items: QuestionEvidenceVO[] | replay(audioMessage) | 逐题证据 |
| `JsonArrayEditor` | modelValue: string[], max, placeholder | update:modelValue | 每行一条；去空/去重/限长 |
| `SkillWeightsEditor` | modelValue: SkillItem[] | update:modelValue | 权重和实时校验 |
| `ScoreWeightsEditor` | modelValue: ScoreWeights | update:modelValue | 三组权重实时校验 |
| `PaginationBar` | page, size, total | change | 统一分页 |

### 6.7 样式与设计令牌

`src/styles/tokens.css`（节选）：

```css
:root {
  --color-primary: #2563eb;
  --color-primary-light: #eff6ff;
  --color-success: #16a34a;
  --color-warning: #d97706;
  --color-danger: #dc2626;
  --color-info: #64748b;
  --color-bg: #eef4ff;
  --color-card: #ffffff;
  --color-text: #0f172a;
  --color-text-secondary: #475569;
  --color-border: rgba(148, 163, 184, 0.28);
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-xl: 22px;
  --radius-2xl: 28px;
  --shadow-card: 0 4px 16px rgba(15, 23, 42, 0.08);
  --space-1: 4px; --space-2: 8px; --space-3: 12px;
  --space-4: 16px; --space-5: 24px; --space-6: 32px;

  /* 液态玻璃材质（v1.1） */
  --glass-blur: 20px;
  --glass-blur-strong: 32px;
  --glass-blur-subtle: 12px;
  --glass-saturate: 165%;
  --glass-bg: rgba(255, 255, 255, 0.56);
  --glass-bg-strong: rgba(255, 255, 255, 0.74);
  --glass-bg-subtle: rgba(255, 255, 255, 0.4);
  --glass-bg-fallback: rgba(255, 255, 255, 0.94);
  --glass-border: rgba(255, 255, 255, 0.62);
  --glass-border-strong: rgba(255, 255, 255, 0.86);
  --glass-sheen: linear-gradient(135deg, rgba(255,255,255,.62), rgba(255,255,255,.12) 28%, transparent 52%, rgba(255,255,255,.18));
  --glass-shadow: 0 8px 32px rgba(30,64,175,.1), 0 1px 0 rgba(255,255,255,.55) inset;
  --glass-shadow-lg: 0 24px 70px rgba(15,23,42,.16), 0 2px 0 rgba(255,255,255,.65) inset;
  --glass-radius: 20px;
  --glass-radius-lg: 28px;
}
```

- Element Plus 变量覆盖集中在 `element-overrides.scss`（`--el-color-primary`、输入框/弹窗/下拉/消息的玻璃材质与降级），不逐组件改主题。
- 字体：`"PingFang SC", "Microsoft YaHei", system-ui, -apple-system, sans-serif`；正文 14px，标题 18/22/28px；行高 1.6。
- 卡片统一 `--glass-*` 材质：标准卡圆角 20px、强玻璃 28px；页面背景 `--color-bg` + `AppBackdrop` 光斑；内容最大宽度 1280px（管理端 1440px）。
- **材质降级**：`@supports not (backdrop-filter)` → `--glass-bg-fallback`；`prefers-reduced-transparency` 关闭 blur；`prefers-reduced-motion` 停光斑/过渡；打印强制白底（§8.3.1）。
- **分数色带**：`scoreClass(score)` → `<60 danger`、`60~74 warning`、`75~84 primary`、`≥85 success`；图表使用同一色板。
- 图表色板（7 技能）：`#2563eb #0ea5e9 #14b8a6 #22c55e #f59e0b #f97316 #8b5cf6`；雷达面积用 primary 12% 透明度。

### 6.8 安全与隐私

| 风险 | 措施 |
|---|---|
| XSS | AI/用户内容只用文本插值；禁止 `v-html`；外链 `rel="noopener noreferrer"`；不渲染用户提供的 HTML |
| Token 存储 | localStorage（课程设计取舍），文档明确 XSS 风险；不存密码；退出清除；过期主动清 |
| Token 泄露 | 不打日志、不进 URL（WS query 为后端既定协议，避免在页面标题/错误提示展示）；错误上报不含 Authorization |
| WS 鉴权失效 | close 4401 → 清 auth → 登录页；不无限重连 |
| 越权 | 前端角色守卫 + 后端强制校验；不以前端隐藏代替权限 |
| 上传 | 前端只接受录音生成的 webm；≤10MB、时长≤600s；文件名由浏览器生成，不参与路径 |
| 外链 | 学习资源 URL 仅 http/https；非 http(s) 不渲染为链接；`noopener` |
| 隐私 | 面试回答/音频只在本账号可见；音频回放使用带 Authorization 的 blob，不暴露静态 URL |
| 依赖安全 | 不引入来源不明包；F6 执行 `npm audit --omit=dev` 记录结果（不强制阻塞课程交付） |

### 6.9 可访问性与中文体验

- 表单：`el-form-item` label + `aria-label`；错误信息 `role="alert"`；必填标记。
- 对话区：`aria-live="polite"` 播报新 AI 消息；计时器 `aria-live="off"`（避免每秒打扰），60s 警示用文本 + 图标。
- 键盘：全部按钮可 Tab；输入框 `Ctrl/Cmd+Enter` 提交；弹窗焦点捕获（Element Plus 自带）；Esc 关闭。
- 对比度：正文/背景 ≥ 4.5:1；分数不只靠颜色，配文字等级。
- 中文化：Element Plus `zh-cn` locale；日期 `YYYY-MM-DD HH:mm`；数字千分位不必要；空值统一"—"。
- 错误文案：面向学生，不出现 Java 异常类名；技术细节放"详情"折叠（traceId）。

### 6.10 性能与稳定性

- 路由懒加载；ECharts 页面级动态 import；Element Plus 组件按需。
- 图片/图标本地化；不加载外部字体。
- 列表分页（管理端默认 10/20）；面试消息量小（≤40），无需虚拟列表。
- 轮询在页面隐藏时暂停（`visibilitychange`）；恢复时立即拉一次。
- 所有定时器/WS/ResizeObserver/objectURL 在 `onUnmounted` 清理。
- 请求竞态：翻页/切换岗位用递增 requestId，丢弃旧响应。
- 首屏不做重型图表；报告页图表在数据到达后再初始化。

---

## 7. 关键流程与状态机设计

### 7.1 启动与登录

```mermaid
sequenceDiagram
    participant U as 用户
    participant App as App/路由守卫
    participant S as authStore
    participant API as 后端
    U->>App: 打开任意路由
    App->>S: restore()
    alt 无 token
        App-->>U: /login?redirect=...
    else token 未过期
        S->>API: GET /api/users/me
        alt 成功
            API-->>S: UserVO
            App-->>U: 目标页
        else 40101
            S->>S: clear()
            App-->>U: /login
        else 网络错误
            App-->>U: 目标页 + 离线提示
        end
    else token 已过期
        S->>S: clear()
        App-->>U: /login
    end
```

### 7.2 创建并开始面试

```mermaid
sequenceDiagram
    participant U as 学生
    participant Setup as 设置页
    participant Room as 房间页
    participant API as 后端
    participant WS as WebSocket
    U->>Setup: 选择岗位/模式/难度/轮次/时长
    Setup->>API: POST /api/interviews
    API-->>Setup: InterviewDetailVO(CREATED)
    Setup->>Room: push /interview/{id} (autoStart)
    Room->>WS: connect(?token=)
    Room->>API: POST /interviews/{id}/start
    API-->>Room: MessageVO(首题) + WS QUESTION
    Room->>Room: 对账 detail+messages
    Room-->>U: 显示首题/轮次/倒计时
```

- WS 连接失败不阻塞 start；start 返回的首题仍可渲染。
- start 失败：显示"开始失败"卡片 + 重试；用户可中止（abort）后回设置页。
- start 重复（10005）：说明已开始 → 直接刷新进入。

### 7.3 文字作答时序

```mermaid
sequenceDiagram
    participant U as 学生
    participant Room as 房间
    participant API as 后端
    participant WS as WebSocket
    U->>Room: 输入 + Ctrl/Cmd+Enter
    Room->>Room: submitting=true, 乐观气泡(pending)
    Room->>API: POST messages/text {roundNo, content}
    API-->>Room: {accepted:true, roundNo}
    Room->>Room: 气泡 sent, aiThinking=true
    par 实时
        WS-->>Room: FOLLOW_UP / QUESTION / PROGRESS
    and 兜底轮询
        Room->>API: GET detail + messages (1s/2s/3s...)
    end
    Room->>Room: 对账、去重、停止轮询、按新状态渲染
    Room-->>U: 显示追问/下一题/终态
```

**轮询策略**：仅在 `PROCESSING_AI` 或"已受理但未见新 AI 消息"时启用；间隔 1s→2s→3s 上限 3s，总时长 60s；收到任意 QUESTION/FOLLOW_UP/PROGRESS 立即停止并刷新；页面隐藏时暂停。

### 7.4 语音作答时序

```mermaid
sequenceDiagram
    participant U as 学生
    participant Rec as AudioRecorder
    participant ASR as Web Speech
    participant Room as 房间
    participant API as 后端
    U->>Rec: 点击录音
    Rec->>U: 请求麦克风权限
    Rec->>ASR: 开始识别(zh-CN)
    ASR-->>Room: interim/final 文本
    U->>Rec: 停止录音
    Rec-->>Room: blob + durationMs + pauseCount
    ASR-->>Room: 最终转写（可编辑）
    Room->>Room: 校验非空/大小/时长
    Room->>API: POST messages/audio (multipart)
    API-->>Room: {accepted, roundNo}
    API-->>Room: WS TRANSCRIPT {messageId, asrText}
    Room->>Room: 对账消息，进入 AI 等待
```

**离线/不支持降级**：ASR 不可用 → 允许手工填写转写；MediaRecorder 不可用 → AUDIO 禁用并引导 TEXT。

### 7.5 房间前端状态机

```mermaid
stateDiagram-v2
    [*] --> LOADING
    LOADING --> CREATED: detail 加载完成且未开始
    LOADING --> WAIT_ANSWER: WAITING_USER
    LOADING --> AI_THINKING: PROCESSING_AI
    LOADING --> TERMINAL: FINISHED/TIMEOUT/ABORTED
    LOADING --> ERROR: 40401/网络失败
    CREATED --> WAIT_ANSWER: start 成功
    CREATED --> ERROR: start 失败
    WAIT_ANSWER --> SUBMITTING: 点击提交/停止录音
    SUBMITTING --> AI_THINKING: 受理成功
    SUBMITTING --> WAIT_ANSWER: 提交失败（保留输入）
    AI_THINKING --> WAIT_ANSWER: WS 追问/下一题 + 对账
    AI_THINKING --> FINISHING: 点击结束
    AI_THINKING --> TERMINAL: 轮次用尽/超时
    WAIT_ANSWER --> FINISHING: 点击结束
    WAIT_ANSWER --> TERMINAL: 超时/他端结束
    FINISHING --> TERMINAL: finish 受理
    FINISHING --> WAIT_ANSWER: finish 失败（可重试）
    TERMINAL --> [*]: 跳报告/历史
    ERROR --> LOADING: 重试
```

| 前端状态 | 判定依据 | 输入 | 计时 | 主要动作 |
|---|---|---|---|---|
| LOADING | 初始 | 禁用 | 显示— | 拉 detail/messages |
| CREATED | detail.status=CREATED | 禁用 | 无 | start / abort |
| WAIT_ANSWER | WAITING_USER 且有待答 AI | 可用 | 运行 | 文字/语音提交、finish、abort |
| SUBMITTING | 本地 submitting | 禁用 | 运行 | 等待受理 |
| AI_THINKING | PROCESSING_AI 或已受理未回 | 禁用 | 运行 | 轮询、finish、abort |
| FINISHING | 本地 finishing | 禁用 | 停止 | 等待受理/轮询报告 |
| TERMINAL | FINISHED/TIMEOUT/ABORTED | 禁用 | 停止 | 跳报告/历史 |
| ERROR | 加载/操作不可恢复 | 禁用 | 停止 | 重试/返回 |

> 独立正交状态：`wsState`（CONNECTING/OPEN/RECONNECTING/CLOSED）与 `audioState`（IDLE/REQUESTING/RECORDING/STOPPED/DENIED）。它们不改变主状态，只在 UI 上叠加提示。

### 7.6 WS 连接生命周期

```text
mount
  → buildWsUrl(sessionId, token)
  → new WebSocket(url)
  → onopen: state=OPEN, attempt=0, 立即拉一次 detail+messages（补漏）
  → onmessage: JSON.parse → 校验 type/payload → 触发对应 handler → 对账
  → onclose:
      code=4401 → 清 auth → 登录页（不重连）
      terminal 状态 → 不重连
      其他 → state=RECONNECTING；退避 1/2/4/8/15s，最多 8 次
              → 成功：拉 detail+messages 对账
              → 失败：显示"实时连接不可用，已切换轮询"；用户可手动重连
  → unmount: 主动 close(1000)，清理重连定时器
```

- URL：`ws(s)://<host>/ws/interviews/{id}?token=${encodeURIComponent(token)}`；dev 用 Vite 代理同源 `/ws`。
- 不做客户端 ping；浏览器自动回 PONG。
- 事件解析容错：非法 JSON/未知 type 只告警，不影响页面。
- 事件处理异常：`try/catch` 包住，避免一个事件打断后续。

### 7.7 报告就绪、轮询与重试

```text
进入报告页
  → GET report
      code=0 → 渲染
      code=10004 → 显示"报告生成中"骨架
          → 轮询 GET report：1s、2s、3s、3s… 最多 60s
          → 同时监听 REPORT_READY（若 WS 仍连）
          → 成功 → 渲染
          → 60s 仍未就绪 → 显示"生成较慢/可能失败"
              → 按钮"重新生成" → POST finish（幂等）
              → 继续轮询（最多 2 轮）
      code=40401 → 404 空态
      code=其他 → 错误卡 + 重试
```

- **避免重复 finish**：页面级 `retrying` 锁；一次失败后才能再点。
- **ABORTED**：不调用 finish；直接展示"已中止，无报告"。
- **TIMEOUT**：可生成报告；按 10004 流程轮询。
- 轮询在页面隐藏时暂停，恢复时立即请求一次；离开页面 abort 请求。

### 7.8 终态处理

| 终态 | 触发 | UI | 路由 |
|---|---|---|---|
| FINISHED | finish / 轮次用尽 | "面试已结束，报告生成中" | 报告页（可先显示生成中） |
| TIMEOUT | 倒计时到期 | "时间到，系统已自动收卷" | 报告页 |
| ABORTED | 用户中止 | "已中止，不生成报告" | 历史页 |
| 40401 | 记录不存在/无权限 | 空态 | 历史/首页 |

终态统一动作：停计时、停轮询、关 WS、禁输入、清理录音/草稿、清除 `beforeunload`。

### 7.9 刷新与断线恢复

| 场景 | 恢复动作 |
|---|---|
| 房间刷新 | detail+messages 重建；start 不重复（status != CREATED）；倒计时取 remainSeconds；录音丢失需重录 |
| WS 断开 | 保持 REST 轮询；重连后对账；消息按 id 去重 |
| 网络断开/恢复 | 顶部离线条；恢复后重新拉 detail+messages；未提交草稿保留 |
| 令牌过期 | 401/4401 → 清 auth → 登录（带回跳） |
| 他端 finish/abort | WS PROGRESS 或轮询发现 → 终态跳转 |
| 后端重启（定时器恢复） | detail 的 remainSeconds 可能变化；以服务端为准；超界会话会被 TIMEOUT |

### 7.10 管理端编辑冲突（40003）

```text
编辑题目（携带 version）
  → 保存 → 40003
  → 提示"数据已被他人修改"
  → 重新 GET 该题最新数据
  → 弹 diff/确认框（至少展示"最新版本号/修改时间"）
  → 用户选择：
       [用我的内容覆盖] → 携带最新 version 重新提交
       [放弃我的修改]   → 关闭弹窗，表格刷新
  → 不静默丢弃用户输入
```

### 7.11 轮次算法（再次强调）

- 提交轮次 = `detail.currentRound`（不是消息数、不是 UI 计数）。
- 可作答 = `status === 'WAITING_USER'` 且当前轮 AI 消息数 > USER 消息数（且最后一条是 AI）。
- 进程状态 `PROCESSING_AI` 期间永远不可作答。
- 追问不改变 `currentRound`；换题才 +1。
- FINISHED 后 `currentRound = plannedRounds`，进度显示 N/N。

---

## 8. UI/UX 与视觉规范

### 8.1 设计原则

1. **一条主线**：学生端任何页面都能回答"我现在该做什么"——首页给 CTA，岗位给能力，房间给问题，报告给结论，推荐给下一步。
2. **状态可见**：加载/提交中/AI 思考/实时断开/报告生成/录音中，全部有明确文案与视觉，不用"转圈到死"。
3. **分数可解释**：每个总分都能下钻到维度、题目、得分点、证据句、理由；颜色只是辅助。
4. **低干扰面试**：房间专注模式，弱化导航；主问题/输入区视觉权重最高；倒计时与网络状态常驻但不喧宾夺主。
5. **不假装实时**：WS 断开明确提示"使用轮询"；报告未就绪明确说"生成中"。
6. **演示友好**：1440×900 首屏完整；字号不小于 13px；图表在投影下可读；关键按钮高对比。

### 8.2 布局栅格与页面骨架

| 断点 | 布局 |
|---|---|
| ≥1280px | 侧栏 220px + 内容区最大 1280px；房间两栏（对话 60% / 作答 40%）；卡片 3~4 列 |
| 1024~1279px | 侧栏可折叠 64px；房间两栏（55/45）；卡片 2~3 列 |
| 768~1023px | 侧栏抽屉；房间单列（作答吸底）；表格横向滚动 |
| <768px | 学生端单列；管理端仅提示"建议桌面端使用"（不阻塞） |

- 页面统一：`AppPageHeader`（标题 + 描述 + 操作）→ 内容卡片 → 底部留白 32px。
- 卡片内边距 24px；卡片间距 16px；表单 label 右对齐/顶部（移动端顶部）。
- 表格：斑马纹弱化、固定表头、操作列右对齐；长文本 `show-overflow-tooltip`。

### 8.3 视觉令牌与组件风格

- 主色 `#2563eb`（与现有占位页品牌一致）；成功/警告/危险见 §6.7。
- 登录页品牌区使用低饱和蓝渐变 + 细网格；不使用外部插画；v1.1 起叠加液态玻璃材质。
- 按钮：主按钮实心蓝、次按钮玻璃描边、危险操作红色描边；禁用降低透明度并禁用 hover。
- 标签：状态用 Element Plus `el-tag` 的 `type` 映射；技能用浅蓝底圆角标签（玻璃浮层内保持白字/深字对比）。
- 图标：Element Plus 图标，尺寸 16/18/24；不引第三方图标库。
- 空态：本地 SVG + 一句行动建议 + CTA。
- **视觉基线**：F2~F5 新增页面统一复用 §8.3.1 的液态玻璃层，不另起一套主题。

#### 8.3.1 液态玻璃视觉规范（v1.1）

> 目标：用“半透明材质 + 背景模糊 + 高光描边 + 多层阴影”建立统一的轻盈质感，同时保证 1440×900 投影可读、1024 宽可用、低端机不卡顿、打印不变形。**纯 CSS 实现，不引入玻璃拟态库或外部图片。**

> 参考实现截图（F1 已交付）：[登录页](../ai-interview/frontend/docs/screenshots/f1/01-login.png)、[学生端](../ai-interview/frontend/docs/screenshots/f1/03-student-home.png)、[管理端](../ai-interview/frontend/docs/screenshots/f1/06-admin-dashboard.png)、[403](../ai-interview/frontend/docs/screenshots/f1/04-forbidden-403.png)。

**三层结构（从底到顶）**

| 层 | 实现 | 说明 |
|---|---|---|
| 背景层 | `AppBackdrop.vue` + `styles/tokens.css` | 固定全屏：柔和渐变 + 4 个模糊光斑（蓝/紫/青/琥珀）+ 48px 细网格 + 颗粒；光斑缓慢漂移，`prefers-reduced-motion` 停止 |
| 材质层 | `GlassPanel.vue`、`.card`、`.glass-surface` | `backdrop-filter: blur(20~32px) saturate(165%)`；半透明白 + 斜向高光（`--glass-sheen`）+ 内外高光描边 + 双层阴影 |
| 内容层 | 页面/表单/图表/表格 | 文本保持深色高对比；不把正文直接放在纯透明背景上 |

**材质等级**

| 等级 | Token | 典型用途 |
|---|---|---|
| 强玻璃 | `--glass-bg-strong` + `--glass-blur-strong` | 登录/注册卡片、403/404、确认弹窗、下拉浮层、MessageBox |
| 标准玻璃 | `--glass-bg` + `--glass-blur` | 业务卡片、报告区块、空态/错误态、岗位卡 |
| 弱玻璃 | `--glass-bg-subtle` + `--glass-blur-subtle` | 标签底、提示条、骨架屏、次级信息块、演示账号框 |

**应用范围**

- 应用外壳：侧栏、顶栏、移动端抽屉遮罩使用玻璃；内容区保持透明，让背景光斑透出。
- 认证页：左侧品牌区为深蓝半透明玻璃 + 镜面高光；右侧表单卡片使用强玻璃；演示账号框/错误提示/验证码区域使用弱玻璃。
- 通用组件：`ErrorState`、`EmptyState`、`LoadingSkeleton`、`TraceIdText`、403/404 卡片、占位卡片全部使用玻璃材质。
- Element Plus：`el-card`、`el-input`、`el-select`、默认按钮、`el-dialog`、`el-message-box`、`el-message`、下拉/选择浮层统一玻璃化；主按钮保留实心蓝渐变以维持操作层级。
- 图表：容器用玻璃，但 ECharts tooltip 必须使用不透明白底 + 阴影（投影可读优先），不在 canvas 内做半透明大面积叠加。
- 表格：表头/悬浮行用弱玻璃或半透明白；表格主体保持可扫读，不把每一行做成玻璃卡片。

**可读性与无障碍（硬约束）**

- 正文与玻璃底对比度 ≥ 4.5:1；主按钮文字保持白色；不使用纯透明背景承载长文本。
- 支持 `@supports not (backdrop-filter: blur(1px))` 回退：降级为 `--glass-bg-fallback`（约 94% 白），不得出现“透明到看不清”。
- 支持 `prefers-reduced-transparency: reduce`：关闭 blur、提升不透明度。
- 支持 `prefers-reduced-motion: reduce`：关闭光斑漂移、页面过渡与 hover 位移。
- 打印：`print.scss` 强制白底、关闭 blur、隐藏 `AppBackdrop` 与导航（见 §8.7）。
- 键盘焦点仍使用 2px 主色 outline；玻璃边框不能替代 focus 样式。

**性能约束**

- 固定模糊层数量：背景光斑 4 个、侧栏/顶栏各 1 个、每个卡片 1 个；禁止在长列表每一行叠加 `backdrop-filter`。
- 模糊半径上限 32px；移动端光斑缩小、模糊降至 56px；避免大面积 `filter: blur()` 动画之外的实时滤镜。
- 动画只作用于 `transform`，不触发布局；复杂页面（报告/房间/管理表格）分块渲染。
- 首屏不新增图片/字体/依赖；玻璃材质全部由 CSS 变量与渐变实现，gzip 预算不因视觉升级放宽。

**禁止项**

- 禁止外部玻璃拟态库、CDN 图片、视频背景、canvas 噪声贴图。
- 禁止在正文、代码块、表格数据区使用强透明底。
- 禁止用玻璃效果替代加载态/错误态/禁用态；状态必须仍有文字和图标。
- 禁止深色模式/主题切换（本期 Out of Scope）。

### 8.4 图表规范

| 图表 | 页面 | 规格 |
|---|---|---|
| 雷达图 | 报告 | 5/6 轴；max=100；splitNumber=4；面积 12% 透明；轴标签中文；TEXT 不显示 voice 轴并加说明 |
| 总分环 | 报告 | CSS conic-gradient 或 SVG；中心大字分数 + 等级；不使用 ECharts（更轻） |
| 维度条 | 报告 | 水平条 + 数值 + 色带；按分数排序或固定顺序（固定顺序便于对比） |
| 成长折线 | 成长 | x=日期（time 轴），y=0~100；每技能一条；legend 可滚动；空点不连（`connectNulls:false`） |
| 看板柱线 | 管理看板 | 柱=场次（左轴），线=平均分（右轴 0~100）；null 分不断裂柱；tooltip 同时显示 |
| 岗位权重条 | 岗位详情 | 横向条或简单表格；权重百分比标签；不额外引图表 |

- 图表统一：字体 12px、轴线 `#e2e8f0`、tooltip 白底阴影（不使用玻璃，保证投影可读）、动画 300ms；无数据时显示 `EmptyState` 而非空图；图表容器可使用玻璃材质（见 §8.3.1）。
- 图表容器固定高度（雷达 320px、折线 360px、看板 320px），避免布局抖动。
- 响应式 resize 用 `ResizeObserver`；侧栏折叠/窗口变化触发 `chart.resize()`。

### 8.5 状态与反馈规范

| 状态 | 展示 |
|---|---|
| 首次加载 | 骨架屏（列表 5 行/卡片 3 个/图表矩形） |
| 局部刷新 | 保留旧数据 + 顶部细进度条/按钮 loading，不整页闪白 |
| 空数据 | `EmptyState`（标题 + 说明 + 主 CTA） |
| 可恢复错误 | `ErrorState`（message + traceId + 重试） |
| 成功 | `ElMessage.success`（≤3s）；表单保存后局部回显 |
| 提交中 | 按钮 loading + 文案（"提交中…"/"生成报告中…"） |
| 离线 | 顶部固定条"网络已断开，部分功能不可用" + 重连后自动消失 |
| 限流 | 登录页倒计时；其他页面"操作过于频繁，请稍后再试" |
| 危险操作 | 二次确认；确认按钮红色；默认焦点在"取消" |

### 8.6 面试房间交互细节

- 新 AI 消息到达：自动滚动到底部（若用户已手动上滚超过 120px，则不强制，显示"有新消息 ↓"按钮）。
- 输入框：聚焦时显示快捷键提示；字数接近 5000 变橙；提交后清空并保留焦点。
- 录音：按钮变为"停止（00:12）"+ 脉冲红点；录音中禁用文字提交；停止后显示波形占位/时长/大小 + 转写编辑 + "重新录制"。
- 倒计时：≤60s 红色 + 数字加粗；到 0 显示"时间到"。
- 网络：WS 断开显示黄色徽标"实时连接断开，已切换轮询"；恢复变绿 2s 后淡出。
- 结束按钮：房间右下固定；确认弹窗列出"已作答 x 题 / 当前第 y 题"。
- 移动端：作答区吸底，键盘弹起时输入框可见；录音按钮 ≥44px。
- 无障碍：AI 消息 `aria-live`；录音状态文字化；快捷键说明可读。

### 8.7 打印与截图

- `print.scss`：隐藏侧栏/顶栏/`AppBackdrop`/按钮；报告宽度 100%；卡片避免跨页（`break-inside: avoid`）；雷达图/分数环使用 `print-color-adjust: exact`。
- 打印强制关闭液态玻璃：`background:#fff`、`backdrop-filter:none`、`box-shadow:none`、隐藏高光伪元素，避免灰底、模糊和色块跨页（§8.3.1）。
- 课程报告截图清单（F6）：登录、岗位详情、面试房间（含追问）、报告雷达与逐题证据、成长曲线、推荐、管理看板、题库编辑。
- 截图统一 1440×900、浏览器缩放 100%、演示账号数据。

### 8.8 性能感知

- 路由切换显示顶部进度条（≤300ms）；骨架屏避免布局跳动。
- 图表/管理端大表格按需加载；报告页先显示分数区，再渲染证据列表（分页/折叠）。
- 图片/图标不阻塞首屏；`font-display: swap` 不适用（系统字体）。
- 首屏 JS gzip ≤300KB；任何页面 3G Fast 下可交互 < 3s（本地环境不作为硬指标，但需避免明显卡顿）。

---

## 9. 测试计划

### 9.1 测试分层与工具

| 层级 | 工具 | 范围 | 运行时机 | 目标 |
|---|---|---|---|---|
| 类型检查 | `vue-tsc --noEmit` | 全量 TS/Vue | 每次提交/CI | 0 error |
| 静态检查 | ESLint + Prettier | `src/**`、`e2e/**` | 每次提交 | 0 error/0 warning |
| 单元测试 | Vitest + jsdom | stores/composables/utils/api 包装 | 每次提交 | 行 ≥70%、分支 ≥60%（核心目录） |
| 组件测试 | Vitest + @vue/test-utils | 关键组件交互/渲染 | 每次提交 | 每个关键组件 ≥2 用例 |
| 契约测试 | Playwright APIRequest / Node fetch | 39 接口关键 shape | F6、联调后 | 字段/错误码不漂移 |
| E2E | Playwright（Chromium） | 学生/管理端主链路 | 每日/F6 | 至少 6 条全绿 |
| 手工验收 | 检查清单 + 截图 | 方案 §19/§20 | F6 | 全部勾选 |

### 9.2 单元测试清单（Vitest）

| 模块 | 用例（至少） |
|---|---|
| `utils/format` | 日期格式化、时长 mm:ss、分数 toFixed、null → "—"、枚举标签 |
| `utils/score` | 色带边界 59/60/74/75/84/85、未知维度、缺失 voice |
| `utils/authSession` | 写入/读取/清理/过期判断/损坏 JSON 容错 |
| `api/http` | code=0 返回 data；code!=0 抛 BizError；HTTP 401 触发事件；HTTP 400 解析 message；blob 分支；网络错误 → -1 |
| `stores/auth` | login 持久化、restore 过期清除、fetchMe 401 清除、logout 失败仍清、isExpired 边界 |
| `stores/interview` | load 合并、refresh 去重、applyWsEvent 各类、reset 清理、提交锁 |
| `composables/useCountdown` | 每秒递减、sync 校准、到 0 停止、stop 清理、visibilitychange |
| `composables/useWebSocket` | 正常连接、4401 不重连、退避重连、最大次数、事件解析容错、卸载关闭 |
| `composables/useAudioRecorder` | 能力检测、开始/停止、MIME 选择、大小超限、权限拒绝、重复 start 防护 |
| `composables/useSpeechRecognition` | final/interim 累积、error 映射、onend 重启上限、权限拒绝不重启、离线提示 |
| `composables/usePagination` | 翻页、size 变更、竞态丢弃、URL 同步 |
| `composables/useAsync` | loading/error/data、卸载不写状态、reset |
| `utils/round` | awaitingAnswer 算法、currentRound 0-based、FINISHED=plannedRounds、无消息边界 |
| `utils/weights` | 三组和校验、±0.01 容差、缺失字段、技能权重和校验 |
| `utils/demoPath`（如实现） | `nextInterviewSuggested` 解析容错 |

### 9.3 组件测试清单

| 组件 | 用例 |
|---|---|
| `MessageBubble` | AI/USER 样式；AUDIO 显示转写与回放按钮；pending 状态；长文本换行 |
| `AnswerComposer` | 字数限制、空内容禁用、Ctrl/Cmd+Enter、IME 组合不提交、提交中禁用 |
| `AudioRecorder` | 不支持提示、录音按钮状态、计时、停止回调、错误提示 |
| `TranscriptPanel` | interim/final 展示、编辑、空态、ASR 错误提示 |
| `TimerBadge` | mm:ss、≤60s 红色、null 不显示 |
| `WsStatusBadge` | 各状态文案/颜色、重连按钮 |
| `RoundProgress` | 第 x/N、追问 n/3、完成态 N/N |
| `RadarScoreChart` | TEXT 5 轴、AUDIO 6 轴、空数据占位（mock ECharts） |
| `EvidenceAccordion` | 展开/折叠、空证据、缺失点/得分点、回放事件 |
| `SkillWeightsEditor` | 增删行、权重和提示、重复 code 提示 |
| `ScoreWeightsEditor` | 三组和、缺失字段禁用保存、重置 |
| `StatusTag` | 所有枚举文案/类型 |
| `EmptyState/ErrorState` | 插槽/按钮/复制 traceId |

### 9.4 E2E 场景（Playwright，Chromium）

| ID | 场景 | 关键断言 | 数据策略 |
|---|---|---|---|
| E2E-01 | 登录与角色跳转 | demo01 → `/home`；admin → `/admin/dashboard`；错误密码 → 10002；登出回登录 | 种子账号 |
| E2E-02 | 文字面试完整闭环 | 选 Java 岗 → 设置 5 轮 → 房间 → 完成 5 轮（含追问）→ 自动/手动 finish → 报告可见总分/雷达/证据 | 用 API 预注册独立学生，UI 完整走一场（Mock 模式响应快） |
| E2E-03 | 追问与轮次推进 | 每轮追问 ≤3；轮次从 1/5 推进到 5/5；不串轮；重复提交被禁用 | 同上；答案用短文本触发追问 |
| E2E-04 | 语音回答 | 伪造麦克风 + 注入 SpeechRecognition stub → 录音 → 转写 → 提交 → 报告含 voice；回放按钮可用 | Playwright `--use-fake-device-for-media-stream` / `--use-fake-ui-for-media-stream`；或走"手工转写"路径 |
| E2E-05 | 报告未就绪与重试 | 修改/模拟 report 第一次 10004 → 页面轮询 → 恢复；或直接 finish 后进入报告页验证"生成中→完成" | 后端 Mock 报告通常 1~3s；用 API 立即进入报告页覆盖 |
| E2E-06 | 历史/成长/推荐 | 两场后面试后：历史分页；成长曲线有点；推荐 Top3 + 资源 + 错题 | API 创建/完成两场（或复用 E2E-02 数据） |
| E2E-07 | 管理端题库 CRUD | 新增题 → 列表可见 → 编辑（version+1）→ 旧 version 40003 提示 → 逻辑删 | admin；创建 `E2E-` 前缀题目，最后删除 |
| E2E-08 | 管理端岗位/权重/资源/用户 | 新建岗位 → 学生端可见；权重组和校验；资源下架；用户启停（不自禁用） | admin；创建 `E2E-` 前缀数据 |
| E2E-09 | 权限与登录失效 | 学生访问 `/admin` → 403；未登录访问 `/home` → 登录；token 失效 → 登录并回跳 | 两个 storageState |
| E2E-10 | WS 断开/轮询恢复 | 拦截 WS 关闭 → 页面提示"已切换轮询" → 仍能完成一轮 | 通过 `page.route`/CDP 模拟；不依赖网络抖动 |

**E2E 规则**：

- 使用 `data-testid` 选择器（`data-testid="answer-input"` 等），不依赖文案/层级；文案变化不破坏测试。
- 每个用例独立创建用户（`e2e_<timestamp>_<rand>`），避免数据污染；管理端数据用 `E2E-` 前缀并在用例结束尽力清理（题目逻辑删、资源下架、岗位停用）。
- 登录限流（5 次/分/IP）：E2E 尽量复用 `storageState`；需要多次登录时，测试环境用环境变量 `APP_SECURITY_LOGIN_RATE_LIMIT_PER_MINUTE=1000` 启动后端（Spring 宽松绑定），并在 README 注明。
- 后端必须在跑：`globalSetup` 请求 `/actuator/health`，失败给出明确提示（不要静默 mock）。
- E2E 超时：单用例 180s；面试 Mock 模式每轮 1~2s；报告轮询最多 60s。
- 截图/视频：失败时保留 trace；F6 生成成功截图供报告使用。

### 9.5 契约检查（F6-3）

独立脚本 `scripts/contract-check.mjs`（Node 22 fetch，不引入额外依赖）：

1. 登录 demo01 → 断言 `code=0`、`data.token` 为 string、`data.user.role=STUDENT`。
2. `GET positions` → 数组、`defaultRounds` 为 number。
3. `GET positions/1/skills` → 数组、每项 `skillCode/weight`。
4. `GET history?page=1&size=1` → `{records,total,size,current,pages}`。
5. `GET growth?positionId=1`、`GET recommendations?positionId=1` → 数组与关键字段。
6. 管理端登录 → dashboard 关键字段、positions 返回 skills/scoreWeights、questions Page、resources Page、users Page。
7. 错误码：错误密码 → 10002；无 token 访问 `/api/users/me` → HTTP 401/code 40101；学生访问 admin → HTTP 403/code 40301。
8. 输出 JSON 报告 `artifacts/contract-check.json`；任一断言失败退出码 1。

> 目的：后端 DTO 若变更（课程设计后期可能微调），前端能快速定位，而不是页面运行时才发现字段变空。

### 9.6 手工验收清单（F6 逐项勾选）

- [ ] 1440×900 下学生端 12 页无横向滚动、无遮挡。
- [ ] 登录/注册/改资料/登出全通；刷新保持登录。
- [ ] 两个岗位均能创建并完成一场文字面试。
- [ ] AUDIO 面试可录音、转写、提交、回放、报告有 voice。
- [ ] 每轮 AI 追问可读；追问计数正确；3 次追问后换题。
- [ ] 结束/中止/超时/刷新恢复/WS 断线 均符合 §5.7。
- [ ] 报告分数/雷达/五要素/逐题证据/推荐均可见；10004 可恢复。
- [ ] 历史分页/状态操作；成长曲线/推荐数据正确。
- [ ] 管理端 7 页 CRUD/校验/分页/权限全通。
- [ ] 403/404/网络错误/空数据 均有合理 UI。
- [ ] 断网（离线栈）下文字模式完整可演示；语音手工转写可提交。
- [ ] `npm run verify` 全绿；构建产物可从 Nginx 正常打开。
- [ ] 打印报告版式正常（可选）。
- [ ] 无 console error（除已知浏览器警告）；无未捕获 Promise。

### 9.7 测试数据与隔离

| 类型 | 策略 |
|---|---|
| 学生 E2E 用户 | 每次注册 `e2e_<timestamp>_<rand>`；通过 API 注册后写入 storageState |
| 管理端 | 使用种子 admin；测试数据统一 `E2E-` 前缀；结束后逻辑删/下架/停用 |
| 面试数据 | E2E 自建会话，不修改 `demo01` 的演示数据；演示前保留 demo01 的历史/成长数据 |
| 岗位 | 测试新建岗位用 `E2E_` code；结束停用；**不修改种子 2 岗位的题库/权重** |
| 数据库 | 测试库/开发库分离（后端已有 `ai_interview_test`）；E2E 对 dev 库有写操作，需在计划中声明并清理 |
| 演示前 | 停用 `ACC_*`/`E2E_*` 残留岗位；确认 `GET /api/positions` 只剩 2 个种子岗位 |

### 9.8 浏览器与兼容性测试

| 项 | 范围 |
|---|---|
| Chromium（Playwright） | 全量 E2E |
| Chrome 桌面（手工） | 文字 + 语音 + 录音 + 回放 + 打印 |
| Edge 桌面（手工） | 文字 + 语音（同 Chromium 内核） |
| Firefox（手工） | 文字流程；确认语音降级提示 |
| Safari（手工，可选） | 登录/文字/报告；语音提示不支持 |
| 分辨率 | 1440×900（主）、1366×768、1920×1080、1024×768 |
| 网络 | 正常、后端不可达、WS 断开、离线栈（Mock） |

### 9.9 质量门禁汇总

```bash
cd ai-interview/frontend
npm run lint          # 0 error/0 warning
npm run type-check    # 0 error
npm run test:coverage # 核心目录行≥70%、分支≥60%
npm run build         # 成功，检查 dist 体积
npm run test:e2e      # ≥6 条全绿
node scripts/contract-check.mjs  # F6
```

> 交付前必须把上述命令与结果写入前端 README/进度记录；E2E 失败时保留 `test-results/` 与 trace。

---

## 10. 工程化、构建与部署

### 10.1 本地联调

```bash
# 终端 A：后端（本机 18080 或 8080）
cd ai-interview/backend
export JAVA_HOME=~/tools/jdk-17.0.20.1+1/Contents/Home
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080

# 终端 B：前端
cd ai-interview/frontend
cp .env.example .env.development.local   # 如需要，填 VITE_PROXY_TARGET
npm run dev
# 浏览器 http://127.0.0.1:5173
```

`vite.config.ts` 关键配置：

```ts
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'node:path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080'
  return {
    plugins: [vue(), Components({ resolvers: [ElementPlusResolver()] })],
    resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
    server: {
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': { target, changeOrigin: true },
        '/ws': { target, ws: true, changeOrigin: true },
      },
    },
    build: {
      target: 'es2020',
      chunkSizeWarningLimit: 800,
      rollupOptions: {
        output: {
          manualChunks: {
            vue: ['vue', 'vue-router', 'pinia'],
            element: ['element-plus'],
            echarts: ['echarts'],
          },
        },
      },
    },
  }
})
```

> 代理的 `/ws` 必须 `ws: true`；生产 Nginx 已经配置 Upgrade，本地 Vite 代理负责开发期同源。

### 10.2 生产构建

```bash
npm ci
npm run build          # 产物 dist/，index.html + assets/
npm run preview        # 本地预览（不含 /api 代理；联调请用 dev 或 Nginx）
```

- `VITE_API_BASE_URL`/`VITE_WS_BASE_URL` 生产留空 → 同源 `/api`、`/ws`。
- 构建时注入的变量在 `import.meta.env`；不要在运行时读取未声明变量。
- `dist/` 不提交（`.gitignore`）；Docker 构建时生成。

### 10.3 Docker 产物链路修复（P9）

现在的链路：

```text
frontend 容器（Dockerfile COPY index.html → 镜像内 /usr/share/nginx/html）→ 退出
nginx 容器（挂载 frontend-dist 空卷 → Docker 用 nginx 镜像默认页初始化）→ 返回 Welcome to nginx!
```

**修复后链路**（F6-4）：

```text
frontend 构建容器（node:20 构建 dist）→ 把 dist 写入挂载的 frontend-dist 卷
nginx 等 frontend 成功退出 → 挂载同一卷 → 返回真正的 Vue 页面
```

`ai-interview/frontend/Dockerfile`（替换）：

```dockerfile
# 前端构建镜像：node:20 多阶段构建，产物交给 frontend-dist 卷
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# 构建期容器：把 dist 写入挂载的 /usr/share/nginx/html（frontend-dist 卷）
FROM alpine:3.20
COPY --from=build /app/dist /opt/frontend-dist
CMD ["sh", "-c", "rm -rf /usr/share/nginx/html/* && cp -r /opt/frontend-dist/. /usr/share/nginx/html/"]
```

`ai-interview/docker-compose.yml` 的 `frontend` 服务增加卷（关键修复）：

```yaml
  frontend:
    build: ./frontend
    volumes:
      - frontend-dist:/usr/share/nginx/html       # 关键：产物写入共享卷（不要 :ro）
    restart: "no"
```

`nginx` 服务保持：

```yaml
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/conf.d/default.conf:ro
      - frontend-dist:/usr/share/nginx/html:ro
    depends_on:
      frontend: { condition: service_completed_successfully }
```

**验收命令（必须验证内容，不只 200）**：

```bash
docker compose up -d --build
curl -s http://127.0.0.1/ | grep -q "AI 模拟面试与能力提升平台" && echo "FRONTEND OK"
docker compose exec nginx sh -c 'ls /usr/share/nginx/html/assets | head'
ASSET=$(curl -s http://127.0.0.1/ | grep -o 'src="/assets/[^"]*"' | head -1 | cut -d'"' -f2)
curl -s -o /dev/null -w '%{http_code}\n' "http://127.0.0.1${ASSET}"   # 期望 200
```

> **离线/网络受限构建策略**：`node:20-alpine` 与 npm registry 可能不可达（本机 Docker Hub 曾超时，P1）。F6 必须在联网时完成 `docker pull node:20-alpine` 与 `docker compose build`；离线演练只做 `docker compose up`，不在断网时 `npm ci`。若现场无法拉取 node 镜像，退路是**宿主机预构建 `dist`**，用 `nginx:1.27-alpine` 直接把 `dist` COPY 进镜像或 bind mount（不依赖 Node 构建），并在计划中记录该退路。

### 10.4 Nginx 静态优化（F6-4 可选但推荐）

在 `ai-interview/nginx/nginx.conf` 的 `server` 内增加：

```nginx
    gzip on;
    gzip_min_length 1024;
    gzip_types text/css application/javascript application/json image/svg+xml;

    location = /index.html {
        add_header Cache-Control "no-cache";
    }
    location /assets/ {
        add_header Cache-Control "public, max-age=604800, immutable";
    }
```

- `/api/` 与 `/ws/` 不加缓存；`client_max_body_size 12m` 保留。
- 若加 `gzip` 后 E2E 无影响；验证 `curl -H 'Accept-Encoding: gzip' -I` 有 `Content-Encoding: gzip`。

### 10.5 正式部署与演示流程

```bash
cd ai-interview
cp .env.example .env
docker compose -f docker-compose.dev.yml stop    # 防双 mysql 共卷
docker compose up -d --build
# 浏览器 http://127.0.0.1
```

演示前检查：

1. `docker compose ps` 五服务状态（frontend 应为 `Exited (0)`，nginx Up）。
2. `curl http://127.0.0.1/` 包含产品名（P9 修复验证）。
3. `/api` 登录、`/ws` 101 升级。
4. `GET /api/positions` 只有 2 个种子岗位（清理 `ACC_*`）。
5. demo01 历史/成长有数据；admin 可登录。
6. 浏览器 Chrome，允许麦克风；准备文字模式兜底。

### 10.6 回滚

- 前端回滚：`git checkout` 回上一版本 + `docker compose up -d --build`；数据库无前端迁移，无数据回滚。
- 部署链路回滚：恢复旧 `frontend` 占位页 Dockerfile 与 compose（不推荐；会回到 nginx 默认页问题）。
- 后端不受影响；前端静态产物与后端 API 版本需通过契约测试确认兼容。

---

## 11. 里程碑与任务分解（F1~F6）

> 状态：**F1 ✅ 已完成（2026-10-17）；F2~F6 ⬜ 未开始**。任务级状态以《进度记录》为唯一事实源；每完成一个任务，按 §14.2 更新进度记录。
> 完成标准（DoD）必须是**可运行验证**，不能只写"页面完成"。

### 11.1 排期建议（单人，6 周）

| 周 | 内容 | 里程碑 |
|---|---|---|
| 第 1 周 | 脚手架、规范、API/类型、登录/注册、布局、权限守卫 | F1 |
| 第 2 周 | 首页、岗位、设置、房间文字闭环、WS、倒计时、finish/abort | F2 |
| 第 3 周 | 报告、历史、图表、轮询/重试 | F3 |
| 第 4 周 | 录音/转写/音频提交/回放、成长、推荐、个人中心 | F4 |
| 第 5 周 | 管理端 7 页 + 管理端测试 | F5 |
| 第 6 周 | 单测/组件/E2E、契约检查、部署修复、文档/答辩/验收 | F6 |

> 双人协作：F1+F2 一人、F3+F4 一人，F5 并行；F6 共同收口，可压缩到 3~4 周。F2 的房间状态机是核心，不建议拆给两人同时改同一文件。

### 11.2 F1 工程基座与认证（3.5 人日）

> 状态：**F1 ✅ 已完成（2026-10-17）**；任务级证据见《进度记录》与 `frontend/docs/F1-验收记录.md`。F2~F6 ⬜。

| 任务 | 内容 | 产出 | 完成标准（DoD） |
|---|---|---|---|
| F1-1 | Vite+Vue3+TS 脚手架、依赖、别名、env、.gitignore | `package.json`、`vite.config.ts`、`tsconfig*.json`、`.env.*` | `npm run dev` 启动 5173；`npm run build` 成功 |
| F1-2 | ESLint/Prettier/vue-tsc/Vitest 配置与 scripts | 配置文件 + scripts | `lint`/`type-check`/`test:unit` 全绿（空用例可先 1 个 smoke） |
| F1-3 | 设计令牌、液态玻璃视觉基座（AppBackdrop/GlassPanel/Element 覆盖）、Auth/Student/Admin 布局、403/404 | `styles/*`、`components/common/AppBackdrop.vue`、`GlassPanel.vue`、`layouts/*`、错误页 | 三种布局可切换；页面无横向滚动；403/404 可达；玻璃层可读且 `@supports`/`prefers-reduced-transparency` 可降级；打印白底 |
| F1-4 | `http.ts` + `authSession.ts` + `types` + 错误映射 + API 模块骨架 | `api/*`、`types/*`、`utils/authSession.ts` | 单测覆盖拦截器；真实登录请求返回 data；401 触发事件 |
| F1-5 | auth store、路由表、守卫、启动 restore、按角色跳转 | `stores/auth.ts`、`router/*` | 刷新保持登录；过期清 token；学生/管理员跳转正确；403 正确 |
| F1-6 | 登录/注册页 + 表单校验 + 错误码处理 | `LoginView`、`RegisterView` | demo01/admin 登录；10001/10002/10003/42901 均有对应 UI；注册→登录 |
| F1-7 | 全局错误处理、离线条、traceId、路由懒加载兜底 | `App.vue`、`utils` | 未捕获错误不白屏；traceId 可复制；懒加载失败提示刷新 |
| F1-8 | API 联调冒烟（Vite 代理） | 手测记录 | 5173 下 `/api` 与 `/ws` 代理通；`GET /me` 成功；无 CORS 报错 |

### 11.3 F2 面试主流程（文字，5.5 人日）

| 任务 | 内容 | 产出 | 完成标准（DoD） |
|---|---|---|---|
| F2-1 | 首页仪表盘 | `HomeView` + 组件 | 新/老用户均正常；最近面试跳转正确；部分接口失败不白屏 |
| F2-2 | 岗位列表/详情 | `PositionListView`、`PositionDetailView` | 2 岗位展示；技能/权重正确；40401 降级；CTA 到设置 |
| F2-3 | 面试设置 | `SetupView`、能力检测 composable | 默认值 clamp 5~8；边界不可越界；创建成功进房间；防重复提交 |
| F2-4 | 房间骨架 + 数据加载 + 轮次算法 | `RoomView`、`useInterviewRoom`、`stores/interview` | CREATED 自动 start；WAIT/THINKING/TERMINAL 渲染正确；刷新恢复 |
| F2-5 | WS 客户端 + 重连 + 对账 | `useWebSocket`、`utils/wsUrl.ts` | QUESTION/FOLLOW_UP/PROGRESS/TIMER 处理正确；4401 跳登录；断线轮询 |
| F2-6 | 文字作答 + 乐观气泡 + 轮询 | `AnswerComposer`、`MessageBubble` | 提交/受理/追问/下一题闭环；无重复消息；失败保留文本；10005 刷新 |
| F2-7 | 倒计时、状态徽标、finish/abort | `TimerBadge`、`WsStatusBadge`、确认弹窗 | 倒计时校准；≤60s 警示；finish/abort 流程与终态正确 |
| F2-8 | 房间边界与 E2E 冒烟 | 测试 + 手工记录 | 刷新/断线/双击/他端结束/超时均正确；E2E 文字主链路通过 |

### 11.4 F3 报告与历史（3.5 人日）

| 任务 | 内容 | 产出 | 完成标准（DoD） |
|---|---|---|---|
| F3-1 | 报告页壳 + 轮询/重试 | `ReportView`、`stores/report` | 10004 轮询自动恢复；重试 finish 幂等；40401 空态 |
| F3-2 | 总分环、雷达、维度条 | `ScoreRing`、`RadarScoreChart`、`DimensionBars` | TEXT 5 维/AUDIO 6 维；色带正确；空维度说明；图表可 resize |
| F3-3 | 逐题证据折叠 | `EvidenceAccordion` | 覆盖率/缺失点/证据/理由正确；空数组降级；默认展开首题 |
| F3-4 | 亮点/不足/建议 + 资源预览 | 报告区块 | 三个列表均有空态；资源预览失败不影响报告；CTA 跳推荐 |
| F3-5 | 历史列表 | `HistoryView`、`PaginationBar` | 分页/状态操作/空态正确；URL 同步 |
| F3-6 | 报告/历史 E2E | `report.spec.ts` | 完整报告断言；10004 路径；历史分页 |

### 11.5 F4 语音 + 成长 + 推荐 + 个人中心（5 人日）

| 任务 | 内容 | 产出 | 完成标准（DoD） |
|---|---|---|---|
| F4-1 | 录音能力检测与 MediaRecorder | `useAudioRecorder`、`AudioRecorder` | 权限/能力检测；录制/停止/重录/计时/大小限制；不泄漏 MediaStream |
| F4-2 | Web Speech 转写 + 降级 | `useSpeechRecognition`、`TranscriptPanel` | final/interim 正确；错误映射；手工转写；离线提示 |
| F4-3 | 音频提交 + 回放 | 房间/报告音频链路 | multipart 正确；TRANSCRIPT 对账；回放 blob 带鉴权；10MB/600s 校验 |
| F4-4 | 成长曲线 | `GrowthView`、`GrowthLineChart` | 多技能曲线；切换岗位；空态/错误；图表 dispose |
| F4-5 | 学习推荐 | `RecommendationView` | Top3/理由/资源/错题/下场建议；空数组降级；外链安全 |
| F4-6 | 个人中心 | `ProfileView` | 修改资料回显；退出登录；不展示密码 |
| F4-7 | 语音/离线 E2E | `audio-flow.spec.ts` | 伪造麦克风或手工转写；报告 voice；离线文字兜底 |

### 11.6 F5 管理端（5.5 人日）

| 任务 | 内容 | 产出 | 完成标准（DoD） |
|---|---|---|---|
| F5-1 | 管理外壳、菜单、路由守卫、权限 | AdminLayout、路由 | 学生访问 403；管理端 7 页可达；刷新保持 |
| F5-2 | 看板 | `DashboardView`、`TrendBarLineChart` | 四卡 + 柱线；null 均分处理；刷新 |
| F5-3 | 题库 CRUD | `QuestionAdminView`、`JsonArrayEditor` | 筛选/分页/新增/编辑/乐观锁/逻辑删全通 |
| F5-4 | 岗位 + 技能权重 + 评分权重 + evaluation 别名 | `PositionAdminView`、`WeightsView`、`SkillWeightsEditor`、`ScoreWeightsEditor` | 技能和/三组和校验；创建/编辑/回读；学生端可见性；evaluation 路由 |
| F5-5 | 学习资源 CRUD | `ResourceAdminView` | 筛选/分页/新增/编辑/下架；技能对齐；状态 |
| F5-6 | 用户管理 | `UserAdminView` | 搜索/分页/启停/自禁用保护 |
| F5-7 | 知识库新增/重建 | `KnowledgeAdminView` | 字段校验/技能多选/成功回显/重建；明确无列表 |
| F5-8 | 管理端 E2E | `admin.spec.ts` | 题库/岗位/资源/用户关键路径；40003 处理 |

### 11.7 F6 质量、部署与验收（3.5 人日）

| 任务 | 内容 | 产出 | 完成标准（DoD） |
|---|---|---|---|
| F6-1 | 单测/组件覆盖率达标 | 测试文件 + 报告 | 核心目录行 ≥70%/分支 ≥60%；`test:coverage` 通过 |
| F6-2 | E2E 全量套件 | `e2e/*` | ≥6 条全绿（建议 10 条）；失败 trace 留存 |
| F6-3 | 契约检查脚本 | `scripts/contract-check.mjs`、`artifacts/contract-check.json` | 39 接口关键 shape/错误码断言全过 |
| F6-4 | Docker 产物卷修复 + gzip | Dockerfile/compose/nginx + 验证记录 | 80 端口页面含产品名；assets 200；gzip 生效；离线栈可启动 |
| F6-5 | 文档/答辩/截图 | `frontend/README.md`、根 README、答辩脚本更新、`docs/screenshots/` | 新人 30 分钟可跑；答辩 15 步 UI 可走；截图齐全 |
| F6-6 | 验收自检与数据清理 | 验收表、清理记录 | §12 全过；`GET /positions` 仅 2 个种子岗位；demo 数据可演示 |

### 11.8 任务依赖图

```mermaid
flowchart LR
    F1[F1 基座/认证] --> F2[F2 文字面试]
    F1 --> F5[F5 管理端]
    F2 --> F3[F3 报告/历史]
    F2 --> F4[F4 语音/成长/推荐]
    F3 --> F4
    F3 --> F5
    F2 --> F6[F6 质量/部署/验收]
    F3 --> F6
    F4 --> F6
    F5 --> F6
```

---

## 12. 验收标准与答辩路径

### 12.1 前端验收标准（25 条）

| # | 标准 | 验证方式 |
|---|---|---|
| 1 | 登录/注册/登出/资料修改全通 | 手工 + E2E-01 |
| 2 | 学生/管理员角色跳转与 403 正确 | 手工 + E2E-09 |
| 3 | 两个岗位均可见，能力模型/权重正确 | 手工 + 契约检查 |
| 4 | 可创建 TEXT 与 AUDIO 会话，参数边界正确 | 手工 + E2E-02/04 |
| 5 | 文字面试可完整走完 5 轮，含追问 | E2E-02/03 |
| 6 | 语音面试可录音/转写/提交/回放 | E2E-04 + 手工 |
| 7 | 倒计时/轮次/追问计数/状态徽标正确 | E2E-03 + 手工 |
| 8 | WS 断线可轮询恢复，刷新可恢复 | E2E-10 + 手工 |
| 9 | finish 异步 + 报告 10004 可自动恢复 | E2E-05 |
| 10 | 报告含总分/维度/雷达/亮点/不足/建议/逐题证据 | E2E-02 + 手工 |
| 11 | AUDIO 报告含 voice 维度；TEXT 不伪造 voice | 手工 + 契约 |
| 12 | 报告可打印/另存 PDF（可选） | 手工 |
| 13 | 历史分页/状态操作正确 | E2E-06 + 手工 |
| 14 | 成长曲线按技能/岗位正确 | E2E-06 + 手工 |
| 15 | 推荐 Top3/资源/错题/下场建议正确 | E2E-06 + 手工 |
| 16 | 管理端看板数字与接口一致 | 手工 + 契约 |
| 17 | 题库 CRUD/逻辑删/40003 全通 | E2E-07 |
| 18 | 岗位/技能/评分权重校验与回读正确 | E2E-08 + 手工 |
| 19 | 学习资源 CRUD/下架正确 | E2E-08 + 手工 |
| 20 | 用户搜索/启停/自禁用保护正确 | E2E-08 + 手工 |
| 21 | 知识库新增/重建与说明一致 | 手工 |
| 22 | 空态/错误态/离线条/限流/404 均有合理 UI | 手工 |
| 23 | `verify` 门禁全绿；构建产物经 Nginx 真实可打开 | 命令 + P9 验证 |
| 24 | 1440×900 无布局问题；报告打印正常 | 手工 + 截图 |
| 25 | 断网 Mock 下文字模式完整可演示 | 离线栈手工 |
| 26 | 液态玻璃视觉体系落地且可读：背景层/玻璃面板/Element Plus 浮层一致；无 backdrop-filter 时降级可读；打印白底无 blur；1440×900 投影下正文对比度达标 | 手工 + 截图；检查 `AppBackdrop`/`GlassPanel`/`element-overrides.scss`/`print.scss` |

### 12.2 对照方案 §20 最终验收标准（前端证据）

| 方案 §20 条目 | 前端落点 |
|---|---|
| 双岗位 | 岗位列表/详情/设置页 |
| 每岗 ≥20 题 | 不直接展示题量；报告题数=5~8；管理端题库可分页查看 |
| 四类题型 | 管理端题库类型筛选/标签 |
| 文字+语音 | 设置页双模式；房间文字/录音；报告 voice |
| ≥3 轮动态追问 | 房间追问气泡 + 追问计数；E2E-03 |
| 大模型参与 | 前端不感知实现；Mock/qwen 均由后端切换；断网演示 |
| 知识检索注入 | 管理端知识库页说明 + 新增；房间无感知（后端行为） |
| 多维报告 | 雷达/维度条/总分 |
| 报告五要素 | 分数/证据/亮点/不足/建议区块 |
| 历史记录 | 历史页 |
| 成长曲线 | 成长页 |
| 短板推荐 | 推荐页 + 报告资源预览 |
| 管理员维护题库/知识库 | 管理端题库/知识库页 |
| 真实大模型/Mock | 前端无感；离线演示走 mock |
| Docker Compose 一键启动 | F6-4 修复后 80 端口真实页面 |

### 12.3 方案 §19 15 步答辩 → UI 路径

| 步 | 演示动作 | 页面/操作 | 讲解要点 |
|---|---|---|---|
| 1 | 学生登录 | `/login` → demo01 | JWT + 角色跳转 |
| 2 | 选择 Java 岗位 | `/positions` → 详情 | 岗位是组卷/评分根 |
| 3 | 查看能力要求 | 岗位详情技能表 | 7 技能权重和=1 |
| 4 | 选择文字/语音 | `/interview/setup` | 模式/难度/轮次/时长约束 |
| 5 | AI 首题 | 房间自动 start | 主问题预写 + 组卷策略 |
| 6 | 学生回答 | 文字/录音提交 | 异步受理 + 关键词/知识检索 |
| 7 | AI 追问 | 气泡/追问计数 | 七条追问规则（后端） |
| 8 | 完成 3~5 轮 | 继续作答/结束 | 轮次推进/自动收卷 |
| 9 | 六维报告 | `/interview/{id}/report` | 加权聚合/总分/voice |
| 10 | 证据/亮点/不足 | 报告逐题折叠 | 分数可解释 |
| 11 | 推荐资源 | `/recommendations` | 短板四规则/资源/错题 |
| 12 | 成长曲线 | `/growth` | 0.7/0.3 折算 |
| 13 | 切换 Web 前端岗位 | 岗位列表 → 新面试 | 岗位隔离 |
| 14 | 管理员后台 | `/admin/questions`、`/admin/knowledge`、`/admin/positions/:id/weights` | ROLE_ADMIN + 乐观锁 + 权重校验 |
| 15 | 断网 Mock | 离线栈 + 文字模式（或手工转写） | 无外网依赖；ASR 需网络，故用文字/手填 |

### 12.4 前端 Definition of Done（每个任务/里程碑）

- 代码通过 `lint + type-check + unit + build`；涉及主链路的任务补 E2E。
- 页面在 1440×900 与 1024×768 下无遮挡/横向滚动（管理端表格允许横向滚动）。
- 加载/空/错误/无权限/离线 至少覆盖该页面会出现的状态。
- 所有接口调用都有失败处理；无未捕获 Promise/console error。
- 关键交互有 `data-testid`，E2E 可稳定复跑。
- 不引入未审批依赖；不提交 token/密码/本地绝对路径。
- 进度记录对应任务勾选 ✅ 并写验证命令/结果。
- 文档（README/页面说明/契约注释）同步更新。

---

## 13. 风险、问题与应对

| ID | 风险/问题 | 概率 | 影响 | 应对 |
|---|---|---|---|---|
| R-F1 | Web Speech API 仅 Chrome/Edge 支持，且依赖网络；离线演示不可用 | 高 | 语音面试无法转写 | 能力检测 + 手工转写兜底；离线演练改用文字模式；答辩设备固定 Chrome + 联网（或提前录屏） |
| R-F2 | MediaRecorder 输出格式因浏览器而异（Safari mp4 不被后端接受） | 中 | 音频上传 40001 | 只支持 webm/opus；不支持时禁用 AUDIO；E2E 用 Chromium fake device |
| R-F3 | 异步受理/WS 丢事件导致 UI 卡在"AI 思考中" | 中 | 无法继续作答 | WS + 轮询双通道；60s 超时提示；刷新/恢复；幂等对账 |
| R-F4 | 报告 10004 持续/生成失败 | 中 | 报告页空白 | 轮询 + finish 重试 + ERROR 提示；后端已有重试入口 |
| R-F5 | WS token 进 query、断线重连风暴 | 中 | 安全/性能 | 4401 不重连；指数退避上限 8 次；终态关闭；不在日志打印 URL token |
| R-F6 | Element Plus + ECharts 体积过大 | 中 | 首屏慢 | 按需导入/路由懒加载/图表独立 chunk；预算门禁 |
| R-F7 | E2E 受登录限流（5 次/分/IP）影响不稳定 | 高 | E2E 偶发失败 | 复用 storageState；测试环境调高限流；每个用例独立用户；失败重试 1 次 |
| R-F8 | **frontend-dist 卷未接产物（P9）** | 已发生 | 80 端口显示 nginx 默认页 | F6-4 增加 volume + 产物写入命令；验证页面**内容**而非仅 200 |
| R-F9 | 开发库测试残留（`ACC_*` 岗位、`acc_*` 用户/题库/资源） | 已发生 | 演示出现异常岗位/数据 | F6 演示前清理/停用；E2E 用 `E2E-` 前缀并清理；设置页 clamp |
| R-F10 | 后端 DTO 后期微调导致前端字段空 | 中 | 页面异常 | 契约测试 + 类型单一来源 + 联调前锁定 commit |
| R-F11 | 后端无知识库列表接口，管理页能力受限 | 确定 | 无法列表/编辑 | 页面明确说明；只做新增/重建；如需列表另立后端任务 |
| R-F12 | 端口/CORS：5174 或直连 18080 可能跨域 | 中 | 登录失败 | 固定 5173 + Vite 代理；README 说明；不依赖后端 CORS |
| R-F13 | Node/npm 镜像网络受限，Docker 构建失败 | 中 | 部署失败 | 提前 pull node:20-alpine、提前 build；退路：宿主机 prebuild dist + nginx COPY |
| R-F14 | 日期无时区，跨时区解析偏差 | 低 | 显示差 8 小时 | 统一按本地时间展示，不做 UTC 转换；不引入时区库 |
| R-F15 | 非 localhost/HTTPS 访问导致麦克风/语音 API 不可用 | 中 | 答辩现场录音失败 | 答辩用 `127.0.0.1`；如需局域网访问，准备 HTTPS 或改文字演示 |
| R-F16 | `defaultRounds=100` 等异常岗位数据 | 已发生 | 设置页越界/后端 40002 | 设置页 clamp 5~8；演示前停用异常岗位；后端仍校验 |
| R-F17 | 打印/PDF 版式在答辩浏览器不一致 | 低 | 截图不美观 | 固定 Chrome 打印；提供屏幕版报告为主 |
| R-F18 | **液态玻璃的 backdrop-filter 在低端机/旧浏览器上卡顿或不可读** | 中 | 首屏掉帧、文字对比不足 | 只对卡片/浮层使用，固定模糊层数量与半径；`@supports` 回退到 94% 白；`prefers-reduced-transparency` 关闭 blur；`prefers-reduced-motion` 停动画；打印强制白底（§8.3.1/§8.7） |

---

## 14. 交付物清单与文档更新规则

### 14.1 前端交付物

| # | 产物 | 路径 | 阶段 | 状态 |
|---|---|---|---|---|
| 1 | 前端开发计划（本文 v1.1） | `开发计划/AI模拟面试与能力提升平台_前端开发计划.md` | F0（计划） | ✅ 已交付（v1.1 液态玻璃视觉规范） |
| 2 | Vue3 工程 | `ai-interview/frontend/` | F1~F5 | 🔧 F1 完成（工程/认证/外壳/玻璃基座），F2~F5 待交付 |
| 3 | 类型/API 层 | `frontend/src/types`、`frontend/src/api` | F1 | ✅ F1 骨架完成（39 REST + 1 WS），F6 契约测试待补 |
| 4 | 学生端 12 页 | `frontend/src/views/{auth,student,interview,growth,user}` | F1~F4 | 🔧 F1 登录/注册完成，其余 F2~F4 |
| 5 | 管理端 7 页 | `frontend/src/views/admin` | F5 | ⬜ |
| 6 | 单元/组件测试 | `frontend/src/**/__tests__` | F1~F5 | 🔧 F1 34 用例；F6 补覆盖率与组件测试 |
| 7 | E2E 套件 | `frontend/e2e` | F6 | ⬜ |
| 8 | 契约检查脚本 | `frontend/scripts/contract-check.mjs` | F6 | ⬜ |
| 9 | Dockerfile + compose 修复 | `frontend/Dockerfile`、`docker-compose.yml` | F6 | ⬜ |
| 10 | Nginx 优化 | `nginx/nginx.conf` | F6 | ⬜ |
| 11 | 前端 README | `frontend/README.md` | F6 | ⬜ |
| 12 | 答辩 UI 路径/脚本更新 | `ai-interview/答辩演示脚本.md` | F6 | ⬜ |
| 13 | 截图 | `frontend/docs/screenshots/f1/`（F1）；F6 补全 | F1/F6 | 🔧 F1 已留 10 张；F6 补答辩全套 |
| 14 | 验收自检 | 进度记录 F6 + 本节 | F6 | ⬜ |

### 14.2 进度记录更新规则

1. 计划文档交付时：在进度记录 §2 产物表追加 #17（本文），在 §3 追加 F1~F6 里程碑表（当前全部 ⬜；任务明细在 F1 开工后按 §11 回填），§4 登记 D31~D34，§5 登记 P9~P11。
2. 每完成一个任务：勾选状态 + 填写完成日期 + 验证命令/结果；不修改本文计划（偏差记入进度记录）。
3. 每个里程碑结束：更新"总体概览"的当前阶段/整体进度；追加文档更新历史。
4. 每次答辩前：更新环境状态与演示数据清理记录。
5. 后端契约变化：先更新 §2/附录 B 类型，再改代码；在进度记录登记偏差。

### 14.3 计划决策（写入进度记录 D31~D34、D38）

| 编号 | 决策 |
|---|---|
| D31 | 前端按方案 §3.1 技术栈实施：Vue 3.4 + TS + Vite 5 + Element Plus + Pinia + Axios + ECharts + 原生 WS + MediaRecorder + Web Speech；不引入替代框架 |
| D32 | 面试交互采用"WS 通知 + REST 对账"模型；提交/结束异步受理；轮次以 `detail.currentRound` 为唯一依据；报告 10004 按正常态轮询 |
| D33 | 管理端知识库按现有契约实现"新增 + 重建索引"；不新增后端列表接口；页面明确能力边界；`/admin/evaluation` 复用评分权重页 |
| D34 | 前端产物部署采用"构建容器写入 frontend-dist 卷"方案修复 P9；离线演练前预构建镜像；语音面试离线不可用时以文字/手工转写兜底 |
| D38 | **液态玻璃视觉体系**：以纯 CSS 实现 `AppBackdrop`（渐变光斑/网格/颗粒）+ `GlassPanel`（blur/saturate/高光/多层阴影）+ Element Plus 玻璃化；F1 完成基座，F2~F5 复用；必须提供 `@supports`、`prefers-reduced-transparency`、`prefers-reduced-motion` 与打印降级；不新增依赖、不引入外部图片 |

### 14.4 计划外但需记录的问题

| 编号 | 问题 | 处理 |
|---|---|---|
| P9 | frontend-dist 卷未接产物，80 端口实际为 nginx 默认页 | F6-4 修复；验收必须 grep 产品名 + 检查 assets 200 |
| P10 | `答辩演示脚本.md` §14 权重请求体为 `weights`，实际为 `scoreWeights` | F6-5 修正脚本；前端按真实契约实现 |
| P11 | dev 库存在 `ACC_*` 岗位（defaultRounds=100）等测试残留 | F6-6 清理/停用；设置页 clamp；E2E 用前缀并清理 |
| P12 | 方案 §11 的 `/admin/evaluation` 无独立后端接口 | D33 复用权重页；不伪造接口 |
| P13 | 方案 §10 管理接口仅 7 个，实际 18 个 | 以前端 §2.2.5 的 18 个为准；文档已在 §2.5 记录 |

---

## 15. 附录

### 附录 A：完整 TypeScript 类型（`src/types`）

> 以 2026-10-17 实际接口返回为准；字段可空规则见 §6.3。后端字段名保持 camelCase，不做重命名。

```ts
// ===== 基础 =====
export interface ApiResult<T> {
  code: number
  message?: string
  data?: T
  traceId?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export type Role = 'STUDENT' | 'ADMIN'
export type InterviewMode = 'TEXT' | 'AUDIO'
export type InterviewStatus =
  | 'CREATED' | 'RUNNING' | 'WAITING_USER' | 'PROCESSING_AI'
  | 'FINISHED' | 'ABORTED' | 'TIMEOUT'
export type QuestionType = 'TECHNICAL' | 'PROJECT' | 'SCENARIO' | 'BEHAVIORAL'
export type QuestionStatus = 'ENABLED' | 'DISABLED'
export type ResourceType = 'ARTICLE' | 'QUESTION' | 'PRACTICE' | 'PROJECT'
export type DimensionKey = 'technical' | 'depth' | 'logic' | 'match' | 'communication' | 'voice'

// ===== 认证 =====
export interface UserVO {
  id: number
  username: string
  realName?: string | null
  role: Role
  major?: string | null
  status: number
}

export interface LoginPayload { username: string; password: string }
export interface LoginResponse { token: string; expiresAt: number; user: UserVO }
export interface RegisterPayload {
  username: string
  password: string
  realName?: string
  major?: string
}
export interface UpdateMePayload { realName?: string; major?: string }

// ===== 岗位 =====
export interface PositionVO {
  id: number
  code: string
  name: string
  description?: string | null
  defaultRounds: number
  skillCount?: number | null
}

export interface SkillVO {
  skillCode: string
  skillName: string
  description?: string | null
  weight: number
  requiredLevel?: number | null
}

// ===== 面试 =====
export interface InterviewDetailVO {
  id: number
  userId: number
  positionId: number
  positionName?: string | null
  mode: InterviewMode
  difficulty: number
  plannedRounds: number
  currentRound: number
  status: InterviewStatus
  totalScore?: number | null
  startedAt?: string | null
  finishedAt?: string | null
  createdAt?: string | null
  remainSeconds?: number | null
}

export interface MessageVO {
  id: number
  sessionId: number
  roundNo: number
  speaker: 'AI' | 'USER'
  inputType?: InterviewMode | null
  contentText: string
  audioPath?: string | null
  audioDurationMs?: number | null
  asrText?: string | null
  questionId?: number | null
  keywords?: string[] | null
  followupIndex?: number | null
  createdAt: string
}

export interface CreateInterviewPayload {
  positionId: number
  mode: InterviewMode
  difficulty: number
  rounds: number
  durationMinutes: number
}
export interface TextAnswerPayload { roundNo: number; content: string }
export interface AudioAnswerPayload {
  file: Blob
  roundNo: number
  asrText: string
  durationMs: number
  pauseCount?: number
}
export interface SubmitAccepted { accepted: boolean; roundNo: number }
export interface FinishAccepted {
  accepted: boolean
  status: InterviewStatus
  reportId: number | null
}

// ===== 报告 =====
export interface ReportVO {
  reportId: number
  sessionId: number
  positionName?: string | null
  mode: InterviewMode
  status: InterviewStatus
  totalScore: number
  dimensionScores: Partial<Record<DimensionKey, number>>
  summary?: string | null
  strengths: string[]
  weaknesses: string[]
  improvementPlan: string[]
  reportText?: string | null
  questionEvidence: QuestionEvidenceVO[]
  createdAt: string
}

export interface QuestionEvidenceVO {
  roundNo: number
  questionId: number
  questionContent: string
  answerExcerpt: string
  difficulty: number
  targetSkills: string[]
  scores: Partial<Record<DimensionKey, number>>
  contentScore: number
  coveredPoints: string[]
  missingPoints: string[]
  evidence: string[]
  reason: string
}

// ===== 成长 =====
export interface HistoryItemVO {
  sessionId: number
  positionId: number
  positionName?: string | null
  mode: InterviewMode
  difficulty: number
  plannedRounds: number
  currentRound: number
  status: InterviewStatus
  totalScore?: number | null
  startedAt?: string | null
  finishedAt?: string | null
}

export interface GrowthPoint { evaluatedAt: string; score: number }
export interface SkillGrowthVO {
  skillCode: string
  skillName: string
  records: GrowthPoint[]
  current: number
}

export interface ResourceVO {
  id: number
  title: string
  resourceType: ResourceType
  contentUrl?: string | null
  difficulty: number
}
export interface RetryQuestionVO {
  questionId: number
  questionContent: string
  missingPoints: string[]
}
export interface RecommendationVO {
  skillCode: string
  skillName: string
  priority: number
  weaknessReasons: string[]
  resources: ResourceVO[]
  practices: ResourceVO[]
  retryQuestions: RetryQuestionVO[]
  nextInterviewSuggested?: string
}

// ===== 管理端 =====
export interface DashboardVO {
  userCount: number
  sessionCount: number
  questionCount: number
  avgScore?: number | null
  recent7Days: Array<{
    date: string
    sessionCount: number
    avgScore?: number | null
  }>
}

export interface SkillItem {
  skillCode: string
  skillName: string
  description?: string | null
  weight: number
  requiredLevel?: number | null
}

export interface DimensionWeights {
  technical?: number | null
  depth?: number | null
  logic?: number | null
  evidence?: number | null
  match?: number | null
  clarity?: number | null
  fluency?: number | null
  pace?: number | null
  confidence?: number | null
  completeness?: number | null
}
export interface TotalWeights {
  content?: number | null
  expression?: number | null
  adaptability?: number | null
  match?: number | null
}
export interface ScoreWeights {
  content?: DimensionWeights | null
  expression?: DimensionWeights | null
  total?: TotalWeights | null
}

export interface PositionAdminVO {
  id: number
  code: string
  name: string
  description?: string | null
  defaultRounds: number
  enabled: number
  skillCount: number
  skills: SkillItem[]
  scoreWeights?: ScoreWeights | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface PositionUpsertPayload {
  code?: string
  name?: string
  description?: string
  defaultRounds?: number
  enabled?: number
  skills?: SkillItem[]
  scoreWeights?: ScoreWeights
}
export interface WeightsUpdatePayload { scoreWeights: ScoreWeights }

export interface QuestionAdminVO {
  id: number
  positionId: number
  type: QuestionType
  content: string
  background?: string | null
  difficulty: number
  skillTags: string[]
  referenceAnswer?: string | null
  keyPoints: string[]
  deductionPoints: string[]
  followupTemplates: string[]
  status: QuestionStatus
  version: number
  createdAt?: string | null
  updatedAt?: string | null
}

export interface QuestionUpsertPayload {
  positionId: number
  type: QuestionType
  content: string
  background?: string
  difficulty: number
  skillTags: string[]
  referenceAnswer?: string
  keyPoints?: string[]
  deductionPoints?: string[]
  followupTemplates?: string[]
  version?: number
}

export interface LearningResource {
  id: number
  positionId: number
  skillCode: string
  title: string
  resourceType: ResourceType
  contentUrl?: string | null
  difficulty: number
  enabled: number
  createdAt?: string | null
}

export interface ResourceUpsertPayload {
  positionId: number
  skillCode: string
  title: string
  resourceType: ResourceType
  contentUrl?: string
  difficulty: number
  enabled?: number
}

export interface KnowledgeChunk {
  id: number
  positionId: number
  title: string
  content: string
  keywords: string
  skillTags: string
  sourceType?: string
  status?: string
  createdAt?: string
}
export interface KnowledgeUpsertPayload {
  positionId: number
  title: string
  content: string
  keywords: string
  skillTags: string
  difficulty?: number
}
```

### 附录 B：WebSocket 类型与 URL

```ts
export type WsEventType = 'QUESTION' | 'FOLLOW_UP' | 'TRANSCRIPT'
  | 'TIMER' | 'PROGRESS' | 'REPORT_READY' | 'ERROR'

export interface WsEnvelope<T = unknown> {
  type: WsEventType
  payload: T
  ts: number
}

export interface QuestionPayload {
  roundNo: number
  followupCount: number
  content: string
  difficulty: number
  targetSkills: string[]
}
export interface TranscriptPayload { messageId: number; asrText: string }
export interface TimerPayload { remainSeconds: number }
export interface ProgressPayload {
  status: InterviewStatus
  currentRound: number
  plannedRounds: number
}
export interface ReportReadyPayload { reportId: number }
export interface ErrorPayload { code: number; message: string }
```

```ts
// src/utils/wsUrl.ts
export function buildWsUrl(sessionId: number, token: string): string {
  const configured = import.meta.env.VITE_WS_BASE_URL as string | undefined
  const base = configured && configured.length > 0
    ? configured.replace(/\/$/, '')
    : `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}`
  return `${base}/ws/interviews/${sessionId}?token=${encodeURIComponent(token)}`
}
```

### 附录 C：环境变量清单

| 变量 | 开发 | 生产 | 说明 |
|---|---|---|---|
| `VITE_APP_TITLE` | AI 模拟面试与能力提升平台 | 同左 | `document.title` 前缀 |
| `VITE_API_BASE_URL` | 空（Vite 代理） | 空（Nginx 同源） | 绝对地址仅在特殊部署时使用 |
| `VITE_WS_BASE_URL` | 空（location 推导） | 空 | 必须与后端 WS 路径匹配 |
| `VITE_PROXY_TARGET` | `http://127.0.0.1:8080`（本机可 18080） | — | 仅 Vite dev 使用 |
| `VITE_ENABLE_AUDIO` | `true` | `true` | 关闭后仅文字模式（演示兜底） |

### 附录 D：契约待确认项（需与后端/答辩文档对齐）

| # | 待确认 | 当前处理 |
|---|---|---|
| Q1 | 是否补充 `GET /api/admin/knowledge` 列表接口 | 不补；页面按现有契约降级；如需补，另立后端任务并更新前端 |
| Q2 | `答辩演示脚本.md` §14 权重 payload 是否修正 | F6-5 修正为 `{"scoreWeights":{...}}`；前端按真实契约 |
| Q3 | `ACC_*` 测试岗位/数据是否在演示前清理 | F6-6 停用/清理；前端 clamp |
| Q4 | TEXT 场报告是否展示第 6 维 | 展示 5 维 + "语音维度仅语音面试采集"说明；不伪造 0 |
| Q5 | 报告接口 `reportText` 是否直接展示 | 以 `summary/strengths/...` 为主，`reportText` 作为"完整报告文本"折叠展示 |
| Q6 | 学习资源外链是否允许非 http(s) | 前端只把 http/https 渲染为链接；其他显示文本 |
| Q7 | 音频回放是否在答辩必须演示 | 计划为 F4 可选增强；若时间紧，保留接口能力但不做 UI 入口，F6 再定 |

### 附录 E：计划一致性自检

| 检查项 | 结果 |
|---|---|
| 方案 §3.1 技术栈全部覆盖 | ✅ Vue3/TS/Vite/Element Plus/Pinia/Axios/ECharts/WS/MediaRecorder/Web Speech |
| 方案 §11 学生端 12 路由 | ✅ §4.2 全部覆盖（另加 403/404） |
| 方案 §11 管理端 7 路由 | ✅ §4.2 覆盖；`/admin/evaluation` 用权重页承接 |
| 方案 §10 REST 接口 | ✅ 39 个（含 18 管理端 + 音频回放） |
| 方案 §10.6 WebSocket | ✅ §2.2.6/§2.4/附录 B |
| 方案 §11 面试房间必需元素 | ✅ §5.7.1 九项 |
| 方案 §16.3 Playwright 流程 | ✅ E2E-02 |
| 方案 §19 15 步答辩 | ✅ §12.3 映射 |
| 方案 §20 验收 15 条 | ✅ §12.2 映射 |
| 后端实际契约差异 | ✅ §2.5 12 条 + 附录 D |
| 液态玻璃视觉体系 | ✅ §6.7 玻璃令牌 + §8.3.1 规范 + F1-3 交付 + §12.1 #26 + R-F18 + D38 |
| 现有部署缺陷 P9 | ✅ §1.4/§10.3/F6-4 |
| 进度记录联动 | ✅ §14.2/§14.3 |

---

> **计划维护**：本文由前端开发会话维护；任务完成状态、偏差与验证证据写入《AI模拟面试与能力提升平台_进度记录.md》，本文只保留计划与契约基线。若后端契约变化，先更新 §2/附录 A/B，再改代码，并在进度记录登记决策/偏差。
>
> 课程：JavaEE 课程设计 · 项目：AI 模拟面试与能力提升平台 · 前端计划 v1.1（2026-10-17 · 液态玻璃视觉升级）
