import type { InterviewStatus, QuestionType, ResourceType } from '@/types/interview'
import type { Role } from '@/types/auth'

export const APP_TITLE = import.meta.env.VITE_APP_TITLE || 'AI 模拟面试与能力提升平台'

export function resolveDocumentTitle(title?: string): string {
  return title ? `${title} · ${APP_TITLE}` : APP_TITLE
}

/** 前端 localStorage 命名空间（storage.ts 会统一加前缀）。 */
export const STORAGE_KEYS = {
  auth: 'auth',
  rememberUsername: 'login-username',
} as const

export const DEFAULT_HOME: Record<Role, string> = {
  STUDENT: '/home',
  ADMIN: '/admin/dashboard',
}

/** 后端建会话参数约束（岗位 defaultRounds 允许 3~8，但建面试只允许 5~8）。 */
export const INTERVIEW_LIMITS = {
  minRounds: 5,
  maxRounds: 8,
  minDurationMinutes: 15,
  maxDurationMinutes: 60,
  minDifficulty: 1,
  maxDifficulty: 5,
} as const

export const INTERVIEW_STATUS_LABELS: Record<InterviewStatus, string> = {
  CREATED: '待开始',
  RUNNING: '进行中',
  WAITING_USER: '等待作答',
  PROCESSING_AI: 'AI 思考中',
  FINISHED: '已完成',
  ABORTED: '已中止',
  TIMEOUT: '已超时',
}

export const INTERVIEW_MODE_LABELS = {
  TEXT: '文字面试',
  AUDIO: '语音面试',
} as const

export const QUESTION_TYPE_LABELS: Record<QuestionType, string> = {
  TECHNICAL: '技术题',
  PROJECT: '项目题',
  SCENARIO: '场景题',
  BEHAVIORAL: '行为题',
}

export const RESOURCE_TYPE_LABELS: Record<ResourceType, string> = {
  ARTICLE: '文章',
  QUESTION: '题目',
  PRACTICE: '练习',
  PROJECT: '项目',
}

export const ROLE_LABELS: Record<Role, string> = {
  STUDENT: '学生',
  ADMIN: '管理员',
}

/** 无法就地展示时使用的错误文案；具体页面仍优先使用后端 message。 */
export const ERROR_CODE_MESSAGES: Record<number, string> = {
  10001: '用户名已存在',
  10002: '用户名或密码错误',
  10003: '账号已禁用，请联系管理员',
  10004: '报告尚未生成',
  10005: '面试状态已变化，请刷新后重试',
  40001: '参数校验失败，请检查填写内容',
  40002: '参数超出允许范围',
  40003: '数据已被其他操作修改，请刷新后重试',
  40101: '登录已过期，请重新登录',
  40301: '当前账号无权访问该资源',
  40401: '资源不存在或无权访问',
  42901: '操作过于频繁，请稍后再试',
  50001: '服务器开小差了，请稍后重试',
  [-1]: '网络异常，请检查后端服务后重试',
}
