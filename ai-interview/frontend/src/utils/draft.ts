import { readRaw, removeRaw, writeRaw } from '@/utils/storage'

/**
 * 面试作答草稿：按 sessionId:roundNo 隔离，刷新页面后可恢复（计划 §5.7.4）。
 * 只使用统一 storage 封装，不直接访问 localStorage。
 */
function draftKey(sessionId: number, roundNo: number): string {
  return `interview-draft:${sessionId}:${roundNo}`
}

export function readAnswerDraft(sessionId: number, roundNo: number): string {
  return readRaw(draftKey(sessionId, roundNo)) ?? ''
}

export function writeAnswerDraft(sessionId: number, roundNo: number, content: string): void {
  if (!content) {
    removeRaw(draftKey(sessionId, roundNo))
    return
  }
  writeRaw(draftKey(sessionId, roundNo), content)
}

export function clearAnswerDraft(sessionId: number, roundNo: number): void {
  removeRaw(draftKey(sessionId, roundNo))
}

/** 终态/重置时清理该会话所有轮次草稿；轮次上限按后端 5~8 留足余量。 */
export function clearSessionDrafts(sessionId: number, plannedRounds = 8): void {
  const upper = Math.max(0, Math.min(20, plannedRounds))
  for (let round = 0; round <= upper; round += 1) {
    clearAnswerDraft(sessionId, round)
  }
}
