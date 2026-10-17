<script setup lang="ts">
import { computed } from 'vue'
import { Microphone, UserFilled } from '@element-plus/icons-vue'

import type { OptimisticState } from '@/stores/interview'
import type { MessageVO } from '@/types/interview'
import { formatDurationMs, formatTime } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    message: MessageVO
    optimisticState?: OptimisticState | null
  }>(),
  { optimisticState: null },
)

const isAi = computed(() => props.message.speaker === 'AI')
const isAudio = computed(() => props.message.inputType === 'AUDIO')
const aiLabel = computed(() => {
  if (!isAi.value) return '我'
  const index = props.message.followupIndex
  if (index == null) return 'AI 面试官'
  return index > 0 ? `追问 ${index}` : '主问题'
})

const statusText = computed(() => {
  if (props.optimisticState === 'pending') return '发送中…'
  if (props.optimisticState === 'sent') return '已送达'
  if (props.optimisticState === 'failed') return '发送失败，内容已保留'
  return ''
})
</script>

<template>
  <article
    class="bubble"
    :class="[
      isAi ? 'bubble--ai' : 'bubble--user',
      props.optimisticState === 'failed' ? 'bubble--failed' : '',
    ]"
    :data-testid="isAi ? 'message-ai' : 'message-user'"
  >
    <div class="bubble__avatar" aria-hidden="true">
      <span v-if="isAi" class="bubble__avatar-text">AI</span>
      <el-icon v-else :size="16"><UserFilled /></el-icon>
    </div>

    <div class="bubble__body">
      <header class="bubble__meta">
        <span class="bubble__speaker">{{ aiLabel }}</span>
        <span v-if="isAudio" class="bubble__audio-tag">
          <el-icon :size="12"><Microphone /></el-icon>
          语音
        </span>
        <span class="bubble__round">第 {{ message.roundNo + 1 }} 题</span>
        <time class="bubble__time" :datetime="message.createdAt">
          {{ formatTime(message.createdAt) }}
        </time>
      </header>

      <div class="bubble__content">{{ message.contentText }}</div>

      <p
        v-if="isAudio && message.asrText && message.asrText !== message.contentText"
        class="bubble__asr"
      >
        转写：{{ message.asrText }}
      </p>
      <p v-if="isAudio && message.audioDurationMs" class="bubble__audio-duration">
        录音时长 {{ formatDurationMs(message.audioDurationMs) }}
      </p>

      <p v-if="statusText" class="bubble__status" :class="`bubble__status--${optimisticState}`">
        {{ statusText }}
      </p>
    </div>
  </article>
</template>

<style scoped>
.bubble {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  max-width: 100%;
}

.bubble--user {
  flex-direction: row-reverse;
}

.bubble__avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 34px;
  height: 34px;
  margin-top: 2px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.18), rgba(14, 165, 233, 0.1));
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.8) inset;
  color: var(--color-primary);
  font-size: 11px;
  font-weight: 700;
}

.bubble__avatar-text {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.02em;
}

.bubble--user .bubble__avatar {
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.08), rgba(37, 99, 235, 0.12));
  color: var(--color-text-secondary);
}

.bubble__body {
  position: relative;
  max-width: min(680px, calc(100% - 46px));
  padding: 12px 14px;
  border: 1px solid var(--glass-border);
  border-radius: 16px 16px 16px 6px;
  background: var(--glass-bg);
  box-shadow: var(--glass-shadow);
  backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
}

.bubble--user .bubble__body {
  border-color: rgba(37, 99, 235, 0.24);
  border-radius: 16px 16px 6px 16px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.14), rgba(14, 165, 233, 0.08));
}

.bubble--failed .bubble__body {
  border-color: rgba(220, 38, 38, 0.38);
  background: rgba(254, 242, 242, 0.74);
}

.bubble__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: var(--font-size-xs);
}

.bubble__speaker {
  color: var(--color-text);
  font-weight: 600;
}

.bubble__audio-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 7px;
  border-radius: var(--radius-pill);
  background: rgba(14, 165, 233, 0.12);
  color: #0369a1;
  font-weight: 600;
}

.bubble__round,
.bubble__time {
  color: var(--color-text-tertiary);
}

.bubble__time {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

.bubble__content {
  color: var(--color-text);
  line-height: 1.72;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.bubble__asr,
.bubble__audio-duration {
  margin-top: 8px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.bubble__status {
  margin-top: 8px;
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.bubble__status--pending,
.bubble__status--sent {
  color: var(--color-text-tertiary);
}

.bubble__status--failed {
  color: var(--color-danger);
}

@media (max-width: 640px) {
  .bubble__body {
    max-width: calc(100% - 42px);
  }
}
</style>
