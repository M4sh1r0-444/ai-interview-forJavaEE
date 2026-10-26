# AI模拟面试与能力提升平台 —— 简历分析与个性化面试 后端开发计划

> 本计划依据《简历分析与个性化面试_总体开发方案》（下称"本方案"）与《简历分析与个性化面试_接口协议》（下称"协议"）编写，把两个新功能拆解为可执行、可验证的后端任务。
>
> 交付边界：**仅后端 `ai-interview/backend`**。前端只消费协议文档，不在本计划范围内。
>
> 里程碑编号 **R1~R4**（避免与已完成的基线 M1~M6 混淆）。进度事实源仍为《进度记录》，本计划的任务编号（R1-1、R2-3…）供进度记录与提交信息引用。
>
> 硬约束：不破坏基线验收项；既有 9 张表结构不变（仅 `interview_session` 加可空列）；`ai.provider=mock` 下全流程可离线演示；`mvn verify` 与 JaCoCo ≥60% 门禁不回退。

---

## 目录

1. [概述与目标](#1-概述与目标)
2. [前置准备](#2-前置准备)
3. [数据库实施计划](#3-数据库实施计划)
4. [工程结构与类清单](#4-工程结构与类清单)
5. [接口实现清单](#5-接口实现清单)
6. [核心业务实现设计](#6-核心业务实现设计)
7. [安全设计落地](#7-安全设计落地)
8. [测试计划](#8-测试计划)
9. [里程碑与任务分解](#9-里程碑与任务分解)
10. [风险与应对](#10-风险与应对)
11. [验收对照表](#11-验收对照表)
- [附录A 新增错误码](#附录a-新增错误码)
- [附录B 新增配置项](#附录b-新增配置项)
- [附录C api.http 增补用例](#附录c-apihttp-增补用例)

---

## 1. 概述与目标

### 1.1 功能闭环（增量）

```text
F1：上传简历 → 安全校验/落盘 → 4 格式文本抽取 → 结构化解析+PII 脱敏
    → 异步 AI 多维评分 → 高/中/低风险改进建议 → ANALYZED（沉淀 digest）
F2：创建面试(带 resumeId) → 个性化组卷偏好 → AI 题干改写(降级保底)
    → 追问注入简历要点 → 报告"简历 vs 实测"对比
```

### 1.2 增量技术栈（其余沿用基线 §3.2）

| 技术 | 版本/选型 | 用途 |
|---|---|---|
| Apache POI（poi-ooxml） | 5.3.x | docx 文本抽取（XWPFDocument，段落+表格） |
| Apache PDFBox | 3.0.x | pdf 文本层抽取（PDFTextStripper；3.x 用 `Loader.loadPDF`） |
| md/txt | JDK 内置（`Files.readString`，UTF-8→GBK 兜底） | 纯文本读取 |

### 1.3 已确认的开发决策

| 决策点 | 结论 |
|---|---|
| 表数量 | 基线"9 张表"约束属基线范围；本期**新增 2 张**（resume、resume_analysis，总量 11），课程报告需同步说明 |
| 解析失败时机 | 上传接口**同步**完成解析（快、可即时反馈），AI 分析**异步**（`aiExecutor`），对齐报告生成模式 |
| 题干改写策略 | all-or-nothing：AI 改写结果数量/ID 不一致或失败 → 整场用原题干，不做部分替换（可测、可解释） |
| 评分基准 | 个性化只改"题干表述"，`question_id` 与评分链路（key_points 命中）完全不变 |
| 简历文本送模型 | 脱敏后全文，超 `max-analysis-chars`（默认 20000）截断送模型，全量落库 `char_count` 计数 |
| 报告对比落点 | 写入既有 `strengths_json/weaknesses_json/report_text`，`evaluation_report` 不加列 |
| WS 消息 | 不新增消息类型；个性化题干走既有 QUESTION 推送 |

### 1.4 交付物总览

| 交付物 | 说明 |
|---|---|
| `resume/` 业务包 | 2 实体 + 2 Mapper + 6 Service/Storage + 1 Controller + DTO（§4） |
| Flyway `V8__resume_feature.sql` | 2 新表 + 1 加列（§3） |
| REST 新增 8 + 扩展 2 | 见 §5，协议用例 R-01~R-10 |
| `AiProvider` +2 方法 | Mock/OpenAiCompatible 双实现 + 4 个模型类 |
| 既有文件改动 10 处 | 见 §4.3 改动点清单 |
| 测试 | 单测 6 类 ≥30 用例；IT 3 类 ≥15 用例；api.http +10 用例（§8） |

---

## 2. 前置准备

### 2.1 pom.xml 新增依赖

```xml
<!-- docx 文本抽取（传递 xmlbeans/commons-compress 等，与现有依赖无冲突） -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>
<!-- pdf 文本层抽取 -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
</dependency>
```

- 版本以中央仓库当期补丁版为准（上为写作时最新稳定版）；POI 5.3 与 PDFBox 3.0 均兼容 JDK 17。
- 不引入 `poi-scratchpad`（不支持 .doc 老格式，协议已声明范围外）。

### 2.2 application.yml 新增配置

```yaml
app:
  resume:
    allowed-types: pdf,docx,md,txt        # 扩展名白名单（Content-Type 复核 + 魔数）
    min-text-chars: 200                   # 抽取字符数低于此值 → 解析失败(10008)
    max-analysis-chars: 20000             # 送模型文本上限，超出截断
    max-count-per-user: 20                # 保留上限（逻辑删除不占额度）
    daily-upload-limit: 10                # 每用户每日上传上限
```

全部支持环境变量覆盖（`APP_RESUME_DAILY_UPLOAD_LIMIT` 等，风格同 `app.websocket.*`）。multipart 全局 10MB/12MB **不调整**。

### 2.3 环境

无新增中间件（MySQL/Redis/AI 配置复用）。`mvn verify` 前置条件不变：dev compose 的 MySQL/Redis 在跑。

---

## 3. 数据库实施计划

新增 `src/main/resources/db/migration/V8__resume_feature.sql`（DDL 全文见本方案 §5）。实施要点：

1. 风格对齐 `V1__init_schema.sql`（引擎/字符集子句、注释写法、`DATETIME NOT NULL`、索引命名 `idx_` 前缀）。
2. `resume_analysis.resume_id` 唯一键承载"一份简历一条结果"；重分析实现为事务内 delete + insert。
3. `interview_session.resume_id` 可空、加普通索引；存量数据不受影响（NULL 兼容）。
4. 空库（Flyway 全量）与存量库（V7 之后增量）两条路径都要验证。
5. 实体映射：JSON 列用 `@TableField(typeHandler = JacksonTypeHandler.class)`（模式同 `Question.skillTagsJson`）；`Resume.digestJson → ResumeDigest`、`ResumeAnalysis.suggestionsJson → List<Suggestion>` 等。

---

## 4. 工程结构与类清单

### 4.1 新增目录

```text
com.example.interview.resume
├── controller
│   └── ResumeController              # R-01~R-08 八个端点
├── service
│   ├── ResumeService                 # 上传受理/列表/详情/删除/配额/默认简历/归属校验
│   ├── ResumeAnalysisService         # 异步分析编排 + 状态机 + reanalyze
│   ├── ResumeTextExtractor           # 魔数分发：PDFBox/POI/纯文本 + 护栏
│   ├── ResumeProfileParser           # 板块分段/技能识别/项目切分/PII 脱敏（纯函数）
│   └── ResumeScoreCalculator         # 六维聚合 + 建议数量/排序约束
├── storage
│   └── ResumeStorageService          # 白名单+魔数+UUID 落盘（照抄 AudioStorageService）
├── entity
│   ├── Resume
│   └── ResumeAnalysis
├── mapper
│   ├── ResumeMapper
│   └── ResumeAnalysisMapper
└── dto
    ├── ResumeItemVO / ResumeDetailVO / ResumeAnalysisVO / UploadResumeVO
    ├── FocusSkillsVO / SuggestionDTO / ResumeDigest / ResumeProfile
    └── FocusSkillsQuery（可省，参数少）
```

### 4.2 新增类清单（实施核对表）

| 类 | 职责 | 关键方法 |
|---|---|---|
| `ResumeController` | 8 端点，全部 `Result.ok(...)` | `upload/list/detail/analysis/reanalyze/active/delete/focusSkills` |
| `ResumeService` | 受理与查询 | `upload(MultipartFile, Long positionId)`、`page(page,size)`、`detail(id)`、`analysisOf(id)`、`reanalyze(id)`、`setActive(id)`、`delete(id)`、`checkOwnership(id)` |
| `ResumeAnalysisService` | 异步编排 | `@Async("aiExecutor") analyze(Long resumeId)`、状态条件更新 `markStatus(id, from, to)` |
| `ResumeTextExtractor` | 抽取 | `extract(bytes, fileType) → String`（抛 `BizException(RESUME_PARSE_FAILED)`） |
| `ResumeProfileParser` | 解析脱敏 | `parse(String text, Position pos) → ResumeProfile`、`toDigest(profile) → ResumeDigest`、`maskPii(text)`（public 供单测） |
| `ResumeScoreCalculator` | 聚合 | `aggregate(AiResumeAnalysis) → ResumeAnalysis`（权重、clamp、排序、数量约束） |
| `ResumeStorageService` | 落盘 | `store(MultipartFile) → storedPath`、`resolve(path)` |
| `MockAiProvider` 增量 | 规则实现 | `analyzeResume`、`personalizeQuestions`（§6.4/§6.6） |
| `OpenAiCompatibleProvider` 增量 | 真实实现 | 同两方法（WebClient，`response_format=json_object`，复用重试/降级） |
| `ai/model/` 增量 | 4 个模型类 | `ResumeAnalysisContext`、`AiResumeAnalysis`、`QuestionPersonalizeContext`、`AiQuestionPersonalization` |

### 4.3 既有文件改动点清单（全部为加法）

| 文件 | 改动 |
|---|---|
| `common/result/ResultCode.java` | +5 枚举（附录A） |
| `ai/provider/AiProvider.java` | +2 方法（含 javadoc，默认不走 default：两实现必须显式实现，防止漏实现静默失效） |
| `ai/prompt/PromptBuilder.java` | +`analyzeResumePrompt(ctx)`、`personalizePrompt(ctx)`；`nextQuestionPrompt` 增可选"候选人简历要点"段 |
| `interview/dto/CreateInterviewRequest.java` | +`private Long resumeId;`（可选，无校验注解，Service 校验） |
| `interview/entity/InterviewSession.java` | +`resumeId` 字段 |
| `interview/service/InterviewService.java` | `create`：resumeId 校验链（归属 40401 → 状态 10009）并落库；`start`：加载 digest → `assemblePaper(..., digest)` → 改写调用与降级 |
| `question/service/QuestionService.java` | `assemblePaper` 增 digest 透传（重载，原签名保留委托） |
| `interview/service/PaperAssembler.java` | +`assemble(questions, difficulty, rounds, ResumeDigest digest)` 重载（§6.5） |
| `interview/service/TurnEngine.java` | 装配 `NextQuestionContext` 时填充 `resumeDigest`（个性化会话） |
| `assessment/service/ReportService.java` | `ReportContext` 填充 `resumeDigest`；报告生成后追加对比条目（§6.8） |
| `interview/dto/InterviewDetailVO.java` | +`resumeId/resumeFileName/personalized` 三字段 |
| `resources/application.yml` | +`app.resume.*`（§2.2） |

不改动：`SecurityConfig`（`/api/resumes/**` 走默认 `authenticated()`）、`GlobalExceptionHandler`（4xx/上传超限映射已覆盖）、WebSocket 全链路、状态机 `InterviewStateMachine`。

---

## 5. 接口实现清单

用例编号与协议文档一致；"验收"列对应 `mvn verify` 内的测试与 api.http 手工用例。

| 用例 | 方法与路径 | 实现位置（Controller 方法 → Service） | 自动化验收 |
|---|---|---|---|
| R-01 | POST `/api/resumes` | `ResumeController.upload` → `ResumeService.upload` | `ResumeUploadIT#uploadFourFormats`、api.http R-01 |
| R-02 | GET `/api/resumes` | `page` | `ResumeUploadIT#listPaged`、R-02 |
| R-03 | GET `/api/resumes/{id}` | `detail` | `ResumeUploadIT#detailOwnership`、R-03 |
| R-04 | GET `/api/resumes/{id}/analysis` | `analysisOf` | `ResumeAnalysisFlowIT#notReadyThenReady`、R-04 |
| R-05 | POST `/api/resumes/{id}/reanalyze` | `reanalyze` → `ResumeAnalysisService.analyze` | `ResumeAnalysisFlowIT#reanalyzeOverwrites`、R-05 |
| R-06 | PUT `/api/resumes/{id}/active` | `setActive` | `ResumeUploadIT#setActiveExclusive`、R-06 |
| R-07 | DELETE `/api/resumes/{id}` | `delete` | `ResumeUploadIT#logicalDelete`、R-07 |
| R-08 | GET `/api/resumes/{id}/focus-skills` | `focusSkills`（纯规则） | `PersonalizedInterviewIT#focusSkills`、R-08 |
| R-09 | POST `/api/interviews`（+resumeId） | `InterviewService.create` 扩展 | `PersonalizedInterviewIT#createWithResume`、R-09 |
| R-10 | GET `/api/interviews/{id}`（+3 字段） | `InterviewDetailVO` 扩展 | `PersonalizedInterviewIT#detailFields`、R-10 |

---

## 6. 核心业务实现设计

### 6.1 上传受理（ResumeService.upload）

```text
1. 配额检查：未删除计数 ≥ max-count-per-user → 10010；当日上传数 ≥ daily-upload-limit → 10010
2. positionId 非空 → 存在性校验（不存在 40001，与基线管理端校验同型）
3. ResumeStorageService.store：扩展名白名单 → Content-Type 复核（放行 octet-stream）
   → 魔数校验（PDF:%PDF- / DOCX:PK\x03\x04 / MD,TXT:UTF-8 或 GBK 可解码且控制字符占比 <5%）
   → 大小 ≤10MB → UUID 命名落盘 {app.upload-dir}/resume/yyyyMM/
4. ResumeTextExtractor.extract（异常 → 落库 FAILED + failReason，仍返回 code=0，data.status=FAILED）
   字符数 < min-text-chars → FAILED（failReason="未检测到可分析文本…"）
5. 解析成功：status=ANALYZING 落库 → 事务提交后触发 ResumeAnalysisService.analyze
   （事务提交后异步，模式同 TurnEngine；避免异步线程读不到记录）
6. 返回 UploadResumeVO
```

### 6.2 异步分析与状态机（ResumeAnalysisService）

- `markStatus(id, expectFrom, to)`：MyBatis-Plus 条件更新（`WHERE status=expectFrom`），失败即并发冲突 → 记 WARN 放弃（同简历并发 reanalyze 被 10006 挡在入口，此处为兜底）。
- 分析流程：`ANALYZING` → 加载脱敏文本（截断至 max-analysis-chars）+ profile + 岗位能力模型 → `AiProvider.analyzeResume` → `ResumeScoreCalculator.aggregate` → 事务内（delete 旧 analysis + insert 新 + 更新 resume.digest_json/status=ANALYZED）。
- 任何异常 → `status=FAILED`，`failReason` 取首条可读信息（不含堆栈）；AI 失败与解析失败共用 FAILED 态，`analysis` 接口分别返回 10007/10008（依据 `fail_reason` 前缀分类或 status 子类型字段，实现取前者：约定 failReason 以 `[PARSE]`/`[AI]` 前缀标记，接口据此映射错误码）。
- `@Async("aiExecutor")`：与面试轮次共用线程池（core4/max8/queue100）；分析任务量小（配额约束下），不新建池。

### 6.3 文本抽取（ResumeTextExtractor）

| 格式 | 实现 | 护栏 |
|---|---|---|
| PDF | `Loader.loadPDF(bytes)` → `PDFTextStripper.getText()` | `InvalidPasswordException` → "简历已加密"；抽取异常统一 FAILED |
| DOCX | `XWPFDocument` 遍历 `getParagraphs()` + `getTables()`（表格文字按单元格拼接，简历常见双栏/表格布局需要） | 异常 → "文件无法解析" |
| MD/TXT | UTF-8 解码失败退 GBK，再失败 → "不支持的文本编码" | 控制字符占比 >5% → 拒绝（防伪装成 txt 的二进制） |

抽取为**纯函数**（bytes+type → text），单测直接喂字节数组，不落盘。

### 6.4 结构化解析与脱敏（ResumeProfileParser）

1. 分段：行级扫描，命中标题词典（`教育背景|教育经历|专业技能|技术栈|项目经历|项目经验|实习经历|工作经历|荣誉奖项|证书|自我评价`，允许 `#`/`【】`/`—` 修饰）即切换当前板块；全无命中 → 单一 `RAW` 板块。
2. 技能识别：`KeywordExtractor` 词典 ∪ 岗位 `competency_model_json` 技能名，按出现次数与分层词（精通/熟悉/掌握/了解）定 `level`。
3. 项目切分：项目板块内按行首"项目名/项目名称"或"《…》"模式分段；每段抽 techStack（技能交集）与首个量化句为 highlight。
4. PII 脱敏（`maskPii`）：手机 `1[3-9]\d{9}`→前3后2；邮箱 `[\w.+-]+@[\w-]+\.[\w.]+`→首字符+`***`+域名；身份证 18 位→前3后2。**先脱敏再分段入库与送模型**。
5. `toDigest(profile)`：仅保留 skills/projects/experiences/education，不含联系方式（digest 契约见本方案 §3.2）。

### 6.5 个性化组卷（PaperAssembler 重载）

```java
public List<Question> assemble(Collection<Question> qs, int difficulty, int rounds, ResumeDigest digest)
```

- `digest == null` → 委托既有三参方法（行为零变化）。
- 题型配额、难度带（|difficulty-目标| 升序前 3 倍配额）、行为题不放第一题、TECHNICAL 补位——**全部复用既有逻辑**；只替换"带内挑选"的比较器：

```text
带内优先级（高→低）：
  ① 覆盖未覆盖技能 且 命中简历技能（skill_tags ∩ digest.skills ≠ ∅）
  ② 命中简历技能
  ③ 覆盖未覆盖技能（现状）
  ④ 随机（现状）
PROJECT 类型额外规则：命中简历技能的题优先；存在命中题时保证 ≥1 道入选
零命中 → 回退既有算法（INFO 日志一次）
```

- 保持可注入 `Random` 种子（既有单测模式），新增用例断言：固定种子下命中题必选、比例不变。

### 6.6 AiProvider 双实现

**`analyzeResume(ctx)`**

- Mock（规则打分，与维度定义一一对应，clamp 0~100）：

| 维度 | 信号 → 加分 |
|---|---|
| basic | 基础 40；姓名/联系方式（脱敏命中）/求职意向 各 +10~15；四大板块存在 每块 +5 |
| education | 基础 40；学历词（硕士+25/本科+15）；专业相关词 +15；排名或核心课程 +15 |
| skill | 基础 30；岗位技能覆盖率 ×50；分层词出现 +10；技能数 > 岗位技能×3（堆砌）−10 |
| project | 基础 30；项目数（1 个 +15 / ≥2 +25）；STAR 信号词（负责/实现/优化/结果）每词 +8（封顶 24）；量化句比例 ≥20% +15 / 10~20% +8；复杂度词（并发/压测/分布式/性能）+6 |
| experience | 基础 40；实习 +25；竞赛/开源 +15；证书 +10；岗位相关 +10 |
| expression | 基础 40；动词开头句比例 ≥30% +15 / 10~30% +8；量化句比例 +15/+8；字符数 800~4000 +15 / 边缘带 +7；重复行 >5 −10 |

  建议生成按本方案 §3.4 分级规则；证据句取该维度板块首句（脱敏后）。信号权重实现可微调，但**分档阈值（40/70）与数量约束（3~8、HIGH 优先排序）由单测锁定**。
- OpenAiCompatible：Prompt 见本方案 §4.2；`AiResponseParser` 围栏容错；重试 1 次（退避 1s）→ 降级 Mock 规则 → 仍失败抛出（Service 置 FAILED）。

**`personalizeQuestions(ctx)`**

- Mock：`"结合你在《{项目名}》项目中使用 {techStack 前2项} 的经历——" + 原题干`（无项目 → 技术栈版前缀；都无 → 原题干）。一次遍历生成，无耗时。
- OpenAiCompatible：一次调用批量改写，输出 `{"items":[{"questionId":1,"content":"…"}]}`；校验：questionId 集合与输入一致、每条 10~500 字。不一致/异常 → 抛出，由 `InterviewService.start` 降级整场原题干（WARN 日志）。

### 6.7 个性化面试集成（InterviewService）

- `create`：既有校验 → resumeId 非空时 `ResumeService.checkOwnership`（40401）→ `status != ANALYZED` → `BizException(RESUME_NOT_ANALYZED)` → 落库 `resume_id`。
- `start`（仅在带 resume_id 时增加两步，均在状态机迁移成功之后）：

```text
1. resume.digest_json → ResumeDigest（缺失/解析失败 → 按无 digest 继续，WARN）
2. assemblePaper(positionId, difficulty, rounds, digest)   ← §6.5 偏好组卷
3. provider.personalizeQuestions(岗位, digest, 题目列表)
   → 成功且校验通过：题目落库 content_text = 改写文本（question_id 不变）
   → 失败：content_text = 原题干（降级，无感知）
4. 其余（首题推送/定时器）走既有链路
```

- 超时控制：改写调用受 `ai.timeout`（30s）约束；mock 模式即时。真实模式下 start 变慢为**已接受的权衡**（协议 §4.1 已注明），如需优化（异步改写+补推）列为后续可选项，本期不做。

### 6.8 追问与报告注入

- `TurnEngine`：会话有 resume_id 时 `NextQuestionContext.resumeDigest = digest`；`PromptBuilder.nextQuestionPrompt` 追加固定段——"候选人简历要点：技术栈{…}；主要项目{名+亮点}。追问可结合其项目深挖实现细节、技术选型原因、遇到的困难。"（digest 为 null 时不追加，Prompt 与基线逐字节一致）。
- Mock 追问：`FollowupRuleEngine` 生成 FOLLOW_UP 决策时，若当前题为 PROJECT 且 digest 存在 → 追问句选"项目深挖"模板并内插项目名（如"在《{project}》里，{tech} 的选型为什么不用 {替代}？"）。
- `ReportService`：`ReportContext.resumeDigest` 填充；报告落库前追加对比条目（技能→题目维度分映射：相关题目平均分 <60 进 weaknesses、≥80 进 strengths，文案见本方案 §3.6⑤，Mock 与真实 Provider 同规则，保证离线演示效果一致）。

### 6.9 配额与归属

- 保留计数：`SELECT COUNT(*) WHERE user_id=? AND deleted=0` ≥ 20 → 10010。
- 每日上传：`COUNT(*) WHERE user_id=? AND deleted=0 AND created_at >= 当日零点` ≥ 10 → 10010（时区 Asia/Shanghai；无需 Redis，直接 COUNT 即可——配额量级小）。
- 归属：所有按 id 操作先 `user_id` 匹配，不匹配/不存在/已删除统一 `40401`（复用 `NOT_FOUND_OR_FORBIDDEN`）。

---

## 7. 安全设计落地（对应本方案 §8）

| # | 措施 | 落点 |
|---|---|---|
| 1 | PII 脱敏先于入库与送模型 | `ResumeProfileParser.maskPii`（上传链路强制调用，单测锁定正则） |
| 2 | 文件白名单 + 魔数 + UUID + 目录隔离 + 路径归一化 | `ResumeStorageService`（照抄 `AudioStorageService` 三查一 resolve） |
| 3 | 配额防滥用 | `ResumeService` §6.9 |
| 4 | 越权 40401 | `checkOwnership` 统一入口 |
| 5 | 简历文件不公网直读 | 不新增静态资源映射；Nginx 不暴露 `uploads/`（现状即如此，验证项） |
| 6 | 日志红线 | 不打简历全文；AI Prompt 日志只打长度与 traceId（对齐基线） |
| 7 | 上传请求体限制 | 复用 multipart 10MB/12MB + 全局异常映射 |

---

## 8. 测试计划

### 8.1 单元测试（不起 Spring 容器，秒级）

| 测试类 | 覆盖 | 用例数 |
|---|---|---|
| `ResumeTextExtractorTest` | 4 格式抽取、魔数不符、加密 PDF、短文本、二进制伪装 txt | ≥8 |
| `ResumeProfileParserTest` | 分段、技能识别、项目切分、PII 三类脱敏、无标题容错 | ≥10 |
| `ResumeScoreCalculatorTest` | 六维聚合/clamp/四舍五入、建议 3~8 条约束、HIGH 优先排序、Top2→LOW | ≥8 |
| `MockResumeAnalyzerTest` | 同输入同输出（确定性）、维度分档与建议分级一致性、空简历容错 | ≥5 |
| `PersonalizedPaperAssemblerTest` | 命中优先、PROJECT 保底 1 题、零命中回退、固定种子复现、题型比例不破坏 | ≥8 |
| `ResumeQuotaTest` | 保留/每日配额边界、逻辑删除释放额度 | ≥4 |

### 8.2 集成测试（@SpringBootTest + MockMvc，`ai.provider=mock`）

| 测试类 | 场景 |
|---|---|
| `ResumeUploadIT` | 四格式上传→轮询至 ANALYZED→详情含 analysis/digest；.doc 拒绝 40001；越权 40401；配额 10010；删除后列表不可见、不可再引用 |
| `ResumeAnalysisFlowIT` | 扫描件→FAILED(10008)→reanalyze 成功覆盖（旧结果替换）；ANALYZING 期 analysis 返回 10006；active 互斥 |
| `PersonalizedInterviewIT` | focus-skills 命中/缺口/零命中；带 resumeId 创建→start 首题含项目名且 questionId 非空；改写降级（注入抛错的 Provider stub）面试仍正常；跑完 5 轮→报告含"简历中的…"条目；未分析简历 10009；**不带 resumeId 全链路回归（对齐既有 InterviewTextFlowIT 断言）** |

轮询写法参照既有报告类 IT（循环 sleep + 断言，mock 下分析在 5s 内完成）。

### 8.3 手工与 AI 质量

- `backend/api.http` 增补 R-01~R-10（附录C），与协议示例一一对应。
- 样例简历集（放入 `qa/samples/`）：优秀 docx / 普通 pdf / 差 md / 扫描件 pdf / 无标题 txt，用于 IT fixture 与人工检查（维度误差 ≤15 分、建议均有证据句）。
- `ai.provider=qwen` 冒烟脚本 `qa/verify_resume_feature.sh`：真实模型跑通 上传→分析→个性化面试→报告。

---

## 9. 里程碑与任务分解

> 每个 R 收口 = 任务全绿 + 对应 IT 通过 + api.http 用例可跑。工作量按单人有效开发时间估。

### R1 简历上传与解析闭环（1.5 天）

| 编号 | 任务 | 交付物 | 验收 |
|---|---|---|---|
| R1-1 | pom 依赖 + `app.resume.*` 配置 + ResultCode 5 新码 | pom/yml/枚举 diff | 编译过；新码 HTTP 语义正确 |
| R1-2 | Flyway V8（2 表 + session 加列）+ 实体/Mapper | V8 脚本、2 实体 2 Mapper | 空库/存量库迁移均成功 |
| R1-3 | ResumeStorageService + ResumeTextExtractor | 存储与抽取服务 | 对应单测全绿（魔数/加密/短文本） |
| R1-4 | 上传受理 + 列表/详情/删除/active 接口 | ResumeController/Service + VO | ResumeUploadIT 主链路绿；api.http R-01~R-03、R-06、R-07 可跑 |
| R1-5 | 配额校验 | §6.9 逻辑 | ResumeQuotaTest + IT 配额分支绿 |

### R2 简历分析（2 天）

| 编号 | 任务 | 交付物 | 验收 |
|---|---|---|---|
| R2-1 | ResumeProfileParser（分段/识别/脱敏/digest） | 纯函数解析器 | ResumeProfileParserTest ≥10 用例绿 |
| R2-2 | AiProvider.analyzeResume：Mock 规则 + 模型类 | 4 模型类 + Mock 实现 | MockResumeAnalyzerTest 绿（确定性+分档） |
| R2-3 | OpenAiCompatible 实现 + PromptBuilder 分析 Prompt | 真实实现 + Prompt | 解析容错单测；qwen 手工冒烟通过 |
| R2-4 | ResumeAnalysisService 异步编排 + 状态机 + reanalyze | 异步分析链路 | ResumeAnalysisFlowIT 绿（含覆盖重分析） |
| R2-5 | ResumeScoreCalculator + analysis 接口 | 聚合 + R-04/R-05 端点 | ScoreCalculatorTest 绿；api.http R-04、R-05 |

### R3 个性化面试（2 天）

| 编号 | 任务 | 交付物 | 验收 |
|---|---|---|---|
| R3-1 | resumeId 入参与校验链 + 会话关联 | CreateInterviewRequest/实体/Service 扩展 | 10009/40401 分支 IT 绿；普通面试零回归 |
| R3-2 | PaperAssembler 简历偏好重载 + QuestionService 透传 | §6.5 | PersonalizedPaperAssemblerTest ≥8 用例绿 |
| R3-3 | personalizeQuestions 双实现 + start 集成与降级 | Mock/真实实现 + start 两步 | 改写与降级分支 IT 绿；questionId 保留断言 |
| R3-4 | TurnEngine/PromptBuilder 追问注入 + Mock 项目深挖 | 上下文注入 | 追问 Prompt 含简历段（单测断言）；无简历 Prompt 逐字节不变 |
| R3-5 | ReportService 对比条目 + focus-skills 接口 | 报告增强 + R-08 | 报告含"简历中的…"条目 IT 绿；R-08 可跑 |
| R3-6 | InterviewDetailVO 扩展 + 面试详情新字段 | VO 3 字段 | R-10 断言绿 |

### R4 测试与收口（1 天）

| 编号 | 任务 | 交付物 | 验收 |
|---|---|---|---|
| R4-1 | 全量 `mvn verify` + 覆盖率复核 | 全绿构建 | 166+新增用例全绿；JaCoCo ≥60% |
| R4-2 | qwen 真实 Provider 冒烟 | `qa/verify_resume_feature.sh` | 真实链路跑通（JSON 契约校验） |
| R4-3 | 文档收口 | backend/README 功能节、根 README、进度记录 R1~R4、api.http | 四处与实现一致 |
| R4-4 | 演示脚本增补 | `答辩演示脚本.md` + 简历环节 | 离线（mock）可完整演示两新功能 |

---

## 10. 风险与应对

| # | 风险 | 应对 |
|---|---|---|
| 1 | POI 传递依赖体积大/与现有依赖冲突（xmlbeans、commons-compress、log4j-api） | 只引 `poi-ooxml`；`mvn dependency:tree` 复核；enforcer 门禁已有 |
| 2 | PDFBox 3.x 与常见 2.x 示例 API 不一致（`Loader.loadPDF`） | 按 3.x 官方迁移说明实现；抽取器隔离在单类，出问题只改一处 |
| 3 | 大 PDF 抽取耗时/内存 | 抽取在请求线程但文件 ≤10MB；送模型截断 20000 字符控制 token 成本 |
| 4 | 真实 AI 输出不稳定（字段缺失/超量建议） | Schema 校验 + 重试 1 次 + 降级 Mock；数量约束在 Calculator 统一裁剪 |
| 5 | qwen 模式 start 变慢（批量改写 1 次调用） | 受 ai.timeout 约束 + 降级保底；文档明示；异步化为后续可选 |
| 6 | 并发 reanalyze/分析竞态 | 入口 10006 挡并发；`markStatus` 条件更新兜底；analysis 唯一键保证单结果 |
| 7 | 磁盘增长 | 配额 20 份×10MB 上限；逻辑删除文件保留（审计），课程设计规模可接受 |
| 8 | 表数超基线"9 张"约束 | 方案已声明为基线外扩展；课程报告与答辩口径统一 |
| 9 | 简历内容导致 Prompt 注入（文本里藏指令） | Prompt 固定模板 + 用户内容置于定界段内；输出仅作数据渲染不执行；后端对输出做结构校验 |

---

## 11. 验收对照表（本方案 §12 → 本计划落点）

| 方案验收项 | 落点 |
|---|---|
| 四格式上传与分析、非法输入反馈 | R1-3/R1-4、ResumeUploadIT |
| 总分+六维、建议 3~8 条风险分组+证据句 | R2-2/R2-5、ScoreCalculatorTest、AI 质量抽测 |
| PII 脱敏 | R2-1、ResumeProfileParserTest 脱敏分支 |
| 配额 20/10 → 10010 | R1-5、ResumeQuotaTest |
| 个性化组卷命中 + 首题改写 + 评分基准不变 | R3-2/R3-3、PersonalizedPaperAssemblerTest、PersonalizedInterviewIT |
| 改写降级可用 | R3-3 降级分支 IT |
| 报告简历对比条目 | R3-5、PersonalizedInterviewIT |
| 普通面试零回归 | R3-1、既有 InterviewTextFlowIT 全绿 |
| mock 离线全流程 + qwen 冒烟 | R4-2、qa 脚本 |
| verify 全绿 + 覆盖率不回退 | R4-1 |
| 表结构约束（V8 兼容空库/存量库） | R1-2 |

---

## 附录A 新增错误码

| 枚举 | code | message | httpStatus |
|---|---:|---|---:|
| `RESUME_NOT_READY` | 10006 | 简历解析/分析进行中 | 200 |
| `RESUME_ANALYSIS_FAILED` | 10007 | 简历分析失败，可重试 | 200 |
| `RESUME_PARSE_FAILED` | 10008 | 简历解析失败 | 200 |
| `RESUME_NOT_ANALYZED` | 10009 | 简历尚未完成分析，无法用于个性化面试 | 200 |
| `RESUME_QUOTA_EXCEEDED` | 10010 | 简历数量/频次超出限额 | 200 |

> 实现注记：上传接口对"文件级失败"返回 `code=0 + data.status=FAILED`（协议 §2.2）；10007/10008 仅用于 `GET /{id}/analysis` 等查询态接口，通过 `fail_reason` 前缀（`[PARSE]`/`[AI]`）区分。

## 附录B 新增配置项

| 配置 | 默认 | 环境变量 | 说明 |
|---|---|---|---|
| `app.resume.allowed-types` | `pdf,docx,md,txt` | — | 扩展名白名单 |
| `app.resume.min-text-chars` | `200` | — | 低于此字符数判解析失败 |
| `app.resume.max-analysis-chars` | `20000` | — | 送模型文本上限（截断） |
| `app.resume.max-count-per-user` | `20` | `APP_RESUME_MAX_COUNT_PER_USER` | 保留上限 |
| `app.resume.daily-upload-limit` | `10` | `APP_RESUME_DAILY_UPLOAD_LIMIT` | 每日上传上限 |

## 附录C api.http 增补用例

| 编号 | 用例 | 断言要点 |
|---|---|---|
| R-01 | 上传 docx（positionId=1） | code=0，status ∈ {ANALYZING, ANALYZED} |
| R-02 | 列表 | 分页结构，含 overallScore（ANALYZED 后） |
| R-03 | 详情 | 含 analysis（六维+suggestions）与 digest |
| R-04 | analysis | ANALYZED 前 10006/后 200+data；断言风险分组排序 |
| R-05 | reanalyze | 覆盖后 createdAt 变化；ANALYZING 中再调 10006 |
| R-06 | active | 互斥生效（旧默认 isActive=false） |
| R-07 | delete | 列表消失；再用该 resumeId 建面试 40401 |
| R-08 | focus-skills | matched/gaps/hitRatio 结构 |
| R-09 | 带 resumeId 创建面试 | data 含 resumeId/personalized；未分析简历 10009 |
| R-10 | start + messages + detail | 首题含项目名改写、questionId 非空；detail 三新字段 |
