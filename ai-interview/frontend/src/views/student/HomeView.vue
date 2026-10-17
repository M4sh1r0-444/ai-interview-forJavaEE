<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  Briefcase,
  Clock,
  MagicStick,
  Refresh,
  TrendCharts,
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'

import { errorMessage } from '@/api/http'
import { getHistory, getRecommendations } from '@/api/growth'
import { listPositions } from '@/api/position'
import GlassPanel from '@/components/common/GlassPanel.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import StatusTag from '@/components/interview/StatusTag.vue'
import { useAuthStore } from '@/stores/auth'
import type { HistoryItemVO, RecommendationVO } from '@/types/growth'
import type { PositionVO } from '@/types/position'
import { INTERVIEW_MODE_LABELS } from '@/utils/constants'
import { formatDateTime, formatNumber, formatScore, truncate } from '@/utils/format'
import { scoreTone } from '@/utils/score'

const router = useRouter()
const auth = useAuthStore()

const positions = ref<PositionVO[]>([])
const history = ref<HistoryItemVO[]>([])
const historyTotal = ref(0)
const recommendations = ref<RecommendationVO[]>([])
const loading = ref(true)
const recommendationsLoading = ref(false)
const positionsError = ref('')
const historyError = ref('')
const recommendationsError = ref('')

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '上午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const todayLabel = computed(() => dayjs().format('YYYY 年 M 月 D 日'))

const lastInterview = computed(() => history.value[0] ?? null)
const latestScore = computed(() => {
  const scored = history.value.find(
    (item) => item.status === 'FINISHED' || item.status === 'TIMEOUT',
  )
  return scored?.totalScore ?? null
})
const topRecommendation = computed(() => recommendations.value[0] ?? null)
const topResource = computed(() => topRecommendation.value?.resources?.[0] ?? null)
const resultCount = computed(
  () =>
    history.value.filter((item) => item.status === 'FINISHED' || item.status === 'TIMEOUT').length,
)

const quickActions = computed(() => [
  {
    key: 'start',
    title: '开始一场面试',
    description: positions.value.length > 1 ? '选择岗位后配置本场面试' : '按岗位能力模型组卷',
    icon: Briefcase,
    tone: 'primary',
    action: startInterview,
  },
  {
    key: 'history',
    title: '面试历史',
    description: `${historyTotal.value} 场记录，可继续或复盘`,
    icon: Clock,
    tone: 'blue',
    action: () => router.push('/history'),
  },
  {
    key: 'growth',
    title: '能力成长',
    description: '查看技能掌握度与成长曲线',
    icon: TrendCharts,
    tone: 'cyan',
    action: () => router.push('/growth'),
  },
  {
    key: 'recommendations',
    title: '学习推荐',
    description: '短板 Top3、资源与错题重练',
    icon: MagicStick,
    tone: 'violet',
    action: () => router.push('/recommendations'),
  },
])

function startInterview(positionId?: number): void {
  if (positionId != null) {
    void router.push({ name: 'interview-setup', query: { positionId: String(positionId) } })
    return
  }
  if (positions.value.length === 1) {
    startInterview(positions.value[0].id)
    return
  }
  void router.push('/positions')
}

function isActive(item: HistoryItemVO): boolean {
  return item.status === 'WAITING_USER' || item.status === 'PROCESSING_AI'
}

function recentActionLabel(item: HistoryItemVO): string {
  if (isActive(item)) return '继续面试'
  if (item.status === 'FINISHED' || item.status === 'TIMEOUT') return '查看报告'
  if (item.status === 'ABORTED') return '重新开始'
  return '进入房间'
}

function handleRecentAction(item: HistoryItemVO): void {
  if (isActive(item) || item.status === 'CREATED') {
    void router.push({ name: 'interview-room', params: { id: String(item.sessionId) } })
    return
  }
  if (item.status === 'FINISHED' || item.status === 'TIMEOUT') {
    void router.push({ name: 'interview-report', params: { id: String(item.sessionId) } })
    return
  }
  startInterview(item.positionId)
}

function goRecommendations(): void {
  const positionId = positions.value[0]?.id
  void router.push({
    name: 'recommendations',
    query: positionId != null ? { positionId: String(positionId) } : undefined,
  })
}

async function loadRecommendations(positionId?: number): Promise<void> {
  if (positionId == null) {
    recommendations.value = []
    return
  }
  recommendationsLoading.value = true
  recommendationsError.value = ''
  try {
    recommendations.value = await getRecommendations(positionId)
  } catch (error) {
    recommendations.value = []
    recommendationsError.value = errorMessage(error, '学习推荐暂时不可用')
  } finally {
    recommendationsLoading.value = false
  }
}

async function load(): Promise<void> {
  loading.value = true
  positionsError.value = ''
  historyError.value = ''
  recommendationsError.value = ''

  const [positionResult, historyResult] = await Promise.allSettled([
    listPositions(),
    getHistory({ page: 1, size: 5 }),
  ])

  if (positionResult.status === 'fulfilled') {
    positions.value = positionResult.value
  } else {
    positions.value = []
    positionsError.value = errorMessage(positionResult.reason, '岗位加载失败')
  }

  if (historyResult.status === 'fulfilled') {
    history.value = historyResult.value.records ?? []
    historyTotal.value = historyResult.value.total ?? 0
  } else {
    history.value = []
    historyTotal.value = 0
    historyError.value = errorMessage(historyResult.reason, '最近面试加载失败')
  }

  loading.value = false
  await loadRecommendations(positions.value[0]?.id)
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="home">
    <GlassPanel tone="strong" class="hero" :padded="false">
      <div class="hero__glow" aria-hidden="true" />
      <div class="hero__main">
        <p class="hero__eyebrow">{{ todayLabel }} · STUDENT CONSOLE</p>
        <h1 class="hero__title">{{ greeting }}，{{ auth.displayName }}</h1>
        <p class="hero__subtitle">
          选择岗位、完成一场多轮模拟面试，让每一次作答都沉淀为可追踪的能力证据。
        </p>
        <div class="hero__actions">
          <el-button
            type="primary"
            size="large"
            class="hero__cta"
            data-testid="home-start"
            @click="startInterview()"
          >
            开始面试
            <el-icon><ArrowRight /></el-icon>
          </el-button>
          <el-button size="large" plain @click="router.push('/positions')">
            查看岗位能力模型
          </el-button>
        </div>
      </div>

      <div class="hero__stats" aria-label="我的面试概况">
        <div class="hero__stat">
          <span class="hero__stat-label">累计面试</span>
          <strong class="hero__stat-value">{{ formatNumber(historyTotal) }}</strong>
          <span class="hero__stat-unit">场</span>
        </div>
        <div class="hero__stat">
          <span class="hero__stat-label">已出分场次</span>
          <strong class="hero__stat-value">{{ resultCount }}</strong>
          <span class="hero__stat-unit">场</span>
        </div>
        <div class="hero__stat">
          <span class="hero__stat-label">最近总分</span>
          <strong
            class="hero__stat-value"
            :class="latestScore == null ? 'text-muted' : `score score--${scoreTone(latestScore)}`"
          >
            {{ latestScore == null ? '—' : formatScore(latestScore) }}
          </strong>
          <span class="hero__stat-unit">分</span>
        </div>
      </div>
    </GlassPanel>

    <section class="quick-grid" aria-label="快捷入口">
      <button
        v-for="action in quickActions"
        :key="action.key"
        type="button"
        class="quick-card"
        :class="`quick-card--${action.tone}`"
        @click="action.action()"
      >
        <span class="quick-card__icon" aria-hidden="true">
          <el-icon :size="20"><component :is="action.icon" /></el-icon>
        </span>
        <span class="quick-card__body">
          <strong>{{ action.title }}</strong>
          <span>{{ action.description }}</span>
        </span>
        <el-icon class="quick-card__arrow" :size="15"><ArrowRight /></el-icon>
      </button>
    </section>

    <LoadingSkeleton v-if="loading" type="card" :rows="4" />

    <div v-else class="home__grid">
      <GlassPanel class="panel" :padded="false">
        <header class="panel__header">
          <div>
            <h2 class="panel__title">最近面试</h2>
            <p class="panel__subtitle">从上次停下的地方继续，或回看已生成的报告。</p>
          </div>
          <el-button text :icon="Refresh" @click="load">刷新</el-button>
        </header>

        <div v-if="historyError" class="panel__error" role="alert">
          <span>{{ historyError }}</span>
          <el-button text type="primary" @click="load">重试</el-button>
        </div>

        <div v-else-if="lastInterview" class="recent">
          <div class="recent__main">
            <div class="recent__badges">
              <StatusTag :status="lastInterview.status" size="small" />
              <el-tag effect="plain" size="small" round>
                {{ INTERVIEW_MODE_LABELS[lastInterview.mode] }}
              </el-tag>
              <el-tag effect="plain" size="small" round>
                难度 {{ lastInterview.difficulty }}
              </el-tag>
            </div>
            <h3 class="recent__position">
              {{ lastInterview.positionName || `岗位 #${lastInterview.positionId}` }}
            </h3>
            <p class="recent__meta">
              开始于 {{ formatDateTime(lastInterview.startedAt) }} · 计划
              {{ lastInterview.plannedRounds }} 轮 ·
              {{
                lastInterview.status === 'FINISHED' || lastInterview.status === 'TIMEOUT'
                  ? `已完成 ${lastInterview.plannedRounds} 轮`
                  : `当前 ${Math.min(lastInterview.currentRound + 1, lastInterview.plannedRounds)} / ${lastInterview.plannedRounds}`
              }}
            </p>
          </div>
          <div class="recent__score">
            <span>总分</span>
            <strong
              :class="
                lastInterview.totalScore == null
                  ? 'text-muted'
                  : `score score--${scoreTone(lastInterview.totalScore)}`
              "
            >
              {{ lastInterview.totalScore == null ? '—' : formatScore(lastInterview.totalScore) }}
            </strong>
          </div>
          <el-button
            type="primary"
            class="recent__action"
            data-testid="recent-action"
            @click="handleRecentAction(lastInterview)"
          >
            {{ recentActionLabel(lastInterview) }}
          </el-button>
        </div>

        <div v-else class="recent recent--empty">
          <div class="recent__empty-art" aria-hidden="true">AI</div>
          <div>
            <h3>还没有面试记录</h3>
            <p>选择目标岗位，完成第一场文字模拟面试，系统会为你生成可解释的评估报告。</p>
          </div>
          <el-button type="primary" @click="startInterview()">开始第一场面试</el-button>
        </div>

        <footer v-if="history.length > 1" class="panel__footer">
          <span>共 {{ historyTotal }} 场记录</span>
          <el-button text type="primary" @click="router.push('/history')">
            查看全部历史
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </footer>
      </GlassPanel>

      <GlassPanel class="panel panel--recommend" :padded="false">
        <header class="panel__header">
          <div>
            <h2 class="panel__title">下一步建议</h2>
            <p class="panel__subtitle">基于最近一场报告与掌握度生成。</p>
          </div>
          <MagicStick class="panel__header-icon" aria-hidden="true" />
        </header>

        <LoadingSkeleton v-if="recommendationsLoading" type="list" :rows="3" />

        <div v-else-if="recommendationsError" class="panel__error" role="alert">
          <span>{{ recommendationsError }}</span>
          <el-button text type="primary" @click="loadRecommendations(positions[0]?.id)">
            重试
          </el-button>
        </div>

        <div v-else-if="topRecommendation" class="recommend">
          <p class="recommend__eyebrow">薄弱技能 TOP 1</p>
          <h3 class="recommend__skill">{{ topRecommendation.skillName }}</h3>
          <p class="recommend__reason">
            {{ topRecommendation.weaknessReasons[0] || '继续通过模拟面试积累该技能证据。' }}
          </p>
          <div v-if="topResource" class="recommend__resource">
            <span class="recommend__resource-label">推荐资源</span>
            <strong>{{ truncate(topResource.title, 42) }}</strong>
          </div>
          <el-button type="primary" plain class="recommend__cta" @click="goRecommendations">
            查看完整推荐
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>

        <div v-else class="panel__empty">
          <p>完成一场面试后，这里会展示最需要补齐的技能与学习资源。</p>
          <el-button text type="primary" @click="startInterview()">先完成一场面试</el-button>
        </div>
      </GlassPanel>
    </div>

    <section class="positions" aria-label="岗位入口">
      <header class="positions__header">
        <div>
          <h2 class="positions__title">岗位入口</h2>
          <p class="positions__subtitle">不同岗位使用独立能力模型与题库，评分口径随之切换。</p>
        </div>
        <el-button text type="primary" @click="router.push('/positions')">
          全部岗位
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </header>

      <div v-if="positionsError" class="panel__error" role="alert">
        <span>{{ positionsError }}</span>
        <el-button text type="primary" @click="load">重试</el-button>
      </div>

      <div v-else-if="positions.length === 0 && !loading" class="positions__empty">
        <p>暂无可面试岗位，请联系管理员启用岗位。</p>
      </div>

      <div v-else class="positions__grid">
        <GlassPanel
          v-for="position in positions"
          :key="position.id"
          hoverable
          class="position-card"
          :padded="false"
        >
          <div class="position-card__top">
            <span class="position-card__code">{{ position.code }}</span>
            <el-tag effect="plain" size="small" round>
              默认 {{ position.defaultRounds }} 轮
            </el-tag>
          </div>
          <h3 class="position-card__name">{{ position.name }}</h3>
          <p class="position-card__desc">
            {{ position.description || '岗位能力模型配置中，可进入详情查看技能权重。' }}
          </p>
          <div class="position-card__actions">
            <el-button
              text
              type="primary"
              @click="router.push({ name: 'position-detail', params: { id: String(position.id) } })"
            >
              查看能力模型
              <el-icon><ArrowRight /></el-icon>
            </el-button>
            <el-button type="primary" @click="startInterview(position.id)">开始面试</el-button>
          </div>
        </GlassPanel>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  padding-bottom: var(--space-6);
}

/* ===== Hero ===== */
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.8fr);
  gap: var(--space-6);
  padding: var(--space-6);
}

.hero__glow {
  position: absolute;
  top: -60%;
  right: -8%;
  width: 520px;
  height: 520px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(37, 99, 235, 0.22), transparent 66%);
  pointer-events: none;
}

.hero__main {
  position: relative;
  min-width: 0;
}

.hero__eyebrow {
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.16em;
}

.hero__title {
  margin-top: var(--space-2);
  font-size: clamp(26px, 3vw, 36px);
  font-weight: 700;
  line-height: 1.28;
}

.hero__subtitle {
  max-width: 620px;
  margin-top: var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-md);
  line-height: 1.7;
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-5);
}

.hero__cta {
  min-width: 150px;
}

.hero__stats {
  display: grid;
  gap: var(--space-2);
  align-content: center;
  padding: var(--space-4);
  border: 1px solid rgba(255, 255, 255, 0.62);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.36);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.7) inset;
}

.hero__stat {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.36);
}

.hero__stat-label {
  flex: 1;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.hero__stat-value {
  font-size: var(--font-size-xl);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.hero__stat-unit {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

/* ===== 快捷入口 ===== */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
}

.quick-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 14px;
  border: 1px solid var(--glass-border);
  border-radius: var(--glass-radius);
  background: var(--glass-bg);
  box-shadow: var(--glass-shadow);
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
  backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  transition:
    transform var(--transition-base),
    box-shadow var(--transition-base);
}

.quick-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--glass-shadow-lg);
}

.quick-card__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 12px;
  background: rgba(37, 99, 235, 0.12);
  color: var(--color-primary);
}

.quick-card--blue .quick-card__icon {
  background: rgba(14, 165, 233, 0.14);
  color: #0369a1;
}

.quick-card--cyan .quick-card__icon {
  background: rgba(20, 184, 166, 0.14);
  color: #0f766e;
}

.quick-card--violet .quick-card__icon {
  background: rgba(139, 92, 246, 0.14);
  color: #6d28d9;
}

.quick-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.quick-card__body strong {
  font-size: var(--font-size-sm);
}

.quick-card__body span {
  overflow: hidden;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-card__arrow {
  flex: none;
  color: var(--color-text-tertiary);
}

/* ===== 主面板 ===== */
.home__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(300px, 0.8fr);
  gap: var(--space-4);
  align-items: start;
}

.panel {
  display: flex;
  flex-direction: column;
}

.panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-5);
  padding-bottom: var(--space-3);
}

.panel__header-icon {
  width: 22px;
  height: 22px;
  color: var(--color-primary);
}

.panel__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
}

.panel__subtitle {
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.panel__error {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin: 0 var(--space-5) var(--space-3);
  padding: var(--space-3);
  border: 1px solid rgba(220, 38, 38, 0.22);
  border-radius: var(--radius-md);
  background: rgba(254, 242, 242, 0.72);
  color: var(--color-danger);
  font-size: var(--font-size-sm);
}

.panel__empty {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-5);
  color: var(--color-text-secondary);
}

.panel__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-5);
  border-top: 1px solid rgba(255, 255, 255, 0.48);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

/* ===== 最近面试 ===== */
.recent {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-4);
  margin: 0 var(--space-5) var(--space-5);
  padding: var(--space-4);
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.08), rgba(14, 165, 233, 0.04));
}

.recent__main {
  flex: 1;
  min-width: 220px;
}

.recent__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: var(--space-2);
}

.recent__position {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.recent__meta {
  margin-top: 4px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.recent__score {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 84px;
  padding: 6px 12px;
  border-left: 1px solid rgba(37, 99, 235, 0.16);
}

.recent__score span {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.recent__score strong {
  font-size: var(--font-size-xl);
  font-variant-numeric: tabular-nums;
}

.recent--empty {
  align-items: center;
  border-style: dashed;
  border-color: var(--color-border-strong);
  background: rgba(255, 255, 255, 0.28);
}

.recent__empty-art {
  display: grid;
  flex: none;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: rgba(37, 99, 235, 0.12);
  color: var(--color-primary);
  font-weight: 800;
}

.recent--empty h3 {
  font-size: var(--font-size-md);
}

.recent--empty p {
  margin-top: 4px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.recent__action {
  flex: none;
}

/* ===== 推荐 ===== */
.recommend {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: 0 var(--space-5) var(--space-5);
}

.recommend__eyebrow {
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.12em;
}

.recommend__skill {
  font-size: var(--font-size-lg);
  font-weight: 700;
}

.recommend__reason {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.recommend__resource {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: var(--space-2);
  padding: var(--space-3);
  border: 1px solid rgba(255, 255, 255, 0.56);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.34);
}

.recommend__resource-label {
  color: var(--color-text-tertiary);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.recommend__resource strong {
  font-size: var(--font-size-sm);
  line-height: 1.5;
}

.recommend__cta {
  align-self: flex-start;
  margin-top: var(--space-2);
}

/* ===== 岗位入口 ===== */
.positions {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.positions__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
}

.positions__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
}

.positions__subtitle {
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.positions__empty {
  padding: var(--space-5);
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--glass-radius);
  color: var(--color-text-secondary);
  text-align: center;
}

.positions__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.position-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-5);
}

.position-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.position-card__code {
  color: var(--color-text-tertiary);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.position-card__name {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.position-card__desc {
  display: -webkit-box;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.position-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-top: auto;
  padding-top: var(--space-2);
}

@media (max-width: 1080px) {
  .quick-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hero {
    grid-template-columns: 1fr;
  }

  .hero__stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .home__grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .quick-grid,
  .positions__grid,
  .hero__stats {
    grid-template-columns: 1fr;
  }

  .hero {
    padding: var(--space-5);
  }

  .recent {
    align-items: flex-start;
  }

  .recent__score {
    align-items: flex-start;
    border-left: none;
    border-top: 1px solid rgba(37, 99, 235, 0.16);
    padding: 6px 0 0;
  }

  .recent__action {
    width: 100%;
  }

  .panel__header,
  .panel__footer {
    padding-left: var(--space-4);
    padding-right: var(--space-4);
  }

  .recent,
  .recommend {
    margin-left: var(--space-4);
    margin-right: var(--space-4);
  }
}
</style>
