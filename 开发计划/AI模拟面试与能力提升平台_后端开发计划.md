# AI模拟面试与能力提升平台 —— 后端开发计划

> 本计划依据《AI模拟面试与能力提升平台_系统实现方案.md》（下称"方案"）编写，把方案中的后端要求拆解为可执行、可验证的开发任务。文中"方案 §x"均指向该文档的章节号。
>
> 交付边界：**仅后端 `ai-interview/backend`**。前端只保留目录约定与联调接口契约，不在本计划范围内。

---

## 目录

1. [概述与目标](#1-概述与目标)
2. [开发环境搭建](#2-开发环境搭建)
3. [数据库实施计划](#3-数据库实施计划)
4. [工程结构与模块设计](#4-工程结构与模块设计)
5. [接口实现清单](#5-接口实现清单)
6. [核心业务实现设计](#6-核心业务实现设计)
7. [安全设计落地](#7-安全设计落地)
8. [测试计划](#8-测试计划)
9. [里程碑与任务分解](#9-里程碑与任务分解)
10. [部署方案](#10-部署方案)
11. [风险与应对](#11-风险与应对)
12. [验收对照表](#12-验收对照表)
- [附录A 错误码表](#附录a-错误码表)
- [附录B 配置项清单](#附录b-配置项清单)
- [附录C 核心流程 curl 验证脚本](#附录c-核心流程-curl-验证脚本)

---

## 1. 概述与目标

### 1.1 系统闭环

```text
选择岗位 → 题库组卷 → AI多轮面试 → 动态追问
       → 多维评估 → 生成报告 → 学习推荐 → 能力成长记录
```

后端承担其中全部业务逻辑：认证鉴权、岗位与题库、面试状态机与实时推送、大模型调用与容错、知识库检索（轻量 RAG）、评分与报告、能力成长与推荐、管理端维护。

### 1.2 技术栈（固定，来自方案 §3.2 / §3.3）

| 层 | 技术 | 版本/选型 |
|---|---|---|
| JDK | Java | 17（本机为 JDK 25，用 `--release 17` 编译，见 §2.1） |
| 框架 | Spring Boot | 3.3.x（建议 3.3 线最新补丁版） |
| Web | Spring MVC | 随 Boot |
| 安全 | Spring Security 6 + JWT(jjwt 0.12.x) | 无状态认证 |
| ORM | MyBatis-Plus 3.5.x | 用 `mybatis-plus-spring-boot3-starter` |
| 数据库 | MySQL | 8.0（utf8mb4，FULLTEXT + ngram） |
| 缓存 | Redis | 7（token 黑名单、限流、会话缓存） |
| 实时 | Spring WebSocket | 面试过程推送 |
| HTTP 客户端 | Spring WebClient（WebFlux 仅作客户端） | 调用大模型 |
| 校验 | Jakarta Validation | 请求参数校验 |
| 迁移 | Flyway（flyway-mysql） | 建表与种子数据 |
| 构建 | Maven | 3.9.x |
| AI | OpenAI 兼容 Chat API；演示模型 qwen-plus；`MockAiProvider` 离线兜底 | 默认 `ai.provider=mock` |

### 1.3 已确认的开发决策

| 决策点 | 结论 |
|---|---|
| MySQL / Redis 来源 | Docker Compose 容器提供（本机 colima 已运行，未安装原生 MySQL/Redis） |
| JDK | 本机仅 JDK 25；编译统一 `maven.compiler.release=17`；**运行建议安装 Temurin 17**（规避 CGLIB/ByteBuddy 对新 class 文件版本的兼容问题，见 §11 R1） |
| AI Provider | 双实现齐备，默认 `mock`，离线可完整演示；配置环境变量后切真实千问 |
| 管理员操作日志 | 按方案 §15.10"或应用日志"选项，用 SLF4J 结构化日志记录，**不建第 10 张表**，严守 9 张表约束 |

### 1.4 后端交付物总览

| 交付物 | 说明 |
|---|---|
| `backend/` Maven 工程 | Spring Boot 3.3 单体，10 个业务包 + common（§4） |
| Flyway 脚本 6 个 | V1 建表（9 张表完整 DDL）+ V2~V6 种子数据（§3.4） |
| REST 接口路径 31 个（含 §5.5 扩展组）+ WebSocket 1 个 | 见 §5 接口清单 |
| 单元测试 ≥ 10 个测试类 | 纯逻辑 JUnit5 + Mockito（§8.1） |
| 集成测试 ≥ 7 个测试类 | MockMvc + Testcontainers MySQL（§8.2） |
| `docker-compose.yml` + `Dockerfile` | mysql/redis/backend/frontend/nginx 五服务（§10） |
| `backend/README.md` | 本地启动、联调、切换 AI Provider、常见问题 |

---

## 2. 开发环境搭建

### 2.1 JDK 与 Maven

本机现状（已检测）：JDK 25（Temurin，arm64）、Maven 3.9.16、Docker（colima）运行中、Node 22（仅前端用）。MySQL/Redis 未安装，全部走 Docker。

```bash
# 建议安装 Temurin 17 用于"运行"（编译用 release 17 即可，JDK 25 也能编）：
brew install --cask temurin@17
# 查看与切换：
/usr/libexec/java_home -V
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

`pom.xml` 中统一：

```xml
<properties>
  <java.version>17</java.version>
  <maven.compiler.release>17</maven.compiler.release>
</properties>
```

> 若坚持用 JDK 25 运行，遇到 CGLIB/ByteBuddy 报 `Unsupported class file major version` 时，加 JVM 参数 `-Dnet.bytebuddy.experimental=true` 临时规避（§11 R1）。开发环境以 Temurin 17 运行为准，不依赖该参数。

### 2.2 Docker Compose 提供开发依赖

工作目录 `ai-interview/` 下新建 `docker-compose.dev.yml`（开发期只起 MySQL + Redis）：

```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: aiinterview-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root123456
      MYSQL_DATABASE: ai_interview        # Flyway 负责建表，这里只建空库
      TZ: Asia/Shanghai
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --ngram-token-size=2              # 中文全文检索最小粒度（默认即 2，显式声明）
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-proot123456"]
      interval: 5s
      retries: 10

  redis:
    image: redis:7-alpine
    container_name: aiinterview-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      retries: 10

volumes:
  mysql-data:
```

```bash
docker compose -f docker-compose.dev.yml up -d   # colima 环境端口自动转发到 127.0.0.1
docker compose -f docker-compose.dev.yml ps      # 确认两个服务 healthy
```

要点：

1. colima 会把容器端口自动转发到宿主机 `127.0.0.1`，Spring Boot 直连 `jdbc:mysql://127.0.0.1:3306/ai_interview` 即可。
2. colima 默认 2C/2GB 内存对 MySQL 8 偏紧，若启动缓慢或被 OOM kill：`colima stop && colima start --cpu 4 --memory 6`（§11 R2）。
3. ngram 是 MySQL 8 内置全文解析器，**无需安装插件**，但建 FULLTEXT 索引必须带 `WITH PARSER ngram`，否则中文按空格分词几乎检索不到（§3.3）。

### 2.3 创建工程骨架

```bash
mkdir -p ai-interview/backend && cd ai-interview/backend
# 用 https://start.spring.io 生成（依赖勾选）：
#   web, security, validation, websocket, data-redis,
#   flyway(+flyway-mysql), aop, actuator
# 手工追加依赖：mybatis-plus-spring-boot3-starter、mysql-connector-j、jjwt-api/impl/jackson、lombok、spring-webflux(仅WebClient)
```

`application.yml` 分 `dev` / `prod` 两 profile，dev 直连本机容器端口，prod 用服务名（§10.3、附录 B）。

### 2.4 IDE 与工具约定

- IDEA：装 Lombok 插件、Annotation Processing 开启；项目编码 UTF-8。
- 接口调试：curl / IDEA HTTP Client（`backend/api.http` 纳入版本管理，见附录 C）。
- 数据库查看：IDEA Database 面板直连 `127.0.0.1:3306`。

---

## 3. 数据库实施计划

### 3.1 总体约定

- 9 张表：`sys_user`、`position`、`question`、`interview_session`、`interview_message`、`knowledge_chunk`、`evaluation_report`、`ability_record`、`learning_resource`（方案 §9）。
- 引擎 InnoDB、字符集 `utf8mb4`、排序规则 `utf8mb4_unicode_ci`。
- 时间列 `DATETIME`，默认 `CURRENT_TIMESTAMP`，更新列加 `ON UPDATE CURRENT_TIMESTAMP`。
- **`position` 是 MySQL 保留字**，所有 DDL 与 SQL 一律反引号包裹 `` `position` ``。
- **JSON 列不能建 FULLTEXT 索引**：`knowledge_chunk.keywords`、`skill_tags` 因此用 `VARCHAR` 存逗号分隔值（可全文检索），题目/报告类 JSON 字段用 MySQL `JSON` 类型 + MyBatis-Plus `JacksonTypeHandler`。
- 外键：核心归属关系（question→position、session→user/position、message→session、report→session）建外键，便于课程设计答辩时说明完整性约束；高频写入表（interview_message）外键在压测出现瓶颈时可去掉，默认保留。
- 方案 §9 只给出 `sys_user` 完整 DDL，其余 8 张的 DDL 由本计划补全如下，字段名与方案 §9 字段清单逐一对应，**实施时直接照抄本节 SQL**。

### 3.2 建表 DDL（V1__init_schema.sql 全文）

```sql
-- ========== 1. 用户表（来自方案 §9.1，原文照抄 + 补充索引） ==========
CREATE TABLE sys_user (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    real_name     VARCHAR(50),
    role          VARCHAR(20)  NOT NULL DEFAULT 'STUDENT',   -- STUDENT / ADMIN
    major         VARCHAR(100),
    status        TINYINT      NOT NULL DEFAULT 1,           -- 1启用 0禁用
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 2. 岗位表 ==========
CREATE TABLE `position` (
    id                   BIGINT       PRIMARY KEY AUTO_INCREMENT,
    code                 VARCHAR(50)  NOT NULL UNIQUE,        -- JAVA_BACKEND / WEB_FRONTEND
    name                 VARCHAR(100) NOT NULL,
    description          VARCHAR(500),
    competency_model_json JSON,                               -- 能力模型 + 评分权重，结构见 §3.5
    default_rounds       INT          NOT NULL DEFAULT 6,     -- 默认主问题数 5~8
    enabled              TINYINT      NOT NULL DEFAULT 1,
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 3. 题目表 ==========
CREATE TABLE question (
    id                       BIGINT        PRIMARY KEY AUTO_INCREMENT,
    position_id              BIGINT        NOT NULL,
    type                     VARCHAR(20)   NOT NULL,          -- TECHNICAL/PROJECT/SCENARIO/BEHAVIORAL
    content                  VARCHAR(1000) NOT NULL,
    background               VARCHAR(1000),                   -- 题目背景描述（可空）
    difficulty               TINYINT       NOT NULL DEFAULT 3,-- 1~5
    skill_tags_json          JSON          NOT NULL,          -- ["redis","缓存"]
    reference_answer         TEXT,                           -- 参考答案
    key_points_json          JSON,                           -- 关键得分点
    deduction_points_json    JSON,                           -- 常见失分点
    followup_templates_json  JSON,                           -- 预设追问模板（AI不可用时的兜底）
    status                   VARCHAR(20)   NOT NULL DEFAULT 'ENABLED',  -- ENABLED/DISABLED
    version                  INT           NOT NULL DEFAULT 1,-- 管理端修改题目的乐观锁版本号
    created_at               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_question_select (position_id, type, difficulty, status),
    CONSTRAINT fk_question_position FOREIGN KEY (position_id) REFERENCES `position`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 4. 面试会话表 ==========
CREATE TABLE interview_session (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL,
    position_id     BIGINT        NOT NULL,
    mode            VARCHAR(20)   NOT NULL DEFAULT 'TEXT',    -- TEXT / AUDIO
    difficulty      TINYINT       NOT NULL DEFAULT 3,
    planned_rounds  INT           NOT NULL DEFAULT 6,         -- 计划主问题数 5~8
    current_round   INT           NOT NULL DEFAULT 0,         -- 当前主问题序号（0-based，追问不+1）
    status          VARCHAR(20)   NOT NULL DEFAULT 'CREATED', -- 状态机见 §6.1
    total_score     DECIMAL(5,2),                             -- 结束后回填
    started_at      DATETIME,
    finished_at     DATETIME,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_session_user (user_id, status),
    KEY idx_session_position (position_id),
    CONSTRAINT fk_session_user     FOREIGN KEY (user_id)    REFERENCES sys_user(id),
    CONSTRAINT fk_session_position FOREIGN KEY (position_id) REFERENCES `position`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 5. 面试消息表 ==========
CREATE TABLE interview_message (
    id                 BIGINT       PRIMARY KEY AUTO_INCREMENT,
    session_id         BIGINT       NOT NULL,
    round_no           INT          NOT NULL,                 -- 所属主问题轮次（0-based）
    speaker            VARCHAR(10)  NOT NULL,                 -- AI / USER / SYSTEM
    input_type         VARCHAR(10),                           -- TEXT / AUDIO（仅 USER 消息有值）
    content_text       TEXT,                                  -- 文字内容（AI问题或学生文字回答）
    audio_path         VARCHAR(255),                          -- 音频相对路径 uploads/audio/...
    audio_duration_ms  INT,
    asr_text           TEXT,                                  -- 前端 Web Speech API 转写文本
    question_id        BIGINT,                                -- AI 消息关联的题目/追问来源题
    keywords_json      JSON,                                  -- 后端从回答中提取的关键词（供检索与报告）
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_message_session (session_id, round_no, speaker),
    CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES interview_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 6. 知识片段表（轻量 RAG 数据源） ==========
CREATE TABLE knowledge_chunk (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    position_id BIGINT       NOT NULL,
    title       VARCHAR(200) NOT NULL,
    content     TEXT         NOT NULL,                        -- 300~800 字知识片段
    keywords    VARCHAR(500) NOT NULL,                        -- 逗号分隔；VARCHAR 而非 JSON，为建全文索引
    skill_tags  VARCHAR(200) NOT NULL,                        -- 逗号分隔技能编码，与能力模型 skill_code 对齐
    source_type VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',       -- MANUAL / DOC_SLICE
    status      VARCHAR(20)  NOT NULL DEFAULT 'ENABLED',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 中文全文检索：ngram 解析器 + 联合索引（MATCH 列必须同属一个索引）
    FULLTEXT KEY ft_chunk_text (content, keywords) WITH PARSER ngram,
    KEY idx_chunk_position (position_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 7. 评估报告表 ==========
CREATE TABLE evaluation_report (
    id                    BIGINT        PRIMARY KEY AUTO_INCREMENT,
    session_id            BIGINT        NOT NULL UNIQUE,
    total_score           DECIMAL(5,2)  NOT NULL,
    dimension_scores_json JSON          NOT NULL,            -- 六维得分 {"technical":82,...}
    strengths_json        JSON,                              -- 亮点数组
    weaknesses_json       JSON,                              -- 不足数组
    improvement_plan_json JSON,                              -- 改进建议/练习计划数组
    report_text           TEXT,                              -- AI 生成的整体评语
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_session FOREIGN KEY (session_id) REFERENCES interview_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 8. 能力记录表 ==========
CREATE TABLE ability_record (
    id                BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    position_id       BIGINT       NOT NULL,
    skill_code        VARCHAR(50)  NOT NULL,                  -- 对应能力模型 skill_code
    score             DECIMAL(5,2) NOT NULL,                  -- 该技能本次掌握度（0~100）
    source_session_id BIGINT,
    evaluated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ability_query (user_id, position_id, skill_code, evaluated_at),
    CONSTRAINT fk_ability_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 9. 学习资源表 ==========
CREATE TABLE learning_resource (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    position_id   BIGINT       NOT NULL,
    skill_code    VARCHAR(50)  NOT NULL,
    title         VARCHAR(200) NOT NULL,
    resource_type VARCHAR(20)  NOT NULL,                      -- ARTICLE / QUESTION / PRACTICE / PROJECT
    content_url   VARCHAR(500),
    difficulty    TINYINT      NOT NULL DEFAULT 2,
    enabled       TINYINT      NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_resource_match (position_id, skill_code, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 3.3 FULLTEXT 检索说明（实施时直接使用）

```sql
-- 检索 SQL（KnowledgeChunkMapper 中 @Select 实现，BOOLEAN MODE，词间 + 连接）：
SELECT id, title, content, skill_tags,
       MATCH(content, keywords) AGAINST(:kw IN BOOLEAN MODE) AS score
FROM knowledge_chunk
WHERE status = 'ENABLED'
  AND position_id = :positionId
  AND MATCH(content, keywords) AGAINST(:kw IN BOOLEAN MODE) > 0
ORDER BY score DESC
LIMIT 5;
```

注意：ngram `ngram-token-size=2`，**单个汉字查询不命中**，关键词提取阶段必须保证查询词 ≥ 2 个汉字（§6.5）。

### 3.4 Flyway 脚本拆分

| 脚本 | 内容 | 行数量级 |
|---|---|---|
| `V1__init_schema.sql` | §3.2 全部 DDL | ~150 |
| `V2__seed_users_positions.sql` | 管理员账号（admin，BCrypt）、演示学生、2 个岗位 + 能力模型 JSON | ~80 |
| `V3__seed_questions_java.sql` | Java 后端岗 30 题（技术 15 / 项目 6 / 场景 6 / 行为 3） | ~500 |
| `V4__seed_questions_frontend.sql` | Web 前端岗 30 题（同比例） | ~500 |
| `V5__seed_knowledge_chunks.sql` | 知识片段：两岗位各 ≥ 20 条（合计 ≥ 40） | ~700 |
| `V6__seed_learning_resources.sql` | 学习资源：两岗位各 ≥ 10 条（合计 ≥ 20） | ~60 |

种子数据中的 BCrypt 密码散列开发期用固定值（如 `123456` 的散列），生成方式：`htpasswd -bnBC 10 "" 123456` 或写一个临时测试用 `BCryptPasswordEncoder.encode` 产出。

### 3.5 `competency_model_json` 结构约定（两岗位共用 Schema）

```json
{
  "skills": [
    { "skillCode": "java_basics", "skillName": "Java基础与面向对象",
      "description": "集合、异常、面向对象设计", "weight": 0.15, "requiredLevel": 70 }
  ],
  "scoreWeights": {
    "content":  { "technical": 0.30, "depth": 0.25, "logic": 0.20, "evidence": 0.15, "match": 0.10 },
    "expression": { "clarity": 0.30, "fluency": 0.20, "pace": 0.20, "confidence": 0.15, "completeness": 0.15 },
    "total": { "content": 0.50, "expression": 0.30, "adaptability": 0.10, "match": 0.10 }
  }
}
```

- 六个评分维度编码固定：`technical`（技术正确性）、`depth`（知识深度）、`logic`（逻辑严谨性）、`match`（岗位匹配度）、`communication`（沟通表达）、`voice`（语音表现）。
- `scoreWeights` 落库于岗位，管理端可改，实现方案"权重保存到数据库中，可以按岗位调整"（§7.2）。缺省时使用上述默认值。
- 两岗位技能清单：Java 后端取方案 §6.2 的 7 组技能映射为 `java_basics / concurrency_jvm / spring / mysql / redis_mq / project_design / communication`；前端取 `html_css_js / browser_network / vue3 / engineering / performance / security / project_ux`。各技能 `weight` 之和 = 1。

### 3.6 种子题库清单（V3/V4 内容规划）

**Java 后端岗 30 题**（type/difficulty/技能标签/题目）：

| # | 类型 | 难度 | 技能标签 | 题目 |
|---|---|---|---|---|
| 1 | TECHNICAL | 2 | java_basics | 谈谈 Java 面向对象三大特性，结合项目举例说明多态的使用场景 |
| 2 | TECHNICAL | 2 | java_basics | HashMap 的底层实现原理是什么？JDK 8 做了哪些优化？ |
| 3 | TECHNICAL | 3 | java_basics | ArrayList 与 LinkedList 的区别？各自适合什么场景？ |
| 4 | TECHNICAL | 3 | java_basics | Java 异常体系分为哪几类？受检异常和非受检异常如何选择？ |
| 5 | TECHNICAL | 3 | concurrency_jvm | synchronized 和 ReentrantLock 的区别与选择 |
| 6 | TECHNICAL | 3 | concurrency_jvm | 线程池的核心参数有哪些？任务提交后的执行流程是什么？ |
| 7 | TECHNICAL | 4 | concurrency_jvm | 谈谈 JVM 内存区域划分，以及哪些区域可能 OOM |
| 8 | TECHNICAL | 4 | concurrency_jvm | 什么是 volatile？它和锁的区别是什么？ |
| 9 | TECHNICAL | 2 | spring | 什么是 Spring IOC 和依赖注入？它解决了什么问题？ |
| 10 | TECHNICAL | 3 | spring | Spring Bean 的生命周期和作用域 |
| 11 | TECHNICAL | 3 | spring | Spring 事务传播行为有哪些？REQUIRED 和 REQUIRES_NEW 的区别？ |
| 12 | TECHNICAL | 4 | spring | Spring Boot 自动装配的原理是什么？ |
| 13 | TECHNICAL | 3 | mysql | MySQL 索引为什么用 B+ 树而不是 B 树或哈希表？ |
| 14 | TECHNICAL | 3 | mysql | 什么情况下索引会失效？如何分析一条慢 SQL？ |
| 15 | TECHNICAL | 4 | redis_mq | Redis 常见数据结构及使用场景；缓存穿透/击穿/雪崩的区别与对策 |
| 16 | PROJECT | 3 | project_design | 介绍你项目中最复杂的模块，复杂在哪里、你是如何设计的 |
| 17 | PROJECT | 3 | project_design | 你的项目中遇到过最棘手的 bug 是什么？讲讲排查过程 |
| 18 | PROJECT | 4 | project_design | 你的项目如果用户量增长 100 倍，哪些地方会先成为瓶颈？ |
| 19 | PROJECT | 3 | project_design | 项目中你负责的模块与其他人如何协作？接口如何约定？ |
| 20 | PROJECT | 2 | project_design | 项目中你使用过哪些设计模式？为什么要用？ |
| 21 | PROJECT | 4 | project_design | 如果让你重构你的项目，你会改进哪三点？为什么当初没这么做？ |
| 22 | SCENARIO | 3 | mysql | 线上接口响应从 50ms 变成 3s，说说你的排查思路 |
| 23 | SCENARIO | 4 | redis_mq | 秒杀场景下如何防止超卖？说说你的方案 |
| 24 | SCENARIO | 3 | spring | 接口偶发返回旧数据（缓存与库不一致），如何定位与解决？ |
| 25 | SCENARIO | 4 | concurrency_jvm | 服务频繁 Full GC 导致超时，如何应急处理与根治？ |
| 26 | SCENARIO | 4 | project_design | 部署后数据库 CPU 飙到 100%，你按什么顺序排查？ |
| 27 | SCENARIO | 3 | redis_mq | 消息队列消息积压了百万条，怎么办？ |
| 28 | BEHAVIORAL | 2 | communication | 和团队成员技术方案意见不一致，你是怎么处理的？举真实例子 |
| 29 | BEHAVIORAL | 3 | communication | 讲一次你在压力下完成任务的经历 |
| 30 | BEHAVIORAL | 2 | communication | 你的学习方法是什么？最近在学什么新技术？ |

**Web 前端岗 30 题**（同比例：技术 15 / 项目 6 / 场景 6 / 行为 3）：

| # | 类型 | 难度 | 技能标签 | 题目 |
|---|---|---|---|---|
| 1 | TECHNICAL | 2 | html_css_js | 说说盒模型，标准盒模型和 IE 盒模型的区别及转换方式 |
| 2 | TECHNICAL | 3 | html_css_js | JS 事件循环（Event Loop）机制；宏任务和微任务的执行顺序 |
| 3 | TECHNICAL | 3 | html_css_js | var、let、const 的区别；什么是暂时性死区？ |
| 4 | TECHNICAL | 3 | html_css_js | 闭包是什么？有哪些实际应用和内存注意事项？ |
| 5 | TECHNICAL | 4 | html_css_js | Promise 和 async/await 的关系；错误如何捕获？ |
| 6 | TECHNICAL | 3 | browser_network | 从输入 URL 到页面渲染经历了什么？ |
| 7 | TECHNICAL | 3 | browser_network | 浏览器同源策略与 CORS 的原理；跨域解决方案对比 |
| 8 | TECHNICAL | 4 | browser_network | HTTP 缓存机制（强缓存/协商缓存）如何工作？ |
| 9 | TECHNICAL | 2 | vue3 | Vue 响应式原理；Vue3 的 Proxy 方案与 Vue2 有何不同？ |
| 10 | TECHNICAL | 3 | vue3 | ref 和 reactive 的区别与使用选择 |
| 11 | TECHNICAL | 3 | vue3 | computed 和 watch 的区别？watchEffect 适合什么场景？ |
| 12 | TECHNICAL | 4 | vue3 | Vue 组件间通信方式有哪些？复杂状态为什么用 Pinia？ |
| 13 | TECHNICAL | 3 | engineering | Vite 为什么比 Webpack 快？ESM 和打包构建的区别 |
| 14 | TECHNICAL | 4 | security | 前端安全：XSS 和 CSRF 的原理与防御 |
| 15 | TECHNICAL | 3 | performance | 首屏加载慢，有哪些测量与优化手段？ |
| 16 | PROJECT | 3 | project_ux | 介绍你做过的前端项目中最复杂的页面/组件，复杂在哪 |
| 17 | PROJECT | 3 | project_ux | 你的项目如何做组件设计？举一个可复用组件的例子 |
| 18 | PROJECT | 4 | project_ux | 项目中前后端接口联调出过什么问题？如何避免？ |
| 19 | PROJECT | 3 | engineering | 你的项目如何管理环境变量、构建与发布流程？ |
| 20 | PROJECT | 2 | project_ux | 项目中用过哪些 UI 组件库？二次封装过组件吗？ |
| 21 | PROJECT | 4 | project_ux | 如果重做你的项目，前端架构上你会改进什么？ |
| 22 | SCENARIO | 3 | performance | 页面列表滚动到 1 万条数据时严重卡顿，如何优化？ |
| 23 | SCENARIO | 3 | browser_network | 用户反馈页面白屏，你怎么定位？ |
| 24 | SCENARIO | 4 | performance | 打包体积过大导致加载慢，如何分析与瘦身？ |
| 25 | SCENARIO | 4 | vue3 | 表单复杂联动 + 频繁输入导致卡顿，你怎么处理？ |
| 26 | SCENARIO | 3 | security | 需要在前端保存登录态，如何权衡安全和体验？ |
| 27 | SCENARIO | 3 | project_ux | 设计稿在不通过的机型上错位，你的适配方案？ |
| 28 | BEHAVIORAL | 2 | communication | 和后端/产品对需求有分歧时你怎么沟通？举例子 |
| 29 | BEHAVIORAL | 3 | communication | 讲一次你赶工期还要保证质量的经历 |
| 30 | BEHAVIORAL | 2 | communication | 你平时如何跟进前端新技术？最近学了什么？ |

每道题的 `key_points_json`（3~6 个得分点）、`deduction_points_json`（2~4 个失分点）、`followup_templates_json`（2~3 条预设追问）在 V3/V4 脚本中逐题补全——**这是 M1 阶段工作量最大的部分，按每天 10 题的节奏推进**。格式样板：

```sql
INSERT INTO question (position_id, type, content, background, difficulty,
                      skill_tags_json, reference_answer, key_points_json,
                      deduction_points_json, followup_templates_json, status) VALUES
(1, 'TECHNICAL', '什么是 Spring IOC？', '假设候选人刚做完一个 Spring Boot 课程项目', 2,
 '["spring"]',
 'IOC 是控制反转……（200~400 字参考答案）',
 '["能说出控制反转与依赖注入的关系", "能结合容器说明对象生命周期托管", "能举出 @Autowired/@Bean 的例子"]',
 '["把 IOC 说成 DI 的同义词且无解释", "无法举出任何实际使用场景"]',
 '["你的项目里哪些对象交给了容器管理？为什么这个适合交给容器？", "如果不用 Spring，你会怎么手动组织这些依赖？"]',
 'ENABLED');
```

### 3.7 知识片段与学习资源种子规划

- **V5 知识片段（每岗 ≥ 20 条）**：按能力模型 7 个技能 × 每技能 3 条左右。内容来自经典面试考点的概念解释、优秀回答要点、常见错误（对应方案 §6.4 知识库内容 7 类）。每条 300~800 字，`keywords` 填 8~15 个逗号分隔关键词，`skill_tags` 与能力模型 `skill_code` 对齐。
- **V6 学习资源（每岗 ≥ 10 条）**：每技能 1~2 条，类型覆盖 ARTICLE（文章）/ QUESTION（面试题）/ PRACTICE（练习）/ PROJECT（项目实战），`content_url` 可用真实公开链接（MDN、MySQL 官方、Vue 文档等）。

### 3.8 能力掌握度的存取约定

- `ability_record` 只追加不更新，**每次面试结束为每个命中的 skill_code 插入一条**；"当前掌握度" = 该 (user, position, skill) 按 `evaluated_at` 最新一条的 `score`。
- 首次面试无历史记录时：`新掌握度 = 本次得分`（不再套 0.7/0.3 公式）；此后按方案 §8.1 `新 = 旧×0.7 + 本次×0.3`。
- 成长曲线数据源：按 `evaluated_at` 升序取全部记录即可画出每次面试后的掌握度变化。

---

## 4. 工程结构与模块设计

### 4.1 目录总览（对应方案 §12）

```text
ai-interview/
├── backend/
│   ├── pom.xml
│   ├── Dockerfile                      # M6 编写（§10.4）
│   ├── api.http                        # IDEA HTTP Client 调试脚本（附录 C 同步维护）
│   ├── uploads/                        # 音频等上传文件（开发期本地目录，部署时挂载卷）
│   └── src/main/java/com/example/interview/
│       ├── InterviewApplication.java
│       ├── auth/        # ① 认证、用户、JWT、Spring Security 接入
│       ├── position/    # ② 岗位与能力模型查询
│       ├── question/    # ③ 题库实体/组卷（管理接口在 admin 包）
│       ├── interview/   # ④ 面试会话、状态机、问答、WebSocket、音频存储
│       ├── knowledge/   # ⑤ 知识片段实体 + FULLTEXT 检索
│       ├── ai/          # ⑥ AiProvider 双实现、PromptBuilder、JSON 容错解析
│       ├── assessment/  # ⑦ 逐题评分、总分计算、语音指标、报告生成
│       ├── growth/      # ⑧ 掌握度、短板识别、推荐、历史
│       ├── admin/       # ⑨ 管理端接口
│       └── common/      # ⑩ 统一响应、异常、配置、工具、日志
│   └── src/main/resources/
│       ├── application.yml / application-dev.yml / application-prod.yml
│       └── db/migration/  # Flyway V1~V6
├── frontend/            # 不在本计划范围，占位
├── docker-compose.yml   # 正式五服务编排（§10）
└── README.md
```

### 4.2 依赖清单（pom.xml 关键项）

| 依赖 | groupId:artifactId | 版本 | 说明 |
|---|---|---|---|
| Web/MVC | org.springframework.boot:spring-boot-starter-web | Boot BOM | REST |
| Security | spring-boot-starter-security | BOM | 认证鉴权 |
| 校验 | spring-boot-starter-validation | BOM | Jakarta Validation |
| WebSocket | spring-boot-starter-websocket | BOM | 面试推送 |
| Redis | spring-boot-starter-data-redis | BOM | 黑名单/限流/缓存 |
| Flyway | org.flywaydb:flyway-core + flyway-mysql | BOM | 迁移 |
| WebClient | spring-boot-starter-webflux | BOM | 仅用 WebClient 调大模型 |
| ORM | com.baomidou:mybatis-plus-spring-boot3-starter | 3.5.7+ | 注意是 boot3 版 starter |
| 驱动 | com.mysql:mysql-connector-j | BOM 管理版本 | scope runtime |
| JWT | io.jsonwebtoken:jjwt-api / jjwt-impl / jjwt-jackson | 0.12.6 | impl/jackson scope runtime |
| Lombok | org.projectlombok:lombok | BOM | optional |
| 测试 | spring-boot-starter-test、spring-security-test | BOM | + org.testcontainers:mysql 1.20.x |

### 4.3 各模块类清单（实施核对表）

> 命名约定：实体与表名一致（`sys_user`→`SysUser`）；Mapper 继承 `BaseMapper<T>`；Service 接口 + Impl 可简化为单类（课程设计规模**直接用类，不强制接口**，答辩可说明取舍）。

**common（⑩）**

| 类 | 职责 |
|---|---|
| `common/result/Result<T>` | 统一响应 `{code, message, data, traceId}`（方案 §10.6） |
| `common/result/ResultCode` | 错误码枚举（附录 A） |
| `common/exception/BizException` | 业务异常（携带 ResultCode） |
| `common/exception/GlobalExceptionHandler` | `@RestControllerAdvice`：BizException→业务码；MethodArgumentNotValidException→40001；AccessDenied→40301；其余→50001 且不泄漏堆栈 |
| `common/config/SecurityConfig` | 过滤链、CORS、无状态会话、路由权限（§7.1） |
| `common/config/WebSocketConfig` | 注册 `InterviewWebSocketHandler` 到 `/ws/interviews/{sessionId}` |
| `common/config/MybatisPlusConfig` | 分页插件；`MetaObjectHandler` 自动填充 created_at/updated_at（或全部依赖 DB 默认值，二选一） |
| `common/config/WebClientConfig` | 超时 30s、连接池、日志过滤器（脱敏 API Key） |
| `common/config/AiProperties` | `@ConfigurationProperties(prefix="ai")`：provider/apiUrl/apiKey/model/timeout |
| `common/util/JwtUtils` | 签发/解析（uid、username、role、jti、exp） |
| `common/util/JsonUtils` | ObjectMapper 单例；JSON 列 TypeHandler 复用 |
| `common/util/TraceIdFilter` | 每请求生成 traceId 写 MDC，响应头回传 |
| `common/log/AuditLogAspect` | `@Around` 切面：admin 包 Controller 自动记录操作日志（脱敏） |

**auth（①）**

| 类 | 职责 |
|---|---|
| `auth/controller/AuthController` | register / login / logout |
| `auth/controller/UserController` | GET/PUT `/api/users/me` |
| `auth/service/AuthService` | 注册（用户名查重、BCrypt 加密）、登录（校验、签发 JWT、Redis 记录 jti）、登出（jti 加入黑名单直至过期） |
| `auth/service/UserService` | 个人资料查询/修改 |
| `auth/entity/SysUser`、`auth/mapper/UserMapper` | 表 1 |
| `auth/dto/RegisterRequest` | username(4~50)、password(6~32)、realName、major |
| `auth/dto/LoginRequest` / `LoginResponse` | token、过期时间、UserVO |
| `auth/dto/UserUpdateRequest` / `UserVO` | 脱敏视图（不含 passwordHash） |
| `auth/security/JwtAuthenticationFilter` | 解析 Bearer Token→装载 `UsernamePasswordAuthenticationToken`；先查 Redis 黑名单 |
| `auth/security/UserDetailsServiceImpl` | 按 username 装载用户与角色（ROLE_STUDENT/ROLE_ADMIN） |
| `auth/security/SecurityContextHolder`（自封装） | 静态取当前 userId/role，供归属校验 |

**position（②）**

| 类 | 职责 |
|---|---|
| `position/controller/PositionController` | GET 列表 / 详情 / skills（方案 §10.2） |
| `position/service/PositionService` | 查询 + 解析 competency_model_json |
| `position/entity/Position`、`position/mapper/PositionMapper` | 表 2 |
| `position/dto/PositionVO`、`SkillVO`、`ScoreWeightsVO` | 能力模型视图 |

**question（③）**

| 类 | 职责 |
|---|---|
| `question/entity/Question`、`question/mapper/QuestionMapper` | 表 3 |
| `question/service/QuestionService` | 供 admin CRUD 复用；`assemblePaper(positionId, difficulty, rounds)` 组卷（§6.3） |
| `question/dto/QuestionAdminVO`、`QuestionUpsertRequest` | 管理端字段校验（content 4~1000 字等） |

**interview（④）——核心模块**

| 类 | 职责 |
|---|---|
| `interview/controller/InterviewController` | §5.3 全部 9 个接口 |
| `interview/service/InterviewService` | 创建/start/回答处理/finish/abort/查询；每步先过状态机与归属校验 |
| `interview/service/TurnEngine` | 一轮问答编排（方案 §13.1 流程的代码化）：存回答→提关键词→检索知识→PromptBuilder→AiProvider→容错→存 AI 消息→WS 推送→推进状态 |
| `interview/statemachine/InterviewStatus`（枚举）+ `InterviewStateMachine` | §6.1 合法转换表 + 乐观更新 |
| `interview/websocket/InterviewWebSocketHandler` | 连接鉴权（query token）、会话注册表、按 type 推送 |
| `interview/websocket/WsMessage`、`WsMessageType` | QUESTION/TRANSCRIPT/FOLLOW_UP/TIMER/PROGRESS/REPORT_READY/ERROR（方案 §10.6） |
| `interview/scheduler/InterviewTimerScheduler` | 每会话定时推送剩余时间；到期自动 TIMEOUT 收卷 |
| `interview/storage/AudioStorageService` | 格式/大小校验，UUID 命名落盘 `uploads/audio/yyyyMM/`，返回相对路径 |
| `interview/entity/InterviewSession`、`InterviewMessage`；对应两个 Mapper | 表 4/5 |
| `interview/dto/CreateInterviewRequest`、`InterviewDetailVO`、`MessageVO`、`TextAnswerRequest`、`AudioAnswerForm`（multipart） | §5.3 |

**knowledge（⑤）**

| 类 | 职责 |
|---|---|
| `knowledge/entity/KnowledgeChunk`、`knowledge/mapper/KnowledgeChunkMapper` | 表 6；Mapper 含 §3.3 的 FULLTEXT @Select |
| `knowledge/service/KnowledgeSearchService` | 检索 Top5；无结果返回空列表（上层降级） |
| `knowledge/service/KeywordExtractor` | 词典匹配法提取关键词（§6.5） |

**ai（⑥）**

| 类 | 职责 |
|---|---|
| `ai/provider/AiProvider` | 接口：`nextQuestion / evaluateTurn / generateReport`（方案 §6.6 原文三方法） |
| `ai/provider/MockAiProvider` | 离线实现：模板追问（用题目 followup_templates）、**规则评分引擎**（关键词命中率→各维分）、结构化报告拼装；保证无网可全流程演示 |
| `ai/provider/OpenAiCompatibleProvider` | WebClient POST `{api-url}/chat/completions`；system prompt 要求只输出 JSON；30s 超时 |
| `ai/prompt/PromptBuilder` | 拼装：岗位能力模型 + 历史对话（近 10 条）+ 知识片段 + 当前指令（追问规则/难度指令）+ 输出 JSON Schema 说明 |
| `ai/parser/AiResponseParser` | 剥离 ```json 围栏→Jackson 反序列化→字段校验；失败重试 1 次→题目模板→抛 AiFallbackException 触发 Mock 降级（方案 §6.6 三级容错） |
| `ai/model/*` | AiNextQuestionResult、AiTurnEvaluation、AiReport、三个 Context、AiChatResponse（OpenAI 兼容响应 DTO） |

**assessment（⑦）**

| 类 | 职责 |
|---|---|
| `assessment/service/ReportService` | finish 编排（方案 §13.2）：汇总逐题→AiProvider.generateReport（失败用 Mock）→六维聚合→权重计算→落库 evaluation_report→回填 session.total_score→触发 growth 更新→WS 推 REPORT_READY |
| `assessment/engine/ScoreCalculator` | §6.6 权重公式；逐题分→总分；维度聚合（题目技能→六维映射） |
| `assessment/engine/VoiceMetricsCalculator` | 语速=转写字数÷时长(分钟)；停顿次数（转写中"……"/时长断裂，取前端上报）、语气词计数（嗯/啊/然后/就是等词典）；产出 voice 维度分 |
| `assessment/entity/EvaluationReport`、`assessment/mapper/EvaluationReportMapper` | 表 7 |
| `assessment/dto/ReportVO`、`QuestionEvidenceVO` | 报告视图：六维分、逐题（题目/回答/得分/coveredPoints/missingPoints/证据句/评分理由） |

**growth（⑧）**

| 类 | 职责 |
|---|---|
| `growth/controller/GrowthController` | growth / recommendations / history（方案 §10.4） |
| `growth/service/GrowthService` | 曲线序列、历史列表（分页，含总分与日期） |
| `growth/service/RecommendationService` | 短板识别→优先级排序→资源匹配→练习计划生成 |
| `growth/engine/MasteryUpdater` | 首次直取，之后 `旧×0.7+本次×0.3`（§3.8） |
| `growth/engine/WeaknessDetector` | 4 条短板规则（方案 §8.2） |
| `growth/engine/RecommendationEngine` | `优先级=(100-掌握度)×技能权重`（方案 §8.3） |
| `growth/entity/AbilityRecord`、`LearningResource`；两个 Mapper | 表 8/9 |
| `growth/dto/GrowthVO`、`RecommendationVO`、`HistoryItemVO` | 视图 |

**admin（⑨）**

| 类 | 职责 |
|---|---|
| `admin/controller/AdminQuestionController` | 题库 CRUD（方案 §10.5），version 乐观锁 |
| `admin/controller/AdminKnowledgeController` | 新增片段 / rebuild（重建全文索引：`ALTER TABLE ... FORCE` 或逐条 REINDEX，简化为 OPTIMIZE TABLE） |
| `admin/controller/AdminPositionController` | 岗位与能力模型/权重维护（方案 §4.2，接口为 §5.5 扩展组） |
| `admin/controller/AdminResourceController` | 学习资源 CRUD |
| `admin/controller/AdminUserController` | 用户列表/启停 |
| `admin/controller/AdminDashboardController` | 统计：用户数、面试场次、岗位题量、平均分、近 7 日面试数 |
| `admin/dto/*` | 各 UpsertRequest / VO |

### 4.4 横切约定

- **JSON 列映射**：实体 JSON 字段类型为 `List<String>` / `Map<String,Object>`，`@TableField(typeHandler = JacksonTypeHandler.class)`，实体加 `@TableName(autoResultMap = true)`。
- **事务**：`TurnEngine` 单轮处理、`ReportService` 报告生成、`MasteryUpdater` 掌握度更新各自 `@Transactional`；**AiProvider 调用放在事务外**（HTTP 调用不持锁）。
- **异步**：AI 调用与报告生成用 `@Async`（专用线程池 `aiExecutor`，核心 4 最大 8），避免 HTTP 请求挂 30s；WebSocket 推送在异步完成后执行。
- **日志**：TRACE/DEBUG 记录 Prompt 与 AI 原始返回（**打码 apiKey**）；INFO 记录业务节点；不打印密码、token、key（方案 §15.7）。

---

## 5. 接口实现清单

统一返回 `{code, message, data, traceId}`；除注册登录外全部 `Authorization: Bearer <jwt>`。**路径与方案 §10 逐字一致**；标注 ★ 的是方案 §4.2/§11 要求但 §10 未列出的管理端扩展接口。

### 5.1 认证与用户（auth）

| # | 接口 | 权限 | 请求 | 响应 data | 校验/错误 |
|---|---|---|---|---|---|
| 1 | POST `/api/auth/register` | 匿名 | `{username, password, realName?, major?}` | `UserVO` | username 4~50 唯一；password 6~32；重名→10001；禁用参数校验→40001 |
| 2 | POST `/api/auth/login` | 匿名 | `{username, password}` | `{token, expiresAt, user:UserVO}` | 错误→10002；限流 5 次/分/IP→42901；status=0→10003 |
| 3 | POST `/api/auth/logout` | 登录 | — | `null` | jti 进 Redis 黑名单至 token 过期 |
| 4 | GET `/api/users/me` | STUDENT/ADMIN | — | `UserVO` | |
| 5 | PUT `/api/users/me` | STUDENT/ADMIN | `{realName?, major?}` | `UserVO` | 不允许改 username/role |

### 5.2 岗位（position）

| # | 接口 | 权限 | 响应 data |
|---|---|---|---|
| 6 | GET `/api/positions` | 登录 | `[{id, code, name, description, defaultRounds}]`（enabled only） |
| 7 | GET `/api/positions/{id}` | 登录 | 详情 + 技能数 |
| 8 | GET `/api/positions/{id}/skills` | 登录 | `[{skillCode, skillName, description, weight, requiredLevel}]` |

### 5.3 面试（interview）——核心

| # | 接口 | 权限 | 请求 | 响应/推送 | 状态约束 |
|---|---|---|---|---|---|
| 9 | POST `/api/interviews` | STUDENT | `{positionId, mode:TEXT\|AUDIO, difficulty:1~5, rounds:5~8, durationMinutes:15~60}` | `InterviewDetailVO{id, status:CREATED, plannedRounds,...}` | 岗位 enabled；rounds 超界→40002 |
| 10 | POST `/api/interviews/{id}/start` | 归属本人 | — | HTTP 返回首题 `MessageVO`；同时 WS 推 `QUESTION`；status→RUNNING→WAITING_USER | 仅 CREATED |
| 11 | POST `/api/interviews/{id}/messages/text` | 归属本人 | `{roundNo, content:1~5000字}` | HTTP 返回 AI 下一条 `MessageVO`（异步：先受理回 `{accepted:true}`，结果走 WS） | 仅 WAITING_USER |
| 12 | POST `/api/interviews/{id}/messages/audio` | 归属本人 | multipart：`file`(webm/wav/mp3 ≤10MB) + `roundNo` + `asrText` + `durationMs` + `pauseCount?` | 同上 | 仅 WAITING_USER 且 mode=AUDIO |
| 13 | POST `/api/interviews/{id}/finish` | 归属本人 | — | `{reportId}`；报告就绪后 WS 推 `REPORT_READY` | WAITING_USER/PROCESSING_AI/RUNNING |
| 14 | POST `/api/interviews/{id}/abort` | 归属本人 | — | `InterviewDetailVO(status:ABORTED)` | 非 FINISHED 均可 |
| 15 | GET `/api/interviews/{id}` | 归属本人/ADMIN | — | `InterviewDetailVO` | |
| 16 | GET `/api/interviews/{id}/messages` | 归属本人/ADMIN | `?roundNo=` 可选 | `[MessageVO]` 按时间序 | |
| 17 | GET `/api/interviews/{id}/report` | 归属本人/ADMIN | — | `ReportVO`（§4.3 assessment） | 报告未生成→10004（允许 finish 后重试生成） |

### 5.4 成长（growth）

| # | 接口 | 权限 | 响应 data |
|---|---|---|---|
| 18 | GET `/api/users/me/growth` | STUDENT | `?positionId=`：`[{skillCode, skillName, records:[{evaluatedAt, score}], current}]` |
| 19 | GET `/api/users/me/recommendations` | STUDENT | `?positionId=`：`[{skillCode, priority, weaknessReasons[], resources[], practices[], nextInterviewSuggested}]` |
| 20 | GET `/api/users/me/history` | STUDENT | 分页 `?page=&size=`：`[{sessionId, positionName, mode, totalScore, startedAt, status}]` |

### 5.5 管理（admin，全部 ROLE_ADMIN）

| # | 接口 | 请求/说明 |
|---|---|---|
| 21 | GET `/api/admin/questions` | `?positionId=&type=&difficulty=&page=&size=` |
| 22 | POST `/api/admin/questions` | QuestionUpsertRequest（§4.3 question） |
| 23 | PUT `/api/admin/questions/{id}` | 同上；带 `version` 乐观锁，冲突→40003 |
| 24 | DELETE `/api/admin/questions/{id}` | 逻辑删（status→DISABLED），保护已引用历史 |
| 25 | POST `/api/admin/knowledge` | `{positionId, title, content, keywords, skillTags}` |
| 26 | POST `/api/admin/knowledge/rebuild` | 重建 FULLTEXT 索引（OPTIMIZE TABLE） |
| 27 | GET `/api/admin/dashboard` | 用户数/场次/题量/平均分/近 7 日趋势 |
| 28★ | GET/POST/PUT `/api/admin/positions`（`/{id}`） | 岗位与能力模型/权重维护 |
| 29★ | GET/POST/PUT/DELETE `/api/admin/resources`（`/{id}`） | 学习资源 CRUD |
| 30★ | PUT `/api/admin/positions/{id}/weights` | 评分权重调整（写 competency_model_json.scoreWeights） |
| 31★ | GET `/api/admin/users`、PUT `/api/admin/users/{id}/status` | 用户列表/启停 |

### 5.6 WebSocket

| 项 | 约定 |
|---|---|
| 地址 | `ws(s)://host/ws/interviews/{sessionId}?token=<jwt>`（浏览器 WS 不能自定义 Header，用 query 传 token，§7.3） |
| 鉴权 | 连接时校验 token 有效性 + 黑名单 + 会话归属；失败立即 close(4401) |
| 下行消息 | `{"type":"QUESTION\|FOLLOW_UP\|TRANSCRIPT\|TIMER\|PROGRESS\|REPORT_READY\|ERROR","payload":{...},"ts":1728800000000}` |
| payload | QUESTION/FOLLOW_UP：`{roundNo, followupCount, content, difficulty, targetSkills[]}`；TIMER：`{remainSeconds}`（每 30s，最后 60s 每 10s）；PROGRESS：`{currentRound, plannedRounds}`；REPORT_READY：`{reportId}`；ERROR：`{code, message}`（如 AI 降级提示）；TRANSCRIPT：语音转写回显确认 `{messageId, asrText}` |
| 心跳 | 服务端 30s PING 帧；客户端 60s 无 PONG 踢除注册表 |
| 并发 | 同一 sessionId 允许多连接（刷新页面），推送时遍历全部连接 |

---

## 6. 核心业务实现设计

### 6.1 面试状态机（方案 §6.5）

```text
CREATED ──start──▶ RUNNING ──发出首题──▶ WAITING_USER
WAITING_USER ──收到回答──▶ PROCESSING_AI ──产出追问/下一题──▶ WAITING_USER
PROCESSING_AI ──轮次用完/时间到──▶ FINISHED
RUNNING/WAITING_USER/PROCESSING_AI ──用户中止──▶ ABORTED
WAITING_USER/PROCESSING_AI ──定时器到期──▶ TIMEOUT（自动收卷走 finish 流程）
```

`InterviewStatus` 枚举内置合法转换表：

```java
enum InterviewStatus {
    CREATED, RUNNING, WAITING_USER, PROCESSING_AI, FINISHED, ABORTED, TIMEOUT;
    private static final Map<InterviewStatus, Set<InterviewStatus>> ALLOWED = Map.of(
        CREATED,        Set.of(RUNNING, ABORTED),
        RUNNING,        Set.of(WAITING_USER, ABORTED),
        WAITING_USER,   Set.of(PROCESSING_AI, ABORTED, TIMEOUT, FINISHED),
        PROCESSING_AI,  Set.of(WAITING_USER, FINISHED, ABORTED, TIMEOUT));
    // FINISHED/ABORTED/TIMEOUT 为终态
}
```

- 数据库 `status` 列是唯一事实源；每次转换用条件更新防并发（乐观锁）：
  `UPDATE interview_session SET status=新 WHERE id=? AND status=旧`，影响行数=0 → 抛 10005（状态冲突），前端收到后刷新会话。
- 双击/重复提交回答：第二次请求因 status 已是 PROCESSING_AI 被拒绝——不额外加分布式锁。

### 6.2 一次回答的完整编排（TurnEngine，对应方案 §13.1）

```text
[同步，事务内]
 1. 校验归属 + 状态(WAITING_USER→PROCESSING_AI，乐观更新)
 2. 保存 USER 消息（content_text 或 asr_text+audio_path+duration）
 3. KeywordExtractor 提取关键词 → 写 keywords_json
[异步 aiExecutor]
 4. KnowledgeSearchService 检索 Top5 片段（可为空）
 5. PromptBuilder 拼提示词（岗位模型+近10条历史+知识片段+追问规则指令）
 6. AiProvider.nextQuestion(context)
 7. AiResponseParser 校验 JSON（失败→重试1次→题目模板→Mock 降级）
 8. 结果决策：
    a) action=FOLLOW_UP 且同题主问题追问<3 → 追问计数+1，WS 推 FOLLOW_UP
    b) action=NEXT_QUESTION（或追问≥3）→ current_round+1：
       还有主问题 → WS 推 QUESTION；用完 → 转 FINISHED 走报告流程
    c) 连续低分/高分规则 → 在 5 的指令中注入升降难度（§6.4）
 9. 状态 PROCESSING_AI→WAITING_USER（或 FINISHED）
```

### 6.3 组卷算法（QuestionService.assemblePaper）

```text
输入 positionId, difficulty(1~5), rounds(5~8)
1. 比例分配（四舍五入后用行为题补齐差额）：
   technical = round(0.5N)  project = round(0.2N)  scenario = round(0.2N)  behavioral = N - 上述和
2. 每类型候选 = status=ENABLED 且 type 匹配的题目
3. 候选内按 |q.difficulty - difficulty| 升序，取前 3 倍配额作为难带
4. 带内随机抽样，且贪心保证 skill_tags 覆盖尽量多技能（优先选含未覆盖技能的题）
5. 输出主问题序列（顺序随机打乱，行为题不放第一题）
约束：某类型题库存量不足配额 → 降级用 TECHNICAL 补足并在日志 WARN（不中断面试）
```

主问题序列**仅在 start 时生成并写入 interview_message（speaker=AI, question_id, round_no）**——避免"每次回答后重新组卷"导致题目漂移；追问动态生成不占主问题名额。

### 6.4 动态追问规则引擎（方案 §6.5 表格逐条落地）

| 方案规则 | 实现点 |
|---|---|
| 缺少关键知识点→追问缺失内容 | Prompt 指令要求 AI 对照 key_points 输出 missingPoints 并就其提问；规则兜底：missingPoints 非空且追问<3 → 从题目 followup_templates 挑一条含该要点的 |
| 回答模糊→要求举例 | AI 判断（responseTooVague 标志）；Mock 实现：回答 < 60 字或无数字/无具体名词 → 模板"能结合一个具体例子说明吗？" |
| 技术错误→问边界/后果 | dedupoints 命中（回答含失分点关键词）→ Prompt 注入"指出其错误边界"指令 |
| 回答完整→提难度/替代方案 | coveredPoints 覆盖率 ≥ 80% → 追问难度 +1，指令"追问替代方案或取舍" |
| 连续两轮低分(<60)→降难度 | TurnEngine 维护 lastTwoScores 队列；命中 → 下一题/追问难度-1，且 Prompt 注入"回到基础主题" |
| 连续两轮高分(≥85)→项目深挖/系统设计 | 同上反向；指令"转入项目深挖或系统设计题" |
| 同题追问 >3 次→下一题 | 硬规则，代码强制（优先级高于 AI 返回的 FOLLOW_UP） |

追问难度漂移范围 clamp 到 1~5。

### 6.5 关键词提取（KeywordExtractor，词典法）

1. 词典来源：岗位能力模型 `skillName` + 全部 `skill_code` + 知识库 keywords 聚合去重（启动时加载进内存，/api/admin/knowledge 新增时刷新）。
2. 匹配：对回答文本做最长匹配，命中词去重；补充分词启发（正则抽取英文技术词 `[A-Za-z][A-Za-z0-9+.#]{1,}`）。
3. 过滤停用词；**保留词长 ≥ 2**（配合 ngram token=2）。
4. 产出 TopN(10) 关键词：`keywords_json` 入库 + 检索查询词（BOOLEAN MODE `+词1 +词2`，词数 > 4 时取权重前 4，避免查空）。

### 6.6 评分与报告（对应方案 §7）

**逐题评分**（AiProvider.evaluateTurn，输入=题目+该轮全部问答+知识片段）输出：

```json
{
  "scores": { "technical": 82, "depth": 75, "logic": 80, "match": 85, "communication": 78 },
  "coveredPoints": ["缓存击穿的概念正确"],
  "missingPoints": ["未提互斥锁方案"],
  "evidence": ["回答中'多个请求同时打到数据库'定位准确"],
  "reason": "概念正确但缺少方案对比，深度一般",
  "targetSkills": ["redis_mq"]
}
```

**语音维度**（mode=AUDIO 时叠加）：`VoiceMetricsCalculator` 产出 `{语速(字/分)、停顿次数、语气词密度、回答时长}`，映射 voice 分（语速 180~260 字/分满分区、停顿少、语气词密度低 → 高分；各指标分段线性打分）。

**总分**（ScoreCalculator，权重来自 §3.5 scoreWeights，缺省用默认公式）：

```text
内容 = technical×.30 + depth×.25 + logic×.20 + evidence×.15 + match×.10
表达 = clarity×.30 + fluency×.20 + pace×.20 + confidence×.15 + completeness×.15
总分 = 内容×.50 + 表达×.30 + 应变×.10 + 岗位匹配×.10      // 方案 §7.2 原式
```

逐题六维按题目技能加权聚合 → 会话六维（写入 dimension_scores_json）。evidence/coveredPoints/missingPoints/reason 逐题保留进报告（满足验收"证据、亮点、不足、建议"）。

**报告生成**（ReportService，方案 §13.2）：逐题评分结果 + 六维聚合 → AiProvider.generateReport 产出 `{summary, strengths[], weaknesses[], improvementPlan[], reportText}` → JSON 校验容错（同三级降级）→ evaluation_report 落库 → session.total_score 回填、status=FINISHED → MasteryUpdater 写 ability_record → RecommendationService 预生成推荐缓存（Redis，TTL 10 分钟）→ WS 推 REPORT_READY。

**Mock 评分规则**（保证离线六维报告可用）：technical/depth 由 key_points 命中率线性映射（命中率 0→40 分，1→90 分，锚定 reference_answer 长度比微调）；logic 由回答结构信号（总分总/序号词/字数适中 200~800）映射；communication 由字数与语气词密度映射；match 由技能标签命中率映射；voice 由 §6.6 指标映射；应变=追问轮回答分数回升幅度。所有映射集中在 MockAiProvider 一处，答辩可解释。

### 6.7 掌握度与推荐（对应方案 §8）

- `MasteryUpdater`：按 §3.8 公式，对本次面试命中的每个 skill_code 取会话内相关题目的加权均分（题目难度高权重高：1~5 → 0.6/0.8/1.0/1.2/1.4）。
- `WeaknessDetector` 四规则：①某技能最近两次记录均 <60；②最新掌握度 < 岗位 requiredLevel；③最近一场面试中该技能 ≥2 道题 missingPoints 命中；④communication/voice 维度连续两次 <60。
- `RecommendationEngine`：`优先级=(100-掌握度)×技能权重` 排序；输出 = 短板技能 Top3 × 每技能匹配 learning_resource（类型覆盖 ARTICLE/QUESTION/PRACTICE）+ 错题重练（最近一场 missingPoints 命中的题目）+ 建议下一次面试配置（短板技能密集的岗位模式、难度建议）。

### 6.8 AI 调用与三级容错（对应方案 §6.6 / §14）

```text
正常：OpenAiCompatibleProvider（WebClient，30s 超时，system 限定只输出 JSON）
  ↓ JSON 校验失败 / 超时 / 5xx
重试 1 次（退避 1s）
  ↓ 仍失败
用题目 followup_templates_json 预设追问（评分场景→规则评分）
  ↓ 模板也不可用（如开放式追问无模板）
MockAiProvider 固定响应；连续 3 次真实调用失败 → 本会话自动锁定 mock 直至结束，日志 WARN
```

- `ai.provider=mock` 时全程走 Mock（默认）；`=qwen` 时按上述容错。
- OpenAI 兼容请求体：`{model, messages:[{role,content}], temperature:0.3, response_format:{type:"json_object"}}`（通义 DashScope OpenAI 兼容模式支持 json_object）。

### 6.9 语音链路边界（前后端契约，方案 §6.7）

后端职责边界（转写在前端浏览器完成）：接收 multipart（音频文件 + `asrText` + `durationMs` + `pauseCount` 可选）→ 校验格式大小 → 落盘 uploads → `content_text=asrText` 进入文字同一分析流程 → 语音指标用 durationMs/pauseCount/字数计算。音频文件**只存档不回放**（M4 可选加受保护回放接口 `GET /api/interviews/{id}/messages/{mid}/audio`，流式返回，做归属校验）。

---

## 7. 安全设计落地（方案 §15 逐条）

| # | 方案要求 | 实现方式 |
|---|---|---|
| 1 | BCrypt 密码 | `BCryptPasswordEncoder`（强度 10）；注册/修改均加密存储 |
| 2 | JWT 认证 | jjwt 0.12 HS256；claims：uid/username/role/jti；exp 24h；secret 走环境变量 `JWT_SECRET`（≥32 字节） |
| 3 | Spring Security 接口权限 | `SecurityConfig`：无状态、禁 CSRF、会话 STATELESS；`/api/auth/register|login`、`/ws/**` permitAll（WS 内部自鉴权），`/api/admin/**` hasRole('ADMIN')，其余 authenticated；方法级补 `@PreAuthorize`（admin 包） |
| 4 | 面试记录归属校验 | Service 层统一 `assertOwner(session)`：学生角色时 `session.userId == 当前 uid` 否则 40401；ADMIN 放行 |
| 5 | 上传限制 | `spring.servlet.multipart.max-file-size=10MB`；扩展名白名单 webm/wav/mp3 + Content-Type 头复核；roundNo/durationMs 数值边界校验 |
| 6 | 音频受保护目录 | 存 `uploads/`（application 配置的**工程外/数据卷目录**，非 classpath、非静态资源映射）；文件名 UUID 防穿越；不注册为静态资源 |
| 7 | 日志脱敏 | TraceIdFilter 与 WebClient 日志过滤器屏蔽 `password/token/api-key/Authorization` 字段；logback pattern 不输出请求体 |
| 8 | SQL 注入防护 | MyBatis-Plus 全参数绑定；FULLTEXT 检索 @Select 用 `#{}`；无字符串拼接 SQL |
| 9 | 文本长度限制 | DTO `@Size`：回答 ≤5000、题目 ≤1000、知识片段 ≤8000；超限 40001 |
| 10 | 管理员操作日志 | `AuditLogAspect` 环绕 admin 包全部 Controller：记录 谁/何时/接口/参数摘要 → SLF4J 独立 logger（audit.log），不建表（§1.3 决策） |

补充：登录限流（Redis INCR，key=`rl:login:{ip}`，5 次/分钟，超限 42901）；JWT 黑名单（logout 的 jti → Redis SETEX 至 exp，`JwtAuthenticationFilter` 先查黑名单）。

---

## 8. 测试计划（方案 §16 后端部分）

### 8.1 单元测试（JUnit5 + Mockito，不起 Spring 容器，秒级）

| 测试类 | 覆盖 | 关键断言 |
|---|---|---|
| `InterviewStatusTest` | 状态机 | 全部合法转换通过；CREATED→FINISHED、FINISHED→RUNNING、WAITING_USER→RUNNING 等 3+ 非法转换抛异常 |
| `PaperAssemblerTest` | 组卷 | rounds=5~8 时四类比例为 50/20/20/10（±1 容差）；难度均值与目标差 ≤1；行为题不在首位；题库不足时 TECHNICAL 补位 |
| `FollowupRuleEngineTest` | 追问规则 | 方案 §6.5 七条规则各一用例（追问≥3 强制换题、连续低分降难度…） |
| `ScoreCalculatorTest` | 评分公式 | 手算样例总分误差 ≤0.01；权重改动后随岗位配置生效；分数 clamp 0~100 |
| `VoiceMetricsCalculatorTest` | 语音指标 | 600 字/200 秒 → 语速 180 字/分；空转写不除零 |
| `MasteryUpdaterTest` | 掌握度 | 旧 70 本次 90 → 76；首次记录=本次得分 |
| `WeaknessDetectorTest` | 短板 | 四条规则各一用例，不误报（单次 55 分不触发规则①） |
| `RecommendationEngineTest` | 推荐 | 优先级排序正确；短板技能资源必现；无短板时输出通用计划 |
| `AiResponseParserTest` | JSON 容错 | 正常 JSON/```json 围栏包裹/半截 JSON 三输入：前两者解析成功，第三个触发模板降级路径 |
| `JwtUtilsTest` | JWT | 签发→解析回 uid/role；篡改签名抛异常；过期 token 抛异常 |

### 8.2 集成测试（@SpringBootTest + MockMvc + Testcontainers MySQL8）

> Testcontainers 直接复用本机 colima 的 Docker。测试 profile 固定 `ai.provider=mock`，保证不依赖外网。每个测试类用 `@TestMethodOrder` 或独立用户隔离数据。

| 测试类 | 流程 | 关键断言 |
|---|---|---|
| `AuthFlowIT` | 注册→登录→me→PUT me→logout→再访问 | 各步 code=0；logout 后旧 token 401；重复注册 10001 |
| `PositionApiIT` | 列表/详情/skills | 两岗位可见；skills 含 weight 求和=1 |
| `InterviewFlowIT` ★核心 | 建会话→start→**文字回答 3 轮（含追问）**→finish→轮询 report | 状态链 CREATED→…→FINISHED；消息表 USER/AI 交替；report 六维均在 0~100；total_score 非空；WS 未连时流程照常（推送失败不影响落库） |
| `AudioAnswerIT` | mode=AUDIO 会话→multipart 提交 | uploads 下生成文件；message 含 asr_text/audio_path/duration；非音频扩展名 40001 |
| `SecurityIT` | 无 token / 学生访问 admin / 跨用户读面试 | 401 / 403 / 40401 |
| `HistoryGrowthIT` | 同用户两场面试→history/growth/recommendations | history 2 条；growth 曲线 2 个点；recommendations 非空且按优先级降序 |
| `AdminCrudIT` | 管理员建题→改题（带 version）→查列表→逻辑删；并发改第二版 | 乐观锁冲突 40003；DISABLED 题不再进入组卷 |

### 8.3 手工验收测试（对应方案 §16.4，M6 执行）

每个岗位人工作答 20 条标准问答（好/中/差三档答案），核对：追问相关性、评分与人工分差 ≤15、报告字段完整、建议对应短板。结果记录进答辩材料。

> 方案 §16.3 的 Playwright 端到端测试依赖前端页面，属于前后端联调阶段工作，不在本后端计划范围内；后端在 M6 用附录 C 的 curl 脚本 + §19 演示流程做等效链路验证。

---

## 9. 里程碑与任务分解

> 承接方案 §17 的 6 周计划，细化为 M1~M6。每个任务给"完成标准"（可执行验证），依赖前一里程碑完成。

### M1 基础工程 + 认证 + 岗位题库（方案第 1 周）

| 任务 | 产出 | 完成标准 |
|---|---|---|
| 1.1 Compose 起 MySQL/Redis | docker-compose.dev.yml | 两容器 healthy |
| 1.2 工程骨架 | pom.xml、application*.yml、主类、common 包（Result/异常/TraceId） | `mvn spring-boot:run` 启动无错，`GET /actuator/health` UP |
| 1.3 Flyway V1+V2 | 9 张表 DDL + admin/学生账号 + 2 岗位 | 启动自动迁移；`SHOW TABLES` 9 张 |
| 1.4 Security+JWT | SecurityConfig、JwtUtils、过滤器、黑名单、限流 | 附录 C 用例 1~5 全通过 |
| 1.5 auth 接口 5 个 | AuthController/UserController | AuthFlowIT 通过 |
| 1.6 position 接口 3 个 | PositionController | PositionApiIT 通过 |
| 1.7 题库 CRUD 4 个 | admin AdminQuestionController | AdminCrudIT 的 CRUD 部分通过 |
| 1.8 种子题 V3（Java 30 题） | V3__seed_questions_java.sql | 题量/比例断言 SQL 通过 |
| 1.9 种子题 V4（前端 30 题） | V4__seed_questions_frontend.sql | 同上 |

### M2 文字面试闭环（方案第 2 周）

| 任务 | 产出 | 完成标准 |
|---|---|---|
| 2.1 interview 实体/建会话/start | Controller+Service+Mapper、状态机 | curl 建会话→start 返回首题；非法 start 被 10005 拒绝 |
| 2.2 组卷 | QuestionService.assemblePaper | PaperAssemblerTest 通过 |
| 2.3 MockAiProvider + PromptBuilder + 解析容错 | ai 包（provider=mock 默认） | AiResponseParserTest 通过；mock 追问可循环产出 |
| 2.4 TurnEngine 文字回答 | §6.2 编排 | 文字回答→（同步或异步）产出下一条 AI 消息落库 |
| 2.5 追问规则引擎 | FollowupRuleEngine | FollowupRuleEngineTest 通过；追问≥3 换题在联调可见 |
| 2.6 WebSocket 推送 | Handler+注册表+消息类型 | wscat/websocat 连接收到 QUESTION/FOLLOW_UP/TIMER |
| 2.7 定时器与 abort | TimerScheduler、abort 接口 | 到期自动 TIMEOUT；abort 后终态不可再答题 |

### M3 评分 + 报告 + 历史（方案第 3 周）

| 任务 | 产出 | 完成标准 |
|---|---|---|
| 3.1 逐题评分 + 六维聚合 | evaluateTurn 链路、ScoreCalculator | ScoreCalculatorTest 通过 |
| 3.2 报告生成与落库 | ReportService、evaluation_report | 跑完一场→report 接口返回完整 ReportVO |
| 3.3 finish/异常收卷 | finish 接口、TIMEOUT 自动 finish | InterviewFlowIT 全绿 |
| 3.4 history 接口 | GrowthController.history | HistoryGrowthIT 的 history 断言通过 |
| 3.5 报告失败重试 | 未生成时 10004 + 重新生成入口 | 断开 AI（mock 抛错模拟）→ 重试成功 |

### M4 语音 + 知识库 RAG（方案第 4 周）

| 任务 | 产出 | 完成标准 |
|---|---|---|
| 4.1 音频上传与存储 | AudioStorageService、audio 接口 | AudioAnswerIT 通过 |
| 4.2 语音指标 | VoiceMetricsCalculator | VoiceMetricsCalculatorTest 通过；AUDIO 场报告含 voice 维度 |
| 4.3 知识检索 | KeywordExtractor、KnowledgeSearchService、V5 种子 | 命中"Redis 缓存"类回答能召回相关片段（SQL 手验） |
| 4.4 Prompt 注入 | PromptBuilder 知识片段段 | DEBUG 日志可见检索片段进入 prompt |
| 4.5 admin knowledge 接口 | AdminKnowledgeController + rebuild | 新增片段立即可被检索 |
| 4.6 真实 Provider | OpenAiCompatibleProvider | 配 `AI_API_KEY` 后 qwen 模式跑通一场（无网自动回 mock） |

### M5 个性化 + 管理端补全（方案第 5 周）

| 任务 | 产出 | 完成标准 |
|---|---|---|
| 5.1 掌握度更新 | MasteryUpdater | MasteryUpdaterTest 通过；两场后 ability_record 有记录 |
| 5.2 growth / recommendations | GrowthController 全部 | HistoryGrowthIT 全绿 |
| 5.3 短板识别+推荐引擎 | Weakness/Recommendation Engine | 对应单测通过 |
| 5.4 管理端扩展接口 ★ | positions/resources/users/weights/dashboard | SecurityIT 确认 403 拦截；dashboard 数据正确 |

### M6 测试 + 部署 + 验收（方案第 6 周）

| 任务 | 产出 | 完成标准 |
|---|---|---|
| 6.1 全量测试 | 单测+集成测试 | `mvn verify` 全绿；覆盖率（行）核心模块 ≥60% |
| 6.2 AI 质量抽测 | §8.3 记录表 | 人工分差 ≤15 的比例 ≥80% |
| 6.3 Dockerfile + docker-compose.yml | 五服务编排（§10） | `docker compose up -d` 一键起全系统，Nginx 80 端口可演示 |
| 6.4 Mock 离线演练 | 断网脚本 | 无网环境完整走通 面试→报告→推荐 |
| 6.5 验收自检 | §12 对照表 15 条（与方案 §20 逐条对应） | 全部勾选并附验证方式 |
| 6.6 文档 | backend/README.md、答辩演示脚本（对应方案 §19 15 步） | 按 README 新人可 30 分钟内启动 |

---

## 10. 部署方案（对应方案 §18）

### 10.1 docker-compose.yml（正式，M6 编写）

```yaml
services:
  mysql:
    image: mysql:8.0
    environment: { MYSQL_ROOT_PASSWORD: ${MYSQL_PASSWORD}, MYSQL_DATABASE: ai_interview }
    command: [--character-set-server=utf8mb4, --collation-server=utf8mb4_unicode_ci, --ngram-token-size=2]
    volumes: [mysql-data:/var/lib/mysql]
    healthcheck: { test: ["CMD","mysqladmin","ping","-h","localhost","-p${MYSQL_PASSWORD}"], interval: 10s, retries: 10 }

  redis:
    image: redis:7-alpine
    volumes: [redis-data:/data]

  backend:
    build: ./backend
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/ai_interview?useUnicode=true&characterEncoding=utf8
      SPRING_DATA_REDIS_HOST: redis
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      AI_PROVIDER: ${AI_PROVIDER:-mock}
      AI_API_URL: ${AI_API_URL:-}
      AI_API_KEY: ${AI_API_KEY:-}
      AI_MODEL: ${AI_MODEL:-qwen-plus}
    volumes: [ uploads:/app/uploads ]      # 音频持久化
    depends_on: { mysql: { condition: service_healthy }, redis: { condition: service_started } }

  frontend:
    build: ./frontend                        # node:20 构建 → 产物交 nginx
    restart: "no"                            # 构建期服务

  nginx:
    image: nginx:1.27-alpine
    ports: ["80:80"]
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/conf.d/default.conf:ro
      - frontend-dist:/usr/share/nginx/html:ro
    depends_on: [backend]

volumes: { mysql-data: {}, redis-data: {}, uploads: {}, frontend-dist: {} }
```

### 10.2 Nginx 路由

```nginx
server {
    listen 80;
    location /            { root /usr/share/nginx/html; try_files $uri $uri/ /index.html; }
    location /api/        { proxy_pass http://backend:8080; proxy_set_header Host $host; }
    location /ws/         { proxy_pass http://backend:8080; proxy_http_version 1.1;
                            proxy_set_header Upgrade $http_upgrade; proxy_set_header Connection "upgrade"; }
    client_max_body_size 12m;               # 音频上传
}
```

### 10.3 启动顺序（方案 §18.3）

```bash
# 开发期（后端本机跑，依赖容器化）：
docker compose -f docker-compose.dev.yml up -d
cd backend && mvn spring-boot:run            # dev profile 连 127.0.0.1

# 正式演示：
docker compose up -d --build                 # 五服务一键启动
```

### 10.4 backend/Dockerfile（多阶段）

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml ./
RUN mvn -B dependency:go-offline             # 依赖层缓存
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]
```

---

## 11. 风险与应对

| # | 风险 | 影响 | 应对 |
|---|---|---|---|
| R1 | 本机仅 JDK 25，Spring Boot 3.3 的 CGLIB/ByteBuddy 可能不识别 class file 69 | 启动报 Unsupported class file major version | 首选按 §2.1 装 Temurin 17 运行；应急 `-Dnet.bytebuddy.experimental=true`；升级 Boot 补丁版 |
| R2 | colima 默认 2C/2G，MySQL 8 吃紧 | 启动慢/OOM | `colima start --cpu 4 --memory 6`；compose 里 mysql 不设内存限制、backend 限制 768m |
| R3 | ngram 最小 token=2，单字查询不命中 | 检索空结果 | KeywordExtractor 过滤单字（§6.5）；查询词 ≥2 汉字 |
| R4 | `position` 为 MySQL 保留字 | SQL 语法错误 | DDL/SQL 一律反引号（§3.1）；实体 `@TableName("\`position\`")` |
| R5 | JSON 列无法建 FULLTEXT | 知识检索失效 | keywords/skill_tags 用 VARCHAR（§3.1 已定） |
| R6 | 千问接口超时/限流/欠费 | 面试卡死 | 30s 超时 + 三级容错（§6.8）；演示默认 mock |
| R7 | 浏览器 WS 无法带 Authorization 头 | WS 无法鉴权 | `?token=` query 鉴权 + 连接后立即校验（§5.6） |
| R8 | 用户双击重复提交回答 | 消息重复 | 状态机乐观锁天然拒绝（§6.1） |
| R9 | AI 返回夹带 markdown/多余文本 | JSON 解析失败 | 解析器剥围栏+抽取首个 `{...}` 平衡块（§6.8） |
| R10 | 上传目录在容器重建后丢失 | 音频丢失 | uploads 挂载命名卷（§10.1） |
| R11 | 前后端跨域（开发期 5173→8080） | 接口被拦 | SecurityConfig 放行 CORS（dev profile 允许 localhost:5173，prod 走 Nginx 同源） |
| R12 | 长报告生成阻塞请求线程 | 接口超时 | 报告生成 @Async + WS 通知（§4.4） |

其余降级场景（识别不可用、浏览器不支持录音、网络中断、报告失败可重生成、知识库无匹配）均已在 §6.8/§6.9/§14 对应机制覆盖，与方案 §14 一致。

---

## 12. 验收对照表（方案 §20 → 本计划落点）

| # | 验收标准 | 落点 | 验证方法 |
|---|---|---|---|
| 1 | 双岗位 | V2 种子 + position 模块 | GET /api/positions 返回 2 条 |
| 2 | 每岗 ≥20 题 | V3/V4（各 30 题） | 题量统计 SQL |
| 3 | 四类题型覆盖 | 种子比例 50/20/20/10 | PaperAssemblerTest + SQL 分组计数 |
| 4 | 文字+语音输入 | §5.3 #11/#12、§6.9 | InterviewFlowIT / AudioAnswerIT |
| 5 | AI ≥3 轮动态追问 | §6.4 规则引擎 | 完整跑一场含追问的面试，消息表验证 |
| 6 | 大模型参与对话/理解/报告 | ai 模块三方法（§4.3） | qwen 模式联调 + prompt 日志 |
| 7 | 知识检索注入提示词 | §6.5/§4.4 | DEBUG 日志含检索片段 |
| 8 | 报告多维评分 | §6.6 六维 | report 接口返回 dimension_scores |
| 9 | 报告含分数/证据/亮点/不足/建议 | ReportVO 结构 | InterviewFlowIT 断言字段非空 |
| 10 | 历史记录 | §5.4 history | HistoryGrowthIT |
| 11 | 成长曲线 | growth 接口 | 两场面试后曲线 2 点 |
| 12 | 短板推荐资源/计划 | §6.7 | recommendations 接口断言 |
| 13 | 管理员维护题库/知识库 | §5.5 | AdminCrudIT |
| 14 | 真实大模型与 Mock 双模式 | §6.8 | mock 全流程演示 + qwen 联调记录 |
| 15 | Docker Compose 一键启动 | §10 | docker compose up -d 后走完 §19 演示 15 步 |

---

## 附录A 错误码表

| code | 含义 | HTTP |
|---|---|---|
| 0 | 成功 | 200 |
| 10001 | 用户名已存在 | 200（业务码） |
| 10002 | 用户名或密码错误 | 200 |
| 10003 | 账号已禁用 | 200 |
| 10004 | 报告尚未生成 | 200 |
| 10005 | 面试状态冲突（非法状态转换） | 200 |
| 40001 | 参数校验失败 | 400 |
| 40002 | rounds/difficulty 超出范围 | 400 |
| 40003 | 乐观锁版本冲突 | 200 |
| 40401 | 资源不存在或不属于当前用户 | 200（不暴露存在性） |
| 40101 | 未登录/Token 无效或已登出 | 401 |
| 40301 | 无权限（角色不足） | 403 |
| 42901 | 请求过于频繁（限流） | 429 |
| 50001 | 服务器内部错误 | 500 |

> 业务失败统一 HTTP 200 + code 区分（前后端只看 code），仅协议级错误用 HTTP 状态码——联调前与前端约定写进 README。

## 附录B 配置项清单（application.yml 骨架）

```yaml
spring:
  profiles: { active: dev }
  servlet: { multipart: { max-file-size: 10MB, max-request-size: 12MB } }
  datasource:
    url: jdbc:mysql://${DB_HOST:127.0.0.1}:3306/ai_interview?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: ${MYSQL_PASSWORD:root123456}
  flyway: { enabled: true, locations: classpath:db/migration, baseline-on-migrate: true }
  data: { redis: { host: ${REDIS_HOST:127.0.0.1}, port: 6379 } }

jwt:
  secret: ${JWT_SECRET:dev-only-secret-key-32bytes-minimum!!}
  expire-hours: 24

ai:
  provider: ${AI_PROVIDER:mock}        # mock / qwen
  api-url: ${AI_API_URL:}
  api-key: ${AI_API_KEY:}
  model: ${AI_MODEL:qwen-plus}
  timeout: 30000                     # 方案 §6.6 原配置项名

app:
  upload-dir: ${UPLOAD_DIR:./uploads}  # 音频存储目录（prod 指向挂载卷 /app/uploads）
  interview:
    max-rounds: 8
    min-rounds: 5
    followup-limit: 3                 # 同题主问题追问上限
    default-duration-minutes: 30
```

## 附录C 核心流程 curl 验证脚本（M1 起维护进 api.http）

```bash
BASE=http://127.0.0.1:8080
# 1. 注册
curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"stu01","password":"123456","major":"软件工程"}'
# 2. 登录拿 token
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"stu01","password":"123456"}' | jq -r '.data.token')
AUTH="Authorization: Bearer $TOKEN"
# 3. 岗位列表
curl -s $BASE/api/positions -H "$AUTH"
# 4. 创建面试（Java 岗=1，文字，难度3，6 题，30 分钟）
SID=$(curl -s -X POST $BASE/api/interviews -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"positionId":1,"mode":"TEXT","difficulty":3,"rounds":6,"durationMinutes":30}' | jq -r '.data.id')
# 5. 开始（返回首题）
curl -s -X POST $BASE/api/interviews/$SID/start -H "$AUTH"
# 6. 提交一轮文字回答
curl -s -X POST $BASE/api/interviews/$SID/messages/text -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"roundNo":0,"content":"缓存击穿是指热点key过期瞬间大量请求直达数据库，可以用互斥锁或逻辑过期缓解"}'
# 7. 结束并取报告
curl -s -X POST $BASE/api/interviews/$SID/finish -H "$AUTH"
curl -s $BASE/api/interviews/$SID/report -H "$AUTH"
# 8. 成长与推荐
curl -s "$BASE/api/users/me/growth?positionId=1" -H "$AUTH"
curl -s "$BASE/api/users/me/recommendations?positionId=1" -H "$AUTH"
# 9. WebSocket（另开终端，websocat）
# websocat "ws://127.0.0.1:8080/ws/interviews/$SID?token=$TOKEN"
```

---

> **版本**：v1.0（2026-10-13） · 依据《AI模拟面试与能力提升平台_系统实现方案.md》制定
> 后端开发按 §9 里程碑 M1 → M6 顺序推进；实施过程中若与方案冲突，以方案为准并回写本计划。
