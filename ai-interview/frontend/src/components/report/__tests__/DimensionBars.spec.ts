import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import DimensionBars from '@/components/report/DimensionBars.vue'

const TEXT_SCORES = {
  technical: 40,
  depth: 48,
  logic: 74,
  match: 55,
  communication: 88,
}

describe('DimensionBars', () => {
  it('TEXT 场渲染 5 行并提示不采集 voice', () => {
    const wrapper = mount(DimensionBars, {
      props: { scores: { ...TEXT_SCORES, voice: 60 }, mode: 'TEXT' },
    })
    expect(wrapper.findAll('.dimension-row')).toHaveLength(5)
    expect(wrapper.find('[data-testid="dimension-voice"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('文本面试不采集语音维度')
    expect(wrapper.get('[data-testid="dimension-technical"]').text()).toContain('40.0')
  })

  it('AUDIO 场渲染 6 行（含 voice）', () => {
    const wrapper = mount(DimensionBars, {
      props: { scores: { ...TEXT_SCORES, voice: 60 }, mode: 'AUDIO' },
    })
    expect(wrapper.findAll('.dimension-row')).toHaveLength(6)
    expect(wrapper.get('[data-testid="dimension-voice"]').text()).toContain('60.0')
  })

  it('空数据展示空态', () => {
    const wrapper = mount(DimensionBars, { props: { scores: null, mode: 'TEXT' } })
    expect(wrapper.findAll('.dimension-row')).toHaveLength(0)
    expect(wrapper.text()).toContain('暂无维度得分')
  })

  it('超界分数宽度按 100% 封顶', () => {
    const wrapper = mount(DimensionBars, {
      props: { scores: { technical: 140 }, mode: 'TEXT' },
    })
    const fill = wrapper.get('[data-testid="dimension-technical"] .dimension-row__fill')
    expect(fill.attributes('style')).toContain('width: 100%')
  })
})
