import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import TranscriptPanel from '@/components/interview/TranscriptPanel.vue'

describe('TranscriptPanel', () => {
  it('展示 final、interim、字数与识别中状态', () => {
    const wrapper = mount(TranscriptPanel, {
      props: {
        modelValue: '我在项目里负责缓存设计',
        listening: true,
        interimText: '并做了',
      },
    })
    expect(wrapper.get('textarea').element.value).toBe('我在项目里负责缓存设计')
    expect(wrapper.text()).toContain('识别中：并做了')
    expect(wrapper.text()).toContain('11 / 5000')
    expect(wrapper.text()).toContain('识别中')
  })

  it('手工编辑触发 update:modelValue', async () => {
    const wrapper = mount(TranscriptPanel, { props: { modelValue: '' } })
    await wrapper.get('textarea').setValue('手工填写的转写')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['手工填写的转写'])
  })

  it('ASR 错误与不支持自动转写时给出手工转写提示', () => {
    const wrapper = mount(TranscriptPanel, {
      props: {
        modelValue: '',
        supported: false,
        speechError: '语音识别需要网络；当前离线时可手工填写转写后提交',
      },
    })
    expect(wrapper.text()).toContain('本浏览器不支持自动转写')
    expect(wrapper.text()).toContain('手工转写')
    expect(wrapper.get('[data-testid="transcript-error"]').text()).toContain('需要网络')
  })

  it('空态提示可重录或手工填写', () => {
    const wrapper = mount(TranscriptPanel, { props: { modelValue: '' } })
    expect(wrapper.text()).toContain('尚未识别到内容')
  })
})
