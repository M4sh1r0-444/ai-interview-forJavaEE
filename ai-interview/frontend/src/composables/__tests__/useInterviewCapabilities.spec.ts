import { describe, expect, it } from 'vitest'

import {
  AUDIO_MIME_CANDIDATES,
  detectInterviewCapabilities,
} from '@/composables/useInterviewCapabilities'

function recorder(supported: string[] | null) {
  return {
    isTypeSupported: (mime: string) => (supported ? supported.includes(mime) : false),
  } as unknown as typeof MediaRecorder
}

const mediaDevices = {
  getUserMedia: async () => ({}) as MediaStream,
} as unknown as MediaDevices

describe('interview capabilities', () => {
  it('完整支持时优先 webm/opus，并给出自动转写提示', () => {
    const caps = detectInterviewCapabilities({
      MediaRecorder: recorder([...AUDIO_MIME_CANDIDATES]),
      mediaDevices,
      speechRecognition: function SpeechRecognition() {},
      audioEnabled: true,
    })
    expect(caps.audioSupported).toBe(true)
    expect(caps.speechSupported).toBe(true)
    expect(caps.preferredMimeType).toBe('audio/webm;codecs=opus')
    expect(caps.audioDisabledReason).toBe('')
    expect(caps.speechHint).toContain('自动转写')
  })

  it('仅支持 audio/webm 时降级选择第二种 MIME', () => {
    const caps = detectInterviewCapabilities({
      MediaRecorder: recorder(['audio/webm']),
      mediaDevices,
      speechRecognition: null,
      audioEnabled: true,
    })
    expect(caps.audioSupported).toBe(true)
    expect(caps.preferredMimeType).toBe('audio/webm')
    expect(caps.speechSupported).toBe(false)
    expect(caps.speechHint).toContain('手工填写')
  })

  it('构建关闭语音时 AUDIO 不可用', () => {
    const caps = detectInterviewCapabilities({
      MediaRecorder: recorder(['audio/webm']),
      mediaDevices,
      speechRecognition: null,
      audioEnabled: false,
    })
    expect(caps.audioEnabled).toBe(false)
    expect(caps.audioSupported).toBe(false)
    expect(caps.audioDisabledReason).toContain('已关闭语音面试')
  })

  it('缺少 MediaRecorder / getUserMedia / MIME 时分别给出原因', () => {
    const noRecorder = detectInterviewCapabilities({
      MediaRecorder: null,
      mediaDevices,
      speechRecognition: null,
      audioEnabled: true,
    })
    expect(noRecorder.audioSupported).toBe(false)
    expect(noRecorder.audioDisabledReason).toContain('MediaRecorder')

    const noMic = detectInterviewCapabilities({
      MediaRecorder: recorder(['audio/webm']),
      mediaDevices: null,
      speechRecognition: null,
      audioEnabled: true,
    })
    expect(noMic.audioSupported).toBe(false)
    expect(noMic.audioDisabledReason).toContain('麦克风')

    const noMime = detectInterviewCapabilities({
      MediaRecorder: recorder(null),
      mediaDevices,
      speechRecognition: null,
      audioEnabled: true,
    })
    expect(noMime.audioSupported).toBe(false)
    expect(noMime.audioDisabledReason).toContain('webm/opus')
  })
})
