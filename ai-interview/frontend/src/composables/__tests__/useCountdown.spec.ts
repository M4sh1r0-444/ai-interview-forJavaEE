import { effectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useCountdown } from '@/composables/useCountdown'

describe('useCountdown', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('每秒递减并在 0 停止', () => {
    const scope = effectScope()
    const countdown = scope.run(() => useCountdown({ handleVisibility: false }))!
    countdown.start(3)
    expect(countdown.remain.value).toBe(3)
    expect(countdown.running.value).toBe(true)

    vi.advanceTimersByTime(1000)
    expect(countdown.remain.value).toBe(2)
    vi.advanceTimersByTime(2000)
    expect(countdown.remain.value).toBe(0)
    expect(countdown.expired.value).toBe(true)
    expect(countdown.running.value).toBe(false)

    vi.advanceTimersByTime(3000)
    expect(countdown.remain.value).toBe(0)
    scope.stop()
  })

  it('sync 用服务端值校准且不重复启动 interval', () => {
    const scope = effectScope()
    const countdown = scope.run(() => useCountdown({ handleVisibility: false }))!
    countdown.start(30)
    vi.advanceTimersByTime(1000)
    countdown.sync(120)
    expect(countdown.remain.value).toBe(120)
    vi.advanceTimersByTime(1000)
    expect(countdown.remain.value).toBe(119)

    countdown.sync(10)
    vi.advanceTimersByTime(1000)
    expect(countdown.remain.value).toBe(9)
    scope.stop()
  })

  it('stop 后不再递减，isWarning 在阈值内为真', () => {
    const scope = effectScope()
    const countdown = scope.run(() => useCountdown({ handleVisibility: false }))!
    countdown.start(120)
    expect(countdown.isWarning.value).toBe(false)
    countdown.sync(60)
    expect(countdown.isWarning.value).toBe(true)
    countdown.stop()
    vi.advanceTimersByTime(5000)
    expect(countdown.remain.value).toBe(60)
    scope.stop()
  })

  it('页面恢复可见时触发 onResume 回调', () => {
    const onResume = vi.fn()
    const scope = effectScope()
    scope.run(() => useCountdown({ onResume }))
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => 'visible',
    })
    document.dispatchEvent(new Event('visibilitychange'))
    expect(onResume).toHaveBeenCalledTimes(1)
    scope.stop()
  })

  it('非法值与 reset 清空显示', () => {
    const scope = effectScope()
    const countdown = scope.run(() => useCountdown({ handleVisibility: false }))!
    countdown.start(Number.NaN)
    expect(countdown.remain.value).toBeNull()
    countdown.start(10)
    countdown.reset()
    expect(countdown.remain.value).toBeNull()
    expect(countdown.running.value).toBe(false)
    scope.stop()
  })
})
