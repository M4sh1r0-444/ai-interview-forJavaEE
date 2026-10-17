<script setup lang="ts">
import { computed, ref } from 'vue'
import { Promotion } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    modelValue: string
    disabled?: boolean
    submitting?: boolean
    disabledHint?: string
    maxLength?: number
    placeholder?: string
  }>(),
  {
    disabled: false,
    submitting: false,
    disabledHint: '',
    maxLength: 5000,
    placeholder: '请组织语言后作答，建议包含结论、依据与取舍……',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  submit: []
}>()

const inputRef = ref<{ focus?: () => void } | null>(null)
const composing = ref(false)

const value = computed({
  get: () => props.modelValue,
  set: (next: string) => emit('update:modelValue', next),
})

const canSubmit = computed(
  () => !props.disabled && !props.submitting && props.modelValue.trim().length > 0,
)

const hint = computed(() => {
  if (props.disabled && props.disabledHint) return props.disabledHint
  if (props.submitting) return '提交中，请稍候…'
  return 'Ctrl / Cmd + Enter 提交，Enter 换行；草稿会自动保存'
})

function submit(): void {
  if (!canSubmit.value) return
  emit('submit')
}

function handleKeydown(event: KeyboardEvent | Event): void {
  const keyboard = event as KeyboardEvent
  if (keyboard.isComposing || composing.value) return
  if (keyboard.key === 'Enter' && (keyboard.ctrlKey || keyboard.metaKey)) {
    keyboard.preventDefault()
    submit()
  }
}

function focus(): void {
  inputRef.value?.focus?.()
}

defineExpose({ focus })
</script>

<template>
  <div class="answer-composer">
    <div class="answer-composer__header">
      <span class="answer-composer__title">我的回答</span>
    </div>

    <el-input
      ref="inputRef"
      v-model="value"
      type="textarea"
      :rows="6"
      :maxlength="maxLength"
      show-word-limit
      resize="none"
      :disabled="disabled || submitting"
      :placeholder="placeholder"
      class="answer-composer__input"
      data-testid="answer-input"
      @keydown="handleKeydown"
      @compositionstart="composing = true"
      @compositionend="composing = false"
    />

    <div class="answer-composer__footer">
      <p class="answer-composer__hint" :class="{ 'answer-composer__hint--warn': disabled }">
        {{ hint }}
      </p>
      <el-button
        type="primary"
        size="large"
        :loading="submitting"
        :disabled="!canSubmit"
        class="answer-composer__submit"
        data-testid="answer-submit"
        @click="submit"
      >
        <el-icon v-if="!submitting" :size="16"><Promotion /></el-icon>
        {{ submitting ? '提交中…' : '提交回答' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.answer-composer {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.answer-composer__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}

.answer-composer__title {
  color: var(--color-text);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.answer-composer__input :deep(.el-textarea__inner) {
  min-height: 132px !important;
  border-radius: var(--radius-lg);
  line-height: 1.7;
}

.answer-composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.answer-composer__hint {
  min-width: 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.5;
}

.answer-composer__hint--warn {
  color: var(--color-warning);
  font-weight: 600;
}

.answer-composer__submit {
  flex: none;
}

@media (max-width: 640px) {
  .answer-composer__footer {
    flex-direction: column;
    align-items: stretch;
  }

  .answer-composer__submit {
    width: 100%;
  }
}
</style>
