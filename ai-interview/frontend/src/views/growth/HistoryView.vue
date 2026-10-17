<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Clock, Refresh, Right } from '@element-plus/icons-vue'

import { getHistory } from '@/api/growth'
import { errorMessage, isBizError } from '@/api/http'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatusTag from '@/components/interview/StatusTag.vue'
import { DEFAULT_PAGE_SIZES, usePagination } from '@/composables/usePagination'
import type { HistoryItemVO } from '@/types/growth'
import { INTERVIEW_MODE_LABELS } from '@/utils/constants'
import { formatDateTime, formatScore } from '@/utils/format'
import { scoreTone } from '@/utils/score'

type StatusFilter = 'all' | 'active' | 'finished' | 'aborted'

const FILTER_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'active', label: '进行中' },
  { value: 'finished', label: '已完成' },
  { value: 'aborted', label: '已中止' },
]

const route = useRoute()
const router = useRouter()

const pagination = usePagination<HistoryItemVO>({
  fetcher: ({ page, size }) => getHistory({ page, size }),
  pageSizes: [...DEFAULT_PAGE_SIZES],
  initialPage: 1,
  initialSize: DEFAULT_PAGE_SIZES[0],
})

const { page, size, total, records, loading, error, load, refresh } = pagination

const filter = ref<StatusFilter>('all')
let routeLoading = false
let pendingRoute = false

const pageCounts = computed(() => {
  const counts: Record<StatusFilter, number> = { all: 0, active: 0, finished: 0, aborted: 0 }
  for (const item of records.value) {
    counts.all += 1
    const group = statusGroup(item.status)
    if (group) counts[group] += 1
  }
  return counts
})

const visibleRecords = computed(() => {
  if (filter.value === 'all') return records.value
  return records.value.filter((item) => statusGroup(item.status) === filter.value)
})

const errorText = computed(() => errorMessage(error.value, '面试历史加载失败，请稍后重试'))
const errorCode = computed(() => (isBizError(error.value) ? error.value.code : undefined))
const errorTrace = computed(() => (isBizError(error.value) ? error.value.traceId : ''))
const hasHistory = computed(() => total.value > 0)
const hasVisibleRecords = computed(() => visibleRecords.value.length > 0)

function statusGroup(status: HistoryItemVO['status']): Exclude<StatusFilter, 'all'> | null {
  if (status === 'RUNNING' || status === 'WAITING_USER' || status === 'PROCESSING_AI') {
    return 'active'
  }
  if (status === 'FINISHED' || status === 'TIMEOUT') return 'finished'
  if (status === 'ABORTED') return 'aborted'
  return null
}

/** Element Plus 表格插槽的 row 是宽泛的 DefaultRow，统一在边界收窄类型。 */
function toItem(row: unknown): HistoryItemVO {
  return row as HistoryItemVO
}

function parseFilter(value: unknown): StatusFilter {
  return value === 'active' || value === 'finished' || value === 'aborted' ? value : 'all'
}

function parseQuery(): { page: number; size: number; filter: StatusFilter } {
  const rawPage = Number(route.query.page)
  const rawSize = Number(route.query.size)
  return {
    page: Number.isInteger(rawPage) && rawPage > 0 ? rawPage : 1,
    size: DEFAULT_PAGE_SIZES.includes(rawSize as (typeof DEFAULT_PAGE_SIZES)[number])
      ? rawSize
      : DEFAULT_PAGE_SIZES[0],
    filter: parseFilter(route.query.status),
  }
}

function replaceQuery(
  nextPage: number,
  nextSize: number,
  nextFilter: StatusFilter = filter.value,
): void {
  const query: Record<string, string> = {
    ...(route.query as Record<string, string>),
    page: String(nextPage),
    size: String(nextSize),
  }
  if (nextFilter === 'all') delete query.status
  else query.status = nextFilter
  void router.replace({ name: 'history', query })
}

async function applyRoute(force = false): Promise<void> {
  if (routeLoading) {
    if (!force) pendingRoute = true
    return
  }
  const query = parseQuery()
  filter.value = query.filter
  if (!force && query.page === page.value && query.size === size.value) return

  routeLoading = true
  try {
    const result = await load(query.page, query.size)
    // 深链接页码越界时 usePagination 会回退到最后一页，同步回 URL。
    if (result && (pagination.page.value !== query.page || pagination.size.value !== query.size)) {
      replaceQuery(pagination.page.value, pagination.size.value, filter.value)
    }
  } finally {
    routeLoading = false
    if (pendingRoute) {
      pendingRoute = false
      void applyRoute(true)
    }
  }
}

function handlePage(nextPage: number): void {
  if (nextPage === page.value) return
  replaceQuery(nextPage, size.value)
}

function handleSize(nextSize: number): void {
  if (nextSize === size.value) return
  replaceQuery(1, nextSize)
}

function handleFilter(next: string | number | boolean | undefined): void {
  const normalized = parseFilter(next)
  if (normalized === filter.value) return
  filter.value = normalized
  replaceQuery(page.value, size.value, normalized)
}

function clearFilter(): void {
  handleFilter('all')
}

function isActive(row: unknown): boolean {
  return statusGroup(toItem(row).status) === 'active'
}

function actionLabel(row: unknown): string {
  const item = toItem(row)
  if (isActive(item)) return '继续面试'
  if (item.status === 'FINISHED' || item.status === 'TIMEOUT') return '查看报告'
  return '重新开始'
}

function handleAction(row: unknown): void {
  const item = toItem(row)
  if (isActive(item)) {
    void router.push({ name: 'interview-room', params: { id: String(item.sessionId) } })
    return
  }
  if (item.status === 'FINISHED' || item.status === 'TIMEOUT') {
    void router.push({ name: 'interview-report', params: { id: String(item.sessionId) } })
    return
  }
  void router.push({ name: 'interview-setup', query: { positionId: String(item.positionId) } })
}

function modeLabel(row: unknown): string {
  const item = toItem(row)
  return INTERVIEW_MODE_LABELS[item.mode] ?? item.mode
}

function roundLabel(row: unknown): string {
  const item = toItem(row)
  const planned = item.plannedRounds || 0
  const current = planned > 0 ? Math.min(Math.max(item.currentRound + 1, 1), planned) : 0
  return `${current} / ${planned}`
}

function scoreClass(row: unknown): string {
  return `score--${scoreTone(toItem(row).totalScore)}`
}

function startNew(): void {
  void router.push('/positions')
}

onMounted(() => {
  void applyRoute(true)
})

watch(
  () => `${String(route.query.page ?? '')}:${String(route.query.size ?? '')}`,
  () => {
    if (routeLoading) return
    void applyRoute()
  },
)
</script>

<template>
  <div class="history-page" data-testid="history-page">
    <AppPageHeader
      title="面试历史"
      description="所有已开始的面试场次（含进行中、已完成、已中止与超时）；按状态继续、查看报告或重新开始。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="refresh">刷新</el-button>
        <el-button type="primary" data-testid="history-start" @click="startNew">
          开始新面试
          <el-icon><Right /></el-icon>
        </el-button>
      </template>
    </AppPageHeader>

    <GlassPanel class="history-panel" :padded="false">
      <div class="history-toolbar">
        <el-radio-group
          :model-value="filter"
          class="history-filter"
          data-testid="history-filter"
          @change="handleFilter"
        >
          <el-radio-button
            v-for="option in FILTER_OPTIONS"
            :key="option.value"
            :value="option.value"
            :label="`${option.label} ${pageCounts[option.value]}`"
          />
        </el-radio-group>
        <p class="history-toolbar__note">
          状态筛选只作用于当前页；{{ total }} 场中当前第 {{ page }} 页、每页 {{ size }} 条。
        </p>
      </div>
      <p class="history-scroll-hint">表格可左右滑动查看状态与操作列</p>

      <LoadingSkeleton v-if="loading && records.length === 0" type="list" :rows="6" />

      <ErrorState
        v-else-if="error"
        title="面试历史加载失败"
        :message="errorText"
        :code="errorCode"
        :trace-id="errorTrace"
        @retry="refresh"
      />

      <template v-else>
        <div v-if="!hasHistory" class="history-empty" data-testid="history-empty">
          <EmptyState
            title="还没有面试记录"
            description="完成第一场模拟面试后，这里会展示分数、状态与复盘入口。"
            action-text="选择岗位开始面试"
            @action="startNew"
          />
        </div>

        <template v-else>
          <div class="history-table-wrap">
            <el-table
              :data="visibleRecords"
              row-key="sessionId"
              class="history-table"
              data-testid="history-table"
              :header-cell-style="{ background: 'transparent' }"
            >
              <el-table-column label="岗位" min-width="190">
                <template #default="{ row }">
                  <div class="history-position">
                    <strong>{{
                      toItem(row).positionName || `岗位 #${toItem(row).positionId}`
                    }}</strong>
                    <span>会话 #{{ toItem(row).sessionId }}</span>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="模式" width="92">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain" round>{{ modeLabel(row) }}</el-tag>
                </template>
              </el-table-column>

              <el-table-column label="难度" width="72" align="center">
                <template #default="{ row }">{{ toItem(row).difficulty }}</template>
              </el-table-column>

              <el-table-column label="轮次" width="88" align="center">
                <template #default="{ row }">
                  <span class="history-round">{{ roundLabel(row) }}</span>
                </template>
              </el-table-column>

              <el-table-column label="状态" width="124">
                <template #default="{ row }">
                  <StatusTag :status="toItem(row).status" size="small" />
                </template>
              </el-table-column>

              <el-table-column label="总分" width="92" align="right">
                <template #default="{ row }">
                  <strong v-if="toItem(row).totalScore != null" :class="scoreClass(row)">
                    {{ formatScore(toItem(row).totalScore) }}
                  </strong>
                  <span v-else class="history-muted">—</span>
                </template>
              </el-table-column>

              <el-table-column label="开始时间" min-width="150">
                <template #default="{ row }">
                  <span class="history-time">{{ formatDateTime(toItem(row).startedAt) }}</span>
                </template>
              </el-table-column>

              <el-table-column label="操作" width="122" fixed="right">
                <template #default="{ row }">
                  <el-button
                    text
                    type="primary"
                    size="small"
                    :data-testid="`history-action-${toItem(row).sessionId}`"
                    @click.stop="handleAction(row)"
                  >
                    {{ actionLabel(row) }}
                  </el-button>
                </template>
              </el-table-column>

              <template #empty>
                <div class="history-empty history-empty--inline">
                  <p>
                    当前页没有"{{
                      FILTER_OPTIONS.find((item) => item.value === filter)?.label
                    }}"状态的记录。
                  </p>
                  <el-button text type="primary" @click="clearFilter">查看全部</el-button>
                </div>
              </template>
            </el-table>
          </div>

          <PaginationBar
            :page="page"
            :size="size"
            :total="total"
            :disabled="loading"
            @update:page="handlePage"
            @update:size="handleSize"
          />
        </template>
      </template>
    </GlassPanel>

    <p v-if="hasHistory && !hasVisibleRecords" class="history-tip">
      <el-icon><Clock /></el-icon>
      当前页没有匹配记录；切换分页或筛选条件可查看其他场次。
    </p>
  </div>
</template>

<style scoped>
.history-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding-bottom: var(--space-6);
}

.history-panel {
  min-width: 0;
}

.history-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid rgba(255, 255, 255, 0.46);
}

.history-filter :deep(.el-radio-button__inner) {
  border-color: rgba(148, 163, 184, 0.26);
  background: rgba(255, 255, 255, 0.36);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.history-filter :deep(.el-radio-button.is-active .el-radio-button__inner) {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: #fff;
}

.history-toolbar__note {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.history-scroll-hint {
  display: none;
}

.history-table-wrap {
  overflow-x: auto;
}

.history-table {
  min-width: 880px;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: transparent;
  --el-table-border-color: rgba(148, 163, 184, 0.18);
  --el-table-row-hover-bg-color: rgba(37, 99, 235, 0.06);
  background: transparent;
}

.history-table :deep(th.el-table__cell) {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.history-table :deep(td.el-table__cell) {
  background: transparent;
}

.history-position {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.history-position strong {
  overflow: hidden;
  color: var(--color-text);
  font-size: var(--font-size-sm);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-position span,
.history-time,
.history-muted {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.history-round {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.history-empty {
  padding: var(--space-4) 0;
}

.history-empty--inline {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.history-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

@media (max-width: 768px) {
  .history-toolbar {
    padding: var(--space-3) var(--space-4);
  }

  .history-scroll-hint {
    display: block;
    padding: var(--space-2) var(--space-4) 0;
    color: var(--color-text-tertiary);
    font-size: var(--font-size-xs);
  }

  .history-filter {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .history-filter :deep(.el-radio-button__inner) {
    border-radius: var(--radius-pill);
  }
}
</style>
