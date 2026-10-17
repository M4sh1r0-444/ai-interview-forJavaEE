import type { DimensionKey } from '@/types/report'

export type ScoreTone = 'danger' | 'warning' | 'primary' | 'success'

/** 分数色带：<60 危险、60~74 警告、75~84 主色、≥85 成功（计划 §6.7）。 */
export function scoreTone(score?: number | null): ScoreTone {
  if (score == null || !Number.isFinite(score) || score < 60) return 'danger'
  if (score < 75) return 'warning'
  if (score < 85) return 'primary'
  return 'success'
}

export function scoreClass(score?: number | null): string {
  return `score--${scoreTone(score)}`
}

export function scoreLevelLabel(score?: number | null): string {
  if (score == null || !Number.isFinite(score)) return '暂无评分'
  if (score < 60) return '待提升'
  if (score < 75) return '合格'
  if (score < 85) return '良好'
  return '优秀'
}

/** 报告维度固定顺序，便于跨场对比。 */
export const DIMENSION_ORDER: DimensionKey[] = [
  'technical',
  'depth',
  'logic',
  'match',
  'communication',
  'voice',
]

export const DIMENSION_LABELS: Record<DimensionKey, string> = {
  technical: '技术正确性',
  depth: '知识深度',
  logic: '逻辑严谨性',
  match: '岗位匹配',
  communication: '沟通表达',
  voice: '语音表现',
}

export function dimensionLabel(key: DimensionKey): string {
  return DIMENSION_LABELS[key] ?? key
}

/** 图表色板（7 技能） */
export const CHART_COLORS = [
  '#2563eb',
  '#0ea5e9',
  '#14b8a6',
  '#22c55e',
  '#f59e0b',
  '#f97316',
  '#8b5cf6',
] as const

export function chartColor(index: number): string {
  return CHART_COLORS[index % CHART_COLORS.length]
}
