import type { InterviewStatus } from '@/types/interview'

export type WsEventType =
  'QUESTION' | 'FOLLOW_UP' | 'TRANSCRIPT' | 'TIMER' | 'PROGRESS' | 'REPORT_READY' | 'ERROR'

export interface WsEnvelope<T = unknown> {
  type: WsEventType
  payload: T
  ts: number
}

export interface QuestionPayload {
  roundNo: number
  followupCount: number
  content: string
  difficulty: number
  targetSkills: string[]
}

export interface TranscriptPayload {
  messageId: number
  asrText: string
}

export interface TimerPayload {
  remainSeconds: number
}

export interface ProgressPayload {
  status: InterviewStatus
  currentRound: number
  plannedRounds: number
}

export interface ReportReadyPayload {
  reportId: number
}

export interface ErrorPayload {
  code: number
  message: string
}

export type WsConnectionState = 'idle' | 'connecting' | 'open' | 'reconnecting' | 'closed'
