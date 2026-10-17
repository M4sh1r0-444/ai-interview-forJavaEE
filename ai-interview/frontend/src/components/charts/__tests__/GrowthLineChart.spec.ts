import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import GrowthLineChart from '@/components/charts/GrowthLineChart.vue'
import type { GrowthSeries } from '@/utils/growth'

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

function series(): GrowthSeries[] {
  return [
    {
      skillCode: 'spring',
      skillName: 'Spring生态',
      points: [
        {
          timestamp: new Date('2026-10-15T08:00:00').getTime(),
          score: 40.59,
          evaluatedAt: '2026-10-15T08:00:00',
        },
        {
          timestamp: new Date('2026-10-16T09:00:00').getTime(),
          score: 52.12,
          evaluatedAt: '2026-10-16T09:00:00',
        },
      ],
      current: 52.12,
      delta: 11.53,
      latestAt: '2026-10-16T09:00:00',
      tone: 'danger',
    },
    {
      skillCode: 'mysql',
      skillName: 'MySQL数据库',
      points: [],
      current: 60,
      delta: 0,
      latestAt: null,
      tone: 'warning',
    },
  ]
}

describe('GrowthLineChart', () => {
  it('有数据时以 time 轴渲染每技能一条折线', () => {
    mount(GrowthLineChart, { props: { series: series() } })
    expect(mocks.setOption).toHaveBeenCalled()
    const option = mocks.setOption.mock.calls.at(-1)?.[0] as {
      xAxis: { type: string }
      series: { name: string; data: unknown[] }[]
    }
    expect(option.xAxis.type).toBe('time')
    expect(option.series).toHaveLength(1)
    expect(option.series[0].name).toBe('Spring生态')
    expect(option.series[0].data).toHaveLength(2)
  })

  it('卸载时 dispose ECharts 实例', () => {
    const wrapper = mount(GrowthLineChart, { props: { series: series() } })
    wrapper.unmount()
    expect(mocks.chart.dispose).toHaveBeenCalled()
  })

  it('无记录时展示空态且不初始化图表', () => {
    mocks.setOption.mockClear()
    const wrapper = mount(GrowthLineChart, {
      props: { series: [{ ...series()[0], points: [] }] },
    })
    expect(mocks.setOption).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('还没有成长记录')
  })
})
