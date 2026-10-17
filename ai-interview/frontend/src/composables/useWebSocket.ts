import { onScopeDispose, ref } from 'vue'

import type { WsConnectionState, WsEnvelope, WsEventType } from '@/types/ws'

const KNOWN_EVENT_TYPES: ReadonlySet<string> = new Set<WsEventType>([
  'QUESTION',
  'FOLLOW_UP',
  'TRANSCRIPT',
  'TIMER',
  'PROGRESS',
  'REPORT_READY',
  'ERROR',
])

export interface UseWebSocketOptions {
  /** 惰性 URL：凭 token 变化或会话 id 就绪后再连接。 */
  url: () => string
  /** 最大自动重连次数（默认 8）。 */
  maxAttempts?: number
  /** 指数退避序列，默认 1/2/4/8/15s；超出后循环使用最后一档。 */
  delays?: number[]
  onOpen?: () => void
  onEvent?: (event: WsEnvelope) => void
  /** willRetry=false 表示不会自动重连（手动关闭 / 4401 / 次数用尽）。 */
  onClose?: (event: CloseEvent, willRetry: boolean) => void
  /** close code 4401：登录失效，由 App 清 auth 并跳登录。 */
  onUnauthorized?: () => void
}

function isWsEnvelope(value: unknown): value is WsEnvelope {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<WsEnvelope>
  if (typeof candidate.type !== 'string' || !KNOWN_EVENT_TYPES.has(candidate.type)) return false
  if (!candidate.payload || typeof candidate.payload !== 'object') return false
  return true
}

/**
 * 原生 WebSocket 封装（前端计划 §5.7.6 / §7.6）：
 * - 指数退避 1/2/4/8/15s，最多 8 次；
 * - 4401 不重连，交给全局登录失效处理；
 * - 终态由调用方 close()，不重连；
 * - 非法 JSON / 未知 type 只告警，不断开连接；
 * - 事件回调异常被捕获，不影响后续事件。
 */
export function useWebSocket(options: UseWebSocketOptions) {
  const state = ref<WsConnectionState>('idle')
  const attempt = ref(0)
  const lastCloseCode = ref<number | null>(null)

  const maxAttempts = options.maxAttempts ?? 8
  const delays = options.delays ?? [1000, 2000, 4000, 8000, 15000]

  let socket: WebSocket | null = null
  let retryTimer: number | null = null
  let manualClosed = false
  let disposed = false

  function clearRetry(): void {
    if (retryTimer != null) {
      window.clearTimeout(retryTimer)
      retryTimer = null
    }
  }

  function detachSocket(): void {
    if (!socket) return
    const current = socket
    socket = null
    current.onopen = null
    current.onmessage = null
    current.onerror = null
    current.onclose = null
    if (current.readyState === 0 || current.readyState === 1) {
      try {
        current.close(1000, 'client closed')
      } catch {
        // 连接已断开
      }
    }
  }

  function connect(): void {
    if (disposed) return
    clearRetry()
    manualClosed = false
    detachSocket()

    let url = ''
    try {
      url = options.url()
    } catch {
      state.value = 'closed'
      return
    }
    if (!url) {
      state.value = 'closed'
      return
    }

    state.value = attempt.value > 0 ? 'reconnecting' : 'connecting'
    let next: WebSocket
    try {
      next = new WebSocket(url)
    } catch {
      scheduleReconnect()
      return
    }
    socket = next

    next.onopen = () => {
      if (socket !== next) return
      attempt.value = 0
      state.value = 'open'
      options.onOpen?.()
    }

    next.onmessage = (event: MessageEvent) => {
      if (socket !== next) return
      let parsed: unknown
      try {
        parsed = JSON.parse(String(event.data))
      } catch {
        console.warn('[ws] 忽略非法 JSON 消息')
        return
      }
      if (!isWsEnvelope(parsed)) {
        console.warn('[ws] 忽略未知事件类型')
        return
      }
      try {
        options.onEvent?.(parsed)
      } catch (error) {
        console.error('[ws] 事件处理异常', error)
      }
    }

    next.onerror = () => {
      // 浏览器随后会触发 onclose，重连逻辑统一在 onclose 中处理
    }

    next.onclose = (event: CloseEvent) => {
      if (socket !== next) return
      socket = null
      lastCloseCode.value = event.code

      if (disposed || manualClosed) {
        state.value = 'closed'
        options.onClose?.(event, false)
        return
      }
      if (event.code === 4401) {
        state.value = 'closed'
        options.onUnauthorized?.()
        options.onClose?.(event, false)
        return
      }
      if (attempt.value >= maxAttempts) {
        state.value = 'closed'
        options.onClose?.(event, false)
        return
      }
      options.onClose?.(event, true)
      scheduleReconnect()
    }
  }

  function scheduleReconnect(): void {
    if (disposed) return
    clearRetry()
    const index = Math.min(attempt.value, delays.length - 1)
    const delay = Math.max(0, delays[index] ?? 1000)
    attempt.value += 1
    state.value = 'reconnecting'
    retryTimer = window.setTimeout(() => {
      retryTimer = null
      connect()
    }, delay)
  }

  /** 手动重连：重置退避计数。 */
  function reconnect(): void {
    attempt.value = 0
    connect()
  }

  /** 主动关闭（终态/卸载），不会触发自动重连。 */
  function close(): void {
    manualClosed = true
    clearRetry()
    detachSocket()
    state.value = 'closed'
  }

  onScopeDispose(() => {
    disposed = true
    close()
  })

  return {
    state,
    attempt,
    lastCloseCode,
    connect,
    reconnect,
    close,
  }
}
