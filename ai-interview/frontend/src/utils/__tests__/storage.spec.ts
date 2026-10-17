import { describe, expect, it } from 'vitest'

import {
  readJson,
  readRaw,
  removeNamespace,
  removeRaw,
  storageKey,
  writeJson,
  writeRaw,
} from '@/utils/storage'

describe('storage', () => {
  it('按命名空间读写字符串与 JSON', () => {
    writeRaw('rememberUsername', 'demo01')
    expect(readRaw('rememberUsername')).toBe('demo01')
    expect(localStorage.getItem('ai-interview:rememberUsername')).toBe('demo01')

    writeJson('object', { a: 1, b: ['x'] })
    expect(readJson<{ a: number; b: string[] }>('object')).toEqual({ a: 1, b: ['x'] })
  })

  it('非法 JSON 返回 null', () => {
    localStorage.setItem(storageKey('broken'), '{oops')
    expect(readJson('broken')).toBeNull()
  })

  it('removeRaw 只删除目标命名空间键', () => {
    writeRaw('one', '1')
    writeRaw('two', '2')
    removeRaw('one')
    expect(readRaw('one')).toBeNull()
    expect(readRaw('two')).toBe('2')
  })

  it('removeNamespace 只清理应用前缀键', () => {
    writeRaw('one', '1')
    writeJson('two', { ok: true })
    localStorage.setItem('other-app:key', 'keep')

    removeNamespace()

    expect(readRaw('one')).toBeNull()
    expect(readJson('two')).toBeNull()
    expect(localStorage.getItem('other-app:key')).toBe('keep')
  })
})
