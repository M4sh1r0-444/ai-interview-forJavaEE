import { describe, expect, it } from 'vitest'

import { EXTERNAL_LINK_ATTRS, isSafeExternalUrl } from '@/utils/url'

describe('utils/url', () => {
  it('仅放行 http/https 外链', () => {
    expect(isSafeExternalUrl('https://dev.java/learn/oop/')).toBe(true)
    expect(isSafeExternalUrl('http://example.com/a?b=1')).toBe(true)
    expect(isSafeExternalUrl('javascript:alert(1)')).toBe(false)
    expect(isSafeExternalUrl('data:text/html,<script>alert(1)</script>')).toBe(false)
    expect(isSafeExternalUrl('/positions/1')).toBe(false)
    expect(isSafeExternalUrl('//evil.example.com')).toBe(false)
    expect(isSafeExternalUrl('')).toBe(false)
    expect(isSafeExternalUrl(null)).toBe(false)
    expect(isSafeExternalUrl(undefined)).toBe(false)
  })

  it('外链属性带 noopener noreferrer', () => {
    expect(EXTERNAL_LINK_ATTRS).toEqual({ target: '_blank', rel: 'noopener noreferrer' })
  })
})
