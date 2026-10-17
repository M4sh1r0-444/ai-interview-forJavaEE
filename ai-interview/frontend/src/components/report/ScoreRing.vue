<script setup lang="ts">
import { computed } from 'vue'

import { formatScore } from '@/utils/format'
import { scoreLevelLabel, scoreTone } from '@/utils/score'

const props = withDefaults(
  defineProps<{
    score?: number | null
    size?: number
    label?: string
  }>(),
  {
    score: null,
    size: 168,
    label: '总分',
  },
)

const RADIUS = 54
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

const safeScore = computed(() => {
  if (props.score == null || !Number.isFinite(props.score)) return 0
  return Math.max(0, Math.min(100, props.score))
})
const displayScore = computed(() =>
  props.score == null || !Number.isFinite(props.score) ? '—' : formatScore(props.score),
)
const level = computed(() => scoreLevelLabel(props.score))
const tone = computed(() => scoreTone(props.score))
const dashOffset = computed(() => CIRCUMFERENCE * (1 - safeScore.value / 100))
const ringSize = computed(() => Math.max(96, props.size))
</script>

<template>
  <div
    class="score-ring"
    :class="`score-ring--${tone}`"
    :style="{ width: `${ringSize}px`, height: `${ringSize}px` }"
    role="img"
    :aria-label="`${label} ${displayScore} 分，${level}`"
    data-testid="score-ring"
  >
    <svg class="score-ring__svg" viewBox="0 0 128 128" aria-hidden="true">
      <circle class="score-ring__track" cx="64" cy="64" r="54" />
      <circle
        class="score-ring__value"
        cx="64"
        cy="64"
        r="54"
        :stroke-dasharray="CIRCUMFERENCE"
        :stroke-dashoffset="dashOffset"
      />
    </svg>
    <div class="score-ring__center">
      <span class="score-ring__label">{{ label }}</span>
      <strong class="score-ring__number" data-testid="score-ring-value">{{ displayScore }}</strong>
      <span class="score-ring__level">{{ level }}</span>
    </div>
  </div>
</template>

<style scoped>
.score-ring {
  --ring-color: var(--color-danger);
  position: relative;
  display: grid;
  flex: none;
  place-items: center;
}

.score-ring--warning {
  --ring-color: var(--color-warning);
}

.score-ring--primary {
  --ring-color: var(--color-primary);
}

.score-ring--success {
  --ring-color: var(--color-success);
}

.score-ring__svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.score-ring__track,
.score-ring__value {
  fill: none;
  stroke-width: 9;
  stroke-linecap: round;
}

.score-ring__track {
  stroke: rgba(148, 163, 184, 0.22);
}

.score-ring__value {
  stroke: var(--ring-color);
  filter: drop-shadow(0 3px 8px rgba(37, 99, 235, 0.16));
  transition: stroke-dashoffset 600ms ease;
}

.score-ring__center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  line-height: 1.15;
}

.score-ring__label {
  color: var(--color-text-tertiary);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.score-ring__number {
  color: var(--ring-color);
  font-size: 30px;
  font-variant-numeric: tabular-nums;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.score-ring__level {
  color: var(--color-text-secondary);
  font-size: 11px;
  font-weight: 600;
}

@media (prefers-reduced-motion: reduce) {
  .score-ring__value {
    transition: none;
  }
}
</style>
