import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import WsStatusBadge from '@/components/interview/WsStatusBadge.vue'

describe('WsStatusBadge', () => {
  it('连接成功展示实时连接且不显示重连按钮', () => {
    const wrapper = mount(WsStatusBadge, { props: { state: 'open', attempt: 0 } })
    expect(wrapper.text()).toContain('实时连接')
    expect(wrapper.find('[data-testid="ws-reconnect"]').exists()).toBe(false)
  })

  it('重连中展示次数与重连按钮，点击派发 reconnect', async () => {
    const wrapper = mount(WsStatusBadge, {
      props: { state: 'reconnecting', attempt: 2 },
    })
    expect(wrapper.text()).toContain('重连中（第 2 次）')
    const button = wrapper.get('[data-testid="ws-reconnect"]')
    await button.trigger('click')
    expect(wrapper.emitted('reconnect')).toHaveLength(1)
  })

  it('断开后提示切换轮询并提供重连', () => {
    const wrapper = mount(WsStatusBadge, { props: { state: 'closed', attempt: 0 } })
    expect(wrapper.text()).toContain('实时连接断开')
    expect(wrapper.find('[data-testid="ws-reconnect"]').exists()).toBe(true)
  })

  it('初始态展示待建立', () => {
    const wrapper = mount(WsStatusBadge, { props: { state: 'idle', attempt: 0 } })
    expect(wrapper.text()).toContain('待建立')
  })
})
