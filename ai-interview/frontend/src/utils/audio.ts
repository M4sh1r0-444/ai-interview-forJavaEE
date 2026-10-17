/**
 * 语音作答边界（F4-1/F4-3，与后端 InterviewService.submitAudioAnswer 对齐）：
 * - 单次录音 1ms ~ 600000ms（10 分钟）；上限到达自动停止；
 * - 音频文件 ≤ 10MB（后端 multipart 全局限制）；
 * - 转写文本 1~5000 字（asrText 同时作为 content_text）。
 */
export const AUDIO_LIMITS = {
  maxDurationMs: 600_000,
  minDurationMs: 1,
  maxBytes: 10 * 1024 * 1024,
  maxTranscriptLength: 5000,
} as const

export interface AudioAnswerDraft {
  blob?: Blob | null
  durationMs?: number | null
  asrText?: string | null
}

/** 字节数 → 人类可读大小（录音体积提示用）。 */
export function formatFileSize(bytes?: number | null): string {
  if (bytes == null || !Number.isFinite(bytes) || bytes < 0) return '—'
  if (bytes < 1024) return `${Math.round(bytes)} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

/**
 * 提交前校验（与后端同口径）。返回中文错误文案；通过返回 null。
 * 页面/组件只展示这一处文案，避免多处规则漂移。
 */
export function validateAudioAnswer(draft: AudioAnswerDraft): string | null {
  const { blob, durationMs, asrText } = draft
  if (!blob || blob.size <= 0) return '请先录制一段语音再提交'
  if (blob.size > AUDIO_LIMITS.maxBytes) {
    return `录音文件超过 ${formatFileSize(AUDIO_LIMITS.maxBytes)}，请缩短后重录`
  }
  if (
    durationMs == null ||
    !Number.isFinite(durationMs) ||
    durationMs < AUDIO_LIMITS.minDurationMs ||
    durationMs > AUDIO_LIMITS.maxDurationMs
  ) {
    return '录音时长需在 1 秒 ~ 10 分钟之间'
  }
  const text = asrText?.trim() ?? ''
  if (!text) return '转写内容不能为空，可手动修正后再提交'
  if (text.length > AUDIO_LIMITS.maxTranscriptLength) {
    return `转写内容不能超过 ${AUDIO_LIMITS.maxTranscriptLength} 字`
  }
  return null
}

/** 录音自动停止原因（达到时长上限或体积上限时给用户的解释）。 */
export type RecorderStopReason = 'manual' | 'max-duration' | 'max-size' | 'permission-lost'
