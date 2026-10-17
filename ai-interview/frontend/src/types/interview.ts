export type InterviewMode = 'TEXT' | 'AUDIO'

export type InterviewStatus =
  'CREATED' | 'RUNNING' | 'WAITING_USER' | 'PROCESSING_AI' | 'FINISHED' | 'ABORTED' | 'TIMEOUT'

export type QuestionType = 'TECHNICAL' | 'PROJECT' | 'SCENARIO' | 'BEHAVIORAL'

export type QuestionStatus = 'ENABLED' | 'DISABLED'

export type ResourceType = 'ARTICLE' | 'QUESTION' | 'PRACTICE' | 'PROJECT'

export interface InterviewDetailVO {
  id: number
  userId: number
  positionId: number
  positionName?: string | null
  mode: InterviewMode
  difficulty: number
  plannedRounds: number
  /** 0-based 当前主问题序号；FINISHED 后等于 plannedRounds */
  currentRound: number
  status: InterviewStatus
  totalScore?: number | null
  startedAt?: string | null
  finishedAt?: string | null
  createdAt?: string | null
  remainSeconds?: number | null
}

export interface MessageVO {
  id: number
  sessionId: number
  /** 主问题序号，0-based；追问不递增 */
  roundNo: number
  speaker: 'AI' | 'USER'
  inputType?: InterviewMode | null
  contentText: string
  audioPath?: string | null
  audioDurationMs?: number | null
  asrText?: string | null
  questionId?: number | null
  keywords?: string[] | null
  /** 0=主问题，1+=追问 */
  followupIndex?: number | null
  createdAt: string
}

export interface CreateInterviewPayload {
  positionId: number
  mode: InterviewMode
  difficulty: number
  rounds: number
  durationMinutes: number
}

export interface TextAnswerPayload {
  roundNo: number
  content: string
}

export interface AudioAnswerPayload {
  file: Blob
  roundNo: number
  asrText: string
  durationMs: number
  pauseCount?: number
}

/** POST messages/text|audio 的异步受理响应。 */
export interface SubmitAccepted {
  accepted: boolean
  roundNo: number
}

/** POST finish 的异步受理响应；reportId 可能为 null。 */
export interface FinishAccepted {
  accepted: boolean
  status: InterviewStatus
  reportId: number | null
}
