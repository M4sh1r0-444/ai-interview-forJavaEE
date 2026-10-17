<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { adminListUsers, adminUpdateUserStatus } from '@/api/admin'
import { errorMessage, isBizError } from '@/api/http'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import { DEFAULT_PAGE_SIZES, usePagination } from '@/composables/usePagination'
import { useAuthStore } from '@/stores/auth'
import type { UserVO } from '@/types/auth'
import { ROLE_LABELS } from '@/utils/constants'
import { parsePositiveInt } from '@/utils/admin'
import { formatNumber } from '@/utils/format'

/**
 * 管理端用户管理（F5-6，计划 §5.19）：
 * keyword 搜索（用户名/真实姓名）+ 分页 + 启用/禁用；当前登录账号标记「当前」且禁止自禁用
 * （后端同样返回 40001 兜底）。无新增/删除。
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const keyword = ref('')
const searchKeyword = ref('')
const updatingId = ref<number | null>(null)

const pagination = usePagination<UserVO>({
  fetcher: ({ page, size }) => adminListUsers({ keyword: keyword.value || undefined, page, size }),
  pageSizes: [...DEFAULT_PAGE_SIZES],
  initialPage: 1,
  initialSize: DEFAULT_PAGE_SIZES[0],
})
const { page, size, total, pages, records, loading, error, load, refresh } = pagination

let routeLoading = false
let pendingRoute = false

const errorText = computed(() => errorMessage(error.value, '用户列表加载失败，请稍后重试'))
const errorCode = computed(() => (isBizError(error.value) ? error.value.code : undefined))
const errorTrace = computed(() => (isBizError(error.value) ? error.value.traceId : ''))
const currentUserId = computed(() => auth.user?.id ?? null)
const hasKeyword = computed(() => keyword.value.trim() !== '')
const resultSummary = computed(() =>
  hasKeyword.value
    ? `关键词「${keyword.value}」命中 ${total.value} 个账号`
    : `全部 ${total.value} 个账号`,
)

function toUser(row: unknown): UserVO {
  return row as UserVO
}

function isSelf(row: unknown): boolean {
  return toUser(row).id === currentUserId.value
}

function roleLabel(row: unknown): string {
  const role = toUser(row).role
  return ROLE_LABELS[role] ?? role
}

function statusLabel(row: unknown): string {
  return toUser(row).status === 1 ? '启用' : '禁用'
}

interface QueryState {
  page: number
  size: number
  keyword: string
}

function parseSize(value: unknown): number {
  const parsed = Number(Array.isArray(value) ? value[0] : value)
  return DEFAULT_PAGE_SIZES.includes(parsed as (typeof DEFAULT_PAGE_SIZES)[number])
    ? parsed
    : DEFAULT_PAGE_SIZES[0]
}

function parseQuery(): QueryState {
  const raw = route.query.keyword
  const rawKeyword = Array.isArray(raw) ? raw[0] : raw
  return {
    page: parsePositiveInt(route.query.page) ?? 1,
    size: parseSize(route.query.size),
    keyword: typeof rawKeyword === 'string' ? rawKeyword.trim() : '',
  }
}

function currentQuery(): QueryState {
  return { page: page.value, size: size.value, keyword: keyword.value }
}

function buildQuery(query: QueryState): Record<string, string> {
  const result: Record<string, string> = {}
  for (const [key, value] of Object.entries(route.query)) {
    if (typeof value === 'string') result[key] = value
  }
  result.page = String(query.page)
  result.size = String(query.size)
  if (query.keyword) result.keyword = query.keyword
  else delete result.keyword
  return result
}

function replaceQuery(query: QueryState): void {
  void router.replace({ name: 'admin-users', query: buildQuery(query) })
}

async function applyRoute(force = false): Promise<void> {
  if (routeLoading) {
    pendingRoute = true
    return
  }
  const query = parseQuery()
  if (
    !force &&
    query.page === page.value &&
    query.size === size.value &&
    query.keyword === keyword.value
  ) {
    return
  }
  routeLoading = true
  keyword.value = query.keyword
  searchKeyword.value = query.keyword
  try {
    const result = await load(query.page, query.size)
    if (result && (page.value !== query.page || size.value !== query.size)) {
      replaceQuery({ ...query, page: page.value, size: size.value })
    }
  } finally {
    routeLoading = false
    if (pendingRoute) {
      pendingRoute = false
      void applyRoute(true)
    }
  }
}

function applySearch(): void {
  const next = searchKeyword.value.trim()
  if (next === keyword.value && page.value === 1) {
    void refresh()
    return
  }
  replaceQuery({ page: 1, size: size.value, keyword: next })
}

function clearSearch(): void {
  searchKeyword.value = ''
  if (keyword.value === '') {
    void refresh()
    return
  }
  replaceQuery({ page: 1, size: size.value, keyword: '' })
}

function handlePage(next: number): void {
  if (next === page.value) return
  replaceQuery({ ...currentQuery(), page: next })
}

function handleSize(next: number): void {
  if (next === size.value) return
  replaceQuery({ ...currentQuery(), page: 1, size: next })
}

async function handleStatusChange(row: unknown, enabled: boolean): Promise<void> {
  const user = toUser(row)
  if (isSelf(user)) return
  const nextStatus = enabled ? 1 : 0
  try {
    await ElMessageBox.confirm(
      `确认${enabled ? '启用' : '禁用'}账号「${user.username}」？${
        enabled
          ? '启用后该账号可正常登录。'
          : '禁用后该账号登录将返回 10003，已有登录态在下次请求时失效。'
      }`,
      enabled ? '启用账号' : '禁用账号',
      {
        type: enabled ? 'info' : 'warning',
        confirmButtonText: enabled ? '启用' : '禁用',
        cancelButtonText: '取消',
        confirmButtonClass: enabled ? '' : 'el-button--danger',
      },
    )
  } catch {
    return
  }
  updatingId.value = user.id
  try {
    const updated = await adminUpdateUserStatus(user.id, nextStatus === 1 ? 1 : 0)
    records.value = records.value.map((item) => (item.id === updated.id ? updated : item))
    ElMessage.success(`账号「${updated.username}」已${updated.status === 1 ? '启用' : '禁用'}`)
  } catch (cause) {
    ElMessage.error(errorMessage(cause, '账号状态修改失败，请稍后重试'))
    await refresh()
  } finally {
    updatingId.value = null
  }
}

onMounted(() => {
  void applyRoute(true)
})

watch(
  () => route.fullPath,
  () => {
    if (routeLoading) return
    void applyRoute()
  },
)
</script>

<template>
  <div class="user-page admin-page" data-testid="user-page">
    <AppPageHeader
      title="用户管理"
      description="查看平台账号并启用/禁用；当前登录账号不可禁用（后端同样兜底）。不提供新增/删除。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="refresh">刷新</el-button>
      </template>
    </AppPageHeader>

    <GlassPanel :padded="false">
      <div class="admin-toolbar">
        <div class="user-search">
          <el-input
            v-model="searchKeyword"
            class="user-search__input"
            placeholder="搜索用户名或真实姓名"
            clearable
            :prefix-icon="Search"
            data-testid="user-search"
            @keyup.enter="applySearch"
            @clear="clearSearch"
          />
          <el-button type="primary" data-testid="user-search-button" @click="applySearch">
            搜索
          </el-button>
        </div>
        <div class="admin-toolbar__spacer" />
        <p class="admin-toolbar__note">
          {{ resultSummary }} · 第 {{ page }} / {{ Math.max(1, pages) }} 页
        </p>
      </div>
      <p class="admin-scroll-hint">表格可左右滑动查看角色与操作列</p>

      <LoadingSkeleton v-if="loading && records.length === 0" type="list" :rows="6" />

      <ErrorState
        v-else-if="error"
        title="用户列表加载失败"
        :message="errorText"
        :code="errorCode"
        :trace-id="errorTrace"
        @retry="refresh"
      />

      <template v-else>
        <div class="admin-table-wrap">
          <el-table
            :data="records"
            row-key="id"
            class="admin-table admin-table--users"
            data-testid="user-table"
            :header-cell-style="{ background: 'transparent' }"
          >
            <el-table-column prop="id" label="ID" width="70" />

            <el-table-column label="用户名" min-width="190">
              <template #default="{ row }">
                <div class="user-name">
                  <strong>{{ toUser(row).username }}</strong>
                  <el-tag
                    v-if="isSelf(row)"
                    size="small"
                    type="primary"
                    effect="light"
                    data-testid="user-self-tag"
                  >
                    当前
                  </el-tag>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="真实姓名" min-width="130">
              <template #default="{ row }">
                <span>{{ toUser(row).realName || '—' }}</span>
              </template>
            </el-table-column>

            <el-table-column label="角色" width="96">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="toUser(row).role === 'ADMIN' ? 'warning' : 'info'"
                  effect="plain"
                >
                  {{ roleLabel(row) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="专业" min-width="140">
              <template #default="{ row }">
                <span class="admin-muted">{{ toUser(row).major || '—' }}</span>
              </template>
            </el-table-column>

            <el-table-column label="状态" width="92">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="toUser(row).status === 1 ? 'success' : 'info'"
                  effect="plain"
                >
                  {{ statusLabel(row) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-tooltip
                  :disabled="!isSelf(row)"
                  content="不能禁用当前登录的管理员账号"
                  placement="top"
                >
                  <span class="user-switch">
                    <el-switch
                      :model-value="toUser(row).status === 1"
                      :loading="updatingId === toUser(row).id"
                      :disabled="isSelf(row) || updatingId !== null"
                      :active-text="toUser(row).status === 1 ? '启用' : '禁用'"
                      :data-testid="`user-status-${toUser(row).id}`"
                      @change="
                        (value: string | number | boolean) =>
                          handleStatusChange(row, value === true || value === 1 || value === '1')
                      "
                    />
                  </span>
                </el-tooltip>
              </template>
            </el-table-column>

            <template #empty>
              <div class="admin-empty">
                <EmptyState
                  :title="hasKeyword ? '没有匹配的账号' : '还没有用户'"
                  :description="
                    hasKeyword
                      ? '换个用户名/真实姓名关键词，或清空搜索查看全部账号。'
                      : '当前平台尚无注册账号。'
                  "
                  :show-action="false"
                />
                <el-button v-if="hasKeyword" text type="primary" @click="clearSearch">
                  清空搜索
                </el-button>
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
    </GlassPanel>

    <div class="admin-note">
      <div>
        <strong>说明</strong>
        <div>
          禁用账号后，该用户登录返回 10003；已有登录态在下次请求时由后端拦截。管理员不能禁用自己，
          以免锁死管理入口。当前共 {{ formatNumber(total) }} 个账号。
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.user-page {
  min-width: 0;
}

.user-search {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.user-search__input {
  width: 280px;
  max-width: 100%;
}

.user-name {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.user-name strong {
  overflow: hidden;
  color: var(--color-text);
  font-size: var(--font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-switch {
  display: inline-flex;
}

@media (max-width: 768px) {
  .user-search {
    width: 100%;
  }

  .user-search__input {
    flex: 1;
    width: auto;
  }
}
</style>
