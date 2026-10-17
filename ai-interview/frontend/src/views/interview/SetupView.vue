<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowRight,
  Headset,
  InfoFilled,
  Microphone,
  Refresh,
  WarningFilled,
} from '@element-plus/icons-vue'

import { errorMessage, isBizError } from '@/api/http'
import { createInterview } from '@/api/interview'
import { getPosition, getPositionSkills, listPositions } from '@/api/position'
import EmptyState from '@/components/common/EmptyState.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import { useInterviewCapabilities } from '@/composables/useInterviewCapabilities'
import type { CreateInterviewPayload, InterviewMode } from '@/types/interview'
import type { PositionVO, SkillVO } from '@/types/position'
import { INTERVIEW_LIMITS, INTERVIEW_MODE_LABELS } from '@/utils/constants'
import { formatPercent } from '@/utils/format'

interface SetupForm {
  positionId: number | null
  mode: InterviewMode
  difficulty: number
  rounds: number
  durationMinutes: number
}

const route = useRoute()
const router = useRouter()
const capabilities = useInterviewCapabilities()

const positions = ref<PositionVO[]>([])
const position = ref<PositionVO | null>(null)
const skills = ref<SkillVO[]>([])
const loading = ref(true)
const positionLoading = ref(false)
const submitting = ref(false)
const loadError = ref('')
const positionError = ref('')
const formError = ref('')
const traceId = ref('')
const roundsAdjusted = ref(false)

const form = reactive<SetupForm>({
  positionId: null,
  mode: 'TEXT',
  difficulty: 3,
  rounds: 6,
  durationMinutes: 15,
})

const difficultyMarks = {
  1: '入门',
  2: '基础',
  3: '中等',
  4: '进阶',
  5: '挑战',
}

const difficultyLabels = ['入门', '基础', '中等', '进阶', '挑战']
const roundOptions = [5, 6, 7, 8]
const durationOptions = [15, 30, 45, 60]

const difficultyLabel = computed(
  () => difficultyLabels[Math.min(5, Math.max(1, form.difficulty)) - 1],
)
const modeLabel = computed(() => INTERVIEW_MODE_LABELS[form.mode])
const audioDisabled = computed(() => !capabilities.audioSupported)
const canStart = computed(
  () => !loading.value && !positionLoading.value && !!position.value && skills.value.length > 0,
)
const defaultRoundsLabel = computed(() => position.value?.defaultRounds ?? 6)
const weightTotal = computed(() =>
  skills.value.reduce((sum, skill) => sum + (skill.weight || 0), 0),
)

const queryPositionId = computed(() => {
  const raw = route.query.positionId
  const parsed = typeof raw === 'string' ? Number(raw) : Number.NaN
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})

/** 推荐页"按建议开始下一场"带过来的建议难度（1~5）；非法值忽略。 */
const queryDifficulty = computed(() => {
  const raw = route.query.difficulty
  const parsed = typeof raw === 'string' ? Number(raw) : Number.NaN
  return Number.isInteger(parsed) && parsed >= 1 && parsed <= 5 ? parsed : null
})

function clampRounds(value: number): number {
  const safe = Number.isFinite(value) ? Math.round(value) : INTERVIEW_LIMITS.minRounds
  return Math.min(INTERVIEW_LIMITS.maxRounds, Math.max(INTERVIEW_LIMITS.minRounds, safe))
}

function selectMode(mode: InterviewMode): void {
  if (mode === 'AUDIO' && audioDisabled.value) return
  form.mode = mode
  formError.value = ''
}

async function loadPosition(id: number, options: { syncRoute?: boolean } = {}): Promise<void> {
  positionLoading.value = true
  positionError.value = ''
  formError.value = ''
  try {
    const [nextPosition, nextSkills] = await Promise.all([getPosition(id), getPositionSkills(id)])
    position.value = nextPosition
    skills.value = nextSkills
    form.positionId = nextPosition.id

    const clamped = clampRounds(nextPosition.defaultRounds)
    form.rounds = clamped
    roundsAdjusted.value = clamped !== nextPosition.defaultRounds
    if (queryDifficulty.value != null) form.difficulty = queryDifficulty.value

    if (form.mode === 'AUDIO' && audioDisabled.value) {
      form.mode = 'TEXT'
    }
    if (skills.value.length === 0) {
      positionError.value = '该岗位能力模型尚未配置技能，暂时无法开始面试'
    }
    if (options.syncRoute) {
      await router.replace({
        name: 'interview-setup',
        query: { positionId: String(nextPosition.id) },
      })
    }
  } catch (error) {
    position.value = null
    skills.value = []
    form.positionId = null
    positionError.value = errorMessage(error, '岗位信息加载失败')
    traceId.value = isBizError(error) ? (error.traceId ?? '') : ''
  } finally {
    positionLoading.value = false
  }
}

async function handlePositionChange(value: number | null): Promise<void> {
  if (value == null || value === position.value?.id) return
  await loadPosition(value, { syncRoute: true })
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    positions.value = await listPositions()
  } catch (error) {
    positions.value = []
    position.value = null
    skills.value = []
    loadError.value = errorMessage(error, '岗位列表加载失败')
    loading.value = false
    return
  }

  if (positions.value.length === 0) {
    position.value = null
    skills.value = []
    loading.value = false
    return
  }

  const requested = queryPositionId.value
  const selected =
    positions.value.find((item) => item.id === requested) ?? positions.value[0] ?? null
  if (!selected) {
    loading.value = false
    return
  }
  if (requested == null || requested !== selected.id) {
    await router.replace({
      name: 'interview-setup',
      query: { positionId: String(selected.id) },
    })
  }
  await loadPosition(selected.id)
  loading.value = false
}

function normalizeForm(): boolean {
  formError.value = ''
  if (form.positionId == null || !position.value) {
    formError.value = '请先选择面试岗位'
    return false
  }
  if (!Number.isInteger(form.difficulty) || form.difficulty < 1 || form.difficulty > 5) {
    formError.value = '难度必须在 1~5 之间'
    return false
  }
  const rounds = clampRounds(form.rounds)
  if (rounds !== form.rounds) {
    form.rounds = rounds
  }
  if (!durationOptions.includes(form.durationMinutes)) {
    formError.value = '面试时长必须为 15 / 30 / 45 / 60 分钟'
    return false
  }
  if (form.mode === 'AUDIO' && audioDisabled.value) {
    formError.value = capabilities.audioDisabledReason || '当前浏览器不支持语音面试'
    return false
  }
  if (skills.value.length === 0) {
    formError.value = '岗位能力模型为空，暂时无法开始面试'
    return false
  }
  return true
}

async function handleSubmit(): Promise<void> {
  if (submitting.value || !normalizeForm()) return
  submitting.value = true
  formError.value = ''
  traceId.value = ''
  const payload: CreateInterviewPayload = {
    positionId: form.positionId as number,
    mode: form.mode,
    difficulty: form.difficulty,
    rounds: form.rounds,
    durationMinutes: form.durationMinutes,
  }
  try {
    const detail = await createInterview(payload)
    await router.push({
      name: 'interview-room',
      params: { id: String(detail.id) },
      state: { autoStart: true },
    })
  } catch (error) {
    formError.value = errorMessage(error, '创建面试失败，请稍后重试')
    traceId.value = isBizError(error) ? (error.traceId ?? '') : ''
    if (isBizError(error) && error.code === 40401) {
      positionError.value = '岗位不存在或已停用，请重新选择'
    }
  } finally {
    submitting.value = false
  }
}

function retry(): void {
  if (position.value) {
    void loadPosition(position.value.id)
  } else {
    void load()
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="setup">
    <header class="setup__header">
      <div>
        <p class="setup__eyebrow">INTERVIEW SETUP</p>
        <h1 class="setup__title">配置本场面试</h1>
        <p class="setup__subtitle">
          选择作答方式、难度、轮次与时长。创建后会立即进入房间并自动开始，中途刷新可恢复。
        </p>
      </div>
      <el-button :icon="Refresh" :loading="loading || positionLoading" @click="retry">
        刷新
      </el-button>
    </header>

    <LoadingSkeleton v-if="loading" type="card" :rows="4" />

    <GlassPanel v-else-if="loadError" :padded="false">
      <EmptyState
        title="岗位列表加载失败"
        :description="loadError"
        action-text="重试"
        @action="load"
      />
    </GlassPanel>

    <GlassPanel v-else-if="positions.length === 0" :padded="false">
      <EmptyState
        title="暂无可面试岗位"
        description="管理员启用岗位后即可开始配置面试。"
        action-text="返回首页"
        @action="router.push('/home')"
      />
    </GlassPanel>

    <div v-else class="setup__layout">
      <div class="setup__main">
        <GlassPanel class="section">
          <header class="section__header">
            <span class="section__index">01</span>
            <div>
              <h2 class="section__title">选择岗位</h2>
              <p class="section__subtitle">岗位决定能力模型、组卷范围与评分权重。</p>
            </div>
          </header>

          <div class="position-select">
            <el-select
              v-model="form.positionId"
              placeholder="请选择岗位"
              size="large"
              :loading="positionLoading"
              class="position-select__select"
              data-testid="setup-position"
              @change="handlePositionChange"
            >
              <el-option
                v-for="item in positions"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
            <el-tag v-if="position" effect="plain" round>{{ position.code }}</el-tag>
          </div>

          <div v-if="positionLoading" class="section__loading">正在加载能力模型…</div>

          <div v-else-if="positionError" class="section__error" role="alert">
            <span>{{ positionError }}</span>
            <el-button text type="primary" @click="retry">重试</el-button>
          </div>

          <div v-else-if="position" class="position-meta">
            <p class="position-meta__desc">{{ position.description || '该岗位暂无描述。' }}</p>
            <div class="position-meta__chips">
              <span>默认 {{ defaultRoundsLabel }} 轮</span>
              <span>{{ skills.length }} 项技能</span>
              <span>权重合计 {{ formatPercent(weightTotal) }}</span>
            </div>
            <ul class="skill-chips" aria-label="岗位技能">
              <li v-for="skill in skills" :key="skill.skillCode">
                {{ skill.skillName }}
                <small>{{ formatPercent(skill.weight) }}</small>
              </li>
            </ul>
          </div>
        </GlassPanel>

        <GlassPanel class="section">
          <header class="section__header">
            <span class="section__index">02</span>
            <div>
              <h2 class="section__title">作答方式</h2>
              <p class="section__subtitle">文字模式最稳定；语音模式在支持的浏览器上可用。</p>
            </div>
          </header>

          <div class="mode-grid" role="radiogroup" aria-label="作答方式">
            <button
              type="button"
              class="mode-card"
              :class="{ 'mode-card--active': form.mode === 'TEXT' }"
              role="radio"
              :aria-checked="form.mode === 'TEXT'"
              data-testid="mode-text"
              @click="selectMode('TEXT')"
            >
              <span class="mode-card__icon"
                ><el-icon :size="20"><InfoFilled /></el-icon
              ></span>
              <strong>文字面试</strong>
              <span>键盘作答，稳定可复制，支持草稿与快捷键提交。</span>
              <em>推荐</em>
            </button>

            <button
              type="button"
              class="mode-card"
              :class="{
                'mode-card--active': form.mode === 'AUDIO',
                'mode-card--disabled': audioDisabled,
              }"
              role="radio"
              :aria-checked="form.mode === 'AUDIO'"
              :aria-disabled="audioDisabled"
              data-testid="mode-audio"
              @click="selectMode('AUDIO')"
            >
              <span class="mode-card__icon"
                ><el-icon :size="20"><Microphone /></el-icon
              ></span>
              <strong>语音面试</strong>
              <span>
                {{
                  audioDisabled
                    ? capabilities.audioDisabledReason
                    : '录音 + 浏览器转写，报告额外采集语音表现维度。'
                }}
              </span>
              <em v-if="audioDisabled">不可用</em>
              <em v-else-if="!capabilities.speechSupported">可录音 · 手工转写</em>
              <em v-else>可用</em>
            </button>
          </div>

          <p v-if="audioDisabled" class="capability-note capability-note--warning">
            <el-icon><WarningFilled /></el-icon>
            <span>{{ capabilities.audioDisabledReason }}；本场请选择文字面试。</span>
          </p>
          <p
            v-else-if="!capabilities.speechSupported"
            class="capability-note capability-note--info"
          >
            <el-icon><InfoFilled /></el-icon>
            <span>{{ capabilities.speechHint }}。录音与语音作答界面将在 F4 完整接入。</span>
          </p>
          <p v-else class="capability-note capability-note--success">
            <el-icon><Headset /></el-icon>
            <span>{{ capabilities.speechHint }}。录音与语音作答界面将在 F4 完整接入。</span>
          </p>
        </GlassPanel>

        <GlassPanel class="section">
          <header class="section__header">
            <span class="section__index">03</span>
            <div>
              <h2 class="section__title">难度与规模</h2>
              <p class="section__subtitle">主问题难度在组卷时锁定；追问会按回答质量自适应。</p>
            </div>
          </header>

          <div class="field">
            <div class="field__head">
              <label for="difficulty">难度档位</label>
              <strong>{{ form.difficulty }} · {{ difficultyLabel }}</strong>
            </div>
            <el-slider
              id="difficulty"
              v-model="form.difficulty"
              :min="1"
              :max="5"
              :step="1"
              show-stops
              :marks="difficultyMarks"
              data-testid="setup-difficulty"
            />
          </div>

          <div class="field-grid">
            <div class="field">
              <div class="field__head">
                <label>主问题轮次</label>
                <strong>{{ form.rounds }} 轮</strong>
              </div>
              <el-radio-group v-model="form.rounds" class="option-group" data-testid="setup-rounds">
                <el-radio-button
                  v-for="round in roundOptions"
                  :key="round"
                  :value="round"
                  :label="String(round)"
                >
                  {{ round }}
                </el-radio-button>
              </el-radio-group>
              <p class="field__hint">
                固定 5~8 轮；当前岗位默认 {{ defaultRoundsLabel }} 轮<span v-if="roundsAdjusted">
                  ，已按约束调整</span
                >。
              </p>
            </div>

            <div class="field">
              <div class="field__head">
                <label>面试时长</label>
                <strong>{{ form.durationMinutes }} 分钟</strong>
              </div>
              <el-radio-group
                v-model="form.durationMinutes"
                class="option-group"
                data-testid="setup-duration"
              >
                <el-radio-button
                  v-for="duration in durationOptions"
                  :key="duration"
                  :value="duration"
                  :label="String(duration)"
                >
                  {{ duration }}
                </el-radio-button>
              </el-radio-group>
              <p class="field__hint">倒计时结束会自动收卷并生成报告。</p>
            </div>
          </div>
        </GlassPanel>
      </div>

      <aside class="setup__aside">
        <GlassPanel tone="strong" class="summary">
          <header class="summary__header">
            <span class="summary__eyebrow">本场概览</span>
            <h2>{{ position?.name || '未选择岗位' }}</h2>
            <p>{{ position?.code || '请选择岗位' }}</p>
          </header>

          <dl class="summary__list">
            <div>
              <dt>作答方式</dt>
              <dd>{{ modeLabel }}</dd>
            </div>
            <div>
              <dt>难度</dt>
              <dd>{{ form.difficulty }} · {{ difficultyLabel }}</dd>
            </div>
            <div>
              <dt>轮次</dt>
              <dd>{{ form.rounds }} 轮</dd>
            </div>
            <div>
              <dt>时长</dt>
              <dd>{{ form.durationMinutes }} 分钟</dd>
            </div>
            <div>
              <dt>技能覆盖</dt>
              <dd>{{ skills.length }} 项</dd>
            </div>
          </dl>

          <div v-if="formError" class="summary__error" role="alert">{{ formError }}</div>
          <p v-if="traceId" class="summary__trace">traceId：{{ traceId }}</p>

          <el-button
            type="primary"
            size="large"
            class="summary__submit"
            :loading="submitting"
            :disabled="!canStart || submitting"
            data-testid="setup-submit"
            @click="handleSubmit"
          >
            {{ submitting ? '正在创建…' : '创建并进入房间' }}
            <el-icon v-if="!submitting"><ArrowRight /></el-icon>
          </el-button>

          <p class="summary__note">
            创建后房间会自动开始并显示首题；如果开始失败，可在房间内重试或中止。
          </p>
        </GlassPanel>

        <GlassPanel class="aside-tip">
          <h3>作答建议</h3>
          <ul>
            <li>先给结论，再讲依据与取舍，方便评分引擎识别得分点。</li>
            <li>回答越具体，越容易命中岗位技能关键词与证据句。</li>
            <li>每轮最多 3 次追问，追问不增加主问题轮次。</li>
          </ul>
        </GlassPanel>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.setup {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding-bottom: var(--space-6);
}

.setup__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.setup__eyebrow {
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.16em;
}

.setup__title {
  margin-top: var(--space-2);
  font-size: var(--font-size-2xl);
  font-weight: 700;
}

.setup__subtitle {
  max-width: 760px;
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
}

.setup__layout {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(300px, 0.75fr);
  gap: var(--space-4);
  align-items: start;
}

.setup__main {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.section__header {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.section__index {
  display: grid;
  flex: none;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 11px;
  background: rgba(37, 99, 235, 0.1);
  color: var(--color-primary);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
}

.section__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
}

.section__subtitle {
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.section__loading {
  padding: var(--space-4);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.section__error {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-3);
  border: 1px solid rgba(220, 38, 38, 0.22);
  border-radius: var(--radius-md);
  background: rgba(254, 242, 242, 0.72);
  color: var(--color-danger);
  font-size: var(--font-size-sm);
}

.position-select {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.position-select__select {
  width: min(360px, 100%);
}

.position-meta {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.position-meta__desc {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.position-meta__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.position-meta__chips span {
  padding: 3px 9px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.38);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.skill-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.skill-chips li {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  padding: 4px 9px;
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: var(--radius-pill);
  background: rgba(37, 99, 235, 0.07);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.skill-chips small {
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.mode-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.mode-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: var(--space-4);
  border: 1px solid var(--glass-border);
  border-radius: var(--glass-radius);
  background: rgba(255, 255, 255, 0.34);
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--transition-fast),
    background var(--transition-fast),
    transform var(--transition-fast);
}

.mode-card:hover:not(.mode-card--disabled) {
  transform: translateY(-1px);
  border-color: rgba(37, 99, 235, 0.32);
}

.mode-card--active {
  border-color: rgba(37, 99, 235, 0.52);
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.12), rgba(14, 165, 233, 0.05));
  box-shadow: 0 0 0 1px rgba(37, 99, 235, 0.16) inset;
}

.mode-card--disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.mode-card__icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 11px;
  background: rgba(37, 99, 235, 0.12);
  color: var(--color-primary);
}

.mode-card strong {
  font-size: var(--font-size-sm);
}

.mode-card span:not(.mode-card__icon) {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.mode-card em {
  margin-top: 2px;
  color: var(--color-primary);
  font-size: 11px;
  font-style: normal;
  font-weight: 600;
}

.mode-card--disabled em {
  color: var(--color-warning);
}

.capability-note {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  margin-top: var(--space-3);
  padding: var(--space-3);
  border: 1px solid rgba(255, 255, 255, 0.52);
  border-radius: var(--radius-md);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.capability-note--warning {
  border-color: rgba(217, 119, 6, 0.3);
  background: rgba(255, 247, 237, 0.7);
  color: var(--color-warning);
}

.capability-note--info {
  background: rgba(239, 246, 255, 0.64);
  color: var(--color-text-secondary);
}

.capability-note--success {
  border-color: rgba(22, 163, 74, 0.24);
  background: rgba(240, 253, 244, 0.64);
  color: var(--color-success);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.field + .field,
.field-grid {
  margin-top: var(--space-4);
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.field__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}

.field__head label {
  color: var(--color-text);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.field__head strong {
  color: var(--color-primary);
  font-size: var(--font-size-sm);
}

.field__hint {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.option-group {
  flex-wrap: wrap;
}

.setup__aside {
  position: sticky;
  top: calc(var(--topbar-height) + var(--space-4));
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.summary__header h2 {
  margin-top: var(--space-1);
  font-size: var(--font-size-lg);
  font-weight: 700;
}

.summary__header p {
  color: var(--color-text-tertiary);
  font-family: var(--font-mono);
  font-size: 11px;
}

.summary__eyebrow {
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.12em;
}

.summary__list {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin: var(--space-4) 0;
}

.summary__list div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 9px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.46);
}

.summary__list dt {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.summary__list dd {
  margin: 0;
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.summary__error {
  padding: var(--space-3);
  border: 1px solid rgba(220, 38, 38, 0.24);
  border-radius: var(--radius-md);
  background: rgba(254, 242, 242, 0.72);
  color: var(--color-danger);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.summary__trace {
  margin-top: 6px;
  color: var(--color-text-tertiary);
  font-size: 11px;
  word-break: break-all;
}

.summary__submit {
  width: 100%;
  margin-top: var(--space-2);
}

.summary__note {
  margin-top: var(--space-3);
  color: var(--color-text-tertiary);
  font-size: 11px;
  line-height: 1.6;
}

.aside-tip h3 {
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.aside-tip ul {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: var(--space-2) 0 0;
  padding-left: 18px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.65;
}

@media (max-width: 1080px) {
  .setup__layout {
    grid-template-columns: 1fr;
  }

  .setup__aside {
    position: static;
  }
}

@media (max-width: 720px) {
  .setup__header {
    flex-direction: column;
  }

  .mode-grid,
  .field-grid {
    grid-template-columns: 1fr;
  }
}
</style>
