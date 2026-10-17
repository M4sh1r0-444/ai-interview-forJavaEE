<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Headset, Microphone } from '@element-plus/icons-vue'

import type { InterviewMode } from '@/types/interview'
import type { DimensionKey, QuestionEvidenceVO } from '@/types/report'
import { dimensionEntries, formatDimensionScore, evidenceTitle } from '@/utils/report'
import { scoreTone } from '@/utils/score'

const props = withDefaults(
  defineProps<{
    items?: QuestionEvidenceVO[]
    mode?: InterviewMode | null
    skillNames?: Record<string, string>
    replayableRounds?: number[]
    playingRound?: number | null
    loadingRound?: number | null
    replayError?: string
  }>(),
  {
    items: () => [],
    mode: null,
    skillNames: () => ({}),
    replayableRounds: () => [],
    playingRound: null,
    loadingRound: null,
    replayError: '',
  },
)

defineEmits<{
  replay: [roundNo: number]
}>()

const sortedItems = computed(() =>
  [...props.items].sort((left, right) => left.roundNo - right.roundNo),
)
const openPanels = ref<string[]>([])

watch(
  sortedItems,
  (items) => {
    if (items.length === 0) {
      openPanels.value = []
      return
    }
    // 默认展开首题；后续轮询/切报告只在当前无展开项时补齐，不打断用户操作。
    if (openPanels.value.length === 0) {
      openPanels.value = [String(items[0].roundNo)]
    }
  },
  { immediate: true },
)

function skillsOf(item: QuestionEvidenceVO): string[] {
  return (item.targetSkills ?? []).map((code) => props.skillNames[code] ?? code)
}

function dimensionsOf(item: QuestionEvidenceVO): ReturnType<typeof dimensionEntries> {
  const scores = item.scores as Partial<Record<DimensionKey, number>> | null | undefined
  return dimensionEntries(scores, props.mode)
}

function canReplay(item: QuestionEvidenceVO): boolean {
  return props.replayableRounds.includes(item.roundNo)
}

function replayLabel(item: QuestionEvidenceVO): string {
  if (props.loadingRound === item.roundNo) return '加载音频…'
  if (props.playingRound === item.roundNo) return '回放中…'
  return '回放'
}
</script>

<template>
  <div class="evidence" data-testid="evidence-accordion">
    <div v-if="sortedItems.length === 0" class="evidence__empty">
      本场没有可展示的逐题证据（可能提前结束或未产生有效作答）。
    </div>

    <el-collapse v-else v-model="openPanels" class="evidence__collapse">
      <el-collapse-item
        v-for="item in sortedItems"
        :key="item.roundNo"
        :name="String(item.roundNo)"
        :data-testid="`evidence-${item.roundNo}`"
      >
        <template #title>
          <div class="evidence__title">
            <span class="evidence__round">{{ evidenceTitle(item.roundNo) }}</span>
            <el-tag v-if="item.difficulty" size="small" effect="plain" round>
              难度 {{ item.difficulty }}
            </el-tag>
            <el-tag
              size="small"
              effect="plain"
              round
              :type="scoreTone(item.contentScore)"
              class="evidence__score-tag"
            >
              内容分 {{ formatDimensionScore(item.contentScore) }}
            </el-tag>
            <span class="evidence__hit">
              得分点 {{ item.coveredPoints?.length ?? 0 }} / 失分点
              {{ item.missingPoints?.length ?? 0 }}
            </span>
          </div>
        </template>

        <div class="evidence__body">
          <section class="evidence__block">
            <h3>题目</h3>
            <p class="evidence__question">{{ item.questionContent || '—' }}</p>
            <div v-if="skillsOf(item).length" class="evidence__chips">
              <el-tag
                v-for="skill in skillsOf(item)"
                :key="skill"
                size="small"
                effect="plain"
                round
              >
                {{ skill }}
              </el-tag>
            </div>
          </section>

          <section class="evidence__block">
            <h3>回答摘要</h3>
            <p class="evidence__answer">{{ item.answerExcerpt || '—' }}</p>
          </section>

          <section class="evidence__block">
            <h3>维度得分</h3>
            <div v-if="dimensionsOf(item).length" class="evidence__dimensions">
              <span
                v-for="dimension in dimensionsOf(item)"
                :key="dimension.key"
                class="evidence__dimension"
                :class="`evidence__dimension--${scoreTone(dimension.value)}`"
              >
                {{ dimension.label }}
                <strong>{{ formatDimensionScore(dimension.value) }}</strong>
              </span>
            </div>
            <p v-else class="evidence__muted">该轮未返回维度分。</p>
          </section>

          <div class="evidence__grid">
            <section class="evidence__block evidence__block--covered">
              <h3>命中的得分点</h3>
              <ul v-if="(item.coveredPoints?.length ?? 0) > 0" class="evidence__list">
                <li v-for="point in item.coveredPoints" :key="point">{{ point }}</li>
              </ul>
              <p v-else class="evidence__muted">未命中得分点。</p>
            </section>

            <section class="evidence__block evidence__block--missing">
              <h3>未覆盖的失分点</h3>
              <ul v-if="(item.missingPoints?.length ?? 0) > 0" class="evidence__list">
                <li v-for="point in item.missingPoints" :key="point">{{ point }}</li>
              </ul>
              <p v-else class="evidence__muted">无失分点。</p>
            </section>
          </div>

          <section class="evidence__block">
            <h3>证据句</h3>
            <ul v-if="(item.evidence?.length ?? 0) > 0" class="evidence__quotes">
              <li v-for="(quote, index) in item.evidence" :key="`${item.roundNo}-${index}`">
                {{ quote }}
              </li>
            </ul>
            <p v-else class="evidence__muted">未提取到证据句。</p>
          </section>

          <section class="evidence__block">
            <h3>评分理由</h3>
            <p class="evidence__reason">{{ item.reason || '未提供评分理由。' }}</p>
          </section>

          <div class="evidence__footer">
            <el-button
              v-if="canReplay(item)"
              size="small"
              plain
              :loading="loadingRound === item.roundNo"
              :data-testid="`evidence-replay-${item.roundNo}`"
              @click="$emit('replay', item.roundNo)"
            >
              <el-icon
                ><Headset v-if="!playingRound || playingRound !== item.roundNo" /><Microphone
                  v-else
              /></el-icon>
              {{ replayLabel(item) }}
            </el-button>
            <span v-else class="evidence__muted">本题无录音回放</span>
          </div>
        </div>
      </el-collapse-item>
    </el-collapse>

    <p v-if="replayError" class="evidence__replay-error" role="alert">{{ replayError }}</p>
  </div>
</template>

<style scoped>
.evidence {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.evidence__empty {
  padding: var(--space-5);
  border: 1px dashed rgba(148, 163, 184, 0.4);
  border-radius: var(--radius-md);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
  text-align: center;
}

.evidence__collapse {
  --el-collapse-border-color: rgba(148, 163, 184, 0.22);
  --el-collapse-header-bg-color: transparent;
  --el-collapse-content-bg-color: transparent;
  border: none;
}

.evidence__collapse :deep(.el-collapse-item) {
  margin-bottom: var(--space-2);
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.3);
}

.evidence__collapse :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 54px;
  padding: 10px var(--space-4);
  border-bottom: none;
  background: transparent;
  line-height: 1.4;
}

.evidence__collapse :deep(.el-collapse-item__wrap) {
  border-bottom: none;
  background: transparent;
}

.evidence__collapse :deep(.el-collapse-item__content) {
  padding: 0 var(--space-4) var(--space-4);
}

.evidence__title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding-right: var(--space-2);
}

.evidence__round {
  color: var(--color-text);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.evidence__hit {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.evidence__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.evidence__block h3 {
  margin-bottom: 4px;
  color: var(--color-text-tertiary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.evidence__question {
  color: var(--color-text);
  font-weight: 600;
  line-height: 1.65;
  white-space: pre-wrap;
}

.evidence__answer,
.evidence__reason {
  padding: var(--space-3);
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.34);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.evidence__chips,
.evidence__dimensions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.evidence__dimension {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  padding: 3px 9px;
  border-radius: var(--radius-pill);
  background: rgba(148, 163, 184, 0.14);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.evidence__dimension strong {
  font-variant-numeric: tabular-nums;
}

.evidence__dimension--danger strong {
  color: var(--color-danger);
}

.evidence__dimension--warning strong {
  color: var(--color-warning);
}

.evidence__dimension--primary strong {
  color: var(--color-primary);
}

.evidence__dimension--success strong {
  color: var(--color-success);
}

.evidence__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.evidence__block--covered,
.evidence__block--missing {
  padding: var(--space-3);
  border-radius: var(--radius-md);
}

.evidence__block--covered {
  border: 1px solid rgba(22, 163, 74, 0.2);
  background: rgba(240, 253, 244, 0.52);
}

.evidence__block--missing {
  border: 1px solid rgba(220, 38, 38, 0.18);
  background: rgba(254, 242, 242, 0.52);
}

.evidence__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding-left: 18px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.65;
}

.evidence__block--covered .evidence__list {
  color: #15803d;
}

.evidence__block--missing .evidence__list {
  color: #b91c1c;
}

.evidence__quotes {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.evidence__quotes li {
  padding: 8px 12px;
  border-left: 3px solid rgba(37, 99, 235, 0.4);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  background: rgba(239, 246, 255, 0.56);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.65;
}

.evidence__muted {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.evidence__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
}

.evidence__replay-error {
  color: var(--color-danger);
  font-size: var(--font-size-xs);
}

@media (max-width: 768px) {
  .evidence__grid {
    grid-template-columns: 1fr;
  }

  .evidence__title {
    gap: 6px;
  }

  .evidence__hit {
    width: 100%;
  }
}

@media print {
  .evidence__collapse :deep(.el-collapse-item__content) {
    display: block !important;
  }

  .evidence__collapse :deep(.el-collapse-item__wrap) {
    display: block !important;
  }

  .evidence__collapse :deep(.el-collapse-item) {
    break-inside: avoid;
  }
}
</style>
