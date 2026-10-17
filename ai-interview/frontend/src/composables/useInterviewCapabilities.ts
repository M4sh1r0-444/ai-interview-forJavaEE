/**
 * 语音面试能力检测（F2-3；录音/转写完整实现属 F4）。
 * 检测项：MediaRecorder、getUserMedia、Web Speech、可用的 webm/opus MIME。
 * 缺失 ASR 但可录音时仍允许 AUDIO，房间内可手工填写转写。
 */

export interface CapabilityOverrides {
  MediaRecorder?: typeof MediaRecorder | null
  mediaDevices?: MediaDevices | null
  speechRecognition?: unknown
  audioEnabled?: boolean
}

export interface InterviewCapabilities {
  /** 构建配置未禁用语音 */
  audioEnabled: boolean
  mediaRecorder: boolean
  getUserMedia: boolean
  speechRecognition: boolean
  /** MediaRecorder + getUserMedia + 可用 MIME */
  audioSupported: boolean
  /** 自动转写可用（Web Speech） */
  speechSupported: boolean
  preferredMimeType: string | null
  /** AUDIO 卡片不可用时展示的原因 */
  audioDisabledReason: string
  /** ASR 不可用时给用户的降级提示 */
  speechHint: string
}

export const AUDIO_MIME_CANDIDATES = ['audio/webm;codecs=opus', 'audio/webm'] as const

function resolveSpeechCtor(): unknown {
  if (typeof window === 'undefined') return undefined
  const scoped = window as unknown as Record<string, unknown>
  return scoped.SpeechRecognition ?? scoped.webkitSpeechRecognition
}

function pickMimeType(ctor?: typeof MediaRecorder | null): string | null {
  if (!ctor || typeof ctor.isTypeSupported !== 'function') return null
  for (const candidate of AUDIO_MIME_CANDIDATES) {
    try {
      if (ctor.isTypeSupported(candidate)) return candidate
    } catch {
      // 某些浏览器对未知 MIME 抛错，继续尝试下一项
    }
  }
  return null
}

export function detectInterviewCapabilities(
  overrides: CapabilityOverrides = {},
): InterviewCapabilities {
  const audioEnabled =
    overrides.audioEnabled ?? (import.meta.env.VITE_ENABLE_AUDIO || 'true') !== 'false'

  const recorderCtor =
    overrides.MediaRecorder !== undefined
      ? overrides.MediaRecorder
      : typeof window !== 'undefined' && typeof window.MediaRecorder === 'function'
        ? window.MediaRecorder
        : null

  const mediaDevices =
    overrides.mediaDevices !== undefined
      ? overrides.mediaDevices
      : typeof navigator !== 'undefined'
        ? (navigator.mediaDevices ?? null)
        : null

  const getUserMedia = typeof mediaDevices?.getUserMedia === 'function'
  const speechCtor =
    overrides.speechRecognition !== undefined ? overrides.speechRecognition : resolveSpeechCtor()
  const speechSupported = typeof speechCtor === 'function'
  const preferredMimeType = pickMimeType(recorderCtor)
  const mediaRecorder = !!recorderCtor
  const audioSupported = audioEnabled && mediaRecorder && getUserMedia && preferredMimeType != null

  let audioDisabledReason = ''
  if (!audioEnabled) {
    audioDisabledReason = '当前构建已关闭语音面试，请使用文字模式'
  } else if (!mediaRecorder) {
    audioDisabledReason = '当前浏览器不支持录音（MediaRecorder）'
  } else if (!getUserMedia) {
    audioDisabledReason = '当前浏览器无法请求麦克风权限'
  } else if (!preferredMimeType) {
    audioDisabledReason = '当前浏览器不支持 webm/opus 录音格式'
  }

  return {
    audioEnabled,
    mediaRecorder,
    getUserMedia,
    speechRecognition: speechSupported,
    audioSupported,
    speechSupported,
    preferredMimeType,
    audioDisabledReason,
    speechHint: speechSupported
      ? '浏览器自动转写已就绪，录音结束后可修正文本'
      : '本浏览器不支持自动转写，录音后可在房间内手工填写转写文本',
  }
}

/** 组件内一次性能力检测；能力不会在会话期间变化。 */
export function useInterviewCapabilities(): InterviewCapabilities {
  return detectInterviewCapabilities()
}
