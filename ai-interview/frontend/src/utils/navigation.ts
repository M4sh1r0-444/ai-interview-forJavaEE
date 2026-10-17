import type { Role } from '@/types/auth'
import { DEFAULT_HOME } from '@/utils/constants'

export function resolveHomePath(role?: Role | null): string {
  return role === 'ADMIN' ? DEFAULT_HOME.ADMIN : DEFAULT_HOME.STUDENT
}

/**
 * 防开放重定向：只接受站内绝对路径；拒绝 //evil.com、协议地址、登录/注册页自身。
 */
export function sanitizeRedirect(raw: unknown): string | null {
  if (typeof raw !== 'string') return null
  const value = raw.trim()
  if (!value.startsWith('/')) return null
  if (value.startsWith('//') || value.startsWith('/\\')) return null
  if (value.startsWith('/login') || value.startsWith('/register')) return null
  return value
}

/** 登录成功后：优先跳 redirect，否则按角色默认首页。 */
export function resolveLoginTarget(redirect: unknown, role: Role): string {
  return sanitizeRedirect(redirect) ?? resolveHomePath(role)
}

export function canAccess(roles: Role[] | undefined, role?: Role | null): boolean {
  if (!roles || roles.length === 0) return true
  return !!role && roles.includes(role)
}

/** 角色越界时的落点：未登录去登录，学生去 403，管理员回管理端首页。 */
export function resolveForbiddenTarget(role?: Role | null): string {
  if (!role) return '/login'
  return role === 'ADMIN' ? DEFAULT_HOME.ADMIN : '/403'
}
