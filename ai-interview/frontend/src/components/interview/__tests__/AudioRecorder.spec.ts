import { nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import AudioRecorder from '@/components/interview/AudioRecorder.vue'

class TestRecorder {
  static instances: TestRecorder[] = []
  state: 'inactive' | 'recording' = 'inactive'
  ondataavailable: ((event: { data: Blob }) => void) | null = null
  onstop: ((event: Event) => void) | null = null
  onerror: ((event: Event) => void) | null = null

  constructor(public stream: MediaStream) {
    TestRecorder.instances.push(this)
  }

  start = vi.fn((): void => {
    this.state = 'recording'
  })

  stop = vi.fn((): void => {
    this.state = 'inactive'
    this.onstop?.(new Event('stop'))
  })

  emitData(bytes: number): void {
    this.ondataavailable?.({ data: new Blob([new Uint8Array(bytes)]) })
  }
}

function installRecorderApi(getUserMedia = vi.fn(async () => ({ getTracks: () => [] }))) {
  Object.defineProperty(window, 'MediaRecorder', {
    configurable: true,
    writable: true,
    value: Object.assign(TestRecorder, {
      isTypeSupported: () => true,
    }),
  })
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    value: { getUserMedia },
  })
  Object.defineProperty(URL, 'createObjectURL', {
    configurable: true,
    writable: true,
    value: vi.fn(() => 'blob:test-recording'),
  })
  Object.defineProperty(URL, 'revokeObjectURL', {
    configurable: true,
    writable: true,
    value: vi.fn(),
  })
}

describe('AudioRecorder', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    TestRecorder.instances = []
  })

  afterEach(() => {
    vi.useRealTimers()
    installRecorderApi()
    Reflect.deleteProperty(window, 'MediaRecorder')
    Reflect.deleteProperty(navigator, 'mediaDevices')
  })

  it('不支持录音时显示提示且不渲染录音按钮', () => {
    const wrapper = mount(AudioRecorder)
    expect(wrapper.get('[data-testid="audio-unsupported"]').text()).toContain('不支持录音')
    expect(wrapper.find('[data-testid="audio-start"]').exists()).toBe(false)
  })

  it('开始/停止录音：计时、预览、重录与 recorded 事件', async () => {
    installRecorderApi()
    const wrapper = mount(AudioRecorder)
    expect(wrapper.find('[data-testid="audio-start"]').exists()).toBe(true)

    await wrapper.get('[data-testid="audio-start"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="audio-recording"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('录音中')

    vi.advanceTimersByTime(1200)
    await nextTick()
    expect(wrapper.get('[data-testid="audio-timer"]').text()).toContain('00:01')

    TestRecorder.instances[0].emitData(4096)
    await wrapper.get('[data-testid="audio-stop"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="audio-result"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="audio-preview"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('4.0 KB')
    const recorded = wrapper.emitted('recorded')?.[0]?.[0] as { blob: Blob; durationMs: number }
    expect(recorded.blob.size).toBe(4096)
    expect(recorded.durationMs).toBeGreaterThanOrEqual(1000)
    expect(wrapper.emitted('started')).toHaveLength(1)
    expect(wrapper.emitted('stopped')).toHaveLength(1)

    await wrapper.get('[data-testid="audio-rerecord"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="audio-recording"]').exists()).toBe(true)
    expect(TestRecorder.instances).toHaveLength(2)
    wrapper.unmount()
  })

  it('权限被拒绝时展示中文错误与重试入口', async () => {
    const getUserMedia = vi.fn(async () => {
      throw Object.assign(new Error('denied'), { name: 'NotAllowedError' })
    })
    installRecorderApi(getUserMedia)
    const wrapper = mount(AudioRecorder)
    await wrapper.get('[data-testid="audio-start"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="audio-error"]').text()).toContain('麦克风权限被拒绝')
    expect(wrapper.find('[data-testid="audio-start"]').exists()).toBe(true)
    expect(wrapper.emitted('recorded')).toBeUndefined()
    wrapper.unmount()
  })

  it('disabled 时录音按钮不可用', () => {
    installRecorderApi()
    const wrapper = mount(AudioRecorder, { props: { disabled: true } })
    expect(wrapper.get('[data-testid="audio-start"]').attributes('disabled')).toBeDefined()
  })
})
