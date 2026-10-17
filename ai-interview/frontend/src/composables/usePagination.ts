import { ref, type Ref } from 'vue'

import type { PageResult } from '@/types/api'

export interface PaginationQuery {
  page: number
  size: number
}

export interface UsePaginationOptions<T> {
  fetcher: (query: PaginationQuery) => Promise<PageResult<T>>
  /** 允许的每页条数；默认 10/20/50（计划 §5.9）。 */
  pageSizes?: number[]
  initialPage?: number
  initialSize?: number
}

export interface UsePaginationResult<T> {
  page: Ref<number>
  size: Ref<number>
  total: Ref<number>
  pages: Ref<number>
  records: Ref<T[]>
  loading: Ref<boolean>
  error: Ref<unknown>
  load: (page?: number, size?: number) => Promise<PageResult<T> | null>
  changePage: (next: number) => Promise<PageResult<T> | null>
  changeSize: (next: number) => Promise<PageResult<T> | null>
  refresh: () => Promise<PageResult<T> | null>
  reset: () => void
}

export const DEFAULT_PAGE_SIZES = [10, 20, 50] as const

function normalizePage(value: unknown, fallback = 1): number {
  const parsed = Math.trunc(Number(value))
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

function normalizeSize(value: unknown, pageSizes: number[], fallback = pageSizes[0] ?? 10): number {
  const parsed = Math.trunc(Number(value))
  return pageSizes.includes(parsed) ? parsed : fallback
}

/**
 * 通用分页（计划 §6.5）：请求序号防竞态、越界页自动回退、size 变化回到第 1 页。
 * URL 同步由页面负责（HistoryView），保证组件不依赖路由。
 */
export function usePagination<T>(options: UsePaginationOptions<T>): UsePaginationResult<T> {
  const pageSizes = [...(options.pageSizes ?? DEFAULT_PAGE_SIZES)]
  const initialSize = normalizeSize(options.initialSize, pageSizes)
  const initialPage = normalizePage(options.initialPage)

  const page = ref(initialPage)
  const size = ref(initialSize)
  const total = ref(0)
  const pages = ref(0)
  const records = ref<T[]>([]) as Ref<T[]>
  const loading = ref(false)
  const error = ref<unknown>(null)

  let requestSeq = 0

  async function load(
    targetPage: number = page.value,
    targetSize: number = size.value,
  ): Promise<PageResult<T> | null> {
    const nextPage = normalizePage(targetPage)
    const nextSize = normalizeSize(targetSize, pageSizes)
    const seq = ++requestSeq
    loading.value = true
    error.value = null

    try {
      const result = await options.fetcher({ page: nextPage, size: nextSize })
      if (seq !== requestSeq) return null

      const resultPages = Math.max(0, Math.trunc(Number(result.pages) || 0))
      // 删除数据/深链接越界时回到最后一页，避免停在空白页。
      if (resultPages > 0 && nextPage > resultPages) {
        return load(resultPages, nextSize)
      }

      page.value = nextPage
      size.value = nextSize
      records.value = Array.isArray(result.records) ? result.records : []
      total.value = Math.max(0, Math.trunc(Number(result.total) || 0))
      pages.value = resultPages
      loading.value = false
      return result
    } catch (cause) {
      if (seq !== requestSeq) return null
      error.value = cause
      records.value = []
      total.value = 0
      pages.value = 0
      loading.value = false
      return null
    }
  }

  function changePage(next: number): Promise<PageResult<T> | null> {
    const normalized = normalizePage(next, page.value)
    if (normalized === page.value) return Promise.resolve(null)
    return load(normalized, size.value)
  }

  function changeSize(next: number): Promise<PageResult<T> | null> {
    const normalized = normalizeSize(next, pageSizes)
    if (normalized === size.value) return Promise.resolve(null)
    size.value = normalized
    page.value = 1
    return load(1, normalized)
  }

  function refresh(): Promise<PageResult<T> | null> {
    return load(page.value, size.value)
  }

  function reset(): void {
    requestSeq += 1
    page.value = initialPage
    size.value = initialSize
    total.value = 0
    pages.value = 0
    records.value = []
    loading.value = false
    error.value = null
  }

  return {
    page,
    size,
    total,
    pages,
    records,
    loading,
    error,
    load,
    changePage,
    changeSize,
    refresh,
    reset,
  }
}
