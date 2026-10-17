import { computed, onScopeDispose, ref } from 'vue'

/**
 * Web Speech 转写封装（F4-2，计划 §5.7.5）：
 * - 只做"实时字幕"：final 累加、interim 单独展示，最终文本可编辑后再提交；
 * - 错误映射：not-allowed/service-not-allowed（权限/服务）、no-speech、network（离线）；
 * - onend 自动重启最多 3 次；权限拒绝/服务禁用不再重启，避免死循环；
 * - 浏览器不支持时直接降级为手工转写（房间内 textarea）。
 *
 * Chrome 的 SpeechRecognition 不在 TS DOM 类型中，这里用最小结构类型描述，
 * 依赖可注入，便于单测覆盖 final/interim 累积与错误分支。
 */
export interface SpeechAlternativeLike {
  transcript: string
  confidence?: number
}

export interface SpeechResultLike {
  readonly isFinal: boolean
  readonly length: number
  [index: number]: SpeechAlternativeLike
}

export interface SpeechResultListLike {
  readonly length: number
  [index: number]: SpeechResultLike
}

export interface SpeechRecognitionEventLike {
  resultIndex?: number
  results: SpeechResultListLike
}

export interface SpeechRecognitionErrorEventLike {
  error: string
  message?: string
}

export interface SpeechRecognitionLike {
  lang: string
  continuous: boolean
  interimResults: boolean
  maxAlternatives: number
  onstart: (() => void) | null
  onresult: ((event: SpeechRecognitionEventLike) => void) | null
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null
  onend: (() => void) | null
  onspeechend: (() => void) | null
  start: () => void
  stop: () => void
  abort: () => void
}

export interface SpeechRecognitionCtorLike {
  new (): SpeechRecognitionLike
}

export type SpeechErrorCode =
  | 'unsupported'
  | 'permission-denied'
  | 'no-speech'
  | 'network'
  | 'audio-capture'
  | 'aborted'
  | 'unknown'

export interface SpeechRecognitionOptions {
  speechRecognitionCtor?: SpeechRecognitionCtorLike | null
  lang?: string
  continuous?: boolean
  interimResults?: boolean
  /** onend 自动重启上限，默认 3（计划 §5.7.5） */
  maxRestarts?: number
}

function resolveSpeechCtor(): SpeechRecognitionCtorLike | null {
  if (typeof window === 'undefined') return null
  const scoped = window as unknown as Record<string, unknown>
  const ctor = scoped.SpeechRecognition ?? scoped.webkitSpeechRecognition
  return typeof ctor === 'function' ? (ctor as SpeechRecognitionCtorLike) : null
}

const ERROR_MESSAGES: Record<SpeechErrorCode, string> = {
  unsupported: '当前浏览器不支持自动转写，可在下方手工填写转写文本',
  'permission-denied': '语音识别权限或服务不可用，可手工填写转写后提交',
  'no-speech': '未识别到语音，可重录或手工填写转写',
  network: '语音识别需要网络；当前离线时可手工填写转写后提交',
  'audio-capture': '未捕获到麦克风声音，请检查设备后重录',
  aborted: '',
  unknown: '语音识别失败，可重录或手工填写转写后提交',
}

function mapSpeechError(code: string): SpeechErrorCode {
  switch (code) {
    case 'not-allowed':
    case 'service-not-allowed':
      return 'permission-denied'
    case 'no-speech':
      return 'no-speech'
    case 'network':
      return 'network'
    case 'audio-capture':
      return 'audio-capture'
    case 'aborted':
      return 'aborted'
    default:
      return 'unknown'
  }
}

export function useSpeechRecognition(options: SpeechRecognitionOptions = {}) {
  const ctor =
    options.speechRecognitionCtor !== undefined
      ? options.speechRecognitionCtor
      : resolveSpeechCtor()
  const supported = ctor != null
  const maxRestarts = Math.max(0, options.maxRestarts ?? 3)

  const listening = ref(false)
  const finalText = ref('')
  const interimText = ref('')
  const error = ref('')
  const errorCode = ref<SpeechErrorCode | null>(null)
  const restartCount = ref(0)
  const speechEndCount = ref(0)

  const pauseCount = computed(() => Math.max(0, speechEndCount.value - 1))

  let recognition: SpeechRecognitionLike | null = null
  let desired = false
  let blocked = false
  let restartTimer: number | null = null

  function clearRestartTimer(): void {
    if (restartTimer != null) {
      window.clearTimeout(restartTimer)
      restartTimer = null
    }
  }

  function setError(code: SpeechErrorCode): void {
    errorCode.value = code
    error.value = ERROR_MESSAGES[code]
  }

  function detach(): void {
    if (!recognition) return
    recognition.onstart = null
    recognition.onresult = null
    recognition.onerror = null
    recognition.onend = null
    recognition.onspeechend = null
    recognition = null
  }

  function handleResult(event: SpeechRecognitionEventLike): void {
    const results = event?.results
    if (!results) return
    const startIndex = Math.max(0, event.resultIndex ?? 0)
    let interim = ''
    for (let index = startIndex; index < results.length; index += 1) {
      const result = results[index]
      if (!result) continue
      const alternative = result[0]
      const transcript = alternative?.transcript ?? ''
      if (!transcript) continue
      if (result.isFinal) {
        finalText.value += transcript
      } else {
        interim += transcript
      }
    }
    interimText.value = interim
  }

  function handleError(event: SpeechRecognitionErrorEventLike): void {
    const code = mapSpeechError(event?.error ?? '')
    if (code === 'aborted') return
    setError(code)
    if (code === 'permission-denied' || code === 'audio-capture') {
      blocked = true
      desired = false
      listening.value = false
    }
    if (code === 'network') {
      // 断网时仍允许用户继续录音，但不再自动重试（避免 3 次无意义重连）
      desired = false
    }
  }

  function scheduleRestart(): void {
    if (!desired || blocked || restartCount.value >= maxRestarts) {
      desired = false
      return
    }
    clearRestartTimer()
    restartTimer = window.setTimeout(() => {
      restartTimer = null
      if (!desired || blocked || !recognition) return
      try {
        restartCount.value += 1
        recognition.start()
        listening.value = true
      } catch {
        desired = false
        setError('unknown')
      }
    }, 300)
  }

  function handleEnd(): void {
    listening.value = false
    interimText.value = ''
    if (desired && !blocked) {
      scheduleRestart()
      return
    }
    desired = false
  }

  function start(): void {
    error.value = ''
    errorCode.value = null
    interimText.value = ''
    if (!supported || !ctor) {
      setError('unsupported')
      return
    }
    if (listening.value && recognition) return

    clearRestartTimer()
    detach()
    desired = true
    blocked = false
    restartCount.value = 0

    let instance: SpeechRecognitionLike
    try {
      instance = new ctor()
    } catch {
      setError('unsupported')
      return
    }
    recognition = instance
    instance.lang = options.lang ?? 'zh-CN'
    instance.continuous = options.continuous ?? true
    instance.interimResults = options.interimResults ?? true
    instance.maxAlternatives = 1
    instance.onstart = () => {
      if (desired) listening.value = true
    }
    instance.onresult = (event) => handleResult(event)
    instance.onerror = (event) => handleError(event)
    instance.onend = () => handleEnd()
    instance.onspeechend = () => {
      speechEndCount.value += 1
    }

    try {
      instance.start()
      listening.value = true
    } catch {
      listening.value = false
      desired = false
      setError('unknown')
    }
  }

  function stop(): void {
    desired = false
    clearRestartTimer()
    listening.value = false
    interimText.value = ''
    if (recognition) {
      try {
        recognition.stop()
      } catch {
        // 已停止时 stop 可能抛 InvalidStateError，忽略
      }
    }
  }

  function reset(): void {
    stop()
    if (recognition) {
      try {
        recognition.abort()
      } catch {
        // ignore
      }
    }
    detach()
    finalText.value = ''
    interimText.value = ''
    error.value = ''
    errorCode.value = null
    restartCount.value = 0
    speechEndCount.value = 0
  }

  onScopeDispose(() => {
    reset()
  })

  return {
    supported,
    listening,
    finalText,
    interimText,
    error,
    errorCode,
    restartCount,
    pauseCount,
    maxRestarts,
    start,
    stop,
    reset,
  }
}
