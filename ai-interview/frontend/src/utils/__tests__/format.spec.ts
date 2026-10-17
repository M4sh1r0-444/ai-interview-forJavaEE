import { describe, expect, it } from 'vitest'

import {
  formatDate,
  formatDateTime,
  formatDuration,
  formatDurationMs,
  formatNumber,
  formatPercent,
  formatScore,
  truncate,
} from '@/utils/format'

describe('format', () => {
  it('LocalDateTime 按本地时间格式化，不做 UTC 转换', () => {
    expect(formatDateTime('2026-10-17T16:46:57')).toBe('2026-10-17 16:46')
    expect(formatDate('2026-10-17T16:46:57')).toBe('2026-10-17')
  })

  it('空值与非法时间返回占位符', () => {
    expect(formatDateTime(null)).toBe('—')
    expect(formatDateTime('not-a-date')).toBe('—')
    expect(formatDate(undefined)).toBe('—')
  })

  it('秒/毫秒时长格式化为 mm:ss', () => {
    expect(formatDuration(0)).toBe('00:00')
    expect(formatDuration(65)).toBe('01:05')
    expect(formatDuration(-1)).toBe('--:--')
    expect(formatDuration(null)).toBe('--:--')
    expect(formatDurationMs(65_000)).toBe('01:05')
  })

  it('分数固定两位小数，空值显示 —', () => {
    expect(formatScore(64.456)).toBe('64.46')
    expect(formatScore(0)).toBe('0.00')
    expect(formatScore(null)).toBe('—')
  })

  it('百分比与数字格式化', () => {
    expect(formatPercent(0.425)).toBe('42.5%')
    expect(formatPercent(42.5, true)).toBe('42.5%')
    expect(formatPercent(undefined)).toBe('—')
    expect(formatNumber(12)).toBe('12')
    expect(formatNumber(null)).toBe('—')
  })

  it('超长文本截断', () => {
    expect(truncate('abcdef', 4)).toBe('abcd…')
    expect(truncate('abc', 4)).toBe('abc')
    expect(truncate(null, 4)).toBe('')
  })
})
