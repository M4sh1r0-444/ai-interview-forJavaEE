import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import StatusTag from '@/components/interview/StatusTag.vue'

describe('StatusTag', () => {
  it('等待作答映射主色标签', () => {
    const wrapper = mount(StatusTag, { props: { status: 'WAITING_USER' } })
    expect(wrapper.text()).toContain('等待作答')
    expect(wrapper.find('.el-tag').classes()).toContain('el-tag--primary')
  })

  it('完成/中止映射成功/危险标签', () => {
    const finished = mount(StatusTag, { props: { status: 'FINISHED' } })
    expect(finished.text()).toContain('已完成')
    expect(finished.find('.el-tag').classes()).toContain('el-tag--success')

    const aborted = mount(StatusTag, { props: { status: 'ABORTED' } })
    expect(aborted.text()).toContain('已中止')
    expect(aborted.find('.el-tag').classes()).toContain('el-tag--danger')
  })
})
