<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { InfoFilled, Refresh, RefreshLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import { adminGetPosition, adminUpdateWeights } from '@/api/admin'
import { errorMessage, isBizError } from '@/api/http'
import ScoreWeightsEditor from '@/components/admin/ScoreWeightsEditor.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import type { PositionAdminVO } from '@/types/admin'
import {
  cloneScoreWeights,
  DEFAULT_SCORE_WEIGHTS,
  normalizeScoreWeights,
  scoreWeightsToPayload,
  validateScoreWeightsForm,
  type ScoreWeightsForm,
} from '@/utils/admin'
import { formatDateTime } from '@/utils/format'

/**
 * 评分权重编辑面板（F5-4，计划 §5.15）：
 * 供 /admin/positions/:id/weights 与 /admin/positions?tab=weights 两处复用。
 * 加载岗位详情 → 三组权重编辑 → PUT /admin/positions/{id}/weights → 回读校验。
 * 未配置 scoreWeights 的岗位按后端默认值展示，保存后写入岗位能力模型。
 */
const props = withDefaults(
  defineProps<{
    positionId: number | null
    positions?: PositionAdminVO[]
    showSelector?: boolean
  }>(),
  {
    positions: () => [],
    showSelector: true,
  },
)

const emit = defineEmits<{
  select: [positionId: number]
  saved: [position: PositionAdminVO]
}>()

const detail = ref<PositionAdminVO | null>(null)
const form = ref<ScoreWeightsForm>(cloneScoreWeights(DEFAULT_SCORE_WEIGHTS))
const original = ref<ScoreWeightsForm | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref<unknown>(null)
const usingDefault = ref(false)
const saveError = ref('')
const saveTrace = ref('')

let requestSeq = 0

const validation = computed(() => validateScoreWeightsForm(form.value))
const dirty = computed(() => {
  if (!detail.value) return false
  if (!original.value) return true
  return JSON.stringify(original.value) !== JSON.stringify(form.value)
})
const canSave = computed(
  () => !!detail.value && !saving.value && validation.value.valid && dirty.value,
)
const errorText = computed(() => errorMessage(error.value, '评分权重加载失败，请稍后重试'))
const errorCode = computed(() => (isBizError(error.value) ? error.value.code : undefined))
const errorTrace = computed(() => (isBizError(error.value) ? error.value.traceId : ''))
const selectedPosition = computed(
  () => props.positions.find((item) => item.id === props.positionId) ?? detail.value,
)

async function loadDetail(id: number | null): Promise<void> {
  saveError.value = ''
  saveTrace.value = ''
  if (id == null) {
    detail.value = null
    loading.value = false
    return
  }
  const seq = ++requestSeq
  loading.value = true
  error.value = null
  try {
    const position = await adminGetPosition(id)
    if (seq !== requestSeq) return
    detail.value = position
    original.value = position.scoreWeights ? normalizeScoreWeights(position.scoreWeights) : null
    form.value = normalizeScoreWeights(position.scoreWeights)
    usingDefault.value = !position.scoreWeights
  } catch (cause) {
    if (seq !== requestSeq) return
    detail.value = null
    error.value = cause
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

watch(
  () => props.positionId,
  (id) => {
    void loadDetail(id)
  },
  { immediate: true },
)

function handleSelect(value: number | string): void {
  const id = Number(value)
  if (Number.isInteger(id) && id > 0) emit('select', id)
}

function resetDefault(): void {
  form.value = cloneScoreWeights(DEFAULT_SCORE_WEIGHTS)
  saveError.value = ''
}

async function save(): Promise<void> {
  if (!detail.value || saving.value) return
  if (!validation.value.valid) {
    ElMessage.error(validation.value.messages[0] ?? '权重校验未通过')
    return
  }
  saving.value = true
  saveError.value = ''
  saveTrace.value = ''
  try {
    const updated = await adminUpdateWeights(detail.value.id, {
      scoreWeights: scoreWeightsToPayload(form.value),
    })
    detail.value = updated
    original.value = normalizeScoreWeights(updated.scoreWeights)
    form.value = normalizeScoreWeights(updated.scoreWeights)
    usingDefault.value = !updated.scoreWeights
    ElMessage.success('评分权重已保存，后续面试立即生效')
    emit('saved', updated)
  } catch (cause) {
    saveError.value = errorMessage(cause, '评分权重保存失败，请稍后重试')
    saveTrace.value = isBizError(cause) ? (cause.traceId ?? '') : ''
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="weights-panel" data-testid="weights-panel">
    <div v-if="showSelector" class="weights-panel__selector">
      <span class="weights-panel__selector-label">选择岗位</span>
      <el-select
        :model-value="positionId ?? undefined"
        class="weights-panel__select"
        placeholder="请选择岗位"
        filterable
        :disabled="loading || positions.length === 0"
        data-testid="weights-position"
        @change="handleSelect"
      >
        <el-option
          v-for="item in positions"
          :key="item.id"
          :value="item.id"
          :label="`${item.name}（${item.code}）${item.enabled === 1 ? '' : ' · 已停用'}`"
        />
      </el-select>
      <span v-if="selectedPosition" class="weights-panel__selector-hint">
        {{ selectedPosition.skillCount }} 个技能 · 默认 {{ selectedPosition.defaultRounds }} 题
      </span>
    </div>

    <LoadingSkeleton v-if="loading && !detail" type="card" />

    <ErrorState
      v-else-if="error"
      title="评分权重加载失败"
      :message="errorText"
      :code="errorCode"
      :trace-id="errorTrace"
      @retry="loadDetail(positionId)"
    />

    <EmptyState
      v-else-if="positionId == null"
      title="请选择一个岗位"
      description="评分权重按岗位保存在能力模型中；选择岗位后可调整内容/表达/总分三组权重。"
      :show-action="false"
    />

    <template v-else-if="detail">
      <GlassPanel class="weights-panel__meta" :padded="false">
        <div class="weights-panel__meta-main">
          <div>
            <h2>{{ detail.name }}</h2>
            <p>
              <code>{{ detail.code }}</code>
              <span>· 默认 {{ detail.defaultRounds }} 题</span>
              <span>· {{ detail.skillCount }} 个技能</span>
              <span>· 更新于 {{ formatDateTime(detail.updatedAt) }}</span>
            </p>
          </div>
          <el-tag :type="detail.enabled === 1 ? 'success' : 'info'" effect="plain" round>
            {{ detail.enabled === 1 ? '启用中' : '已停用' }}
          </el-tag>
        </div>
      </GlassPanel>

      <div v-if="usingDefault" class="admin-note admin-note--warning">
        <el-icon :size="16" class="weights-panel__note-icon"><InfoFilled /></el-icon>
        <div>
          <strong>当前未显式配置</strong>
          <div>
            该岗位尚未写入
            scoreWeights，后端按默认权重评分。下方已载入默认值；点击「保存权重」后将写入岗位能力模型并回读校验。
          </div>
        </div>
      </div>

      <GlassPanel :padded="false">
        <div class="weights-panel__head">
          <div>
            <h3>三组评分权重</h3>
            <p>内容 5 项 / 表达 5 项 / 总分 4 项；每组必须完整、取值 0~1 且组内和=1（±0.01）。</p>
          </div>
          <el-button
            :icon="Refresh"
            circle
            :loading="loading"
            aria-label="重新加载评分权重"
            @click="loadDetail(positionId)"
          />
        </div>

        <div class="weights-panel__body">
          <ScoreWeightsEditor v-model="form" :disabled="saving" :show-reset="false" />
        </div>

        <div class="weights-panel__footer">
          <div>
            <p v-if="!validation.valid" class="weights-panel__error" role="alert">
              校验未通过：{{ validation.messages[0] }}
            </p>
            <p v-else-if="dirty" class="weights-panel__dirty">有未保存的修改</p>
            <p v-else class="weights-panel__clean">当前权重已保存</p>
            <p v-if="saveError" class="weights-panel__error" role="alert">
              {{ saveError }}
              <span v-if="saveTrace" class="weights-panel__trace">traceId: {{ saveTrace }}</span>
            </p>
          </div>
          <div class="weights-panel__actions">
            <el-button
              :icon="RefreshLeft"
              :disabled="saving"
              data-testid="weights-reset"
              @click="resetDefault"
            >
              恢复默认
            </el-button>
            <el-button
              type="primary"
              :loading="saving"
              :disabled="!canSave"
              data-testid="weights-save"
              @click="save"
            >
              保存权重
            </el-button>
          </div>
        </div>
      </GlassPanel>
    </template>
  </div>
</template>

<style scoped>
.weights-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  min-width: 0;
}

.weights-panel__selector {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.4);
}

.weights-panel__selector-label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.weights-panel__select {
  width: 320px;
  max-width: 100%;
}

.weights-panel__selector-hint {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.weights-panel__meta-main {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
}

.weights-panel__meta-main h2 {
  font-size: var(--font-size-lg);
  font-weight: 700;
}

.weights-panel__meta-main p {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.weights-panel__meta-main code {
  color: var(--color-text-secondary);
}

.weights-panel__note-icon {
  flex: none;
  margin-top: 2px;
  color: var(--color-warning);
}

.weights-panel__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid rgba(255, 255, 255, 0.46);
}

.weights-panel__head h3 {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.weights-panel__head p {
  margin-top: 2px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.weights-panel__body {
  padding: var(--space-4) var(--space-5);
}

.weights-panel__footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-5);
  border-top: 1px solid rgba(255, 255, 255, 0.46);
}

.weights-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.weights-panel__error {
  color: var(--color-danger);
  font-size: var(--font-size-xs);
}

.weights-panel__trace {
  margin-left: 6px;
  opacity: 0.8;
}

.weights-panel__dirty {
  color: var(--color-warning);
  font-size: var(--font-size-xs);
}

.weights-panel__clean {
  color: var(--color-success);
  font-size: var(--font-size-xs);
}

@media (max-width: 768px) {
  .weights-panel__select {
    width: 100%;
  }

  .weights-panel__head,
  .weights-panel__body,
  .weights-panel__footer,
  .weights-panel__meta-main {
    padding-inline: var(--space-4);
  }
}
</style>
