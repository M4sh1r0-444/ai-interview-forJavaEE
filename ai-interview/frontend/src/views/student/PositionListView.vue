<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Briefcase, Refresh } from '@element-plus/icons-vue'

import { errorMessage } from '@/api/http'
import { listPositions } from '@/api/position'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import type { PositionVO } from '@/types/position'

const router = useRouter()

const positions = ref<PositionVO[]>([])
const loading = ref(true)
const error = ref('')
const traceId = ref('')

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  traceId.value = ''
  try {
    positions.value = await listPositions()
  } catch (err) {
    positions.value = []
    error.value = errorMessage(err, '岗位加载失败，请稍后再试')
    traceId.value = (err as { traceId?: string })?.traceId ?? ''
  } finally {
    loading.value = false
  }
}

function goDetail(id: number): void {
  void router.push({ name: 'position-detail', params: { id: String(id) } })
}

function goSetup(id: number): void {
  void router.push({ name: 'interview-setup', query: { positionId: String(id) } })
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="position-list">
    <AppPageHeader
      title="岗位选择"
      description="选择一个目标岗位，系统将按该岗位的能力模型组卷、追问与评分。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </AppPageHeader>

    <LoadingSkeleton v-if="loading" type="card" :rows="4" />

    <ErrorState
      v-else-if="error"
      title="岗位列表加载失败"
      :message="error"
      :trace-id="traceId"
      @retry="load"
    />

    <GlassPanel v-else-if="positions.length === 0" :padded="false">
      <EmptyState
        title="暂无可面试岗位"
        description="管理员启用岗位后即可在这里看到能力模型与开始面试入口。"
        action-text="刷新列表"
        @action="load"
      />
    </GlassPanel>

    <template v-else>
      <div class="position-list__grid">
        <GlassPanel
          v-for="position in positions"
          :key="position.id"
          hoverable
          class="position-card"
          :padded="false"
          :data-testid="`position-card-${position.id}`"
        >
          <div class="position-card__header">
            <span class="position-card__icon" aria-hidden="true">
              <el-icon :size="20"><Briefcase /></el-icon>
            </span>
            <div class="position-card__title">
              <span class="position-card__code">{{ position.code }}</span>
              <h2>{{ position.name }}</h2>
            </div>
            <el-tag effect="plain" round size="small">
              默认 {{ position.defaultRounds }} 轮
            </el-tag>
          </div>

          <p class="position-card__description">
            {{ position.description || '该岗位的能力模型正在配置中，可进入详情查看现有技能。' }}
          </p>

          <ul class="position-card__facts">
            <li>
              <span>面试轮次</span>
              <strong>5 ~ 8 轮（默认 {{ position.defaultRounds }}）</strong>
            </li>
            <li>
              <span>题型覆盖</span>
              <strong>技术 / 项目 / 场景 / 行为</strong>
            </li>
            <li>
              <span>评分方式</span>
              <strong>维度加权 + 逐题证据</strong>
            </li>
          </ul>

          <footer class="position-card__actions">
            <el-button
              text
              type="primary"
              :data-testid="`position-detail-${position.id}`"
              @click="goDetail(position.id)"
            >
              查看能力模型
              <el-icon><ArrowRight /></el-icon>
            </el-button>
            <el-button
              type="primary"
              :data-testid="`position-start-${position.id}`"
              @click="goSetup(position.id)"
            >
              开始面试
            </el-button>
          </footer>
        </GlassPanel>
      </div>

      <footer class="position-list__note">
        <p>
          题目覆盖技术、项目、场景、行为四类；具体题量由本场面试设置决定，追问不额外占用主问题轮次。
        </p>
      </footer>
    </template>
  </div>
</template>

<style scoped>
.position-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding-bottom: var(--space-6);
}

.position-list__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.position-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-5);
}

.position-card__header {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}

.position-card__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 42px;
  height: 42px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 13px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.18), rgba(14, 165, 233, 0.1));
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.8) inset;
  color: var(--color-primary);
}

.position-card__title {
  flex: 1;
  min-width: 0;
}

.position-card__title h2 {
  margin-top: 2px;
  font-size: var(--font-size-lg);
  font-weight: 700;
  line-height: 1.35;
}

.position-card__code {
  color: var(--color-text-tertiary);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.position-card__description {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.72;
}

.position-card__facts {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: var(--space-3);
  border: 1px solid rgba(255, 255, 255, 0.52);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.3);
  list-style: none;
}

.position-card__facts li {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  font-size: var(--font-size-xs);
}

.position-card__facts span {
  color: var(--color-text-tertiary);
}

.position-card__facts strong {
  color: var(--color-text-secondary);
  font-weight: 600;
  text-align: right;
}

.position-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-top: auto;
  padding-top: var(--space-2);
}

.position-list__note {
  padding: var(--space-4);
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--glass-radius);
  background: rgba(255, 255, 255, 0.22);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
  text-align: center;
}

@media (max-width: 900px) {
  .position-list__grid {
    grid-template-columns: 1fr;
  }
}
</style>
