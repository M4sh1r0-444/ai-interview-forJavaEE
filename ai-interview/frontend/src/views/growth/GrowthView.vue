<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { InfoFilled, Refresh, Right, TrendCharts } from '@element-plus/icons-vue'

import { getGrowth } from '@/api/growth'
import { errorMessage, isBizError } from '@/api/http'
import { listPositions } from '@/api/position'
import GrowthLineChart from '@/components/charts/GrowthLineChart.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import type { SkillGrowthVO } from '@/types/growth'
import type { PositionVO } from '@/types/position'
import { formatDateTime, formatScore } from '@/utils/format'
import { buildGrowthSeries, growthDeltaText, growthPointCount, hasGrowthData } from '@/utils/growth'

/**
 * 能力成长（F4-4，计划 §5.10）：
 * 顶部岗位切换 + 掌握度口径说明 + ECharts 多折线 + 技能卡片；
 * 切岗位写 URL（positionId），空态/错误态可恢复，图表由 useEchart 自动 dispose。
 */
const route = useRoute()
const router = useRouter()

const positions = ref<PositionVO[]>([])
const positionId = ref<number | null>(null)
const skills = ref<SkillGrowthVO[]>([])
const loading = ref(true)
const positionsLoading = ref(true)
const error = ref<unknown>(null)
const positionsError = ref<unknown>(null)

let requestSeq = 0

const series = computed(() => buildGrowthSeries(skills.value))
const hasData = computed(() => hasGrowthData(series.value))
const pointTotal = computed(() => growthPointCount(series.value))
const recordedSkills = computed(() => series.value.filter((item) => item.points.length > 0).length)
const latestAt = computed(() => {
  let latest = 0
  for (const item of series.value) {
    if (!item.latestAt) continue
    const timestamp = new Date(item.latestAt).getTime()
    if (Number.isFinite(timestamp) && timestamp > latest) latest = timestamp
  }
  return latest > 0 ? new Date(latest).toISOString() : null
})
const selectedPosition = computed(
  () => positions.value.find((item) => item.id === positionId.value) ?? null,
)
const errorText = computed(() => errorMessage(error.value, '能力成长加载失败，请稍后重试'))
const errorCode = computed(() => (isBizError(error.value) ? error.value.code : undefined))
const errorTrace = computed(() => (isBizError(error.value) ? error.value.traceId : ''))
const positionsErrorText = computed(() =>
  errorMessage(positionsError.value, '岗位列表加载失败，请稍后重试'),
)
const positionsErrorCode = computed(() =>
  isBizError(positionsError.value) ? positionsError.value.code : undefined,
)
const positionsErrorTrace = computed(() =>
  isBizError(positionsError.value) ? (positionsError.value.traceId ?? '') : '',
)

function parsePositionId(value: unknown): number | null {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = typeof raw === 'string' ? Number(raw) : Number.NaN
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

type LoadOptions = { syncRoute?: boolean }

async function loadGrowth(id: number, options: LoadOptions = {}): Promise<void> {
  const seq = ++requestSeq
  loading.value = true
  error.value = null
  if (options.syncRoute && parsePositionId(route.query.positionId) !== id) {
    await router.replace({ name: 'growth', query: { positionId: String(id) } })
  }
  try {
    const list = await getGrowth(id)
    if (seq !== requestSeq) return
    skills.value = Array.isArray(list) ? list : []
  } catch (cause) {
    if (seq !== requestSeq) return
    skills.value = []
    error.value = cause
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

async function initialize(): Promise<void> {
  positionsLoading.value = true
  positionsError.value = null
  error.value = null
  try {
    const list = await listPositions()
    positions.value = Array.isArray(list) ? list : []
  } catch (cause) {
    positionsError.value = cause
    positions.value = []
    skills.value = []
    positionsLoading.value = false
    loading.value = false
    return
  }
  positionsLoading.value = false

  if (positions.value.length === 0) {
    positionId.value = null
    skills.value = []
    loading.value = false
    return
  }

  const requested = parsePositionId(route.query.positionId)
  const selected =
    positions.value.find((item) => item.id === requested) ?? positions.value[0] ?? null
  if (!selected) {
    loading.value = false
    return
  }
  positionId.value = selected.id
  if (selected.id !== requested) {
    await router.replace({ name: 'growth', query: { positionId: String(selected.id) } })
  }
  await loadGrowth(selected.id)
}

function handlePositionChange(value: number | string): void {
  const next = Number(value)
  if (!Number.isInteger(next) || next <= 0 || next === positionId.value) return
  positionId.value = next
  void loadGrowth(next, { syncRoute: true })
}

function refresh(): void {
  if (positionsError.value) {
    void initialize()
    return
  }
  if (positionId.value != null) void loadGrowth(positionId.value)
}

function startInterview(): void {
  const query: Record<string, string> = {}
  if (positionId.value != null) query.positionId = String(positionId.value)
  void router.push({ name: 'interview-setup', query })
}

onMounted(() => {
  void initialize()
})

watch(
  () => route.query.positionId,
  (raw) => {
    const next = parsePositionId(raw)
    if (next == null || next === positionId.value) return
    if (!positions.value.some((item) => item.id === next)) return
    positionId.value = next
    void loadGrowth(next)
  },
)
</script>

<template>
  <div class="growth-page" data-testid="growth-page">
    <AppPageHeader
      title="能力成长"
      description="按技能查看掌握度随面试场次的变化；掌握度首次直取，之后按旧 × 0.7 + 本次 × 0.3 折算。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="refresh">刷新</el-button>
        <el-button type="primary" :icon="TrendCharts" @click="startInterview">
          开始面试
          <el-icon><Right /></el-icon>
        </el-button>
      </template>
    </AppPageHeader>

    <GlassPanel class="growth-toolbar" :padded="false" data-testid="growth-toolbar">
      <div class="growth-toolbar__row">
        <div class="growth-toolbar__field">
          <span class="growth-toolbar__label">查看岗位</span>
          <el-select
            :model-value="positionId ?? undefined"
            class="growth-toolbar__select"
            placeholder="选择岗位"
            :loading="positionsLoading"
            :disabled="positionsLoading || positions.length === 0"
            data-testid="growth-position"
            @change="handlePositionChange"
          >
            <el-option
              v-for="position in positions"
              :key="position.id"
              :label="position.name"
              :value="position.id"
            />
          </el-select>
        </div>
        <div v-if="selectedPosition" class="growth-toolbar__meta">
          <span>{{ selectedPosition.name }} · 已记录 {{ recordedSkills }} 个技能</span>
          <span>共 {{ pointTotal }} 个掌握度记录点</span>
          <span v-if="latestAt">最近更新 {{ formatDateTime(latestAt) }}</span>
        </div>
      </div>
      <p class="growth-toolbar__note">
        <el-icon><InfoFilled /></el-icon>
        掌握度按题目难度加权；同一天多场面试会生成多个记录点。岗位切换会同步到地址栏。
      </p>
    </GlassPanel>

    <ErrorState
      v-if="positionsError"
      title="岗位列表加载失败"
      :message="positionsErrorText"
      :code="positionsErrorCode"
      :trace-id="positionsErrorTrace"
      @retry="initialize"
    />

    <template v-else-if="positionsLoading || (loading && skills.length === 0)">
      <LoadingSkeleton type="chart" />
      <div class="growth-grid">
        <LoadingSkeleton v-for="index in 3" :key="index" type="card" :rows="2" />
      </div>
    </template>

    <GlassPanel
      v-else-if="positions.length === 0"
      :padded="false"
      data-testid="growth-no-positions"
    >
      <EmptyState
        title="暂无可查看的岗位"
        description="当前没有启用的岗位，请稍后再试或联系管理员。"
        :show-action="false"
      />
    </GlassPanel>

    <ErrorState
      v-else-if="error"
      title="能力成长加载失败"
      :message="errorText"
      :code="errorCode"
      :trace-id="errorTrace"
      @retry="refresh"
    />

    <GlassPanel v-else-if="!hasData" :padded="false" data-testid="growth-empty">
      <EmptyState
        title="还没有成长记录"
        description="完成第一场面试后，系统会按技能记录掌握度，并在曲线上展示每次变化。"
        action-text="选择岗位开始面试"
        @action="startInterview"
      />
    </GlassPanel>

    <template v-else>
      <GlassPanel class="growth-chart-panel" :padded="false">
        <header class="growth-panel-head">
          <div>
            <h2>掌握度曲线</h2>
            <p>纵轴 0~100；每条折线代表一个技能，图例可点击显隐，悬停查看各场分数。</p>
          </div>
          <span class="growth-panel-head__count">{{ recordedSkills }} 个技能</span>
        </header>
        <div class="growth-chart-panel__body">
          <GrowthLineChart :series="series" />
        </div>
      </GlassPanel>

      <div class="growth-grid">
        <GlassPanel
          v-for="item in series"
          :key="item.skillCode"
          class="skill-card"
          :class="{ 'skill-card--muted': item.points.length === 0 }"
          :padded="false"
          :data-testid="`growth-skill-${item.skillCode}`"
        >
          <header class="skill-card__head">
            <div>
              <h3>{{ item.skillName }}</h3>
              <p class="skill-card__code">{{ item.skillCode }}</p>
            </div>
            <strong class="skill-card__current" :class="`score--${item.tone}`">
              {{ formatScore(item.current) }}
            </strong>
          </header>

          <div class="skill-card__bar" aria-hidden="true">
            <span :style="{ width: `${Math.max(0, Math.min(100, item.current ?? 0))}%` }" />
          </div>

          <div class="skill-card__meta">
            <span>{{ item.points.length }} 个记录点</span>
            <span
              v-if="item.points.length > 0"
              :class="item.delta != null && item.delta < 0 ? 'is-down' : 'is-up'"
            >
              变化 {{ growthDeltaText(item) }}
            </span>
            <span v-else>尚无考察记录</span>
          </div>

          <p v-if="item.latestAt" class="skill-card__latest">
            最近记录 {{ formatDateTime(item.latestAt) }}
          </p>
          <p v-else class="skill-card__latest">建议安排一场包含该技能的摸底面试。</p>
        </GlassPanel>
      </div>
    </template>
  </div>
</template>

<style scoped>
.growth-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding-bottom: var(--space-6);
}

.growth-toolbar {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-5);
}

.growth-toolbar__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.growth-toolbar__field {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.growth-toolbar__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.growth-toolbar__select {
  width: 260px;
}

.growth-toolbar__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.growth-toolbar__note {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.growth-chart-panel {
  min-width: 0;
}

.growth-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5) var(--space-3);
  border-bottom: 1px solid rgba(255, 255, 255, 0.46);
}

.growth-panel-head h2 {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.growth-panel-head p {
  margin-top: 2px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.growth-panel-head__count {
  flex: none;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.growth-chart-panel__body {
  padding: var(--space-3) var(--space-4) var(--space-4);
}

.growth-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--space-3);
}

.skill-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-4);
}

.skill-card--muted {
  opacity: 0.82;
}

.skill-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-2);
}

.skill-card__head h3 {
  font-size: var(--font-size-sm);
  font-weight: 700;
  line-height: 1.45;
}

.skill-card__code {
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.skill-card__current {
  font-size: var(--font-size-xl);
  font-variant-numeric: tabular-nums;
}

.skill-card__bar {
  overflow: hidden;
  height: 7px;
  border-radius: var(--radius-pill);
  background: rgba(148, 163, 184, 0.18);
}

.skill-card__bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2563eb, #38bdf8);
  transition: width 300ms ease;
}

.skill-card__meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.skill-card__meta .is-up {
  color: var(--color-success);
}

.skill-card__meta .is-down {
  color: var(--color-danger);
}

.skill-card__latest {
  color: var(--color-text-tertiary);
  font-size: 11px;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .growth-toolbar {
    padding: var(--space-3) var(--space-4);
  }

  .growth-toolbar__select {
    width: 100%;
  }

  .growth-toolbar__field {
    width: 100%;
  }

  .growth-panel-head,
  .growth-chart-panel__body {
    padding-left: var(--space-4);
    padding-right: var(--space-4);
  }
}

@media (prefers-reduced-motion: reduce) {
  .skill-card__bar span {
    transition: none;
  }
}
</style>
