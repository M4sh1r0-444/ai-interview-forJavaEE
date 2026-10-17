import { request } from '@/api/http'
import type {
  LoginPayload,
  LoginResponse,
  RegisterPayload,
  UpdateMePayload,
  UserVO,
} from '@/types/auth'

/** 注册（返回 UserVO，不返回 token；后端固定 STUDENT）。 */
export function register(payload: RegisterPayload): Promise<UserVO> {
  return request<UserVO>({ url: '/api/auth/register', method: 'POST', data: payload })
}

/** 登录（token/expiresAt/user）。 */
export function login(payload: LoginPayload): Promise<LoginResponse> {
  return request<LoginResponse>({ url: '/api/auth/login', method: 'POST', data: payload })
}

/** 登出（jti 进 Redis 黑名单；失败不影响本地清理）。 */
export function logout(): Promise<void> {
  return request<void>({ url: '/api/auth/logout', method: 'POST' })
}

/** 当前用户（启动校验/个人中心）。 */
export function getMe(): Promise<UserVO> {
  return request<UserVO>({ url: '/api/users/me', method: 'GET' })
}

/** 修改 realName/major（不允许改 username/role）。 */
export function updateMe(payload: UpdateMePayload): Promise<UserVO> {
  return request<UserVO>({ url: '/api/users/me', method: 'PUT', data: payload })
}
