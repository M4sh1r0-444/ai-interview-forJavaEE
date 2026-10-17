import { computed, onScopeDispose, ref } from 'vue'

import { detectInterviewCapabilities } from '@/composables/useInterviewCapabilities'
import { AUDIO_LIMITS, type RecorderStopReason } from '@/utils/audio'

/**
 * MediaRecorder 录音封装（F4-1，计划 §5.7.5）：
 * - 首次 start 才请求麦克风权限，拒绝/无设备/被占用均有中文错误映射；
 * - 优先 audio/webm;codecs=opus，其次 audio/webm（与后端白名单 webm 对齐）；
 * - 停止后立即释放 MediaStream 全部 track，避免麦克风指示灯常亮；
 * - 计时用 Date.now 差值，到达 600s 自动停止；累计体积超过 10MB 自动停止并丢弃结果；
 * - 重复 start 防护；组件卸载/调用 reset 时同样释放资源。
 *
 * 依赖可注入，便于 Vitest 用假 MediaRecorder 覆盖边界（计划 §9.2）。
 */
export interface MediaRecorderLike {
  state: 'inactive' | 'recording' | 'paused'
  mimeType?: string
  ondataavailable: ((event: { data: Blob }) => void) | null
  onstop: ((event: Event) => void) | null
  onerror: ((event: Event) => void) | null
  start: (timeslice?: number) => void
  stop: () => void
}

export interface MediaRecorderCtorLike {
  new (stream: MediaStream, options?: { mimeType?: string }): MediaRecorderLike
  isTypeSupported?: (type: string) => boolean
}

export interface AudioRecorderOptions {
  maxDurationMs?: number
  maxBytes?: number
  /** 覆盖自动探测的 MIME；不传则使用能力检测结果 */
  mimeType?: string | null
  mediaRecorderCtor?: MediaRecorderCtorLike | null
  mediaDevices?: Pick<MediaDevices, 'getUserMedia'> | null
  audioEnabled?: boolean
  now?: () => number
  /** 计时刷新间隔，默认 200ms（UI 计时足够平滑，又不会过度渲染） */
  intervalMs?: number
}

function errorName(error: unknown): string {
  if (typeof error === 'object' && error !== null && 'name' in error) {
    return String((error as { name?: unknown }).name ?? '')
  }
  return ''
}

function permissionErrorText(error: unknown): string {
  switch (errorName(error)) {
    case 'NotAllowedError':
    case 'PermissionDeniedError':
    case 'SecurityError':
      return '麦克风权限被拒绝，请在浏览器地址栏允许麦克风后重试'
    case 'NotFoundError':
    case 'DevicesNotFoundError':
      return '未检测到可用的麦克风设备'
    case 'NotReadableError':
    case 'TrackStartError':
      return '麦克风正被其他应用占用，请关闭后重试'
    case 'OverconstrainedError':
      return '当前麦克风不满足录音参数要求'
    default:
      return '无法访问麦克风，请检查系统权限与设备连接'
  }
}

function defaultRecorderCtor(): MediaRecorderCtorLike | null {
  if (typeof window === 'undefined' || typeof window.MediaRecorder !== 'function') return null
  return window.MediaRecorder as unknown as MediaRecorderCtorLike
}

function defaultMediaDevices(): Pick<MediaDevices, 'getUserMedia'> | null {
  if (typeof navigator === 'undefined') return null
  return navigator.mediaDevices ?? null
}

export function useAudioRecorder(options: AudioRecorderOptions = {}) {
  const maxDurationMs = options.maxDurationMs ?? AUDIO_LIMITS.maxDurationMs
  const maxBytes = options.maxBytes ?? AUDIO_LIMITS.maxBytes
  const intervalMs = Math.max(100, options.intervalMs ?? 200)
  const now = options.now ?? (() => Date.now())

  const recorderCtor =
    options.mediaRecorderCtor !== undefined ? options.mediaRecorderCtor : defaultRecorderCtor()
  const mediaDevices =
    options.mediaDevices !== undefined ? options.mediaDevices : defaultMediaDevices()

  const capabilities = detectInterviewCapabilities({
    audioEnabled: options.audioEnabled,
    MediaRecorder: recorderCtor as unknown as typeof MediaRecorder | null,
    mediaDevices: mediaDevices as MediaDevices | null,
  })

  const mimeType = options.mimeType ?? capabilities.preferredMimeType
  // 显式 mimeType 可覆盖能力检测（测试注入与特殊浏览器）；仍要求录音器 + 麦克风可用。
  const supported =
    capabilities.audioEnabled &&
    capabilities.mediaRecorder &&
    capabilities.getUserMedia &&
    mimeType != null
  const disabledReason = capabilities.audioDisabledReason

  const requesting = ref(false)
  const recording = ref(false)
  const stopping = ref(false)
  const elapsedMs = ref(0)
  const blob = ref<Blob | null>(null)
  const durationMs = ref<number | null>(null)
  const recordedBytes = ref(0)
  const error = ref('')
  const stopReason = ref<RecorderStopReason | null>(null)

  let recorder: MediaRecorderLike | null = null
  let stream: MediaStream | null = null
  let chunks: Blob[] = []
  let startedAt = 0
  let timer: number | null = null
  let discardResult = false
  let requestToken = 0

  const state = computed(() => {
    if (requesting.value) return 'REQUESTING'
    if (recording.value) return 'RECORDING'
    if (blob.value) return 'STOPPED'
    return 'IDLE'
  })

  const progress = computed(() =>
    maxDurationMs > 0 ? Math.min(1, elapsedMs.value / maxDurationMs) : 0,
  )

  function clearTimer(): void {
    if (timer != null) {
      window.clearInterval(timer)
      timer = null
    }
  }

  function releaseStream(): void {
    if (!stream) return
    for (const track of stream.getTracks()) {
      try {
        track.stop()
      } catch {
        // 已停止的 track 再次 stop 不抛错；这里兜底旧浏览器
      }
    }
    stream = null
  }

  function detachRecorder(): void {
    if (!recorder) return
    recorder.ondataavailable = null
    recorder.onstop = null
    recorder.onerror = null
    recorder = null
  }

  function finishResult(reason: RecorderStopReason | null): void {
    const stoppedAt = now()
    const measured = Math.max(0, stoppedAt - startedAt)
    const finalDuration = Math.min(
      maxDurationMs,
      Math.max(AUDIO_LIMITS.minDurationMs, Math.round(measured)),
    )
    const collected = chunks
    chunks = []
    if (discardResult) {
      blob.value = null
      durationMs.value = null
      elapsedMs.value = 0
      stopReason.value = reason
      return
    }
    const played = new Blob(collected, { type: mimeType ?? 'audio/webm' })
    blob.value = played.size > 0 ? played : null
    durationMs.value = played.size > 0 ? finalDuration : null
    elapsedMs.value = played.size > 0 ? finalDuration : 0
    stopReason.value = reason
  }

  function onRecorderStop(): void {
    recording.value = false
    stopping.value = false
    clearTimer()
    releaseStream()
    finishResult(stopReason.value)
    detachRecorder()
  }

  function stop(reason: RecorderStopReason = 'manual'): void {
    if (!recorder || !recording.value) return
    if (recording.value) {
      recording.value = false
      stopping.value = true
    }
    stopReason.value = reason
    try {
      recorder.stop()
    } catch {
      stopping.value = false
      recording.value = false
      releaseStream()
      clearTimer()
      error.value = '停止录音失败，请重新录制'
      detachRecorder()
    }
  }

  async function start(): Promise<void> {
    if (recording.value || requesting.value) return
    error.value = ''
    stopReason.value = null
    if (!supported || !recorderCtor || !mediaDevices || !mimeType) {
      error.value = disabledReason || '当前浏览器不支持录音'
      return
    }
    const token = ++requestToken
    requesting.value = true
    let granted: MediaStream | null = null
    try {
      granted = await mediaDevices.getUserMedia({ audio: true })
    } catch (cause) {
      if (token === requestToken) {
        requesting.value = false
        error.value = permissionErrorText(cause)
      }
      return
    }
    if (token !== requestToken) {
      // reset/unmount 期间才拿到权限：立即释放，避免泄漏麦克风
      for (const track of granted.getTracks()) track.stop()
      return
    }

    requesting.value = false
    // 只有真正拿到权限后才丢弃上一次结果，权限被拒时保留旧录音
    blob.value = null
    durationMs.value = null
    elapsedMs.value = 0
    recordedBytes.value = 0
    chunks = []
    discardResult = false
    stream = granted

    let instance: MediaRecorderLike
    try {
      instance = new recorderCtor(granted, mimeType ? { mimeType } : undefined)
    } catch {
      releaseStream()
      error.value = '当前浏览器无法创建录音器，请更换 Chrome / Edge 后重试'
      return
    }
    recorder = instance
    instance.ondataavailable = (event) => {
      const data = event?.data
      if (!data || data.size <= 0) return
      chunks.push(data)
      recordedBytes.value += data.size
      if (recordedBytes.value > maxBytes && recording.value) {
        discardResult = true
        error.value = `录音超过 ${Math.round(maxBytes / 1024 / 1024)}MB 上限，已自动停止；请缩短后重录`
        stop('max-size')
      }
    }
    instance.onstop = onRecorderStop
    instance.onerror = () => {
      error.value = '录音过程中发生错误，请重新录制'
      stop('permission-lost')
    }

    try {
      instance.start(1000)
    } catch {
      releaseStream()
      detachRecorder()
      error.value = '录音启动失败，请检查麦克风权限'
      return
    }

    startedAt = now()
    recording.value = true
    stopping.value = false
    timer = window.setInterval(() => {
      if (!recording.value) return
      const elapsed = Math.max(0, now() - startedAt)
      elapsedMs.value = Math.min(elapsed, maxDurationMs)
      if (elapsed >= maxDurationMs) {
        stop('max-duration')
      }
    }, intervalMs)
  }

  /** 重录 = reset + start；保留错误不清空，交给 start 重新覆盖。 */
  async function restart(): Promise<void> {
    reset()
    await start()
  }

  function reset(): void {
    requestToken += 1
    clearTimer()
    if (recording.value && recorder) {
      discardResult = true
      try {
        recorder.stop()
      } catch {
        // ignore
      }
    }
    discardResult = false
    recording.value = false
    requesting.value = false
    stopping.value = false
    releaseStream()
    detachRecorder()
    chunks = []
    blob.value = null
    durationMs.value = null
    elapsedMs.value = 0
    recordedBytes.value = 0
    error.value = ''
    stopReason.value = null
  }

  onScopeDispose(() => {
    reset()
  })

  return {
    supported,
    disabledReason,
    mimeType,
    maxDurationMs,
    maxBytes,
    requesting,
    recording,
    stopping,
    elapsedMs,
    durationMs,
    recordedBytes,
    blob,
    error,
    stopReason,
    state,
    progress,
    start,
    stop,
    restart,
    reset,
  }
}
