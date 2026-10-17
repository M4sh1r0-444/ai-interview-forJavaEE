import { describe, expect, it } from 'vitest'

import type { InterviewDetailVO, MessageVO } from '@/types/interview'
import {
  computeAwaitingAnswer,
  computeDisplayRound,
  computeFollowupCount,
  computeRoundSnapshot,
  countAnsweredRounds,
  currentQuestionOfRound,
  isActiveStatus,
  isTerminalStatus,
  messagesOfRound,
} from '@/utils/round'

function detail(overrides: Partial<InterviewDetailVO> = {}): InterviewDetailVO {
  return {
    id: 1,
    userId: 2,
    positionId: 1,
    positionName: 'Java 后端开发工程师',
    mode: 'TEXT',
    difficulty: 3,
    plannedRounds: 5,
    currentRound: 0,
    status: 'WAITING_USER',
    ...overrides,
  }
}

let messageId = 100
function message(
  speaker: 'AI' | 'USER',
  roundNo: number,
  contentText = `${speaker}-${roundNo}`,
  overrides: Partial<MessageVO> = {},
): MessageVO {
  messageId += 1
  return {
    id: messageId,
    sessionId: 1,
    roundNo,
    speaker,
    contentText,
    createdAt: '2026-10-17T20:00:00',
    ...overrides,
  }
}

describe('round utils', () => {
  it('区分终态与进行中状态', () => {
    expect(isTerminalStatus('FINISHED')).toBe(true)
    expect(isTerminalStatus('TIMEOUT')).toBe(true)
    expect(isTerminalStatus('ABORTED')).toBe(true)
    expect(isTerminalStatus('WAITING_USER')).toBe(false)
    expect(isTerminalStatus(null)).toBe(false)
    expect(isActiveStatus('CREATED')).toBe(true)
    expect(isActiveStatus('PROCESSING_AI')).toBe(true)
    expect(isActiveStatus('FINISHED')).toBe(false)
  })

  it('按轮次过滤消息并统计追问次数（0~3）', () => {
    const messages = [
      message('AI', 0),
      message('USER', 0),
      message('AI', 0),
      message('USER', 0),
      message('AI', 0),
      message('USER', 0),
      message('AI', 0),
      message('AI', 1),
    ]
    expect(messagesOfRound(messages, 0)).toHaveLength(7)
    expect(computeFollowupCount(messages, 0)).toBe(3)
    expect(computeFollowupCount(messages, 1)).toBe(0)
    expect(computeFollowupCount([], 0)).toBe(0)
  })

  it('仅当 WAITING_USER、AI 多于 USER 且最后一条是 AI 时可作答', () => {
    const aiFirst = [message('AI', 0)]
    expect(computeAwaitingAnswer(detail(), aiFirst)).toBe(true)

    expect(computeAwaitingAnswer(detail({ status: 'PROCESSING_AI' }), aiFirst)).toBe(false)
    expect(computeAwaitingAnswer(detail(), [message('AI', 0), message('USER', 0)])).toBe(false)
    expect(computeAwaitingAnswer(detail(), [])).toBe(false)
    expect(computeAwaitingAnswer(detail(), [message('USER', 0), message('AI', 0)])).toBe(false)
    expect(
      computeAwaitingAnswer(detail(), [
        message('AI', 0),
        message('USER', 0),
        message('AI', 0),
        message('USER', 0),
        message('AI', 0),
        message('USER', 0),
      ]),
    ).toBe(false)
  })

  it('忽略隐藏的未来轮次消息，主问题预写不导致误判', () => {
    const messages = [message('AI', 0), message('AI', 1), message('AI', 2)]
    // 当前轮 0：1 条 AI 1 条 USER，不可作答
    expect(computeAwaitingAnswer(detail(), [messages[0], message('USER', 0), messages[1]])).toBe(
      false,
    )
    // 未来轮次不计入当前轮判据
    expect(computeAwaitingAnswer(detail(), [messages[0]])).toBe(true)
  })

  it('显示轮次：进行中 currentRound+1，FINISHED/TIMEOUT 为 plannedRounds', () => {
    expect(computeDisplayRound(detail({ currentRound: 0 }))).toBe(1)
    expect(computeDisplayRound(detail({ currentRound: 4 }))).toBe(5)
    expect(computeDisplayRound(detail({ currentRound: 3, plannedRounds: 8 }))).toBe(4)
    expect(computeDisplayRound(detail({ status: 'FINISHED', currentRound: 0 }))).toBe(5)
    expect(computeDisplayRound(detail({ status: 'TIMEOUT', currentRound: 2 }))).toBe(5)
    // ABORTED 不按 plannedRounds 展示
    expect(computeDisplayRound(detail({ status: 'ABORTED', currentRound: 1 }))).toBe(2)
    expect(computeDisplayRound(null)).toBe(0)
  })

  it('当前问题取当前轮最后一条 AI 消息', () => {
    const messages = [
      message('AI', 0, '主问题'),
      message('USER', 0),
      message('AI', 0, '追问一'),
      message('AI', 0, '追问二'),
      message('AI', 1, '下一题'),
    ]
    expect(currentQuestionOfRound(messages, 0)?.contentText).toBe('追问二')
    expect(currentQuestionOfRound(messages, 2)).toBeNull()
  })

  it('统计已作答轮次与快照', () => {
    const messages = [
      message('AI', 0),
      message('USER', 0),
      message('AI', 0),
      message('AI', 1),
      message('USER', 1),
      message('USER', 1),
    ]
    expect(countAnsweredRounds(messages)).toBe(2)
    const snapshot = computeRoundSnapshot(detail(), messages)
    expect(snapshot.roundNo).toBe(0)
    expect(snapshot.followupCount).toBe(1)
    expect(snapshot.displayRound).toBe(1)
    expect(snapshot.awaitingAnswer).toBe(false)
    expect(snapshot.currentQuestion?.contentText).toBe('AI-0')
  })
})
