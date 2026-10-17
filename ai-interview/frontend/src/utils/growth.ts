import type { EChartsCoreOption } from 'echarts/core'
import dayjs from 'dayjs'

import type { SkillGrowthVO } from '@/types/growth'
import { chartColor, scoreTone, type ScoreTone } from '@/utils/score'

/**
 * 成长曲线纯函数（F4-4）：把后端 records 转成 time 轴多折线数据。
 * 后端 records 已按 evaluated_at 升序，但前端仍做一次排序/清洗，
 * 以防同日多场或历史数据缺失导致图表错位。
 */
export interface GrowthSeriesPoint {
  timestamp: number
  score: number
  evaluatedAt: string
}

export interface GrowthSeries {
  skillCode: string
  skillName: string
  points: GrowthSeriesPoint[]
  current: number | null
  /** 最新 - 最早（保留 2 位）；只有一个点时即该点分数 */
  delta: number | null
  latestAt: string | null
  tone: ScoreTone
}

export const GROWTH_MIN_POINTS_HINT = '完成第一场面试后，这里会按技能绘制掌握度曲线。'

function clamp(value: number): number {
  return Math.max(0, Math.min(100, value))
}

function normalizePoints(skill: SkillGrowthVO): GrowthSeriesPoint[] {
  const points: GrowthSeriesPoint[] = []
  for (const record of skill.records ?? []) {
    const score = Number(record.score)
    const parsed = dayjs(record.evaluatedAt)
    if (!parsed.isValid() || !Number.isFinite(score)) continue
    points.push({
      timestamp: parsed.valueOf(),
      score: clamp(score),
      evaluatedAt: record.evaluatedAt,
    })
  }
  return points.sort((left, right) => left.timestamp - right.timestamp)
}

/** 统一清洗成长数据；无有效记录时仍保留技能（用于展示"尚无记录"卡片）。 */
export function buildGrowthSeries(skills?: SkillGrowthVO[] | null): GrowthSeries[] {
  return (skills ?? []).map((skill) => {
    const points = normalizePoints(skill)
    const currentValue = Number(skill.current)
    const current =
      points.length > 0
        ? points[points.length - 1].score
        : Number.isFinite(currentValue)
          ? clamp(currentValue)
          : null
    const first = points.length > 0 ? points[0].score : current
    const delta =
      first == null || current == null ? null : Math.round((current - first) * 100) / 100
    return {
      skillCode: skill.skillCode,
      skillName: skill.skillName || skill.skillCode,
      points,
      current,
      delta,
      latestAt: points.length > 0 ? points[points.length - 1].evaluatedAt : null,
      tone: scoreTone(current),
    }
  })
}

export function hasGrowthData(series: GrowthSeries[]): boolean {
  return series.some((item) => item.points.length > 0)
}

export function growthPointCount(series: GrowthSeries[]): number {
  return series.reduce((sum, item) => sum + item.points.length, 0)
}

function formatDelta(delta: number | null): string {
  if (delta == null) return '—'
  if (delta === 0) return '持平'
  return `${delta > 0 ? '+' : ''}${delta.toFixed(2)}`
}

export function growthDeltaText(series: GrowthSeries): string {
  return formatDelta(series.delta)
}

/**
 * ECharts 多折线（计划 §8.4）：x=time、y=0~100、每技能一条、legend 可滚动、
 * 空点不连（connectNulls:false）。颜色复用全局 7 色板。
 */
export function buildGrowthOption(series: GrowthSeries[]): EChartsCoreOption {
  return {
    color: series.map((_, index) => chartColor(index)),
    animationDuration: 300,
    grid: { left: 8, right: 20, top: 48, bottom: 8, containLabel: true },
    legend: {
      type: 'scroll',
      top: 0,
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { color: '#475569', fontSize: 12 },
      pageTextStyle: { color: '#64748b', fontSize: 11 },
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#ffffff',
      borderColor: '#e2e8f0',
      borderWidth: 1,
      textStyle: { color: '#0f172a', fontSize: 12 },
      extraCssText: 'box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);',
      valueFormatter: (value: unknown) =>
        typeof value === 'number' ? `${value.toFixed(1)} 分` : String(value ?? '—'),
    },
    xAxis: {
      type: 'time',
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisTick: { show: false },
      axisLabel: {
        color: '#64748b',
        fontSize: 12,
        hideOverlap: true,
        formatter: (value: unknown) =>
          typeof value === 'number' ? dayjs(value).format('MM-DD') : String(value),
      },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      interval: 20,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#64748b', fontSize: 12 },
      splitLine: { lineStyle: { color: '#e2e8f0' } },
    },
    series: series
      .filter((item) => item.points.length > 0)
      .map((item) => ({
        name: item.skillName,
        type: 'line',
        smooth: false,
        showSymbol: true,
        symbol: 'circle',
        symbolSize: 6,
        connectNulls: false,
        lineStyle: { width: 2 },
        itemStyle: { borderColor: '#ffffff', borderWidth: 1 },
        emphasis: { focus: 'series' },
        data: item.points.map((point) => [point.timestamp, point.score]),
      })),
  }
}

export interface InterviewSuggestion {
  /** 从"建议下一场：… · 难度 2 · …"解析出的难度；解析失败为 null */
  difficulty: number | null
  /** 原文，便于页面展示 */
  text: string
}

/**
 * `nextInterviewSuggested` 是自然语言（如"建议下一场：Java 后端开发工程师 · 难度 2 · 重点覆盖…"）。
 * 只解析难度（1~5），解析失败时页面只带岗位进入设置页（计划 §5.11）。
 */
export function parseInterviewSuggestion(text?: string | null): InterviewSuggestion {
  const raw = text?.trim() ?? ''
  const match = /难度\s*[:：]?\s*([1-5])/.exec(raw)
  const difficulty = match ? Number(match[1]) : null
  return {
    difficulty: difficulty != null && difficulty >= 1 && difficulty <= 5 ? difficulty : null,
    text: raw,
  }
}
