export type Role = 'STUDENT' | 'ADMIN'

/** 后端脱敏用户视图（不含 passwordHash）。 */
export interface UserVO {
  id: number
  username: string
  realName?: string | null
  role: Role
  major?: string | null
  /** 1=启用，0=禁用 */
  status: number
}

export interface LoginPayload {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  /** epoch 毫秒 */
  expiresAt: number
  user: UserVO
}

export interface RegisterPayload {
  username: string
  password: string
  realName?: string
  major?: string
}

export interface UpdateMePayload {
  realName?: string
  major?: string
}

/** localStorage 持久化结构（唯一持久化的 store 数据）。 */
export interface PersistedAuth {
  token: string
  expiresAt: number
  user: UserVO
}
