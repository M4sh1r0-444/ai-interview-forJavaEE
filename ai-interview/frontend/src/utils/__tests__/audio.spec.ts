import { describe, expect, it } from 'vitest'

import { AUDIO_LIMITS, formatFileSize, validateAudioAnswer } from '@/utils/audio'

function blobOfSize(size: number): Blob {
  return new Blob([new Uint8Array(size)], { type: 'audio/webm' })
}

describe('utils/audio', () => {
  it('大小格式化覆盖 B/KB/MB 与非法值', () => {
    expect(formatFileSize(0)).toBe('0 B')
    expect(formatFileSize(800)).toBe('800 B')
    expect(formatFileSize(1536)).toBe('1.5 KB')
    expect(formatFileSize(2 * 1024 * 1024)).toBe('2.00 MB')
    expect(formatFileSize(null)).toBe('—')
    expect(formatFileSize(Number.NaN)).toBe('—')
  })

  it('合法音频回答通过校验', () => {
    expect(
      validateAudioAnswer({
        blob: blobOfSize(1024),
        durationMs: 10_000,
        asrText: '这是我的语音回答',
      }),
    ).toBeNull()
  })

  it('缺少录音/空文件/超 10MB 被拒绝', () => {
    expect(validateAudioAnswer({ durationMs: 1000, asrText: 'x' })).toContain('录制')
    expect(validateAudioAnswer({ blob: blobOfSize(0), durationMs: 1000, asrText: 'x' })).toContain(
      '录制',
    )
    expect(
      validateAudioAnswer({
        blob: blobOfSize(AUDIO_LIMITS.maxBytes + 1),
        durationMs: 1000,
        asrText: 'x',
      }),
    ).toContain('10.00 MB')
  })

  it('时长边界：0 / 超过 600000ms / 非数字均拒绝', () => {
    const base = { blob: blobOfSize(10), asrText: '回答' }
    expect(validateAudioAnswer({ ...base, durationMs: 0 })).toContain('1 秒')
    expect(validateAudioAnswer({ ...base, durationMs: AUDIO_LIMITS.maxDurationMs + 1 })).toContain(
      '10 分钟',
    )
    expect(validateAudioAnswer({ ...base, durationMs: Number.NaN })).toContain('1 秒')
  })

  it('转写必填且不超过 5000 字', () => {
    const base = { blob: blobOfSize(10), durationMs: 1000 }
    expect(validateAudioAnswer({ ...base, asrText: '   ' })).toContain('转写内容不能为空')
    expect(
      validateAudioAnswer({ ...base, asrText: '字'.repeat(AUDIO_LIMITS.maxTranscriptLength + 1) }),
    ).toContain('5000')
  })
})
