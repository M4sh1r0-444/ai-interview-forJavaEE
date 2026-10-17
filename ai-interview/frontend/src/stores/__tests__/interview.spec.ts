import { createPinia, setActivePinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as interviewApi from '@/api/interview'
import { BizError } from '@/api/http'
import { useInterviewStore } from '@/stores/interview'
import type { InterviewDetailVO, MessageVO } from '@/types/interview'

vi.mock('@/api/interview', () => ({
  getInterview: vi.fn(),
  getMessages: vi.fn(),
  startInterview: vi.fn(),
  submitTextAnswer: vi.fn(),
  submitAudioAnswer: vi.fn(),
  finishInterview: vi.fn(),
  abortInterview: vi.fn(),
}))

const mocked = vi.mocked(interviewApi)

function detail(overrides: Partial<InterviewDetailVO> = {}): InterviewDetailVO {
  return {
    id: 1,
    userId: 2,
    positionId: 1,
    positionName: 'Java 后端开发工程师',
    mode: 'TEXT',
    difficulty: 3,
    plannedRounds: 5,
    currentRound: 0,
    status: 'WAITING_USER',
    remainSeconds: 900,
    ...overrides,
  }
}

let nextId = 1
function message(
  speaker: 'AI' | 'USER',
  roundNo: number,
  contentText = `${speaker}-${roundNo}`,
): MessageVO {
  nextId += 1
  return {
    id: nextId,
    sessionId: 1,
    roundNo,
    speaker,
    contentText,
    createdAt: '2026-10-17T20:00:00',
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('interview store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('init 合并 detail/messages 并派生轮次、追问与可作答状态', async () => {
    mocked.getInterview.mockResolvedValue(detail())
    mocked.getMessages.mockResolvedValue([message('AI', 0, '首题')])

    const store = useInterviewStore()
    await store.init(1)

    expect(store.detail?.status).toBe('WAITING_USER')
    expect(store.messages).toHaveLength(1)
    expect(store.awaitingAnswer).toBe(true)
    expect(store.canSubmit).toBe(true)
    expect(store.displayRound).toBe(1)
    expect(store.followupCount).toBe(0)
    expect(store.currentQuestion?.contentText).toBe('首题')
    expect(store.loaded).toBe(true)
  })

  it('refresh 并发去重，只发一次请求', async () => {
    mocked.getInterview.mockResolvedValue(detail())
    mocked.getMessages.mockResolvedValue([])

    const store = useInterviewStore()
    await store.init(1)
    vi.clearAllMocks()

    const pending = deferred<InterviewDetailVO>()
    mocked.getInterview.mockReturnValue(pending.promise)
    mocked.getMessages.mockResolvedValue([])

    const first = store.refresh()
    const second = store.refresh()
    expect(mocked.getInterview).toHaveBeenCalledTimes(1)
    pending.resolve(detail())
    await Promise.all([first, second])
    expect(store.refreshing).toBe(false)
  })

  it('提交时加锁并展示乐观气泡，成功后由服务端消息对账清除', async () => {
    const store = useInterviewStore()
    store.sessionId = 1
    store.detail = detail()
    store.messages = [message('AI', 0, '首题')]

    const pending = deferred<{ accepted: boolean; roundNo: number }>()
    mocked.submitTextAnswer.mockReturnValue(pending.promise)
    mocked.getInterview.mockResolvedValue(detail({ status: 'PROCESSING_AI' }))
    const userMessage = message('USER', 0, '我的回答')
    mocked.getMessages.mockResolvedValue([message('AI', 0, '首题'), userMessage])

    const task = store.submitText('我的回答')
    expect(store.submitting).toBe(true)
    expect(store.canSubmit).toBe(false)
    expect(store.optimistic?.message.contentText).toBe('我的回答')

    await expect(store.submitText('重复提交')).rejects.toMatchObject({ code: 10005 })

    pending.resolve({ accepted: true, roundNo: 0 })
    await task
    await flushPromises()

    expect(store.submitting).toBe(false)
    expect(store.optimistic).toBeNull()
    expect(store.aiThinking).toBe(true)
    expect(store.detail?.status).toBe('PROCESSING_AI')
    expect(store.canSubmit).toBe(false)
    expect(mocked.submitTextAnswer).toHaveBeenCalledTimes(1)
  })

  it('提交失败保留失败气泡和错误，不自动重发', async () => {
    const store = useInterviewStore()
    store.sessionId = 1
    store.detail = detail()
    store.messages = [message('AI', 0, '首题')]

    mocked.submitTextAnswer.mockRejectedValue(new BizError(50001, '服务异常', 'trace-1'))

    await expect(store.submitText('失败的回答')).rejects.toMatchObject({ code: 50001 })

    expect(store.submitting).toBe(false)
    expect(store.optimistic?.state).toBe('failed')
    expect(store.submitError).toMatchObject({ code: 50001 })
    expect(mocked.submitTextAnswer).toHaveBeenCalledTimes(1)
  })

  it('音频提交：本地校验 → multipart 受理 → 乐观气泡对账', async () => {
    const store = useInterviewStore()
    store.sessionId = 1
    store.detail = detail({ mode: 'AUDIO' })
    store.messages = [message('AI', 0, '请用语音回答')]

    mocked.submitAudioAnswer.mockResolvedValue({ accepted: true, roundNo: 0 })
    mocked.getInterview.mockResolvedValue(detail({ mode: 'AUDIO', status: 'PROCESSING_AI' }))
    const audioMessage = {
      ...message('USER', 0, '我的语音回答'),
      inputType: 'AUDIO' as const,
      asrText: '我的语音回答',
    }
    mocked.getMessages.mockResolvedValue([message('AI', 0, '请用语音回答'), audioMessage])

    const file = new Blob([new Uint8Array(1024)], { type: 'audio/webm' })
    const accepted = await store.submitAudio({
      file,
      asrText: '  我的语音回答  ',
      durationMs: 30_000,
      pauseCount: 1,
    })
    await flushPromises()

    expect(accepted.accepted).toBe(true)
    expect(mocked.submitAudioAnswer).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        roundNo: 0,
        asrText: '我的语音回答',
        durationMs: 30_000,
        pauseCount: 1,
      }),
    )
    expect(store.submitting).toBe(false)
    expect(store.optimistic).toBeNull()
    expect(store.aiThinking).toBe(true)
    expect(store.detail?.status).toBe('PROCESSING_AI')
  })

  it('音频提交前校验：转写为空/录音超限不发请求', async () => {
    const store = useInterviewStore()
    store.sessionId = 1
    store.detail = detail({ mode: 'AUDIO' })
    store.messages = [message('AI', 0)]

    await expect(
      store.submitAudio({
        file: new Blob([new Uint8Array(10)]),
        asrText: '   ',
        durationMs: 1000,
      }),
    ).rejects.toMatchObject({ code: 40001 })

    await expect(
      store.submitAudio({
        file: new Blob([new Uint8Array(10)]),
        asrText: '回答',
        durationMs: 600_001,
      }),
    ).rejects.toMatchObject({ code: 40001 })
    expect(mocked.submitAudioAnswer).not.toHaveBeenCalled()
  })

  it('音频提交失败保留失败气泡与录音，不自动重发', async () => {
    const store = useInterviewStore()
    store.sessionId = 1
    store.detail = detail({ mode: 'AUDIO' })
    store.messages = [message('AI', 0)]

    mocked.submitAudioAnswer.mockRejectedValue(new BizError(50001, '上传失败', 'trace-audio'))
    await expect(
      store.submitAudio({
        file: new Blob([new Uint8Array(10)]),
        asrText: '回答',
        durationMs: 2000,
      }),
    ).rejects.toMatchObject({ code: 50001 })

    expect(store.optimistic?.state).toBe('failed')
    expect(store.optimistic?.message.inputType).toBe('AUDIO')
    expect(store.submitError).toMatchObject({ code: 50001 })
    expect(mocked.submitAudioAnswer).toHaveBeenCalledTimes(1)
  })

  it('applyWsEvent 更新状态、轮次、剩余时间与错误', () => {
    const store = useInterviewStore()
    store.detail = detail()
    store.messages = [message('AI', 0)]

    store.applyWsEvent({
      type: 'TIMER',
      payload: { remainSeconds: 42 },
      ts: 1,
    })
    expect(store.detail?.remainSeconds).toBe(42)

    store.applyWsEvent({
      type: 'QUESTION',
      payload: {
        roundNo: 1,
        followupCount: 0,
        content: '下一题',
        difficulty: 4,
        targetSkills: ['java_basics'],
      },
      ts: 2,
    })
    expect(store.detail?.currentRound).toBe(1)
    expect(store.detail?.status).toBe('WAITING_USER')
    expect(store.aiThinking).toBe(false)

    store.applyWsEvent({
      type: 'PROGRESS',
      payload: { status: 'FINISHED', currentRound: 2, plannedRounds: 5 },
      ts: 3,
    })
    expect(store.isTerminal).toBe(true)
    expect(store.displayRound).toBe(5)

    store.applyWsEvent({
      type: 'ERROR',
      payload: { code: 50001, message: 'AI 繁忙' },
      ts: 4,
    })
    expect(store.lastWsError?.message).toBe('AI 繁忙')
    expect(store.lastWsEventAt).toBeGreaterThan(0)
  })

  it('TRANSCRIPT 事件按 messageId 回填转写文本', () => {
    const store = useInterviewStore()
    const audioMessage = { ...message('USER', 0, '初始转写'), inputType: 'AUDIO' as const }
    store.messages = [message('AI', 0), audioMessage]

    store.applyWsEvent({
      type: 'TRANSCRIPT',
      payload: { messageId: audioMessage.id, asrText: '服务端校正后的转写' },
      ts: 5,
    })
    expect(store.messages[1].asrText).toBe('服务端校正后的转写')
    expect(store.messages[0].asrText).toBeUndefined()
  })

  it('finish 后将状态推进到终态并刷新', async () => {
    const store = useInterviewStore()
    store.sessionId = 1
    store.detail = detail()
    store.messages = [message('AI', 0)]

    mocked.finishInterview.mockResolvedValue({
      accepted: true,
      status: 'FINISHED',
      reportId: null,
    })
    mocked.getInterview.mockResolvedValue(detail({ status: 'FINISHED' }))
    mocked.getMessages.mockResolvedValue([message('AI', 0)])

    const result = await store.finish()
    expect(result.accepted).toBe(true)
    expect(store.finishing).toBe(false)
    expect(store.detail?.status).toBe('FINISHED')
    expect(store.isTerminal).toBe(true)
    expect(store.aiThinking).toBe(false)
  })

  it('abort 返回 ABORTED 详情并清理进行中状态', async () => {
    const store = useInterviewStore()
    store.sessionId = 1
    store.detail = detail()
    store.messages = [message('AI', 0)]
    store.aiThinking = true

    mocked.abortInterview.mockResolvedValue(detail({ status: 'ABORTED' }))
    mocked.getInterview.mockResolvedValue(detail({ status: 'ABORTED' }))
    mocked.getMessages.mockResolvedValue([message('AI', 0)])

    await store.abort()
    expect(store.detail?.status).toBe('ABORTED')
    expect(store.isTerminal).toBe(true)
    expect(store.aiThinking).toBe(false)
    expect(store.aborting).toBe(false)
  })

  it('reset 清理全部房间状态', () => {
    const store = useInterviewStore()
    store.sessionId = 1
    store.detail = detail()
    store.messages = [message('AI', 0)]
    store.optimistic = {
      message: message('USER', 0),
      state: 'pending',
    }
    store.reset()

    expect(store.sessionId).toBeNull()
    expect(store.detail).toBeNull()
    expect(store.messages).toEqual([])
    expect(store.optimistic).toBeNull()
    expect(store.loaded).toBe(false)
    expect(store.isTerminal).toBe(false)
  })
})
