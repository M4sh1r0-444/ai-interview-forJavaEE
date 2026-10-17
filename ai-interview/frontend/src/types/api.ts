/**
 * 统一响应与分页契约。
 * 后端空字段可能被 @JsonInclude(NON_NULL) 省略，所有可选字段一律 `?: T | null`。
 */
export interface ApiResult<T> {
  code: number
  message?: string
  data?: T
  traceId?: string
}

/** 后端 MyBatis-Plus Page 序列化后的最小字段集。 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** 后端业务错误码（HTTP 200 + code != 0，或协议级 4xx/5xx）。 */
export const BUSINESS_CODE = {
  OK: 0,
  USERNAME_EXISTS: 10001,
  BAD_CREDENTIALS: 10002,
  ACCOUNT_DISABLED: 10003,
  REPORT_NOT_READY: 10004,
  INTERVIEW_STATE_CONFLICT: 10005,
  VALIDATION_FAILED: 40001,
  OUT_OF_RANGE: 40002,
  VERSION_CONFLICT: 40003,
  NOT_FOUND: 40401,
  UNAUTHORIZED: 40101,
  FORBIDDEN: 40301,
  TOO_MANY_REQUESTS: 42901,
  SERVER_ERROR: 50001,
  NETWORK_ERROR: -1,
} as const

export type BusinessCode = (typeof BUSINESS_CODE)[keyof typeof BUSINESS_CODE]
