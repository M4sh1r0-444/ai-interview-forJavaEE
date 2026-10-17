import { describe, expect, it } from 'vitest'

import {
  clearAnswerDraft,
  clearSessionDrafts,
  readAnswerDraft,
  writeAnswerDraft,
} from '@/utils/draft'
import { readRaw } from '@/utils/storage'

describe('interview draft', () => {
  it('按 sessionId:roundNo 隔离写入与读取', () => {
    writeAnswerDraft(10, 0, '第一题草稿')
    writeAnswerDraft(10, 1, '第二题草稿')
    writeAnswerDraft(11, 0, '另一场草稿')

    expect(readAnswerDraft(10, 0)).toBe('第一题草稿')
    expect(readAnswerDraft(10, 1)).toBe('第二题草稿')
    expect(readAnswerDraft(11, 0)).toBe('另一场草稿')
    expect(readAnswerDraft(10, 2)).toBe('')
  })

  it('写入空内容等价于删除草稿', () => {
    writeAnswerDraft(10, 0, '内容')
    writeAnswerDraft(10, 0, '')
    expect(readAnswerDraft(10, 0)).toBe('')
    expect(readRaw('interview-draft:10:0')).toBeNull()
  })

  it('可清除指定轮次与会话全部轮次草稿', () => {
    writeAnswerDraft(10, 0, 'a')
    writeAnswerDraft(10, 1, 'b')
    writeAnswerDraft(10, 5, 'c')
    writeAnswerDraft(12, 0, 'other')

    clearAnswerDraft(10, 0)
    expect(readAnswerDraft(10, 0)).toBe('')
    expect(readAnswerDraft(10, 1)).toBe('b')

    clearSessionDrafts(10, 5)
    expect(readAnswerDraft(10, 1)).toBe('')
    expect(readAnswerDraft(10, 5)).toBe('')
    expect(readAnswerDraft(12, 0)).toBe('other')
  })

  it('轮次参数异常时不会抛出（上限钳制）', () => {
    expect(() => clearSessionDrafts(10, -3)).not.toThrow()
    expect(() => clearSessionDrafts(10, 999)).not.toThrow()
  })
})
