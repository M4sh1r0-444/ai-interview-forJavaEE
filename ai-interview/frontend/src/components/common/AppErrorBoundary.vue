<script setup lang="ts">
import { onErrorCaptured, ref } from 'vue'
import ErrorState from '@/components/common/ErrorState.vue'

/**
 * 局部错误边界：捕获子树渲染/生命周期异常，避免整页白屏（计划 §4.6）。
 * 事件处理器里的异步错误不会进入这里，由 main.ts 的全局兜底处理。
 */
const error = ref<Error | null>(null)

function normalizeError(value: unknown): Error {
  if (value instanceof Error) return value
  return new Error(typeof value === 'string' ? value : '页面发生未知异常')
}

onErrorCaptured((errorValue) => {
  error.value = normalizeError(errorValue)
  return false
})

function retry(): void {
  error.value = null
  location.reload()
}
</script>

<template>
  <slot v-if="!error" />
  <div v-else class="error-boundary">
    <ErrorState
      title="页面渲染异常"
      :message="error.message"
      retry-text="刷新页面"
      @retry="retry"
    />
  </div>
</template>

<style scoped>
.error-boundary {
  max-width: 760px;
  margin: var(--space-7) auto;
  padding: 0 var(--space-4);
}
</style>
