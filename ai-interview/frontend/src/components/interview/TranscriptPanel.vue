<script setup lang="ts">
import { computed } from 'vue'
import { EditPen, Microphone, WarningFilled } from '@element-plus/icons-vue'

import { AUDIO_LIMITS } from '@/utils/audio'

/**
 * 转写面板（F4-2）：实时字幕（final + interim）+ 可编辑最终文本 + 手工转写降级。
 * 不在组件内调用 Web Speech；由 AudioAnswerPanel 注入识别状态，保证录音与识别同生命周期。
 */
const props = withDefaults(
  defineProps<{
    modelValue: string
    supported?: boolean
    listening?: boolean
    interimText?: string
    speechError?: string
    disabled?: boolean
    maxLength?: number
  }>(),
  {
    supported: true,
    listening: false,
    interimText: '',
    speechError: '',
    disabled: false,
    maxLength: AUDIO_LIMITS.maxTranscriptLength,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const value = computed({
  get: () => props.modelValue,
  set: (next: string) => emit('update:modelValue', next),
})

const overLimit = computed(() => props.modelValue.length > props.maxLength)
const showEmpty = computed(
  () => !props.modelValue && !props.interimText && !props.listening && !props.speechError,
)
const badge = computed(() => {
  if (!props.supported) return { label: '手工转写', type: 'info' as const }
  if (props.listening) return { label: '识别中', type: 'danger' as const }
  return { label: '自动转写已就绪', type: 'success' as const }
})
</script>

<template>
  <section class="transcript" data-testid="transcript-panel">
    <header class="transcript__head">
      <div>
        <h3>
          <el-icon><EditPen /></el-icon>
          转写文本
        </h3>
        <p v-if="supported">识别结果会自动填入；停止录音后可修正错别字，再提交语音回答。</p>
        <p v-else>本浏览器不支持自动转写，请在录音后手工填写完整回答。</p>
      </div>
      <el-tag :type="badge.type" effect="plain" round size="small">{{ badge.label }}</el-tag>
    </header>

    <el-input
      v-model="value"
      type="textarea"
      :rows="5"
      :maxlength="maxLength"
      resize="none"
      :disabled="disabled"
      placeholder="录音开始后会自动显示转写；也可以直接在此手工填写/修正回答内容"
      class="transcript__input"
      data-testid="transcript-input"
    />

    <p v-if="interimText" class="transcript__interim" aria-live="polite">
      <el-icon><Microphone /></el-icon>
      识别中：{{ interimText }}
    </p>

    <p v-if="showEmpty" class="transcript__empty">
      尚未识别到内容。可以重新录制，或直接在上方手工填写。
    </p>

    <p v-if="speechError" class="transcript__error" role="alert" data-testid="transcript-error">
      <el-icon><WarningFilled /></el-icon>
      {{ speechError }}
    </p>

    <div class="transcript__footer">
      <span :class="{ 'transcript__count--over': overLimit }" class="transcript__count">
        {{ modelValue.length }} / {{ maxLength }}
      </span>
      <span class="transcript__hint"> 提交时以当前文本为准（后端使用它计算语音指标与评分）。 </span>
    </div>
  </section>
</template>

<style scoped>
.transcript {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-4);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  background: var(--glass-bg-subtle);
}

.transcript__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.transcript__head h3 {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.transcript__head p {
  margin-top: 2px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.transcript__input :deep(.el-textarea__inner) {
  min-height: 112px !important;
  border-radius: var(--radius-md);
  line-height: 1.7;
}

.transcript__interim {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  font-style: italic;
  line-height: 1.6;
}

.transcript__empty {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.transcript__error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: var(--space-2) var(--space-3);
  border: 1px solid rgba(217, 119, 6, 0.3);
  border-radius: var(--radius-md);
  background: rgba(255, 247, 237, 0.72);
  color: var(--color-warning);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.transcript__footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.transcript__count {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.transcript__count--over {
  color: var(--color-danger);
  font-weight: 700;
}

.transcript__hint {
  color: var(--color-text-tertiary);
  font-size: 11px;
}

@media (max-width: 640px) {
  .transcript__head {
    flex-direction: column;
  }
}
</style>
