<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  CircleCheck,
  Refresh,
  SwitchButton,
  WarningFilled,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { errorMessage, isBizError } from '@/api/http'
import ErrorState from '@/components/common/ErrorState.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import AnswerComposer from '@/components/interview/AnswerComposer.vue'
import AudioAnswerPanel from '@/components/interview/AudioAnswerPanel.vue'
import FinishConfirmDialog from '@/components/interview/FinishConfirmDialog.vue'
import MessageBubble from '@/components/interview/MessageBubble.vue'
import RoundProgress from '@/components/interview/RoundProgress.vue'
import StatusTag from '@/components/interview/StatusTag.vue'
import TimerBadge from '@/components/interview/TimerBadge.vue'
import WsStatusBadge from '@/components/interview/WsStatusBadge.vue'
import { useAudioReplay } from '@/composables/useAudioReplay'
import { useInterviewCapabilities } from '@/composables/useInterviewCapabilities'
import { useInterviewRoom } from '@/composables/useInterviewRoom'
import { useInterviewStore } from '@/stores/interview'
import { INTERVIEW_MODE_LABELS } from '@/utils/constants'
import { formatDateTime, formatScore } from '@/utils/format'
import { scoreTone } from '@/utils/score'

const route = useRoute()
const router = useRouter()
const store = useInterviewStore()

const sessionId = computed(() => {
  const parsed = Number(route.params.id)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 0
})
const validSessionId = computed(() => sessionId.value > 0)

const {
  wsState,
  wsAttempt,
  countdownRemain,
  answerDraft,
  submitError,
  actionError,
  startFailed,
  aiSlow,
  disabledHint,
  canAnswer,
  bubbles,
  terminalTitle,
  terminalDescription,
  initialize,
  retryLoad,
  retryStart,
  submitAnswer,
  submitAudio,
  finishRoom,
  abortRoom,
  leaveRoom,
  reconnect,
} = useInterviewRoom(sessionId)

const finishDialogOpen = ref(false)
const abortDialogOpen = ref(false)
const chatList = ref<HTMLElement | null>(null)
const composerRef = ref<{ focus?: () => void } | null>(null)
const audioPanelRef = ref<InstanceType<typeof AudioAnswerPanel> | null>(null)
const capabilities = useInterviewCapabilities()
const nearBottom = ref(true)
const hasNewMessages = ref(false)

const modeLabel = computed(() =>
  store.detail ? INTERVIEW_MODE_LABELS[store.detail.mode] : '文字面试',
)
const detailStatus = computed(() => store.detail?.status ?? null)
const loadErrorCode = computed(() => (isBizError(store.loadError) ? store.loadError.code : null))
const loadErrorTrace = computed(() =>
  isBizError(store.loadError) ? (store.loadError.traceId ?? '') : '',
)
const loadErrorText = computed(() => errorMessage(store.loadError, '面试房间加载失败'))
const isNotFound = computed(() => loadErrorCode.value === 40401)
const showReportEntry = computed(
  () => store.isTerminal && (detailStatus.value === 'FINISHED' || detailStatus.value === 'TIMEOUT'),
)
const showRestartEntry = computed(() => detailStatus.value === 'ABORTED')
const isAudioMode = computed(() => store.detail?.mode === 'AUDIO')
const audioReady = computed(() => capabilities.audioSupported)

function updateNearBottom(): void {
  const el = chatList.value
  if (!el) return
  nearBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 120
  if (nearBottom.value) hasNewMessages.value = false
}

function scrollToBottom(behavior: ScrollBehavior = 'smooth'): void {
  const el = chatList.value
  if (!el) return
  el.scrollTo({ top: el.scrollHeight, behavior })
  nearBottom.value = true
  hasNewMessages.value = false
}

function focusComposer(): void {
  // 语音模式不自动聚焦输入框；移动端不自动聚焦，避免键盘把首题顶出屏幕。
  if (isAudioMode.value) return
  if (window.innerWidth < 900) return
  void nextTick(() => composerRef.value?.focus?.())
}

const {
  playingRound,
  loadingRound,
  error: replayError,
  replay: replayAudio,
  stop: stopAudioReplay,
} = useAudioReplay(sessionId)

function handleReplay(messageId: number, roundNo: number): void {
  void replayAudio(roundNo, messageId)
}

function isReplayable(messageId: number, audioPath?: string | null): boolean {
  return Boolean(audioPath) && messageId > 0
}

async function confirmFinish(): Promise<void> {
  await finishRoom()
  if (store.isTerminal) finishDialogOpen.value = false
}

async function confirmAbort(): Promise<void> {
  await abortRoom()
  if (store.isTerminal) abortDialogOpen.value = false
}

function openFinish(): void {
  actionError.value = ''
  // 录音中结束面试：先停止录音，避免 MediaStream 与提交竞态；未提交录音保留在面板中
  audioPanelRef.value?.stopRecording()
  finishDialogOpen.value = true
}

function openAbort(): void {
  actionError.value = ''
  audioPanelRef.value?.stopRecording()
  abortDialogOpen.value = true
}

function goReport(): void {
  if (!store.detail) return
  void router.push({
    name: 'interview-report',
    params: { id: String(store.detail.id) },
  })
}

function startAgain(): void {
  if (!store.detail) return
  void router.push({
    name: 'interview-setup',
    query: { positionId: String(store.detail.positionId) },
  })
}

function goHistory(): void {
  void router.push('/history')
}

function handleAnswerSubmit(): void {
  void submitAnswer()
}

async function handleAudioSubmit(payload: {
  file: Blob
  asrText: string
  durationMs: number
  pauseCount?: number
}): Promise<void> {
  const ok = await submitAudio(payload)
  if (ok) {
    audioPanelRef.value?.reset()
    await nextTick()
    if (nearBottom.value) scrollToBottom('smooth')
  }
}

function switchToTextSetup(): void {
  if (!store.detail) return
  void router.push({
    name: 'interview-setup',
    query: { positionId: String(store.detail.positionId) },
  })
}

async function handleLeave(): Promise<void> {
  if (audioPanelRef.value?.isRecording()) {
    try {
      await ElMessageBox.confirm(
        '正在录音；离开将停止录音，且这一段语音不会提交。',
        '离开面试房间',
        {
          type: 'warning',
          confirmButtonText: '停止并离开',
          cancelButtonText: '继续录音',
        },
      )
    } catch {
      return
    }
    audioPanelRef.value.stopRecording()
  }
  await leaveRoom()
}

onBeforeRouteLeave(async () => {
  if (!audioPanelRef.value?.isRecording()) return true
  try {
    await ElMessageBox.confirm(
      '正在录音；离开将停止录音，且这一段语音不会提交。',
      '确认离开面试房间',
      {
        type: 'warning',
        confirmButtonText: '停止并离开',
        cancelButtonText: '继续录音',
      },
    )
  } catch {
    return false
  }
  audioPanelRef.value.stopRecording()
  return true
})

watch(
  sessionId,
  (id) => {
    if (id > 0) void initialize()
  },
  { immediate: true },
)

watch(
  () => [bubbles.value.length, store.aiThinking, aiSlow.value],
  async () => {
    await nextTick()
    if (nearBottom.value) scrollToBottom('smooth')
    else hasNewMessages.value = true
  },
)

watch(
  () => store.awaitingAnswer,
  async (awaiting) => {
    if (!awaiting) return
    await nextTick()
    focusComposer()
  },
)

watch(
  () => store.isTerminal,
  (terminal) => {
    if (!terminal) return
    finishDialogOpen.value = false
    abortDialogOpen.value = false
    hasNewMessages.value = false
    stopAudioReplay()
    void nextTick(() => scrollToBottom('auto'))
  },
)

watch(
  () => store.loadError,
  (error) => {
    if (isBizError(error) && error.code === 40401) {
      ElMessage.error('面试记录不存在或无权访问')
    }
  },
)

onBeforeUnmount(() => {
  stopAudioReplay()
  store.reset()
})
</script>

<template>
  <div v-if="!validSessionId" class="room room--invalid">
    <ErrorState
      title="面试编号无效"
      message="当前地址中的面试编号无法识别，请从面试历史重新进入。"
      :show-retry="false"
    >
      <template #actions>
        <el-button type="primary" @click="goHistory">返回面试历史</el-button>
      </template>
    </ErrorState>
  </div>

  <div v-else class="room">
    <div
      v-if="!store.detail && !store.loadError && (store.loading || !store.loaded)"
      class="room-loading"
    >
      <div class="room-loading__bar" />
      <p>正在加载面试房间…</p>
    </div>

    <ErrorState
      v-else-if="!store.detail && store.loadError"
      :title="isNotFound ? '面试记录不存在或无权访问' : '面试房间加载失败'"
      :message="loadErrorText"
      :code="loadErrorCode ?? undefined"
      :trace-id="loadErrorTrace"
      @retry="retryLoad"
    >
      <template #actions>
        <el-button type="primary" @click="goHistory">返回面试历史</el-button>
      </template>
    </ErrorState>

    <template v-else-if="store.detail">
      <header class="room-header">
        <div class="room-header__main">
          <div class="room-header__title-row">
            <span class="room-header__eyebrow">INTERVIEW ROOM</span>
            <StatusTag v-if="detailStatus" :status="detailStatus" size="small" />
          </div>
          <h1 class="room-header__title">
            {{ store.detail.positionName || `岗位 #${store.detail.positionId}` }}
          </h1>
          <div class="room-header__tags">
            <el-tag effect="plain" size="small" round>{{ modeLabel }}</el-tag>
            <el-tag effect="plain" size="small" round>难度 {{ store.detail.difficulty }}</el-tag>
            <span class="room-header__round">
              第 {{ store.displayRound }} / {{ store.detail.plannedRounds }} 题
            </span>
          </div>
        </div>

        <div class="room-header__side">
          <TimerBadge
            v-if="!store.isTerminal"
            :remain-seconds="countdownRemain"
            :warning-threshold="60"
          />
          <WsStatusBadge
            v-if="!store.isTerminal"
            :state="wsState"
            :attempt="wsAttempt"
            @reconnect="reconnect"
          />
          <el-button text class="room-header__leave" data-testid="room-leave" @click="handleLeave">
            <el-icon><SwitchButton /></el-icon>
            退出
          </el-button>
        </div>
      </header>

      <div class="room-grid">
        <GlassPanel class="chat" :padded="false">
          <header class="chat__header">
            <div>
              <h2>面试对话</h2>
              <p>主问题与追问会实时推送；断线时自动切换轮询对账。</p>
            </div>
            <span class="chat__count">{{ store.messages.length }} 条</span>
          </header>

          <p v-if="replayError" class="chat__replay-error" role="alert">
            <el-icon><WarningFilled /></el-icon>
            {{ replayError }}
          </p>

          <div
            ref="chatList"
            class="chat__list"
            aria-live="polite"
            aria-relevant="additions"
            @scroll="updateNearBottom"
          >
            <div v-if="bubbles.length === 0" class="chat__empty">
              <div class="chat__empty-mark">AI</div>
              <p v-if="store.starting">正在开始面试并生成首题…</p>
              <p v-else>等待 AI 提问…</p>
            </div>

            <MessageBubble
              v-for="message in bubbles"
              :key="message.id"
              :message="message"
              :optimistic-state="message.id < 0 ? store.optimistic?.state : null"
              :replayable="isReplayable(message.id, message.audioPath)"
              :playing="playingRound === message.roundNo"
              :loading="loadingRound === message.roundNo"
              @replay="handleReplay(message.id, message.roundNo)"
            />

            <div v-if="store.aiThinking" class="typing" aria-label="AI 正在分析回答">
              <span class="typing__avatar" aria-hidden="true">AI</span>
              <div class="typing__body">
                <span class="typing__dots" aria-hidden="true">
                  <i />
                  <i />
                  <i />
                </span>
                <span class="typing__text">AI 正在分析你的回答…</span>
              </div>
            </div>

            <p v-if="aiSlow" class="chat__slow">
              <el-icon><WarningFilled /></el-icon>
              AI 响应较慢，可以继续等待或结束本场面试（已提交内容不会丢失）。
            </p>
          </div>

          <button
            v-if="hasNewMessages"
            type="button"
            class="chat__new"
            @click="scrollToBottom('smooth')"
          >
            有新消息
            <el-icon><ArrowDown /></el-icon>
          </button>
        </GlassPanel>

        <GlassPanel class="answer-panel" :padded="false">
          <div class="answer-panel__head">
            <RoundProgress
              :current="store.displayRound"
              :planned="store.detail.plannedRounds"
              :followup="store.followupCount"
              :finished="store.isTerminal"
            />
            <StatusTag v-if="detailStatus" :status="detailStatus" size="small" />
          </div>

          <div v-if="startFailed" class="state-panel state-panel--error" role="alert">
            <div class="state-panel__icon">
              <el-icon :size="24"><WarningFilled /></el-icon>
            </div>
            <h2>面试开始失败</h2>
            <p>{{ actionError || '开始面试时发生错误，请重试。' }}</p>
            <div class="state-panel__actions">
              <el-button type="primary" :loading="store.starting" @click="retryStart">
                <el-icon><Refresh /></el-icon>
                重试开始
              </el-button>
              <el-button :loading="store.aborting" @click="abortRoom">中止本场</el-button>
            </div>
          </div>

          <div v-else-if="store.isTerminal" class="state-panel">
            <div
              class="state-panel__icon"
              :class="{ 'state-panel__icon--danger': showRestartEntry }"
            >
              <el-icon :size="26">
                <CircleCheck v-if="showReportEntry" />
                <WarningFilled v-else />
              </el-icon>
            </div>
            <p class="state-panel__eyebrow">INTERVIEW TERMINAL</p>
            <h2>{{ terminalTitle }}</h2>
            <p class="state-panel__description">{{ terminalDescription }}</p>

            <div v-if="store.detail.totalScore != null" class="terminal-score">
              <span>本场总分</span>
              <strong :class="`score score--${scoreTone(store.detail.totalScore)}`">
                {{ formatScore(store.detail.totalScore) }}
              </strong>
            </div>

            <p v-if="store.detail.finishedAt" class="state-panel__meta">
              结束时间 {{ formatDateTime(store.detail.finishedAt) }}
            </p>

            <div class="state-panel__actions">
              <el-button v-if="showReportEntry" type="primary" @click="goReport">
                查看报告
              </el-button>
              <el-button v-if="showRestartEntry" type="primary" @click="startAgain">
                重新开始
              </el-button>
              <el-button type="primary" @click="goHistory">返回面试历史</el-button>
            </div>
          </div>

          <div v-else class="answer-panel__body">
            <p v-if="wsState !== 'open'" class="sync-notice">
              实时连接不可用，正在用轮询同步状态；你仍然可以正常{{
                isAudioMode ? '录音并提交语音' : '提交文字'
              }}回答。
            </p>

            <template v-if="isAudioMode">
              <div v-if="!audioReady" class="mode-notice" data-testid="audio-unsupported-room">
                <p>
                  <el-icon><WarningFilled /></el-icon>
                  当前浏览器不支持录音（{{ capabilities.audioDisabledReason }}）。语音面试需要
                  MediaRecorder 与麦克风权限，请改用 Chrome / Edge，或新建一场文字面试。
                </p>
                <el-button size="small" type="primary" plain @click="switchToTextSetup">
                  去设置页新建文字面试
                </el-button>
              </div>
              <AudioAnswerPanel
                v-else
                ref="audioPanelRef"
                :disabled="!canAnswer"
                :submitting="store.submitting"
                @submit="handleAudioSubmit"
              />
            </template>

            <AnswerComposer
              v-else
              ref="composerRef"
              v-model="answerDraft"
              :disabled="!canAnswer"
              :disabled-hint="disabledHint"
              :submitting="store.submitting"
              @submit="handleAnswerSubmit"
            />

            <p v-if="submitError" class="submit-error" role="alert">
              <el-icon><WarningFilled /></el-icon>
              {{ submitError }}
            </p>
            <p v-if="actionError" class="submit-error" role="alert">
              <el-icon><WarningFilled /></el-icon>
              {{ actionError }}
            </p>

            <div class="answer-panel__actions">
              <el-button
                type="danger"
                plain
                :disabled="store.isBusy"
                data-testid="finish-open"
                @click="openFinish"
              >
                结束面试
              </el-button>
              <el-button
                text
                type="danger"
                :disabled="store.isBusy"
                data-testid="abort-open"
                @click="openAbort"
              >
                中止面试
              </el-button>
            </div>

            <p class="answer-panel__hint">
              结束后生成报告；中止不生成报告。离开页面不会自动中止，服务端会继续计时。
            </p>
          </div>
        </GlassPanel>
      </div>

      <FinishConfirmDialog
        v-model="finishDialogOpen"
        mode="finish"
        :loading="store.finishing"
        :answered-rounds="store.answeredRounds"
        :current-round="store.displayRound"
        :planned-rounds="store.detail.plannedRounds"
        :remain-seconds="countdownRemain"
        @confirm="confirmFinish"
      />
      <FinishConfirmDialog
        v-model="abortDialogOpen"
        mode="abort"
        :loading="store.aborting"
        :answered-rounds="store.answeredRounds"
        :current-round="store.displayRound"
        :planned-rounds="store.detail.plannedRounds"
        :remain-seconds="countdownRemain"
        @confirm="confirmAbort"
      />
    </template>
  </div>
</template>

<style scoped>
.room {
  display: flex;
  flex-direction: column;
  /* 内容区独立滚动后，用 AppShell 提供的可视高度；外层兜底兼容独立挂载。 */
  height: var(--content-viewport-height, calc(100vh - var(--topbar-height) - 48px));
  min-height: 600px;
  gap: var(--space-3);
}

.room--invalid {
  height: auto;
  min-height: 50vh;
  justify-content: center;
}

.room-loading {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  color: var(--color-text-secondary);
}

.room-loading__bar {
  width: min(420px, 70%);
  height: 10px;
  overflow: hidden;
  border-radius: var(--radius-pill);
  background: rgba(148, 163, 184, 0.2);
}

.room-loading__bar::after {
  display: block;
  width: 40%;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2563eb, #38bdf8);
  content: '';
  animation: room-loading 1.2s ease-in-out infinite;
}

@keyframes room-loading {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(300%);
  }
}

/* ===== 顶栏 ===== */
.room-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: 14px var(--space-5);
  border: 1px solid var(--glass-border);
  border-radius: var(--glass-radius);
  background: var(--glass-bg-strong);
  box-shadow: var(--glass-shadow);
  backdrop-filter: blur(var(--glass-blur-strong)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur-strong)) saturate(var(--glass-saturate));
}

.room-header__main {
  min-width: 0;
}

.room-header__title-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.room-header__eyebrow {
  color: var(--color-primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.room-header__title {
  overflow: hidden;
  margin-top: 2px;
  font-size: var(--font-size-lg);
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room-header__tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.room-header__round {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.room-header__side {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
}

.room-header__leave {
  flex: none;
}

/* ===== 两栏 ===== */
.room-grid {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 1.45fr) minmax(340px, 0.85fr);
  gap: var(--space-3);
  min-height: 0;
}

.chat {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.chat__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5) var(--space-3);
  border-bottom: 1px solid rgba(255, 255, 255, 0.46);
}

.chat__header h2 {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.chat__header p {
  margin-top: 2px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.chat__count {
  flex: none;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.chat__replay-error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: var(--space-2) var(--space-5);
  border-bottom: 1px solid rgba(220, 38, 38, 0.18);
  background: rgba(254, 242, 242, 0.68);
  color: var(--color-danger);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.chat__list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--space-4);
  min-height: 0;
  padding: var(--space-4) var(--space-5);
  overflow-y: auto;
  scroll-behavior: smooth;
}

.chat__empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  min-height: 200px;
  color: var(--color-text-secondary);
}

.chat__empty-mark {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 18px;
  background: rgba(37, 99, 235, 0.12);
  color: var(--color-primary);
  font-size: var(--font-size-lg);
  font-weight: 800;
}

.chat__new {
  align-self: center;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: var(--space-3);
  padding: 6px 14px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-pill);
  background: var(--glass-bg-strong);
  box-shadow: var(--glass-shadow);
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: 600;
  cursor: pointer;
}

.chat__slow {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: var(--space-3);
  border: 1px solid rgba(217, 119, 6, 0.3);
  border-radius: var(--radius-md);
  background: rgba(255, 247, 237, 0.72);
  color: var(--color-warning);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

/* AI 思考中 */
.typing {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}

.typing__avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 12px;
  background: rgba(37, 99, 235, 0.14);
  color: var(--color-primary);
  font-size: 11px;
  font-weight: 800;
}

.typing__body {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 10px 14px;
  border: 1px solid var(--glass-border);
  border-radius: 16px 16px 16px 6px;
  background: var(--glass-bg);
  box-shadow: var(--glass-shadow);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.typing__dots {
  display: inline-flex;
  gap: 3px;
}

.typing__dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--color-primary);
  animation: typing-bounce 1.2s ease-in-out infinite;
}

.typing__dots i:nth-child(2) {
  animation-delay: 0.15s;
}

.typing__dots i:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes typing-bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

/* ===== 作答面板 ===== */
.answer-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow-y: auto;
}

.answer-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-5) var(--space-5) var(--space-3);
  border-bottom: 1px solid rgba(255, 255, 255, 0.46);
}

.answer-panel__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5) var(--space-5);
}

.answer-panel__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.answer-panel__hint {
  color: var(--color-text-tertiary);
  font-size: 11px;
  line-height: 1.6;
}

.mode-notice,
.sync-notice {
  padding: var(--space-3);
  border: 1px solid rgba(217, 119, 6, 0.28);
  border-radius: var(--radius-md);
  background: rgba(255, 247, 237, 0.68);
  color: var(--color-warning);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.mode-notice {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.mode-notice p {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.mode-notice :deep(.el-button) {
  align-self: flex-start;
}

.sync-notice {
  border-color: rgba(37, 99, 235, 0.2);
  background: rgba(239, 246, 255, 0.7);
  color: var(--color-primary);
}

.submit-error {
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

/* 开始失败 / 终态 */
.state-panel {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-6) var(--space-5);
}

.state-panel__icon {
  display: grid;
  place-items: center;
  width: 50px;
  height: 50px;
  margin-bottom: var(--space-2);
  border: 1px solid rgba(22, 163, 74, 0.26);
  border-radius: 16px;
  background: rgba(240, 253, 244, 0.82);
  color: var(--color-success);
}

.state-panel__icon--danger {
  border-color: rgba(220, 38, 38, 0.26);
  background: rgba(254, 242, 242, 0.86);
  color: var(--color-danger);
}

.state-panel__eyebrow {
  color: var(--color-text-tertiary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.state-panel h2 {
  font-size: var(--font-size-lg);
  font-weight: 700;
  line-height: 1.4;
}

.state-panel__description,
.state-panel p {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.state-panel--error .state-panel__icon {
  border-color: rgba(220, 38, 38, 0.26);
  background: rgba(254, 242, 242, 0.86);
  color: var(--color-danger);
}

.state-panel__meta {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.state-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.terminal-score {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  margin-top: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px solid rgba(255, 255, 255, 0.52);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.34);
}

.terminal-score span {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.terminal-score strong {
  font-size: var(--font-size-2xl);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1100px) {
  .room-grid {
    grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.9fr);
  }
}

@media (max-width: 900px) {
  .room {
    height: auto;
    min-height: 0;
  }

  .room-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .room-header__side {
    justify-content: flex-start;
  }

  .room-grid {
    grid-template-columns: 1fr;
  }

  .chat {
    min-height: 46vh;
  }

  .chat__list {
    max-height: 52vh;
  }

  .answer-panel {
    overflow: visible;
  }
}

@media (max-width: 640px) {
  .room-header {
    padding: var(--space-4);
  }

  .chat__header,
  .answer-panel__head {
    padding-left: var(--space-4);
    padding-right: var(--space-4);
  }

  .chat__list,
  .answer-panel__body {
    padding-left: var(--space-4);
    padding-right: var(--space-4);
  }

  .answer-panel__actions {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (prefers-reduced-motion: reduce) {
  .typing__dots i,
  .room-loading__bar::after {
    animation: none;
  }
}
</style>
