import { computed, onScopeDispose, ref } from 'vue'

export interface UseCountdownOptions {
  /** 页面从后台恢复时回调，由房间重新拉取 detail 校准（计划 §5.7.6）。 */
  onResume?: () => void
  /** 警示阈值，默认 60 秒。 */
  warningThreshold?: number
  /** 是否自动监听 visibilitychange，默认 true。 */
  handleVisibility?: boolean
}

/**
 * 倒计时：本地每秒递减，收到服务端 TIMER/detail.remainSeconds 时用 sync 校准。
 * - 不用差值累加，sync 直接覆盖；
 * - 到 0 停止并触发 expired；
 * - 卸载/重置时清理 interval 与 visibilitychange 监听。
 */
export function useCountdown(options: UseCountdownOptions = {}) {
  const warningThreshold = options.warningThreshold ?? 60
  const remain = ref<number | null>(null)
  const running = ref(false)
  let timer: number | null = null

  const expired = computed(() => remain.value != null && remain.value <= 0)
  const isWarning = computed(
    () => remain.value != null && remain.value > 0 && remain.value <= warningThreshold,
  )

  function clearTimer(): void {
    if (timer != null) {
      window.clearInterval(timer)
      timer = null
    }
  }

  function tick(): void {
    if (remain.value == null) return
    if (remain.value <= 0) {
      remain.value = 0
      running.value = false
      clearTimer()
      return
    }
    remain.value -= 1
    if (remain.value <= 0) {
      remain.value = 0
      running.value = false
      clearTimer()
    }
  }

  function ensureTimer(): void {
    if (timer != null) return
    timer = window.setInterval(tick, 1000)
  }

  function start(seconds?: number | null): void {
    if (seconds == null || !Number.isFinite(seconds)) {
      reset()
      return
    }
    remain.value = Math.max(0, Math.floor(seconds))
    running.value = remain.value > 0
    clearTimer()
    if (running.value) ensureTimer()
  }

  /** 服务端校准：只覆盖剩余秒数，不重置已有 interval。 */
  function sync(seconds?: number | null): void {
    if (seconds == null || !Number.isFinite(seconds)) return
    remain.value = Math.max(0, Math.floor(seconds))
    running.value = remain.value > 0
    if (running.value) {
      ensureTimer()
    } else {
      clearTimer()
    }
  }

  function stop(): void {
    running.value = false
    clearTimer()
  }

  function reset(): void {
    stop()
    remain.value = null
  }

  function handleVisibility(): void {
    if (document.visibilityState !== 'visible') return
    // 浏览器会节流后台定时器；恢复前台时交由页面重新对账。
    options.onResume?.()
  }

  if (options.handleVisibility !== false) {
    document.addEventListener('visibilitychange', handleVisibility)
  }

  onScopeDispose(() => {
    stop()
    document.removeEventListener('visibilitychange', handleVisibility)
  })

  return {
    remain,
    running,
    expired,
    isWarning,
    warningThreshold,
    start,
    sync,
    stop,
    reset,
  }
}
