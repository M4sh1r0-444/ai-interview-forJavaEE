export interface PositionVO {
  id: number
  code: string
  name: string
  description?: string | null
  defaultRounds: number
  skillCount?: number | null
}

export interface SkillVO {
  skillCode: string
  skillName: string
  description?: string | null
  /** 技能权重，岗位内合计为 1 */
  weight: number
  requiredLevel?: number | null
}
