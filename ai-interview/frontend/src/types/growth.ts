import type { InterviewMode, InterviewStatus, ResourceType } from '@/types/interview'

export interface HistoryQuery {
  page?: number
  size?: number
}

export interface HistoryItemVO {
  sessionId: number
  positionId: number
  positionName?: string | null
  mode: InterviewMode
  difficulty: number
  plannedRounds: number
  currentRound: number
  status: InterviewStatus
  totalScore?: number | null
  startedAt?: string | null
  finishedAt?: string | null
}

export interface GrowthPoint {
  evaluatedAt: string
  score: number
}

export interface SkillGrowthVO {
  skillCode: string
  skillName: string
  records: GrowthPoint[]
  current: number
}

export interface ResourceVO {
  id: number
  title: string
  resourceType: ResourceType
  contentUrl?: string | null
  difficulty: number
}

export interface RetryQuestionVO {
  questionId: number
  questionContent: string
  missingPoints: string[]
}

export interface RecommendationVO {
  skillCode: string
  skillName: string
  priority: number
  weaknessReasons: string[]
  resources: ResourceVO[]
  practices: ResourceVO[]
  retryQuestions: RetryQuestionVO[]
  nextInterviewSuggested?: string
}
