import { effectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  useAudioRecorder,
  type MediaRecorderCtorLike,
  type MediaRecorderLike,
} from '@/composables/useAudioRecorder'

class FakeMediaRecorder implements MediaRecorderLike {
  static instances: FakeMediaRecorder[] = []
  static isTypeSupported = vi.fn(() => true)

  state: 'inactive' | 'recording' | 'paused' = 'inactive'
  mimeType = 'audio/webm;codecs=opus'
  ondataavailable: ((event: { data: Blob }) => void) | null = null
  onstop: ((event: Event) => void) | null = null
  onerror: ((event: Event) => void) | null = null

  constructor(
    public stream: MediaStream,
    public options?: { mimeType?: string },
  ) {
    FakeMediaRecorder.instances.push(this)
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

function createStream() {
  const stop = vi.fn()
  const stream = { getTracks: () => [{ stop }] } as unknown as MediaStream
  return { stream, stop }
}

function setup(
  overrides: {
    ctor?: MediaRecorderCtorLike | null
    mediaDevices?: Pick<MediaDevices, 'getUserMedia'> | null
    maxDurationMs?: number
    maxBytes?: number
  } = {},
) {
  const { stream, stop } = createStream()
  const getUserMedia = vi.fn(async () => stream)
  const scope = effectScope()
  const recorder = scope.run(() =>
    useAudioRecorder({
      mediaRecorderCtor:
        overrides.ctor === undefined
          ? (FakeMediaRecorder as unknown as MediaRecorderCtorLike)
          : overrides.ctor,
      mediaDevices:
        overrides.mediaDevices === undefined
          ? ({ getUserMedia } as unknown as Pick<MediaDevices, 'getUserMedia'>)
          : overrides.mediaDevices,
      mimeType: 'audio/webm;codecs=opus',
      maxDurationMs: overrides.maxDurationMs,
      maxBytes: overrides.maxBytes,
      intervalMs: 200,
    }),
  )!
  return { recorder, scope, getUserMedia, trackStop: stop, stream }
}

describe('useAudioRecorder', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    FakeMediaRecorder.instances = []
    FakeMediaRecorder.isTypeSupported.mockClear()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('能力不足时 supported=false，start 只报错不请求权限', async () => {
    const { recorder, scope, getUserMedia } = setup({
      ctor: null,
      mediaDevices: null,
    })
    expect(recorder.supported).toBe(false)
    expect(recorder.disabledReason).toContain('不支持录音')

    await recorder.start()
    expect(getUserMedia).not.toHaveBeenCalled()
    expect(recorder.error.value).toContain('不支持录音')
    expect(recorder.recording.value).toBe(false)
    scope.stop()
  })

  it('权限拒绝映射为中文提示，不保留录音', async () => {
    const getUserMedia = vi.fn(async () => {
      throw Object.assign(new Error('denied'), { name: 'NotAllowedError' })
    })
    const { recorder, scope } = setup({
      mediaDevices: { getUserMedia } as unknown as Pick<MediaDevices, 'getUserMedia'>,
    })

    await recorder.start()
    expect(recorder.requesting.value).toBe(false)
    expect(recorder.recording.value).toBe(false)
    expect(recorder.blob.value).toBeNull()
    expect(recorder.error.value).toContain('麦克风权限被拒绝')
    scope.stop()
  })

  it('重复点击开始只请求一次权限', async () => {
    let grant!: (stream: MediaStream) => void
    const getUserMedia = vi.fn(
      () =>
        new Promise<MediaStream>((resolve) => {
          grant = resolve
        }),
    )
    const { recorder, scope, stream } = setup({
      mediaDevices: { getUserMedia } as unknown as Pick<MediaDevices, 'getUserMedia'>,
    })

    const first = recorder.start()
    const second = recorder.start()
    expect(getUserMedia).toHaveBeenCalledTimes(1)

    grant(stream)
    await Promise.all([first, second])
    expect(recorder.recording.value).toBe(true)
    expect(recorder.requesting.value).toBe(false)
    recorder.stop('manual')
    scope.stop()
  })

  it('录制/停止：生成 blob 与真实时长，并释放 MediaStream', async () => {
    vi.setSystemTime(new Date('2026-10-18T09:00:00Z'))
    const { recorder, scope, trackStop } = setup()

    await recorder.start()
    expect(recorder.recording.value).toBe(true)
    expect(recorder.mimeType).toBe('audio/webm;codecs=opus')
    const instance = FakeMediaRecorder.instances[0]
    expect(instance.options?.mimeType).toBe('audio/webm;codecs=opus')
    expect(instance.start).toHaveBeenCalledWith(1000)

    instance.emitData(2048)
    vi.advanceTimersByTime(1500)
    expect(recorder.elapsedMs.value).toBeGreaterThanOrEqual(1000)

    recorder.stop('manual')
    expect(recorder.recording.value).toBe(false)
    expect(recorder.blob.value?.size).toBe(2048)
    expect(recorder.durationMs.value).toBe(1500)
    expect(recorder.stopReason.value).toBe('manual')
    expect(trackStop).toHaveBeenCalledTimes(1)
    scope.stop()
  })

  it('超过体积上限自动停止并丢弃结果', async () => {
    const { recorder, scope } = setup({ maxBytes: 1024 * 1024 })
    await recorder.start()
    const instance = FakeMediaRecorder.instances[0]
    instance.emitData(1024 * 1024 + 1)

    expect(recorder.recording.value).toBe(false)
    expect(recorder.blob.value).toBeNull()
    expect(recorder.stopReason.value).toBe('max-size')
    expect(recorder.error.value).toContain('1MB')
    scope.stop()
  })

  it('达到时长上限自动停止并保留录音（时长 clamp 到上限）', async () => {
    const { recorder, scope } = setup({ maxDurationMs: 1000 })
    await recorder.start()
    const instance = FakeMediaRecorder.instances[0]
    instance.emitData(512)

    vi.advanceTimersByTime(1200)
    expect(recorder.recording.value).toBe(false)
    expect(recorder.stopReason.value).toBe('max-duration')
    expect(recorder.durationMs.value).toBe(1000)
    expect(recorder.blob.value?.size).toBe(512)
    scope.stop()
  })

  it('reset 停止录音、释放 track 并清空结果', async () => {
    const { recorder, scope, trackStop } = setup()
    await recorder.start()
    FakeMediaRecorder.instances[0].emitData(128)
    vi.advanceTimersByTime(500)

    recorder.reset()
    expect(recorder.recording.value).toBe(false)
    expect(recorder.blob.value).toBeNull()
    expect(recorder.durationMs.value).toBeNull()
    expect(recorder.elapsedMs.value).toBe(0)
    expect(recorder.error.value).toBe('')
    expect(trackStop).toHaveBeenCalledTimes(1)
    scope.stop()
  })
})
