import { effectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useWebSocket } from '@/composables/useWebSocket'
import type { WsEnvelope } from '@/types/ws'

class FakeSocket {
  static instances: FakeSocket[] = []
  static CONNECTING = 0
  static OPEN = 1
  static CLOSING = 2
  static CLOSED = 3

  url: string
  readyState = 0
  onopen: ((event: Event) => void) | null = null
  onmessage: ((event: MessageEvent) => void) | null = null
  onerror: ((event: Event) => void) | null = null
  onclose: ((event: CloseEvent) => void) | null = null

  constructor(url: string | URL) {
    this.url = String(url)
    FakeSocket.instances.push(this)
  }

  open(): void {
    this.readyState = 1
    this.onopen?.(new Event('open'))
  }

  message(payload: WsEnvelope): void {
    this.onmessage?.({ data: JSON.stringify(payload) } as MessageEvent)
  }

  rawMessage(data: string): void {
    this.onmessage?.({ data } as MessageEvent)
  }

  serverClose(code = 1006): void {
    this.readyState = 3
    this.onclose?.({ code } as CloseEvent)
  }

  close(): void {
    this.readyState = 3
    this.onclose?.({ code: 1000 } as CloseEvent)
  }
}

function setup(options: Partial<Parameters<typeof useWebSocket>[0]> = {}) {
  const onOpen = vi.fn()
  const onEvent = vi.fn()
  const onClose = vi.fn()
  const onUnauthorized = vi.fn()
  const scope = effectScope()
  const ws = scope.run(() =>
    useWebSocket({
      url: () => 'ws://example.test/ws/interviews/1?token=t',
      onOpen,
      onEvent,
      onClose,
      onUnauthorized,
      ...options,
    }),
  )!
  return { ws, scope, onOpen, onEvent, onClose, onUnauthorized }
}

describe('useWebSocket', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    FakeSocket.instances = []
    vi.stubGlobal('WebSocket', FakeSocket)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('连接成功后状态变为 open 并分发合法事件', () => {
    const { ws, scope, onOpen, onEvent } = setup()
    ws.connect()
    expect(FakeSocket.instances).toHaveLength(1)
    expect(ws.state.value).toBe('connecting')
    expect(FakeSocket.instances[0].url).toContain('/ws/interviews/1')

    FakeSocket.instances[0].open()
    expect(ws.state.value).toBe('open')
    expect(onOpen).toHaveBeenCalledTimes(1)

    const envelope: WsEnvelope = {
      type: 'TIMER',
      payload: { remainSeconds: 120 },
      ts: Date.now(),
    }
    FakeSocket.instances[0].message(envelope)
    expect(onEvent).toHaveBeenCalledWith(envelope)
    scope.stop()
  })

  it('非法 JSON / 未知 type / 回调异常不会破坏连接', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const onEvent = vi.fn(() => {
      throw new Error('handler boom')
    })
    const { ws, scope } = setup({ onEvent })
    ws.connect()
    const socket = FakeSocket.instances[0]
    socket.open()

    socket.rawMessage('{not-json')
    socket.message({ type: 'UNKNOWN' as WsEnvelope['type'], payload: {}, ts: 1 })
    socket.message({ type: 'QUESTION', payload: { roundNo: 0 }, ts: 1 })
    expect(warn).toHaveBeenCalled()
    expect(error).toHaveBeenCalled()
    expect(ws.state.value).toBe('open')
    scope.stop()
  })

  it('异常断开后指数退避重连，成功后重置计数', () => {
    const { ws, scope, onClose } = setup({ delays: [1000, 2000] })
    ws.connect()
    FakeSocket.instances[0].open()
    FakeSocket.instances[0].serverClose(1006)
    expect(ws.state.value).toBe('reconnecting')
    expect(ws.attempt.value).toBe(1)
    expect(onClose).toHaveBeenCalledWith(expect.objectContaining({ code: 1006 }), true)

    vi.advanceTimersByTime(1000)
    expect(FakeSocket.instances).toHaveLength(2)
    FakeSocket.instances[1].open()
    expect(ws.state.value).toBe('open')
    expect(ws.attempt.value).toBe(0)
    scope.stop()
  })

  it('4401 鉴权失败只回调一次且不再重连', () => {
    const { ws, scope, onUnauthorized } = setup()
    ws.connect()
    FakeSocket.instances[0].serverClose(4401)
    expect(ws.state.value).toBe('closed')
    expect(onUnauthorized).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(60_000)
    expect(FakeSocket.instances).toHaveLength(1)
    scope.stop()
  })

  it('达到最大重连次数后停止', () => {
    const { ws, scope } = setup({ maxAttempts: 1, delays: [1000] })
    ws.connect()
    FakeSocket.instances[0].open()
    FakeSocket.instances[0].serverClose(1006)
    expect(ws.state.value).toBe('reconnecting')

    vi.advanceTimersByTime(1000)
    expect(FakeSocket.instances).toHaveLength(2)
    FakeSocket.instances[1].serverClose(1006)
    expect(ws.state.value).toBe('closed')

    vi.advanceTimersByTime(10_000)
    expect(FakeSocket.instances).toHaveLength(2)
    scope.stop()
  })

  it('手动 close 后不再自动重连', () => {
    const { ws, scope } = setup()
    ws.connect()
    FakeSocket.instances[0].open()
    ws.close()
    expect(ws.state.value).toBe('closed')
    vi.advanceTimersByTime(30_000)
    expect(FakeSocket.instances).toHaveLength(1)
    scope.stop()
  })

  it('URL 为空时直接关闭', () => {
    const { ws, scope } = setup({ url: () => '' })
    ws.connect()
    expect(ws.state.value).toBe('closed')
    expect(FakeSocket.instances).toHaveLength(0)
    scope.stop()
  })
})
