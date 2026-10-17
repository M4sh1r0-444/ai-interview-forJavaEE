import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { isBizError } from '@/api/http'
import { finishInterview, getReport } from '@/api/interview'
import type { ReportVO } from '@/types/report'

/** 10004 轮询节奏：1s、2s、3s 后每 3s 一次（计划 §7.7）。 */
export const REPORT_POLL_DELAYS = [1000, 2000, 3000] as const
/** 生成较慢阈值：超过后停止轮询并允许"重新生成"（计划 §5.8）。 */
export const REPORT_POLL_MAX_MS = 60_000

/**
 * 报告唯一事实源（计划 §6.4）：
 * - 缓存按 sessionId 复用，返回报告页不重复加载；
 * - 10004 是正常态：自动轮询（页面隐藏暂停），60s 未就绪转"生成较慢"；
 * - 重新生成 = POST finish（后端幂等，已存在报告直接返回 reportId）；
 * - 40401 / 其他错误落到 error，由页面展示空态或错误卡。
 */
export const useReportStore = defineStore('report', () => {
  const cache = ref<Map<number, ReportVO>>(new Map())
  const sessionId = ref<number | null>(null)
  const loading = ref(false)
  const polling = ref(false)
  const paused = ref(false)
  const retrying = ref(false)
  const pending = ref(false)
  const timedOut = ref(false)
  const error = ref<unknown>(null)
  const pollAttempt = ref(0)
  const pollStartedAt = ref(0)

  let timer: number | null = null
  let pollToken = 0
  let requestSeq = 0

  const report = computed<ReportVO | null>(() => {
    if (sessionId.value == null) return null
    return cache.value.get(sessionId.value) ?? null
  })
  const hasReport = computed(() => report.value != null)
  const errorCode = computed<number | null>(() =>
    isBizError(error.value) ? error.value.code : null,
  )

  function setCache(id: number, value: ReportVO): void {
    const next = new Map(cache.value)
    next.set(id, value)
    cache.value = next
  }

  function removeCache(id: number): void {
    if (!cache.value.has(id)) return
    const next = new Map(cache.value)
    next.delete(id)
    cache.value = next
  }

  function clearTimer(): void {
    if (timer != null) {
      window.clearTimeout(timer)
      timer = null
    }
  }

  function stopPolling(): void {
    clearTimer()
    pollToken += 1
    polling.value = false
    paused.value = false
  }

  function resetFlags(): void {
    pending.value = false
    timedOut.value = false
    error.value = null
    pollAttempt.value = 0
    pollStartedAt.value = 0
  }

  function reset(): void {
    stopPolling()
    sessionId.value = null
    loading.value = false
    retrying.value = false
    resetFlags()
    requestSeq += 1
  }

  function clear(id: number): void {
    removeCache(id)
    if (sessionId.value === id) reset()
  }

  /** 登出/切换账号时清空所有缓存，避免跨账号复用同一 sessionId 的报告。 */
  function clearAll(): void {
    cache.value = new Map()
    reset()
  }

  async function fetchOnce(
    id: number,
    options: { silent?: boolean } = {},
  ): Promise<ReportVO | null> {
    const seq = ++requestSeq
    if (!options.silent) loading.value = true
    try {
      const data = await getReport(id)
      if (seq !== requestSeq || sessionId.value !== id) return null
      setCache(id, data)
      pending.value = false
      timedOut.value = false
      error.value = null
      stopPolling()
      return data
    } catch (cause) {
      if (seq !== requestSeq || sessionId.value !== id) return null
      if (isBizError(cause) && cause.code === 10004) {
        // 报告尚未生成：保留轮询，不当作失败。
        pending.value = true
        error.value = null
        return null
      }
      error.value = cause
      stopPolling()
      return null
    } finally {
      if (seq === requestSeq) loading.value = false
    }
  }

  function scheduleNext(id: number, token: number): void {
    if (!polling.value || token !== pollToken) return
    if (typeof document !== 'undefined' && document.hidden) {
      paused.value = true
      return
    }
    const index = Math.min(pollAttempt.value, REPORT_POLL_DELAYS.length - 1)
    const delay = REPORT_POLL_DELAYS[index] ?? 3000
    pollAttempt.value += 1
    timer = window.setTimeout(() => {
      timer = null
      void pollTick(id, token)
    }, delay)
  }

  async function pollTick(id: number, token: number): Promise<void> {
    if (!polling.value || token !== pollToken) return
    if (typeof document !== 'undefined' && document.hidden) {
      paused.value = true
      return
    }
    if (Date.now() - pollStartedAt.value >= REPORT_POLL_MAX_MS) {
      timedOut.value = true
      stopPolling()
      return
    }
    await fetchOnce(id, { silent: true })
    if (!polling.value || token !== pollToken) return
    if (pending.value) scheduleNext(id, token)
  }

  function startPolling(id: number, immediate = false): void {
    if (sessionId.value !== id) return
    stopPolling()
    polling.value = true
    paused.value = false
    pollAttempt.value = 0
    pollStartedAt.value = Date.now()
    const token = pollToken
    if (immediate) {
      void pollTick(id, token)
      return
    }
    scheduleNext(id, token)
  }

  /**
   * 加载报告：已有缓存时直接复用（force=true 强制重拉）。
   * 返回报告；10004 返回 null 并自动开始轮询，其他错误写入 error 并返回 null。
   */
  async function load(
    id: number,
    options: { force?: boolean; silent?: boolean } = {},
  ): Promise<ReportVO | null> {
    if (sessionId.value !== id) {
      stopPolling()
      sessionId.value = id
      resetFlags()
    }
    if (!options.force) {
      const cached = cache.value.get(id)
      if (cached) {
        stopPolling()
        pending.value = false
        timedOut.value = false
        error.value = null
        return cached
      }
    }
    if (options.force || !cache.value.has(id)) timedOut.value = false

    const data = await fetchOnce(id, { silent: options.silent })
    if (!data && pending.value && !polling.value && !timedOut.value) {
      startPolling(id)
    }
    return data
  }

  /** 页面恢复可见：若有未完成轮询，立即补一次请求（计划 §7.7）。 */
  function resumePolling(): void {
    const id = sessionId.value
    if (id == null || !polling.value || !pending.value) return
    if (typeof document !== 'undefined' && document.hidden) return
    paused.value = false
    if (Date.now() - pollStartedAt.value >= REPORT_POLL_MAX_MS) {
      timedOut.value = true
      stopPolling()
      return
    }
    if (timer == null) {
      const token = pollToken
      void pollTick(id, token)
    }
  }

  /** WS REPORT_READY 到达时立即拉取一次（仍以 REST 为事实源）。 */
  async function refreshNow(id: number): Promise<ReportVO | null> {
    if (sessionId.value !== id) return null
    const data = await fetchOnce(id, { silent: true })
    if (!data && pending.value && !polling.value && !timedOut.value) {
      startPolling(id)
    }
    return data
  }

  /**
   * 重新生成报告：POST finish（幂等）→ 清缓存 → 立即拉取并重新轮询。
   * 返回 true 表示已受理（不代表报告已生成）。
   */
  async function retry(id: number): Promise<boolean> {
    if (retrying.value) return false
    if (sessionId.value !== id) {
      stopPolling()
      sessionId.value = id
    }
    retrying.value = true
    error.value = null
    try {
      await finishInterview(id)
      removeCache(id)
      pending.value = true
      timedOut.value = false
      startPolling(id, true)
      return true
    } catch (cause) {
      error.value = cause
      stopPolling()
      return false
    } finally {
      retrying.value = false
    }
  }

  return {
    cache,
    sessionId,
    loading,
    polling,
    paused,
    retrying,
    pending,
    timedOut,
    error,
    pollAttempt,
    pollStartedAt,
    report,
    hasReport,
    errorCode,
    load,
    fetchOnce,
    refreshNow,
    retry,
    resumePolling,
    startPolling,
    stopPolling,
    clear,
    clearAll,
    reset,
  }
})
