<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Microphone, RefreshRight, VideoPause, WarningFilled } from '@element-plus/icons-vue'

import { useAudioRecorder } from '@/composables/useAudioRecorder'
import { AUDIO_LIMITS, formatFileSize } from '@/utils/audio'
import { formatDurationMs } from '@/utils/format'

/**
 * 录音组件（F4-1）：权限/能力检测 → 录制（计时/脉冲/上限）→ 停止预览 → 重录。
 * 只负责录音与本地预览；转写与提交由 AudioAnswerPanel 组合。
 * MediaStream 生命周期完全由 useAudioRecorder 管理（停止/重录/卸载均释放）。
 */
const props = withDefaults(
  defineProps<{
    disabled?: boolean
    maxDurationMs?: number
    maxBytes?: number
  }>(),
  {
    disabled: false,
    maxDurationMs: AUDIO_LIMITS.maxDurationMs,
    maxBytes: AUDIO_LIMITS.maxBytes,
  },
)

const emit = defineEmits<{
  started: []
  stopped: []
  recorded: [payload: { blob: Blob; durationMs: number }]
  error: [message: string]
}>()

const {
  supported,
  disabledReason,
  mimeType,
  requesting,
  recording,
  elapsedMs,
  durationMs,
  recordedBytes,
  blob,
  error,
  stopReason,
  progress,
  start,
  stop,
  reset,
} = useAudioRecorder({
  maxDurationMs: props.maxDurationMs,
  maxBytes: props.maxBytes,
})

const previewUrl = ref('')

const recordingBlocked = computed(() => props.disabled || !supported)
const maxMinutes = computed(() => Math.round(props.maxDurationMs / 60_000))
const stopNotice = computed(() => {
  if (stopReason.value === 'max-duration') {
    return `已达单次录音上限（${maxMinutes.value} 分钟），录音已自动停止`
  }
  return ''
})
const sizeText = computed(() =>
  blob.value ? formatFileSize(blob.value.size) : formatFileSize(recordedBytes.value),
)

function releasePreview(): void {
  if (!previewUrl.value) return
  URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

watch(
  blob,
  (value) => {
    releasePreview()
    if (value && typeof URL.createObjectURL === 'function') {
      previewUrl.value = URL.createObjectURL(value)
    }
    if (value && durationMs.value != null) {
      emit('recorded', { blob: value, durationMs: durationMs.value })
    }
  },
  { flush: 'post' },
)

watch(recording, (value, previous) => {
  if (previous && !value) emit('stopped')
})

watch(error, (value) => {
  if (value) emit('error', value)
})

async function handleStart(): Promise<void> {
  if (recordingBlocked.value) return
  await start()
  if (recording.value) emit('started')
}

function handleStop(): void {
  stop('manual')
}

function handleRerecord(): void {
  reset()
  void handleStart()
}

function focus(): void {
  if (!recording.value) void handleStart()
}

defineExpose({ reset, focus, stop })

onBeforeUnmount(() => {
  releasePreview()
})
</script>

<template>
  <section class="audio-recorder" data-testid="audio-recorder">
    <header class="audio-recorder__head">
      <div>
        <h3>语音作答</h3>
        <p v-if="supported" class="audio-recorder__hint">
          {{ mimeType === 'audio/webm;codecs=opus' ? 'WebM / Opus' : 'WebM' }} 录音 · 单次最长
          {{ maxMinutes }} 分钟 · 不超过 {{ formatFileSize(maxBytes) }}
        </p>
        <p v-else class="audio-recorder__hint">当前浏览器不支持录音</p>
      </div>
      <el-tag v-if="recording" type="danger" effect="dark" round class="audio-recorder__live">
        <span class="audio-recorder__dot" aria-hidden="true" />
        录音中
      </el-tag>
      <el-tag v-else-if="blob" type="success" effect="plain" round>已录制</el-tag>
    </header>

    <div v-if="!supported" class="audio-recorder__alert" data-testid="audio-unsupported">
      <el-icon><WarningFilled /></el-icon>
      <span>{{
        disabledReason || '当前浏览器不支持录音，请使用 Chrome / Edge 或新建文字面试。'
      }}</span>
    </div>

    <template v-else>
      <div v-if="requesting" class="audio-recorder__requesting" role="status">
        <span class="audio-recorder__spinner" aria-hidden="true" />
        正在请求麦克风权限…
      </div>

      <div v-else-if="recording" class="audio-recorder__recording" data-testid="audio-recording">
        <div class="audio-recorder__timer" data-testid="audio-timer">
          <span class="audio-recorder__pulse" aria-hidden="true" />
          {{ formatDurationMs(elapsedMs) }}
        </div>
        <div class="audio-recorder__progress" aria-hidden="true">
          <span :style="{ width: `${Math.round(progress * 100)}%` }" />
        </div>
        <p class="audio-recorder__live-hint">正在录音，请对着麦克风作答；转写会实时出现在下方。</p>
        <el-button
          type="danger"
          size="large"
          :disabled="disabled"
          data-testid="audio-stop"
          @click="handleStop"
        >
          <el-icon><VideoPause /></el-icon>
          停止录音
        </el-button>
      </div>

      <div v-else-if="blob" class="audio-recorder__result" data-testid="audio-result">
        <audio
          v-if="previewUrl"
          :src="previewUrl"
          controls
          preload="metadata"
          class="audio-recorder__player"
          data-testid="audio-preview"
        />
        <p class="audio-recorder__meta">
          时长 {{ formatDurationMs(durationMs) }} · 大小 {{ sizeText }}
        </p>
        <div class="audio-recorder__actions">
          <el-button
            :icon="RefreshRight"
            :disabled="disabled"
            data-testid="audio-rerecord"
            @click="handleRerecord"
          >
            重新录制
          </el-button>
        </div>
        <p class="audio-recorder__tip">确认转写内容无误后，在下方点击"提交语音回答"。</p>
      </div>

      <div v-else class="audio-recorder__idle">
        <button
          type="button"
          class="audio-recorder__record"
          :disabled="recordingBlocked"
          data-testid="audio-start"
          @click="handleStart"
        >
          <span class="audio-recorder__record-ring" aria-hidden="true">
            <el-icon :size="26"><Microphone /></el-icon>
          </span>
          <span class="audio-recorder__record-text">开始录音</span>
        </button>
        <p class="audio-recorder__tip">
          首次录音会请求麦克风权限；录音结束后可试听、重录，并在下方修正转写文本。
        </p>
      </div>
    </template>

    <p v-if="stopNotice" class="audio-recorder__notice" data-testid="audio-stop-notice">
      <el-icon><WarningFilled /></el-icon>
      {{ stopNotice }}
    </p>
    <p v-if="error" class="audio-recorder__error" role="alert" data-testid="audio-error">
      <el-icon><WarningFilled /></el-icon>
      {{ error }}
    </p>
  </section>
</template>

<style scoped>
.audio-recorder {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid rgba(37, 99, 235, 0.18);
  border-radius: var(--radius-lg);
  background: rgba(239, 246, 255, 0.42);
}

.audio-recorder__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.audio-recorder__head h3 {
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.audio-recorder__hint,
.audio-recorder__tip {
  margin-top: 2px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.audio-recorder__live {
  flex: none;
}

.audio-recorder__dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 5px;
  border-radius: 50%;
  background: currentColor;
  vertical-align: middle;
  animation: audio-blink 1.2s ease-in-out infinite;
}

.audio-recorder__alert,
.audio-recorder__error,
.audio-recorder__notice {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: var(--space-2) var(--space-3);
  border: 1px solid rgba(217, 119, 6, 0.3);
  border-radius: var(--radius-md);
  background: rgba(255, 247, 237, 0.72);
  color: var(--color-warning);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.audio-recorder__error {
  border-color: rgba(220, 38, 38, 0.26);
  background: rgba(254, 242, 242, 0.76);
  color: var(--color-danger);
}

.audio-recorder__notice {
  border-color: rgba(37, 99, 235, 0.22);
  background: rgba(239, 246, 255, 0.72);
  color: var(--color-primary);
}

.audio-recorder__requesting {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.audio-recorder__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(37, 99, 235, 0.24);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: audio-spin 0.8s linear infinite;
}

.audio-recorder__recording {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid rgba(220, 38, 38, 0.2);
  border-radius: var(--radius-md);
  background: rgba(254, 242, 242, 0.55);
}

.audio-recorder__timer {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-danger);
  font-size: var(--font-size-2xl);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.audio-recorder__pulse {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--color-danger);
  box-shadow: 0 0 0 0 rgba(220, 38, 38, 0.42);
  animation: audio-pulse 1.4s ease-out infinite;
}

.audio-recorder__progress {
  overflow: hidden;
  height: 6px;
  border-radius: var(--radius-pill);
  background: rgba(220, 38, 38, 0.14);
}

.audio-recorder__progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #ef4444, #f97316);
  transition: width 200ms linear;
}

.audio-recorder__live-hint {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.audio-recorder__idle {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
}

.audio-recorder__record {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  padding: 10px 20px 10px 12px;
  border: 1px solid rgba(37, 99, 235, 0.24);
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.76);
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.12);
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  font-weight: 700;
  cursor: pointer;
  transition:
    transform var(--transition-base),
    box-shadow var(--transition-base);
}

.audio-recorder__record:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 26px rgba(37, 99, 235, 0.18);
}

.audio-recorder__record:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.audio-recorder__record-ring {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2563eb, #38bdf8);
  color: #fff;
}

.audio-recorder__result {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.audio-recorder__player {
  width: 100%;
  height: 40px;
}

.audio-recorder__meta {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.audio-recorder__actions {
  display: flex;
  gap: var(--space-2);
}

@keyframes audio-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(220, 38, 38, 0.42);
  }
  100% {
    box-shadow: 0 0 0 14px rgba(220, 38, 38, 0);
  }
}

@keyframes audio-blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.35;
  }
}

@keyframes audio-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .audio-recorder__dot,
  .audio-recorder__pulse,
  .audio-recorder__spinner {
    animation: none;
  }

  .audio-recorder__progress span {
    transition: none;
  }
}
</style>
