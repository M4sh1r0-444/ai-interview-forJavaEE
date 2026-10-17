import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import TimerBadge from '@/components/interview/TimerBadge.vue'

describe('TimerBadge', () => {
  it('展示 mm:ss，未超过阈值时不告警', () => {
    const wrapper = mount(TimerBadge, { props: { remainSeconds: 125 } })
    expect(wrapper.text()).toContain('02:05')
    expect(wrapper.classes()).not.toContain('timer-badge--warning')
    expect(wrapper.text()).not.toContain('即将结束')
  })

  it('≤60 秒进入警示态', () => {
    const wrapper = mount(TimerBadge, { props: { remainSeconds: 60 } })
    expect(wrapper.text()).toContain('01:00')
    expect(wrapper.classes()).toContain('timer-badge--warning')
    expect(wrapper.text()).toContain('即将结束')
  })

  it('归零显示时间到', () => {
    const wrapper = mount(TimerBadge, { props: { remainSeconds: 0 } })
    expect(wrapper.text()).toContain('00:00')
    expect(wrapper.classes()).toContain('timer-badge--expired')
    expect(wrapper.text()).toContain('时间到')
  })

  it('未开始（null）显示占位', () => {
    const wrapper = mount(TimerBadge, { props: { remainSeconds: null } })
    expect(wrapper.text()).toContain('--:--')
    expect(wrapper.classes()).not.toContain('timer-badge--warning')
  })
})
