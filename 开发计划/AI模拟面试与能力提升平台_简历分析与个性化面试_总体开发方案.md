# AI模拟面试与能力提升平台 —— 简历分析与个性化面试 总体开发方案

> 本方案是在《AI模拟面试与能力提升平台_系统实现方案.md》（下称"基线方案"，已按 M1~M6 完成开发与验收）基础上的**功能增量方案**，覆盖两个新需求：
>
> - **F1 简历上传与分析**：上传 pdf/docx/md/txt 简历，AI 多维度评分 + 按高/中/低风险分级的改进意见；
> - **F2 个性化面试**：已上传并完成分析的简历，可作为面试输入，AI 结合简历技术栈与项目经历个性化出题与追问。
>
> 配套文档：
>
> | 文档 | 职责 |
> |---|---|
> | 本文 | 需求分析、总体架构、功能与数据库设计、AI 扩展、安全与测试方案 |
> | 《简历分析与个性化面试_接口协议》 | 新增/扩展 REST 接口的统一协议（请求/响应/错误码/示例） |
> | 《简历分析与个性化面试_后端开发计划》 | 后端任务分解、里程碑 R1~R4、改动点清单、验收标准 |
>
> 文中"基线方案 §x"指向基线文档章节号。实现不得破坏基线方案的既有验收项（六维报告、状态机、WebSocket、离线 Mock 演示等）。

---

## 目录

1. [需求分析](#1-需求分析)
2. [总体架构](#2-总体架构)
3. [功能详细设计](#3-功能详细设计)
4. [AI 能力扩展设计](#4-ai-能力扩展设计)
5. [数据库设计](#5-数据库设计)
6. [接口总览](#6-接口总览)
7. [前端页面设计](#7-前端页面设计)
8. [安全设计](#8-安全设计)
9. [异常与降级](#9-异常与降级)
10. [测试方案](#10-测试方案)
11. [里程碑概览](#11-里程碑概览)
12. [验收标准](#12-验收标准)

---

## 1. 需求分析

### 1.1 功能需求

**F1 简历上传与分析**

1. 学生上传简历文件，支持 **pdf / docx / md / txt** 四种格式，单文件 ≤ 10MB。
2. 后端抽取文本并做结构化解析（基础信息、教育、技能、项目、经历板块），敏感信息（手机号/邮箱/身份证）在送入大模型前**脱敏**。
3. AI 给出**多维度评分**：总分 0~100 + 六个维度分（见 §3.3），维度与权重对齐"岗位能力模型"思路，可解释。
4. AI 给出**改进意见**，每条意见标注风险等级 **HIGH / MEDIUM / LOW**（高=必须修改、中=建议优化、低=保持亮点/锦上添花），并附简历原文证据句。
5. 分析结果持久化，可反复查看；分析失败可重试；支持设置一份"默认简历"。
6. 每用户配额：保留 ≤ 20 份，每日上传 ≤ 10 份（防滥用，超限返回业务码，见协议文档）。

**F2 个性化面试**

1. 发起面试时可选一份**已完成分析**的简历（`resumeId` 可选参数，不传则与现状完全一致）。
2. 选中简历后，面试在四个环节个性化（详见 §3.6）：
   - **组卷偏好**：题目技能标签优先匹配简历技术栈，项目题优先与简历项目相关；
   - **题干个性化**：AI 将题库题干改写为"结合候选人项目经历"的表述（评分基准不变）；
   - **追问个性化**：追问上下文注入简历要点，项目题追问围绕简历项目深挖；
   - **报告个性化**：报告输出"简历自述技能 vs 面试实测表现"的对比洞察。
3. 提供组卷前预览：基于简历与岗位能力模型，预测本次面试的重点考察技能。

### 1.2 用户故事

| 编号 | 用户故事 |
|---|---|
| US-1 | 作为学生，我想上传简历获得多维度评分和分级的修改建议，以便在投递前针对性完善简历。 |
| US-2 | 作为学生，我想让面试围绕我简历里写的技术栈和项目出题，模拟"简历深挖式"真实面试。 |
| US-3 | 作为学生，我想在面试前看到"这次面试会重点考察我哪些技能"，以便预判和准备。 |
| US-4 | 作为学生，我希望面试报告告诉我"简历上写的技能哪些经得起追问、哪些是水分"。 |

### 1.3 范围边界（本期不做）

| 不做的事 | 说明 / 替代 |
|---|---|
| `.doc` 老格式 | 仅支持 docx；上传 .doc 返回 40001 并提示"请另存为 .docx" |
| 扫描件 / 图片型 PDF（OCR） | 文本层抽取字符数不足即判定解析失败（10008），提示换文本型 PDF |
| 简历在线编辑、模板生成 | 本期只读分析，改进意见引导用户线下修改后重新上传 |
| 简历投递 / HR 端 | 不涉及 |
| 简历多语言（中英以外） | 词典与 Prompt 面向中英文，其他语言不保证效果 |
| 管理端简历审核 | 管理端接口本期不新增（后续可选扩展：简历统计看板） |

### 1.4 依赖的既有能力（全部复用，不重复建设）

| 既有能力 | 代码位置 | 复用方式 |
|---|---|---|
| 统一响应/错误码 | `common/result/Result`、`ResultCode` | 新增 5 个业务码（10006~10010），协议不变 |
| JWT 鉴权与角色 | `auth/security/*`、`SecurityConfig` | 新接口默认 `authenticated()`，归属校验沿用 40401 惯例 |
| 文件上传安全模式 | `interview/storage/AudioStorageService` | 白名单 + 魔数 + UUID 落盘 + 路径归一化，照抄出 `ResumeStorageService` |
| multipart 配置与超限处理 | `application.yml`、`GlobalExceptionHandler` | 10MB/12MB 不变，`MaxUploadSizeExceededException` 已映射 400/40001 |
| AI 双实现与三级容错 | `ai/provider/AiProvider` + Mock/OpenAiCompatible | 接口扩 2 个方法，两实现同步补齐，容错链复用 |
| 异步执行器 | `common/config/AsyncConfig`（`aiExecutor`） | 简历分析复用该线程池 |
| 岗位能力模型 | `position` 包 `competency_model_json` | 技能匹配度的对照基准 |
| 组卷算法 | `interview/service/PaperAssembler` | 重载方法加"简历偏好"，保持可注入随机种子 |
| 追问规则引擎 / Prompt | `interview/rule/FollowupRuleEngine`、`ai/prompt/PromptBuilder` | 上下文注入简历摘要 |
| Flyway 迁移体系 | `db/migration/V1~V7` | 新增 `V8__resume_feature.sql` |
| 测试与质量门禁 | `*Test` / `*IT`、JaCoCo ≥60% | 新代码同等纳入 `mvn verify` |

---

## 2. 总体架构

### 2.1 模块视图

新增 1 个业务包 `resume/`，改动 4 个既有包（改动点均为**加法**，不修改既有签名语义）：

```text
com.example.interview
├── resume/            ★ 新增：简历域
│   ├── controller/ResumeController
│   ├── service/ResumeService              上传受理/列表/详情/删除/配额/默认简历
│   ├── service/ResumeAnalysisService      异步分析编排（状态机）
│   ├── service/ResumeTextExtractor       4 格式文本抽取（PDFBox/POI/纯文本）
│   ├── service/ResumeProfileParser       结构化分段 + PII 脱敏
│   ├── service/ResumeScoreCalculator     六维聚合
│   ├── storage/ResumeStorageService      安全落盘
│   ├── entity/Resume、ResumeAnalysis
│   ├── mapper/ResumeMapper、ResumeAnalysisMapper
│   └── dto/...                            VO 与请求对象
├── ai/                ▲ 改动：AiProvider +2 方法；Mock/OpenAiCompatible 双实现；
│                          PromptBuilder +2 段（分析/改写/追问注入）
├── interview/         ▲ 改动：CreateInterviewRequest +resumeId；PaperAssembler
│                          +简历偏好重载；TurnEngine/ReportService 注入简历摘要
├── common/            ▲ 改动：ResultCode +10006~10010；application.yml +app.resume.*
└── （auth/position/question/knowledge/growth/admin 不动）
```

### 2.2 端到端流程

**F1 简历分析链路：**

```text
POST /api/resumes (multipart)
      ↓ 安全校验（扩展名白名单 + 魔数 + 大小 + 配额）
      ↓ UUID 落盘 {app.upload-dir}/resume/yyyyMM/，DB 建 PENDING 记录
      ↓ 同步：文本抽取（失败→FAILED/10008；字符数<200→FAILED/10008）
      ↓ 状态 ANALYZING → @Async("aiExecutor") 异步分析
      │     ProfileParser 分段 + PII 脱敏
      │     → AiProvider.analyzeResume（mock 规则 / qwen JSON，三级容错）
      │     → ScoreCalculator 聚合总分 → 建议风险分级
      ↓ ANALYZED（结果落 resume_analysis，摘要落 resume.digest_json）
前端轮询 GET /api/resumes/{id}（2s 间隔）→ 展示雷达图 + 分级建议
```

**F2 个性化面试链路：**

```text
POST /api/interviews {positionId, ..., resumeId?}
      ↓ resumeId 非空：归属校验(40401) + 状态=ANALYZED(否则 10009)
      ↓ 会话落库（interview_session.resume_id）
POST /api/interviews/{id}/start
      ↓ PaperAssembler.assemble(..., resumeDigest)   ← 组卷偏好
      ↓ AiProvider.personalizeQuestions(...)          ← 题干改写（失败降级原文）
      ↓ 首题推送（WebSocket / REST，消息结构不变）
面试中 TurnEngine → PromptBuilder 注入简历要点 → 追问围绕项目深挖
结束时 ReportService → 报告增加"简历技能 vs 实测"对比（不加列，写入现有字段）
```

### 2.3 设计原则

1. **纯增量**：不传 `resumeId` 的面试与基线行为完全一致；既有 9 张表结构不变，仅 `interview_session` 加 1 个可空列。
2. **AI 可离线**：`MockAiProvider` 同步补齐两个新方法，无网/无 Key 时简历功能可完整演示（课程设计答辩刚需）。
3. **评分可解释**：Mock 打分规则与维度定义一一对应（词典命中、STAR 信号、量化比例等），真实 Provider 失败时降级 Mock，分数逻辑不漂移。
4. **失败不阻塞主流程**：题干改写失败降级为原题；追问/报告注入失败按无简历处理；只有"解析失败"是硬失败（明确告知用户）。

---

## 3. 功能详细设计

### 3.1 简历上传与存储

**格式支持与抽取方式：**

| 格式 | 扩展名白名单 | 魔数校验 | 抽取实现 |
|---|---|---|---|
| PDF | `.pdf` | 头 5 字节 `%PDF-` | Apache PDFBox 3.x `PDFTextStripper`（仅文本层，不做 OCR） |
| DOCX | `.docx` | ZIP 头 `PK\x03\x04` | Apache POI `XWPFDocument`（段落 + 表格文字） |
| Markdown | `.md` | 文本可解码校验 | JDK `Files.readString`（UTF-8，失败尝试 GBK） |
| 纯文本 | `.txt` | 文本可解码校验 | 同上 |

- Content-Type 复核沿用音频先例：白名单 + 放行 `application/octet-stream`（浏览器对 md/txt 的 MIME 不稳定）。
- 存储路径：`{app.upload-dir}/resume/yyyyMM/{uuid}.{ext}`，DB 存相对路径；`resolve()` 归一化后必须仍在 upload-dir 内（防穿越，照抄 `AudioStorageService`）。
- 删除为**逻辑删除**（`deleted=1`）：文件保留作审计，列表/引用立即不可见；历史面试会话中 `resume_id` 不级联。

**解析护栏（硬失败，状态置 FAILED，`fail_reason` 记录）：**

| 场景 | 判定 | 用户提示 |
|---|---|---|
| 加密 PDF | PDFBox 抛 `InvalidPasswordException` | 简历已加密，请去除密码后重传 |
| 损坏文件 | 抽取抛异常 | 文件无法解析，请确认文件未损坏 |
| 图片型/空文本 PDF | 抽取字符数 < `app.resume.min-text-chars`（默认 200） | 未检测到可分析文本（扫描件不支持），请使用文本型 PDF |
| 超大文件 | > 10MB | 全局 multipart 拦截 → 400/40001 |

### 3.2 结构化解析与 PII 脱敏

`ResumeProfileParser`（纯规则，不依赖 AI，可单测）：

1. **分段**：按标题词典切分文本板块——`教育背景/教育经历`、`专业技能/技术栈`、`项目经历/项目经验`、`实习经历/工作经历`、`荣誉奖项/证书`、`自我评价`；无标题时整段作为"未分段"处理，交给 AI 容错。
2. **技能识别**：复用 `knowledge/service/KeywordExtractor` 词典 + 岗位 `competency_model_json` 技能名表做交集（如 Spring Boot、Redis、Vue3、MySQL）。
3. **项目识别**：项目板块内按"项目名：/项目描述："模式切分，提取项目名、技术栈、1~2 句亮点（含量化句优先）。
4. **PII 脱敏**（送模型前执行，正则实现）：

| 类型 | 规则 | 示例 |
|---|---|---|
| 手机号 | `1[3-9]\d{9}` → 保留前 3 后 2 | `13812345678` → `138****78` |
| 邮箱 | `@` 前保留首字符 | `zhangsan@qq.com` → `z***@qq.com` |
| 身份证 | 18 位（含 X）→ 保留前 3 后 2 | `110101200001011234` → `110**********34` |

解析结果 `profile_json` 与个性化摘要 `digest_json` 均**只存脱敏后内容，且不含任何联系方式字段**。

**digest_json 结构**（F2 全链路的数据契约，Mock 与真实 Provider 共用）：

```json
{
  "skills": [
    { "name": "Spring Boot", "category": "BACKEND", "level": "熟悉" }
  ],
  "projects": [
    {
      "name": "校园二手交易平台",
      "techStack": ["Spring Boot", "MySQL", "Redis"],
      "highlights": ["实现秒杀下单，压测峰值 500 QPS"]
    }
  ],
  "experiences": [
    { "org": "某互联网公司", "role": "后端开发实习生", "months": 6 }
  ],
  "education": { "degree": "本科", "major": "计算机科学与技术" }
}
```

### 3.3 简历多维评分模型

六个维度（0~100 整数分），总分 = 加权求和后四舍五入：

| 维度 key | 名称 | 默认权重 | 评价内容与可解释信号 |
|---|---|---:|---|
| `basic` | 基础信息完整度 | 0.10 | 姓名/联系方式/求职意向等要素齐全；板块结构完整 |
| `education` | 教育背景 | 0.10 | 学历层次、专业相关度、成绩排名/核心课程 |
| `skill` | 技能与岗位匹配 | 0.25 | 技能对岗位能力模型覆盖率；深度分层表述（精通/熟悉/了解）；与岗位无关技能堆砌扣分 |
| `project` | 项目经历质量 | 0.25 | STAR 完整度（背景-任务-行动-结果）；量化成果比例；技术栈与职责表述准确性；复杂度信号（并发/性能/设计权衡） |
| `experience` | 实习与实践经历 | 0.10 | 实习/竞赛/开源/证书与岗位相关度 |
| `expression` | 表达与排版规范 | 0.20 | 动词开头句比例、量化句比例、篇幅控制（有效字符带 800~4000）、术语一致、重复表述 |

```text
overall = Σ(dimension_score × weight)，weights = 0.10/0.10/0.25/0.25/0.10/0.20
```

- 权重代码内固定常量（与基线方案六维权重同样处理方式）；若简历未绑定岗位，`skill` 维度按"通用软件研发技能词典"评估并在结果中注明。
- 真实 Provider 由模型按维度定义打分；Mock 按上表"可解释信号"规则打分（详见后端计划 §6），两者维度与区间一致，保证降级时分数语义不漂移。

### 3.4 风险分级改进建议

每条建议结构（前端按 `riskLevel` 分组渲染）：

```json
{
  "riskLevel": "HIGH",
  "dimension": "project",
  "title": "项目经历缺少量化成果",
  "issue": "两个项目均只描述功能，没有性能、规模或业务结果数据，难以体现贡献度",
  "suggestion": "为每个项目补充 1~2 个量化结果，例如 QPS、响应耗时下降比例、覆盖用户量",
  "evidence": "负责订单模块的开发和上线"
}
```

**分级规则（Mock 与真实 Provider 输出校验共用）：**

| 风险级 | 触发条件（按维度） | 语义 |
|---|---|---|
| HIGH | 维度分 < 40 | 硬伤，必须修改 |
| MEDIUM | 40 ≤ 维度分 < 70 | 短板，建议优化 |
| LOW | 维度分 ≥ 70 且属 Top2 维度或含量化亮点 | 保持优势 / 锦上添花 |

**数量约束**：总数 3~8 条；按 HIGH→MEDIUM→LOW 排序返回；AI 返回不足 3 条时由 Mock 规则补齐，超过 8 条截断（保留高风险优先）。

### 3.5 分析状态机

```text
PENDING ──解析成功──→ ANALYZING ──AI成功──→ ANALYZED
   │                    │
   └─解析失败→ FAILED ←─┘（AI 失败/超时/JSON 不可恢复）
                     │
                     └─ POST /reanalyze → 重新进入 ANALYZING（覆盖旧结果）
```

- 上传接口同步完成"校验 + 落盘 + 抽取"，抽取成功即返回 `ANALYZING`（抽取失败同步返回 FAILED，用户立刻可感知，无需轮询）。
- ANALYZED 的简历允许重分析（如切换 AI Provider 或修复后），旧 `resume_analysis` 记录被覆盖。

### 3.6 个性化面试设计

**① 入口与校验**：`POST /api/interviews` 新增可选 `resumeId`。校验链：存在且属于当前用户（否则 40401）→ `status=ANALYZED`（否则 10009）→ 通过后会话记录 `resume_id`。简历分析时绑定的岗位与面试岗位**允许不同**：`digest` 与岗位无关，技能匹配始终按面试岗位的能力模型计算。

**② 组卷偏好（PaperAssembler 扩展）**：题型比例 50/20/20/10 与难度带逻辑**完全不变**，仅在"难度带内挑选"环节增加优先级：

```text
候选题优先级（带内）：
  1) 覆盖未覆盖技能 且 命中简历技能（skill_tags ∩ digest.skills）
  2) 命中简历技能
  3) 覆盖未覆盖技能（现状）
  4) 其他（随机，现状）
PROJECT 类型：命中简历技能/项目的题优先，保证至少 1 道命中题（若题库存在）
零命中（简历技能与该岗位题库无交集）→ 完全回退现有算法，记 INFO 日志
```

**③ 题干个性化改写（AiProvider.personalizeQuestions）**：

- `start` 时把"岗位 + digest + 本场主问题列表"一次性送 AI，返回与题目一一对应的改写题干；
- 改写保留题目考查点，仅绑定候选人背景，如："结合你在《校园二手交易平台》中使用 Redis 的经历——原题：缓存穿透有哪些解决方案？请说明适用场景。"
- **落库规则**：改写文本存 `interview_message.content_text`，`question_id` 照旧关联 → 逐题评分基准（reference_answer/key_points）不变；
- **降级**：AI 返回数量/ID 不匹配、超时或解析失败 → 整场使用原题干（all-or-nothing，简单可测），不报错、不打断 start；
- Mock 实现：模板拼接（项目名 + 技术栈 + 原题干），零耗时。

**④ 追问个性化**：`NextQuestionContext` 增加可空 `resumeDigest`；`PromptBuilder` 追加"候选人简历要点"段（技术栈、项目名与亮点，明确提示"追问可结合其项目深挖实现细节、技术选型原因、遇到的困难"）。Mock 侧 `FollowupRuleEngine`：PROJECT 轮的追问模板优先选"项目深挖"池并引用项目名。

**⑤ 报告个性化**：`ReportContext` 增加可空 `resumeDigest`。报告生成后，对"digest 技能 ↔ 逐题维度得分"做映射：

- 简历自述技能对应题目得分 < 60 → `weaknesses` 追加"简历中自述掌握 {skill}，但相关题目表现偏弱（{score} 分），建议重点巩固"；
- ≥ 80 → `strengths` 追加"简历中的 {skill} 经受住了追问验证"。

写入现有 `strengths_json / weaknesses_json / report_text` 字段，`evaluation_report` **不加列**。

**⑥ 重点考察预览（focus-skills）**：组卷前由规则计算（无 AI 参与）：`digest.skills ∩ 岗位能力模型` → 命中技能（含岗位权重与简历证据）+ 缺口技能（岗位要求但简历未体现），供前端在面试设置页展示。

---

## 4. AI 能力扩展设计

### 4.1 AiProvider 接口扩展

```java
public interface AiProvider {
    // ……既有三方法不变……

    /** F1：简历多维分析（评分 + 分级建议 + 摘要），失败抛 AiException 由调用方降级。 */
    AiResumeAnalysis analyzeResume(ResumeAnalysisContext ctx);

    /** F2：主问题题干个性化改写；失败时调用方降级为原题干。 */
    AiQuestionPersonalization personalizeQuestions(QuestionPersonalizeContext ctx);
}
```

新增模型类（`ai/model/`）：`ResumeAnalysisContext`（脱敏全文、profile、岗位能力模型、维度定义）、`AiResumeAnalysis`（dimensionScores、suggestions[]、strengths[]、summary、digest）、`QuestionPersonalizeContext`（岗位、digest、题目列表）、`AiQuestionPersonalization`（items[{questionId, content}]）。

### 4.2 Prompt 设计（真实 Provider）

- **分析 Prompt**：系统角色"资深技术面试官与简历评审"；输入 = 脱敏简历全文 + 目标岗位能力模型（技能名+权重）+ 六维定义与分档标准；输出强约束 JSON（`response_format=json_object`），字段与 `AiResumeAnalysis` 一一对应，建议须引用原文证据句。
- **改写 Prompt**：输入 = 岗位名 + digest + 题目列表（ID/题型/技能标签/原题干）；输出 = `[{questionId, content}]`，要求保留考查点、每条 10~500 字、不得泄露评分要点。
- JSON 解析复用 `ai/parser/AiResponseParser` 围栏容错。

### 4.3 Mock 规则（可解释、确定性，答辩离线演示用）

| 能力 | Mock 规则概要 |
|---|---|
| analyzeResume | 按词典命中/板块存在性/STAR 信号词（负责/实现/优化/结果）/量化句比例（含数字+% 的句子占比）/字符带，逐维打分；建议按 §3.4 规则生成，证据句取该板块首句 |
| personalizeQuestions | 模板："结合你在《{项目名}》项目中使用 {技术栈} 的经历——" + 原题干（无项目时仅拼技术栈） |

### 4.4 容错与降级链（复用基线三级容错）

| 环节 | 失败处理 |
|---|---|
| analyzeResume | 重试 1 次（退避 1s）→ 降级 Mock 规则 → 仍失败置 FAILED（10007，可 reanalyze） |
| personalizeQuestions | 重试 1 次 → 降级 Mock 模板 → Mock 也异常则使用原题干（不感知） |
| 追问/报告注入 | 按"无简历"上下文生成（既有链路） |

---

## 5. 数据库设计

新增 Flyway 脚本 `V8__resume_feature.sql`。基线方案"9 张表以内"约束适用于基线范围；本需求**新增 2 张表（总量 11）+ 既有表加 1 可空列**，既有表结构不做其他变更。

```sql
-- 简历主表
CREATE TABLE resume (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    file_name     VARCHAR(255) NOT NULL COMMENT '原始文件名',
    stored_path   VARCHAR(500) NOT NULL COMMENT '相对存储路径',
    file_type     VARCHAR(10)  NOT NULL COMMENT 'PDF/DOCX/MD/TXT',
    file_size     BIGINT       NOT NULL COMMENT '字节',
    position_id   BIGINT       NULL COMMENT '分析对照岗位（空=通用分析）',
    status        VARCHAR(20)  NOT NULL COMMENT 'PENDING/ANALYZING/ANALYZED/FAILED',
    fail_reason   VARCHAR(500) NULL,
    char_count    INT          NOT NULL DEFAULT 0,
    profile_json  JSON         NULL COMMENT '结构化抽取（脱敏后）',
    digest_json   JSON         NULL COMMENT '个性化摘要（F2 消费）',
    is_active     TINYINT      NOT NULL DEFAULT 0 COMMENT '默认简历，每用户至多 1 条',
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    KEY idx_resume_user (user_id),
    KEY idx_resume_position (position_id)
) COMMENT '简历';

-- 简历分析结果（一份简历至多一条，重分析覆盖）
CREATE TABLE resume_analysis (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    resume_id             BIGINT      NOT NULL UNIQUE,
    provider              VARCHAR(20) NOT NULL COMMENT 'mock/qwen',
    overall_score         INT         NOT NULL COMMENT '0~100',
    dimension_scores_json JSON        NOT NULL COMMENT '六维 {basic,education,skill,project,experience,expression}',
    strengths_json        JSON        NOT NULL,
    suggestions_json      JSON        NOT NULL COMMENT '[{riskLevel,dimension,title,issue,suggestion,evidence}]',
    summary_text          TEXT        NULL,
    created_at            DATETIME    NOT NULL
) COMMENT '简历分析结果';

-- 既有表：面试会话关联简历
ALTER TABLE interview_session
    ADD COLUMN resume_id BIGINT NULL COMMENT '个性化面试来源简历，空=普通面试',
    ADD KEY idx_session_resume (resume_id);
```

JSON 列映射沿用 `JacksonTypeHandler`（同 `question.skill_tags_json` 模式）；DDL 细节（注释风格/引擎子句）实施时对齐 `V1__init_schema.sql`。

---

## 6. 接口总览

统一协议（认证头、`Result` 包装、错误码语义、分页与时间格式）完全沿用基线；详细请求/响应字段与示例见《简历分析与个性化面试_接口协议》。

| # | 方法与路径 | 说明 | 权限 |
|---|---|---|---|
| 1 | POST `/api/resumes` | 上传简历（multipart），同步完成校验/落盘/抽取 | STUDENT |
| 2 | GET `/api/resumes` | 简历列表（分页，含状态与总分摘要） | STUDENT |
| 3 | GET `/api/resumes/{id}` | 简历详情（含分析结果，轮询端点） | 归属本人 |
| 4 | GET `/api/resumes/{id}/analysis` | 仅分析结果（ANALYZED 前返回 10006） | 归属本人 |
| 5 | POST `/api/resumes/{id}/reanalyze` | 重新分析（FAILED/ANALYZED 均可） | 归属本人 |
| 6 | PUT `/api/resumes/{id}/active` | 设为默认简历 | 归属本人 |
| 7 | DELETE `/api/resumes/{id}` | 逻辑删除 | 归属本人 |
| 8 | GET `/api/resumes/{id}/focus-skills` | 个性化面试重点考察预览（纯规则） | 归属本人 |
| 9 | POST `/api/interviews` | **扩展**：新增可选 `resumeId` | STUDENT |
| 10 | GET `/api/interviews/{id}` | **扩展**：响应增加 `resumeId/resumeFileName/personalized` | 归属本人 |

WebSocket 消息类型与结构**不变**（个性化面试复用 QUESTION/FOLLOW_UP 等既有推送）。

---

## 7. 前端页面设计

前端实现不在本方案与后端计划范围，此处仅约定页面与契约：

| 页面/改动 | 内容 |
|---|---|
| `/resumes` 简历管理 | 上传区（格式/大小限制提示与错误回显）、简历卡片列表（状态徽标：解析中/已分析/失败+重试）、默认简历标记 |
| `/resumes/:id` 分析详情 | 总分 + ECharts 六维雷达图；建议按 高/中/低风险 三组折叠面板渲染（标题/问题/建议/证据句）；失败态展示 `failReason` 与"重新分析" |
| `/interview/setup` 面试设置 | 新增"个性化面试"开关：开启后选择简历（默认选中 `isActive` 简历），调用 focus-skills 展示重点技能 chips |
| 面试房间 | 个性化会话展示"个性化面试"标识（数据源 `GET /api/interviews/{id}` 的 `personalized`） |
| 面试报告 | 个性化会话展示"简历技能 vs 实测表现"卡片（数据已含在 strengths/weaknesses 中，前端按前缀识别渲染即可） |

轮询约定：上传后对 `GET /api/resumes/{id}` 每 2s 轮询，上限 60s，超时提示"分析仍在进行，稍后刷新"。

---

## 8. 安全设计

1. **PII 不出域**：手机号/邮箱/身份证在送模型前正则脱敏；`profile_json/digest_json` 不含联系方式；Prompt 与日志不落原文 PII。
2. **文件安全**：扩展名白名单 + 魔数双重校验；UUID 重命名落盘；路径归一化校验；`resume/` 与 `audio/` 目录隔离；文件不提供公网直读（仅后端内部读取）。
3. **配额**：每用户保留 ≤20 份（超限 10010）、每日上传 ≤10 份（超限 10010）；删除释放保留额度。
4. **归属与越权**：所有简历读/写接口校验 `user_id` 归属，越权统一 40401（不暴露资源存在性）。
5. **注入防护**：简历文本进入 Prompt 前不做任何拼接执行；AI 返回仅作为数据渲染，前端文本节点渲染（防 XSS 由前端框架默认转义保证）。
6. **日志红线**：不记录简历全文、不记录 JWT/模型 Key（沿用基线约定）。

---

## 9. 异常与降级

| 异常情况 | 处理方案 | 用户感知 |
|---|---|---|
| 上传格式/大小不合法 | 40001/40002（与音频上传一致） | 明确提示格式或大小限制 |
| 文件损坏/加密/扫描件 | 状态 FAILED + fail_reason | 详情页展示原因，可重传 |
| AI 分析超时/失败 | 重试→降级 Mock→FAILED（10007） | 提示"分析失败，可重试" |
| AI 返回 JSON 不合规 | AiResponseParser 容错→重试→降级 | 无感（结果仍产出） |
| 题干改写失败 | all-or-nothing 降级原题干 | 无感（面试正常进行） |
| 简历技能与题库零交集 | 回退通用组卷 | focus-skills 显示"未命中，将按通用策略出题" |
| 用未分析简历发起个性化面试 | 10009 | 提示先完成简历分析 |
| 分析进行中请求结果 | 10006 | 前端继续轮询 |
| 超配额上传/保留 | 10010 | 提示删除旧简历或明日再传 |

---

## 10. 测试方案

### 10.1 单元测试（JUnit5 + Mockito）

- `ResumeTextExtractorTest`：4 格式抽取、魔数不符、加密 PDF、短文本（每类 ≥2 用例）。
- `ResumeProfileParserTest`：分段、技能识别、PII 脱敏、无标题容错。
- `ResumeScoreCalculatorTest`：六维聚合、边界分、建议数量/排序约束。
- `MockResumeAnalyzerTest`：规则确定性（同输入同输出）、维度分档与建议分级一致。
- `PersonalizedPaperAssemblerTest`：技能命中优先、PROJECT 优先命中、零交集回退、固定种子可复现、题型比例不破坏。
- `ResumeQuotaTest`：保留/每日配额边界。

### 10.2 集成测试（@SpringBootTest + MockMvc，`ai.provider=mock`）

- `ResumeUploadIT`：4 格式上传→轮询至 ANALYZED→详情断言；非法格式 40001；越权 40401；配额 10010。
- `ResumeAnalysisFlowIT`：失败文件→FAILED→reanalyze 成功覆盖；analysis 未就绪 10006。
- `PersonalizedInterviewIT`：带 resumeId 建会话→start 首题为改写题干且 question_id 保留→完整走完→报告含简历对比条目；未分析简历 10009；不带 resumeId 回归基线行为。

### 10.3 手工与 AI 质量验收

- `backend/api.http` 增补 R-01~R-10 用例（对齐协议文档用例编号）。
- 准备 5~8 份样例简历（好/中/差 + 扫描件 PDF + 无标题 md），检查：维度分与人工评估误差 ≤15 分；建议均能在原文找到证据句；HIGH 建议对应维度确实 <40。
- `ai.provider=qwen` 冒烟：真实模型跑通上传→分析→个性化面试全链路，验证 JSON 契约。

---

## 11. 里程碑概览

详细任务分解（编号、交付物、验收标准）见《简历分析与个性化面试_后端开发计划》。

| 里程碑 | 内容 | 预估工作量 |
|---|---|---|
| R1 简历上传与解析闭环 | 依赖/配置/V8 迁移/存储/4 格式抽取/基础 CRUD 接口 | 1.5 天 |
| R2 简历分析 | 结构化解析与脱敏、AiProvider.analyzeResume 双实现、异步编排与状态机、评分聚合与分级建议 | 2 天 |
| R3 个性化面试 | resumeId 校验链、组卷偏好、题干改写与降级、追问/报告注入、focus-skills | 2 天 |
| R4 测试与收口 | 全量 verify、覆盖率、qwen 冒烟、README/进度记录/演示脚本收口 | 1 天 |

---

## 12. 验收标准

- [ ] pdf/docx/md/txt 四格式可上传并完成分析；非法格式、超限、扫描件均有明确错误反馈。
- [ ] 分析结果含总分 + 六维分，维度定义可解释；建议 3~8 条且按高/中/低风险分组，每条含证据句。
- [ ] PII 脱敏：送模型文本与落库 JSON 中无完整手机号/邮箱/身份证。
- [ ] 配额生效：保留 20 份、每日 10 份，超限返回 10010。
- [ ] 带 resumeId 的面试：组卷命中简历技能（有日志/断言证据）、首题题干个性化、评分基准题不变。
- [ ] 改写降级可用：AI 改写异常时面试以原题干正常进行。
- [ ] 报告包含"简历自述技能 vs 实测"对比条目。
- [ ] 不带 resumeId 的既有面试链路回归通过（既有 IT 全绿）。
- [ ] `ai.provider=mock` 下两功能全流程可离线演示；`qwen` 冒烟通过。
- [ ] `mvn verify` 全绿，JaCoCo 行覆盖率 ≥60% 门禁不回退。
- [ ] 既有 9 表结构不变（仅 interview_session 新增可空列），V8 迁移在空库/存量库均可执行。
