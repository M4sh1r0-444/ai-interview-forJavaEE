import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AnswerComposer from '@/components/interview/AnswerComposer.vue'

function mountComposer(props: Record<string, unknown> = {}) {
  return mount(AnswerComposer, {
    props: { modelValue: '我的回答', ...props },
    attachTo: document.body,
  })
}

describe('AnswerComposer', () => {
  it('Ctrl/Cmd + Enter 提交，普通 Enter 不提交', async () => {
    const wrapper = mountComposer()
    const textarea = wrapper.get('textarea')
    await textarea.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('submit')).toBeUndefined()

    await textarea.trigger('keydown', { key: 'Enter', ctrlKey: true })
    expect(wrapper.emitted('submit')).toHaveLength(1)

    await textarea.trigger('keydown', { key: 'Enter', metaKey: true })
    expect(wrapper.emitted('submit')).toHaveLength(2)
    wrapper.unmount()
  })

  it('输入法组合期间不触发快捷键提交', async () => {
    const wrapper = mountComposer()
    const textarea = wrapper.get('textarea')
    await textarea.trigger('compositionstart')
    await textarea.trigger('keydown', { key: 'Enter', ctrlKey: true })
    expect(wrapper.emitted('submit')).toBeUndefined()

    await textarea.trigger('compositionend')
    await textarea.trigger('keydown', { key: 'Enter', ctrlKey: true })
    expect(wrapper.emitted('submit')).toHaveLength(1)
    wrapper.unmount()
  })

  it('内容为空或禁用时提交按钮不可用', () => {
    const empty = mountComposer({ modelValue: '' })
    expect(empty.get('[data-testid="answer-submit"]').attributes('disabled')).toBeDefined()
    empty.unmount()

    const disabled = mountComposer({ disabled: true, disabledHint: 'AI 正在分析上一条回答…' })
    expect(disabled.text()).toContain('AI 正在分析上一条回答…')
    expect(disabled.get('[data-testid="answer-submit"]').attributes('disabled')).toBeDefined()
    disabled.unmount()
  })

  it('点击按钮在可提交时派发 submit', async () => {
    const wrapper = mountComposer()
    await wrapper.get('[data-testid="answer-submit"]').trigger('click')
    expect(wrapper.emitted('submit')).toHaveLength(1)
    wrapper.unmount()
  })

  it('字数与上限展示', () => {
    const wrapper = mountComposer({ modelValue: '1234' })
    expect(wrapper.text()).toContain('4 / 5000')
    wrapper.unmount()
  })
})
