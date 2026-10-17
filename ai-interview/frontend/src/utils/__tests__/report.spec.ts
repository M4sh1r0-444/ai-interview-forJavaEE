import { describe, expect, it } from 'vitest'

import {
  buildRadarOption,
  dimensionEntries,
  evidenceTitle,
  formatDimensionScore,
  formatPriority,
  formatReportDuration,
  reportDurationSeconds,
  voiceDimensionNote,
} from '@/utils/report'

const TEXT_SCORES = {
  technical: 40,
  depth: 48,
  logic: 74,
  match: 55,
  communication: 88,
}

describe('utils/report', () => {
  it('TEXT 场按固定顺序输出 5 维并排除 voice', () => {
    const entries = dimensionEntries({ ...TEXT_SCORES, voice: 60 }, 'TEXT')
    expect(entries.map((entry) => entry.key)).toEqual([
      'technical',
      'depth',
      'logic',
      'match',
      'communication',
    ])
    expect(entries.map((entry) => entry.value)).toEqual([40, 48, 74, 55, 88])
    expect(entries[0].label).toBe('技术正确性')
  })

  it('AUDIO 场包含 voice，且 clamp 到 0~100、跳过缺失维度', () => {
    const entries = dimensionEntries(
      { technical: 120, voice: -5, logic: 60, depth: undefined },
      'AUDIO',
    )
    expect(entries.map((entry) => entry.key)).toEqual(['technical', 'logic', 'voice'])
    expect(entries.map((entry) => entry.value)).toEqual([100, 60, 0])
  })

  it('维度说明区分 TEXT 与缺失 voice 的 AUDIO', () => {
    expect(voiceDimensionNote('TEXT', TEXT_SCORES)).toContain('不采集语音维度')
    expect(voiceDimensionNote('AUDIO', TEXT_SCORES)).toContain('未采集到语音指标')
    expect(voiceDimensionNote('AUDIO', { ...TEXT_SCORES, voice: 60 })).toBe('')
  })

  it('维度分保留 1 位小数，空值显示 —', () => {
    expect(formatDimensionScore(64.456)).toBe('64.5')
    expect(formatDimensionScore(undefined)).toBe('—')
    expect(formatDimensionScore(Number.NaN)).toBe('—')
  })

  it('用时取 finishedAt - startedAt，并兼容缺失/负值', () => {
    expect(
      reportDurationSeconds({
        startedAt: '2026-10-17T10:00:00',
        finishedAt: '2026-10-17T10:02:05',
      }),
    ).toBe(125)
    expect(reportDurationSeconds({ startedAt: '2026-10-17T10:00:00' })).toBeNull()
    expect(
      reportDurationSeconds({
        startedAt: '2026-10-17T10:05:00',
        finishedAt: '2026-10-17T10:00:00',
      }),
    ).toBeNull()
    expect(reportDurationSeconds(null)).toBeNull()
  })

  it('用时可读文案覆盖秒/分钟/空值', () => {
    expect(formatReportDuration(0)).toBe('0 秒')
    expect(formatReportDuration(59)).toBe('59 秒')
    expect(formatReportDuration(60)).toBe('1 分钟')
    expect(formatReportDuration(125)).toBe('2 分 5 秒')
    expect(formatReportDuration(null)).toBe('—')
  })

  it('逐题标题与推荐优先级格式化', () => {
    expect(evidenceTitle(0)).toBe('第 1 题')
    expect(evidenceTitle(2, 5)).toBe('第 3 题 · 共 5 题')
    expect(formatPriority(15.04)).toBe('15.0')
    expect(formatPriority(null)).toBe('—')
  })

  it('雷达 option 使用 5 个指示器、max=100 与面积系列', () => {
    const entries = dimensionEntries(TEXT_SCORES, 'TEXT')
    const option = buildRadarOption(entries) as {
      radar: { indicator: { name: string; max: number }[]; splitNumber: number }
      series: { type: string; data: { value: number[] }[] }[]
      animationDuration: number
    }

    expect(option.radar.indicator).toHaveLength(5)
    expect(option.radar.indicator[0]).toEqual({ name: '技术正确性', max: 100 })
    expect(option.radar.splitNumber).toBe(4)
    expect(option.series[0].type).toBe('radar')
    expect(option.series[0].data[0].value).toEqual([40, 48, 74, 55, 88])
    expect(option.animationDuration).toBe(300)
  })
})
