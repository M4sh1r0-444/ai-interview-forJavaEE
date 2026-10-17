<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    description?: string
    actionText?: string
    showAction?: boolean
  }>(),
  {
    showAction: true,
  },
)

defineEmits<{
  action: []
}>()
</script>

<template>
  <div class="empty-state">
    <svg
      class="empty-state__art"
      viewBox="0 0 120 90"
      role="img"
      aria-label="暂无数据"
      xmlns="http://www.w3.org/2000/svg"
    >
      <rect x="18" y="16" width="84" height="58" rx="10" fill="#eff6ff" />
      <rect x="30" y="28" width="60" height="6" rx="3" fill="#bfdbfe" />
      <rect x="30" y="41" width="44" height="6" rx="3" fill="#dbeafe" />
      <rect x="30" y="54" width="52" height="6" rx="3" fill="#dbeafe" />
      <circle cx="92" cy="70" r="12" fill="#bfdbfe" />
      <path d="M88 70h8M92 66v8" stroke="#2563eb" stroke-width="2" stroke-linecap="round" />
    </svg>
    <h3 class="empty-state__title">{{ title }}</h3>
    <p v-if="description" class="empty-state__description">{{ description }}</p>
    <el-button
      v-if="showAction && actionText"
      type="primary"
      class="empty-state__action"
      @click="$emit('action')"
    >
      {{ actionText }}
    </el-button>
    <slot name="actions" />
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-7) var(--space-5);
  text-align: center;
}

.empty-state__art {
  width: 120px;
  height: 90px;
  margin-bottom: var(--space-2);
}

.empty-state__title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-text);
}

.empty-state__description {
  max-width: 520px;
  color: var(--color-text-secondary);
}

.empty-state__action {
  margin-top: var(--space-3);
}
</style>
