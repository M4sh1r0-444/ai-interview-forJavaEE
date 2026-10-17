import type { InterviewDetailVO, InterviewStatus, MessageVO } from '@/types/interview'

/**
 * 面试轮次与"是否可作答"算法（前端计划 §5.7.3 / §7.11）。
 *
 * 铁律：
 * - 轮次只以服务端 detail.currentRound 为准，禁止用消息总数推断；
 * - 追问不增加 currentRound；主问题在 start 时已预写，未来轮次被后端隐藏；
 * - 可作答 = WAITING_USER + 当前轮 AI 消息数 > USER 消息数 + 最后一条是 AI。
 */

export const TERMINAL_STATUSES: InterviewStatus[] = ['FINISHED', 'ABORTED', 'TIMEOUT']

export function isTerminalStatus(status?: InterviewStatus | null): boolean {
  return status != null && TERMINAL_STATUSES.includes(status)
}

export function isActiveStatus(status?: InterviewStatus | null): boolean {
  return status === 'CREATED' || status === 'WAITING_USER' || status === 'PROCESSING_AI'
}

export function messagesOfRound(messages: MessageVO[], roundNo: number): MessageVO[] {
  return messages.filter((message) => message.roundNo === roundNo)
}

/** 当前轮追问次数：AI 消息数 - 1（主问题），clamp 0~3；追问不增加轮次。 */
export function computeFollowupCount(messages: MessageVO[], roundNo: number): number {
  const aiCount = messagesOfRound(messages, roundNo).filter(
    (message) => message.speaker === 'AI',
  ).length
  return Math.max(0, Math.min(3, aiCount - 1))
}

/** 当前轮最后一条 AI 提问（主问题或追问）。 */
export function currentQuestionOfRound(messages: MessageVO[], roundNo: number): MessageVO | null {
  const aiMessages = messagesOfRound(messages, roundNo).filter(
    (message) => message.speaker === 'AI',
  )
  return aiMessages.length > 0 ? aiMessages[aiMessages.length - 1] : null
}

/**
 * 是否处于"等待用户作答"：以服务端状态为主，消息数做二次确认。
 * PROCESSING_AI 期间永远不可作答。
 */
export function computeAwaitingAnswer(
  detail?: InterviewDetailVO | null,
  messages: MessageVO[] = [],
): boolean {
  if (!detail || detail.status !== 'WAITING_USER') return false
  const roundMessages = messagesOfRound(messages, detail.currentRound)
  const aiCount = roundMessages.filter((message) => message.speaker === 'AI').length
  const userCount = roundMessages.filter((message) => message.speaker === 'USER').length
  const lastMessage = messages.length > 0 ? messages[messages.length - 1] : null
  return aiCount > userCount && lastMessage?.speaker === 'AI'
}

/**
 * 进度展示：FINISHED/TIMEOUT 显示 N/N；其余显示 currentRound+1（clamp）。
 * 注意 currentRound 是 0-based，追问不推进它。
 */
export function computeDisplayRound(detail?: InterviewDetailVO | null): number {
  if (!detail) return 0
  if (detail.status === 'FINISHED' || detail.status === 'TIMEOUT') {
    return detail.plannedRounds
  }
  return Math.max(1, Math.min(detail.currentRound + 1, detail.plannedRounds))
}

/** 已完成作答的主问题数（用于结束确认弹窗的"已作答 x 题"）。 */
export function countAnsweredRounds(messages: MessageVO[]): number {
  const rounds = new Set<number>()
  for (const message of messages) {
    if (message.speaker === 'USER') rounds.add(message.roundNo)
  }
  return rounds.size
}

/** 轻量快照，便于 store 与组件测试复用。 */
export interface RoundSnapshot {
  roundNo: number
  followupCount: number
  displayRound: number
  awaitingAnswer: boolean
  currentQuestion: MessageVO | null
}

export function computeRoundSnapshot(
  detail?: InterviewDetailVO | null,
  messages: MessageVO[] = [],
): RoundSnapshot {
  const roundNo = detail?.currentRound ?? 0
  return {
    roundNo,
    followupCount: computeFollowupCount(messages, roundNo),
    displayRound: computeDisplayRound(detail),
    awaitingAnswer: computeAwaitingAnswer(detail, messages),
    currentQuestion: currentQuestionOfRound(messages, roundNo),
  }
}
