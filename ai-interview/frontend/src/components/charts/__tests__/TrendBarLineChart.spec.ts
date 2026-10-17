import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import TrendBarLineChart from '@/components/charts/TrendBarLineChart.vue'
import type { DashboardTrendPoint } from '@/types/admin'

const mocks = vi.hoisted(() => {
  const setOption = vi.fn()
  return {
    setOption,
    chart: { setOption, resize: vi.fn(), dispose: vi.fn() },
  }
})

vi.mock('echarts/core', () => ({
  init: () => mocks.chart,
}))

vi.mock('@/utils/echarts', () => ({
  ensureEcharts: vi.fn(),
}))

function points(): DashboardTrendPoint[] {
  return [
    { date: '2026-10-12', sessionCount: 0, avgScore: null },
    { date: '2026-10-13', sessionCount: 3, avgScore: 72.5 },
    { date: '2026-10-14', sessionCount: 4, avgScore: null },
  ]
}

describe('TrendBarLineChart', () => {
  it('有数据时渲染柱线组合，null 均分断点', () => {
    mount(TrendBarLineChart, { props: { points: points() } })
    expect(mocks.setOption).toHaveBeenCalled()
    const option = mocks.setOption.mock.calls.at(-1)?.[0] as {
      series: Array<{ type: string; data: unknown[]; connectNulls?: boolean }>
      yAxis: unknown[]
    }
    expect(option.yAxis).toHaveLength(2)
    expect(option.series[0].type).toBe('bar')
    expect(option.series[0].data).toEqual([0, 3, 4])
    expect(option.series[1].type).toBe('line')
    expect(option.series[1].data).toEqual([null, 72.5, null])
    expect(option.series[1].connectNulls).toBe(false)
  })

  it('卸载时 dispose 实例', () => {
    const wrapper = mount(TrendBarLineChart, { props: { points: points() } })
    wrapper.unmount()
    expect(mocks.chart.dispose).toHaveBeenCalled()
  })

  it('无数据时显示空态且不初始化图表', () => {
    mocks.setOption.mockClear()
    const wrapper = mount(TrendBarLineChart, { props: { points: [] } })
    expect(mocks.setOption).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('近 7 日暂无趋势数据')
  })
})
