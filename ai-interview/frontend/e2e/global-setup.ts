import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'

import { request, type FullConfig } from '@playwright/test'

const BACKEND_URL = process.env.E2E_BACKEND_URL || 'http://127.0.0.1:18080'
const FRONTEND_URL = process.env.E2E_BASE_URL || 'http://127.0.0.1:5173'
const STUDENT = { username: 'demo01', password: '123456' }

/**
 * E2E 前置：
 * 1. 后端必须可访问，否则给出明确失败信息（不静默 mock）；
 * 2. 只登录一次 demo01，写入 storageState，避免每个用例重复登录触发 5 次/分钟限流；
 * 3. 同时落一份 token/user 文件，供用例内的 API 准备数据使用。
 */
export default async function globalSetup(_config: FullConfig): Promise<void> {
  const api = await request.newContext()

  try {
    const health = await api.get(`${BACKEND_URL}/actuator/health`).catch(() => null)
    if (!health || !health.ok()) {
      throw new Error(
        `后端不可达：${BACKEND_URL}/actuator/health。请先在 ai-interview/backend 启动 dev profile（默认 8080，本机可用 18080 并通过 frontend/.env.development.local 代理）。`,
      )
    }
    const healthBody = (await health.json()) as { status?: string }
    if (healthBody.status !== 'UP') {
      throw new Error(`后端健康检查非 UP：${JSON.stringify(healthBody)}`)
    }

    const login = await api.post(`${BACKEND_URL}/api/auth/login`, { data: STUDENT })
    const body = (await login.json()) as {
      code?: number
      message?: string
      data?: {
        token?: string
        expiresAt?: number
        user?: { id: number; username: string; role: string; status: number }
      }
    }
    if (!login.ok() || body.code !== 0 || !body.data?.token || !body.data.user) {
      throw new Error(
        `demo01 登录失败（HTTP ${login.status()}，code=${body.code ?? '?'}）：${body.message ?? '未知'}。若为 42901 请等待 60 秒后重跑。`,
      )
    }

    const auth = {
      token: body.data.token,
      expiresAt: body.data.expiresAt ?? Date.now() + 2 * 60 * 60 * 1000,
      user: body.data.user,
    }

    const authDir = path.resolve(process.cwd(), 'e2e/.auth')
    mkdirSync(authDir, { recursive: true })
    writeFileSync(path.join(authDir, 'student-token.json'), JSON.stringify(auth, null, 2))

    const storageState = {
      cookies: [],
      origins: [
        {
          origin: FRONTEND_URL,
          localStorage: [{ name: 'ai-interview:auth', value: JSON.stringify(auth) }],
        },
      ],
    }
    writeFileSync(path.join(authDir, 'student.json'), JSON.stringify(storageState, null, 2))
  } finally {
    await api.dispose()
  }
}
