import axios, { type AxiosError, type AxiosResponse, type AxiosRequestConfig } from 'axios'

import type { ApiResult } from '@/types/api'
import { BUSINESS_CODE } from '@/types/api'
import { notifyForbidden } from '@/utils/appEvents'
import { getToken, notifyAuthExpired } from '@/utils/authSession'
import { ERROR_CODE_MESSAGES } from '@/utils/constants'

/**
 * 业务错误：HTTP 200 也可能失败（code != 0），页面统一捕获 BizError。
 * code=-1 表示网络/超时等本层兜底错误。
 */
export class BizError extends Error {
  readonly code: number
  readonly traceId?: string
  readonly httpStatus?: number

  constructor(code: number, message: string, traceId?: string, httpStatus?: number) {
    super(message)
    this.name = 'BizError'
    this.code = code
    this.traceId = traceId
    this.httpStatus = httpStatus
  }
}

export function isBizError(error: unknown): error is BizError {
  return error instanceof BizError
}

/** 页面兜底文案：优先后端 message，其次错误码映射。 */
export function errorMessage(error: unknown, fallback = '操作失败，请稍后再试'): string {
  if (error instanceof BizError) {
    return error.message || ERROR_CODE_MESSAGES[error.code] || fallback
  }
  if (error instanceof Error && error.message) return error.message
  return fallback
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 20_000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function handleAuthEvents(body: ApiResult<unknown> | undefined, status?: number): void {
  if (body?.code === BUSINESS_CODE.UNAUTHORIZED || status === 401) {
    notifyAuthExpired()
  }
  if (body?.code === BUSINESS_CODE.FORBIDDEN || status === 403) {
    notifyForbidden({ code: body?.code, message: body?.message })
  }
}

http.interceptors.response.use(
  (res) => {
    // 音频等二进制响应交给调用方处理（fetchAudioBlob 会检查 JSON 业务错误）
    if (res.config.responseType === 'blob') return res

    const body = res.data as ApiResult<unknown> | undefined
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === BUSINESS_CODE.OK) return body.data as unknown as AxiosResponse
      handleAuthEvents(body, res.status)
      throw new BizError(
        body.code,
        body.message || ERROR_CODE_MESSAGES[body.code] || '请求失败',
        body.traceId,
        res.status,
      )
    }
    // 非统一包装响应（如 actuator/health）原样返回
    return res.data as unknown as AxiosResponse
  },
  (error: AxiosError<ApiResult<unknown>>) => {
    const status = error.response?.status
    const body = error.response?.data
    handleAuthEvents(body, status)

    if (body && typeof body === 'object' && 'code' in body) {
      throw new BizError(
        body.code,
        body.message || ERROR_CODE_MESSAGES[body.code] || error.message,
        body.traceId,
        status,
      )
    }
    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
      throw new BizError(
        BUSINESS_CODE.NETWORK_ERROR,
        '请求超时，请检查网络后重试',
        undefined,
        status,
      )
    }
    if (error.code === 'ERR_CANCELED') {
      throw new BizError(BUSINESS_CODE.NETWORK_ERROR, '请求已取消', undefined, status)
    }
    throw new BizError(
      BUSINESS_CODE.NETWORK_ERROR,
      '网络异常，请检查后端服务后重试',
      undefined,
      status,
    )
  },
)

/**
 * 统一请求包装：拦截器返回 data 本体，故第二个泛型就是最终返回类型。
 * 所有 API 函数必须经此入口，页面不得直接 import axios。
 */
export function request<T>(config: AxiosRequestConfig): Promise<T> {
  // axios 1.20 的 R 泛型是条件类型，此处边界收窄为拦截器实际返回的 data 本体
  return http.request<unknown, T>(config) as unknown as Promise<T>
}

/** 去掉 undefined/null/空串，避免把空筛选条件拼进 URL。 */
export function cleanParams<T extends Record<string, unknown>>(params: T): Partial<T> {
  const result: Partial<T> = {}
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    result[key as keyof T] = value as T[keyof T]
  }
  return result
}

/** 兼容不支持 Blob.text() 的浏览器（以及 jsdom 测试环境）。 */
async function readBlobText(blob: Blob): Promise<string> {
  if (typeof blob.text === 'function') return blob.text()
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(reader.error ?? new Error('读取文件失败'))
    reader.readAsText(blob)
  })
}

/** 受保护音频回放：后端业务失败时仍可能 HTTP 200 + JSON。 */
export async function fetchBlob(config: AxiosRequestConfig): Promise<Blob> {
  const res = await http.request<Blob, AxiosResponse<Blob>>({
    ...config,
    responseType: 'blob',
    timeout: 60_000,
  })
  const blob = res.data
  if (blob.type.includes('application/json')) {
    let body: ApiResult<unknown> | undefined
    try {
      body = JSON.parse(await readBlobText(blob)) as ApiResult<unknown>
    } catch {
      body = undefined
    }
    if (body?.code != null && body.code !== BUSINESS_CODE.OK) {
      handleAuthEvents(body, res.status)
      throw new BizError(
        body.code,
        body.message || ERROR_CODE_MESSAGES[body.code] || '文件读取失败',
        body.traceId,
        res.status,
      )
    }
  }
  return blob
}
