import { readFileSync } from 'node:fs'
import path from 'node:path'

import { expect, type APIRequestContext, type Page } from '@playwright/test'

import type { InterviewDetailVO, MessageVO } from '../src/types/interview'

interface StudentAuth {
  token: string
  expiresAt: number
  user: { id: number; username: string; role: string; status: number }
}

export function readAuth(): StudentAuth {
  const file = path.resolve(process.cwd(), 'e2e/.auth/student-token.json')
  return JSON.parse(readFileSync(file, 'utf8')) as StudentAuth
}

function authHeaders(): Record<string, string> {
  return { Authorization: `Bearer ${readAuth().token}` }
}

async function expectOk<T>(
  response: Awaited<ReturnType<APIRequestContext['post']>>,
  action: string,
): Promise<T> {
  const body = (await response.json()) as { code: number; message?: string; data?: T }
  expect(response.ok(), `${action} HTTP ${response.status()}`).toBeTruthy()
  expect(body.code, `${action} code=${body.code} message=${body.message ?? ''}`).toBe(0)
  return body.data as T
}

export async function createInterview(
  request: APIRequestContext,
  overrides: Partial<{
    positionId: number
    mode: 'TEXT' | 'AUDIO'
    difficulty: number
    rounds: number
    durationMinutes: number
  }> = {},
): Promise<InterviewDetailVO> {
  const response = await request.post('/api/interviews', {
    headers: authHeaders(),
    data: {
      positionId: 1,
      mode: 'TEXT',
      difficulty: 2,
      rounds: 5,
      durationMinutes: 15,
      ...overrides,
    },
  })
  return expectOk<InterviewDetailVO>(response, '创建面试')
}

export async function startInterview(
  request: APIRequestContext,
  sessionId: number,
): Promise<MessageVO> {
  const response = await request.post(`/api/interviews/${sessionId}/start`, {
    headers: authHeaders(),
  })
  return expectOk<MessageVO>(response, '开始面试')
}

export async function createAndStart(
  request: APIRequestContext,
  overrides: Parameters<typeof createInterview>[1] = {},
): Promise<InterviewDetailVO> {
  const detail = await createInterview(request, overrides)
  await startInterview(request, detail.id)
  return detail
}

export async function abortSession(request: APIRequestContext, sessionId: number): Promise<void> {
  await request.post(`/api/interviews/${sessionId}/abort`, {
    headers: authHeaders(),
  })
}

export function trackPageErrors(page: Page): string[] {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  return errors
}

export const SAMPLE_ANSWER =
  '先给结论：我会从复现、定位、验证、预防四步排查。第一步用日志与监控确认影响面并稳定复现；第二步结合 EXPLAIN、线程栈和 GC 日志定位根因，区分是索引失效、锁竞争还是内存泄漏；第三步做最小化修改并补充回归测试；最后增加指标告警与压测，防止同类问题再次发生。'
