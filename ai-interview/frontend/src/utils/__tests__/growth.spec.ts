import { describe, expect, it } from 'vitest'

import type { SkillGrowthVO } from '@/types/growth'
import {
  buildGrowthOption,
  buildGrowthSeries,
  growthDeltaText,
  growthPointCount,
  hasGrowthData,
  parseInterviewSuggestion,
} from '@/utils/growth'

function skill(overrides: Partial<SkillGrowthVO> = {}): SkillGrowthVO {
  return {
    skillCode: 'spring',
    skillName: 'Spring生态',
    records: [
      { evaluatedAt: '2026-10-15T08:00:00', score: 40.59 },
      { evaluatedAt: '2026-10-16T09:30:00', score: 52.12 },
    ],
    current: 52.12,
    ...overrides,
  }
}

describe('utils/growth', () => {
  it('按时间升序清洗 records，计算 current/delta/tone', () => {
    const [series] = buildGrowthSeries([
      skill({
        records: [
          { evaluatedAt: '2026-10-16T09:30:00', score: 52.12 },
          { evaluatedAt: '2026-10-15T08:00:00', score: 40.59 },
          { evaluatedAt: 'bad-date', score: 99 },
        ],
      }),
    ])
    expect(series.points.map((point) => point.score)).toEqual([40.59, 52.12])
    expect(series.current).toBe(52.12)
    expect(series.delta).toBe(11.53)
    expect(series.tone).toBe('danger')
    expect(series.latestAt).toBe('2026-10-16T09:30:00')
    expect(growthDeltaText(series)).toBe('+11.53')
  })

  it('无记录时保留技能并回退 current；current 缺失为 null', () => {
    const [withCurrent, withoutCurrent] = buildGrowthSeries([
      skill({ records: [], current: 66.6 }),
      skill({ skillCode: 'redis', records: [], current: undefined as unknown as number }),
    ])
    expect(withCurrent.points).toEqual([])
    expect(withCurrent.current).toBe(66.6)
    expect(withCurrent.delta).toBe(0)
    expect(growthDeltaText(withCurrent)).toBe('持平')
    expect(withoutCurrent.current).toBeNull()
    expect(growthDeltaText(withoutCurrent)).toBe('—')
    expect(hasGrowthData([withCurrent, withoutCurrent])).toBe(false)
  })

  it('统计记录点并识别有数据的曲线', () => {
    const series = buildGrowthSeries([skill(), skill({ skillCode: 'mysql', records: [] })])
    expect(growthPointCount(series)).toBe(2)
    expect(hasGrowthData(series)).toBe(true)
  })

  it('ECharts option：time 轴、y 0~100、每技能一条且过滤空曲线', () => {
    const series = buildGrowthSeries([
      skill(),
      skill({ skillCode: 'mysql', records: [], current: 60 }),
      skill({
        skillCode: 'redis',
        skillName: 'Redis与消息队列',
        records: [{ evaluatedAt: '2026-10-16T10:00:00', score: 70 }],
        current: 70,
      }),
    ])
    const option = buildGrowthOption(series) as {
      xAxis: { type: string }
      yAxis: { min: number; max: number }
      series: { name: string; type: string; data: [number, number][]; connectNulls: boolean }[]
    }
    expect(option.xAxis.type).toBe('time')
    expect(option.yAxis).toMatchObject({ min: 0, max: 100 })
    expect(option.series).toHaveLength(2)
    expect(option.series.map((item) => item.name)).toEqual(['Spring生态', 'Redis与消息队列'])
    expect(option.series[0].type).toBe('line')
    expect(option.series[0].connectNulls).toBe(false)
    expect(option.series[0].data).toHaveLength(2)
    expect(typeof option.series[0].data[0][0]).toBe('number')
  })

  it('解析下场建议难度：命中 1~5，缺失/非法返回 null', () => {
    expect(
      parseInterviewSuggestion('建议下一场：Java 后端开发工程师 · 难度 2 · 重点覆盖 MySQL')
        .difficulty,
    ).toBe(2)
    expect(parseInterviewSuggestion('难度：5').difficulty).toBe(5)
    expect(parseInterviewSuggestion('建议下一场：Java · 重点覆盖').difficulty).toBeNull()
    expect(parseInterviewSuggestion('难度 9').difficulty).toBeNull()
    expect(parseInterviewSuggestion(null).difficulty).toBeNull()
    expect(parseInterviewSuggestion(undefined).text).toBe('')
  })
})
