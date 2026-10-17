<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowRight,
  MagicStick,
  Printer,
  Refresh,
  TrendCharts,
  WarningFilled,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import { getRecommendations } from '@/api/growth'
import { errorMessage, isBizError } from '@/api/http'
import { getInterview, getMessages } from '@/api/interview'
import { getPositionSkills } from '@/api/position'
import RadarScoreChart from '@/components/charts/RadarScoreChart.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import DimensionBars from '@/components/report/DimensionBars.vue'
import EvidenceAccordion from '@/components/report/EvidenceAccordion.vue'
import ScoreRing from '@/components/report/ScoreRing.vue'
import StatusTag from '@/components/interview/StatusTag.vue'
import { useAudioReplay } from '@/composables/useAudioReplay'
import { useWebSocket } from '@/composables/useWebSocket'
import { useAuthStore } from '@/stores/auth'
import { useReportStore } from '@/stores/report'
import type { RecommendationVO } from '@/types/growth'
import type { InterviewDetailVO, MessageVO } from '@/types/interview'
import { notifyAuthExpired } from '@/utils/authSession'
import { INTERVIEW_MODE_LABELS } from '@/utils/constants'
import { formatDateTime } from '@/utils/format'
import { isTerminalStatus } from '@/utils/round'
import { formatPriority, formatReportDuration, reportDurationSeconds } from '@/utils/report'
import { buildWsUrl } from '@/utils/wsUrl'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const store = useReportStore()

const sessionId = computed(() => {
  const parsed = Number(route.params.id)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 0
})

const detail = ref<InterviewDetailVO | null>(null)
const detailLoading = ref(true)
const detailError = ref<unknown>(null)
const recommendations = ref<RecommendationVO[]>([])
const recommendationsLoading = ref(false)
const recommendationsError = ref('')
const skillNames = ref<Record<string, string>>({})
const messages = ref<MessageVO[]>([])
const wsError = ref('')
const waitingSeconds = ref(0)
const auxLoaded = ref(false)

let waitingTimer: number | null = null

const report = computed(() => store.report)
const status = computed(() => report.value?.status ?? detail.value?.status ?? null)
const mode = computed(() => report.value?.mode ?? detail.value?.mode ?? null)
const modeLabel = computed(() => (mode.value ? INTERVIEW_MODE_LABELS[mode.value] : '面试'))
const positionName = computed(
  () =>
    report.value?.positionName ||
    detail.value?.positionName ||
    (detail.value ? `岗位 #${detail.value.positionId}` : '面试报告'),
)
const isAborted = computed(() => detail.value?.status === 'ABORTED')
const isActiveSession = computed(
  () => detail.value != null && !isTerminalStatus(detail.value.status),
)
const detailErrorCode = computed(() =>
  isBizError(detailError.value) ? detailError.value.code : null,
)
const isNotFound = computed(() => detailErrorCode.value === 40401 || store.errorCode === 40401)
const detailErrorText = computed(() =>
  errorMessage(detailError.value, '面试报告加载失败，请稍后重试'),
)
const reportErrorText = computed(() => errorMessage(store.error, '报告加载失败，请稍后重试'))
const errorTrace = computed(() => {
  if (isBizError(detailError.value)) return detailError.value.traceId ?? ''
  if (isBizError(store.error)) return store.error.traceId ?? ''
  return ''
})
const errorCode = computed(() => detailErrorCode.value ?? store.errorCode)

const showPending = computed(
  () =>
    !report.value &&
    !isAborted.value &&
    !isActiveSession.value &&
    detail.value != null &&
    store.error == null,
)
const canRetry = computed(() => store.timedOut || Boolean(wsError.value))
const waitingText = computed(() => {
  const seconds = waitingSeconds.value
  if (seconds <= 3) return '报告通常会在 1~3 秒内生成，请稍候。'
  return `已等待 ${seconds} 秒；超过 60 秒仍未就绪时可重新生成。`
})

const score = computed(() => report.value?.totalScore ?? null)
const dimensions = computed(() => report.value?.dimensionScores ?? null)
const evidence = computed(() => report.value?.questionEvidence ?? [])
const durationSeconds = computed(() => reportDurationSeconds(detail.value))
const durationText = computed(() => formatReportDuration(durationSeconds.value))
const createdAt = computed(() =>
  formatDateTime(detail.value?.finishedAt ?? report.value?.createdAt),
)
const roundCount = computed(() => evidence.value.length)
const summary = computed(() => report.value?.summary || '暂无总评。')
const strengths = computed(() => report.value?.strengths ?? [])
const weaknesses = computed(() => report.value?.weaknesses ?? [])
const improvementPlan = computed(() => report.value?.improvementPlan ?? [])

const audioByRound = computed<Record<number, number>>(() => {
  const map: Record<number, number> = {}
  for (const message of messages.value) {
    if (message.speaker === 'USER' && message.audioPath) {
      map[message.roundNo] = message.id
    }
  }
  return map
})
const replayableRounds = computed(() => Object.keys(audioByRound.value).map(Number))

const {
  playingRound,
  loadingRound,
  error: replayError,
  replay: replayAudio,
  stop: stopAudio,
} = useAudioReplay(sessionId)

const ws = useWebSocket({
  url: () =>
    sessionId.value > 0 && showPending.value ? buildWsUrl(sessionId.value, auth.token) : '',
  maxAttempts: 3,
  onEvent: (event) => {
    if (event.type === 'REPORT_READY') {
      wsError.value = ''
      void store.refreshNow(sessionId.value)
      return
    }
    if (event.type === 'ERROR') {
      const payload = event.payload as { message?: string }
      if (payload.message) wsError.value = payload.message
    }
  },
  onUnauthorized: () => notifyAuthExpired(),
})
const wsState = ws.state

function firstResource(recommendation: RecommendationVO) {
  return recommendation.resources?.[0] ?? recommendation.practices?.[0] ?? null
}

function startWaitingTimer(): void {
  stopWaitingTimer()
  waitingSeconds.value = 0
  waitingTimer = window.setInterval(() => {
    waitingSeconds.value += 1
  }, 1000)
}

function stopWaitingTimer(): void {
  if (waitingTimer != null) {
    window.clearInterval(waitingTimer)
    waitingTimer = null
  }
}

async function loadAuxiliary(): Promise<void> {
  if (!detail.value || auxLoaded.value) return
  auxLoaded.value = true

  const positionId = detail.value.positionId
  recommendationsLoading.value = true
  recommendationsError.value = ''
  void getRecommendations(positionId)
    .then((list) => {
      recommendations.value = (list ?? []).slice(0, 2)
    })
    .catch((cause) => {
      recommendations.value = []
      recommendationsError.value = errorMessage(cause, '学习推荐暂时不可用')
    })
    .finally(() => {
      recommendationsLoading.value = false
    })

  void getPositionSkills(positionId)
    .then((skills) => {
      const map: Record<string, string> = {}
      for (const skill of skills) map[skill.skillCode] = skill.skillName
      skillNames.value = map
    })
    .catch(() => {
      skillNames.value = {}
    })

  if (detail.value.mode === 'AUDIO') {
    try {
      messages.value = await getMessages(sessionId.value)
    } catch {
      messages.value = []
    }
  }
}

async function loadReport(force = false): Promise<void> {
  if (!sessionId.value) return
  await store.load(sessionId.value, { force })
  if (store.report) await loadAuxiliary()
}

async function initialize(): Promise<void> {
  if (!sessionId.value) {
    detailLoading.value = false
    return
  }

  ws.close()
  store.stopPolling()
  auxLoaded.value = false
  recommendations.value = []
  recommendationsError.value = ''
  skillNames.value = {}
  messages.value = []
  wsError.value = ''
  stopWaitingTimer()
  waitingSeconds.value = 0
  detailLoading.value = true
  detailError.value = null
  detail.value = null

  try {
    detail.value = await getInterview(sessionId.value)
  } catch (cause) {
    detailError.value = cause
    detailLoading.value = false
    return
  }
  detailLoading.value = false

  if (isAborted.value || isActiveSession.value) {
    store.stopPolling()
    return
  }
  await loadReport()
}

async function refreshReport(): Promise<void> {
  if (!sessionId.value) return
  if (isActiveSession.value || isAborted.value) {
    await initialize()
    return
  }
  auxLoaded.value = false
  await loadReport(true)
}

async function retryGenerate(): Promise<void> {
  if (!sessionId.value) return
  wsError.value = ''
  const accepted = await store.retry(sessionId.value)
  if (accepted) {
    waitingSeconds.value = 0
    ElMessage.success('已重新触发报告生成，正在等待结果')
    return
  }
  ElMessage.error(errorMessage(store.error, '重新生成报告失败'))
}

function printReport(): void {
  window.print()
}

function goHistory(): void {
  void router.push('/history')
}

function goRoom(): void {
  void router.push({ name: 'interview-room', params: { id: String(sessionId.value) } })
}

function restart(): void {
  const positionId = detail.value?.positionId
  void router.push({
    name: 'interview-setup',
    query: positionId != null ? { positionId: String(positionId) } : undefined,
  })
}

function goGrowth(): void {
  const positionId = detail.value?.positionId
  void router.push({
    name: 'growth',
    query: positionId != null ? { positionId: String(positionId) } : undefined,
  })
}

function goRecommendations(): void {
  const positionId = detail.value?.positionId
  void router.push({
    name: 'recommendations',
    query: positionId != null ? { positionId: String(positionId) } : undefined,
  })
}

function handleReplay(roundNo: number): void {
  const messageId = audioByRound.value[roundNo]
  if (messageId == null) return
  void replayAudio(roundNo, messageId)
}

function handleVisibility(): void {
  if (document.visibilityState === 'visible') store.resumePolling()
}

watch(
  sessionId,
  (id) => {
    if (id > 0) void initialize()
  },
  { immediate: true },
)

watch(showPending, (pending) => {
  if (pending) startWaitingTimer()
  else stopWaitingTimer()
})

watch(
  showPending,
  (pending) => {
    if (pending) ws.connect()
    else ws.close()
  },
  { immediate: true },
)

onMounted(() => {
  document.addEventListener('visibilitychange', handleVisibility)
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', handleVisibility)
  stopWaitingTimer()
  ws.close()
  stopAudio()
  store.stopPolling()
})
</script>

<template>
  <div class="report-page" data-testid="report-page">
    <AppPageHeader
      title="面试报告"
      :description="
        report
          ? `${positionName} · ${modeLabel} · 每个分数都可下钻到维度与逐题证据`
          : '查看本场面试的总分、维度、逐题证据与下一步建议'
      "
      back-to="/history"
    >
      <template #actions>
        <el-button
          v-if="report"
          class="no-print"
          :icon="Refresh"
          :loading="store.loading"
          @click="refreshReport"
        >
          刷新
        </el-button>
        <el-button
          v-if="report"
          type="primary"
          class="no-print"
          :icon="Printer"
          data-testid="report-print"
          @click="printReport"
        >
          打印 / 导出 PDF
        </el-button>
      </template>
    </AppPageHeader>

    <div v-if="!sessionId" class="report-state">
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

    <div v-else-if="detailLoading" class="report-state">
      <LoadingSkeleton type="card" :rows="3" />
      <LoadingSkeleton type="chart" />
    </div>

    <div v-else-if="detailError" class="report-state">
      <ErrorState
        :title="isNotFound ? '面试记录不存在或无权访问' : '面试报告加载失败'"
        :message="detailErrorText"
        :code="errorCode ?? undefined"
        :trace-id="errorTrace"
        @retry="initialize"
      >
        <template #actions>
          <el-button type="primary" @click="goHistory">返回面试历史</el-button>
        </template>
      </ErrorState>
    </div>

    <div v-else-if="isAborted" class="report-state">
      <GlassPanel :padded="false" data-testid="report-aborted">
        <EmptyState
          title="该面试已中止，无报告"
          description="中止的面试不会生成评分报告；可以重新开始一场同岗位面试，或先回历史查看其他记录。"
        >
          <template #actions>
            <el-button type="primary" @click="restart">重新开始</el-button>
            <el-button @click="goHistory">返回面试历史</el-button>
          </template>
        </EmptyState>
      </GlassPanel>
    </div>

    <div v-else-if="isActiveSession" class="report-state">
      <GlassPanel :padded="false" data-testid="report-active">
        <EmptyState
          title="本场面试尚未结束"
          description="面试结束后系统才会生成报告；回到房间继续作答或结束本场面试。"
        >
          <template #actions>
            <el-button type="primary" @click="goRoom">回到面试房间</el-button>
            <el-button @click="goHistory">返回面试历史</el-button>
          </template>
        </EmptyState>
      </GlassPanel>
    </div>

    <div v-else-if="showPending" class="report-state" data-testid="report-pending">
      <GlassPanel tone="strong" class="pending">
        <div class="pending__head">
          <span class="pending__icon" aria-hidden="true">
            <el-icon :size="22"><MagicStick /></el-icon>
          </span>
          <div>
            <p class="pending__eyebrow">AI ASSESSMENT</p>
            <h2>{{ store.timedOut ? '报告生成较慢或失败' : '报告生成中' }}</h2>
            <p class="pending__desc">
              {{
                store.timedOut
                  ? '已超过 60 秒仍未就绪。可重新生成报告（调用幂等的 finish 重试入口），或稍后刷新。'
                  : waitingText
              }}
            </p>
          </div>
        </div>

        <div class="pending__progress" aria-hidden="true">
          <span :style="{ width: `${Math.min(100, Math.max(8, waitingSeconds * 2))}%` }" />
        </div>

        <div class="pending__meta">
          <span>第 {{ store.pollAttempt }} 次检查</span>
          <span>{{ wsState === 'open' ? '实时通知已连接' : '轮询同步中' }}</span>
          <span>10004 = 正常生成中，不是错误</span>
        </div>

        <p v-if="wsError" class="pending__ws-error">
          <el-icon><WarningFilled /></el-icon>
          {{ wsError }}
        </p>

        <div class="pending__actions">
          <el-button
            v-if="canRetry"
            type="primary"
            :loading="store.retrying"
            data-testid="report-retry"
            @click="retryGenerate"
          >
            重新生成报告
          </el-button>
          <el-button :icon="Refresh" :loading="store.loading" @click="refreshReport">
            立即刷新
          </el-button>
          <el-button text @click="goHistory">返回面试历史</el-button>
        </div>

        <div class="pending__skeleton">
          <LoadingSkeleton type="chart" />
        </div>
      </GlassPanel>
    </div>

    <div v-else-if="store.error" class="report-state">
      <ErrorState
        :title="isNotFound ? '面试报告不存在或无权访问' : '面试报告加载失败'"
        :message="reportErrorText"
        :code="errorCode ?? undefined"
        :trace-id="errorTrace"
        @retry="refreshReport"
      >
        <template #actions>
          <el-button
            v-if="!isNotFound"
            type="primary"
            :loading="store.retrying"
            @click="retryGenerate"
          >
            重新生成报告
          </el-button>
          <el-button @click="goHistory">返回面试历史</el-button>
        </template>
      </ErrorState>
    </div>

    <template v-else-if="report">
      <GlassPanel tone="strong" class="report-hero" :padded="false" data-testid="report-hero">
        <div class="report-hero__main">
          <div class="report-hero__meta">
            <p class="report-hero__eyebrow">INTERVIEW REPORT · {{ modeLabel }}</p>
            <h2 class="report-hero__title">{{ positionName }}</h2>
            <div class="report-hero__tags">
              <StatusTag v-if="status" :status="status" size="small" />
              <el-tag v-if="detail?.difficulty" size="small" effect="plain" round>
                难度 {{ detail.difficulty }}
              </el-tag>
              <el-tag size="small" effect="plain" round> {{ roundCount }} 题证据 </el-tag>
              <span v-if="detail?.plannedRounds" class="report-hero__fact">
                计划 {{ detail.plannedRounds }} 轮
              </span>
              <span class="report-hero__fact">用时 {{ durationText }}</span>
              <span class="report-hero__fact">{{ createdAt }}</span>
            </div>
            <p class="report-hero__summary">{{ summary }}</p>
          </div>

          <div class="report-hero__score" data-testid="report-score">
            <ScoreRing :score="score" :size="180" label="总分" />
            <p class="report-hero__score-hint">满分 100</p>
          </div>
        </div>
      </GlassPanel>

      <div class="report-grid">
        <GlassPanel class="report-panel" :padded="false">
          <header class="panel-head">
            <div>
              <h2>能力雷达</h2>
              <p>
                {{
                  mode === 'AUDIO'
                    ? '按报告实际维度展示：语音面试 6 维（含语音表现）。'
                    : '按报告实际维度展示；文本面试不补 voice 维度。'
                }}
              </p>
            </div>
          </header>
          <div class="report-panel__body">
            <RadarScoreChart :scores="dimensions" :mode="mode" />
          </div>
        </GlassPanel>

        <GlassPanel class="report-panel" :padded="false">
          <header class="panel-head">
            <div>
              <h2>维度得分</h2>
              <p>固定顺序便于跨场对比：&lt;60 红、60~74 橙、75~84 蓝、≥85 绿。</p>
            </div>
          </header>
          <div class="report-panel__body">
            <DimensionBars :scores="dimensions" :mode="mode" />
          </div>
        </GlassPanel>
      </div>

      <div class="report-feedback">
        <GlassPanel
          class="report-panel feedback-card"
          :padded="false"
          data-testid="report-strengths"
        >
          <header class="panel-head">
            <div>
              <h2>亮点</h2>
              <p>本场表现较好的维度与依据。</p>
            </div>
            <span class="panel-count">{{ strengths.length }}</span>
          </header>
          <ul v-if="strengths.length > 0" class="feedback-list feedback-list--success">
            <li v-for="item in strengths" :key="item">{{ item }}</li>
          </ul>
          <p v-else class="panel-empty">暂无亮点记录。</p>
        </GlassPanel>

        <GlassPanel
          class="report-panel feedback-card"
          :padded="false"
          data-testid="report-weaknesses"
        >
          <header class="panel-head">
            <div>
              <h2>不足</h2>
              <p>优先补齐的短板维度。</p>
            </div>
            <span class="panel-count">{{ weaknesses.length }}</span>
          </header>
          <ul v-if="weaknesses.length > 0" class="feedback-list feedback-list--danger">
            <li v-for="item in weaknesses" :key="item">{{ item }}</li>
          </ul>
          <p v-else class="panel-empty">暂无不足记录。</p>
        </GlassPanel>

        <GlassPanel class="report-panel feedback-card" :padded="false" data-testid="report-plan">
          <header class="panel-head">
            <div>
              <h2>改进建议</h2>
              <p>可以直接执行的下一步练习。</p>
            </div>
            <span class="panel-count">{{ improvementPlan.length }}</span>
          </header>
          <ul v-if="improvementPlan.length > 0" class="feedback-list feedback-list--primary">
            <li v-for="item in improvementPlan" :key="item">{{ item }}</li>
          </ul>
          <p v-else class="panel-empty">暂无改进建议。</p>
        </GlassPanel>
      </div>

      <GlassPanel class="report-panel" :padded="false" data-testid="report-recommendations">
        <header class="panel-head">
          <div>
            <h2>下一步：学习推荐</h2>
            <p>报告只做 Top2 预览；完整资源、练习与错题重练在推荐页。</p>
          </div>
          <el-button text type="primary" class="no-print" @click="goRecommendations">
            查看完整推荐
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </header>
        <div class="report-panel__body">
          <p v-if="recommendationsLoading" class="panel-empty">正在匹配学习资源…</p>
          <p v-else-if="recommendationsError" class="panel-empty panel-empty--warning">
            {{ recommendationsError }}。这不影响报告内容，可稍后在推荐页重试。
          </p>
          <div v-else-if="recommendations.length === 0" class="panel-empty">
            暂无个性化推荐，先完成一场面试后再来看看。
          </div>
          <div v-else class="recommendation-list">
            <div
              v-for="recommendation in recommendations"
              :key="recommendation.skillCode"
              class="recommendation"
              data-testid="report-recommendation"
            >
              <div class="recommendation__head">
                <strong>{{ recommendation.skillName }}</strong>
                <el-tag size="small" effect="plain" round>
                  优先级 {{ formatPriority(recommendation.priority) }}
                </el-tag>
              </div>
              <p class="recommendation__reason">
                {{ recommendation.weaknessReasons?.[0] || '建议优先补齐该技能的岗位要求。' }}
              </p>
              <template v-if="firstResource(recommendation)">
                <a
                  v-if="firstResource(recommendation)?.contentUrl"
                  class="recommendation__resource"
                  :href="firstResource(recommendation)?.contentUrl ?? '#'"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {{ firstResource(recommendation)?.title }}
                  <el-icon><ArrowRight /></el-icon>
                </a>
                <span v-else class="recommendation__resource">
                  {{ firstResource(recommendation)?.title }}
                </span>
              </template>
              <span v-else class="recommendation__resource recommendation__resource--muted">
                暂无匹配资源，可先做错题重练
              </span>
            </div>
          </div>
        </div>
      </GlassPanel>

      <GlassPanel class="report-panel" :padded="false" data-testid="report-evidence">
        <header class="panel-head">
          <div>
            <h2>逐题证据</h2>
            <p>按轮次升序；默认展开第 1 题，可逐题查看得分点、失分点、证据句与评分理由。</p>
          </div>
          <span class="panel-count">{{ evidence.length }} 题</span>
        </header>
        <div class="report-panel__body">
          <EvidenceAccordion
            :items="evidence"
            :mode="mode"
            :skill-names="skillNames"
            :replayable-rounds="replayableRounds"
            :playing-round="playingRound"
            :loading-round="loadingRound"
            :replay-error="replayError"
            @replay="handleReplay"
          />
        </div>
      </GlassPanel>

      <GlassPanel class="report-actions no-print" data-testid="report-actions">
        <div class="report-actions__copy">
          <h2>把这次结论变成下一场提升</h2>
          <p>重练同岗位会更聚焦短板技能；也可以先看能力成长曲线与完整推荐。</p>
        </div>
        <div class="report-actions__buttons">
          <el-button data-testid="report-action-growth" :icon="TrendCharts" @click="goGrowth">
            看成长
          </el-button>
          <el-button
            data-testid="report-action-recommendations"
            :icon="MagicStick"
            @click="goRecommendations"
          >
            看推荐
          </el-button>
          <el-button type="primary" data-testid="report-action-restart" @click="restart">
            重练同岗位
          </el-button>
        </div>
      </GlassPanel>
    </template>

    <div v-else class="report-state">
      <GlassPanel :padded="false">
        <EmptyState
          title="报告暂不可用"
          description="该面试没有可展示的报告。若刚刚结束，请稍后重试；若已中止，不会生成报告。"
          action-text="返回面试历史"
          @action="goHistory"
        />
      </GlassPanel>
    </div>
  </div>
</template>

<style scoped>
.report-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding-bottom: var(--space-6);
}

.report-state {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.report-hero {
  position: relative;
}

.report-hero__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-6);
  padding: var(--space-5);
}

.report-hero__meta {
  min-width: 0;
}

.report-hero__eyebrow {
  color: var(--color-primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.report-hero__title {
  margin-top: 2px;
  font-size: var(--font-size-2xl);
  font-weight: 800;
  line-height: 1.25;
}

.report-hero__tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.report-hero__fact {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.report-hero__summary {
  max-width: 720px;
  margin-top: var(--space-3);
  padding: var(--space-3);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.34);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
  white-space: pre-wrap;
}

.report-hero__score {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.report-hero__score-hint {
  color: var(--color-text-tertiary);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.report-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(0, 0.92fr);
  gap: var(--space-4);
}

.report-panel {
  min-width: 0;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5) var(--space-3);
  border-bottom: 1px solid rgba(255, 255, 255, 0.46);
}

.panel-head h2 {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.panel-head p {
  margin-top: 2px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.55;
}

.panel-count {
  flex: none;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.report-panel__body {
  padding: var(--space-4) var(--space-5) var(--space-5);
}

.panel-empty {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
  line-height: 1.65;
}

.panel-empty--warning {
  color: var(--color-warning);
}

.report-feedback {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
}

.feedback-card {
  display: flex;
  flex-direction: column;
}

.feedback-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0;
  padding: var(--space-4) var(--space-5) var(--space-5) 34px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.65;
}

.feedback-list li::marker {
  font-weight: 700;
}

.feedback-list--success li::marker {
  color: var(--color-success);
}

.feedback-list--danger li::marker {
  color: var(--color-danger);
}

.feedback-list--primary li::marker {
  color: var(--color-primary);
}

.feedback-card .panel-empty {
  padding: var(--space-4) var(--space-5) var(--space-5);
}

.recommendation-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.recommendation {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3);
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.3);
}

.recommendation__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.recommendation__reason {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.65;
}

.recommendation__resource {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.recommendation__resource--muted {
  color: var(--color-text-tertiary);
  font-weight: 400;
}

.report-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.report-actions__copy h2 {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.report-actions__copy p {
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.report-actions__buttons {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

/* 报告生成中 */
.pending {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-5);
}

.pending__head {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}

.pending__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 48px;
  height: 48px;
  border: 1px solid rgba(37, 99, 235, 0.2);
  border-radius: 14px;
  background: rgba(37, 99, 235, 0.1);
  color: var(--color-primary);
}

.pending__eyebrow {
  color: var(--color-primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.pending h2 {
  margin-top: 1px;
  font-size: var(--font-size-lg);
  font-weight: 700;
}

.pending__desc {
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.65;
}

.pending__progress {
  overflow: hidden;
  height: 8px;
  border-radius: var(--radius-pill);
  background: rgba(148, 163, 184, 0.2);
}

.pending__progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2563eb, #38bdf8);
  transition: width 600ms ease;
}

.pending__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.pending__ws-error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  color: var(--color-warning);
  font-size: var(--font-size-xs);
}

.pending__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.pending__skeleton {
  margin-top: var(--space-2);
  border: 1px dashed rgba(148, 163, 184, 0.34);
  border-radius: var(--radius-md);
}

@media (max-width: 1100px) {
  .report-grid {
    grid-template-columns: 1fr;
  }

  .report-feedback {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .report-hero__main {
    flex-direction: column;
    align-items: flex-start;
    padding: var(--space-4);
  }

  .report-hero__score {
    align-self: center;
  }

  .recommendation-list {
    grid-template-columns: 1fr;
  }

  .panel-head,
  .report-panel__body {
    padding-left: var(--space-4);
    padding-right: var(--space-4);
  }

  .report-actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .report-actions__buttons {
    width: 100%;
  }

  .report-actions__buttons :deep(.el-button) {
    flex: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .pending__progress span {
    transition: none;
  }
}

@media print {
  .report-page {
    gap: 12px;
    padding-bottom: 0;
  }

  .report-hero__main {
    padding: 12px;
  }

  .report-grid,
  .report-feedback {
    grid-template-columns: 1fr 1fr;
  }

  .report-panel,
  .report-hero,
  .feedback-card,
  .report-actions {
    break-inside: avoid;
  }

  .panel-head,
  .report-panel__body,
  .feedback-list {
    padding: 8px 12px;
  }

  .report-hero__summary,
  .recommendation,
  .evidence__collapse :deep(.el-collapse-item) {
    background: #fff;
  }
}
</style>
