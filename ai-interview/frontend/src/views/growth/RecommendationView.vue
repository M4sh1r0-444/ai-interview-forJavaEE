<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowRight,
  InfoFilled,
  Link,
  MagicStick,
  Refresh,
  TrendCharts,
} from '@element-plus/icons-vue'

import { getRecommendations } from '@/api/growth'
import { errorMessage, isBizError } from '@/api/http'
import { listPositions } from '@/api/position'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import type { RecommendationVO, ResourceVO } from '@/types/growth'
import type { PositionVO } from '@/types/position'
import { RESOURCE_TYPE_LABELS } from '@/utils/constants'
import { formatPriority } from '@/utils/report'
import { isSafeExternalUrl } from '@/utils/url'
import { parseInterviewSuggestion } from '@/utils/growth'

/**
 * 学习推荐（F4-5，计划 §5.11）：
 * Top3 技能卡（理由/资源/练习/错题/下场建议）+ 岗位切换 + 按建议开始下一场。
 * 外链只允许 http(s) 并带 noopener noreferrer；空数组逐项降级，不阻塞其他板块。
 */
const route = useRoute()
const router = useRouter()

const positions = ref<PositionVO[]>([])
const positionId = ref<number | null>(null)
const recommendations = ref<RecommendationVO[]>([])
const loading = ref(true)
const positionsLoading = ref(true)
const error = ref<unknown>(null)
const positionsError = ref<unknown>(null)
const refreshing = ref(false)

let requestSeq = 0

const topRecommendations = computed(() =>
  [...recommendations.value].sort((left, right) => right.priority - left.priority).slice(0, 3),
)
const selectedPosition = computed(
  () => positions.value.find((item) => item.id === positionId.value) ?? null,
)
const suggestion = computed(() => {
  const text = topRecommendations.value.find(
    (item) => item.nextInterviewSuggested,
  )?.nextInterviewSuggested
  return parseInterviewSuggestion(text)
})
const errorText = computed(() => errorMessage(error.value, '学习推荐加载失败，请稍后重试'))
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

function resourceLabel(resource: ResourceVO): string {
  return RESOURCE_TYPE_LABELS[resource.resourceType] ?? resource.resourceType
}

function safeUrl(value?: string | null): string | null {
  return isSafeExternalUrl(value) ? value : null
}

async function loadRecommendations(
  id: number,
  options: { syncRoute?: boolean } = {},
): Promise<void> {
  const seq = ++requestSeq
  loading.value = true
  error.value = null
  if (options.syncRoute && parsePositionId(route.query.positionId) !== id) {
    await router.replace({ name: 'recommendations', query: { positionId: String(id) } })
  }
  try {
    const list = await getRecommendations(id)
    if (seq !== requestSeq) return
    recommendations.value = Array.isArray(list) ? list : []
  } catch (cause) {
    if (seq !== requestSeq) return
    recommendations.value = []
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
    recommendations.value = []
    positionsLoading.value = false
    loading.value = false
    return
  }
  positionsLoading.value = false

  if (positions.value.length === 0) {
    positionId.value = null
    recommendations.value = []
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
    await router.replace({ name: 'recommendations', query: { positionId: String(selected.id) } })
  }
  await loadRecommendations(selected.id)
}

function handlePositionChange(value: number | string): void {
  const next = Number(value)
  if (!Number.isInteger(next) || next <= 0 || next === positionId.value) return
  positionId.value = next
  void loadRecommendations(next, { syncRoute: true })
}

async function refresh(): Promise<void> {
  if (positionsError.value) {
    await initialize()
    return
  }
  if (positionId.value == null) return
  refreshing.value = true
  await loadRecommendations(positionId.value)
  refreshing.value = false
}

function startNextInterview(): void {
  const query: Record<string, string> = {}
  if (positionId.value != null) query.positionId = String(positionId.value)
  if (suggestion.value.difficulty != null) query.difficulty = String(suggestion.value.difficulty)
  void router.push({ name: 'interview-setup', query })
}

function goGrowth(): void {
  const query: Record<string, string> = {}
  if (positionId.value != null) query.positionId = String(positionId.value)
  void router.push({ name: 'growth', query })
}

function goHistory(): void {
  void router.push('/history')
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
    void loadRecommendations(next)
  },
)
</script>

<template>
  <div class="recommendation-page" data-testid="recommendations-page">
    <AppPageHeader
      title="学习推荐"
      description="把最近一场面试的短板转成可执行的学习、练习与重练计划；优先级 =（100 − 掌握度）× 技能权重。"
    >
      <template #actions>
        <el-button :icon="TrendCharts" @click="goGrowth">看成长</el-button>
        <el-button :icon="Refresh" :loading="refreshing" data-testid="rec-refresh" @click="refresh">
          刷新推荐
        </el-button>
        <el-button
          type="primary"
          :icon="MagicStick"
          data-testid="rec-start-next"
          @click="startNextInterview"
        >
          按建议开始下一场
        </el-button>
      </template>
    </AppPageHeader>

    <GlassPanel class="rec-toolbar" :padded="false" data-testid="rec-toolbar">
      <div class="rec-toolbar__row">
        <div class="rec-toolbar__field">
          <span class="rec-toolbar__label">推荐岗位</span>
          <el-select
            :model-value="positionId ?? undefined"
            class="rec-toolbar__select"
            placeholder="选择岗位"
            :loading="positionsLoading"
            :disabled="positionsLoading || positions.length === 0"
            data-testid="rec-position"
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
        <div v-if="selectedPosition" class="rec-toolbar__meta">
          <span>{{ selectedPosition.name }} · Top {{ topRecommendations.length }}</span>
          <span>推荐结果缓存在服务端约 10 分钟，刷新可能仍命中缓存</span>
        </div>
      </div>
      <p class="rec-toolbar__note">
        <el-icon><InfoFilled /></el-icon>
        新用户也会得到默认岗位的摸底推荐；完成更多面试后，资源、错题重练与下场建议会更贴合你的短板。
      </p>
      <p v-if="suggestion.text" class="rec-toolbar__suggestion" data-testid="rec-suggestion">
        <el-icon><MagicStick /></el-icon>
        {{ suggestion.text }}
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

    <template v-else-if="positionsLoading || (loading && recommendations.length === 0)">
      <LoadingSkeleton type="card" :rows="3" />
      <LoadingSkeleton type="list" :rows="5" />
    </template>

    <GlassPanel v-else-if="positions.length === 0" :padded="false" data-testid="rec-no-positions">
      <EmptyState
        title="暂无可推荐岗位"
        description="当前没有启用的岗位，请稍后再试或联系管理员。"
        :show-action="false"
      />
    </GlassPanel>

    <ErrorState
      v-else-if="error"
      title="学习推荐加载失败"
      :message="errorText"
      :code="errorCode"
      :trace-id="errorTrace"
      @retry="refresh"
    />

    <GlassPanel v-else-if="topRecommendations.length === 0" :padded="false" data-testid="rec-empty">
      <EmptyState
        title="还没有可用的推荐"
        description="完成一场面试并生成报告后，这里会按短板技能给出 Top3 学习与重练计划。"
      >
        <template #actions>
          <el-button type="primary" @click="startNextInterview">开始面试</el-button>
          <el-button @click="goHistory">查看面试历史</el-button>
        </template>
      </EmptyState>
    </GlassPanel>

    <div v-else class="rec-list">
      <GlassPanel
        v-for="(item, index) in topRecommendations"
        :key="item.skillCode"
        class="rec-card"
        :padded="false"
        :data-testid="`rec-card-${item.skillCode}`"
      >
        <header class="rec-card__head">
          <div class="rec-card__identity">
            <span class="rec-card__rank" aria-hidden="true">TOP {{ index + 1 }}</span>
            <div>
              <h2>{{ item.skillName }}</h2>
              <p class="rec-card__code">{{ item.skillCode }}</p>
            </div>
          </div>
          <div class="rec-card__priority">
            <span>优先级</span>
            <strong>{{ formatPriority(item.priority) }}</strong>
          </div>
        </header>

        <section class="rec-card__section">
          <h3>为什么推荐</h3>
          <ul v-if="(item.weaknessReasons?.length ?? 0) > 0" class="rec-reasons">
            <li v-for="reason in item.weaknessReasons" :key="reason">{{ reason }}</li>
          </ul>
          <p v-else class="rec-muted">暂无短板理由，建议先安排一场摸底面试。</p>
        </section>

        <div class="rec-card__columns">
          <section class="rec-card__section">
            <h3>学习资源</h3>
            <ul v-if="(item.resources?.length ?? 0) > 0" class="rec-resources">
              <li v-for="resource in item.resources" :key="resource.id">
                <a
                  v-if="safeUrl(resource.contentUrl)"
                  :href="safeUrl(resource.contentUrl) ?? undefined"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="rec-resource__title"
                  :data-testid="`rec-resource-${resource.id}`"
                >
                  <el-icon><Link /></el-icon>
                  {{ resource.title }}
                </a>
                <span v-else class="rec-resource__title rec-resource__title--plain">
                  {{ resource.title }}
                </span>
                <span class="rec-resource__meta">
                  {{ resourceLabel(resource) }} · 难度 {{ resource.difficulty }}
                </span>
              </li>
            </ul>
            <p v-else class="rec-muted">暂无匹配文章/题目，可先做练习或重练错题。</p>
          </section>

          <section class="rec-card__section">
            <h3>练习与实践</h3>
            <ul v-if="(item.practices?.length ?? 0) > 0" class="rec-resources">
              <li v-for="resource in item.practices" :key="resource.id">
                <a
                  v-if="safeUrl(resource.contentUrl)"
                  :href="safeUrl(resource.contentUrl) ?? undefined"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="rec-resource__title"
                  :data-testid="`rec-practice-${resource.id}`"
                >
                  <el-icon><Link /></el-icon>
                  {{ resource.title }}
                </a>
                <span v-else class="rec-resource__title rec-resource__title--plain">
                  {{ resource.title }}
                </span>
                <span class="rec-resource__meta">
                  {{ resourceLabel(resource) }} · 难度 {{ resource.difficulty }}
                </span>
              </li>
            </ul>
            <p v-else class="rec-muted">暂无练习项；可先用站内题库做同类题。</p>
          </section>
        </div>

        <section class="rec-card__section">
          <h3>错题重练</h3>
          <div v-if="(item.retryQuestions?.length ?? 0) > 0" class="rec-retries">
            <details
              v-for="question in item.retryQuestions"
              :key="question.questionId"
              class="rec-retry"
              :data-testid="`rec-retry-${item.skillCode}-${question.questionId}`"
            >
              <summary>
                <span class="rec-retry__summary">{{ question.questionContent }}</span>
                <span class="rec-retry__count">
                  {{ question.missingPoints?.length ?? 0 }} 个缺失点
                </span>
              </summary>
              <div class="rec-retry__body">
                <p class="rec-retry__label">未覆盖的失分点：</p>
                <ul v-if="(question.missingPoints?.length ?? 0) > 0">
                  <li v-for="point in question.missingPoints" :key="point">{{ point }}</li>
                </ul>
                <p v-else class="rec-muted">该题未记录缺失点，建议完整复盘回答。</p>
              </div>
            </details>
          </div>
          <p v-else class="rec-muted">暂无错题重练（最近一场没有该技能的失分记录）。</p>
        </section>

        <footer v-if="item.nextInterviewSuggested" class="rec-card__footer">
          <el-icon><MagicStick /></el-icon>
          <span>{{ item.nextInterviewSuggested }}</span>
        </footer>
      </GlassPanel>

      <GlassPanel class="rec-actions" data-testid="rec-actions">
        <div>
          <h2>把推荐变成下一场练习</h2>
          <p>
            按建议难度与重点技能开始下一场
            <template v-if="suggestion.difficulty">（难度 {{ suggestion.difficulty }}）</template>
            面试；结束后成长曲线与推荐会随之更新。
          </p>
        </div>
        <div class="rec-actions__buttons">
          <el-button :icon="TrendCharts" @click="goGrowth">查看成长</el-button>
          <el-button :icon="Refresh" :loading="refreshing" @click="refresh">刷新推荐</el-button>
          <el-button type="primary" @click="startNextInterview">
            开始下一场
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </GlassPanel>
    </div>
  </div>
</template>

<style scoped>
.recommendation-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding-bottom: var(--space-6);
}

.rec-toolbar {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-5);
}

.rec-toolbar__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.rec-toolbar__field {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.rec-toolbar__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.rec-toolbar__select {
  width: 260px;
}

.rec-toolbar__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.rec-toolbar__note,
.rec-toolbar__suggestion {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.rec-toolbar__suggestion {
  padding: var(--space-2) var(--space-3);
  border: 1px solid rgba(37, 99, 235, 0.2);
  border-radius: var(--radius-md);
  background: rgba(239, 246, 255, 0.64);
  color: var(--color-primary);
  font-weight: 600;
}

.rec-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.rec-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-5);
}

.rec-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid rgba(255, 255, 255, 0.46);
}

.rec-card__identity {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  min-width: 0;
}

.rec-card__rank {
  flex: none;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.16), rgba(14, 165, 233, 0.1));
  color: var(--color-primary);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.rec-card__identity h2 {
  font-size: var(--font-size-lg);
  font-weight: 700;
  line-height: 1.35;
}

.rec-card__code {
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.rec-card__priority {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: flex-end;
  gap: 1px;
}

.rec-card__priority span {
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.rec-card__priority strong {
  color: var(--color-primary);
  font-size: var(--font-size-xl);
  font-variant-numeric: tabular-nums;
}

.rec-card__section h3 {
  margin-bottom: 4px;
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.rec-reasons {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding-left: 18px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.65;
}

.rec-reasons li::marker {
  color: var(--color-warning);
}

.rec-card__columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.rec-resources {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.rec-resources li {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: var(--space-2) var(--space-3);
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.3);
}

.rec-resource__title {
  display: inline-flex;
  align-items: flex-start;
  gap: 5px;
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: 600;
  line-height: 1.55;
}

.rec-resource__title--plain {
  color: var(--color-text);
}

.rec-resource__meta {
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.rec-retries {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.rec-retry {
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.3);
}

.rec-retry summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3);
  cursor: pointer;
  list-style: none;
}

.rec-retry summary::-webkit-details-marker {
  display: none;
}

.rec-retry__summary {
  color: var(--color-text);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.rec-retry__count {
  flex: none;
  color: var(--color-danger);
  font-size: 11px;
  font-weight: 600;
}

.rec-retry__body {
  padding: 0 var(--space-3) var(--space-3) var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.65;
}

.rec-retry__body ul {
  margin: 4px 0 0;
  padding-left: 18px;
}

.rec-retry__label {
  color: var(--color-text-tertiary);
}

.rec-card__footer {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: var(--space-3);
  border: 1px solid rgba(37, 99, 235, 0.18);
  border-radius: var(--radius-md);
  background: rgba(239, 246, 255, 0.56);
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  line-height: 1.65;
}

.rec-muted {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.rec-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.rec-actions h2 {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.rec-actions p {
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.rec-actions__buttons {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

@media (max-width: 900px) {
  .rec-card__columns {
    grid-template-columns: 1fr;
  }

  .rec-actions {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 768px) {
  .rec-toolbar {
    padding: var(--space-3) var(--space-4);
  }

  .rec-toolbar__field {
    width: 100%;
  }

  .rec-toolbar__select {
    width: 100%;
  }

  .rec-card {
    padding: var(--space-4);
  }

  .rec-actions__buttons {
    width: 100%;
  }

  .rec-actions__buttons :deep(.el-button) {
    flex: 1;
  }
}
</style>
