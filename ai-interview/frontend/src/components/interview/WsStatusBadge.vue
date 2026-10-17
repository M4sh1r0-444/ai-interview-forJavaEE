<script setup lang="ts">
import { computed } from 'vue'
import { Connection, Refresh } from '@element-plus/icons-vue'

import type { WsConnectionState } from '@/types/ws'

const props = defineProps<{
  state: WsConnectionState
  attempt: number
}>()

defineEmits<{
  reconnect: []
}>()

const view = computed(() => {
  switch (props.state) {
    case 'open':
      return {
        type: 'success' as const,
        text: '实时连接',
        description: 'WebSocket 已连接，AI 消息将实时推送',
      }
    case 'connecting':
      return {
        type: 'info' as const,
        text: '连接中…',
        description: '正在建立实时连接；提交与刷新不依赖连接',
      }
    case 'reconnecting':
      return {
        type: 'warning' as const,
        text: `重连中（第 ${Math.max(1, props.attempt)} 次）`,
        description: '实时连接中断，正在自动重连；已切换轮询兜底',
      }
    case 'closed':
      return {
        type: 'warning' as const,
        text: '实时连接断开',
        description: '已切换为轮询同步，也可以手动重连',
      }
    default:
      return {
        type: 'info' as const,
        text: '实时连接待建立',
        description: '进入房间后将自动连接',
      }
  }
})

const canReconnect = computed(() => props.state === 'reconnecting' || props.state === 'closed')
</script>

<template>
  <div
    class="ws-badge"
    :class="`ws-badge--${view.type}`"
    :title="view.description"
    data-testid="ws-status"
  >
    <el-icon :size="15" aria-hidden="true"><Connection /></el-icon>
    <span class="ws-badge__text" aria-live="polite">{{ view.text }}</span>
    <el-button
      v-if="canReconnect"
      text
      size="small"
      class="ws-badge__retry"
      data-testid="ws-reconnect"
      @click="$emit('reconnect')"
    >
      <el-icon><Refresh /></el-icon>
      重连
    </el-button>
  </div>
</template>

<style scoped>
.ws-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-pill);
  background: var(--glass-bg-subtle);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.72) inset;
  font-size: var(--font-size-xs);
  white-space: nowrap;
  backdrop-filter: blur(var(--glass-blur-subtle)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur-subtle)) saturate(var(--glass-saturate));
}

.ws-badge__text {
  font-weight: 600;
}

.ws-badge__retry {
  height: auto;
  padding: 0 2px;
  font-size: var(--font-size-xs);
}

.ws-badge--success {
  border-color: rgba(22, 163, 74, 0.32);
  background: rgba(240, 253, 244, 0.76);
  color: var(--color-success);
}

.ws-badge--warning {
  border-color: rgba(217, 119, 6, 0.38);
  background: rgba(255, 247, 237, 0.8);
  color: var(--color-warning);
}

.ws-badge--info {
  color: var(--color-text-secondary);
}
</style>
