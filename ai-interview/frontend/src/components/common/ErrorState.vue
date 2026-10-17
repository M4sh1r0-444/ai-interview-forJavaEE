<script setup lang="ts">
import TraceIdText from '@/components/common/TraceIdText.vue'

withDefaults(
  defineProps<{
    title?: string
    message: string
    traceId?: string
    code?: number | string
    retryText?: string
    showRetry?: boolean
  }>(),
  {
    title: '加载失败',
    retryText: '重试',
    showRetry: true,
  },
)

defineEmits<{
  retry: []
}>()
</script>

<template>
  <div class="error-state" role="alert">
    <div class="error-state__icon" aria-hidden="true">!</div>
    <h3 class="error-state__title">{{ title }}</h3>
    <p class="error-state__message">{{ message }}</p>
    <p v-if="code !== undefined && code !== ''" class="error-state__code">错误码：{{ code }}</p>
    <TraceIdText v-if="traceId" :trace-id="traceId" class="error-state__trace" />
    <div class="error-state__actions">
      <el-button v-if="showRetry" type="primary" @click="$emit('retry')">
        {{ retryText }}
      </el-button>
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.error-state {
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  overflow: hidden;
  padding: var(--space-7) var(--space-5);
  text-align: center;
  border: 1px solid var(--glass-border);
  border-radius: var(--glass-radius);
  background: var(--glass-bg);
  box-shadow: var(--glass-shadow);
  backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
}

.error-state::before {
  position: absolute;
  inset: 0;
  z-index: -1;
  border-radius: inherit;
  background: var(--glass-sheen);
  content: '';
  pointer-events: none;
}

.error-state__icon {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  margin-bottom: var(--space-1);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(220, 38, 38, 0.18), rgba(255, 255, 255, 0.32));
  box-shadow:
    0 8px 20px rgba(220, 38, 38, 0.12),
    0 1px 0 rgba(255, 255, 255, 0.7) inset;
  color: var(--color-danger);
  font-size: 22px;
  font-weight: 700;
}

.error-state__title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-text);
}

.error-state__message {
  max-width: 560px;
  color: var(--color-text-secondary);
}

.error-state__code {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.error-state__trace {
  margin-top: var(--space-1);
}

.error-state__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-3);
  margin-top: var(--space-3);
}
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .error-state {
    background: var(--glass-bg-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .error-state {
    background: var(--glass-bg-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}
</style>
