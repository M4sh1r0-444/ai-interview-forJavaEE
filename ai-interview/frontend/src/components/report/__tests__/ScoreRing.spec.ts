import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ScoreRing from '@/components/report/ScoreRing.vue'

describe('ScoreRing', () => {
  it('展示两位小数总分与等级，并按色带着色', () => {
    const wrapper = mount(ScoreRing, { props: { score: 64.46 } })
    expect(wrapper.get('[data-testid="score-ring-value"]').text()).toBe('64.46')
    expect(wrapper.text()).toContain('总分')
    expect(wrapper.text()).toContain('合格')
    expect(wrapper.classes()).toContain('score-ring--warning')
    expect(wrapper.attributes('aria-label')).toContain('64.46')
  })

  it('高分为绿色成功态', () => {
    const wrapper = mount(ScoreRing, { props: { score: 90 } })
    expect(wrapper.classes()).toContain('score-ring--success')
    expect(wrapper.text()).toContain('优秀')
  })

  it('空分数显示占位且不崩溃', () => {
    const wrapper = mount(ScoreRing, { props: { score: null } })
    expect(wrapper.get('[data-testid="score-ring-value"]').text()).toBe('—')
    expect(wrapper.text()).toContain('暂无评分')
    expect(wrapper.classes()).toContain('score-ring--danger')
  })
})
