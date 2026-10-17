import { computed, nextTick, onScopeDispose, ref, watch, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { isBizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useInterviewStore } from '@/stores/interview'
import { useCountdown } from '@/composables/useCountdown'
import { useWebSocket } from '@/composables/useWebSocket'
import type { ErrorPayload, TimerPayload, WsEnvelope } from '@/types/ws'
import { notifyAuthExpired } from '@/utils/authSession'
import { ERROR_CODE_MESSAGES } from '@/utils/constants'
import {
  clearAnswerDraft,
  clearSessionDrafts,
  readAnswerDraft,
  writeAnswerDraft,
} from '@/utils/draft'
import { buildWsUrl } from '@/utils/wsUrl'

const AI_POLL_DELAYS = [1000, 2000, 3000]
const AI_POLL_TIMEOUT_MS = 60_000
const AI_SLOW_MS = 60_000
const FALLBACK_POLL_MS = 2000

function errorText(error: unknown): string {
  if (isBizError(error)) {
    switch (error.code) {
      case 10005:
        return '面试状态已更新，已为你刷新最新状态'
      case 40002:
        return error.message || '参数超出允许范围'
      case 40401:
        return '面试记录不存在或无权访问'
      case 40101:
        return '登录已过期，请重新登录'
      case 50001:
        return '服务器开小差了，请稍后重试'
      default:
        return error.message || ERROR_CODE_MESSAGES[error.code] || '操作失败，请稍后重试'
    }
  }
  return error instanceof Error && error.message ? error.message : '操作失败，请稍后重试'
}

/**
 * 房间编排（F2-4~F2-7）：
 * 加载 detail/messages → 连接 WS → CREATED 自动 start → 提交后轮询/WS 对账 →
 * 倒计时校准 → 终态收口。页面只负责渲染与确认弹窗。
 */
export function useInterviewRoom(sessionId: Ref<number>) {
  const router = useRouter()
  const auth = useAuthStore()
  const store = useInterviewStore()

  const countdown = useCountdown({ handleVisibility: false })
  const answerDraft = ref('')
  const submitError = ref('')
  const actionError = ref('')
  const startFailed = ref(false)
  const aiSlow = ref(false)
  const initialized = ref(false)

  let aiPollTimer: number | null = null
  let aiPollAttempt = 0
  let aiPollStartedAt = 0
  let fallbackTimer: number | null = null
  let aiSlowTimer: number | null = null
  let disposed = false

  const ws = useWebSocket({
    url: () => (sessionId.value ? buildWsUrl(sessionId.value, auth.token) : ''),
    onOpen: () => {
      stopFallbackPolling()
      if (initialized.value) void syncAndResume()
    },
    onEvent: handleWsEvent,
    onClose: (event, willRetry) => {
      if (event.code === 4401) return
      if (!willRetry && !store.isTerminal) {
        ElMessage.warning('实时连接已断开，已切换为轮询同步')
      }
    },
    onUnauthorized: () => {
      notifyAuthExpired()
    },
  })

  const wsState = ws.state
  const wsAttempt = ws.attempt
  const countdownRemain = countdown.remain
  const countdownExpired = countdown.expired
  const countdownWarning = countdown.isWarning

  const disabledHint = computed(() => {
    if (store.isTerminal) return '面试已结束'
    if (countdownExpired.value) return '已到时间，正在收卷'
    if (store.starting) return '正在开始面试…'
    if (store.submitting) return '正在提交回答…'
    if (store.aiThinking) return 'AI 正在分析上一条回答…'
    if (!store.awaitingAnswer) return '请等待 AI 提问'
    return ''
  })

  const canAnswer = computed(() => store.canSubmit && !countdownExpired.value && !store.isTerminal)

  const bubbles = computed(() => {
    const list = [...store.messages]
    if (store.optimistic) list.push(store.optimistic.message)
    return list
  })

  const terminalTitle = computed(() => {
    switch (store.detail?.status) {
      case 'FINISHED':
        return '面试已结束，报告生成中'
      case 'TIMEOUT':
        return '时间到，系统已自动收卷'
      case 'ABORTED':
        return '面试已中止'
      default:
        return '面试已结束'
    }
  })

  const terminalDescription = computed(() => {
    switch (store.detail?.status) {
      case 'FINISHED':
        return '报告生成后可查看总分、维度得分与逐题证据；现在可以离开房间。'
      case 'TIMEOUT':
        return '本次面试已按计划时长自动收卷，报告正在异步生成。'
      case 'ABORTED':
        return '本次面试不会生成报告，记录已标记为已中止。'
      default:
        return '当前面试已进入终态。'
    }
  })

  // ===== 轮询 =====

  function clearAiSlowTimer(): void {
    if (aiSlowTimer != null) {
      window.clearTimeout(aiSlowTimer)
      aiSlowTimer = null
    }
    aiSlow.value = false
  }

  function armAiSlowTimer(): void {
    clearAiSlowTimer()
    aiSlowTimer = window.setTimeout(() => {
      aiSlowTimer = null
      aiSlow.value = true
    }, AI_SLOW_MS)
  }

  function stopAiPolling(): void {
    if (aiPollTimer != null) {
      window.clearTimeout(aiPollTimer)
      aiPollTimer = null
    }
    aiPollAttempt = 0
    aiPollStartedAt = 0
  }

  function scheduleNextAiPoll(): void {
    if (disposed || store.isTerminal) {
      stopAiPolling()
      return
    }
    if (document.hidden) {
      stopAiPolling()
      return
    }
    const delay = AI_POLL_DELAYS[Math.min(aiPollAttempt, AI_POLL_DELAYS.length - 1)]
    aiPollAttempt += 1
    aiPollTimer = window.setTimeout(async () => {
      aiPollTimer = null
      if (disposed || store.isTerminal) {
        stopAiPolling()
        return
      }
      if (document.hidden) {
        stopAiPolling()
        return
      }
      await syncNow()
      if (disposed || store.isTerminal) {
        stopAiPolling()
        return
      }
      if (
        store.detail?.status === 'PROCESSING_AI' &&
        Date.now() - aiPollStartedAt < AI_POLL_TIMEOUT_MS
      ) {
        scheduleNextAiPoll()
      } else {
        stopAiPolling()
        if (wsState.value !== 'open') startFallbackPolling()
      }
    }, delay)
  }

  function startAiPolling(): void {
    if (disposed || store.isTerminal || aiPollTimer != null) return
    aiPollAttempt = 0
    aiPollStartedAt = Date.now()
    armAiSlowTimer()
    scheduleNextAiPoll()
  }

  function startFallbackPolling(): void {
    if (disposed || store.isTerminal || fallbackTimer != null) return
    fallbackTimer = window.setInterval(() => {
      void fallbackTick()
    }, FALLBACK_POLL_MS)
  }

  function stopFallbackPolling(): void {
    if (fallbackTimer != null) {
      window.clearInterval(fallbackTimer)
      fallbackTimer = null
    }
  }

  async function fallbackTick(): Promise<void> {
    if (disposed || store.isTerminal) {
      stopFallbackPolling()
      return
    }
    if (document.hidden) return
    if (aiPollTimer != null) return
    await syncNow()
  }

  // ===== 对账 =====

  function applySyncedState(): void {
    const detail = store.detail
    if (!detail) return
    if (detail.remainSeconds != null) {
      countdown.sync(detail.remainSeconds)
    }
    if (store.isTerminal) {
      handleTerminal()
      return
    }
    if (detail.status !== 'PROCESSING_AI') {
      clearAiSlowTimer()
      stopAiPolling()
      if (store.awaitingAnswer) loadDraft()
    }
    if (wsState.value !== 'open') startFallbackPolling()
  }

  async function syncNow(): Promise<void> {
    if (disposed || !store.sessionId) return
    try {
      await store.refresh({ silent: true })
    } catch (error) {
      if (isBizError(error) && error.code === 40401) {
        stopAiPolling()
        stopFallbackPolling()
        ws.close()
      }
      return
    }
    applySyncedState()
  }

  async function syncAndResume(): Promise<void> {
    await syncNow()
    if (store.detail?.status === 'PROCESSING_AI' && !store.isTerminal) {
      startAiPolling()
    }
  }

  // ===== WS 事件 =====

  function handleWsEvent(event: WsEnvelope): void {
    store.applyWsEvent(event)
    switch (event.type) {
      case 'TIMER': {
        const payload = event.payload as TimerPayload
        if (typeof payload.remainSeconds === 'number') {
          countdown.sync(payload.remainSeconds)
        }
        return
      }
      case 'ERROR': {
        const payload = event.payload as ErrorPayload
        if (payload.message) {
          actionError.value = payload.message
          ElMessage.warning(payload.message)
        }
        void syncNow()
        return
      }
      case 'QUESTION':
      case 'FOLLOW_UP':
      case 'TRANSCRIPT':
      case 'PROGRESS': {
        stopAiPolling()
        void syncAndResume()
        return
      }
      case 'REPORT_READY':
      default:
        return
    }
  }

  // ===== 生命周期动作 =====

  function loadDraft(): void {
    if (!store.detail || store.isTerminal) return
    if (answerDraft.value) return
    const saved = readAnswerDraft(store.detail.id, store.detail.currentRound)
    if (saved) answerDraft.value = saved
  }

  async function startSession(): Promise<void> {
    startFailed.value = false
    actionError.value = ''
    if (store.detail?.status !== 'CREATED') {
      await syncAndResume()
      return
    }
    try {
      await store.start()
      await syncAndResume()
    } catch (error) {
      if (isBizError(error) && error.code === 10005) {
        await syncAndResume()
        if (store.detail && store.detail.status !== 'CREATED') return
      }
      startFailed.value = true
      actionError.value = errorText(error)
    }
  }

  async function initialize(): Promise<void> {
    if (disposed) return
    initialized.value = false
    startFailed.value = false
    actionError.value = ''
    submitError.value = ''
    ws.close()
    stopAiPolling()
    stopFallbackPolling()
    clearAiSlowTimer()
    countdown.reset()

    try {
      await store.init(sessionId.value)
    } catch {
      return
    }
    if (!store.detail) return
    initialized.value = true

    if (store.detail.remainSeconds != null) {
      countdown.start(store.detail.remainSeconds)
    }
    if (store.isTerminal) {
      handleTerminal()
      return
    }

    ws.connect()
    if (store.detail.status === 'CREATED') {
      await startSession()
    } else {
      if (store.detail.status === 'PROCESSING_AI') startAiPolling()
      if (store.awaitingAnswer) loadDraft()
    }
  }

  async function retryLoad(): Promise<void> {
    await initialize()
  }

  async function retryStart(): Promise<void> {
    await startSession()
  }

  async function submitAnswer(): Promise<void> {
    if (!canAnswer.value || !answerDraft.value.trim()) return
    submitError.value = ''
    actionError.value = ''
    const roundNo = store.detail?.currentRound ?? 0
    try {
      await store.submitText(answerDraft.value)
      if (store.sessionId != null) clearAnswerDraft(store.sessionId, roundNo)
      answerDraft.value = ''
      if (store.detail?.status === 'PROCESSING_AI') startAiPolling()
      if (wsState.value !== 'open') startFallbackPolling()
    } catch (error) {
      submitError.value = errorText(error)
      if (isBizError(error) && (error.code === 10005 || error.code === 40002)) {
        await syncAndResume()
      }
    }
  }

  async function finishRoom(): Promise<void> {
    actionError.value = ''
    try {
      const accepted = await store.finish()
      if (store.sessionId != null && store.detail) {
        clearSessionDrafts(store.sessionId, store.detail.plannedRounds)
      }
      answerDraft.value = ''
      handleTerminal()
      ElMessage.success(accepted.status === 'FINISHED' ? '面试已结束，报告正在生成' : '面试已结束')
    } catch (error) {
      actionError.value = errorText(error)
      if (isBizError(error) && error.code === 10005) await syncAndResume()
    }
  }

  async function abortRoom(): Promise<void> {
    actionError.value = ''
    try {
      await store.abort()
      if (store.sessionId != null && store.detail) {
        clearSessionDrafts(store.sessionId, store.detail.plannedRounds)
      }
      answerDraft.value = ''
      handleTerminal()
      ElMessage.success('面试已中止，本次不生成报告')
    } catch (error) {
      actionError.value = errorText(error)
      if (isBizError(error) && error.code === 10005) await syncAndResume()
    }
  }

  function handleTerminal(): void {
    if (!store.detail) return
    countdown.stop()
    stopAiPolling()
    stopFallbackPolling()
    clearAiSlowTimer()
    ws.close()
    if (store.sessionId != null) {
      clearSessionDrafts(store.sessionId, store.detail.plannedRounds)
    }
  }

  async function leaveRoom(): Promise<void> {
    if (store.isTerminal) {
      await router.push('/history')
      return
    }
    try {
      await ElMessageBox.confirm(
        '离开房间不会中止面试；服务端会继续计时，你可以从历史记录回到本场面试。',
        '离开面试房间',
        {
          type: 'warning',
          confirmButtonText: '离开房间',
          cancelButtonText: '继续作答',
        },
      )
    } catch {
      return
    }
    await router.push('/history')
  }

  function reconnect(): void {
    ws.reconnect()
  }

  function focusAnswer(): void {
    void nextTick(() => {
      const el = document.querySelector<HTMLTextAreaElement>('[data-testid="answer-input"]')
      el?.focus()
    })
  }

  // ===== 可见性 / 卸载 =====

  function handleVisibility(): void {
    if (document.visibilityState !== 'visible') {
      stopAiPolling()
      stopFallbackPolling()
      return
    }
    if (!initialized.value || store.isTerminal) return
    void syncAndResume()
    if (wsState.value !== 'open') startFallbackPolling()
  }

  function handleBeforeUnload(event: BeforeUnloadEvent): void {
    if (store.submitting || store.finishing) {
      event.preventDefault()
      event.returnValue = ''
    }
  }

  watch(answerDraft, (value) => {
    if (!initialized.value || !store.detail || store.isTerminal) return
    writeAnswerDraft(store.detail.id, store.detail.currentRound, value)
  })

  watch(
    () => store.detail?.currentRound,
    () => {
      if (!store.awaitingAnswer) return
      submitError.value = ''
      loadDraft()
    },
  )

  watch(
    () => store.isTerminal,
    (terminal) => {
      if (terminal) handleTerminal()
    },
  )

  watch(wsState, (state) => {
    if (disposed || !initialized.value) return
    if (state === 'open') {
      stopFallbackPolling()
      return
    }
    if (state === 'reconnecting' || state === 'closed') {
      if (store.isTerminal) return
      if (ws.lastCloseCode.value === 4401) return
      startFallbackPolling()
    }
  })

  document.addEventListener('visibilitychange', handleVisibility)
  window.addEventListener('beforeunload', handleBeforeUnload)

  onScopeDispose(() => {
    disposed = true
    stopAiPolling()
    stopFallbackPolling()
    clearAiSlowTimer()
    countdown.stop()
    document.removeEventListener('visibilitychange', handleVisibility)
    window.removeEventListener('beforeunload', handleBeforeUnload)
  })

  return {
    store,
    wsState,
    wsAttempt,
    lastCloseCode: ws.lastCloseCode,
    countdownRemain,
    countdownExpired,
    countdownWarning,
    answerDraft,
    submitError,
    actionError,
    startFailed,
    aiSlow,
    disabledHint,
    canAnswer,
    bubbles,
    terminalTitle,
    terminalDescription,
    initialize,
    retryLoad,
    retryStart,
    submitAnswer,
    finishRoom,
    abortRoom,
    leaveRoom,
    reconnect,
    focusAnswer,
    syncNow,
  }
}
