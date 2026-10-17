<script setup lang="ts">
import { computed, watch } from 'vue'

import EmptyState from '@/components/common/EmptyState.vue'
import { useEchart } from '@/composables/useEchart'
import type { GrowthSeries } from '@/utils/growth'
import { buildGrowthOption, hasGrowthData } from '@/utils/growth'

/**
 * 成长多折线（F4-4，计划 §8.4）：time 轴 + 每技能一条 + legend 滚动 + 空态。
 * useEchart 负责 ResizeObserver 与卸载 dispose（侧栏折叠/窗口变化会 resize）。
 */
const props = withDefaults(
  defineProps<{
    series?: GrowthSeries[]
  }>(),
  {
    series: () => [],
  },
)

const hasData = computed(() => hasGrowthData(props.series))
const { el, setOption } = useEchart()

watch(
  () => props.series,
  (series) => {
    if (!hasGrowthData(series)) return
    setOption(buildGrowthOption(series))
  },
  { immediate: true, deep: true },
)
</script>

<template>
  <div class="growth-chart" data-testid="growth-chart">
    <div v-show="hasData" ref="el" class="growth-chart__canvas" />

    <EmptyState
      v-if="!hasData"
      title="还没有成长记录"
      description="先完成一场面试，系统会按技能记录掌握度并绘制变化曲线。"
      :show-action="false"
    />
  </div>
</template>

<style scoped>
.growth-chart {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.growth-chart__canvas {
  width: 100%;
  height: 360px;
}

@media (max-width: 768px) {
  .growth-chart__canvas {
    height: 300px;
  }
}
</style>
