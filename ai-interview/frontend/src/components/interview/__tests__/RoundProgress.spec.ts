import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import RoundProgress from '@/components/interview/RoundProgress.vue'

describe('RoundProgress', () => {
  it('展示当前轮次、总轮次与追问计数', () => {
    const wrapper = mount(RoundProgress, {
      props: { current: 2, planned: 6, followup: 1 },
    })
    expect(wrapper.get('[data-testid="round-current"]').text()).toBe('2')
    expect(wrapper.get('[data-testid="followup-count"]').text()).toContain('1/3')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('2')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuemax')).toBe('6')
  })

  it('追问计数上限为 3', () => {
    const wrapper = mount(RoundProgress, {
      props: { current: 3, planned: 5, followup: 9 },
    })
    expect(wrapper.get('[data-testid="followup-count"]').text()).toContain('3/3')
  })

  it('终态展示已完成且隐藏追问计数', () => {
    const wrapper = mount(RoundProgress, {
      props: { current: 5, planned: 5, followup: 2, finished: true },
    })
    expect(wrapper.text()).toContain('本场面试已完成')
    expect(wrapper.text()).toContain('已结束')
    expect(wrapper.find('[data-testid="followup-count"]').exists()).toBe(false)
  })
})
