<script setup lang="ts">
withDefaults(
  defineProps<{
    rows?: number
    type?: 'list' | 'card' | 'chart'
  }>(),
  {
    rows: 5,
    type: 'list',
  },
)
</script>

<template>
  <div class="skeleton" :class="`skeleton--${type}`" aria-busy="true" aria-label="加载中">
    <template v-if="type === 'chart'">
      <div class="skeleton__chart" />
    </template>
    <template v-else-if="type === 'card'">
      <div v-for="index in 3" :key="index" class="skeleton__card">
        <div class="skeleton__line skeleton__line--short" />
        <div class="skeleton__line" />
        <div class="skeleton__line skeleton__line--medium" />
      </div>
    </template>
    <template v-else>
      <div v-for="index in rows" :key="index" class="skeleton__row">
        <div class="skeleton__line" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
}

.skeleton__row,
.skeleton__line,
.skeleton__chart,
.skeleton__card {
  border-radius: var(--radius-sm);
  background: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0.34) 25%,
    rgba(255, 255, 255, 0.72) 37%,
    rgba(255, 255, 255, 0.34) 63%
  );
  background-size: 400% 100%;
  animation: skeleton-loading 1.4s ease infinite;
}

.skeleton__row {
  height: 40px;
}

.skeleton__line {
  height: 14px;
  width: 100%;
  margin-bottom: var(--space-2);
}

.skeleton__line:last-child {
  margin-bottom: 0;
}

.skeleton__line--short {
  width: 36%;
}

.skeleton__line--medium {
  width: 64%;
}

.skeleton--card {
  flex-direction: row;
  flex-wrap: wrap;
}

.skeleton__card {
  flex: 1 1 240px;
  height: 140px;
  padding: var(--space-4);
}

.skeleton__chart {
  height: 320px;
}

@keyframes skeleton-loading {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}
</style>
