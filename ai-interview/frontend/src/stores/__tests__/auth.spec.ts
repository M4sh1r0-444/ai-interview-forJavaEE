import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import * as authApi from '@/api/auth'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { LoginResponse, UserVO } from '@/types/auth'
import { clearSession, getSession, setSession } from '@/utils/authSession'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  getMe: vi.fn(),
  register: vi.fn(),
  updateMe: vi.fn(),
}))

const student: UserVO = {
  id: 1,
  username: 'demo01',
  realName: '演示学生',
  role: 'STUDENT',
  major: '软件工程',
  status: 1,
}

const admin: UserVO = {
  id: 9,
  username: 'admin',
  realName: '管理员',
  role: 'ADMIN',
  status: 1,
}

const mockedApi = vi.mocked(authApi)

function loginResponse(user: UserVO): LoginResponse {
  return { token: `token-${user.username}`, expiresAt: Date.now() + 60_000, user }
}

beforeEach(() => {
  setActivePinia(createPinia())
  clearSession()
  vi.clearAllMocks()
})

describe('auth store', () => {
  it('登录后写入内存与 localStorage，角色首页正确', async () => {
    const store = useAuthStore()
    mockedApi.login.mockResolvedValue(loginResponse(admin))

    const user = await store.login({ username: 'admin', password: 'Admin@123456' })

    expect(user).toEqual(admin)
    expect(store.isAuthenticated).toBe(true)
    expect(store.isAdmin).toBe(true)
    expect(store.homePath).toBe('/admin/dashboard')
    expect(getSession()?.token).toBe('token-admin')
    expect(mockedApi.login).toHaveBeenCalledWith({
      username: 'admin',
      password: 'Admin@123456',
    })
  })

  it('restore 成功时用 /me 结果更新用户', async () => {
    const updated = { ...student, realName: '新名字' }
    setSession({ token: 'saved', expiresAt: Date.now() + 60_000, user: student })
    mockedApi.getMe.mockResolvedValue(updated)

    const store = useAuthStore()
    await store.restore()

    expect(store.initialized).toBe(true)
    expect(store.user?.realName).toBe('新名字')
    expect(getSession()?.user.realName).toBe('新名字')
  })

  it('restore 遇到过期 token 直接清理，不请求 /me', async () => {
    setSession({ token: 'expired', expiresAt: Date.now() - 1000, user: student })

    const store = useAuthStore()
    await store.restore()

    expect(store.isAuthenticated).toBe(false)
    expect(store.initialized).toBe(true)
    expect(getSession()).toBeNull()
    expect(mockedApi.getMe).not.toHaveBeenCalled()
  })

  it('restore 遇到 40101 清理登录态', async () => {
    setSession({ token: 'stale', expiresAt: Date.now() + 60_000, user: student })
    mockedApi.getMe.mockRejectedValue(new BizError(40101, '登录已过期'))

    const store = useAuthStore()
    await store.restore()

    expect(store.isAuthenticated).toBe(false)
    expect(getSession()).toBeNull()
  })

  it('restore 网络失败保留 token 并标记服务不可达', async () => {
    setSession({ token: 'offline-token', expiresAt: Date.now() + 60_000, user: student })
    mockedApi.getMe.mockRejectedValue(new BizError(-1, '网络异常'))

    const store = useAuthStore()
    await store.restore()

    expect(store.isAuthenticated).toBe(true)
    expect(store.serverUnavailable).toBe(true)
    expect(getSession()?.token).toBe('offline-token')
  })

  it('并发 restore 只请求一次 /me', async () => {
    setSession({ token: 'saved', expiresAt: Date.now() + 60_000, user: student })
    mockedApi.getMe.mockResolvedValue(student)

    const store = useAuthStore()
    await Promise.all([store.restore(), store.restore(), store.restore()])

    expect(mockedApi.getMe).toHaveBeenCalledTimes(1)
  })

  it('logout 即使接口失败也清理本地状态', async () => {
    const store = useAuthStore()
    mockedApi.login.mockResolvedValue(loginResponse(student))
    await store.login({ username: 'demo01', password: '123456' })
    mockedApi.logout.mockRejectedValue(new BizError(-1, '网络异常'))

    await store.logout()

    expect(store.isAuthenticated).toBe(false)
    expect(store.user).toBeNull()
    expect(getSession()).toBeNull()
  })

  it('ensureFresh 在时间推移后清理过期会话', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-17T12:00:00Z'))
    try {
      const store = useAuthStore()
      mockedApi.login.mockResolvedValue({
        token: 'short',
        expiresAt: Date.now() + 50,
        user: student,
      })
      await store.login({ username: 'demo01', password: '123456' })
      expect(store.ensureFresh()).toBe(true)

      vi.advanceTimersByTime(60)

      expect(store.ensureFresh()).toBe(false)
      expect(store.isAuthenticated).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })
})
