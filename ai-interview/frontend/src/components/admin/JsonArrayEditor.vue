<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { arrayToLines, linesToArray } from '@/utils/admin'

/**
 * 「每行一条」数组编辑器（F5-3，计划 §5.16）：
 * 用于得分点/失分点/追问模板等数组字段；输入即解析为 string[]，
 * 空行忽略、保留用户顺序，超过上限时给出提示但不静默截断。
 */
const props = withDefaults(
  defineProps<{
    modelValue?: string[]
    label?: string
    placeholder?: string
    hint?: string
    max?: number
    rows?: number
    disabled?: boolean
    error?: string
    testid?: string
  }>(),
  {
    modelValue: () => [],
    label: '',
    placeholder: '每行一条',
    hint: '',
    max: 10,
    rows: 3,
    disabled: false,
    error: '',
    testid: 'json-array-editor',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

const text = ref(arrayToLines(props.modelValue))

watch(
  () => props.modelValue,
  (value) => {
    const next = arrayToLines(value)
    if (next !== text.value) text.value = next
  },
  { deep: true },
)

const count = computed(() => linesToArray(text.value).length)
const overflow = computed(() => count.value > props.max)

function handleInput(value: string): void {
  emit('update:modelValue', linesToArray(value))
}
</script>

<template>
  <div class="json-array-editor" :data-testid="testid">
    <div v-if="label" class="json-array-editor__head">
      <span class="json-array-editor__label">{{ label }}</span>
      <span class="json-array-editor__count" :class="{ 'is-overflow': overflow }">
        {{ count }} / {{ max }} 条
      </span>
    </div>
    <el-input
      v-model="text"
      type="textarea"
      :rows="rows"
      :placeholder="placeholder"
      :disabled="disabled"
      :aria-label="label || '数组内容'"
      :data-testid="`${testid}-input`"
      @input="handleInput"
    />
    <p v-if="overflow" class="json-array-editor__error" role="alert">
      最多 {{ max }} 条，请合并或删除多余行后再保存。
    </p>
    <p v-else-if="error" class="json-array-editor__error" role="alert">{{ error }}</p>
    <p v-else-if="hint" class="json-array-editor__hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.json-array-editor {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.json-array-editor__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
}

.json-array-editor__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.json-array-editor__count {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.json-array-editor__count.is-overflow {
  color: var(--color-danger);
  font-weight: 600;
}

.json-array-editor__hint {
  color: var(--color-text-tertiary);
  font-size: 11px;
  line-height: 1.6;
}

.json-array-editor__error {
  color: var(--color-danger);
  font-size: 11px;
  line-height: 1.6;
}
</style>
