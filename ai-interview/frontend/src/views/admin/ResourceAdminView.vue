<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import {
  adminCreateResource,
  adminDeleteResource,
  adminListPositions,
  adminListResources,
  adminUpdateResource,
} from '@/api/admin'
import { errorMessage, isBizError } from '@/api/http'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import { DEFAULT_PAGE_SIZES, usePagination } from '@/composables/usePagination'
import type { LearningResource, PositionAdminVO, ResourceUpsertPayload } from '@/types/admin'
import type { ResourceType } from '@/types/interview'
import { parsePositiveInt, parseResourceType, RESOURCE_TYPE_OPTIONS } from '@/utils/admin'
import { formatDateTime } from '@/utils/format'
import { isSafeExternalUrl } from '@/utils/url'

/**
 * 管理端学习资源（F5-5，计划 §5.18）：
 * 岗位/技能/类型筛选 + 分页 + 新增/编辑/下架（enabled→0）；
 * skillCode 下拉随岗位能力模型变化，保证与推荐引擎匹配口径一致。
 */
const route = useRoute()
const router = useRouter()

const positions = ref<PositionAdminVO[]>([])
const positionsLoading = ref(true)
const positionsError = ref<unknown>(null)

const positionFilter = ref<number | null>(null)
const skillFilter = ref('')
const typeFilter = ref<ResourceType | ''>('')

const pagination = usePagination<LearningResource>({
  fetcher: ({ page, size }) =>
    adminListResources({
      positionId: positionFilter.value ?? undefined,
      skillCode: skillFilter.value || undefined,
      resourceType: typeFilter.value || undefined,
      page,
      size,
    }),
  pageSizes: [...DEFAULT_PAGE_SIZES],
  initialPage: 1,
  initialSize: DEFAULT_PAGE_SIZES[0],
})
const { page, size, total, pages, records, loading, error, load, refresh } = pagination

const dialogVisible = ref(false)
const editing = ref<LearningResource | null>(null)
const saving = ref(false)
const formError = ref('')
const formRef = ref<FormInstance>()

interface ResourceFormModel {
  positionId: number | null
  skillCode: string
  title: string
  resourceType: ResourceType
  contentUrl: string
  difficulty: number
  enabled: number
}

const form = reactive<ResourceFormModel>({
  positionId: null,
  skillCode: '',
  title: '',
  resourceType: 'ARTICLE',
  contentUrl: '',
  difficulty: 2,
  enabled: 1,
})

let routeLoading = false
let pendingRoute = false

const errorText = computed(() => errorMessage(error.value, '学习资源加载失败，请稍后重试'))
const errorCode = computed(() => (isBizError(error.value) ? error.value.code : undefined))
const errorTrace = computed(() => (isBizError(error.value) ? error.value.traceId : ''))
const positionsErrorText = computed(() =>
  errorMessage(positionsError.value, '岗位列表加载失败，请稍后重试'),
)
const dialogTitle = computed(() =>
  editing.value ? `编辑资源 #${editing.value.id}` : '新增学习资源',
)
const hasFilters = computed(
  () => positionFilter.value != null || skillFilter.value !== '' || typeFilter.value !== '',
)
const filterSkillOptions = computed(() => {
  if (positionFilter.value != null) {
    return (positions.value.find((item) => item.id === positionFilter.value)?.skills ?? []).map(
      (skill) => ({ code: skill.skillCode, name: skill.skillName }),
    )
  }
  const map = new Map<string, string>()
  for (const position of positions.value) {
    for (const skill of position.skills ?? []) {
      if (!map.has(skill.skillCode)) map.set(skill.skillCode, skill.skillName)
    }
  }
  return [...map.entries()].map(([code, name]) => ({ code, name }))
})
const formSkillOptions = computed(() => {
  const options = (positions.value.find((item) => item.id === form.positionId)?.skills ?? []).map(
    (skill) => ({ code: skill.skillCode, name: skill.skillName }),
  )
  if (form.skillCode && !options.some((item) => item.code === form.skillCode)) {
    options.push({ code: form.skillCode, name: form.skillCode })
  }
  return options
})

const rules: FormRules = {
  positionId: [{ required: true, message: '请选择岗位', trigger: 'change' }],
  skillCode: [{ required: true, message: '请选择技能', trigger: 'change' }],
  title: [
    { required: true, message: '资源标题必填', trigger: 'blur' },
    { max: 200, message: '资源标题最长 200 字', trigger: 'blur' },
  ],
  resourceType: [{ required: true, message: '请选择资源类型', trigger: 'change' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  contentUrl: [
    {
      validator: (_rule, value: string, callback) => {
        const text = (value ?? '').trim()
        if (text.length > 500) {
          callback(new Error('资源链接最长 500 字'))
          return
        }
        if (text && !isSafeExternalUrl(text)) {
          callback(new Error('资源链接需以 http:// 或 https:// 开头'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

function toResource(row: unknown): LearningResource {
  return row as LearningResource
}

function positionName(id: number): string {
  return positions.value.find((item) => item.id === id)?.name ?? `岗位 #${id}`
}

function skillName(code: string, positionId: number): string {
  const skill = positions.value
    .find((item) => item.id === positionId)
    ?.skills?.find((item) => item.skillCode === code)
  return skill?.skillName ?? code
}

function resourceTypeLabel(type: ResourceType): string {
  return RESOURCE_TYPE_OPTIONS.find((item) => item.value === type)?.label ?? type
}

function statusLabel(enabled: number): string {
  return enabled === 1 ? '上架' : '已下架'
}

async function loadPositions(): Promise<void> {
  positionsLoading.value = true
  positionsError.value = null
  try {
    const result = await adminListPositions({ page: 1, size: 100 })
    positions.value = result.records ?? []
  } catch (cause) {
    positionsError.value = cause
    positions.value = []
  } finally {
    positionsLoading.value = false
  }
}

interface QueryState {
  page: number
  size: number
  positionId: number | null
  skillCode: string
  resourceType: ResourceType | ''
}

function parseSize(value: unknown): number {
  const parsed = Number(Array.isArray(value) ? value[0] : value)
  return DEFAULT_PAGE_SIZES.includes(parsed as (typeof DEFAULT_PAGE_SIZES)[number])
    ? parsed
    : DEFAULT_PAGE_SIZES[0]
}

function parseSkill(value: unknown): string {
  const raw = Array.isArray(value) ? value[0] : value
  return typeof raw === 'string' ? raw.trim() : ''
}

function parseQuery(): QueryState {
  return {
    page: parsePositiveInt(route.query.page) ?? 1,
    size: parseSize(route.query.size),
    positionId: parsePositiveInt(route.query.positionId),
    skillCode: parseSkill(route.query.skillCode),
    resourceType: parseResourceType(route.query.resourceType),
  }
}

function currentQuery(): QueryState {
  return {
    page: page.value,
    size: size.value,
    positionId: positionFilter.value,
    skillCode: skillFilter.value,
    resourceType: typeFilter.value,
  }
}

function buildQuery(query: QueryState): Record<string, string> {
  const result: Record<string, string> = {}
  for (const [key, value] of Object.entries(route.query)) {
    if (typeof value === 'string') result[key] = value
  }
  result.page = String(query.page)
  result.size = String(query.size)
  if (query.positionId != null) result.positionId = String(query.positionId)
  else delete result.positionId
  if (query.skillCode) result.skillCode = query.skillCode
  else delete result.skillCode
  if (query.resourceType) result.resourceType = query.resourceType
  else delete result.resourceType
  return result
}

function replaceQuery(query: QueryState): void {
  void router.replace({ name: 'admin-resources', query: buildQuery(query) })
}

function sameQuery(left: QueryState, right: QueryState): boolean {
  return (
    left.page === right.page &&
    left.size === right.size &&
    left.positionId === right.positionId &&
    left.skillCode === right.skillCode &&
    left.resourceType === right.resourceType
  )
}

async function applyRoute(force = false): Promise<void> {
  if (routeLoading) {
    pendingRoute = true
    return
  }
  const query = parseQuery()
  if (!force && sameQuery(query, currentQuery())) return
  routeLoading = true
  positionFilter.value = query.positionId
  skillFilter.value = query.skillCode
  typeFilter.value = query.resourceType
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

function handlePage(next: number): void {
  if (next === page.value) return
  replaceQuery({ ...currentQuery(), page: next })
}

function handleSize(next: number): void {
  if (next === size.value) return
  replaceQuery({ ...currentQuery(), page: 1, size: next })
}

function handlePositionFilter(value: number | undefined): void {
  const positionId = value ?? null
  const skills = (positions.value.find((item) => item.id === positionId)?.skills ?? []).map(
    (skill) => skill.skillCode,
  )
  const nextSkill = skillFilter.value && skills.includes(skillFilter.value) ? skillFilter.value : ''
  replaceQuery({
    ...currentQuery(),
    page: 1,
    positionId,
    skillCode: nextSkill,
  })
}

function handleSkillFilter(value: string | undefined): void {
  replaceQuery({ ...currentQuery(), page: 1, skillCode: value ?? '' })
}

function handleTypeFilter(value: ResourceType | undefined): void {
  replaceQuery({ ...currentQuery(), page: 1, resourceType: value ?? '' })
}

function clearFilters(): void {
  replaceQuery({ page: 1, size: size.value, positionId: null, skillCode: '', resourceType: '' })
}

function resetForm(): void {
  Object.assign(form, {
    positionId: null,
    skillCode: '',
    title: '',
    resourceType: 'ARTICLE' as ResourceType,
    contentUrl: '',
    difficulty: 2,
    enabled: 1,
  })
  formError.value = ''
  nextTick(() => formRef.value?.clearValidate())
}

function openCreate(): void {
  editing.value = null
  resetForm()
  form.positionId = positionFilter.value ?? positions.value[0]?.id ?? null
  const firstSkill = (positions.value.find((item) => item.id === form.positionId)?.skills ?? [])[0]
  form.skillCode = firstSkill?.skillCode ?? ''
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row: unknown): void {
  const resource = toResource(row)
  editing.value = resource
  Object.assign(form, {
    positionId: resource.positionId,
    skillCode: resource.skillCode,
    title: resource.title,
    resourceType: resource.resourceType,
    contentUrl: resource.contentUrl ?? '',
    difficulty: resource.difficulty,
    enabled: resource.enabled,
  })
  formError.value = ''
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function handleFormPositionChange(value: number): void {
  const codes = new Set(
    (positions.value.find((item) => item.id === value)?.skills ?? []).map(
      (skill) => skill.skillCode,
    ),
  )
  if (!codes.has(form.skillCode)) {
    form.skillCode = [...codes][0] ?? ''
  }
}

async function save(): Promise<void> {
  if (!formRef.value || saving.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!form.positionId) {
    formError.value = '请选择岗位'
    return
  }
  const payload: ResourceUpsertPayload = {
    positionId: form.positionId,
    skillCode: form.skillCode,
    title: form.title.trim(),
    resourceType: form.resourceType,
    difficulty: form.difficulty,
    enabled: form.enabled,
  }
  const contentUrl = form.contentUrl.trim()
  if (contentUrl) payload.contentUrl = contentUrl

  saving.value = true
  formError.value = ''
  try {
    if (editing.value) {
      await adminUpdateResource(editing.value.id, payload)
      ElMessage.success('学习资源已更新')
    } else {
      await adminCreateResource(payload)
      ElMessage.success('学习资源已新增')
    }
    dialogVisible.value = false
    await refresh()
  } catch (cause) {
    formError.value = errorMessage(cause, '学习资源保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

async function removeResource(row: unknown): Promise<void> {
  const resource = toResource(row)
  try {
    await ElMessageBox.confirm(
      `确认下架资源 #${resource.id}「${resource.title}」？下架后推荐引擎不再命中，可在编辑中重新启用。`,
      '下架学习资源',
      {
        type: 'warning',
        confirmButtonText: '下架',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }
  try {
    await adminDeleteResource(resource.id)
    ElMessage.success('资源已下架（enabled=0）')
    await refresh()
  } catch (cause) {
    ElMessage.error(errorMessage(cause, '下架失败，请稍后重试'))
  }
}

onMounted(async () => {
  await loadPositions()
  await applyRoute(true)
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
  <div class="resource-page admin-page" data-testid="resource-page">
    <AppPageHeader
      title="学习资源"
      description="维护推荐引擎使用的文章/题目/练习/项目资源；下架为 enabled=0，不物理删除。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="refresh">刷新</el-button>
        <el-button
          type="primary"
          :icon="Plus"
          :disabled="positions.length === 0"
          data-testid="resource-add"
          @click="openCreate"
        >
          新增资源
        </el-button>
      </template>
    </AppPageHeader>

    <div v-if="positionsError" class="admin-note admin-note--warning">
      <div>
        <strong>岗位数据不可用</strong>
        <div>
          {{ positionsErrorText }}
          <el-button link type="primary" @click="loadPositions">重试</el-button>
        </div>
      </div>
    </div>

    <GlassPanel :padded="false">
      <div class="admin-toolbar">
        <div class="admin-toolbar__filters">
          <el-select
            :model-value="positionFilter ?? undefined"
            class="admin-filter"
            placeholder="全部岗位"
            clearable
            :loading="positionsLoading"
            data-testid="resource-filter-position"
            @change="handlePositionFilter"
          >
            <el-option
              v-for="item in positions"
              :key="item.id"
              :value="item.id"
              :label="`${item.name}（${item.code}）`"
            />
          </el-select>
          <el-select
            :model-value="skillFilter || undefined"
            class="admin-filter"
            placeholder="全部技能"
            clearable
            filterable
            data-testid="resource-filter-skill"
            @change="handleSkillFilter"
          >
            <el-option
              v-for="item in filterSkillOptions"
              :key="item.code"
              :value="item.code"
              :label="`${item.name}（${item.code}）`"
            />
          </el-select>
          <el-select
            :model-value="typeFilter || undefined"
            class="admin-filter"
            placeholder="全部类型"
            clearable
            data-testid="resource-filter-type"
            @change="handleTypeFilter"
          >
            <el-option
              v-for="item in RESOURCE_TYPE_OPTIONS"
              :key="item.value"
              :value="item.value"
              :label="item.label"
            />
          </el-select>
          <el-button v-if="hasFilters" text type="primary" @click="clearFilters">
            清除筛选
          </el-button>
        </div>
        <div class="admin-toolbar__spacer" />
        <p class="admin-toolbar__note">
          共 {{ total }} 条 · 第 {{ page }} / {{ Math.max(1, pages) }} 页 · 筛选与分页已同步到地址栏
        </p>
      </div>
      <p class="admin-scroll-hint">表格可左右滑动查看资源字段与操作列</p>

      <LoadingSkeleton v-if="loading && records.length === 0" type="list" :rows="6" />

      <ErrorState
        v-else-if="error"
        title="学习资源加载失败"
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
            class="admin-table admin-table--resources"
            data-testid="resource-table"
            :header-cell-style="{ background: 'transparent' }"
          >
            <el-table-column prop="id" label="ID" width="64" />

            <el-table-column label="岗位" min-width="150">
              <template #default="{ row }">
                <div class="admin-cell-stack">
                  <span class="admin-cell-title">
                    {{ positionName(toResource(row).positionId) }}
                  </span>
                  <span class="admin-cell-sub">#{{ toResource(row).positionId }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="技能" min-width="130">
              <template #default="{ row }">
                <div class="admin-cell-stack">
                  <span class="admin-cell-title">
                    {{ skillName(toResource(row).skillCode, toResource(row).positionId) }}
                  </span>
                  <span class="admin-cell-sub">{{ toResource(row).skillCode }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="标题" min-width="216" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="admin-cell-clamp">{{ toResource(row).title }}</span>
              </template>
            </el-table-column>

            <el-table-column label="类型" width="80">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">
                  {{ resourceTypeLabel(toResource(row).resourceType) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="难度" width="68" align="center">
              <template #default="{ row }">
                <span class="admin-muted">{{ toResource(row).difficulty }}</span>
              </template>
            </el-table-column>

            <el-table-column label="状态" width="84">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="toResource(row).enabled === 1 ? 'success' : 'info'"
                  effect="plain"
                >
                  {{ statusLabel(toResource(row).enabled) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="创建时间" width="132">
              <template #default="{ row }">
                <span class="admin-muted">{{ formatDateTime(toResource(row).createdAt) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="148" fixed="right">
              <template #default="{ row }">
                <div class="admin-row-actions">
                  <el-button
                    text
                    type="primary"
                    size="small"
                    :icon="Edit"
                    :data-testid="`resource-edit-${toResource(row).id}`"
                    @click.stop="openEdit(row)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    v-if="toResource(row).enabled === 1"
                    text
                    type="danger"
                    size="small"
                    :icon="Delete"
                    :data-testid="`resource-delete-${toResource(row).id}`"
                    @click.stop="removeResource(row)"
                  >
                    下架
                  </el-button>
                </div>
              </template>
            </el-table-column>

            <template #empty>
              <div class="admin-empty admin-empty--inline">
                <EmptyState
                  title="没有匹配的学习资源"
                  description="调整筛选条件，或新增一条资源；下架资源仍会出现在本列表中。"
                  :show-action="false"
                />
                <el-button v-if="hasFilters" type="primary" plain @click="clearFilters">
                  清除筛选
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

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="min(760px, 92vw)"
      :close-on-click-modal="false"
      destroy-on-close
      data-testid="resource-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="admin-form-grid">
          <el-form-item label="岗位" prop="positionId">
            <el-select
              v-model="form.positionId"
              placeholder="请选择岗位"
              filterable
              data-testid="resource-form-position"
              @change="handleFormPositionChange"
            >
              <el-option
                v-for="item in positions"
                :key="item.id"
                :value="item.id"
                :label="`${item.name}（${item.code}）`"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="技能（须属于岗位能力模型）" prop="skillCode">
            <el-select
              v-model="form.skillCode"
              placeholder="请选择技能"
              filterable
              data-testid="resource-form-skill"
            >
              <el-option
                v-for="item in formSkillOptions"
                :key="item.code"
                :value="item.code"
                :label="`${item.name}（${item.code}）`"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="资源标题" prop="title" class="admin-form-grid__full">
            <el-input
              v-model="form.title"
              maxlength="200"
              show-word-limit
              placeholder="如 Spring 事务传播行为专题练习"
              data-testid="resource-form-title"
            />
          </el-form-item>

          <el-form-item label="资源类型" prop="resourceType">
            <el-select v-model="form.resourceType" data-testid="resource-form-type">
              <el-option
                v-for="item in RESOURCE_TYPE_OPTIONS"
                :key="item.value"
                :value="item.value"
                :label="item.label"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="难度（1~5）" prop="difficulty">
            <el-select v-model="form.difficulty" data-testid="resource-form-difficulty">
              <el-option v-for="level in 5" :key="level" :value="level" :label="`难度 ${level}`" />
            </el-select>
          </el-form-item>

          <el-form-item
            label="资源链接（可选，http/https）"
            prop="contentUrl"
            class="admin-form-grid__full"
          >
            <el-input
              v-model="form.contentUrl"
              maxlength="500"
              show-word-limit
              placeholder="https://…；留空则显示为站内资源"
              data-testid="resource-form-url"
            />
          </el-form-item>

          <el-form-item label="上架状态">
            <el-switch
              v-model="form.enabled"
              :active-value="1"
              :inactive-value="0"
              active-text="上架"
              inactive-text="下架"
              data-testid="resource-form-enabled"
            />
          </el-form-item>
        </div>
      </el-form>

      <p v-if="formError" class="admin-inline-alert" role="alert">{{ formError }}</p>

      <template #footer>
        <div class="admin-dialog-footer">
          <span class="admin-dialog-footer__hint"> 保存为全量替换；下架后可通过编辑重新启用 </span>
          <div>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button
              type="primary"
              :loading="saving"
              data-testid="resource-form-submit"
              @click="save"
            >
              {{ editing ? '保存修改' : '创建资源' }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.resource-page {
  min-width: 0;
}

.resource-page :deep(.el-form-item .el-select) {
  width: 100%;
}
</style>
