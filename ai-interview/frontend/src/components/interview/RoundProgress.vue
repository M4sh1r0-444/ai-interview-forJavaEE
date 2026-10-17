<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    current: number
    planned: number
    followup: number
    finished?: boolean
    followupLimit?: number
  }>(),
  { finished: false, followupLimit: 3 },
)

const safePlanned = computed(() => Math.max(1, props.planned))
const percent = computed(() =>
  Math.min(100, Math.max(0, Math.round((props.current / safePlanned.value) * 100))),
)
const followupText = computed(() => {
  const value = Math.min(props.followup, props.followupLimit)
  return `${value}/${props.followupLimit}`
})
</script>

<template>
  <div class="round-progress" data-testid="round-progress">
    <div class="round-progress__row">
      <span class="round-progress__label">当前轮次</span>
      <strong class="round-progress__value">
        <span data-testid="round-current">{{ current }}</span>
        <span class="round-progress__divider">/</span>
        <span>{{ safePlanned }}</span>
      </strong>
      <span v-if="!finished" class="round-progress__followup" data-testid="followup-count">
        追问 {{ followupText }}
      </span>
      <span v-else class="round-progress__followup">已结束</span>
    </div>
    <div
      class="round-progress__track"
      role="progressbar"
      :aria-valuenow="current"
      :aria-valuemin="0"
      :aria-valuemax="safePlanned"
      :aria-label="`面试进度 ${current}/${safePlanned}`"
    >
      <span class="round-progress__bar" :style="{ width: `${percent}%` }" />
    </div>
    <p v-if="finished" class="round-progress__done">本场面试已完成</p>
  </div>
</template>

<style scoped>
.round-progress {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.round-progress__row {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
}

.round-progress__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.round-progress__value {
  color: var(--color-text);
  font-size: var(--font-size-lg);
  font-variant-numeric: tabular-nums;
}

.round-progress__divider {
  margin: 0 1px;
  color: var(--color-text-tertiary);
  font-weight: 400;
}

.round-progress__followup {
  margin-left: auto;
  padding: 2px 8px;
  border: 1px solid rgba(37, 99, 235, 0.18);
  border-radius: var(--radius-pill);
  background: rgba(37, 99, 235, 0.08);
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.round-progress__track {
  height: 8px;
  overflow: hidden;
  border-radius: var(--radius-pill);
  background: rgba(148, 163, 184, 0.22);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05) inset;
}

.round-progress__bar {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2563eb, #38bdf8);
  box-shadow: 0 0 10px rgba(37, 99, 235, 0.34);
  transition: width var(--transition-base);
}

.round-progress__done {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}
</style>
