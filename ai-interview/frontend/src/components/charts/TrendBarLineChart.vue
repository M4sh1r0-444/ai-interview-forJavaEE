<script setup lang="ts">
import { computed, watch } from 'vue'

import EmptyState from '@/components/common/EmptyState.vue'
import { useEchart } from '@/composables/useEchart'
import type { DashboardTrendPoint } from '@/types/admin'
import { buildDashboardTrendOption } from '@/utils/admin'

/**
 * 看板近 7 日柱线组合图（F5-2，计划 §5.13 / §8.4）：
 * 柱=每日开始场次（左轴），线=每日出分均分（右轴），null 均分用断点表示。
 * useEchart 负责 ResizeObserver 与卸载 dispose。
 */
const props = withDefaults(
  defineProps<{
    points?: DashboardTrendPoint[]
  }>(),
  {
    points: () => [],
  },
)

const hasData = computed(() => props.points.length > 0)
const { el, setOption } = useEchart()

watch(
  () => props.points,
  (points) => {
    if (points.length === 0) return
    setOption(buildDashboardTrendOption(points))
  },
  { immediate: true, deep: true },
)
</script>

<template>
  <div class="dashboard-trend" data-testid="dashboard-trend">
    <div v-show="hasData" ref="el" class="dashboard-trend__canvas" />

    <EmptyState
      v-if="!hasData"
      title="近 7 日暂无趋势数据"
      description="完成面试后，这里会按日展示场次与平均分；当前没有可绘制的数据点。"
      :show-action="false"
    />
  </div>
</template>

<style scoped>
.dashboard-trend {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.dashboard-trend__canvas {
  width: 100%;
  height: 340px;
}

@media (max-width: 768px) {
  .dashboard-trend__canvas {
    height: 280px;
  }
}
</style>
