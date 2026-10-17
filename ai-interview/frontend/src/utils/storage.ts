/**
 * localStorage 安全封装：统一命名空间，读写均 try/catch。
 * 页面不得直接操作 localStorage（计划 §6.1）。
 */
const PREFIX = 'ai-interview:'

function getStorage(): Storage | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage
  } catch {
    // 隐私模式 / 禁用 storage 时降级为内存态，不影响页面运行
    return null
  }
}

export function storageKey(key: string): string {
  return `${PREFIX}${key}`
}

export function readRaw(key: string): string | null {
  try {
    return getStorage()?.getItem(storageKey(key)) ?? null
  } catch {
    return null
  }
}

export function writeRaw(key: string, value: string): void {
  try {
    getStorage()?.setItem(storageKey(key), value)
  } catch {
    // 配额已满或不可写：静默降级
  }
}

export function removeRaw(key: string): void {
  try {
    getStorage()?.removeItem(storageKey(key))
  } catch {
    // ignore
  }
}

export function readJson<T>(key: string): T | null {
  const raw = readRaw(key)
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

export function writeJson<T>(key: string, value: T): void {
  try {
    writeRaw(key, JSON.stringify(value))
  } catch {
    // 循环引用等异常不应中断业务
  }
}

export function removeNamespace(): void {
  try {
    const storage = getStorage()
    if (!storage) return
    const keys: string[] = []
    for (let i = 0; i < storage.length; i += 1) {
      const key = storage.key(i)
      if (key?.startsWith(PREFIX)) keys.push(key)
    }
    keys.forEach((key) => storage.removeItem(key))
  } catch {
    // ignore
  }
}

export const storage = {
  readRaw,
  writeRaw,
  removeRaw,
  readJson,
  writeJson,
  removeNamespace,
  storageKey,
}
