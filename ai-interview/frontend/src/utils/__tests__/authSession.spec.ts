import { describe, expect, it, vi } from 'vitest'

import type { PersistedAuth, UserVO } from '@/types/auth'
import {
  AUTH_EXPIRED_EVENT,
  clearSession,
  getSession,
  getToken,
  isSessionExpired,
  notifyAuthExpired,
  onAuthExpired,
  resetAuthExpiredFlag,
  setSession,
} from '@/utils/authSession'
import { storageKey } from '@/utils/storage'

const user: UserVO = {
  id: 2,
  username: 'student',
  role: 'STUDENT',
  status: 1,
}

function session(overrides: Partial<PersistedAuth> = {}): PersistedAuth {
  return {
    token: 'jwt-token',
    expiresAt: Date.now() + 60_000,
    user,
    ...overrides,
  }
}

describe('authSession', () => {
  it('读写会话并使用统一命名空间', () => {
    const value = session()
    setSession(value)

    expect(getSession()).toEqual(value)
    expect(getToken()).toBe('jwt-token')
    expect(localStorage.getItem(storageKey('auth'))).toBeTruthy()
    expect(localStorage.getItem('ai-interview:auth')).toBeTruthy()
  })

  it('损坏的 JSON 或缺失字段返回 null', () => {
    localStorage.setItem(storageKey('auth'), '{not-json')
    expect(getSession()).toBeNull()

    localStorage.setItem(
      storageKey('auth'),
      JSON.stringify({ token: 't', expiresAt: 1, user: { id: 1 } }),
    )
    expect(getSession()).toBeNull()
  })

  it('过期判断与清除', () => {
    const expired = session({ expiresAt: Date.now() - 1 })
    expect(isSessionExpired(expired)).toBe(true)
    setSession(session({ expiresAt: Date.now() + 10_000 }))
    clearSession()
    expect(getSession()).toBeNull()
  })

  it('并发 401 只派发一次事件，重新登录后复位', () => {
    const listener = vi.fn()
    const unsubscribe = onAuthExpired(listener)

    notifyAuthExpired()
    notifyAuthExpired()
    expect(listener).toHaveBeenCalledTimes(1)

    resetAuthExpiredFlag()
    notifyAuthExpired()
    expect(listener).toHaveBeenCalledTimes(2)

    unsubscribe()
    window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT))
    expect(listener).toHaveBeenCalledTimes(2)
  })
})
