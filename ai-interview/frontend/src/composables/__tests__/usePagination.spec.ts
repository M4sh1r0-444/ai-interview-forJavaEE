import { describe, expect, it, vi } from 'vitest'

import { usePagination } from '@/composables/usePagination'
import type { PageResult } from '@/types/api'

interface Row {
  id: number
}

function pageResult(records: Row[], overrides: Partial<PageResult<Row>> = {}): PageResult<Row> {
  return {
    records,
    total: records.length,
    size: 10,
    current: 1,
    pages: 1,
    ...overrides,
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('usePagination', () => {
  it('加载并暴露 page/size/total/records', async () => {
    const fetcher = vi.fn().mockResolvedValue(pageResult([{ id: 1 }, { id: 2 }]))
    const pagination = usePagination<Row>({ fetcher })

    const result = await pagination.load()
    expect(result?.records).toHaveLength(2)
    expect(fetcher).toHaveBeenCalledWith({ page: 1, size: 10 })
    expect(pagination.page.value).toBe(1)
    expect(pagination.size.value).toBe(10)
    expect(pagination.total.value).toBe(2)
    expect(pagination.loading.value).toBe(false)
    expect(pagination.error.value).toBeNull()
  })

  it('翻页与切换每页条数（size 变化回到第 1 页）', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(pageResult([{ id: 1 }], { total: 25, pages: 3 }))
      .mockResolvedValueOnce(pageResult([{ id: 2 }], { total: 25, pages: 3, current: 2 }))
      .mockResolvedValueOnce(pageResult([{ id: 3 }], { total: 25, pages: 2, size: 20 }))
    const pagination = usePagination<Row>({ fetcher })

    await pagination.load()
    await pagination.changePage(2)
    expect(fetcher).toHaveBeenLastCalledWith({ page: 2, size: 10 })
    expect(pagination.page.value).toBe(2)

    await pagination.changeSize(20)
    expect(fetcher).toHaveBeenLastCalledWith({ page: 1, size: 20 })
    expect(pagination.page.value).toBe(1)
    expect(pagination.size.value).toBe(20)
  })

  it('页码越界自动回退到最后一页', async () => {
    const fetcher = vi.fn().mockImplementation(async ({ page }: { page: number }) => {
      if (page === 5) {
        return pageResult([], { total: 25, pages: 3, current: 5, size: 10 })
      }
      return pageResult([{ id: 30 }], { total: 25, pages: 3, current: 3, size: 10 })
    })
    const pagination = usePagination<Row>({ fetcher })

    await pagination.load(5, 10)
    expect(fetcher).toHaveBeenLastCalledWith({ page: 3, size: 10 })
    expect(pagination.page.value).toBe(3)
    expect(pagination.records.value).toHaveLength(1)
  })

  it('竞态时只采用最后一次请求结果', async () => {
    const first = deferred<PageResult<Row>>()
    const second = deferred<PageResult<Row>>()
    const fetcher = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const pagination = usePagination<Row>({ fetcher })

    const firstTask = pagination.load(1, 10)
    const secondTask = pagination.load(2, 10)

    second.resolve(pageResult([{ id: 2 }], { current: 2, total: 25, pages: 3 }))
    await secondTask
    expect(pagination.records.value.map((row) => row.id)).toEqual([2])

    first.resolve(pageResult([{ id: 1 }], { current: 1, total: 25, pages: 3 }))
    await firstTask
    expect(pagination.records.value.map((row) => row.id)).toEqual([2])
    expect(pagination.page.value).toBe(2)
  })

  it('失败时记录错误、清空列表并结束 loading', async () => {
    const fetcher = vi.fn().mockRejectedValue(new Error('boom'))
    const pagination = usePagination<Row>({ fetcher })

    const result = await pagination.load()
    expect(result).toBeNull()
    expect(pagination.error.value).toBeInstanceOf(Error)
    expect(pagination.records.value).toEqual([])
    expect(pagination.total.value).toBe(0)
    expect(pagination.loading.value).toBe(false)
  })

  it('非法 page/size 被归一化', async () => {
    const fetcher = vi.fn().mockResolvedValue(pageResult([]))
    const pagination = usePagination<Row>({ fetcher, initialPage: -3, initialSize: 999 })

    expect(pagination.page.value).toBe(1)
    expect(pagination.size.value).toBe(10)
    await pagination.load(Number.NaN, 999)
    expect(fetcher).toHaveBeenCalledWith({ page: 1, size: 10 })
  })
})
