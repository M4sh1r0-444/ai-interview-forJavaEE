import { describe, expect, it } from 'vitest'

import {
  canAccess,
  resolveForbiddenTarget,
  resolveHomePath,
  resolveLoginTarget,
  sanitizeRedirect,
} from '@/utils/navigation'

describe('navigation', () => {
  it('按角色返回默认首页', () => {
    expect(resolveHomePath('STUDENT')).toBe('/home')
    expect(resolveHomePath('ADMIN')).toBe('/admin/dashboard')
    expect(resolveHomePath(null)).toBe('/home')
  })

  it('只接受站内 redirect，拒绝开放重定向', () => {
    expect(sanitizeRedirect('/positions?page=1')).toBe('/positions?page=1')
    expect(sanitizeRedirect(' /home ')).toBe('/home')
    expect(sanitizeRedirect('https://evil.example.com')).toBeNull()
    expect(sanitizeRedirect('//evil.example.com')).toBeNull()
    expect(sanitizeRedirect('/\\evil.example.com')).toBeNull()
    expect(sanitizeRedirect('/login?redirect=/home')).toBeNull()
    expect(sanitizeRedirect('/register')).toBeNull()
    expect(sanitizeRedirect(undefined)).toBeNull()
    expect(sanitizeRedirect(123)).toBeNull()
  })

  it('登录后优先跳合法 redirect，否则按角色兜底', () => {
    expect(resolveLoginTarget('/history?page=2', 'STUDENT')).toBe('/history?page=2')
    expect(resolveLoginTarget('//evil.example.com', 'ADMIN')).toBe('/admin/dashboard')
    expect(resolveLoginTarget(null, 'STUDENT')).toBe('/home')
  })

  it('角色匹配与越界落点', () => {
    expect(canAccess(['STUDENT'], 'STUDENT')).toBe(true)
    expect(canAccess(['ADMIN'], 'STUDENT')).toBe(false)
    expect(canAccess(undefined, null)).toBe(true)
    expect(resolveForbiddenTarget('STUDENT')).toBe('/403')
    expect(resolveForbiddenTarget('ADMIN')).toBe('/admin/dashboard')
    expect(resolveForbiddenTarget(null)).toBe('/login')
  })
})
