<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back, InfoFilled } from '@element-plus/icons-vue'

import { adminListPositions } from '@/api/admin'
import { errorMessage, isBizError } from '@/api/http'
import WeightsEditorPanel from '@/components/admin/WeightsEditorPanel.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import type { PositionAdminVO } from '@/types/admin'
import { parsePositiveInt } from '@/utils/admin'

/**
 * 评分权重页（F5-4，计划 §5.15）：
 * /admin/positions/:id/weights 与 /admin/evaluation 别名共用；选择岗位跳转对应路由，
 * 三组权重由 WeightsEditorPanel 统一加载/校验/保存/回读。
 */
const route = useRoute()
const router = useRouter()

const positions = ref<PositionAdminVO[]>([])
const loading = ref(true)
const error = ref<unknown>(null)

const positionId = computed(() => parsePositiveInt(route.params.id))
const errorText = computed(() => errorMessage(error.value, '岗位列表加载失败，请稍后重试'))
const errorCode = computed(() => (isBizError(error.value) ? error.value.code : undefined))
const errorTrace = computed(() => (isBizError(error.value) ? error.value.traceId : ''))
const selectedPosition = computed(
  () => positions.value.find((item) => item.id === positionId.value) ?? null,
)

async function loadPositions(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const page = await adminListPositions({ page: 1, size: 100 })
    positions.value = page.records ?? []
    if (positionId.value == null && positions.value.length > 0) {
      await router.replace({
        name: 'admin-position-weights',
        params: { id: positions.value[0].id },
      })
    }
  } catch (cause) {
    error.value = cause
    positions.value = []
  } finally {
    loading.value = false
  }
}

function selectPosition(id: number): void {
  void router.replace({ name: 'admin-position-weights', params: { id } })
}

function backToList(): void {
  void router.push({ name: 'admin-positions' })
}

onMounted(loadPositions)

watch(
  () => positions.value.length,
  () => {
    if (positionId.value == null && positions.value.length > 0) {
      void router.replace({
        name: 'admin-position-weights',
        params: { id: positions.value[0].id },
      })
    }
  },
)
</script>

<template>
  <div class="weights-page admin-page" data-testid="weights-page">
    <AppPageHeader
      title="评分权重"
      description="按岗位调整内容/表达/总分三组权重；保存后仅影响后续面试，历史报告分数不变。"
      show-back
      back-to="/admin/positions"
    >
      <template #actions>
        <el-button :icon="Back" @click="backToList">返回岗位列表</el-button>
      </template>
    </AppPageHeader>

    <div class="admin-note">
      <el-icon :size="16" class="weights-page__note-icon"><InfoFilled /></el-icon>
      <div>
        <strong>评分公式</strong>
        <div>
          内容 = technical×w + depth×w + logic×w + evidence×w + match×w；表达 =
          clarity/fluency/pace/confidence/completeness 加权；总分 = 内容×w + 表达×w + 应变×w +
          岗位匹配×w。权重和必须为 1（±0.01），缺失子维度按已出现权重归一化。
        </div>
      </div>
    </div>

    <LoadingSkeleton v-if="loading" type="card" />

    <ErrorState
      v-else-if="error"
      title="岗位列表加载失败"
      :message="errorText"
      :code="errorCode"
      :trace-id="errorTrace"
      @retry="loadPositions"
    />

    <EmptyState
      v-else-if="positions.length === 0"
      title="还没有岗位"
      description="请先在「岗位与能力模型」中创建岗位，再配置评分权重。"
      action-text="去岗位列表"
      @action="backToList"
    />

    <template v-else>
      <div v-if="selectedPosition" class="weights-page__crumb">
        <span>当前岗位</span>
        <strong>{{ selectedPosition.name }}</strong>
        <el-tag v-if="selectedPosition.enabled !== 1" type="info" size="small" effect="plain">
          已停用
        </el-tag>
      </div>

      <WeightsEditorPanel
        :position-id="positionId"
        :positions="positions"
        @select="selectPosition"
      />
    </template>
  </div>
</template>

<style scoped>
.weights-page {
  min-width: 0;
}

.weights-page__note-icon {
  flex: none;
  margin-top: 2px;
  color: var(--color-primary);
}

.weights-page__crumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.weights-page__crumb strong {
  color: var(--color-text);
  font-size: var(--font-size-sm);
}
</style>
