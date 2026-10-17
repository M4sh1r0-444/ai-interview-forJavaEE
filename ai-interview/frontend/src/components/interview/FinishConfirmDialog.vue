<script setup lang="ts">
import { computed } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'

import { formatDuration } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    mode?: 'finish' | 'abort'
    loading?: boolean
    answeredRounds: number
    currentRound: number
    plannedRounds: number
    remainSeconds?: number | null
  }>(),
  { mode: 'finish', loading: false, remainSeconds: null },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
}>()

const isFinish = computed(() => props.mode === 'finish')
const title = computed(() => (isFinish.value ? '结束本场面试？' : '中止本场面试？'))
const description = computed(() =>
  isFinish.value
    ? '结束后将生成评估报告，本场不能再继续作答。'
    : '中止后不会生成报告，历史记录将标记为“已中止”，此操作不可撤销。',
)
const confirmText = computed(() => (isFinish.value ? '结束并生成报告' : '确认中止'))
const remainText = computed(() =>
  props.remainSeconds == null ? '—' : formatDuration(props.remainSeconds),
)

const visible = computed({
  get: () => props.modelValue,
  set: (next: boolean) => emit('update:modelValue', next),
})

function cancel(): void {
  if (props.loading) return
  visible.value = false
}

function confirm(): void {
  if (props.loading) return
  emit('confirm')
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="440px"
    align-center
    :close-on-click-modal="!loading"
    :close-on-press-escape="!loading"
    :show-close="!loading"
    class="finish-dialog"
    data-testid="finish-dialog"
  >
    <div class="finish-dialog__body">
      <div class="finish-dialog__icon" :class="{ 'finish-dialog__icon--danger': !isFinish }">
        <el-icon :size="22"><WarningFilled /></el-icon>
      </div>
      <p class="finish-dialog__description">{{ description }}</p>

      <dl class="finish-dialog__summary">
        <div>
          <dt>已作答</dt>
          <dd>{{ answeredRounds }} 题</dd>
        </div>
        <div>
          <dt>当前轮次</dt>
          <dd>{{ Math.min(Math.max(currentRound, 1), plannedRounds) }} / {{ plannedRounds }}</dd>
        </div>
        <div>
          <dt>剩余时间</dt>
          <dd>{{ remainText }}</dd>
        </div>
      </dl>
    </div>

    <template #footer>
      <div class="finish-dialog__footer">
        <el-button :disabled="loading" @click="cancel">继续作答</el-button>
        <el-button
          :type="isFinish ? 'primary' : 'danger'"
          :loading="loading"
          data-testid="finish-confirm"
          @click="confirm"
        >
          {{ loading ? '处理中…' : confirmText }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.finish-dialog__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.finish-dialog__icon {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border: 1px solid rgba(217, 119, 6, 0.28);
  border-radius: 14px;
  background: rgba(255, 247, 237, 0.82);
  color: var(--color-warning);
}

.finish-dialog__icon--danger {
  border-color: rgba(220, 38, 38, 0.28);
  background: rgba(254, 242, 242, 0.86);
  color: var(--color-danger);
}

.finish-dialog__description {
  color: var(--color-text-secondary);
  line-height: 1.7;
}

.finish-dialog__summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
  margin: 0;
  padding: var(--space-3);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  background: var(--glass-bg-subtle);
}

.finish-dialog__summary div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-align: center;
}

.finish-dialog__summary dt {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.finish-dialog__summary dd {
  margin: 0;
  color: var(--color-text);
  font-size: var(--font-size-md);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.finish-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}

@media (max-width: 520px) {
  .finish-dialog__summary {
    grid-template-columns: 1fr;
  }

  .finish-dialog__summary div {
    flex-direction: row;
    justify-content: space-between;
    text-align: left;
  }
}
</style>
