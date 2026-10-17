<script setup lang="ts">
import { computed, watch } from 'vue'

import EmptyState from '@/components/common/EmptyState.vue'
import { useEchart } from '@/composables/useEchart'
import type { InterviewMode } from '@/types/interview'
import type { DimensionKey } from '@/types/report'
import { buildRadarOption, dimensionEntries, voiceDimensionNote } from '@/utils/report'

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
const hasEnoughDimensions = computed(() => entries.value.length >= 3)
const note = computed(() => voiceDimensionNote(props.mode, props.scores))

const { el, setOption } = useEchart()

watch(
  entries,
  (value) => {
    if (value.length < 3) return
    setOption(buildRadarOption(value))
  },
  { immediate: true, deep: true },
)
</script>

<template>
  <div class="radar-chart" data-testid="radar-chart">
    <div v-show="hasEnoughDimensions" ref="el" class="radar-chart__canvas" />

    <EmptyState
      v-if="!hasEnoughDimensions"
      title="暂无可展示的维度"
      description="报告生成后这里会显示技术、深度、逻辑、匹配与表达等维度得分。"
      :show-action="false"
    />

    <p v-if="note" class="radar-chart__note">{{ note }}</p>
  </div>
</template>

<style scoped>
.radar-chart {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.radar-chart__canvas {
  width: 100%;
  height: 320px;
}

.radar-chart__note {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

@media (max-width: 640px) {
  .radar-chart__canvas {
    height: 280px;
  }
}
</style>
