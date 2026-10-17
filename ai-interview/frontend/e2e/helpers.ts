import { readFileSync } from 'node:fs'
import path from 'node:path'

import { expect, type APIRequestContext, type Page } from '@playwright/test'

import type { InterviewDetailVO, InterviewMode, MessageVO } from '../src/types/interview'

export interface StudentAuth {
  token: string
  expiresAt: number
  user: { id: number; username: string; role: string; status: number }
}

export type AdminAuth = StudentAuth

interface RegisterOverrides {
  positionId?: number
  mode?: InterviewMode
  difficulty?: number
  rounds?: number
  durationMinutes?: number
}

interface ApiEnvelope<T> {
  code: number
  message?: string
  data?: T
}

export function readAuth(): StudentAuth {
  const file = path.resolve(process.cwd(), 'e2e/.auth/student-token.json')
  return JSON.parse(readFileSync(file, 'utf8')) as StudentAuth
}

/** 管理端登录态（global-setup 写入；供 admin.spec.ts 的 API 准备/清理使用）。 */
export function readAdminAuth(): AdminAuth {
  const file = path.resolve(process.cwd(), 'e2e/.auth/admin-token.json')
  return JSON.parse(readFileSync(file, 'utf8')) as AdminAuth
}

export function authHeaders(token: string = readAuth().token): Record<string, string> {
  return { Authorization: `Bearer ${token}` }
}

export function adminAuthHeaders(token: string = readAdminAuth().token): Record<string, string> {
  return authHeaders(token)
}

async function expectOk<T>(
  response: Awaited<ReturnType<APIRequestContext['post']>>,
  action: string,
): Promise<T> {
  const body = (await response.json()) as ApiEnvelope<T>
  expect(response.ok(), `${action} HTTP ${response.status()}`).toBeTruthy()
  expect(body.code, `${action} code=${body.code} message=${body.message ?? ''}`).toBe(0)
  return body.data as T
}

/** 注册独立 E2E 学生并登录（避免污染 demo01 的演示历史）。 */
export async function createStudent(request: APIRequestContext): Promise<StudentAuth> {
  const username = `e2e_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const password = 'E2e@123456'

  const register = await request.post('/api/auth/register', {
    data: { username, password, realName: 'E2E 学生', major: '软件工程' },
  })
  await expectOk<{ id: number }>(register, '注册 E2E 学生')

  const login = await request.post('/api/auth/login', { data: { username, password } })
  const auth = await expectOk<StudentAuth>(login, '登录 E2E 学生')
  expect(auth.token).toBeTruthy()
  expect(auth.user.username).toBe(username)
  return auth
}

/** 把任意学生登录态注入浏览器 localStorage（在页面脚本前执行）。 */
export async function installAuth(page: Page, auth: StudentAuth): Promise<void> {
  await page.addInitScript(
    ({ key, value }) => {
      window.localStorage.setItem(key, value)
    },
    { key: 'ai-interview:auth', value: JSON.stringify(auth) },
  )
}

export async function createInterviewAs(
  request: APIRequestContext,
  auth: StudentAuth,
  overrides: RegisterOverrides = {},
): Promise<InterviewDetailVO> {
  const response = await request.post('/api/interviews', {
    headers: authHeaders(auth.token),
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

export async function startInterviewAs(
  request: APIRequestContext,
  auth: StudentAuth,
  sessionId: number,
): Promise<MessageVO> {
  const response = await request.post(`/api/interviews/${sessionId}/start`, {
    headers: authHeaders(auth.token),
  })
  return expectOk<MessageVO>(response, '开始面试')
}

export async function createAndStartAs(
  request: APIRequestContext,
  auth: StudentAuth,
  overrides: RegisterOverrides = {},
): Promise<InterviewDetailVO> {
  const detail = await createInterviewAs(request, auth, overrides)
  await startInterviewAs(request, auth, detail.id)
  return detail
}

export async function submitTextAs(
  request: APIRequestContext,
  auth: StudentAuth,
  sessionId: number,
  roundNo: number,
  content: string,
): Promise<void> {
  const response = await request.post(`/api/interviews/${sessionId}/messages/text`, {
    headers: authHeaders(auth.token),
    data: { roundNo, content },
  })
  await expectOk<{ accepted: boolean }>(response, '提交回答')
}

export async function finishAs(
  request: APIRequestContext,
  auth: StudentAuth,
  sessionId: number,
): Promise<void> {
  const response = await request.post(`/api/interviews/${sessionId}/finish`, {
    headers: authHeaders(auth.token),
  })
  await expectOk<{ accepted: boolean }>(response, '结束面试')
}

export async function abortAs(
  request: APIRequestContext,
  auth: StudentAuth,
  sessionId: number,
): Promise<void> {
  const response = await request.post(`/api/interviews/${sessionId}/abort`, {
    headers: authHeaders(auth.token),
  })
  await expectOk<{ id: number }>(response, '中止面试')
}

/** 轮询 detail 直到进入指定状态（Mock 模式通常 1~2s）。 */
export async function waitForInterviewStatus(
  request: APIRequestContext,
  auth: StudentAuth,
  sessionId: number,
  statuses: string[],
  timeout = 30_000,
): Promise<void> {
  await expect
    .poll(
      async () => {
        const response = await request.get(`/api/interviews/${sessionId}`, {
          headers: authHeaders(auth.token),
        })
        const body = (await response.json()) as ApiEnvelope<{ status?: string }>
        return statuses.includes(body.data?.status ?? '')
      },
      { timeout, intervals: [250, 500, 1000] },
    )
    .toBe(true)
}

// ===== demo01 兼容包装（F2 用例继续使用） =====

export async function createInterview(
  request: APIRequestContext,
  overrides: RegisterOverrides = {},
): Promise<InterviewDetailVO> {
  return createInterviewAs(request, readAuth(), overrides)
}

export async function startInterview(
  request: APIRequestContext,
  sessionId: number,
): Promise<MessageVO> {
  return startInterviewAs(request, readAuth(), sessionId)
}

export async function createAndStart(
  request: APIRequestContext,
  overrides: RegisterOverrides = {},
): Promise<InterviewDetailVO> {
  return createAndStartAs(request, readAuth(), overrides)
}

export async function abortSession(request: APIRequestContext, sessionId: number): Promise<void> {
  await abortAs(request, readAuth(), sessionId)
}

export function trackPageErrors(page: Page): string[] {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  return errors
}

export const SAMPLE_ANSWER =
  '先给结论：我会从复现、定位、验证、预防四步排查。第一步用日志与监控确认影响面并稳定复现；第二步结合 EXPLAIN、线程栈和 GC 日志定位根因，区分是索引失效、锁竞争还是内存泄漏；第三步做最小化修改并补充回归测试；最后增加指标告警与压测，防止同类问题再次发生。'
