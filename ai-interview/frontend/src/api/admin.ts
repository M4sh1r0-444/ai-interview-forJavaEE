import { cleanParams, request } from '@/api/http'
import type { PageResult } from '@/types/api'
import type { UserVO } from '@/types/auth'
import type {
  AdminPositionQuery,
  AdminQuestionQuery,
  AdminResourceQuery,
  AdminUserQuery,
  DashboardVO,
  KnowledgeChunk,
  KnowledgeUpsertPayload,
  LearningResource,
  PositionAdminVO,
  PositionUpsertPayload,
  QuestionAdminVO,
  QuestionUpsertPayload,
  ResourceUpsertPayload,
  WeightsUpdatePayload,
} from '@/types/admin'

// ===== 看板 =====

export function adminDashboard(): Promise<DashboardVO> {
  return request<DashboardVO>({ url: '/api/admin/dashboard', method: 'GET' })
}

// ===== 题库（无 status 过滤；更新需 version） =====

export function adminListQuestions(
  params: AdminQuestionQuery = {},
): Promise<PageResult<QuestionAdminVO>> {
  return request<PageResult<QuestionAdminVO>>({
    url: '/api/admin/questions',
    method: 'GET',
    params: cleanParams({
      positionId: params.positionId,
      type: params.type,
      difficulty: params.difficulty,
      page: params.page,
      size: params.size,
    }),
  })
}

export function adminCreateQuestion(payload: QuestionUpsertPayload): Promise<QuestionAdminVO> {
  return request<QuestionAdminVO>({ url: '/api/admin/questions', method: 'POST', data: payload })
}

export function adminUpdateQuestion(
  id: number,
  payload: QuestionUpsertPayload,
): Promise<QuestionAdminVO> {
  return request<QuestionAdminVO>({
    url: `/api/admin/questions/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** 逻辑删：status → DISABLED，不物理删除。 */
export function adminDeleteQuestion(id: number): Promise<void> {
  return request<void>({ url: `/api/admin/questions/${id}`, method: 'DELETE' })
}

// ===== 岗位与能力模型 =====

export function adminListPositions(
  params: AdminPositionQuery = {},
): Promise<PageResult<PositionAdminVO>> {
  return request<PageResult<PositionAdminVO>>({
    url: '/api/admin/positions',
    method: 'GET',
    params: cleanParams({ page: params.page, size: params.size }),
  })
}

export function adminGetPosition(id: number): Promise<PositionAdminVO> {
  return request<PositionAdminVO>({ url: `/api/admin/positions/${id}`, method: 'GET' })
}

export function adminCreatePosition(payload: PositionUpsertPayload): Promise<PositionAdminVO> {
  return request<PositionAdminVO>({ url: '/api/admin/positions', method: 'POST', data: payload })
}

/** 部分更新：仅非 null 字段生效；code 不可改。 */
export function adminUpdatePosition(
  id: number,
  payload: PositionUpsertPayload,
): Promise<PositionAdminVO> {
  return request<PositionAdminVO>({
    url: `/api/admin/positions/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** body 为 { scoreWeights: {...} }（不是 { weights }，见前端计划 §2.5#12）。 */
export function adminUpdateWeights(
  id: number,
  payload: WeightsUpdatePayload,
): Promise<PositionAdminVO> {
  return request<PositionAdminVO>({
    url: `/api/admin/positions/${id}/weights`,
    method: 'PUT',
    data: payload,
  })
}

// ===== 学习资源（含已下架；更新为全量替换） =====

export function adminListResources(
  params: AdminResourceQuery = {},
): Promise<PageResult<LearningResource>> {
  return request<PageResult<LearningResource>>({
    url: '/api/admin/resources',
    method: 'GET',
    params: cleanParams({
      positionId: params.positionId,
      skillCode: params.skillCode,
      resourceType: params.resourceType,
      page: params.page,
      size: params.size,
    }),
  })
}

export function adminCreateResource(payload: ResourceUpsertPayload): Promise<LearningResource> {
  return request<LearningResource>({ url: '/api/admin/resources', method: 'POST', data: payload })
}

export function adminUpdateResource(
  id: number,
  payload: ResourceUpsertPayload,
): Promise<LearningResource> {
  return request<LearningResource>({
    url: `/api/admin/resources/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** 下架（enabled→0），非物理删。 */
export function adminDeleteResource(id: number): Promise<void> {
  return request<void>({ url: `/api/admin/resources/${id}`, method: 'DELETE' })
}

// ===== 知识库（后端无列表接口，仅新增与重建索引） =====

export function adminCreateKnowledge(payload: KnowledgeUpsertPayload): Promise<KnowledgeChunk> {
  return request<KnowledgeChunk>({ url: '/api/admin/knowledge', method: 'POST', data: payload })
}

export function adminRebuildKnowledge(): Promise<{ rebuilt: boolean }> {
  return request<{ rebuilt: boolean }>({ url: '/api/admin/knowledge/rebuild', method: 'POST' })
}

// ===== 用户管理 =====

export function adminListUsers(params: AdminUserQuery = {}): Promise<PageResult<UserVO>> {
  return request<PageResult<UserVO>>({
    url: '/api/admin/users',
    method: 'GET',
    params: cleanParams({ keyword: params.keyword, page: params.page, size: params.size }),
  })
}

/** status: 0=禁用，1=启用；后端禁止禁用自己。 */
export function adminUpdateUserStatus(id: number, status: 0 | 1): Promise<UserVO> {
  return request<UserVO>({
    url: `/api/admin/users/${id}/status`,
    method: 'PUT',
    data: { status },
  })
}
