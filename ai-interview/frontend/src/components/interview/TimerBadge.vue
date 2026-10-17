<script setup lang="ts">
import { computed } from 'vue'
import { Timer } from '@element-plus/icons-vue'

import { formatDuration } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    remainSeconds?: number | null
    warningThreshold?: number
  }>(),
  { remainSeconds: null, warningThreshold: 60 },
)

const label = computed(() => {
  if (props.remainSeconds == null) return '--:--'
  return formatDuration(props.remainSeconds)
})

const warning = computed(
  () =>
    props.remainSeconds != null &&
    props.remainSeconds > 0 &&
    props.remainSeconds <= props.warningThreshold,
)

const expired = computed(() => props.remainSeconds != null && props.remainSeconds <= 0)

const stateClass = computed(() => {
  if (expired.value) return 'timer-badge--expired'
  if (warning.value) return 'timer-badge--warning'
  return ''
})
</script>

<template>
  <div
    class="timer-badge"
    :class="stateClass"
    role="timer"
    aria-live="off"
    data-testid="timer-badge"
  >
    <el-icon :size="15" aria-hidden="true"><Timer /></el-icon>
    <span class="timer-badge__label">剩余</span>
    <strong class="timer-badge__value">{{ label }}</strong>
    <span v-if="expired" class="timer-badge__hint">时间到</span>
    <span v-else-if="warning" class="timer-badge__hint">即将结束</span>
  </div>
</template>

<style scoped>
.timer-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-pill);
  background: var(--glass-bg-subtle);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.72) inset;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
  backdrop-filter: blur(var(--glass-blur-subtle)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur-subtle)) saturate(var(--glass-saturate));
  transition: all var(--transition-base);
}

.timer-badge__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.timer-badge__value {
  font-size: var(--font-size-md);
  font-weight: 700;
  letter-spacing: 0.03em;
}

.timer-badge__hint {
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.timer-badge--warning {
  border-color: rgba(217, 119, 6, 0.42);
  background: rgba(255, 247, 237, 0.76);
  color: var(--color-warning);
}

.timer-badge--warning .timer-badge__label,
.timer-badge--warning .timer-badge__hint {
  color: var(--color-warning);
}

.timer-badge--expired {
  border-color: rgba(220, 38, 38, 0.42);
  background: rgba(254, 242, 242, 0.8);
  color: var(--color-danger);
}

.timer-badge--expired .timer-badge__label,
.timer-badge--expired .timer-badge__hint {
  color: var(--color-danger);
}

.timer-badge--warning .timer-badge__value {
  animation: timer-pulse 1.2s ease-in-out infinite;
}

@keyframes timer-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.55;
  }
}

@media (prefers-reduced-motion: reduce) {
  .timer-badge--warning .timer-badge__value {
    animation: none;
  }
}
</style>
