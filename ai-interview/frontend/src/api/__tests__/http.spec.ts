import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  AxiosError,
  AxiosHeaders,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'

import { BizError, cleanParams, fetchBlob, http, request } from '@/api/http'
import type { UserVO } from '@/types/auth'
import { clearSession, resetAuthExpiredFlag, setSession } from '@/utils/authSession'

const user: UserVO = {
  id: 1,
  username: 'demo01',
  realName: '演示学生',
  role: 'STUDENT',
  major: '软件工程',
  status: 1,
}

interface MockResponseOptions {
  data: unknown
  status?: number
  headers?: Record<string, string>
}

function makeResponse(
  config: InternalAxiosRequestConfig,
  options: MockResponseOptions,
): AxiosResponse {
  return {
    data: options.data,
    status: options.status ?? 200,
    statusText: 'OK',
    headers: new AxiosHeaders(options.headers),
    config,
  } as AxiosResponse
}

function installAdapter(
  handler: (config: InternalAxiosRequestConfig) => AxiosResponse | Promise<AxiosResponse>,
): void {
  http.defaults.adapter = async (config) => handler(config)
}

beforeEach(() => {
  vi.restoreAllMocks()
  resetAuthExpiredFlag()
  clearSession()
})

afterEach(() => {
  clearSession()
  resetAuthExpiredFlag()
})

describe('http 拦截器', () => {
  it('code=0 时解包 data，并自动携带 Bearer token', async () => {
    setSession({ token: 'token-123', expiresAt: Date.now() + 60_000, user })
    let capturedAuthorization = ''
    installAdapter((config) => {
      capturedAuthorization = String(config.headers.Authorization ?? '')
      return makeResponse(config, { data: { code: 0, message: '成功', data: { hello: 'world' } } })
    })

    const data = await request<{ hello: string }>({ url: '/api/users/me', method: 'GET' })

    expect(data).toEqual({ hello: 'world' })
    expect(capturedAuthorization).toBe('Bearer token-123')
  })

  it('HTTP 200 但 code != 0 时抛 BizError 并保留 traceId', async () => {
    installAdapter((config) =>
      makeResponse(config, {
        data: { code: 10002, message: '用户名或密码错误', traceId: 'trace-10002' },
      }),
    )

    await expect(
      request({ url: '/api/auth/login', method: 'POST', data: {} }),
    ).rejects.toMatchObject({
      name: 'BizError',
      code: 10002,
      message: '用户名或密码错误',
      traceId: 'trace-10002',
      httpStatus: 200,
    } satisfies Partial<BizError>)
  })

  it('HTTP 401 触发一次 auth:expired（并发去重）', async () => {
    const listener = vi.fn()
    window.addEventListener('auth:expired', listener)
    installAdapter((config) => {
      const response = makeResponse(config, {
        data: { code: 40101, message: '登录已过期', traceId: 'trace-401' },
        status: 401,
      })
      return Promise.reject(
        new AxiosError('Unauthorized', AxiosError.ERR_BAD_REQUEST, config, undefined, response),
      )
    })

    const first = request({ url: '/api/users/me' })
    const second = request({ url: '/api/positions' })

    await expect(first).rejects.toMatchObject({ code: 40101, httpStatus: 401 })
    await expect(second).rejects.toMatchObject({ code: 40101, httpStatus: 401 })
    expect(listener).toHaveBeenCalledTimes(1)
    window.removeEventListener('auth:expired', listener)
  })

  it('网络错误归一为 code=-1 的 BizError', async () => {
    http.defaults.adapter = async (config) => {
      return Promise.reject(
        new AxiosError('Network Error', AxiosError.ERR_NETWORK, config, undefined),
      )
    }

    await expect(request({ url: '/api/positions' })).rejects.toMatchObject({
      code: -1,
      message: '网络异常，请检查后端服务后重试',
    })
  })

  it('cleanParams 去掉空值但不影响 0 与 false', () => {
    const result = cleanParams({
      page: 1,
      size: undefined,
      keyword: '',
      enabled: 0,
      flag: false,
      name: null,
    })

    expect(result).toEqual({ page: 1, enabled: 0, flag: false })
  })
})

describe('受保护音频 Blob', () => {
  it('成功时返回 Blob', async () => {
    installAdapter((config) =>
      makeResponse(config, {
        data: new Blob(['audio-bytes'], { type: 'audio/webm' }),
        headers: { 'content-type': 'audio/webm' },
      }),
    )

    const blob = await fetchBlob({ url: '/api/interviews/1/messages/2/audio', method: 'GET' })

    expect(blob).toBeInstanceOf(Blob)
    expect(blob.type).toBe('audio/webm')
  })

  it('HTTP 200 + JSON 业务错误时抛 BizError', async () => {
    installAdapter((config) =>
      makeResponse(config, {
        data: new Blob(
          [JSON.stringify({ code: 40401, message: '音频不存在', traceId: 'trace-a' })],
          {
            type: 'application/json',
          },
        ),
        headers: { 'content-type': 'application/json' },
      }),
    )

    await expect(
      fetchBlob({ url: '/api/interviews/1/messages/2/audio', method: 'GET' }),
    ).rejects.toMatchObject({
      code: 40401,
      message: '音频不存在',
      traceId: 'trace-a',
    })
  })
})
