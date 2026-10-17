import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import * as interviewApi from '@/api/interview'
import { BizError, isBizError } from '@/api/http'
import type {
  AudioAnswerPayload,
  FinishAccepted,
  InterviewDetailVO,
  MessageVO,
  SubmitAccepted,
} from '@/types/interview'
import type {
  ErrorPayload,
  ProgressPayload,
  QuestionPayload,
  TimerPayload,
  TranscriptPayload,
  WsEnvelope,
} from '@/types/ws'
import { validateAudioAnswer } from '@/utils/audio'
import {
  computeAwaitingAnswer,
  computeDisplayRound,
  computeFollowupCount,
  countAnsweredRounds,
  currentQuestionOfRound,
  isTerminalStatus,
  messagesOfRound,
} from '@/utils/round'

export type OptimisticState = 'pending' | 'sent' | 'failed'

export interface OptimisticAnswer {
  message: MessageVO
  state: OptimisticState
}

interface LoadOptions {
  /** 静默刷新：保留旧数据，不触发整页骨架屏。 */
  silent?: boolean
}

function isSameAnswer(server: MessageVO, optimistic: MessageVO): boolean {
  return (
    server.speaker === 'USER' &&
    server.roundNo === optimistic.roundNo &&
    server.contentText.trim() === optimistic.contentText.trim()
  )
}

/**
 * 面试房间唯一事实源（计划 §6.4）：detail + messages 均来自服务端；
 * 前端只维护派生态（可作答、轮次展示、追问计数、乐观气泡、提交锁）。
 * 所有 WS 事件都先落到这里，再统一刷新对账（D32）。
 */
export const useInterviewStore = defineStore('interview', () => {
  const sessionId = ref<number | null>(null)
  const detail = ref<InterviewDetailVO | null>(null)
  const messages = ref<MessageVO[]>([])
  const loaded = ref(false)
  const loading = ref(false)
  const refreshing = ref(false)
  const loadError = ref<unknown>(null)

  const submitting = ref(false)
  const finishing = ref(false)
  const aborting = ref(false)
  const starting = ref(false)
  const submitError = ref<unknown>(null)
  const aiThinking = ref(false)
  const optimistic = ref<OptimisticAnswer | null>(null)

  const lastSyncedAt = ref(0)
  const lastWsEventAt = ref(0)
  const lastWsError = ref<ErrorPayload | null>(null)
  const lastReportId = ref<number | null>(null)

  let refreshPromise: Promise<void> | null = null
  let requestSeq = 0

  const status = computed(() => detail.value?.status ?? null)
  const isTerminal = computed(() => isTerminalStatus(detail.value?.status))
  const awaitingAnswer = computed(() => computeAwaitingAnswer(detail.value, messages.value))
  const canSubmit = computed(
    () =>
      awaitingAnswer.value &&
      !submitting.value &&
      !finishing.value &&
      !aborting.value &&
      !starting.value,
  )
  const displayRound = computed(() => computeDisplayRound(detail.value))
  const followupCount = computed(() =>
    computeFollowupCount(messages.value, detail.value?.currentRound ?? 0),
  )
  const currentQuestion = computed(() =>
    currentQuestionOfRound(messages.value, detail.value?.currentRound ?? 0),
  )
  const currentRoundMessages = computed(() =>
    messagesOfRound(messages.value, detail.value?.currentRound ?? 0),
  )
  const answeredRounds = computed(() => countAnsweredRounds(messages.value))
  const isBusy = computed(
    () => submitting.value || finishing.value || aborting.value || starting.value,
  )

  function reset(): void {
    sessionId.value = null
    detail.value = null
    messages.value = []
    loaded.value = false
    loading.value = false
    refreshing.value = false
    loadError.value = null
    submitting.value = false
    finishing.value = false
    aborting.value = false
    starting.value = false
    submitError.value = null
    aiThinking.value = false
    optimistic.value = null
    lastSyncedAt.value = 0
    lastWsEventAt.value = 0
    lastWsError.value = null
    lastReportId.value = null
    refreshPromise = null
    requestSeq += 1
  }

  async function init(id: number): Promise<void> {
    if (sessionId.value !== id) reset()
    sessionId.value = id
    await refresh()
    loaded.value = true
  }

  function reconcileOptimistic(): void {
    if (!optimistic.value) return
    const match = messages.value.find((message) => isSameAnswer(message, optimistic.value!.message))
    if (match) optimistic.value = null
  }

  async function runLoad(options: LoadOptions): Promise<void> {
    const id = sessionId.value
    if (id == null) return
    const seq = ++requestSeq
    if (!options.silent) loading.value = true
    refreshing.value = true

    try {
      const [nextDetail, nextMessages] = await Promise.all([
        interviewApi.getInterview(id),
        interviewApi.getMessages(id),
      ])
      if (seq !== requestSeq) return
      detail.value = nextDetail
      messages.value = nextMessages
      loadError.value = null
      aiThinking.value = nextDetail.status === 'PROCESSING_AI'
      reconcileOptimistic()
      lastSyncedAt.value = Date.now()
    } catch (error) {
      if (seq !== requestSeq) return
      loadError.value = error
      if (!options.silent && !detail.value) {
        messages.value = []
      }
      throw error
    } finally {
      if (seq === requestSeq) {
        loading.value = false
        refreshing.value = false
      }
    }
  }

  /** 去重的静默刷新：并发调用共享同一个在途请求。 */
  function refresh(options: LoadOptions = {}): Promise<void> {
    if (refreshPromise) return refreshPromise
    const task = runLoad(options)
    refreshPromise = task
    task.then(
      () => {
        if (refreshPromise === task) refreshPromise = null
      },
      () => {
        if (refreshPromise === task) refreshPromise = null
      },
    )
    return task
  }

  /**
   * 作废可能在途的旧刷新：受理类操作（start/submit/finish/abort）成功后必须让下一次
   * refresh 真正发请求，避免复用一个提交前发起、返回旧状态的请求造成重复作答窗口。
   */
  function invalidateInFlight(): void {
    requestSeq += 1
    refreshPromise = null
  }

  function requireSessionId(): number {
    if (sessionId.value == null) {
      throw new BizError(40401, '面试会话尚未加载')
    }
    return sessionId.value
  }

  async function start(): Promise<MessageVO> {
    const id = requireSessionId()
    if (starting.value) {
      throw new BizError(10005, '正在开始面试，请稍候')
    }
    starting.value = true
    try {
      const firstMessage = await interviewApi.startInterview(id)
      // 立即给出首题，刷新失败时页面仍有内容可渲染。
      if (detail.value?.status === 'CREATED') {
        detail.value = { ...detail.value, status: 'WAITING_USER' }
      }
      if (!messages.value.some((message) => message.id === firstMessage.id)) {
        messages.value = [...messages.value, firstMessage]
      }
      invalidateInFlight()
      try {
        await refresh({ silent: true })
      } catch {
        // start 已成功；后续轮询/可见性恢复会继续对账
      }
      return firstMessage
    } finally {
      starting.value = false
    }
  }

  async function submitText(content: string): Promise<SubmitAccepted> {
    const id = requireSessionId()
    const current = detail.value
    if (!current || !canSubmit.value) {
      throw new BizError(10005, '当前状态不可作答，请等待最新状态')
    }
    const trimmed = content.trim()
    if (!trimmed) {
      throw new BizError(40001, '回答内容不能为空')
    }
    if (trimmed.length > 5000) {
      throw new BizError(40001, '回答不能超过 5000 字')
    }

    const roundNo = current.currentRound
    submitting.value = true
    submitError.value = null
    optimistic.value = {
      message: {
        id: -Date.now(),
        sessionId: id,
        roundNo,
        speaker: 'USER',
        inputType: 'TEXT',
        contentText: trimmed,
        createdAt: new Date().toISOString(),
      },
      state: 'pending',
    }

    try {
      const accepted = await interviewApi.submitTextAnswer(id, { roundNo, content: trimmed })
      if (optimistic.value) optimistic.value.state = 'sent'
      if (detail.value) {
        detail.value = { ...detail.value, status: 'PROCESSING_AI' }
      }
      aiThinking.value = true
      invalidateInFlight()
      try {
        await refresh({ silent: true })
      } catch {
        // 受理成功但刷新失败：轮询会继续对账，保留乐观气泡
      }
      return accepted
    } catch (error) {
      if (optimistic.value) optimistic.value.state = 'failed'
      submitError.value = error
      if (
        isBizError(error) &&
        (error.code === 10005 || error.code === 40002 || error.code === -1)
      ) {
        invalidateInFlight()
        try {
          await refresh({ silent: true })
        } catch {
          // 网络仍不可用，保留失败气泡与草稿
        }
      }
      if (detail.value?.status !== 'PROCESSING_AI') {
        aiThinking.value = false
      }
      throw error
    } finally {
      submitting.value = false
    }
  }

  /**
   * 音频回答（F4-3）：先做与后端同口径的本地校验，再 multipart 提交。
   * 成功后与文字回答共用"PROCESSING_AI → WS/轮询对账"路径；失败保留 blob 与转写由页面重试。
   */
  async function submitAudio(
    payload: Omit<AudioAnswerPayload, 'roundNo'>,
  ): Promise<SubmitAccepted> {
    const id = requireSessionId()
    const current = detail.value
    if (!current || !canSubmit.value) {
      throw new BizError(10005, '当前状态不可作答，请等待最新状态')
    }
    const problem = validateAudioAnswer({
      blob: payload.file,
      durationMs: payload.durationMs,
      asrText: payload.asrText,
    })
    if (problem) {
      throw new BizError(40001, problem)
    }

    const roundNo = current.currentRound
    const asrText = payload.asrText.trim()
    submitting.value = true
    submitError.value = null
    optimistic.value = {
      message: {
        id: -Date.now(),
        sessionId: id,
        roundNo,
        speaker: 'USER',
        inputType: 'AUDIO',
        contentText: asrText,
        asrText,
        audioDurationMs: payload.durationMs,
        createdAt: new Date().toISOString(),
      },
      state: 'pending',
    }

    try {
      const accepted = await interviewApi.submitAudioAnswer(id, {
        ...payload,
        asrText,
        roundNo,
      })
      if (optimistic.value) optimistic.value.state = 'sent'
      if (detail.value) {
        detail.value = { ...detail.value, status: 'PROCESSING_AI' }
      }
      aiThinking.value = true
      invalidateInFlight()
      try {
        await refresh({ silent: true })
      } catch {
        // 受理成功但刷新失败：轮询会继续对账并带出服务端音频消息
      }
      return accepted
    } catch (error) {
      if (optimistic.value) optimistic.value.state = 'failed'
      submitError.value = error
      if (
        isBizError(error) &&
        (error.code === 10005 || error.code === 40002 || error.code === -1)
      ) {
        invalidateInFlight()
        try {
          await refresh({ silent: true })
        } catch {
          // 网络仍不可用，保留失败气泡与录音
        }
      }
      if (detail.value?.status !== 'PROCESSING_AI') {
        aiThinking.value = false
      }
      throw error
    } finally {
      submitting.value = false
    }
  }

  async function finish(): Promise<FinishAccepted> {
    const id = requireSessionId()
    if (finishing.value) {
      throw new BizError(10005, '正在结束面试，请稍候')
    }
    finishing.value = true
    try {
      const accepted = await interviewApi.finishInterview(id)
      if (detail.value && accepted.status) {
        detail.value = { ...detail.value, status: accepted.status }
      }
      aiThinking.value = false
      invalidateInFlight()
      try {
        await refresh({ silent: true })
      } catch {
        // 终态已由受理响应确认；刷新失败由页面重试
      }
      return accepted
    } catch (error) {
      if (isBizError(error) && error.code === 10005) {
        invalidateInFlight()
        try {
          await refresh({ silent: true })
        } catch {
          // ignore
        }
      }
      throw error
    } finally {
      finishing.value = false
    }
  }

  async function abort(): Promise<InterviewDetailVO> {
    const id = requireSessionId()
    if (aborting.value) {
      throw new BizError(10005, '正在中止面试，请稍候')
    }
    aborting.value = true
    try {
      const nextDetail = await interviewApi.abortInterview(id)
      detail.value = nextDetail
      aiThinking.value = false
      invalidateInFlight()
      try {
        await refresh({ silent: true })
      } catch {
        // ignore
      }
      return nextDetail
    } finally {
      aborting.value = false
    }
  }

  /** WS 事件只做即时状态提示，最终以 refresh 的服务端数据为准（D32）。 */
  function applyWsEvent(event: WsEnvelope): void {
    lastWsEventAt.value = Date.now()
    switch (event.type) {
      case 'TIMER': {
        const payload = event.payload as TimerPayload
        if (detail.value && typeof payload.remainSeconds === 'number') {
          detail.value = { ...detail.value, remainSeconds: payload.remainSeconds }
        }
        return
      }
      case 'PROGRESS': {
        const payload = event.payload as ProgressPayload
        if (detail.value && payload.status) {
          detail.value = {
            ...detail.value,
            status: payload.status,
            currentRound: payload.currentRound ?? detail.value.currentRound,
            plannedRounds: payload.plannedRounds ?? detail.value.plannedRounds,
          }
          aiThinking.value = payload.status === 'PROCESSING_AI'
        }
        return
      }
      case 'QUESTION':
      case 'FOLLOW_UP': {
        const payload = event.payload as QuestionPayload
        if (detail.value) {
          detail.value = {
            ...detail.value,
            status: 'WAITING_USER',
            currentRound:
              typeof payload.roundNo === 'number' ? payload.roundNo : detail.value.currentRound,
          }
        }
        aiThinking.value = false
        return
      }
      case 'TRANSCRIPT': {
        const payload = event.payload as TranscriptPayload
        if (typeof payload.messageId === 'number' && typeof payload.asrText === 'string') {
          messages.value = messages.value.map((message) =>
            message.id === payload.messageId ? { ...message, asrText: payload.asrText } : message,
          )
        }
        return
      }
      case 'ERROR': {
        lastWsError.value = event.payload as ErrorPayload
        aiThinking.value = false
        return
      }
      case 'REPORT_READY': {
        const payload = event.payload as { reportId?: number }
        lastReportId.value = typeof payload.reportId === 'number' ? payload.reportId : null
        return
      }
      default:
        return
    }
  }

  function clearSubmitError(): void {
    submitError.value = null
  }

  function dismissOptimistic(): void {
    optimistic.value = null
  }

  return {
    sessionId,
    detail,
    messages,
    loaded,
    loading,
    refreshing,
    loadError,
    submitting,
    finishing,
    aborting,
    starting,
    submitError,
    aiThinking,
    optimistic,
    lastSyncedAt,
    lastWsEventAt,
    lastWsError,
    lastReportId,
    status,
    isTerminal,
    awaitingAnswer,
    canSubmit,
    displayRound,
    followupCount,
    currentQuestion,
    currentRoundMessages,
    answeredRounds,
    isBusy,
    reset,
    init,
    refresh,
    start,
    submitText,
    submitAudio,
    finish,
    abort,
    applyWsEvent,
    clearSubmitError,
    dismissOptimistic,
  }
})
