<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'

import type { SkillItem } from '@/types/admin'
import {
  formatWeightSum,
  validateSkillWeights,
  weightDifferenceText,
  weightSumValid,
} from '@/utils/admin'

/**
 * 岗位技能与权重编辑器（F5-4，计划 §5.14）：
 * 动态行、实时合计、重复编码提示、至少保留 1 行；权重和=1±0.01 由父级保存按钮一并校验。
 * 只用 v-model 向外同步，不在组件内直接改 props。
 */
interface SkillRow {
  skillCode: string
  skillName: string
  description: string
  weight: number
  requiredLevel: number | null
}

const props = withDefaults(
  defineProps<{
    modelValue?: SkillItem[]
    disabled?: boolean
  }>(),
  {
    modelValue: () => [],
    disabled: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: SkillItem[]]
}>()

function cloneRows(skills: readonly SkillItem[]): SkillRow[] {
  return (skills ?? []).map((skill) => ({
    skillCode: skill.skillCode ?? '',
    skillName: skill.skillName ?? '',
    description: skill.description ?? '',
    weight: typeof skill.weight === 'number' ? skill.weight : 0,
    requiredLevel:
      skill.requiredLevel == null || !Number.isFinite(Number(skill.requiredLevel))
        ? null
        : Number(skill.requiredLevel),
  }))
}

const rows = ref<SkillRow[]>(cloneRows(props.modelValue))

watch(
  () => props.modelValue,
  (value) => {
    const next = cloneRows(value)
    if (JSON.stringify(next) === JSON.stringify(rows.value)) return
    rows.value = next
  },
  { deep: true },
)

const validation = computed(() => validateSkillWeights(rows.value))
const sum = computed(() => validation.value.sum)
const sumOk = computed(() => weightSumValid(sum.value))
const duplicateSet = computed(
  () => new Set(validation.value.duplicateCodes.map((code) => code.toLowerCase())),
)

function emitRows(): void {
  emit(
    'update:modelValue',
    rows.value.map((row) => ({
      skillCode: row.skillCode,
      skillName: row.skillName,
      description: row.description,
      weight: row.weight,
      requiredLevel: row.requiredLevel,
    })),
  )
}

function addRow(): void {
  rows.value.push({
    skillCode: '',
    skillName: '',
    description: '',
    weight: 0.1,
    requiredLevel: 70,
  })
  emitRows()
}

function removeRow(index: number): void {
  if (rows.value.length <= 1) return
  rows.value.splice(index, 1)
  emitRows()
}

function handleRequiredLevel(row: SkillRow, value: number | undefined): void {
  row.requiredLevel = typeof value === 'number' && Number.isFinite(value) ? value : null
  emitRows()
}

function isDuplicate(code: string): boolean {
  return !!code && duplicateSet.value.has(code.trim().toLowerCase())
}
</script>

<template>
  <div class="skill-weights-editor" data-testid="skill-weights-editor">
    <div class="skill-weights-editor__bar">
      <div>
        <strong>能力模型技能</strong>
        <span class="skill-weights-editor__hint">至少 1 行；skillCode 唯一；技能权重和须为 1</span>
      </div>
      <div class="skill-weights-editor__sum" :class="{ 'is-invalid': !sumOk }">
        <span>权重合计</span>
        <strong>{{ formatWeightSum(sum) }}</strong>
        <em>{{ weightDifferenceText(sum) }}</em>
      </div>
    </div>

    <div class="skill-weights-editor__head" aria-hidden="true">
      <span>skillCode</span>
      <span>skillName</span>
      <span>描述</span>
      <span>权重</span>
      <span>要求等级</span>
      <span />
    </div>

    <div
      v-for="(row, index) in rows"
      :key="index"
      class="skill-weights-editor__row"
      :data-testid="`skill-row-${index}`"
    >
      <el-input
        v-model="row.skillCode"
        placeholder="如 spring"
        maxlength="50"
        :disabled="disabled"
        :class="{ 'is-duplicate': isDuplicate(row.skillCode) }"
        :data-testid="`skill-row-${index}-code`"
        aria-label="技能编码"
        @input="emitRows"
      />
      <el-input
        v-model="row.skillName"
        placeholder="如 Spring生态"
        maxlength="100"
        :disabled="disabled"
        :data-testid="`skill-row-${index}-name`"
        aria-label="技能名称"
        @input="emitRows"
      />
      <el-input
        v-model="row.description"
        placeholder="一句描述（可选）"
        maxlength="200"
        :disabled="disabled"
        :data-testid="`skill-row-${index}-description`"
        aria-label="技能描述"
        @input="emitRows"
      />
      <el-input-number
        v-model="row.weight"
        :min="0"
        :max="1"
        :step="0.05"
        :precision="2"
        :controls="false"
        :disabled="disabled"
        :data-testid="`skill-row-${index}-weight`"
        aria-label="技能权重"
        @change="emitRows"
      />
      <el-input-number
        :model-value="row.requiredLevel ?? undefined"
        :min="0"
        :max="100"
        :step="5"
        :controls="false"
        :disabled="disabled"
        :data-testid="`skill-row-${index}-level`"
        aria-label="要求等级"
        @update:model-value="(value: number | undefined) => handleRequiredLevel(row, value)"
      />
      <el-button
        text
        type="danger"
        :icon="Delete"
        :disabled="disabled || rows.length <= 1"
        :aria-label="`删除第 ${index + 1} 个技能`"
        :data-testid="`skill-row-${index}-remove`"
        @click="removeRow(index)"
      />
    </div>

    <div class="skill-weights-editor__footer">
      <el-button text type="primary" :icon="Plus" :disabled="disabled" @click="addRow">
        添加技能
      </el-button>
      <span v-if="duplicateSet.size > 0" class="skill-weights-editor__error" role="alert">
        技能编码重复：{{ [...duplicateSet].join('、') }}
      </span>
      <span v-else-if="!sumOk" class="skill-weights-editor__error" role="alert">
        {{ weightDifferenceText(sum) }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.skill-weights-editor {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-width: 0;
}

.skill-weights-editor__bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.skill-weights-editor__bar strong {
  font-size: var(--font-size-sm);
}

.skill-weights-editor__hint {
  margin-left: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.skill-weights-editor__sum {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 2px 10px;
  border: 1px solid rgba(22, 163, 74, 0.24);
  border-radius: var(--radius-pill);
  background: rgba(240, 253, 244, 0.72);
  color: var(--color-success);
  font-size: var(--font-size-xs);
}

.skill-weights-editor__sum strong {
  font-size: var(--font-size-md);
  font-variant-numeric: tabular-nums;
}

.skill-weights-editor__sum em {
  font-style: normal;
  opacity: 0.85;
}

.skill-weights-editor__sum.is-invalid {
  border-color: rgba(220, 38, 38, 0.24);
  background: rgba(254, 242, 242, 0.8);
  color: var(--color-danger);
}

.skill-weights-editor__head,
.skill-weights-editor__row {
  display: grid;
  grid-template-columns:
    minmax(120px, 1fr) minmax(140px, 1.1fr) minmax(160px, 1.5fr)
    100px 112px 40px;
  gap: var(--space-2);
  align-items: center;
}

.skill-weights-editor__head {
  padding: 0 var(--space-1);
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.skill-weights-editor__row {
  padding: var(--space-2);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.32);
}

.skill-weights-editor__row :deep(.el-input-number) {
  width: 100%;
}

.skill-weights-editor__row :deep(.is-duplicate .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--color-danger) inset;
}

.skill-weights-editor__footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding-top: var(--space-1);
}

.skill-weights-editor__error {
  color: var(--color-danger);
  font-size: var(--font-size-xs);
}

@media (max-width: 900px) {
  .skill-weights-editor__head {
    display: none;
  }

  .skill-weights-editor__row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .skill-weights-editor__row :deep(.el-input:nth-child(3)) {
    grid-column: 1 / -1;
  }

  .skill-weights-editor__row :deep(.el-button) {
    justify-self: end;
  }
}

@media (max-width: 560px) {
  .skill-weights-editor__row {
    grid-template-columns: minmax(0, 1fr);
  }

  .skill-weights-editor__row :deep(.el-input:nth-child(3)) {
    grid-column: auto;
  }
}
</style>
