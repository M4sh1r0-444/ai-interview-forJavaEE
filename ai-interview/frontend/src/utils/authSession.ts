import type { PersistedAuth } from '@/types/auth'
import { STORAGE_KEYS } from '@/utils/constants'
import { readJson, removeRaw, writeJson } from '@/utils/storage'

export const AUTH_EXPIRED_EVENT = 'auth:expired'

/** 防止并发 401 触发多次跳转（计划 §4.4）。 */
let expiredNotified = false

function isRole(value: unknown): value is PersistedAuth['user']['role'] {
  return value === 'STUDENT' || value === 'ADMIN'
}

function isPersistedAuth(value: unknown): value is PersistedAuth {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<PersistedAuth>
  if (typeof candidate.token !== 'string' || candidate.token.length === 0) return false
  if (typeof candidate.expiresAt !== 'number' || !Number.isFinite(candidate.expiresAt)) return false
  if (!candidate.user || typeof candidate.user !== 'object') return false
  const user = candidate.user as PersistedAuth['user']
  return (
    typeof user.id === 'number' &&
    typeof user.username === 'string' &&
    isRole(user.role) &&
    typeof user.status === 'number'
  )
}

export function getSession(): PersistedAuth | null {
  const value = readJson<unknown>(STORAGE_KEYS.auth)
  return isPersistedAuth(value) ? value : null
}

export function setSession(session: PersistedAuth): void {
  writeJson(STORAGE_KEYS.auth, session)
  expiredNotified = false
}

export function clearSession(): void {
  removeRaw(STORAGE_KEYS.auth)
  expiredNotified = false
}

export function getToken(): string {
  return getSession()?.token ?? ''
}

export function isSessionExpired(session: PersistedAuth | null, now = Date.now()): boolean {
  if (!session || !session.token) return true
  return !Number.isFinite(session.expiresAt) || now >= session.expiresAt
}

/**
 * 只发事件，不直接清 store：避免 http.ts 依赖 Pinia（循环依赖，计划 §6.1）。
 * App.vue 监听后调用 authStore.clear() 并跳转登录。
 */
export function notifyAuthExpired(): void {
  if (expiredNotified) return
  expiredNotified = true
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT))
  }
}

export function onAuthExpired(handler: () => void): () => void {
  window.addEventListener(AUTH_EXPIRED_EVENT, handler)
  return () => window.removeEventListener(AUTH_EXPIRED_EVENT, handler)
}

/** 供测试与重新登录后复位去重标志。 */
export function resetAuthExpiredFlag(): void {
  expiredNotified = false
}
