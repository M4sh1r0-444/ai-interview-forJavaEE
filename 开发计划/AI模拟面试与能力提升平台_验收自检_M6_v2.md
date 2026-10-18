# M6 验收自检表 v2（FIX-01~20 修复后重新验收）

> **依据**：《后端验收检查报告_20261017》（12/15 → 修复）与《后端修复计划_20261017》§8 重新验收清单。
> **验证时间**：2026-10-18；**分支**：`fix/backend-acceptance-20261017`；**执行**：ZCode（后端验收修复会话 Phase 3/4）。
> **证据标记**：**[现场]** = 本轮新实测（命令可复跑）；**[IT]** = `mvn -B verify` 166 用例全绿（JDK17，JaCoCo 行覆盖 89.55%）；**[部署]** = `qa/verify_compose_acceptance.sh` 全新独立 Compose project 实测（PASS 13/13）；**[stub]** = `qa/verify_real_provider_auth.sh` 本地鉴权 stub 冒烟（PASS）；**[离线]** = `qa/m6_offline_drill.sh` 本轮复跑；**[v1]** = 沿用 v1 自检仍有效的历史证据。
> **D5 口径**：真实 qwen key 的成功调用是 #6/#14 的最终条件；本轮用户确认无 key（2026-10-18），两条维持「协议级通过、真实待环境验证」，**不宣称 15/15**。
> 详细命令与输出见《AI模拟面试与能力提升平台_修复验证记录_20261018.md》。

## 总结论：**13/15 通过 + #6/#14 协议级通过（真实 key 待环境验证）⚠️**（2026-10-18）

| # | 验收标准（方案 §20） | 结果 | 验证方式（v2 新证据 → 沿用证据） |
|---|---|---|---|
| 1 | 双岗位 | ✅ | [部署] 全新迁移库 GET /api/positions 返回 2 条 [v1] SQL 能力模型各 7 技能 |
| 2 | 每岗 ≥20 题 | ✅ | [v1] SQL 30/30（≥20） |
| 3 | 四类题型覆盖 | ✅ | [v1] 15/6/6/3 + PaperAssemblerTest 4 用例 [IT] |
| 4 | 文字+语音输入 | ✅ | [IT] InterviewTextFlowIT 5 / AudioAnswerIT 3（含 FIX-14 后 match 口径） |
| 5 | AI ≥3 轮动态追问 | ✅ | [v1] 每轮 3 追问 × 5 轮；[IT] FollowupRuleEngineTest 9（R5/R6 difficultyDelta ±1 断言）；**口径 D37**：升/降难度作用于追问难度与提示强度，主问题组卷锁定（FIX-13 文档收口） |
| 6 | 大模型参与对话/理解/报告 | ⚠️ 协议级通过 | [stub] 本地 Bearer stub 冒烟：evaluateTurn/nextQuestion/generateReport **三路径全部经真实 Provider 产出带标记结果（零降级）**，stub 侧 Authorization 全部精确 `Bearer <key>`；[IT] OpenAiCompatibleProviderTest 8。**真实 key 成功调用待环境验证（D5）** |
| 7 | 知识检索注入提示词 | ✅ | [IT] KnowledgeSearchRecallIT 2（FIX-15：严格 0 命中回退宽松仍召回 + 岗位隔离）；[v1] Prompt【参考知识片段】日志 |
| 8 | 报告多维评分 | ✅ | [v1] TEXT 5 维 / AUDIO 6 维；[IT] MockMatchScoreTest 4（FIX-14 全中文高分 match≥75） |
| 9 | 报告含分数/证据/亮点/不足/建议 | ✅ | [IT] ReportEvidenceIT 3（FIX-07 后读取不调 AI、旧报告回填一次）；[v1] 五要素实录 |
| 10 | 历史记录 | ✅ | [IT] HistoryGrowthIT；[部署] 分页 page=1&size=10 正常（FIX-11 越界 400 断言见 PaginationValidationIT） |
| 11 | 成长曲线 | ✅ | [v1] 两场 5 曲线 2 点；[IT] MasteryIdempotencyIT 2（FIX-12 并发只写一套） |
| 12 | 短板推荐资源/计划 | ✅ | [v1] Top3/资源/错题/下场建议；[IT] RecommendationIT（读取不隐式调 AI） |
| 13 | 管理员维护题库/知识库 | ✅ | [IT] AdminValidationIT 5（FIX-18：defaultRounds=100/enabled=5→40001、坏 positionId→40002、并发注册败者 10001）+ AdminCrud/AdminExtended；[部署] 管理链路可用 |
| 14 | 真实大模型与 Mock 双模式 | ⚠️ 协议级通过 | [离线] Mock 断网演练见 #15；[stub] qwen 模式协议级链路（鉴权/解析/产出/零降级）。**真实 key 成功调用是本条最终条件（D5），待环境验证——不得勾选 15/15** |
| 15 | Docker Compose 一键启动 | ✅ | [部署] **PASS 13/13**：页面内容含产品名（非默认页）、assets 200、/api 40101、登录/岗位、同源 WS 101 + **95s 存活（4×PING 自动回 PONG，无 4408）**、限流 200×5→第 6 次 429（D2 语义经 Nginx 生效）、伪造 XFF 仍 429、**Nginx 全部日志无 token**（safe 格式仍可审计）；[IT] WebSocketOriginIT 同源 101/外部 403 |

### 附加验收（计划 §8「附加验收」块）

| 项 | 结果 | 证据 |
|---|---|---|
| `mvn -B verify` JDK17 全绿、覆盖率 ≥60% | ✅ | 166/166，JaCoCo 行覆盖 **89.55%** [Gate 3] |
| JDK25 明确报错（不再 Byte Buddy 难懂失败） | ✅ | enforcer `[17,25)`：`Rule 0: RequireJavaVersion failed`（FIX-17 实测） |
| 全新 Compose 内容/API/WS/限流脚本全通 | ✅ | verify_compose_acceptance.sh PASS 13/13（独立 project + down -v，D6） |
| 真实 Provider stub 鉴权通过 | ✅ | verify_real_provider_auth.sh PASS（三路径 + 精确 Bearer） |
| finish/abort/timeout 竞态测试通过 | ✅ | TurnEngineTerminalRaceIT 4（FIX-05，Phase 2） |
| 重启时长保持/处理中恢复测试通过 | ✅ | SessionDurationRestartIT / ProcessingRecoveryIT（FIX-08） |
| 报告 GET 不调 AI 测试通过 | ✅ | ReportEvidenceIT 3（FIX-07） |
| 离线演练 13/13 | ✅ | 本轮复跑（FIX-19 后：页面**内容断言**替代仅 200），见《修复验证记录》§5.4 |
| 文档/演示脚本/P10/P11 更新完成 | ✅ | §14 payload 交叉验证（P10 关闭）；cleanup --yes + 全新迁移库演示口径（P11）；答辩脚本/README/前端契约错误码同步 |
| 演示库无测试残留 | ✅ | `qa/cleanup_e2e_data.sh --yes`（E2E_/ACC_/e2e_/acc_/race_/dis_/shots2_ 前缀）+ 演示用全新迁移库（D6） |

---

## v1 → v2 变化说明

- **#6/#14**：v1 即按 D5 改判协议级通过；v2 新增 stub 冒烟实证（三路径零降级），仍是「真实 key 待环境验证」（用户 2026-10-18 确认无 key）。
- **#15**：v1 标注「限流/日志脱敏属 Phase 3」；v2 由 FIX-09/10 补齐并经独立 Compose 栈实测（13/13）。
- **#5/#7/#8/#11/#13**：分别叠加 FIX-13 口径收口、FIX-15 召回、FIX-14 中文 match、FIX-12 幂等、FIX-18 校验的新 IT 证据。
- 原始 v1 表格保留于《AI模拟面试与能力提升平台_验收自检_M6.md》（历史证据不复述）。

> 自检人：ZCode（后端验收修复会话 · Phase 3/4）· 复跑入口：`ai-interview/qa/verify_compose_acceptance.sh`、`ai-interview/qa/verify_real_provider_auth.sh`、`ai-interview/qa/m6_offline_drill.sh`。
