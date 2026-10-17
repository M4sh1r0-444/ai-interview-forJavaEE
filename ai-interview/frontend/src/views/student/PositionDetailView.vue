<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  ArrowRight,
  DataAnalysis,
  Refresh,
  WarningFilled,
} from '@element-plus/icons-vue'

import { errorMessage, isBizError } from '@/api/http'
import { getPosition, getPositionSkills } from '@/api/position'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import type { PositionVO, SkillVO } from '@/types/position'
import { formatPercent } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const position = ref<PositionVO | null>(null)
const skills = ref<SkillVO[]>([])
const loading = ref(true)
const error = ref('')
const errorCode = ref<number | null>(null)
const traceId = ref('')
const notFound = ref(false)

const positionId = computed(() => {
  const parsed = Number(route.params.id)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})

const totalWeight = computed(() =>
  skills.value.reduce((sum, skill) => sum + (skill.weight || 0), 0),
)
const weightValid = computed(() => Math.abs(totalWeight.value - 1) <= 0.01)
const canStart = computed(() => skills.value.length > 0 && !!position.value)

const scoreRules = [
  { label: '内容正确性', weight: '50%', description: '得分点覆盖、技术准确性、深度与证据' },
  { label: '表达与逻辑', weight: '30%', description: '结构化表达、因果衔接、沟通清晰度' },
  { label: '应变与追问', weight: '10%', description: '追问响应质量、边界条件与取舍' },
  { label: '岗位匹配', weight: '10%', description: '技能关键词、真实场景与岗位要求' },
]

async function load(): Promise<void> {
  if (positionId.value == null) {
    error.value = '岗位编号无效'
    errorCode.value = 40401
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  errorCode.value = null
  traceId.value = ''
  notFound.value = false

  const [positionResult, skillResult] = await Promise.allSettled([
    getPosition(positionId.value),
    getPositionSkills(positionId.value),
  ])

  if (positionResult.status === 'fulfilled') {
    position.value = positionResult.value
  } else {
    position.value = null
    if (isBizError(positionResult.reason) && positionResult.reason.code === 40401) {
      notFound.value = true
    } else {
      error.value = errorMessage(positionResult.reason, '岗位详情加载失败')
      errorCode.value = isBizError(positionResult.reason) ? positionResult.reason.code : null
      traceId.value = isBizError(positionResult.reason) ? (positionResult.reason.traceId ?? '') : ''
    }
    loading.value = false
    return
  }

  if (skillResult.status === 'fulfilled') {
    skills.value = skillResult.value
  } else {
    skills.value = []
    error.value = errorMessage(skillResult.reason, '能力模型加载失败')
    errorCode.value = isBizError(skillResult.reason) ? skillResult.reason.code : null
    traceId.value = isBizError(skillResult.reason) ? (skillResult.reason.traceId ?? '') : ''
  }
  loading.value = false
}

function goSetup(): void {
  if (positionId.value == null) return
  void router.push({ name: 'interview-setup', query: { positionId: String(positionId.value) } })
}

function goBack(): void {
  void router.push('/positions')
}

watch(
  () => route.params.id,
  () => {
    void load()
  },
  { immediate: true },
)
</script>

<template>
  <div class="position-detail">
    <AppPageHeader
      :title="position?.name || '岗位详情'"
      :description="position?.description || '查看岗位能力模型、技能权重与本场面试入口。'"
      show-back
      back-to="/positions"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button v-if="canStart" type="primary" data-testid="detail-start" @click="goSetup">
          开始面试
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </template>
    </AppPageHeader>

    <LoadingSkeleton v-if="loading" type="card" :rows="4" />

    <GlassPanel v-else-if="notFound" :padded="false">
      <EmptyState
        title="岗位不存在或已停用"
        description="该岗位可能已被管理员停用，返回岗位列表查看当前可面试的岗位。"
        action-text="返回岗位列表"
        @action="goBack"
      />
    </GlassPanel>

    <ErrorState
      v-else-if="error && !position"
      title="岗位详情加载失败"
      :message="error"
      :code="errorCode ?? undefined"
      :trace-id="traceId"
      @retry="load"
    />

    <template v-else-if="position">
      <div v-if="error" class="position-detail__error" role="alert">
        <span>{{ error }}</span>
        <el-button text type="primary" @click="load">重试</el-button>
      </div>

      <div class="position-detail__summary">
        <GlassPanel class="summary-card" tone="strong">
          <div class="summary-card__main">
            <span class="summary-card__code">{{ position.code }}</span>
            <h2 class="summary-card__title">{{ position.name }}</h2>
            <p class="summary-card__desc">
              {{ position.description || '该岗位暂未填写描述。' }}
            </p>
          </div>
          <dl class="summary-card__facts">
            <div>
              <dt>默认轮次</dt>
              <dd>{{ position.defaultRounds }}</dd>
            </div>
            <div>
              <dt>技能项</dt>
              <dd>{{ position.skillCount ?? skills.length }}</dd>
            </div>
            <div>
              <dt>权重合计</dt>
              <dd :class="weightValid ? 'text-success' : 'text-danger'">
                {{ formatPercent(totalWeight) }}
              </dd>
            </div>
          </dl>
        </GlassPanel>

        <GlassPanel class="weight-card">
          <header class="weight-card__header">
            <div>
              <h2 class="weight-card__title">能力模型</h2>
              <p class="weight-card__subtitle">技能权重用于组卷覆盖与评分加权。</p>
            </div>
            <el-tag :type="weightValid ? 'success' : 'danger'" effect="light" round size="small">
              {{ weightValid ? '权重校验通过' : '权重合计异常' }}
            </el-tag>
          </header>

          <div v-if="skills.length === 0" class="weight-card__empty">
            <el-icon :size="20"><WarningFilled /></el-icon>
            <div>
              <strong>能力模型配置中</strong>
              <p>该岗位还没有技能项，暂时无法开始面试。</p>
            </div>
          </div>

          <ul v-else class="skill-list">
            <li v-for="(skill, index) in skills" :key="skill.skillCode" class="skill-row">
              <div class="skill-row__head">
                <span class="skill-row__index">{{ String(index + 1).padStart(2, '0') }}</span>
                <div class="skill-row__name">
                  <strong>{{ skill.skillName }}</strong>
                  <code>{{ skill.skillCode }}</code>
                </div>
                <span class="skill-row__weight">{{ formatPercent(skill.weight) }}</span>
              </div>
              <p v-if="skill.description" class="skill-row__desc">{{ skill.description }}</p>
              <div class="skill-row__bar" aria-hidden="true">
                <span
                  class="skill-row__bar-fill"
                  :style="{ width: `${Math.min(100, Math.max(4, (skill.weight || 0) * 100))}%` }"
                />
              </div>
              <p v-if="skill.requiredLevel != null" class="skill-row__level">
                要求等级 {{ skill.requiredLevel }} / 100
              </p>
            </li>
          </ul>
        </GlassPanel>
      </div>

      <div class="position-detail__grid">
        <GlassPanel class="rules-card">
          <header class="rules-card__header">
            <span class="rules-card__icon" aria-hidden="true">
              <el-icon :size="19"><DataAnalysis /></el-icon>
            </span>
            <div>
              <h2>评分口径</h2>
              <p>默认总分权重；岗位可在管理端覆盖为自定义权重。</p>
            </div>
          </header>
          <ul class="rules-list">
            <li v-for="rule in scoreRules" :key="rule.label">
              <div class="rules-list__head">
                <strong>{{ rule.label }}</strong>
                <span>{{ rule.weight }}</span>
              </div>
              <p>{{ rule.description }}</p>
            </li>
          </ul>
        </GlassPanel>

        <GlassPanel class="explain-card">
          <h2>这场面试会怎样进行？</h2>
          <ol class="explain-list">
            <li>
              <strong>组卷</strong>
              <span>按岗位技能权重与难度抽取 5~8 道主问题，覆盖技术/项目/场景/行为。</span>
            </li>
            <li>
              <strong>多轮追问</strong>
              <span>AI 根据回答的得分点覆盖与模糊程度追问，最多 3 次后推进下一题。</span>
            </li>
            <li>
              <strong>收卷评分</strong>
              <span>结束后异步生成报告，包含维度得分、逐题证据与改进建议。</span>
            </li>
          </ol>
          <el-button
            v-if="canStart"
            type="primary"
            size="large"
            class="explain-card__cta"
            @click="goSetup"
          >
            进入面试设置
            <el-icon><ArrowRight /></el-icon>
          </el-button>
          <el-button v-else text disabled class="explain-card__cta">
            能力模型配置完成后可开始
          </el-button>
        </GlassPanel>
      </div>

      <div class="position-detail__back">
        <el-button text @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回岗位列表
        </el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.position-detail {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding-bottom: var(--space-6);
}

.position-detail__error {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px solid rgba(220, 38, 38, 0.22);
  border-radius: var(--radius-md);
  background: rgba(254, 242, 242, 0.72);
  color: var(--color-danger);
  font-size: var(--font-size-sm);
}

.position-detail__summary {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.4fr);
  gap: var(--space-4);
  align-items: start;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.summary-card__main {
  min-width: 0;
}

.summary-card__code {
  color: var(--color-primary);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.summary-card__title {
  margin-top: var(--space-2);
  font-size: var(--font-size-xl);
  font-weight: 700;
  line-height: 1.35;
}

.summary-card__desc {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.summary-card__facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
  margin: 0;
  padding-top: var(--space-3);
  border-top: 1px solid rgba(255, 255, 255, 0.52);
}

.summary-card__facts div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.summary-card__facts dt {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.summary-card__facts dd {
  margin: 0;
  font-size: var(--font-size-xl);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.weight-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.weight-card__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
}

.weight-card__subtitle {
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.weight-card__empty {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px dashed rgba(217, 119, 6, 0.4);
  border-radius: var(--radius-md);
  background: rgba(255, 247, 237, 0.64);
  color: var(--color-warning);
}

.weight-card__empty strong {
  font-size: var(--font-size-sm);
}

.weight-card__empty p {
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.skill-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.skill-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--space-3);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.3);
}

.skill-row__head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.skill-row__index {
  flex: none;
  width: 24px;
  color: var(--color-text-tertiary);
  font-family: var(--font-mono);
  font-size: 11px;
}

.skill-row__name {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2);
  min-width: 0;
}

.skill-row__name strong {
  font-size: var(--font-size-sm);
}

.skill-row__name code {
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.skill-row__weight {
  flex: none;
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.skill-row__desc {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.skill-row__bar {
  height: 7px;
  overflow: hidden;
  border-radius: var(--radius-pill);
  background: rgba(148, 163, 184, 0.2);
}

.skill-row__bar-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2563eb, #38bdf8);
}

.skill-row__level {
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.position-detail__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: var(--space-4);
  align-items: start;
}

.rules-card__header {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.rules-card__icon {
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

.rules-card__header h2 {
  font-size: var(--font-size-lg);
  font-weight: 700;
}

.rules-card__header p {
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.rules-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.rules-list li {
  padding: var(--space-3);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.3);
}

.rules-list__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
}

.rules-list__head strong {
  font-size: var(--font-size-sm);
}

.rules-list__head span {
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.rules-list li p {
  margin-top: 4px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.explain-card h2 {
  font-size: var(--font-size-lg);
  font-weight: 700;
}

.explain-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin: var(--space-4) 0 0;
  padding: 0 0 0 18px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.explain-list li {
  padding-left: 4px;
}

.explain-list strong {
  display: block;
  color: var(--color-text);
  font-size: var(--font-size-sm);
}

.explain-list span {
  display: block;
  margin-top: 2px;
  line-height: 1.65;
}

.explain-card__cta {
  margin-top: var(--space-5);
}

.position-detail__back {
  display: flex;
  justify-content: flex-start;
}

@media (max-width: 1024px) {
  .position-detail__summary,
  .position-detail__grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .summary-card__facts,
  .rules-list {
    grid-template-columns: 1fr;
  }
}
</style>
