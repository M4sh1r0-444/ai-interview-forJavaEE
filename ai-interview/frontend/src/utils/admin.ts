import type { EChartsCoreOption } from 'echarts/core'

import type {
  DashboardTrendPoint,
  QuestionAdminVO,
  QuestionUpsertPayload,
  ScoreWeights,
  SkillItem,
} from '@/types/admin'
import type { QuestionStatus, QuestionType, ResourceType } from '@/types/interview'

/** 后端 AdminPositionService 的权重和容差（技能/评分三组均为 ±0.01）。 */
export const WEIGHT_SUM_TOLERANCE = 0.01

export type ScoreGroupKey = 'content' | 'expression' | 'total'

export const CONTENT_WEIGHT_FIELDS = [
  { key: 'technical', label: '技术正确性', hint: '技术要点是否正确、是否有明显错误' },
  { key: 'depth', label: '知识深度', hint: '是否讲清原理、边界与取舍' },
  { key: 'logic', label: '逻辑严谨性', hint: '结构、因果与前后一致性' },
  { key: 'evidence', label: '证据充分性', hint: '是否有案例、数据或具体细节支撑' },
  { key: 'match', label: '岗位匹配', hint: '回答与目标岗位能力的贴合度' },
] as const

export const EXPRESSION_WEIGHT_FIELDS = [
  { key: 'clarity', label: '表达清晰度', hint: '重点突出、易于理解' },
  { key: 'fluency', label: '语言流畅度', hint: '停顿、重复与语病控制' },
  { key: 'pace', label: '语速节奏', hint: '语速适中、有合理停顿' },
  { key: 'confidence', label: '表达自信度', hint: '语气坚定、少犹豫词' },
  { key: 'completeness', label: '表达完整度', hint: '结论、过程与收尾完整' },
] as const

export const TOTAL_WEIGHT_FIELDS = [
  { key: 'content', label: '内容分', hint: '技术内容在总分中的占比' },
  { key: 'expression', label: '表达分', hint: '表达与沟通在总分中的占比' },
  { key: 'adaptability', label: '应变分', hint: '追问调整能力在总分中的占比' },
  { key: 'match', label: '岗位匹配', hint: '岗位匹配度在总分中的占比' },
] as const

export type ContentWeightKey = (typeof CONTENT_WEIGHT_FIELDS)[number]['key']
export type ExpressionWeightKey = (typeof EXPRESSION_WEIGHT_FIELDS)[number]['key']
export type TotalWeightKey = (typeof TOTAL_WEIGHT_FIELDS)[number]['key']

export type ContentWeightsForm = Record<ContentWeightKey, number>
export type ExpressionWeightsForm = Record<ExpressionWeightKey, number>
export type TotalWeightsForm = Record<TotalWeightKey, number>

/** 权重编辑器的完整三组表单（后端缺省值与 V2 种子一致）。 */
export interface ScoreWeightsForm {
  content: ContentWeightsForm
  expression: ExpressionWeightsForm
  total: TotalWeightsForm
}

export interface ScoreWeightField {
  key: string
  label: string
  hint: string
}

export interface ScoreWeightGroup {
  key: ScoreGroupKey
  title: string
  description: string
  fields: readonly ScoreWeightField[]
}

export const SCORE_WEIGHT_GROUPS: readonly ScoreWeightGroup[] = [
  {
    key: 'content',
    title: '内容分权重',
    description: '逐题内容分 = technical×权重 + depth×...（缺失子维度按已出现权重归一化）',
    fields: CONTENT_WEIGHT_FIELDS,
  },
  {
    key: 'expression',
    title: '表达分权重',
    description: '表达分用于 TEXT 的沟通表达与 AUDIO 的表达子维度（组内和=1）',
    fields: EXPRESSION_WEIGHT_FIELDS,
  },
  {
    key: 'total',
    title: '总分权重',
    description: '总分 = 内容×权重 + 表达×权重 + 应变×权重 + 岗位匹配×权重（组内和=1）',
    fields: TOTAL_WEIGHT_FIELDS,
  },
]

/** 与后端 ScoreCalculator/V2 种子一致的默认权重；「恢复默认」直接使用此值。 */
export const DEFAULT_SCORE_WEIGHTS: ScoreWeightsForm = {
  content: {
    technical: 0.3,
    depth: 0.25,
    logic: 0.2,
    evidence: 0.15,
    match: 0.1,
  },
  expression: {
    clarity: 0.3,
    fluency: 0.2,
    pace: 0.2,
    confidence: 0.15,
    completeness: 0.15,
  },
  total: {
    content: 0.5,
    expression: 0.3,
    adaptability: 0.1,
    match: 0.1,
  },
}

export function cloneScoreWeights(form: ScoreWeightsForm): ScoreWeightsForm {
  return {
    content: { ...form.content },
    expression: { ...form.expression },
    total: { ...form.total },
  }
}

function finiteNumber(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

/** 后端可能只配置了部分组/字段；缺省部分回填默认值，避免编辑器出现空输入。 */
export function normalizeScoreWeights(input?: ScoreWeights | null): ScoreWeightsForm {
  return {
    content: {
      technical: finiteNumber(input?.content?.technical, DEFAULT_SCORE_WEIGHTS.content.technical),
      depth: finiteNumber(input?.content?.depth, DEFAULT_SCORE_WEIGHTS.content.depth),
      logic: finiteNumber(input?.content?.logic, DEFAULT_SCORE_WEIGHTS.content.logic),
      evidence: finiteNumber(input?.content?.evidence, DEFAULT_SCORE_WEIGHTS.content.evidence),
      match: finiteNumber(input?.content?.match, DEFAULT_SCORE_WEIGHTS.content.match),
    },
    expression: {
      clarity: finiteNumber(input?.expression?.clarity, DEFAULT_SCORE_WEIGHTS.expression.clarity),
      fluency: finiteNumber(input?.expression?.fluency, DEFAULT_SCORE_WEIGHTS.expression.fluency),
      pace: finiteNumber(input?.expression?.pace, DEFAULT_SCORE_WEIGHTS.expression.pace),
      confidence: finiteNumber(
        input?.expression?.confidence,
        DEFAULT_SCORE_WEIGHTS.expression.confidence,
      ),
      completeness: finiteNumber(
        input?.expression?.completeness,
        DEFAULT_SCORE_WEIGHTS.expression.completeness,
      ),
    },
    total: {
      content: finiteNumber(input?.total?.content, DEFAULT_SCORE_WEIGHTS.total.content),
      expression: finiteNumber(input?.total?.expression, DEFAULT_SCORE_WEIGHTS.total.expression),
      adaptability: finiteNumber(
        input?.total?.adaptability,
        DEFAULT_SCORE_WEIGHTS.total.adaptability,
      ),
      match: finiteNumber(input?.total?.match, DEFAULT_SCORE_WEIGHTS.total.match),
    },
  }
}

/** 只提交后端校验所需的 5/5/4 个字段（未用字段不写入 JSON）。 */
export function scoreWeightsToPayload(form: ScoreWeightsForm): ScoreWeights {
  return {
    content: { ...form.content },
    expression: { ...form.expression },
    total: { ...form.total },
  }
}

export function getGroupWeight(form: ScoreWeightsForm, group: ScoreGroupKey, key: string): number {
  const values = form[group] as Record<string, unknown>
  return typeof values[key] === 'number' ? (values[key] as number) : Number.NaN
}

export function setGroupWeight(
  form: ScoreWeightsForm,
  group: ScoreGroupKey,
  key: string,
  value: number,
): void {
  ;(form[group] as Record<string, number>)[key] = value
}

export function roundWeight(value: number): number {
  return Math.round(value * 10_000) / 10_000
}

export function sumWeights(values: readonly number[]): number {
  return roundWeight(values.reduce((sum, value) => sum + (Number.isFinite(value) ? value : 0), 0))
}

export function weightSumValid(sum: number): boolean {
  return Number.isFinite(sum) && Math.abs(sum - 1) <= WEIGHT_SUM_TOLERANCE + 1e-9
}

export function formatWeight(value?: number | null): string {
  if (value == null || !Number.isFinite(value)) return '—'
  return value.toFixed(2)
}

export function formatWeightSum(sum: number): string {
  return Number.isFinite(sum) ? sum.toFixed(2) : '—'
}

/** 实时提示文案：「差 0.10」/「超出 0.05」/「合计正确」。 */
export function weightDifferenceText(sum: number): string {
  if (!Number.isFinite(sum)) return '存在未填写的权重'
  const diff = roundWeight(1 - sum)
  if (Math.abs(diff) <= WEIGHT_SUM_TOLERANCE) return '合计正确'
  return diff > 0 ? `还差 ${diff.toFixed(2)}` : `超出 ${Math.abs(diff).toFixed(2)}`
}

export interface WeightGroupValidation {
  key: ScoreGroupKey
  sum: number
  valid: boolean
  messages: string[]
}

export interface ScoreWeightsValidation {
  valid: boolean
  groups: Record<ScoreGroupKey, WeightGroupValidation>
  messages: string[]
}

export function validateScoreWeightsForm(form: ScoreWeightsForm): ScoreWeightsValidation {
  const groups = {} as Record<ScoreGroupKey, WeightGroupValidation>
  const messages: string[] = []

  for (const group of SCORE_WEIGHT_GROUPS) {
    const groupMessages: string[] = []
    const values = group.fields.map((field) => getGroupWeight(form, group.key, field.key))
    values.forEach((value, index) => {
      const field = group.fields[index]
      if (!Number.isFinite(value)) {
        groupMessages.push(`「${field.label}」未填写`)
        return
      }
      if (value < 0 || value > 1) {
        groupMessages.push(`「${field.label}」取值须在 0~1`)
      }
    })
    const sum = sumWeights(values)
    if (!weightSumValid(sum)) {
      groupMessages.push(`${group.title}合计须为 1（当前 ${formatWeightSum(sum)}）`)
    }
    const valid = groupMessages.length === 0
    groups[group.key] = { key: group.key, sum, valid, messages: groupMessages }
    messages.push(...groupMessages)
  }

  return { valid: messages.length === 0, groups, messages }
}

export interface SkillWeightsValidation {
  valid: boolean
  sum: number
  duplicateCodes: string[]
  messages: string[]
}

/** 技能行校验：与后端 AdminPositionService.validateSkills 同规则。 */
export function validateSkillWeights(skills: readonly SkillItem[]): SkillWeightsValidation {
  const messages: string[] = []
  const duplicateCodes: string[] = []
  const seen = new Set<string>()

  if (!Array.isArray(skills) || skills.length === 0) {
    messages.push('能力模型至少包含 1 个技能')
  }

  const values = skills.map((skill, index) => {
    const code = (skill.skillCode ?? '').trim()
    const name = (skill.skillName ?? '').trim()
    const weight = typeof skill.weight === 'number' ? skill.weight : Number.NaN
    const requiredLevel = skill.requiredLevel == null ? null : Number(skill.requiredLevel)

    if (!code) messages.push(`第 ${index + 1} 行 skillCode 必填`)
    if (!name) messages.push(`第 ${index + 1} 行 skillName 必填`)
    if (!Number.isFinite(weight) || weight <= 0 || weight > 1) {
      messages.push(`技能「${code || index + 1}」权重须在 (0,1]`)
    }
    if (
      requiredLevel != null &&
      (!Number.isFinite(requiredLevel) || requiredLevel < 0 || requiredLevel > 100)
    ) {
      messages.push(`技能「${code || index + 1}」要求等级须在 0~100`)
    }
    if (code) {
      const normalized = code.toLowerCase()
      if (seen.has(normalized)) duplicateCodes.push(code)
      seen.add(normalized)
    }
    return weight
  })

  for (const code of new Set(duplicateCodes)) {
    messages.push(`技能编码重复：${code}`)
  }

  const sum = sumWeights(values)
  if (skills.length > 0 && !weightSumValid(sum)) {
    messages.push(`技能权重和须为 1（当前 ${formatWeightSum(sum)}）`)
  }

  return { valid: messages.length === 0, sum, duplicateCodes, messages }
}

export function cleanSkills(skills: readonly SkillItem[]): SkillItem[] {
  return skills.map((skill) => ({
    skillCode: (skill.skillCode ?? '').trim(),
    skillName: (skill.skillName ?? '').trim(),
    description: (skill.description ?? '').trim() || undefined,
    weight: typeof skill.weight === 'number' ? skill.weight : 0,
    requiredLevel:
      skill.requiredLevel == null || !Number.isFinite(Number(skill.requiredLevel))
        ? null
        : Number(skill.requiredLevel),
  }))
}

// ===== 题库表单 =====

export const QUESTION_TYPE_OPTIONS: { value: QuestionType; label: string }[] = [
  { value: 'TECHNICAL', label: '技术题' },
  { value: 'PROJECT', label: '项目题' },
  { value: 'SCENARIO', label: '场景题' },
  { value: 'BEHAVIORAL', label: '行为题' },
]

export const QUESTION_STATUS_LABELS: Record<QuestionStatus, string> = {
  ENABLED: '已启用',
  DISABLED: '已删除',
}

export const RESOURCE_TYPE_OPTIONS: { value: ResourceType; label: string }[] = [
  { value: 'ARTICLE', label: '文章' },
  { value: 'QUESTION', label: '题目' },
  { value: 'PRACTICE', label: '练习' },
  { value: 'PROJECT', label: '项目' },
]

export interface QuestionFormModel {
  positionId: number | null
  type: QuestionType
  content: string
  background: string
  difficulty: number
  skillTags: string[]
  referenceAnswer: string
  keyPoints: string[]
  deductionPoints: string[]
  followupTemplates: string[]
  version?: number
}

export function emptyQuestionForm(positionId: number | null = null): QuestionFormModel {
  return {
    positionId,
    type: 'TECHNICAL',
    content: '',
    background: '',
    difficulty: 3,
    skillTags: [],
    referenceAnswer: '',
    keyPoints: [],
    deductionPoints: [],
    followupTemplates: [],
  }
}

export function questionToForm(question: QuestionAdminVO): QuestionFormModel {
  return {
    positionId: question.positionId,
    type: question.type,
    content: question.content ?? '',
    background: question.background ?? '',
    difficulty: question.difficulty,
    skillTags: [...(question.skillTags ?? [])],
    referenceAnswer: question.referenceAnswer ?? '',
    keyPoints: [...(question.keyPoints ?? [])],
    deductionPoints: [...(question.deductionPoints ?? [])],
    followupTemplates: [...(question.followupTemplates ?? [])],
    version: question.version,
  }
}

/** 新增不带 version；编辑必须携带 version（后端乐观锁）。 */
export function questionFormToPayload(form: QuestionFormModel): QuestionUpsertPayload {
  if (!form.positionId) throw new Error('岗位不能为空')
  const payload: QuestionUpsertPayload = {
    positionId: form.positionId,
    type: form.type,
    content: form.content.trim(),
    difficulty: form.difficulty,
    skillTags: form.skillTags.map((tag) => tag.trim()).filter(Boolean),
  }
  const background = form.background.trim()
  if (background) payload.background = background
  const referenceAnswer = form.referenceAnswer.trim()
  if (referenceAnswer) payload.referenceAnswer = referenceAnswer
  if (form.keyPoints.length > 0)
    payload.keyPoints = form.keyPoints.map((item) => item.trim()).filter(Boolean)
  if (form.deductionPoints.length > 0) {
    payload.deductionPoints = form.deductionPoints.map((item) => item.trim()).filter(Boolean)
  }
  if (form.followupTemplates.length > 0) {
    payload.followupTemplates = form.followupTemplates.map((item) => item.trim()).filter(Boolean)
  }
  if (form.version != null) payload.version = form.version
  return payload
}

export function validateQuestionForm(form: QuestionFormModel): string[] {
  const messages: string[] = []
  if (!form.positionId) messages.push('请选择岗位')
  if (!QUESTION_TYPE_OPTIONS.some((item) => item.value === form.type)) messages.push('题型不合法')
  const content = form.content.trim()
  if (content.length < 4 || content.length > 1000) messages.push('题目内容需为 4~1000 字')
  if (form.background.trim().length > 1000) messages.push('题目背景不能超过 1000 字')
  if (!Number.isInteger(form.difficulty) || form.difficulty < 1 || form.difficulty > 5) {
    messages.push('难度需为 1~5')
  }
  if (form.skillTags.length === 0) messages.push('至少选择 1 个技能标签')
  if (form.skillTags.length > 10) messages.push('技能标签最多 10 个')
  if (form.referenceAnswer.trim().length > 4000) messages.push('参考答案不能超过 4000 字')
  if (form.keyPoints.length > 10) messages.push('得分点最多 10 条')
  if (form.deductionPoints.length > 10) messages.push('失分点最多 10 条')
  if (form.followupTemplates.length > 10) messages.push('追问模板最多 10 条')
  return messages
}

// ===== 知识库 / 逗号分隔字段 =====

/** 逗号分隔字符串 → 去空、去重（忽略大小写）的数组；中英文逗号都接受。 */
export function splitCommaList(value?: string | null): string[] {
  if (!value) return []
  const seen = new Set<string>()
  const result: string[] = []
  for (const raw of value.split(/[,，]/)) {
    const item = raw.trim()
    if (!item) continue
    const key = item.toLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    result.push(item)
  }
  return result
}

export function joinCommaList(values?: readonly string[] | null): string {
  return splitCommaList((values ?? []).join(',')).join(',')
}

/** 「每行一条」数组编辑器：保留所有非空行（上限由编辑器/校验提示控制）。 */
export function linesToArray(value: string): string[] {
  return value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
}

export function arrayToLines(values?: readonly string[] | null): string {
  return (values ?? []).join('\n')
}

// ===== 查询参数解析 =====

export function parsePositiveInt(value: unknown): number | null {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = typeof raw === 'string' ? Number(raw) : Number.NaN
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

export function parseQuestionType(value: unknown): QuestionType | '' {
  const raw = Array.isArray(value) ? value[0] : value
  return QUESTION_TYPE_OPTIONS.some((item) => item.value === raw) ? (raw as QuestionType) : ''
}

export function parseDifficulty(value: unknown): number | null {
  const parsed = parsePositiveInt(value)
  return parsed != null && parsed >= 1 && parsed <= 5 ? parsed : null
}

export function parseResourceType(value: unknown): ResourceType | '' {
  const raw = Array.isArray(value) ? value[0] : value
  return RESOURCE_TYPE_OPTIONS.some((item) => item.value === raw) ? (raw as ResourceType) : ''
}

// ===== 看板柱线组合图 =====

/**
 * 近 7 日趋势：柱=每日开始场次（左轴），线=每日出分均分（右轴）。
 * null 均分保持 null，并用 connectNulls:false 形成断点（计划 §5.13）。
 */
export function buildDashboardTrendOption(
  points: readonly DashboardTrendPoint[],
): EChartsCoreOption {
  const dates = points.map((point) => point.date)
  const sessionCounts = points.map((point) => Number(point.sessionCount) || 0)
  const avgScores = points.map((point) =>
    typeof point.avgScore === 'number' && Number.isFinite(point.avgScore) ? point.avgScore : null,
  )

  return {
    animationDuration: 500,
    color: ['#2563eb', '#f59e0b'],
    grid: { top: 52, right: 58, bottom: 28, left: 18, containLabel: true },
    legend: {
      top: 0,
      right: 0,
      itemWidth: 14,
      itemHeight: 8,
      textStyle: { color: '#475569', fontSize: 12 },
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#ffffff',
      borderColor: 'rgba(148, 163, 184, 0.35)',
      borderWidth: 1,
      padding: [8, 12],
      textStyle: { color: '#0f172a', fontSize: 12 },
      extraCssText: 'box-shadow: 0 12px 30px rgba(15, 23, 42, 0.14); border-radius: 10px;',
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.4)' } },
      axisLabel: {
        color: '#64748b',
        fontSize: 11,
        interval: 0,
        formatter: (value: string) => value.slice(5),
      },
    },
    yAxis: [
      {
        type: 'value',
        name: '场次',
        minInterval: 1,
        nameTextStyle: { color: '#64748b', fontSize: 11 },
        axisLabel: { color: '#64748b', fontSize: 11 },
        splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.18)' } },
      },
      {
        type: 'value',
        name: '均分',
        min: 0,
        max: 100,
        nameTextStyle: { color: '#64748b', fontSize: 11 },
        axisLabel: { color: '#64748b', fontSize: 11 },
        splitLine: { show: false },
      },
    ],
    media: [
      {
        query: { maxWidth: 520 },
        option: {
          grid: { top: 56, right: 42, bottom: 46, left: 12, containLabel: true },
          xAxis: { axisLabel: { rotate: 35, fontSize: 10 } },
        },
      },
    ],
    series: [
      {
        name: '面试场次',
        type: 'bar',
        yAxisIndex: 0,
        barMaxWidth: 28,
        itemStyle: { borderRadius: [6, 6, 0, 0], color: '#2563eb' },
        emphasis: { itemStyle: { color: '#1d4ed8' } },
        data: sessionCounts,
      },
      {
        name: '平均分',
        type: 'line',
        yAxisIndex: 1,
        smooth: false,
        connectNulls: false,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 2.5, color: '#f59e0b' },
        itemStyle: { color: '#f59e0b', borderColor: '#fff', borderWidth: 2 },
        data: avgScores,
      },
    ],
  }
}
