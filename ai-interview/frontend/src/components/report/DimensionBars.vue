<script setup lang="ts">
import { computed } from 'vue'

import type { InterviewMode } from '@/types/interview'
import type { DimensionKey } from '@/types/report'
import { dimensionEntries, formatDimensionScore, voiceDimensionNote } from '@/utils/report'
import { scoreTone } from '@/utils/score'

const props = withDefaults(
  defineProps<{
    scores?: Partial<Record<DimensionKey, number>> | null
    mode?: InterviewMode | null
  }>(),
  {
    scores: null,
    mode: null,
  },
)

const entries = computed(() => dimensionEntries(props.scores, props.mode))
const note = computed(() => voiceDimensionNote(props.mode, props.scores))
</script>

<template>
  <div class="dimension-bars" data-testid="dimension-bars">
    <div v-if="entries.length === 0" class="dimension-bars__empty">暂无维度得分</div>

    <div
      v-for="entry in entries"
      :key="entry.key"
      class="dimension-row"
      :class="`dimension-row--${scoreTone(entry.value)}`"
      :data-testid="`dimension-${entry.key}`"
    >
      <span class="dimension-row__label">{{ entry.label }}</span>
      <span class="dimension-row__track" aria-hidden="true">
        <span class="dimension-row__fill" :style="{ width: `${entry.value}%` }" />
      </span>
      <strong class="dimension-row__value">{{ formatDimensionScore(entry.value) }}</strong>
    </div>

    <p v-if="note" class="dimension-bars__note">{{ note }}</p>
  </div>
</template>

<style scoped>
.dimension-bars {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.dimension-row {
  --bar-color: var(--color-danger);
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr) 48px;
  align-items: center;
  gap: var(--space-3);
}

.dimension-row--warning {
  --bar-color: var(--color-warning);
}

.dimension-row--primary {
  --bar-color: var(--color-primary);
}

.dimension-row--success {
  --bar-color: var(--color-success);
}

.dimension-row__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.dimension-row__track {
  display: block;
  overflow: hidden;
  height: 9px;
  border-radius: var(--radius-pill);
  background: rgba(148, 163, 184, 0.18);
}

.dimension-row__fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background-color: var(--bar-color);
  background-image: linear-gradient(
    90deg,
    color-mix(in srgb, var(--bar-color) 62%, #fff),
    var(--bar-color)
  );
  transition: width 500ms ease;
}

.dimension-row__value {
  color: var(--bar-color);
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.dimension-bars__note,
.dimension-bars__empty {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.dimension-bars__empty {
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.32);
  text-align: center;
}

@media (max-width: 640px) {
  .dimension-row {
    grid-template-columns: 76px minmax(0, 1fr) 42px;
    gap: var(--space-2);
  }
}

@media (prefers-reduced-motion: reduce) {
  .dimension-row__fill {
    transition: none;
  }
}
</style>
