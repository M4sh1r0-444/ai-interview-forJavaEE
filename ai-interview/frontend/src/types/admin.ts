import type { Role, UserVO } from '@/types/auth'
import type {
  InterviewMode,
  InterviewStatus,
  QuestionStatus,
  QuestionType,
  ResourceType,
} from '@/types/interview'

export interface DashboardVO {
  userCount: number
  sessionCount: number
  questionCount: number
  avgScore?: number | null
  recent7Days: Array<{
    date: string
    sessionCount: number
    avgScore?: number | null
  }>
}

export interface SkillItem {
  skillCode: string
  skillName: string
  description?: string | null
  weight: number
  requiredLevel?: number | null
}

export interface DimensionWeights {
  technical?: number | null
  depth?: number | null
  logic?: number | null
  evidence?: number | null
  match?: number | null
  clarity?: number | null
  fluency?: number | null
  pace?: number | null
  confidence?: number | null
  completeness?: number | null
}

export interface TotalWeights {
  content?: number | null
  expression?: number | null
  adaptability?: number | null
  match?: number | null
}

export interface ScoreWeights {
  content?: DimensionWeights | null
  expression?: DimensionWeights | null
  total?: TotalWeights | null
}

export interface PositionAdminVO {
  id: number
  code: string
  name: string
  description?: string | null
  defaultRounds: number
  /** 1=启用 0=停用 */
  enabled: number
  skillCount: number
  skills: SkillItem[]
  scoreWeights?: ScoreWeights | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface PositionUpsertPayload {
  code?: string
  name?: string
  description?: string
  defaultRounds?: number
  enabled?: number
  skills?: SkillItem[]
  scoreWeights?: ScoreWeights
}

export interface WeightsUpdatePayload {
  scoreWeights: ScoreWeights
}

export interface QuestionAdminVO {
  id: number
  positionId: number
  type: QuestionType
  content: string
  background?: string | null
  difficulty: number
  skillTags: string[]
  referenceAnswer?: string | null
  keyPoints: string[]
  deductionPoints: string[]
  followupTemplates: string[]
  status: QuestionStatus
  version: number
  createdAt?: string | null
  updatedAt?: string | null
}

export interface QuestionUpsertPayload {
  positionId: number
  type: QuestionType
  content: string
  background?: string
  difficulty: number
  skillTags: string[]
  referenceAnswer?: string
  keyPoints?: string[]
  deductionPoints?: string[]
  followupTemplates?: string[]
  /** 更新时必须携带，冲突返回 40003 */
  version?: number
}

export interface LearningResource {
  id: number
  positionId: number
  skillCode: string
  title: string
  resourceType: ResourceType
  contentUrl?: string | null
  difficulty: number
  /** 1=上架 0=下架 */
  enabled: number
  createdAt?: string | null
}

export interface ResourceUpsertPayload {
  positionId: number
  skillCode: string
  title: string
  resourceType: ResourceType
  contentUrl?: string
  difficulty: number
  enabled?: number
}

/** 后端无知识片段列表接口；此类型仅用于新增成功回显。 */
export interface KnowledgeChunk {
  id: number
  positionId: number
  title: string
  content: string
  keywords: string
  skillTags: string
  sourceType?: string
  status?: string
  createdAt?: string
}

export interface KnowledgeUpsertPayload {
  positionId: number
  title: string
  content: string
  keywords: string
  skillTags: string
  difficulty?: number
}

export interface AdminUserQuery {
  keyword?: string
  page?: number
  size?: number
}

export interface AdminQuestionQuery {
  positionId?: number
  type?: QuestionType
  difficulty?: number
  page?: number
  size?: number
}

export interface AdminResourceQuery {
  positionId?: number
  skillCode?: string
  resourceType?: ResourceType
  page?: number
  size?: number
}

export interface AdminPositionQuery {
  page?: number
  size?: number
}

export interface AdminUserListItem extends UserVO {
  /** 后端 Page<UserVO> 当前只返回 UserVO 字段，保留别名便于组件复用 */
  role: Role
}

export interface DashboardTrendPoint {
  date: string
  sessionCount: number
  avgScore?: number | null
}

export type { InterviewMode, InterviewStatus, QuestionStatus, QuestionType, ResourceType }
