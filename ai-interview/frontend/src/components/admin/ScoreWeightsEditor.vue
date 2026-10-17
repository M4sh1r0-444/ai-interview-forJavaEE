<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RefreshLeft } from '@element-plus/icons-vue'

import {
  cloneScoreWeights,
  DEFAULT_SCORE_WEIGHTS,
  formatWeightSum,
  getGroupWeight,
  SCORE_WEIGHT_GROUPS,
  setGroupWeight,
  validateScoreWeightsForm,
  weightDifferenceText,
  type ScoreGroupKey,
  type ScoreWeightsForm,
} from '@/utils/admin'

/**
 * 评分权重三组编辑器（F5-4，计划 §5.15）：
 * content 5 项 / expression 5 项 / total 4 项，实时求和；未填字段会阻止保存。
 * 三组都必须完整且组内和=1±0.01；仅影响后续面试，不回溯历史报告。
 */
const props = withDefaults(
  defineProps<{
    modelValue: ScoreWeightsForm
    disabled?: boolean
    showReset?: boolean
  }>(),
  {
    disabled: false,
    showReset: true,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: ScoreWeightsForm]
}>()

const form = ref<ScoreWeightsForm>(cloneScoreWeights(props.modelValue))

watch(
  () => props.modelValue,
  (value) => {
    if (JSON.stringify(value) === JSON.stringify(form.value)) return
    form.value = cloneScoreWeights(value)
  },
  { deep: true },
)

const validation = computed(() => validateScoreWeightsForm(form.value))

function emitForm(): void {
  emit('update:modelValue', cloneScoreWeights(form.value))
}

function displayWeight(group: ScoreGroupKey, key: string): number | undefined {
  const value = getGroupWeight(form.value, group, key)
  return Number.isFinite(value) ? value : undefined
}

function handleWeight(group: ScoreGroupKey, key: string, value: number | undefined): void {
  setGroupWeight(form.value, group, key, typeof value === 'number' ? value : Number.NaN)
  emitForm()
}

function resetToDefault(): void {
  form.value = cloneScoreWeights(DEFAULT_SCORE_WEIGHTS)
  emitForm()
}
</script>

<template>
  <div class="score-weights-editor" data-testid="score-weights-editor">
    <div
      v-for="group in SCORE_WEIGHT_GROUPS"
      :key="group.key"
      class="score-weight-group"
      :data-testid="`score-group-${group.key}`"
    >
      <header class="score-weight-group__head">
        <div>
          <h4>{{ group.title }}</h4>
          <p>{{ group.description }}</p>
        </div>
        <div
          class="score-weight-group__sum"
          :class="{ 'is-invalid': !validation.groups[group.key].valid }"
          :data-testid="`score-sum-${group.key}`"
        >
          合计 <strong>{{ formatWeightSum(validation.groups[group.key].sum) }}</strong>
          <span>{{ weightDifferenceText(validation.groups[group.key].sum) }}</span>
        </div>
      </header>

      <div class="score-weight-group__grid">
        <label
          v-for="field in group.fields"
          :key="field.key"
          class="score-weight-field"
          :data-testid="`score-field-${group.key}-${field.key}`"
        >
          <span class="score-weight-field__label">{{ field.label }}</span>
          <el-input-number
            :model-value="displayWeight(group.key, field.key)"
            :min="0"
            :max="1"
            :step="0.05"
            :precision="2"
            :controls="false"
            :disabled="disabled"
            :data-testid="`score-${group.key}-${field.key}`"
            :aria-label="field.label"
            @update:model-value="
              (value: number | undefined) => handleWeight(group.key, field.key, value)
            "
          />
          <span class="score-weight-field__hint">{{ field.hint }}</span>
        </label>
      </div>

      <p v-if="!validation.groups[group.key].valid" class="score-weight-group__error" role="alert">
        {{ validation.groups[group.key].messages.join('；') }}
      </p>
    </div>

    <div class="score-weights-editor__footer">
      <p class="score-weights-editor__note">
        权重保存到岗位能力模型；新面试的评分聚合立即生效，历史报告分数保持不变。
      </p>
      <el-button
        v-if="showReset"
        :icon="RefreshLeft"
        :disabled="disabled"
        data-testid="score-weights-reset"
        @click="resetToDefault"
      >
        恢复默认
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.score-weights-editor {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  min-width: 0;
}

.score-weight-group {
  padding: var(--space-4);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.3);
}

.score-weight-group__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.score-weight-group__head h4 {
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.score-weight-group__head p {
  margin-top: 2px;
  color: var(--color-text-tertiary);
  font-size: 11px;
  line-height: 1.6;
}

.score-weight-group__sum {
  flex: none;
  padding: 2px 10px;
  border: 1px solid rgba(22, 163, 74, 0.24);
  border-radius: var(--radius-pill);
  background: rgba(240, 253, 244, 0.72);
  color: var(--color-success);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.score-weight-group__sum strong {
  font-size: var(--font-size-base);
}

.score-weight-group__sum span {
  margin-left: 4px;
  opacity: 0.85;
}

.score-weight-group__sum.is-invalid {
  border-color: rgba(220, 38, 38, 0.24);
  background: rgba(254, 242, 242, 0.8);
  color: var(--color-danger);
}

.score-weight-group__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: var(--space-3);
}

.score-weight-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.score-weight-field__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.score-weight-field :deep(.el-input-number) {
  width: 100%;
}

.score-weight-field__hint {
  color: var(--color-text-tertiary);
  font-size: 11px;
  line-height: 1.5;
}

.score-weight-group__error {
  margin-top: var(--space-2);
  color: var(--color-danger);
  font-size: 11px;
  line-height: 1.6;
}

.score-weights-editor__footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.score-weights-editor__note {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

@media (max-width: 640px) {
  .score-weight-group {
    padding: var(--space-3);
  }

  .score-weights-editor__footer {
    align-items: flex-start;
  }
}
</style>
