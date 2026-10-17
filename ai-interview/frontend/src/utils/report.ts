import type { EChartsCoreOption } from 'echarts/core'
import dayjs from 'dayjs'

import type { InterviewDetailVO, InterviewMode } from '@/types/interview'
import type { DimensionKey } from '@/types/report'
import { DIMENSION_ORDER, dimensionLabel } from '@/utils/score'

/**
 * 报告页纯函数（F3）：维度归一、用时换算、色带与雷达图配置。
 * 单独抽出便于单测，也避免页面组件里堆叠格式化逻辑。
 */

export interface DimensionEntry {
  key: DimensionKey
  label: string
  /** 已 clamp 到 0~100 的维度得分 */
  value: number
}

/** TEXT 场固定 5 维；AUDIO 场 6 维（含 voice）。 */
export function dimensionsForMode(mode?: InterviewMode | null): DimensionKey[] {
  if (mode === 'AUDIO') return [...DIMENSION_ORDER]
  return DIMENSION_ORDER.filter((key) => key !== 'voice')
}

export function clampScore(value: number): number {
  if (!Number.isFinite(value)) return 0
  return Math.max(0, Math.min(100, value))
}

/**
 * 按固定顺序输出"实际存在"的维度；缺失维度不补 0（计划 §5.8 雷达规范）。
 * voice 在 TEXT 场直接排除；AUDIO 场缺失时由 voiceDimensionNote 说明。
 */
export function dimensionEntries(
  scores: Partial<Record<DimensionKey, number>> | null | undefined,
  mode?: InterviewMode | null,
): DimensionEntry[] {
  const entries: DimensionEntry[] = []
  for (const key of dimensionsForMode(mode)) {
    const value = scores?.[key]
    if (typeof value !== 'number' || !Number.isFinite(value)) continue
    entries.push({ key, label: dimensionLabel(key), value: clampScore(value) })
  }
  return entries
}

export function formatDimensionScore(value?: number | null): string {
  if (value == null || !Number.isFinite(value)) return '—'
  return value.toFixed(1)
}

/** 维度说明：TEXT 场不采集 voice；AUDIO 场缺 voice 时明确提示，而不是画成 0 分。 */
export function voiceDimensionNote(
  mode?: InterviewMode | null,
  scores?: Partial<Record<DimensionKey, number>> | null,
): string {
  if (mode === 'TEXT') return '文本面试不采集语音维度（voice），雷达图按 5 维展示。'
  if (mode === 'AUDIO' && (scores?.voice == null || !Number.isFinite(scores.voice))) {
    return '本场未采集到语音指标（voice），维度图按实际数据展示。'
  }
  return ''
}

/** 面试用时（秒）：以服务端 startedAt / finishedAt 为准，缺失或负值返回 null。 */
export function reportDurationSeconds(
  detail?: Partial<Pick<InterviewDetailVO, 'startedAt' | 'finishedAt'>> | null,
): number | null {
  if (!detail?.startedAt || !detail?.finishedAt) return null
  const start = dayjs(detail.startedAt)
  const end = dayjs(detail.finishedAt)
  if (!start.isValid() || !end.isValid()) return null
  const seconds = end.diff(start, 'second')
  return seconds >= 0 ? seconds : null
}

/** 报告头部用时可读文案（与 mm:ss 倒计时区分，避免 02:05 被误读为剩余时间）。 */
export function formatReportDuration(seconds?: number | null): string {
  if (seconds == null || !Number.isFinite(seconds) || seconds < 0) return '—'
  if (seconds < 60) return `${Math.round(seconds)} 秒`
  const minutes = Math.floor(seconds / 60)
  const rest = Math.round(seconds % 60)
  return rest === 0 ? `${minutes} 分钟` : `${minutes} 分 ${rest} 秒`
}

export function evidenceTitle(roundNo: number, total?: number): string {
  const base = `第 ${roundNo + 1} 题`
  if (total == null || total <= 0) return base
  return `${base} · 共 ${total} 题`
}

/**
 * ECharts 雷达 option（计划 §8.4）：max=100、splitNumber=4、面积 12% 透明、
 * 轴标签中文；数据少于 3 个维度时组件不渲染图表。
 */
export function buildRadarOption(entries: DimensionEntry[]): EChartsCoreOption {
  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: '#ffffff',
      borderColor: '#e2e8f0',
      borderWidth: 1,
      textStyle: { color: '#0f172a', fontSize: 12 },
      extraCssText: 'box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);',
    },
    radar: {
      indicator: entries.map((entry) => ({ name: entry.label, max: 100 })),
      radius: '66%',
      center: ['50%', '52%'],
      splitNumber: 4,
      axisName: { color: '#475569', fontSize: 12 },
      splitLine: { lineStyle: { color: '#e2e8f0' } },
      splitArea: {
        areaStyle: {
          color: ['rgba(255, 255, 255, 0.34)', 'rgba(239, 246, 255, 0.22)'],
        },
      },
      axisLine: { lineStyle: { color: '#e2e8f0' } },
    },
    series: [
      {
        type: 'radar',
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: '#2563eb', width: 2 },
        itemStyle: { color: '#2563eb', borderColor: '#ffffff', borderWidth: 1 },
        areaStyle: { color: 'rgba(37, 99, 235, 0.18)' },
        data: [{ value: entries.map((entry) => entry.value), name: '维度得分' }],
      },
    ],
    animationDuration: 300,
  }
}

/** 推荐优先级展示（保留 1 位，缺失显示 —）。 */
export function formatPriority(value?: number | null): string {
  if (value == null || !Number.isFinite(value)) return '—'
  return value.toFixed(1)
}
