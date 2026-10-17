import { effectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  useSpeechRecognition,
  type SpeechRecognitionCtorLike,
  type SpeechRecognitionErrorEventLike,
  type SpeechRecognitionEventLike,
  type SpeechRecognitionLike,
  type SpeechResultLike,
  type SpeechResultListLike,
} from '@/composables/useSpeechRecognition'

class FakeRecognition implements SpeechRecognitionLike {
  static instances: FakeRecognition[] = []

  lang = ''
  continuous = false
  interimResults = false
  maxAlternatives = 0
  onstart: (() => void) | null = null
  onresult: ((event: SpeechRecognitionEventLike) => void) | null = null
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null = null
  onend: (() => void) | null = null
  onspeechend: (() => void) | null = null

  start = vi.fn((): void => {
    this.onstart?.()
  })

  stop = vi.fn((): void => {
    this.onend?.()
  })

  abort = vi.fn((): void => {
    this.onend?.()
  })

  constructor() {
    FakeRecognition.instances.push(this)
  }

  emitResult(entries: { transcript: string; final: boolean }[], resultIndex = 0): void {
    const list = entries.map((entry) => result(entry.transcript, entry.final))
    this.onresult?.({
      resultIndex,
      results: Object.assign(list, { length: list.length }) as SpeechResultListLike,
    })
  }

  emitError(code: string): void {
    this.onerror?.({ error: code })
  }

  end(): void {
    this.onend?.()
  }
}

function result(transcript: string, isFinal: boolean): SpeechResultLike {
  const alternatives = Object.assign([{ transcript, confidence: 0.9 }], {
    isFinal,
    length: 1,
  })
  return alternatives as unknown as SpeechResultLike
}

function setup(
  ctor: SpeechRecognitionCtorLike | null = FakeRecognition as unknown as SpeechRecognitionCtorLike,
) {
  const scope = effectScope()
  const speech = scope.run(() => useSpeechRecognition({ speechRecognitionCtor: ctor }))!
  return { speech, scope }
}

describe('useSpeechRecognition', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    FakeRecognition.instances = []
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('final 累积、interim 单独展示且支持增量 resultIndex', () => {
    const { speech, scope } = setup()
    expect(speech.supported).toBe(true)
    speech.start()
    expect(speech.listening.value).toBe(true)
    const instance = FakeRecognition.instances[0]
    expect(instance.lang).toBe('zh-CN')
    expect(instance.continuous).toBe(true)
    expect(instance.interimResults).toBe(true)

    instance.emitResult([{ transcript: '我在', final: true }])
    expect(speech.finalText.value).toBe('我在')
    expect(speech.interimText.value).toBe('')

    instance.emitResult([{ transcript: '做', final: false }])
    expect(speech.interimText.value).toBe('做')

    instance.emitResult(
      [
        { transcript: '做', final: false },
        { transcript: '项目', final: false },
      ],
      0,
    )
    expect(speech.interimText.value).toBe('做项目')

    instance.emitResult(
      [
        { transcript: '我在', final: true },
        { transcript: '项目', final: true },
      ],
      1,
    )
    expect(speech.finalText.value).toBe('我在项目')
    scope.stop()
  })

  it('权限拒绝映射为手工转写提示，且不再自动重启', () => {
    const { speech, scope } = setup()
    speech.start()
    const instance = FakeRecognition.instances[0]

    instance.emitError('not-allowed')
    expect(speech.errorCode.value).toBe('permission-denied')
    expect(speech.error.value).toContain('手工填写')

    instance.end()
    vi.advanceTimersByTime(1000)
    expect(instance.start).toHaveBeenCalledTimes(1)
    expect(FakeRecognition.instances).toHaveLength(1)
    expect(speech.listening.value).toBe(false)
    scope.stop()
  })

  it('network 错误给离线手工转写提示且不重试', () => {
    const { speech, scope } = setup()
    speech.start()
    const instance = FakeRecognition.instances[0]
    instance.emitError('network')

    expect(speech.errorCode.value).toBe('network')
    expect(speech.error.value).toContain('需要网络')
    instance.end()
    vi.advanceTimersByTime(1000)
    expect(instance.start).toHaveBeenCalledTimes(1)
    scope.stop()
  })

  it('no-speech 后在 onend 自动重启，最多 3 次', () => {
    const { speech, scope } = setup()
    speech.start()
    const instance = FakeRecognition.instances[0]

    for (let attempt = 0; attempt < 3; attempt += 1) {
      instance.emitError('no-speech')
      instance.end()
      vi.advanceTimersByTime(300)
    }
    expect(speech.restartCount.value).toBe(3)
    expect(instance.start).toHaveBeenCalledTimes(4)

    instance.end()
    vi.advanceTimersByTime(1000)
    expect(instance.start).toHaveBeenCalledTimes(4)
    expect(speech.listening.value).toBe(false)
    scope.stop()
  })

  it('stop/reset 清理状态与定时器，不再重启', () => {
    const { speech, scope } = setup()
    speech.start()
    const instance = FakeRecognition.instances[0]
    instance.emitResult([{ transcript: '你好', final: false }])
    instance.end()

    speech.reset()
    expect(speech.finalText.value).toBe('')
    expect(speech.interimText.value).toBe('')
    expect(speech.error.value).toBe('')
    expect(speech.listening.value).toBe(false)
    expect(instance.abort).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(1000)
    expect(instance.start).toHaveBeenCalledTimes(1)
    scope.stop()
  })

  it('浏览器不支持时 start 给出降级提示', () => {
    const { speech, scope } = setup(null)
    expect(speech.supported).toBe(false)
    speech.start()
    expect(speech.errorCode.value).toBe('unsupported')
    expect(speech.error.value).toContain('手工填写')
    scope.stop()
  })

  it('speechend 次数换算停顿估计', () => {
    const { speech, scope } = setup()
    speech.start()
    const instance = FakeRecognition.instances[0]
    expect(speech.pauseCount.value).toBe(0)
    instance.onspeechend?.()
    expect(speech.pauseCount.value).toBe(0)
    instance.onspeechend?.()
    expect(speech.pauseCount.value).toBe(1)
    scope.stop()
  })

  it('stop 后 onend 不再重启', () => {
    const { speech, scope } = setup()
    speech.start()
    const instance = FakeRecognition.instances[0]
    speech.stop()
    expect(instance.stop).toHaveBeenCalledTimes(1)
    expect(speech.listening.value).toBe(false)
    vi.advanceTimersByTime(1000)
    expect(instance.start).toHaveBeenCalledTimes(1)
    scope.stop()
  })
})
