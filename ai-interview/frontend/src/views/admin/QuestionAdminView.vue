<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import {
  adminCreateQuestion,
  adminDeleteQuestion,
  adminListPositions,
  adminListQuestions,
  adminUpdateQuestion,
} from '@/api/admin'
import { errorMessage, isBizError } from '@/api/http'
import JsonArrayEditor from '@/components/admin/JsonArrayEditor.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import { DEFAULT_PAGE_SIZES, usePagination } from '@/composables/usePagination'
import { BUSINESS_CODE } from '@/types/api'
import type { PositionAdminVO, QuestionAdminVO, QuestionUpsertPayload } from '@/types/admin'
import type { QuestionType } from '@/types/interview'
import {
  emptyQuestionForm,
  parseDifficulty,
  parseQuestionType,
  parsePositiveInt,
  QUESTION_STATUS_LABELS,
  QUESTION_TYPE_OPTIONS,
  questionFormToPayload,
  questionToForm,
  validateQuestionForm,
  type QuestionFormModel,
} from '@/utils/admin'
import { formatDateTime, truncate } from '@/utils/format'

/**
 * 管理端题库（F5-3，计划 §5.16）：
 * 岗位/题型/难度筛选 + 分页 + 新增/编辑/逻辑删；编辑携带 version，
 * 40003 时不丢弃草稿：拉取最新版本，让用户选择覆盖或放弃。
 */
const route = useRoute()
const router = useRouter()

const positions = ref<PositionAdminVO[]>([])
const positionsLoading = ref(true)
const positionsError = ref<unknown>(null)

const positionFilter = ref<number | null>(null)
const typeFilter = ref<QuestionType | ''>('')
const difficultyFilter = ref<number | null>(null)

const pagination = usePagination<QuestionAdminVO>({
  fetcher: ({ page, size }) =>
    adminListQuestions({
      positionId: positionFilter.value ?? undefined,
      type: typeFilter.value || undefined,
      difficulty: difficultyFilter.value ?? undefined,
      page,
      size,
    }),
  pageSizes: [...DEFAULT_PAGE_SIZES],
  initialPage: 1,
  initialSize: DEFAULT_PAGE_SIZES[0],
})
const { page, size, total, pages, records, loading, error, load, refresh } = pagination

const dialogVisible = ref(false)
const editing = ref<QuestionAdminVO | null>(null)
const saving = ref(false)
const formError = ref('')
const formRef = ref<FormInstance>()
const form = reactive<QuestionFormModel>(emptyQuestionForm())

const conflictVisible = ref(false)
const conflictLatest = ref<QuestionAdminVO | null>(null)
const conflictSaving = ref(false)

let routeLoading = false
let pendingRoute = false

const errorText = computed(() => errorMessage(error.value, '题库加载失败，请稍后重试'))
const errorCode = computed(() => (isBizError(error.value) ? error.value.code : undefined))
const errorTrace = computed(() => (isBizError(error.value) ? error.value.traceId : ''))
const positionsErrorText = computed(() =>
  errorMessage(positionsError.value, '岗位列表加载失败，请稍后重试'),
)
const dialogTitle = computed(() => (editing.value ? `编辑题目 #${editing.value.id}` : '新增题目'))
const hasFilters = computed(
  () => positionFilter.value != null || typeFilter.value !== '' || difficultyFilter.value != null,
)
const selectedPositionSkills = computed(() => {
  const position = positions.value.find((item) => item.id === form.positionId)
  return (position?.skills ?? []).map((skill) => ({
    code: skill.skillCode,
    name: skill.skillName,
  }))
})
const skillOptions = computed(() => {
  const options = [...selectedPositionSkills.value]
  for (const tag of form.skillTags) {
    if (!options.some((item) => item.code === tag)) options.push({ code: tag, name: tag })
  }
  return options
})
const conflictVersion = computed(() => conflictLatest.value?.version ?? null)
const localVersion = computed(() => editing.value?.version ?? form.version ?? null)

const rules: FormRules = {
  positionId: [{ required: true, message: '请选择岗位', trigger: 'change' }],
  type: [{ required: true, message: '请选择题型', trigger: 'change' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  content: [
    { required: true, message: '题目内容必填', trigger: 'blur' },
    { min: 4, max: 1000, message: '题目内容需为 4~1000 字', trigger: 'blur' },
  ],
  skillTags: [
    {
      type: 'array',
      required: true,
      min: 1,
      message: '至少选择 1 个技能标签',
      trigger: 'change',
    },
  ],
}

function toQuestion(row: unknown): QuestionAdminVO {
  return row as QuestionAdminVO
}

function positionName(id: number): string {
  return positions.value.find((item) => item.id === id)?.name ?? `岗位 #${id}`
}

function questionTypeLabel(type: QuestionType): string {
  return QUESTION_TYPE_OPTIONS.find((item) => item.value === type)?.label ?? type
}

function difficultyTagType(difficulty: number): 'success' | 'warning' | 'danger' | 'info' {
  if (difficulty >= 5) return 'danger'
  if (difficulty >= 4) return 'warning'
  if (difficulty <= 2) return 'success'
  return 'info'
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
  type: QuestionType | ''
  difficulty: number | null
}

function parseSize(value: unknown): number {
  const parsed = Number(Array.isArray(value) ? value[0] : value)
  return DEFAULT_PAGE_SIZES.includes(parsed as (typeof DEFAULT_PAGE_SIZES)[number])
    ? parsed
    : DEFAULT_PAGE_SIZES[0]
}

function parseQuery(): QueryState {
  return {
    page: parsePositiveInt(route.query.page) ?? 1,
    size: parseSize(route.query.size),
    positionId: parsePositiveInt(route.query.positionId),
    type: parseQuestionType(route.query.type),
    difficulty: parseDifficulty(route.query.difficulty),
  }
}

function currentQuery(): QueryState {
  return {
    page: page.value,
    size: size.value,
    positionId: positionFilter.value,
    type: typeFilter.value,
    difficulty: difficultyFilter.value,
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
  if (query.type) result.type = query.type
  else delete result.type
  if (query.difficulty != null) result.difficulty = String(query.difficulty)
  else delete result.difficulty
  return result
}

function replaceQuery(query: QueryState): void {
  void router.replace({ name: 'admin-questions', query: buildQuery(query) })
}

function sameQuery(left: QueryState, right: QueryState): boolean {
  return (
    left.page === right.page &&
    left.size === right.size &&
    left.positionId === right.positionId &&
    left.type === right.type &&
    left.difficulty === right.difficulty
  )
}

async function applyRoute(force = false): Promise<void> {
  if (routeLoading) {
    pendingRoute = true
    return
  }
  const query = parseQuery()
  const current = currentQuery()
  if (!force && sameQuery(query, current)) return

  routeLoading = true
  positionFilter.value = query.positionId
  typeFilter.value = query.type
  difficultyFilter.value = query.difficulty
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

function handlePositionFilter(value: number | null): void {
  replaceQuery({ ...currentQuery(), page: 1, positionId: value ?? null })
}

function handleTypeFilter(value: QuestionType | ''): void {
  replaceQuery({ ...currentQuery(), page: 1, type: value })
}

function handleDifficultyFilter(value: number | null): void {
  replaceQuery({ ...currentQuery(), page: 1, difficulty: value ?? null })
}

function clearFilters(): void {
  replaceQuery({ page: 1, size: size.value, positionId: null, type: '', difficulty: null })
}

function resetForm(): void {
  Object.assign(form, emptyQuestionForm())
  formError.value = ''
  nextTick(() => formRef.value?.clearValidate())
}

function openCreate(): void {
  editing.value = null
  resetForm()
  form.positionId = positionFilter.value ?? positions.value[0]?.id ?? null
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row: unknown): void {
  const question = toQuestion(row)
  editing.value = question
  Object.assign(form, questionToForm(question))
  formError.value = ''
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function handlePositionChange(value: number): void {
  const codes = new Set(
    (positions.value.find((item) => item.id === value)?.skills ?? []).map(
      (skill) => skill.skillCode,
    ),
  )
  form.skillTags = form.skillTags.filter((tag) => codes.has(tag))
}

async function submit(): Promise<void> {
  if (!formRef.value || saving.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  const messages = validateQuestionForm(form)
  if (messages.length > 0) {
    formError.value = messages[0]
    return
  }
  const payload = questionFormToPayload(form)
  saving.value = true
  formError.value = ''
  try {
    if (editing.value) {
      await adminUpdateQuestion(editing.value.id, payload)
      ElMessage.success('题目已更新')
    } else {
      await adminCreateQuestion(payload)
      ElMessage.success('题目已新增')
    }
    dialogVisible.value = false
    await refresh()
  } catch (cause) {
    if (isBizError(cause) && cause.code === BUSINESS_CODE.VERSION_CONFLICT) {
      await beginConflict(payload)
      return
    }
    formError.value = errorMessage(cause, '题目保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

async function fetchLatestQuestion(
  id: number,
  positionId: number,
): Promise<QuestionAdminVO | null> {
  let currentPage = 1
  let pages = 1
  while (currentPage <= pages) {
    const result = await adminListQuestions({ positionId, page: currentPage, size: 100 })
    const found = (result.records ?? []).find((item) => item.id === id)
    if (found) return found
    pages = Math.max(1, Number(result.pages) || 1)
    currentPage += 1
  }
  return null
}

async function beginConflict(payload: QuestionUpsertPayload): Promise<void> {
  if (!editing.value) {
    formError.value = '数据已被他人修改，请关闭后重新打开该题'
    return
  }
  try {
    const latest = await fetchLatestQuestion(editing.value.id, payload.positionId)
    if (!latest) {
      formError.value = '数据已被他人修改，且无法读取最新版本，请刷新列表后重试'
      return
    }
    conflictLatest.value = latest
    conflictVisible.value = true
  } catch (cause) {
    formError.value = errorMessage(cause, '读取最新题目失败，请稍后重试')
  }
}

async function overrideConflict(): Promise<void> {
  if (!editing.value || !conflictLatest.value || conflictSaving.value) return
  const payload = { ...questionFormToPayload(form), version: conflictLatest.value.version }
  conflictSaving.value = true
  try {
    await adminUpdateQuestion(editing.value.id, payload)
    ElMessage.success('已用你的内容覆盖最新版本并保存')
    conflictVisible.value = false
    dialogVisible.value = false
    await refresh()
  } catch (cause) {
    if (isBizError(cause) && cause.code === BUSINESS_CODE.VERSION_CONFLICT) {
      await beginConflict(payload)
      return
    }
    formError.value = errorMessage(cause, '覆盖保存失败，请稍后重试')
  } finally {
    conflictSaving.value = false
  }
}

async function discardConflict(): Promise<void> {
  conflictVisible.value = false
  dialogVisible.value = false
  ElMessage.info('已放弃本地修改，列表已刷新')
  await refresh()
}

async function removeQuestion(row: unknown): Promise<void> {
  const question = toQuestion(row)
  try {
    await ElMessageBox.confirm(
      `确认逻辑删除题目 #${question.id}「${truncate(question.content, 36)}」？删除后不参与组卷，数据仍保留在库中。`,
      '删除题目',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }
  try {
    await adminDeleteQuestion(question.id)
    ElMessage.success('题目已逻辑删除（DISABLED）')
    await refresh()
  } catch (cause) {
    ElMessage.error(errorMessage(cause, '删除失败，请稍后重试'))
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
  <div class="question-page admin-page" data-testid="question-page">
    <AppPageHeader
      title="题库管理"
      description="维护题目、参考答案与追问模板；删除为逻辑删除（DISABLED），编辑使用 version 乐观锁。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="refresh">刷新</el-button>
        <el-button
          type="primary"
          :icon="Plus"
          :disabled="positions.length === 0"
          data-testid="question-add"
          @click="openCreate"
        >
          新增题目
        </el-button>
      </template>
    </AppPageHeader>

    <div v-if="positionsError" class="admin-note admin-note--warning">
      <div>
        <strong>岗位筛选不可用</strong>
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
            data-testid="question-filter-position"
            @change="(value: number | undefined) => handlePositionFilter(value ?? null)"
          >
            <el-option
              v-for="item in positions"
              :key="item.id"
              :value="item.id"
              :label="`${item.name}（${item.code}）`"
            />
          </el-select>
          <el-select
            :model-value="typeFilter || undefined"
            class="admin-filter"
            placeholder="全部题型"
            clearable
            data-testid="question-filter-type"
            @change="(value: QuestionType | undefined) => handleTypeFilter(value ?? '')"
          >
            <el-option
              v-for="item in QUESTION_TYPE_OPTIONS"
              :key="item.value"
              :value="item.value"
              :label="item.label"
            />
          </el-select>
          <el-select
            :model-value="difficultyFilter ?? undefined"
            class="admin-filter"
            placeholder="全部难度"
            clearable
            data-testid="question-filter-difficulty"
            @change="(value: number | undefined) => handleDifficultyFilter(value ?? null)"
          >
            <el-option v-for="level in 5" :key="level" :value="level" :label="`难度 ${level}`" />
          </el-select>
          <el-button v-if="hasFilters" text type="primary" @click="clearFilters">
            清除筛选
          </el-button>
        </div>
        <div class="admin-toolbar__spacer" />
        <p class="admin-toolbar__note">
          共 {{ total }} 题 · 第 {{ page }} / {{ Math.max(1, pages) }} 页 · 筛选与分页已同步到地址栏
        </p>
      </div>
      <p class="admin-scroll-hint">表格可左右滑动查看题库字段与操作列</p>

      <LoadingSkeleton v-if="loading && records.length === 0" type="list" :rows="6" />

      <ErrorState
        v-else-if="error"
        title="题库加载失败"
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
            class="admin-table admin-table--questions"
            data-testid="question-table"
            :header-cell-style="{ background: 'transparent' }"
          >
            <el-table-column prop="id" label="ID" width="64" />

            <el-table-column label="岗位" min-width="150">
              <template #default="{ row }">
                <div class="admin-cell-stack">
                  <span class="admin-cell-title">{{
                    positionName(toQuestion(row).positionId)
                  }}</span>
                  <span class="admin-cell-sub">#{{ toQuestion(row).positionId }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="题型" width="84">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{
                  questionTypeLabel(toQuestion(row).type)
                }}</el-tag>
              </template>
            </el-table-column>

            <el-table-column label="题干" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="admin-cell-clamp">{{ toQuestion(row).content }}</span>
              </template>
            </el-table-column>

            <el-table-column label="难度" width="72" align="center">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="difficultyTagType(toQuestion(row).difficulty)"
                  effect="light"
                >
                  {{ toQuestion(row).difficulty }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="技能标签" min-width="140">
              <template #default="{ row }">
                <div class="admin-tag-list">
                  <el-tag
                    v-for="tag in toQuestion(row).skillTags.slice(0, 2)"
                    :key="tag"
                    size="small"
                    effect="plain"
                  >
                    {{ tag }}
                  </el-tag>
                  <el-tooltip
                    v-if="toQuestion(row).skillTags.length > 2"
                    :content="toQuestion(row).skillTags.join('、')"
                    placement="top"
                  >
                    <el-tag size="small" type="info" effect="plain">
                      +{{ toQuestion(row).skillTags.length - 2 }}
                    </el-tag>
                  </el-tooltip>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="状态" width="88">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="toQuestion(row).status === 'ENABLED' ? 'success' : 'info'"
                  effect="plain"
                >
                  {{ QUESTION_STATUS_LABELS[toQuestion(row).status] }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="版本" width="56" align="center">
              <template #default="{ row }">
                <span class="admin-muted">v{{ toQuestion(row).version }}</span>
              </template>
            </el-table-column>

            <el-table-column label="更新时间" width="132">
              <template #default="{ row }">
                <span class="admin-muted">{{ formatDateTime(toQuestion(row).updatedAt) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="136" fixed="right">
              <template #default="{ row }">
                <div v-if="toQuestion(row).status === 'ENABLED'" class="admin-row-actions">
                  <el-button
                    text
                    type="primary"
                    size="small"
                    :icon="Edit"
                    :data-testid="`question-edit-${toQuestion(row).id}`"
                    @click.stop="openEdit(row)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    text
                    type="danger"
                    size="small"
                    :icon="Delete"
                    :data-testid="`question-delete-${toQuestion(row).id}`"
                    @click.stop="removeQuestion(row)"
                  >
                    删除
                  </el-button>
                </div>
                <span v-else class="admin-muted">已删除</span>
              </template>
            </el-table-column>

            <template #empty>
              <div class="admin-empty admin-empty--inline">
                <EmptyState
                  title="没有匹配的题目"
                  description="调整岗位/题型/难度筛选，或新增一道题目。"
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
      width="min(920px, 92vw)"
      top="4vh"
      :close-on-click-modal="false"
      destroy-on-close
      data-testid="question-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="admin-form-grid">
          <el-form-item label="岗位" prop="positionId">
            <el-select
              v-model="form.positionId"
              placeholder="请选择岗位"
              filterable
              data-testid="question-form-position"
              @change="handlePositionChange"
            >
              <el-option
                v-for="item in positions"
                :key="item.id"
                :value="item.id"
                :label="`${item.name}（${item.code}）`"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="题型" prop="type">
            <el-select v-model="form.type" data-testid="question-form-type">
              <el-option
                v-for="item in QUESTION_TYPE_OPTIONS"
                :key="item.value"
                :value="item.value"
                :label="item.label"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="难度" prop="difficulty">
            <el-select v-model="form.difficulty" data-testid="question-form-difficulty">
              <el-option v-for="level in 5" :key="level" :value="level" :label="`难度 ${level}`" />
            </el-select>
          </el-form-item>

          <el-form-item label="技能标签" prop="skillTags">
            <el-select
              v-model="form.skillTags"
              multiple
              filterable
              allow-create
              default-first-option
              collapse-tags
              collapse-tags-tooltip
              placeholder="选择岗位技能，或输入后回车新建"
              data-testid="question-form-skills"
            >
              <el-option
                v-for="skill in skillOptions"
                :key="skill.code"
                :value="skill.code"
                :label="`${skill.name}（${skill.code}）`"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="题干" prop="content" class="admin-form-grid__full">
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit
              placeholder="请输入题目正文，4~1000 字"
              data-testid="question-form-content"
            />
          </el-form-item>

          <el-form-item label="题目背景（可选）" prop="background" class="admin-form-grid__full">
            <el-input
              v-model="form.background"
              type="textarea"
              :rows="2"
              maxlength="1000"
              show-word-limit
              placeholder="例如业务场景、前置信息；面试开始时会与题干合并展示"
              data-testid="question-form-background"
            />
          </el-form-item>

          <el-form-item class="admin-form-grid__full">
            <template #label>
              <div class="admin-form-section__title">
                <span>参考答案与评分证据</span>
                <span>得分点/失分点将参与逐题证据与追问规则</span>
              </div>
            </template>
            <el-input
              v-model="form.referenceAnswer"
              type="textarea"
              :rows="4"
              maxlength="4000"
              show-word-limit
              placeholder="参考答案（可选，≤4000 字）"
              data-testid="question-form-answer"
            />
          </el-form-item>

          <el-form-item class="admin-form-grid__full">
            <JsonArrayEditor
              v-model="form.keyPoints"
              label="得分点（每行一条，最多 10 条）"
              placeholder="例如：能说出 JDK8 的 Node 数组+链表+红黑树"
              :max="10"
              :rows="3"
              testid="question-form-key-points"
            />
          </el-form-item>

          <el-form-item class="admin-form-grid__full">
            <JsonArrayEditor
              v-model="form.deductionPoints"
              label="失分点（每行一条，最多 10 条）"
              placeholder="例如：只停留在 JDK7 分段锁"
              :max="10"
              :rows="3"
              testid="question-form-deduction-points"
            />
          </el-form-item>

          <el-form-item class="admin-form-grid__full">
            <JsonArrayEditor
              v-model="form.followupTemplates"
              label="追问模板（每行一条，最多 10 条）"
              placeholder="例如：扩容时读写请求怎么处理？"
              :max="10"
              :rows="3"
              testid="question-form-followups"
            />
          </el-form-item>
        </div>
      </el-form>

      <p v-if="formError" class="admin-inline-alert" role="alert">{{ formError }}</p>

      <template #footer>
        <div class="admin-dialog-footer">
          <span class="admin-dialog-footer__hint">
            {{
              editing
                ? `编辑携带 version=${form.version}，冲突返回 40003`
                : '新增题目 version 由后端生成'
            }}
          </span>
          <div>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button
              type="primary"
              :loading="saving"
              data-testid="question-form-submit"
              @click="submit"
            >
              {{ editing ? '保存修改' : '创建题目' }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="conflictVisible"
      title="数据已被他人修改（40003）"
      width="min(640px, 92vw)"
      :close-on-click-modal="false"
      data-testid="question-conflict-dialog"
    >
      <div class="conflict">
        <p class="conflict__lead">
          该题已被其他操作修改。你的编辑内容仍在左侧表单中，未被丢弃；请确认使用哪个版本。
        </p>
        <dl class="conflict__meta">
          <div>
            <dt>你的版本</dt>
            <dd>v{{ localVersion ?? '—' }}</dd>
          </div>
          <div>
            <dt>最新版本</dt>
            <dd>v{{ conflictVersion ?? '—' }}</dd>
          </div>
          <div>
            <dt>最新修改</dt>
            <dd>{{ formatDateTime(conflictLatest?.updatedAt) }}</dd>
          </div>
        </dl>
        <div class="conflict__latest">
          <strong>最新版本题干</strong>
          <p>{{ conflictLatest?.content }}</p>
        </div>
      </div>

      <template #footer>
        <div class="admin-dialog-footer">
          <span class="admin-dialog-footer__hint">覆盖会以最新 version 重新提交你当前表单内容</span>
          <div>
            <el-button :disabled="conflictSaving" @click="discardConflict">放弃我的修改</el-button>
            <el-button
              type="primary"
              :loading="conflictSaving"
              data-testid="question-conflict-override"
              @click="overrideConflict"
            >
              用我的内容覆盖
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.question-page {
  min-width: 0;
}

.conflict {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.conflict__lead {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.conflict__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  margin: 0;
  padding: var(--space-3);
  border: 1px solid rgba(255, 255, 255, 0.52);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.36);
}

.conflict__meta div {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.conflict__meta dt {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.conflict__meta dd {
  margin: 0;
  color: var(--color-text);
  font-size: var(--font-size-sm);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.conflict__latest {
  padding: var(--space-3);
  border: 1px dashed rgba(148, 163, 184, 0.36);
  border-radius: var(--radius-md);
  background: rgba(248, 250, 252, 0.6);
}

.conflict__latest strong {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.conflict__latest p {
  margin-top: 4px;
  color: var(--color-text);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.question-page :deep(.el-form-item .el-select) {
  width: 100%;
}

@media (max-width: 768px) {
  .admin-form-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
