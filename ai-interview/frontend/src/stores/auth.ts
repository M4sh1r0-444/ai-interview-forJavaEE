import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import * as authApi from '@/api/auth'
import { isBizError } from '@/api/http'
import type { LoginPayload, PersistedAuth, Role, UserVO } from '@/types/auth'
import { clearSession, getSession, isSessionExpired, setSession } from '@/utils/authSession'
import { resolveHomePath } from '@/utils/navigation'

/**
 * 认证状态是唯一持久化的 store（localStorage `ai-interview:auth`）。
 * 关键约束：
 * - token 过期立即清理；restore 失败区分 40101（清空）与网络错误（保留 token + 离线提示）；
 * - 不存密码，不打印 token；
 * - 单实例 restore，避免启动与路由守卫并发请求 GET /me。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref('')
  const expiresAt = ref(0)
  const user = ref<UserVO | null>(null)
  const initialized = ref(false)
  const serverUnavailable = ref(false)

  let restorePromise: Promise<void> | null = null

  const isAuthenticated = computed(() => token.value.length > 0 && expiresAt.value > Date.now())
  const isStudent = computed(() => user.value?.role === 'STUDENT')
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const role = computed<Role | null>(() => user.value?.role ?? null)
  const homePath = computed(() => resolveHomePath(role.value))
  const displayName = computed(() => user.value?.realName || user.value?.username || '同学')

  function applySession(session: PersistedAuth): void {
    token.value = session.token
    expiresAt.value = session.expiresAt
    user.value = session.user
    setSession(session)
  }

  /** 从 localStorage 恢复内存态；返回是否存在可用 token。 */
  function hydrate(): boolean {
    const session = getSession()
    if (!session) {
      token.value = ''
      expiresAt.value = 0
      user.value = null
      return false
    }
    if (isSessionExpired(session)) {
      clear()
      return false
    }
    token.value = session.token
    expiresAt.value = session.expiresAt
    user.value = session.user
    return true
  }

  /** 只清内存与持久化，不触发网络请求。 */
  function clear(): void {
    token.value = ''
    expiresAt.value = 0
    user.value = null
    serverUnavailable.value = false
    clearSession()
  }

  /** 每次路由跳转前调用，兜底时间推移导致的过期（computed 不会自动 tick）。 */
  function ensureFresh(): boolean {
    if (!token.value) return false
    if (Date.now() >= expiresAt.value) {
      clear()
      return false
    }
    return true
  }

  async function login(payload: LoginPayload): Promise<UserVO> {
    const res = await authApi.login(payload)
    applySession({ token: res.token, expiresAt: res.expiresAt, user: res.user })
    serverUnavailable.value = false
    initialized.value = true
    return res.user
  }

  /** 用服务端返回的最新 UserVO 更新内存态与持久化（资料修改后避免整页刷新）。 */
  function applyUser(next: UserVO): void {
    user.value = next
    if (token.value) {
      setSession({ token: token.value, expiresAt: expiresAt.value, user: next })
    }
  }

  async function fetchMe(): Promise<UserVO> {
    const me = await authApi.getMe()
    applyUser(me)
    return me
  }

  async function restore(): Promise<void> {
    if (initialized.value) return
    if (restorePromise) return restorePromise

    const task = (async () => {
      const hadSession = hydrate()
      if (!hadSession) {
        initialized.value = true
        return
      }
      try {
        await fetchMe()
        serverUnavailable.value = false
      } catch (error) {
        // 40101/账号禁用：清空登录态；网络失败保留 token，由离线条提示（计划 §7.1）
        if (isBizError(error) && (error.code === 40101 || error.code === 10003)) {
          clear()
        } else {
          serverUnavailable.value = true
        }
      } finally {
        initialized.value = true
        restorePromise = null
      }
    })()

    restorePromise = task
    return task
  }

  async function logout(): Promise<void> {
    try {
      if (token.value) await authApi.logout()
    } catch {
      // 后端不可达也继续本地登出
    } finally {
      clear()
    }
  }

  /** 多标签页同步：他页登出/换号后刷新本页内存态。 */
  function syncFromStorage(): boolean {
    const hadSession = hydrate()
    if (!hadSession) return false
    return true
  }

  return {
    token,
    expiresAt,
    user,
    initialized,
    serverUnavailable,
    isAuthenticated,
    isStudent,
    isAdmin,
    role,
    homePath,
    displayName,
    login,
    logout,
    fetchMe,
    applyUser,
    restore,
    clear,
    ensureFresh,
    syncFromStorage,
  }
})
