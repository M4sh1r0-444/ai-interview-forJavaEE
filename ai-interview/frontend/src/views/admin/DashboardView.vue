<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { DataLine, Document, InfoFilled, Refresh, User, VideoCamera } from '@element-plus/icons-vue'

import { adminDashboard } from '@/api/admin'
import { errorMessage, isBizError } from '@/api/http'
import TrendBarLineChart from '@/components/charts/TrendBarLineChart.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import type { DashboardVO } from '@/types/admin'
import { formatNumber, formatScore } from '@/utils/format'

/**
 * 管理端看板（F5-2，计划 §5.13）：
 * 四张统计卡 + 近 7 日柱线组合；avgScore 可能 null → 显示「—」，
 * 折线 null 用断点表示；刷新按钮重新拉取，失败可重试。
 */
const data = ref<DashboardVO | null>(null)
const loading = ref(true)
const error = ref<unknown>(null)
let requestSeq = 0

const errorText = computed(() => errorMessage(error.value, '看板数据加载失败，请稍后重试'))
const errorCode = computed(() => (isBizError(error.value) ? error.value.code : undefined))
const errorTrace = computed(() => (isBizError(error.value) ? error.value.traceId : ''))
const trendPoints = computed(() => data.value?.recent7Days ?? [])
const hasData = computed(() => data.value != null)

const statCards = computed(() => [
  {
    key: 'user',
    label: '用户总数',
    value: formatNumber(data.value?.userCount),
    hint: '全部注册账号（含管理员）',
    icon: User,
    tone: 'blue',
    score: false,
  },
  {
    key: 'session',
    label: '面试场次',
    value: formatNumber(data.value?.sessionCount),
    hint: '已开始场次（started_at 非空）',
    icon: VideoCamera,
    tone: 'cyan',
    score: false,
  },
  {
    key: 'question',
    label: '题库题量',
    value: formatNumber(data.value?.questionCount),
    hint: '全部题目（含逻辑删除）',
    icon: Document,
    tone: 'violet',
    score: false,
  },
  {
    key: 'avg',
    label: '平均分',
    value: formatScore(data.value?.avgScore),
    hint: '全部已出分场次均值',
    icon: DataLine,
    tone: 'amber',
    score: true,
  },
])

async function load(): Promise<void> {
  const seq = ++requestSeq
  loading.value = true
  error.value = null
  try {
    const result = await adminDashboard()
    if (seq !== requestSeq) return
    data.value = result
  } catch (cause) {
    if (seq !== requestSeq) return
    error.value = cause
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="dashboard-page admin-page" data-testid="dashboard-page">
    <AppPageHeader
      title="数据看板"
      description="用户、场次、题库与均分的一屏概览；近 7 日趋势按日展示场次与出分均分。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" data-testid="dashboard-refresh" @click="load">
          刷新
        </el-button>
      </template>
    </AppPageHeader>

    <div v-if="loading && !hasData" class="dashboard-loading" aria-busy="true">
      <div class="admin-stat-grid">
        <GlassPanel v-for="index in 4" :key="index" class="dashboard-loading__card">
          <LoadingSkeleton :rows="2" />
        </GlassPanel>
      </div>
      <GlassPanel>
        <LoadingSkeleton type="chart" />
      </GlassPanel>
    </div>

    <ErrorState
      v-else-if="error && !hasData"
      title="看板数据加载失败"
      :message="errorText"
      :code="errorCode"
      :trace-id="errorTrace"
      @retry="load"
    />

    <template v-else-if="hasData">
      <section class="admin-stat-grid" aria-label="核心指标">
        <GlassPanel
          v-for="card in statCards"
          :key="card.key"
          class="stat-card"
          :class="`stat-card--${card.tone}`"
          :data-testid="`stat-${card.key}`"
        >
          <div class="stat-card__top">
            <span class="stat-card__label">{{ card.label }}</span>
            <span class="stat-card__icon" aria-hidden="true">
              <el-icon :size="18"><component :is="card.icon" /></el-icon>
            </span>
          </div>
          <strong class="stat-card__value" :class="{ 'stat-card__value--score': card.score }">
            {{ card.value }}
          </strong>
          <p class="stat-card__hint">{{ card.hint }}</p>
        </GlassPanel>
      </section>

      <GlassPanel class="dashboard-chart-panel" :padded="false">
        <div class="dashboard-chart-panel__head">
          <div>
            <h2>近 7 日趋势</h2>
            <p>柱=每日开始场次（左轴）；线=当日出分平均分（右轴），无出分记录保持断点。</p>
          </div>
          <el-tag type="info" effect="plain" round>含今日 · 日期升序</el-tag>
        </div>
        <div class="dashboard-chart-panel__body">
          <TrendBarLineChart :points="trendPoints" />
        </div>
      </GlassPanel>

      <div class="admin-note">
        <el-icon :size="16" class="admin-note__icon"><InfoFilled /></el-icon>
        <div>
          <strong>指标口径</strong>
          <div>
            场次只统计已开始的面试（CREATED 草稿不计）；平均分只统计已出分场次（total_score
            非空）；题量包含逻辑删除题目，因此与题库页「已启用」筛选下的数量可能不同。
          </div>
        </div>
      </div>

      <p v-if="error" class="dashboard-stale" role="alert">
        刷新失败，当前展示的是上一次成功数据：{{ errorText }}
      </p>
    </template>
  </div>
</template>

<style scoped>
.dashboard-page {
  min-width: 0;
}

.dashboard-loading {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.dashboard-loading__card {
  min-height: 132px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-height: 132px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.72), rgba(255, 255, 255, 0.4));
}

.stat-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.stat-card__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.stat-card__icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 10px;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.8) inset;
}

.stat-card--blue .stat-card__icon {
  background: rgba(37, 99, 235, 0.12);
  color: #2563eb;
}

.stat-card--cyan .stat-card__icon {
  background: rgba(14, 165, 233, 0.14);
  color: #0284c7;
}

.stat-card--violet .stat-card__icon {
  background: rgba(139, 92, 246, 0.12);
  color: #7c3aed;
}

.stat-card--amber .stat-card__icon {
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
}

.stat-card__value {
  color: var(--color-text);
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.01em;
}

.stat-card__value--score {
  color: var(--color-primary);
}

.stat-card__hint {
  margin-top: auto;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.5;
}

.dashboard-chart-panel {
  min-width: 0;
}

.dashboard-chart-panel__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid rgba(255, 255, 255, 0.46);
}

.dashboard-chart-panel__head h2 {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.dashboard-chart-panel__head p {
  margin-top: 2px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.dashboard-chart-panel__body {
  padding: var(--space-3) var(--space-4) var(--space-4);
}

.admin-note__icon {
  flex: none;
  margin-top: 2px;
  color: var(--color-primary);
}

.dashboard-stale {
  color: var(--color-danger);
  font-size: var(--font-size-xs);
}

@media (max-width: 768px) {
  .stat-card__value {
    font-size: 26px;
  }

  .dashboard-chart-panel__head {
    padding: var(--space-3) var(--space-4);
  }
}
</style>
