import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import MessageBubble from '@/components/interview/MessageBubble.vue'
import type { MessageVO } from '@/types/interview'

function message(overrides: Partial<MessageVO> = {}): MessageVO {
  return {
    id: 1,
    sessionId: 1,
    roundNo: 0,
    speaker: 'AI',
    contentText: '请介绍 Java 的面向对象特性',
    createdAt: '2026-10-17T20:01:02',
    followupIndex: 0,
    ...overrides,
  }
}

describe('MessageBubble', () => {
  it('AI 主问题展示标签、轮次与时间', () => {
    const wrapper = mount(MessageBubble, { props: { message: message() } })
    expect(wrapper.classes()).toContain('bubble--ai')
    expect(wrapper.text()).toContain('主问题')
    expect(wrapper.text()).toContain('第 1 题')
    expect(wrapper.text()).toContain('20:01:02')
    expect(wrapper.text()).toContain('请介绍 Java 的面向对象特性')
  })

  it('追问按 followupIndex 展示序号', () => {
    const wrapper = mount(MessageBubble, {
      props: { message: message({ followupIndex: 2, contentText: '再具体一些' }) },
    })
    expect(wrapper.text()).toContain('追问 2')
    expect(wrapper.text()).toContain('再具体一些')
  })

  it('USER 气泡右对齐，失败态展示保留提示', () => {
    const wrapper = mount(MessageBubble, {
      props: {
        message: message({ speaker: 'USER', followupIndex: null, contentText: '我的回答' }),
        optimisticState: 'failed',
      },
    })
    expect(wrapper.classes()).toContain('bubble--user')
    expect(wrapper.classes()).toContain('bubble--failed')
    expect(wrapper.text()).toContain('发送失败，内容已保留')
  })

  it('AUDIO 消息展示回放按钮并可派发 replay', async () => {
    const wrapper = mount(MessageBubble, {
      props: {
        message: message({
          speaker: 'USER',
          inputType: 'AUDIO',
          contentText: '语音回答',
          audioPath: 'audio/202610/abc.webm',
          audioDurationMs: 30_000,
        }),
        replayable: true,
      },
    })
    expect(wrapper.get('[data-testid="message-replay-1"]').text()).toContain('回放录音')
    await wrapper.get('[data-testid="message-replay-1"]').trigger('click')
    expect(wrapper.emitted('replay')).toHaveLength(1)

    await wrapper.setProps({ playing: true })
    expect(wrapper.get('[data-testid="message-replay-1"]').text()).toContain('回放中')
  })

  it('AUDIO 消息展示语音标记、转写与录音时长', () => {
    const wrapper = mount(MessageBubble, {
      props: {
        message: message({
          inputType: 'AUDIO',
          contentText: '转写内容',
          asrText: '原始识别文本',
          audioDurationMs: 65_000,
        }),
      },
    })
    expect(wrapper.text()).toContain('语音')
    expect(wrapper.text()).toContain('转写：原始识别文本')
    expect(wrapper.text()).toContain('录音时长 01:05')
  })
})
