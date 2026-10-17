import { request } from '@/api/http'
import type { PositionVO, SkillVO } from '@/types/position'

/** 启用岗位列表（无 skillCount）。 */
export function listPositions(): Promise<PositionVO[]> {
  return request<PositionVO[]>({ url: '/api/positions', method: 'GET' })
}

/** 岗位详情（含 skillCount）。 */
export function getPosition(id: number): Promise<PositionVO> {
  return request<PositionVO>({ url: `/api/positions/${id}`, method: 'GET' })
}

/** 技能/权重/要求等级。 */
export function getPositionSkills(id: number): Promise<SkillVO[]> {
  return request<SkillVO[]>({ url: `/api/positions/${id}/skills`, method: 'GET' })
}
