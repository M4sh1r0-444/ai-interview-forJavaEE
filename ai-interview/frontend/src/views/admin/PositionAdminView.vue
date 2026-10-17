<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Edit, Odometer, Plus, Refresh, View } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { adminCreatePosition, adminListPositions, adminUpdatePosition } from '@/api/admin'
import { errorMessage, isBizError } from '@/api/http'
import ScoreWeightsEditor from '@/components/admin/ScoreWeightsEditor.vue'
import SkillWeightsEditor from '@/components/admin/SkillWeightsEditor.vue'
import WeightsEditorPanel from '@/components/admin/WeightsEditorPanel.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import { DEFAULT_PAGE_SIZES, usePagination } from '@/composables/usePagination'
import type { PositionAdminVO, PositionUpsertPayload, SkillItem } from '@/types/admin'
import {
  cleanSkills,
  cloneScoreWeights,
  DEFAULT_SCORE_WEIGHTS,
  normalizeScoreWeights,
  parsePositiveInt,
  scoreWeightsToPayload,
  validateScoreWeightsForm,
  validateSkillWeights,
  type ScoreWeightsForm,
} from '@/utils/admin'
import { formatDateTime } from '@/utils/format'

/**
 * 管理端岗位与能力模型（F5-4，计划 §5.14）：
 * 列表/新增/编辑/查看 + 技能权重实时校验 + 可选评分权重三组编辑；
 * 不提供删除（岗位被题库/历史引用）；?tab=weights 复用评分权重面板（D33）。
 */
const route = useRoute()
const router = useRouter()

const allPositions = ref<PositionAdminVO[]>([])
const allPositionsLoading = ref(true)
const allPositionsError = ref<unknown>(null)

const pagination = usePagination<PositionAdminVO>({
  fetcher: ({ page, size }) => adminListPositions({ page, size }),
  pageSizes: [...DEFAULT_PAGE_SIZES],
  initialPage: 1,
  initialSize: DEFAULT_PAGE_SIZES[0],
})
const { page, size, total, pages, records, loading, error, load, refresh } = pagination

const activeTab = computed<'list' | 'weights'>(() =>
  route.query.tab === 'weights' ? 'weights' : 'list',
)
const weightsPositionId = computed(() => parsePositiveInt(route.query.positionId))

const dialogVisible = ref(false)
const editing = ref<PositionAdminVO | null>(null)
const saving = ref(false)
const formError = ref('')
const formRef = ref<FormInstance>()

const viewVisible = ref(false)
const viewPosition = ref<PositionAdminVO | null>(null)

interface PositionFormModel {
  code: string
  name: string
  description: string
  defaultRounds: number
  enabled: number
  skills: SkillItem[]
  scoreEnabled: boolean
  scoreWeights: ScoreWeightsForm
}

const form = reactive<PositionFormModel>({
  code: '',
  name: '',
  description: '',
  defaultRounds: 6,
  enabled: 1,
  skills: [],
  scoreEnabled: false,
  scoreWeights: cloneScoreWeights(DEFAULT_SCORE_WEIGHTS),
})

const rules: FormRules = {
  code: [
    { required: true, message: '岗位编码必填', trigger: 'blur' },
    { min: 2, max: 50, message: '岗位编码长度 2~50', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '岗位名称必填', trigger: 'blur' },
    { max: 100, message: '岗位名称最长 100 字', trigger: 'blur' },
  ],
  description: [{ max: 500, message: '岗位描述最长 500 字', trigger: 'blur' }],
  defaultRounds: [{ required: true, message: '默认题数必填', trigger: 'change' }],
}

const errorText = computed(() => errorMessage(error.value, '岗位列表加载失败，请稍后重试'))
const errorCode = computed(() => (isBizError(error.value) ? error.value.code : undefined))
const errorTrace = computed(() => (isBizError(error.value) ? error.value.traceId : ''))
const allPositionsErrorText = computed(() =>
  errorMessage(allPositionsError.value, '岗位下拉数据加载失败，请稍后重试'),
)
const dialogTitle = computed(() => (editing.value ? `编辑岗位 #${editing.value.id}` : '新增岗位'))
const scoreInactiveText = computed(() =>
  editing.value ? '暂不修改（保留原配置）' : '暂不配置（使用默认权重）',
)
const skillValidation = computed(() => validateSkillWeights(form.skills))
const scoreValidation = computed(() => validateScoreWeightsForm(form.scoreWeights))
const formValid = computed(
  () => skillValidation.value.valid && (!form.scoreEnabled || scoreValidation.value.valid),
)
const viewSkillCount = computed(() => viewPosition.value?.skillCount ?? 0)

function toPosition(row: unknown): PositionAdminVO {
  return row as PositionAdminVO
}

function statusLabel(enabled: number): string {
  return enabled === 1 ? '启用' : '停用'
}

async function loadAllPositions(): Promise<void> {
  allPositionsLoading.value = true
  allPositionsError.value = null
  try {
    const result = await adminListPositions({ page: 1, size: 100 })
    allPositions.value = result.records ?? []
  } catch (cause) {
    allPositionsError.value = cause
    allPositions.value = []
  } finally {
    allPositionsLoading.value = false
  }
}

function resetForm(): void {
  Object.assign(form, {
    code: '',
    name: '',
    description: '',
    defaultRounds: 6,
    enabled: 1,
    skills: [
      {
        skillCode: '',
        skillName: '',
        description: '',
        weight: 1,
        requiredLevel: 70,
      },
    ],
    scoreEnabled: false,
    scoreWeights: cloneScoreWeights(DEFAULT_SCORE_WEIGHTS),
  })
  formError.value = ''
  nextTick(() => formRef.value?.clearValidate())
}

function openCreate(): void {
  editing.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: unknown): void {
  const position = toPosition(row)
  editing.value = position
  Object.assign(form, {
    code: position.code,
    name: position.name,
    description: position.description ?? '',
    defaultRounds: position.defaultRounds,
    enabled: position.enabled,
    skills: (position.skills ?? []).map((skill) => ({ ...skill })),
    scoreEnabled: position.scoreWeights != null,
    scoreWeights: normalizeScoreWeights(position.scoreWeights),
  })
  formError.value = ''
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openView(row: unknown): void {
  viewPosition.value = toPosition(row)
  viewVisible.value = true
}

async function save(): Promise<void> {
  if (saving.value || !formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!skillValidation.value.valid) {
    formError.value = skillValidation.value.messages[0]
    return
  }
  if (form.scoreEnabled && !scoreValidation.value.valid) {
    formError.value = scoreValidation.value.messages[0]
    return
  }
  if (form.defaultRounds < 3 || form.defaultRounds > 8) {
    formError.value = '默认题数须为 3~8'
    return
  }

  const payload: PositionUpsertPayload = {
    name: form.name.trim(),
    description: form.description.trim(),
    defaultRounds: form.defaultRounds,
    enabled: form.enabled,
    skills: cleanSkills(form.skills),
  }
  if (!editing.value) payload.code = form.code.trim()
  if (form.scoreEnabled) payload.scoreWeights = scoreWeightsToPayload(form.scoreWeights)

  saving.value = true
  formError.value = ''
  try {
    if (editing.value) {
      await adminUpdatePosition(editing.value.id, payload)
      ElMessage.success('岗位已更新')
    } else {
      await adminCreatePosition(payload)
      ElMessage.success('岗位已创建，学生端立即生效')
    }
    dialogVisible.value = false
    await Promise.all([refresh(), loadAllPositions()])
  } catch (cause) {
    formError.value = errorMessage(cause, '岗位保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

function handleTabChange(name: string | number): void {
  if (name === 'weights') {
    const id = weightsPositionId.value ?? allPositions.value[0]?.id
    void router.replace({
      name: 'admin-positions',
      query: { tab: 'weights', ...(id ? { positionId: String(id) } : {}) },
    })
    return
  }
  void router.replace({ name: 'admin-positions' })
}

function handleWeightsSelect(id: number): void {
  void router.replace({
    name: 'admin-positions',
    query: { tab: 'weights', positionId: String(id) },
  })
}

function openWeights(row: unknown): void {
  const position = toPosition(row)
  void router.push({ name: 'admin-position-weights', params: { id: position.id } })
}

function handleScoreEnabled(): void {
  if (form.scoreEnabled && !form.scoreWeights) {
    form.scoreWeights = cloneScoreWeights(DEFAULT_SCORE_WEIGHTS)
  }
}

function handlePage(value: number): void {
  void load(value, size.value)
}

function handleSize(value: number): void {
  void load(1, value)
}

onMounted(async () => {
  await Promise.all([load(1, size.value), loadAllPositions()])
})

watch(activeTab, (tab) => {
  if (tab === 'weights' && weightsPositionId.value == null && allPositions.value.length > 0) {
    void router.replace({
      name: 'admin-positions',
      query: { tab: 'weights', positionId: String(allPositions.value[0].id) },
    })
  }
})

watch(
  () => allPositions.value.length,
  () => {
    if (
      activeTab.value === 'weights' &&
      weightsPositionId.value == null &&
      allPositions.value.length > 0
    ) {
      void router.replace({
        name: 'admin-positions',
        query: { tab: 'weights', positionId: String(allPositions.value[0].id) },
      })
    }
  },
)
</script>

<template>
  <div class="position-page admin-page" data-testid="position-page">
    <AppPageHeader
      title="岗位与能力模型"
      description="维护岗位、技能权重与启用状态；岗位被题库/历史引用，因此不提供删除。"
    >
      <template #actions>
        <el-button v-if="activeTab === 'list'" :icon="Refresh" :loading="loading" @click="refresh">
          刷新
        </el-button>
        <el-button
          v-if="activeTab === 'list'"
          type="primary"
          :icon="Plus"
          data-testid="position-add"
          @click="openCreate"
        >
          新增岗位
        </el-button>
        <el-button v-else :icon="View" @click="handleTabChange('list')">返回岗位列表</el-button>
      </template>
    </AppPageHeader>

    <GlassPanel :padded="false">
      <div class="position-tabs-wrap">
        <el-tabs
          :model-value="activeTab"
          class="position-tabs"
          data-testid="position-tabs"
          @tab-change="handleTabChange"
        >
          <el-tab-pane label="岗位列表" name="list" />
          <el-tab-pane label="评分权重" name="weights" />
        </el-tabs>
      </div>

      <template v-if="activeTab === 'list'">
        <div class="admin-toolbar">
          <p class="admin-toolbar__note">
            共 {{ total }} 个岗位 · 第 {{ page }} / {{ Math.max(1, pages) }} 页 ·
            技能权重和与评分权重组内和均须为 1
          </p>
          <div class="admin-toolbar__spacer" />
          <p class="admin-toolbar__note">新增岗位后学生端岗位列表立即可见；停用后不可见</p>
        </div>
        <p class="admin-scroll-hint">表格可左右滑动查看能力模型与操作列</p>

        <LoadingSkeleton v-if="loading && records.length === 0" type="list" :rows="5" />

        <ErrorState
          v-else-if="error"
          title="岗位列表加载失败"
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
              class="admin-table admin-table--positions"
              data-testid="position-table"
              :header-cell-style="{ background: 'transparent' }"
            >
              <el-table-column prop="id" label="ID" width="64" />

              <el-table-column label="岗位" min-width="220">
                <template #default="{ row }">
                  <div class="admin-cell-stack">
                    <span class="admin-cell-title">{{ toPosition(row).name }}</span>
                    <span class="admin-cell-sub">{{ toPosition(row).code }}</span>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="默认题数" width="92" align="center">
                <template #default="{ row }">
                  <span class="admin-muted">{{ toPosition(row).defaultRounds }}</span>
                </template>
              </el-table-column>

              <el-table-column label="技能数" width="84" align="center">
                <template #default="{ row }">
                  <span class="admin-muted">{{ toPosition(row).skillCount }}</span>
                </template>
              </el-table-column>

              <el-table-column label="技能权重和" width="112" align="center">
                <template #default="{ row }">
                  <span class="admin-muted">
                    {{
                      toPosition(row)
                        .skills.reduce((sum, skill) => sum + (Number(skill.weight) || 0), 0)
                        .toFixed(2)
                    }}
                  </span>
                </template>
              </el-table-column>

              <el-table-column label="评分权重" width="96" align="center">
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    :type="toPosition(row).scoreWeights ? 'success' : 'info'"
                    effect="plain"
                  >
                    {{ toPosition(row).scoreWeights ? '已配置' : '默认' }}
                  </el-tag>
                </template>
              </el-table-column>

              <el-table-column label="状态" width="86">
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    :type="toPosition(row).enabled === 1 ? 'success' : 'info'"
                    effect="plain"
                  >
                    {{ statusLabel(toPosition(row).enabled) }}
                  </el-tag>
                </template>
              </el-table-column>

              <el-table-column label="更新时间" width="150">
                <template #default="{ row }">
                  <span class="admin-muted">{{ formatDateTime(toPosition(row).updatedAt) }}</span>
                </template>
              </el-table-column>

              <el-table-column label="操作" width="230" fixed="right">
                <template #default="{ row }">
                  <div class="admin-row-actions">
                    <el-button
                      text
                      type="primary"
                      size="small"
                      :icon="Edit"
                      :data-testid="`position-edit-${toPosition(row).id}`"
                      @click.stop="openEdit(row)"
                    >
                      编辑
                    </el-button>
                    <el-button
                      text
                      type="primary"
                      size="small"
                      :icon="Odometer"
                      :data-testid="`position-weights-${toPosition(row).id}`"
                      @click.stop="openWeights(row)"
                    >
                      评分权重
                    </el-button>
                    <el-button text size="small" :icon="View" @click.stop="openView(row)">
                      查看
                    </el-button>
                  </div>
                </template>
              </el-table-column>

              <template #empty>
                <div class="admin-empty admin-empty--inline">
                  <EmptyState
                    title="还没有岗位"
                    description="创建第一个岗位后，学生端即可选择它开始面试。"
                    :show-action="false"
                  />
                  <el-button type="primary" :icon="Plus" @click="openCreate">新增岗位</el-button>
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

      <template v-else>
        <div class="position-weights">
          <div v-if="allPositionsError" class="admin-note admin-note--warning">
            <div>
              <strong>岗位选择数据不可用</strong>
              <div>
                {{ allPositionsErrorText }}
                <el-button link type="primary" @click="loadAllPositions">重试</el-button>
              </div>
            </div>
          </div>
          <LoadingSkeleton v-else-if="allPositionsLoading" type="card" />
          <EmptyState
            v-else-if="allPositions.length === 0"
            title="还没有岗位"
            description="请先新增岗位，再配置评分权重。"
            action-text="新增岗位"
            @action="openCreate"
          />
          <WeightsEditorPanel
            v-else
            :position-id="weightsPositionId"
            :positions="allPositions"
            @select="handleWeightsSelect"
          />
        </div>
      </template>
    </GlassPanel>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="min(960px, 94vw)"
      top="3vh"
      :close-on-click-modal="false"
      destroy-on-close
      data-testid="position-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="admin-form-grid">
          <el-form-item label="岗位编码（创建后只读）" prop="code">
            <el-input
              v-model="form.code"
              :disabled="!!editing"
              maxlength="50"
              placeholder="如 JAVA_BACKEND / WEB_FRONTEND"
              data-testid="position-form-code"
            />
          </el-form-item>

          <el-form-item label="岗位名称" prop="name">
            <el-input
              v-model="form.name"
              maxlength="100"
              placeholder="如 Java 后端开发工程师"
              data-testid="position-form-name"
            />
          </el-form-item>

          <el-form-item label="岗位描述" prop="description" class="admin-form-grid__full">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              placeholder="说明岗位覆盖的技术方向与考察重点"
              data-testid="position-form-description"
            />
          </el-form-item>

          <el-form-item label="默认题数（3~8）" prop="defaultRounds">
            <el-input-number
              v-model="form.defaultRounds"
              :min="3"
              :max="8"
              :step="1"
              data-testid="position-form-rounds"
            />
          </el-form-item>

          <el-form-item label="启用状态">
            <el-switch
              v-model="form.enabled"
              :active-value="1"
              :inactive-value="0"
              active-text="启用"
              inactive-text="停用"
              data-testid="position-form-enabled"
            />
          </el-form-item>

          <el-form-item class="admin-form-grid__full">
            <SkillWeightsEditor v-model="form.skills" :disabled="saving" />
            <p v-if="!skillValidation.valid" class="admin-inline-alert" role="alert">
              {{ skillValidation.messages[0] }}
            </p>
          </el-form-item>

          <el-form-item class="admin-form-grid__full">
            <div class="admin-form-section">
              <div class="admin-form-section__title">
                <span>评分权重（可选）</span>
                <span>不展开时保留岗位原有配置；新建岗位不配置则使用后端默认权重</span>
              </div>
              <el-switch
                v-model="form.scoreEnabled"
                active-text="同时配置内容/表达/总分三组权重"
                :inactive-text="scoreInactiveText"
                data-testid="position-form-score-enabled"
                @change="handleScoreEnabled"
              />
              <ScoreWeightsEditor
                v-if="form.scoreEnabled"
                v-model="form.scoreWeights"
                :disabled="saving"
              />
            </div>
          </el-form-item>
        </div>
      </el-form>

      <p v-if="formError" class="admin-inline-alert" role="alert">{{ formError }}</p>

      <template #footer>
        <div class="admin-dialog-footer">
          <span class="admin-dialog-footer__hint">
            保存后回读校验；停用岗位不影响历史面试与报告
          </span>
          <div>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button
              type="primary"
              :loading="saving"
              :disabled="!formValid"
              data-testid="position-form-submit"
              @click="save"
            >
              {{ editing ? '保存修改' : '创建岗位' }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="viewVisible"
      :title="viewPosition ? `岗位 #${viewPosition.id} · ${viewPosition.name}` : '岗位详情'"
      width="min(760px, 92vw)"
      data-testid="position-view-dialog"
    >
      <div v-if="viewPosition" class="position-view">
        <p class="position-view__desc">{{ viewPosition.description || '暂无岗位描述' }}</p>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="编码">{{ viewPosition.code }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ statusLabel(viewPosition.enabled) }}
          </el-descriptions-item>
          <el-descriptions-item label="默认题数">
            {{ viewPosition.defaultRounds }}
          </el-descriptions-item>
          <el-descriptions-item label="技能数">{{ viewSkillCount }}</el-descriptions-item>
          <el-descriptions-item label="评分权重">
            {{ viewPosition.scoreWeights ? '已配置' : '默认（未配置）' }}
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">
            {{ formatDateTime(viewPosition.updatedAt) }}
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="viewPosition.skills" size="small" class="position-view__table">
          <el-table-column prop="skillCode" label="skillCode" width="150" />
          <el-table-column prop="skillName" label="技能名称" min-width="160" />
          <el-table-column prop="weight" label="权重" width="80" align="center" />
          <el-table-column prop="requiredLevel" label="要求" width="80" align="center" />
          <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.position-page {
  min-width: 0;
}

.position-tabs-wrap {
  padding: 0 var(--space-5);
  border-bottom: 1px solid rgba(255, 255, 255, 0.46);
}

.position-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.position-tabs :deep(.el-tabs__content) {
  display: none;
}

.position-weights {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
}

.position-page :deep(.el-form-item .el-select),
.position-page :deep(.el-form-item .el-input-number) {
  width: 100%;
}

.position-view {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.position-view__desc {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.position-view__table {
  --el-table-header-bg-color: transparent;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
}

@media (max-width: 768px) {
  .position-tabs-wrap {
    padding: 0 var(--space-4);
  }

  .position-weights {
    padding: var(--space-3) var(--space-4);
  }
}
</style>
