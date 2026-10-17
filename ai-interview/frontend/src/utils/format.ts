import dayjs from 'dayjs'

const EMPTY = '—'

/** 后端 LocalDateTime 无时区（如 2026-10-17T16:46:57），按本地时间展示，不做 UTC 转换。 */
export function formatDateTime(value?: string | null): string {
  if (!value) return EMPTY
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm') : EMPTY
}

export function formatDate(value?: string | null): string {
  if (!value) return EMPTY
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format('YYYY-MM-DD') : EMPTY
}

export function formatTime(value?: string | null): string {
  if (!value) return EMPTY
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format('HH:mm:ss') : EMPTY
}

/** 秒 → mm:ss；为空/负数返回 --:-- */
export function formatDuration(seconds?: number | null): string {
  if (seconds == null || !Number.isFinite(seconds) || seconds < 0) return '--:--'
  const total = Math.floor(seconds)
  const minutes = Math.floor(total / 60)
  const rest = total % 60
  return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
}

/** 毫秒 → m:ss（录音时长） */
export function formatDurationMs(ms?: number | null): string {
  if (ms == null || !Number.isFinite(ms) || ms < 0) return '--:--'
  return formatDuration(ms / 1000)
}

export function formatScore(value?: number | null): string {
  if (value == null || !Number.isFinite(value)) return EMPTY
  return value.toFixed(2)
}

/** 0.42 → 42.0%；已是百分数时传 secondArg=true */
export function formatPercent(value?: number | null, isPercent = false): string {
  if (value == null || !Number.isFinite(value)) return EMPTY
  const percent = isPercent ? value : value * 100
  return `${percent.toFixed(1)}%`
}

export function formatNumber(value?: number | null): string {
  if (value == null || !Number.isFinite(value)) return EMPTY
  return String(value)
}

export function formatBoolean(value?: boolean | null, yes = '是', no = '否'): string {
  if (value == null) return EMPTY
  return value ? yes : no
}

export function truncate(text: string | null | undefined, maxLength: number): string {
  if (!text) return ''
  if (text.length <= maxLength) return text
  return `${text.slice(0, Math.max(0, maxLength))}…`
}

export { EMPTY as EMPTY_TEXT }
