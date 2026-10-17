/**
 * 学习资源外链安全（计划 §6.8 / §5.11）：
 * 只把 http/https 渲染成可点击链接，其余（javascript:/data:/相对路径等）降级为纯文本。
 */
export function isSafeExternalUrl(value?: string | null): value is string {
  if (!value) return false
  try {
    const parsed = new URL(value)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:'
  } catch {
    return false
  }
}

/** 外链统一属性：新窗口 + 断开 opener，防止 tabnabbing。 */
export const EXTERNAL_LINK_ATTRS = {
  target: '_blank',
  rel: 'noopener noreferrer',
} as const
