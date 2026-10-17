import { cleanParams, request } from '@/api/http'
import type { HistoryItemVO, HistoryQuery, RecommendationVO, SkillGrowthVO } from '@/types/growth'
import type { PageResult } from '@/types/api'

/** 我的面试历史（含进行中/中止/超时），时间倒序。 */
export function getHistory(params: HistoryQuery = {}): Promise<PageResult<HistoryItemVO>> {
  return request<PageResult<HistoryItemVO>>({
    url: '/api/users/me/history',
    method: 'GET',
    params: cleanParams({ page: params.page, size: params.size }),
  })
}

/** 每技能成长曲线（records 升序 + current）。 */
export function getGrowth(positionId?: number): Promise<SkillGrowthVO[]> {
  return request<SkillGrowthVO[]>({
    url: '/api/users/me/growth',
    method: 'GET',
    params: positionId == null ? undefined : { positionId },
  })
}

/** 短板 Top3 + 资源/练习/错题/下场建议。 */
export function getRecommendations(positionId?: number): Promise<RecommendationVO[]> {
  return request<RecommendationVO[]>({
    url: '/api/users/me/recommendations',
    method: 'GET',
    params: positionId == null ? undefined : { positionId },
  })
}
