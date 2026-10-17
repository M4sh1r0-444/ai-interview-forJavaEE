import { createPinia, setActivePinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import * as interviewApi from '@/api/interview'
import { BizError } from '@/api/http'
import { REPORT_POLL_MAX_MS, useReportStore } from '@/stores/report'
import type { ReportVO } from '@/types/report'

vi.mock('@/api/interview', () => ({
  getReport: vi.fn(),
  finishInterview: vi.fn(),
}))

const mocked = vi.mocked(interviewApi)

function report(overrides: Partial<ReportVO> = {}): ReportVO {
  return {
    reportId: 20,
    sessionId: 21,
    positionName: 'Java 后端开发工程师',
    mode: 'TEXT',
    status: 'FINISHED',
    totalScore: 64.46,
    dimensionScores: {
      technical: 40,
      depth: 48,
      logic: 74,
      match: 55,
      communication: 88,
    },
    summary: '总评',
    strengths: ['沟通表达较好'],
    weaknesses: ['技术正确性有待加强'],
    improvementPlan: ['重刷高频题'],
    questionEvidence: [],
    createdAt: '2026-10-17T20:00:00',
    ...overrides,
  }
}

function notReady(): BizError {
  return new BizError(10004, '报告尚未生成')
}

describe('report store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.useFakeTimers()
    Object.defineProperty(document, 'hidden', { configurable: true, value: false })
  })

  afterEach(() => {
    vi.useRealTimers()
    Object.defineProperty(document, 'hidden', { configurable: true, value: false })
  })

  it('加载成功后缓存报告，再次 load 不重复请求', async () => {
    mocked.getReport.mockResolvedValue(report())
    const store = useReportStore()

    const first = await store.load(21)
    expect(first?.totalScore).toBe(64.46)
    expect(store.report?.reportId).toBe(20)
    expect(store.pending).toBe(false)
    expect(store.loading).toBe(false)

    const second = await store.load(21)
    expect(second?.reportId).toBe(20)
    expect(mocked.getReport).toHaveBeenCalledTimes(1)
  })

  it('10004 视为生成中并自动轮询恢复', async () => {
    mocked.getReport.mockRejectedValueOnce(notReady()).mockResolvedValueOnce(report())
    const store = useReportStore()

    const result = await store.load(21)
    expect(result).toBeNull()
    expect(store.pending).toBe(true)
    expect(store.polling).toBe(true)
    expect(store.error).toBeNull()

    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect(mocked.getReport).toHaveBeenCalledTimes(2)
    expect(store.report?.totalScore).toBe(64.46)
    expect(store.pending).toBe(false)
    expect(store.polling).toBe(false)
  })

  it('超过 60 秒仍未就绪转为生成较慢并可重试', async () => {
    mocked.getReport.mockRejectedValue(notReady())
    const store = useReportStore()

    await store.load(21)
    expect(store.pending).toBe(true)
    await vi.advanceTimersByTimeAsync(REPORT_POLL_MAX_MS + 1000)

    expect(store.timedOut).toBe(true)
    expect(store.polling).toBe(false)
    expect(store.pending).toBe(true)
    expect(store.pollAttempt).toBeGreaterThan(1)
  })

  it('非 10004 错误停止轮询并保留错误码', async () => {
    mocked.getReport.mockRejectedValue(new BizError(50001, '服务器错误', 'trace-1'))
    const store = useReportStore()

    const result = await store.load(21)
    expect(result).toBeNull()
    expect(store.errorCode).toBe(50001)
    expect(store.polling).toBe(false)
    expect(store.pending).toBe(false)
  })

  it('40401 保留为不存在的错误码', async () => {
    mocked.getReport.mockRejectedValue(new BizError(40401, '不存在'))
    const store = useReportStore()

    await store.load(99)
    expect(store.errorCode).toBe(40401)
  })

  it('重新生成调用幂等 finish 后立即拉取并恢复', async () => {
    mocked.getReport.mockRejectedValueOnce(notReady()).mockResolvedValueOnce(report())
    mocked.finishInterview.mockResolvedValue({
      accepted: true,
      status: 'FINISHED',
      reportId: null,
    })
    const store = useReportStore()

    await store.load(21)
    expect(store.polling).toBe(true)

    const accepted = await store.retry(21)
    expect(accepted).toBe(true)
    expect(mocked.finishInterview).toHaveBeenCalledWith(21)
    await flushPromises()

    expect(store.report?.reportId).toBe(20)
    expect(store.timedOut).toBe(false)
    expect(store.retrying).toBe(false)
  })

  it('重新生成失败时记录错误并停止轮询', async () => {
    mocked.getReport.mockRejectedValue(notReady())
    mocked.finishInterview.mockRejectedValue(new BizError(10005, '状态冲突'))
    const store = useReportStore()

    await store.load(21)
    const accepted = await store.retry(21)

    expect(accepted).toBe(false)
    expect(store.errorCode).toBe(10005)
    expect(store.polling).toBe(false)
  })

  it('页面隐藏时暂停轮询，恢复可见后立即补一次请求', async () => {
    Object.defineProperty(document, 'hidden', { configurable: true, value: true })
    mocked.getReport.mockRejectedValueOnce(notReady()).mockResolvedValueOnce(report())
    const store = useReportStore()

    await store.load(21)
    await vi.advanceTimersByTimeAsync(5000)
    expect(mocked.getReport).toHaveBeenCalledTimes(1)
    expect(store.paused).toBe(true)
    expect(store.polling).toBe(true)

    Object.defineProperty(document, 'hidden', { configurable: true, value: false })
    store.resumePolling()
    await flushPromises()

    expect(mocked.getReport).toHaveBeenCalledTimes(2)
    expect(store.report?.totalScore).toBe(64.46)
  })

  it('清空指定会话缓存并复位当前会话', async () => {
    mocked.getReport.mockResolvedValue(report())
    const store = useReportStore()
    await store.load(21)

    store.clear(21)
    expect(store.report).toBeNull()
    expect(store.sessionId).toBeNull()
    expect(store.cache.has(21)).toBe(false)
  })

  it('clearAll 清空全部缓存（登出/切换账号）', async () => {
    mocked.getReport.mockResolvedValue(report())
    const store = useReportStore()
    await store.load(21)
    await store.load(22)

    store.clearAll()
    expect(store.cache.size).toBe(0)
    expect(store.report).toBeNull()
    expect(store.sessionId).toBeNull()
  })
})
