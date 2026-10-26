# AI模拟面试与能力提升平台 —— 简历分析与个性化面试 接口协议

> 本文档定义"简历上传与分析（F1）+ 个性化面试（F2）"新增/扩展 REST 接口的统一协议，是前后端联调的**唯一契约**。总体设计见《简历分析与个性化面试_总体开发方案》（下称"本方案"），任务分解见《简历分析与个性化面试_后端开发计划》。
>
> 协议完全沿用平台既有约定（认证、`Result` 包装、错误码语义、分页与时间格式），前端无需改造 HTTP 层。既有 39 个 REST 接口中，仅 #9/#14 两个接口的请求或响应有**向后兼容的增量字段**，其余不变。

---

## 目录

1. [通用约定](#1-通用约定)
2. [错误码](#2-错误码)
3. [简历管理接口](#3-简历管理接口)
4. [个性化面试接口扩展](#4-个性化面试接口扩展)
5. [状态与轮询契约](#5-状态与轮询契约)

---

## 1. 通用约定

| 项 | 约定 |
|---|---|
| Base URL | `http://<host>:8080/api`（无 context-path；正式环境经 Nginx 80 入口） |
| 认证 | 除登录/注册外全部接口需 `Authorization: Bearer <JWT>`；无效/过期 → HTTP 401 + `code=40101` |
| 角色 | 简历接口仅需登录（STUDENT/ADMIN 均可，ADMIN 亦按本人数据处理）；`/api/admin/**` 规则不变，本期不新增管理端接口 |
| 响应包装 | 所有接口返回 `Result`：`{"code":0,"message":"success","data":{...},"traceId":"..."}` |
| 失败语义 | **业务失败 HTTP 200 + `code≠0`**；协议级错误（参数/401/403/404/415/429/500）使用真实 HTTP 状态码 |
| 空值 | `data` 内字段 `@JsonInclude(NON_NULL)`：字段为 null 时**可能不出现在 JSON 中**，前端需容错 |
| 命名 | 字段 camelCase；枚举值大写（如 `ANALYZED`、`HIGH`） |
| 时间 | ISO 格式 `2026-10-26T14:30:00`，时区 Asia/Shanghai |
| 分页 | 请求 `page`（从 1 起，默认 1）/ `size`（默认 10，上限 50，越界 40002）；响应为 MyBatis-Plus `Page` 序列化结构（与 `GET /api/users/me/history` 完全一致），核心字段：`records`（当前页数组）、`total`、`size`、`current`、`pages` |
| 上传 | multipart 全局限制：单文件 ≤10MB、请求 ≤12MB，超限 → HTTP 400 + `code=40001` |

统一响应示例（成功）：

```json
{
  "code": 0,
  "message": "success",
  "data": { "id": 1 },
  "traceId": "c4f9e2a1b3d8"
}
```

---

## 2. 错误码

### 2.1 新增业务码（追加进 `ResultCode` 枚举，业务失败 HTTP 200）

| 枚举 | code | message | 触发场景 |
|---|---:|---|---|
| `RESUME_NOT_READY` | 10006 | 简历解析/分析进行中 | `GET /{id}/analysis` 在 ANALYZING/PENDING 时 |
| `RESUME_ANALYSIS_FAILED` | 10007 | 简历分析失败，可重试 | AI 分析最终失败（详情接口同时返回 `failReason`） |
| `RESUME_PARSE_FAILED` | 10008 | 简历解析失败 | 文件损坏/加密 PDF/文本量不足（`failReason` 给具体原因） |
| `RESUME_NOT_ANALYZED` | 10009 | 简历尚未完成分析，无法用于个性化面试 | 创建面试传入未 ANALYZED 的 resumeId |
| `RESUME_QUOTA_EXCEEDED` | 10010 | 简历数量/频次超出限额 | 保留 >20 份或当日上传 >10 份 |

### 2.2 复用既有码

| code | 场景（本功能相关） |
|---|---|
| 40001 / HTTP 400 | 上传扩展名或 Content-Type 不在白名单；缺 `file` part；multipart 总体不合法；`positionId` 非法 |
| 40002 / HTTP 400 | 分页参数越界（page<1 或 size>50） |
| 40401 | 资源不存在或无权访问：操作**他人**简历、resumeId 不存在（含已删除） |
| 40101 / 40301 / 40501 / 41501 / 42901 / 50001 | 沿用基线语义，无新场景 |

> 上传接口中"文件级业务失败"（解析失败）不走 400：上传本身成功（HTTP 200 + `code=0`），`data.status=FAILED` + `data.failReason`。前端据状态渲染失败态并引导重传/重试。仅"请求本身不合法"（格式白名单外、超限、缺参）才返回 4xx。

---

## 3. 简历管理接口

### R-01 上传简历

```text
POST /api/resumes          Content-Type: multipart/form-data
```

**请求参数（form-data）：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | file | 是 | 简历文件；扩展名白名单 pdf/docx/md/txt + 魔数校验；≤10MB |
| `positionId` | number | 否 | 分析对照岗位；缺省为通用分析（skill 维度按通用技能词典评估） |

**响应 `data`（上传即返回，解析同步完成）：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 简历 ID |
| `fileName` | string | 原始文件名 |
| `fileType` | string | `PDF` / `DOCX` / `MD` / `TXT` |
| `fileSize` | number | 字节数 |
| `positionId` | number \| absent | 绑定岗位 |
| `status` | string | `ANALYZING`（解析成功，等待 AI）或 `FAILED`（解析失败，见 failReason） |
| `failReason` | string \| absent | 仅 FAILED 时存在 |
| `charCount` | number | 抽取到的文本字符数 |
| `createdAt` | string | ISO 时间 |

**示例：**

```json
// 成功（进入分析）
{ "code": 0, "message": "success", "data": { "id": 3, "fileName": "张三-后端简历.pdf", "fileType": "PDF", "fileSize": 486213, "positionId": 1, "status": "ANALYZING", "charCount": 2317, "createdAt": "2026-10-26T14:30:05" }, "traceId": "..." }

// 上传成功但解析失败（扫描件 PDF）
{ "code": 0, "message": "success", "data": { "id": 4, "fileName": "扫描简历.pdf", "fileType": "PDF", "fileSize": 1200000, "status": "FAILED", "failReason": "未检测到可分析文本（不支持扫描件），请上传文本型 PDF 或 docx/md/txt", "charCount": 0, "createdAt": "2026-10-26T14:31:10" }, "traceId": "..." }
```

**错误码：** `40001`（格式/缺 file）、`40001`（超 10MB，HTTP 400）、`10010`（配额超限）、`40001`（positionId 不存在）。

### R-02 简历列表

```text
GET /api/resumes?page=1&size=10
```

**响应 `data`**：`Page` 分页结构，`records[]` 元素：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` / `fileName` / `fileType` / `positionId` | — | 同上 |
| `positionName` | string \| absent | 岗位名 |
| `status` | string | `ANALYZING` / `ANALYZED` / `FAILED` |
| `overallScore` | number \| absent | 仅 ANALYZED 时存在（0~100） |
| `isActive` | boolean | 是否默认简历 |
| `createdAt` | string | — |

**说明：** 仅返回当前用户、未删除的简历，按 `createdAt` 倒序。

### R-03 简历详情（轮询端点）

```text
GET /api/resumes/{id}
```

**响应 `data`：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` / `fileName` / `fileType` / `fileSize` / `positionId` / `positionName` / `status` / `failReason` / `charCount` / `createdAt` / `updatedAt` | — | 见 R-01/R-02 |
| `profile` | object \| absent | 结构化抽取（脱敏）：`{basicInfo:{...}, education:{...}, skills:[...], projects:[...], experiences:[...], honors:[...]}`，键可能按简历实际内容缺席 |
| `digest` | object \| absent | 个性化摘要（结构见本方案 §3.2），仅 ANALYZED 时存在 |
| `analysis` | object \| absent | 分析结果，结构同 R-04 的 `data`；仅 ANALYZED 时存在 |

**错误码：** `40401`（不存在或非本人）。

### R-04 获取分析结果

```text
GET /api/resumes/{id}/analysis
```

**响应 `data`：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `resumeId` | number | — |
| `provider` | string | `mock` / `qwen`（实际产出结果的 Provider） |
| `overallScore` | number | 0~100 |
| `dimensionScores` | object | 六维分：`{ "basic":72, "education":80, "skill":64, "project":48, "experience":60, "expression":75 }`，每维 0~100 |
| `strengths` | string[] | 亮点（≤5 条） |
| `suggestions` | object[] | 改进建议（3~8 条，按风险降序），元素结构见下 |
| `summary` | string | 总评（≤500 字） |
| `createdAt` | string | — |

**`suggestions[]` 元素：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `riskLevel` | string | `HIGH` / `MEDIUM` / `LOW` |
| `dimension` | string | 六维 key 之一（basic/education/skill/project/experience/expression） |
| `title` | string | 一句话标题（≤20 字） |
| `issue` | string | 问题描述（引用证据说明为什么是问题） |
| `suggestion` | string | 具体可执行的修改建议 |
| `evidence` | string \| absent | 简历原文证据句（脱敏后），LOW 级亮点项可为空 |

**示例 `data`：**

```json
{
  "resumeId": 3,
  "provider": "mock",
  "overallScore": 63,
  "dimensionScores": { "basic": 85, "education": 78, "skill": 70, "project": 42, "experience": 55, "expression": 66 },
  "strengths": ["技能表述有层次（精通/熟悉/了解区分明确）", "教育背景与目标岗位专业相关"],
  "suggestions": [
    { "riskLevel": "HIGH", "dimension": "project", "title": "项目经历缺少量化成果", "issue": "两个项目均只描述功能实现，没有性能、规模或业务结果数据，难以体现个人贡献度", "suggestion": "为每个项目补充 1~2 个量化结果，例如 QPS、响应耗时下降比例、覆盖用户量、故障率下降等", "evidence": "负责订单模块的开发和上线" },
    { "riskLevel": "HIGH", "dimension": "experience", "title": "缺少实习或实践经历", "issue": "简历中没有实习、竞赛或开源贡献，实践维度证据不足", "suggestion": "补充课程设计以外的实践经历；如暂无，可将课程项目按实习粒度展开（角色/周期/产出）", "evidence": "无" },
    { "riskLevel": "MEDIUM", "dimension": "expression", "title": "量化句比例偏低", "issue": "全文仅 6% 的句子含数据，说服力不足", "suggestion": "将至少 1/3 的职责描述改写为'动作 + 技术 + 结果'结构", "evidence": "参与系统测试与文档编写" },
    { "riskLevel": "LOW", "dimension": "skill", "title": "技能分层表述规范，保持", "issue": "技能板块使用精通/熟悉/了解分层，表述可信", "suggestion": "保持该写法；面试前确保'熟悉'级技能可回答原理类问题", "evidence": "熟悉 Spring Boot、MyBatis，了解 JVM 调优" }
  ],
  "summary": "简历结构与岗位相关性良好，主要短板在项目经历的深度呈现与实践经历缺失，建议优先补量化成果。",
  "createdAt": "2026-10-26T14:30:12"
}
```

**错误码：** `10006`（分析中）、`10007`（分析失败）、`10008`（解析失败）、`40401`。

### R-05 重新分析

```text
POST /api/resumes/{id}/reanalyze
```

- 适用状态：`FAILED`（重试）或 `ANALYZED`（如切换 Provider 后重评）。**覆盖**旧分析结果。
- 响应 `data`：同 R-01（`status=ANALYZING`）。
- 限制：同一简历 `ANALYZING` 期间重复调用 → `10006`（不并发重分析）。
- **错误码：** `10006`、`40401`。

### R-06 设为默认简历

```text
PUT /api/resumes/{id}/active
```

- 响应 `data`：`{ "id": 3, "isActive": true }`。每用户至多一份默认（原默认自动取消）。
- 仅 `ANALYZED` 简历可设为默认；否则 `40001`。
- **错误码：** `40001`、`40401`。

### R-07 删除简历（逻辑删除）

```text
DELETE /api/resumes/{id}
```

- 响应 `data`：`{ "id": 3, "deleted": true }`。删除后列表不可见、不可再被新面试引用；**历史面试会话中的记录不受影响**（报告仍可查看）。
- **错误码：** `40401`。

### R-08 个性化重点考察预览

```text
GET /api/resumes/{id}/focus-skills?positionId=1
```

- `positionId` 可选：缺省用简历绑定的岗位；两者皆空 → `40001`。
- 纯规则计算（无 AI、无副作用），供面试设置页展示。

**响应 `data`：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `resumeId` / `positionId` / `positionName` | — | — |
| `matched` | object[] | 命中技能：`{ "skillCode": "SPRING_BOOT", "skillName": "Spring 全家桶", "weight": 0.2, "matchedTerms": ["Spring Boot"] }`，按岗位权重降序 |
| `gaps` | object[] | 岗位要求但简历未体现：`{ "skillCode": "REDIS", "skillName": "Redis 与缓存" }` |
| `hitRatio` | number | 命中岗位技能权重占比 0~1；`0` 表示将按通用策略组卷 |

**错误码：** `40001`（无岗位可用）、`40401`。

---

## 4. 个性化面试接口扩展

### R-09 创建面试（扩展既有接口）

```text
POST /api/interviews          Content-Type: application/json
```

**请求体 = 既有字段 + 1 个可选字段：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `positionId` / `mode` / `difficulty` / `rounds` / `durationMinutes` | — | — | 既有字段与校验完全不变（难度 1~5、轮次 5~8、时长 15~60） |
| `resumeId` | number | 否 | 个性化面试来源简历；须为本人且 `ANALYZED`；**不传 = 普通面试，行为与基线完全一致** |

**响应 `data`（增量字段）：**

```json
{ "id": 12, "resumeId": 3, "personalized": true }
```

（基线仅返回 `id`；`resumeId/personalized` 为新增，不传 resumeId 时二者为 `null/false`，且可能因 NON_NULL 缺席。）

**校验顺序与错误码：** 既有参数校验（40002）→ 简历归属（`40401`）→ 简历状态非 ANALYZED（`10009`）。

**说明：** 简历绑定岗位与面试岗位允许不同——digest 与岗位无关，技能匹配始终按**面试岗位**能力模型计算；前端可在 focus-skills 预览后再提交。

### R-10 面试详情（扩展既有接口）

```text
GET /api/interviews/{id}
```

**响应 `data` 增量字段**（追加在既有 `InterviewDetailVO` 上，其余字段不变）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `resumeId` | number \| absent | 个性化会话的来源简历 |
| `resumeFileName` | string \| absent | 简历文件名（供房间页展示） |
| `personalized` | boolean | 是否个性化面试（= resumeId 非空） |

### 4.3 不变的部分（明确承诺）

| 项 | 说明 |
|---|---|
| WebSocket | 地址、鉴权（`?token=`）、消息类型（QUESTION/FOLLOW_UP/TRANSCRIPT/TIMER/PROGRESS/REPORT_READY/ERROR）与负载结构**全部不变**；个性化题干经既有 QUESTION 推送 |
| 作答接口 | `/messages/text`、`/messages/audio` 契约不变；个性化会话的评分流程不变（评分基准仍为题库 key_points） |
| 报告接口 | `GET /api/interviews/{id}/report` 结构不变；个性化对比条目写入既有 `strengths/weaknesses` 数组（前缀"简历中的…"），前端按前缀渲染即可 |
| 消息接口 | `GET /{id}/messages` 的 `MessageVO` 不变（个性化题干即 `contentText`，`questionId` 照旧指向题库题） |

---

## 5. 状态与轮询契约

### 5.1 简历状态机

```text
            ┌──────────── reanalyze（FAILED/ANALYZED → 覆盖） ───────────┐
            ▼                                                          │
上传: PENDING →(解析成功)→ ANALYZING →(AI 成功)→ ANALYZED ─(reanalyze)─┘
         │                    │
         └──(解析失败)──→ FAILED ←──(AI 失败/超时)──┘
```

| status | 含义 | 可发起个性化面试 |
|---|---|---|
| `PENDING` | 瞬态（上传事务内），前端基本观察不到 | 否 |
| `ANALYZING` | 解析成功，AI 分析中 | 否（10009） |
| `ANALYZED` | 完成，结果与 digest 可用 | 是 |
| `FAILED` | 解析或分析失败，`failReason` 有值 | 否（10009） |

### 5.2 前端轮询建议

1. 上传返回后立即以 **2s** 间隔轮询 `GET /api/resumes/{id}`，观察 `status`；
2. `ANALYZING` 持续轮询，**上限 60s**（约 30 次）；超时提示"分析仍在进行，可稍后回到列表刷新"（后端分析超时约 30s + 重试，最终必然落到 ANALYZED/FAILED）；
3. `ANALYZED` 停止轮询并渲染 `analysis`；`FAILED` 停止并展示 `failReason` + 重试按钮（R-05）；
4. 列表页进入时对 `ANALYZING` 项做同样轮询（可合并为单请求批量判断：以列表接口刷新为准即可）。

### 5.3 curl 快速验证（同步维护进 `backend/api.http`）

```bash
BASE=http://127.0.0.1:8080
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"demo01","password":"123456"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
AUTH="Authorization: Bearer $TOKEN"

# R-01 上传（positionId=1 Java 后端岗）
RID=$(curl -s -X POST $BASE/api/resumes -H "$AUTH" -F "file=@简历.docx" -F "positionId=1" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
sleep 3
# R-03 详情（含 analysis 与 digest）
curl -s $BASE/api/resumes/$RID -H "$AUTH"
# R-08 重点技能预览
curl -s "$BASE/api/resumes/$RID/focus-skills?positionId=1" -H "$AUTH"
# R-09 个性化面试（5 轮文字面试）
SID=$(curl -s -X POST $BASE/api/interviews -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"positionId\":1,\"mode\":\"TEXT\",\"difficulty\":3,\"rounds\":5,\"durationMinutes\":15,\"resumeId\":$RID}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
# R-10 start 后首题即为个性化题干（questionId 保留）
curl -s -X POST $BASE/api/interviews/$SID/start -H "$AUTH"
curl -s $BASE/api/interviews/$SID/messages -H "$AUTH"
```
