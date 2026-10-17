<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Promotion, WarningFilled } from '@element-plus/icons-vue'

import AudioRecorder from '@/components/interview/AudioRecorder.vue'
import TranscriptPanel from '@/components/interview/TranscriptPanel.vue'
import { detectInterviewCapabilities } from '@/composables/useInterviewCapabilities'
import { useSpeechRecognition } from '@/composables/useSpeechRecognition'
import { AUDIO_LIMITS, formatFileSize, validateAudioAnswer } from '@/utils/audio'
import { formatDurationMs } from '@/utils/format'

/**
 * 语音作答面板（F4-1~F4-3）：录音 + 实时转写 + 手工修正 + 提交前校验。
 * 录音/识别共用生命周期：开始录音即开始识别，停止录音即停止识别；
 * 提交失败时保留 blob 与转写，用户可修正后重试（不自动重发）。
 */
const props = withDefaults(
  defineProps<{
    disabled?: boolean
    submitting?: boolean
  }>(),
  {
    disabled: false,
    submitting: false,
  },
)

const emit = defineEmits<{
  submit: [payload: { file: Blob; asrText: string; durationMs: number; pauseCount?: number }]
}>()

const recorderRef = ref<InstanceType<typeof AudioRecorder> | null>(null)
const recorderSupported = detectInterviewCapabilities().audioSupported

// 录音状态由 AudioRecorder 内部持有；面板通过事件接收结果，避免创建第二个录音器实例。
const recording = ref(false)
const audioBlob = ref<Blob | null>(null)
const audioDuration = ref<number | null>(null)

const {
  supported: speechSupported,
  listening,
  finalText,
  interimText,
  error: speechError,
  pauseCount,
  start: startSpeech,
  stop: stopSpeech,
  reset: resetSpeech,
} = useSpeechRecognition()

const transcript = ref('')
const edited = ref(false)
const validationError = ref('')

const canSubmit = computed(
  () =>
    !props.disabled &&
    !props.submitting &&
    audioBlob.value != null &&
    transcript.value.trim().length > 0 &&
    transcript.value.length <= AUDIO_LIMITS.maxTranscriptLength,
)

const fileSummary = computed(() => {
  if (!audioBlob.value) return ''
  return `${formatDurationMs(audioDuration.value)} · ${formatFileSize(audioBlob.value.size)}`
})

const disabledHint = computed(() => {
  if (props.submitting) return '正在上传语音回答…'
  if (props.disabled) return '当前不可作答'
  if (!audioBlob.value) return '请先录制一段语音'
  if (!transcript.value.trim()) return '请填写或确认转写文本'
  return ''
})

watch(finalText, (value) => {
  if (!edited.value) transcript.value = value
})

watch(
  () => props.disabled,
  (disabled) => {
    if (!disabled) return
    if (recording.value) recorderRef.value?.stop()
    stopSpeech()
  },
)

function handleRecordingStarted(): void {
  validationError.value = ''
  edited.value = false
  transcript.value = ''
  audioBlob.value = null
  audioDuration.value = null
  recording.value = true
  resetSpeech()
  if (speechSupported) startSpeech()
}

function handleRecordingStopped(): void {
  recording.value = false
  stopSpeech()
  if (!edited.value && finalText.value) transcript.value = finalText.value
}

function handleRecorded(payload: { blob: Blob; durationMs: number }): void {
  audioBlob.value = payload.blob
  audioDuration.value = payload.durationMs
  if (!edited.value && finalText.value) transcript.value = finalText.value
}

function handleTranscriptUpdate(value: string): void {
  transcript.value = value
  edited.value = true
  validationError.value = ''
}

function handleSubmit(): void {
  validationError.value = ''
  const problem = validateAudioAnswer({
    blob: audioBlob.value,
    durationMs: audioDuration.value,
    asrText: transcript.value,
  })
  if (problem) {
    validationError.value = problem
    return
  }
  if (!audioBlob.value || audioDuration.value == null) return
  const payload: { file: Blob; asrText: string; durationMs: number; pauseCount?: number } = {
    file: audioBlob.value,
    asrText: transcript.value.trim(),
    durationMs: audioDuration.value,
  }
  if (pauseCount.value > 0) payload.pauseCount = pauseCount.value
  emit('submit', payload)
}

function reset(): void {
  recorderRef.value?.reset()
  resetSpeech()
  transcript.value = ''
  edited.value = false
  validationError.value = ''
  audioBlob.value = null
  audioDuration.value = null
  recording.value = false
}

function isRecording(): boolean {
  return recording.value
}

function stopRecordingNow(): void {
  if (recording.value) recorderRef.value?.stop()
  stopSpeech()
}

defineExpose({ reset, isRecording, stopRecording: stopRecordingNow })
</script>

<template>
  <div class="audio-answer" data-testid="audio-answer-panel">
    <AudioRecorder
      :disabled="disabled || submitting"
      @started="handleRecordingStarted"
      @stopped="handleRecordingStopped"
      @recorded="handleRecorded"
    />

    <TranscriptPanel
      :model-value="transcript"
      :supported="speechSupported && recorderSupported"
      :listening="listening"
      :interim-text="interimText"
      :speech-error="speechError"
      :disabled="disabled || submitting"
      @update:model-value="handleTranscriptUpdate"
    />

    <div class="audio-answer__submit">
      <div class="audio-answer__summary">
        <p v-if="fileSummary">录音：{{ fileSummary }}</p>
        <p v-else>录制完成后可试听并提交；提交后不可再修改本轮回答。</p>
        <p v-if="disabledHint" class="audio-answer__hint">{{ disabledHint }}</p>
      </div>
      <el-button
        type="primary"
        size="large"
        :loading="submitting"
        :disabled="!canSubmit"
        data-testid="audio-submit"
        @click="handleSubmit"
      >
        <el-icon v-if="!submitting"><Promotion /></el-icon>
        {{ submitting ? '上传中…' : '提交语音回答' }}
      </el-button>
    </div>

    <p
      v-if="validationError"
      class="audio-answer__error"
      role="alert"
      data-testid="audio-validation"
    >
      <el-icon><WarningFilled /></el-icon>
      {{ validationError }}
    </p>

    <p class="audio-answer__privacy">
      录音仅用于本场面试评分与回放，经登录鉴权后由本人访问；音频不会暴露为公开地址。
    </p>
  </div>
</template>

<style scoped>
.audio-answer {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.audio-answer__submit {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3);
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.34);
}

.audio-answer__summary {
  min-width: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.audio-answer__hint {
  color: var(--color-warning);
  font-weight: 600;
}

.audio-answer__error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: var(--space-2) var(--space-3);
  border: 1px solid rgba(220, 38, 38, 0.24);
  border-radius: var(--radius-md);
  background: rgba(254, 242, 242, 0.74);
  color: var(--color-danger);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.audio-answer__privacy {
  color: var(--color-text-tertiary);
  font-size: 11px;
  line-height: 1.6;
}

@media (max-width: 640px) {
  .audio-answer__submit {
    flex-direction: column;
    align-items: stretch;
  }

  .audio-answer__submit :deep(.el-button) {
    width: 100%;
  }
}
</style>
