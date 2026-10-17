import type { InterviewMode, InterviewStatus } from '@/types/interview'

export type DimensionKey = 'technical' | 'depth' | 'logic' | 'match' | 'communication' | 'voice'

export interface ReportVO {
  reportId: number
  sessionId: number
  positionName?: string | null
  mode: InterviewMode
  status: InterviewStatus
  totalScore: number
  /** TEXT 场没有 voice 维度；后端可能省略空键 */
  dimensionScores: Partial<Record<DimensionKey, number>>
  summary?: string | null
  strengths: string[]
  weaknesses: string[]
  improvementPlan: string[]
  reportText?: string | null
  questionEvidence: QuestionEvidenceVO[]
  createdAt: string
}

export interface QuestionEvidenceVO {
  roundNo: number
  questionId: number
  questionContent: string
  answerExcerpt: string
  difficulty: number
  targetSkills: string[]
  scores: Partial<Record<DimensionKey, number>>
  contentScore: number
  coveredPoints: string[]
  missingPoints: string[]
  evidence: string[]
  reason: string
}
