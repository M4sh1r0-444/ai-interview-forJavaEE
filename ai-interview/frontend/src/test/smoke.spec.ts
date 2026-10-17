import { describe, expect, it } from 'vitest'

describe('test harness', () => {
  it('runs vitest with jsdom', () => {
    expect(typeof window).toBe('object')
    expect(localStorage.length).toBe(0)
  })
})
