import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import RadarScoreChart from '@/components/charts/RadarScoreChart.vue'

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

const TEXT_SCORES = {
  technical: 40,
  depth: 48,
  logic: 74,
  match: 55,
  communication: 88,
}

describe('RadarScoreChart', () => {
  it('TEXT 场生成 5 轴雷达并排除 voice', () => {
    const wrapper = mount(RadarScoreChart, {
      props: { scores: { ...TEXT_SCORES, voice: 60 }, mode: 'TEXT' },
    })

    expect(mocks.setOption).toHaveBeenCalled()
    const option = mocks.setOption.mock.calls.at(-1)?.[0] as {
      radar: { indicator: { name: string }[] }
      series: { data: { value: number[] }[] }[]
    }
    expect(option.radar.indicator).toHaveLength(5)
    expect(option.radar.indicator.map((item) => item.name)).not.toContain('语音表现')
    expect(option.series[0].data[0].value).toEqual([40, 48, 74, 55, 88])
    expect(wrapper.text()).toContain('不采集语音维度')
  })

  it('AUDIO 场生成 6 轴雷达', () => {
    mocks.setOption.mockClear()
    mount(RadarScoreChart, {
      props: { scores: { ...TEXT_SCORES, voice: 60 }, mode: 'AUDIO' },
    })

    const option = mocks.setOption.mock.calls.at(-1)?.[0] as {
      radar: { indicator: { name: string }[] }
    }
    expect(option.radar.indicator).toHaveLength(6)
    expect(option.radar.indicator.map((item) => item.name)).toContain('语音表现')
  })

  it('维度不足 3 个时不初始化图表并展示空态', () => {
    mocks.setOption.mockClear()
    const wrapper = mount(RadarScoreChart, {
      props: { scores: { technical: 40 }, mode: 'TEXT' },
    })
    expect(mocks.setOption).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('暂无可展示的维度')
  })
})
