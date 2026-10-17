<script setup lang="ts">
import { computed } from 'vue'

import type { InterviewStatus } from '@/types/interview'
import { INTERVIEW_STATUS_LABELS } from '@/utils/constants'

const props = withDefaults(
  defineProps<{
    status: InterviewStatus
    size?: 'small' | 'default' | 'large'
  }>(),
  { size: 'default' },
)

const tagType = computed(() => {
  switch (props.status) {
    case 'WAITING_USER':
      return 'primary'
    case 'PROCESSING_AI':
    case 'RUNNING':
      return 'warning'
    case 'FINISHED':
      return 'success'
    case 'ABORTED':
    case 'TIMEOUT':
      return 'danger'
    default:
      return 'info'
  }
})

const label = computed(() => INTERVIEW_STATUS_LABELS[props.status] ?? props.status)
</script>

<template>
  <el-tag
    :type="tagType"
    :size="size"
    effect="light"
    round
    class="status-tag"
    data-testid="status-tag"
  >
    <span class="status-tag__dot" aria-hidden="true" />
    {{ label }}
  </el-tag>
</template>

<style scoped>
.status-tag {
  --el-tag-border-radius: var(--radius-pill);
  gap: 4px;
}

.status-tag__dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 4px;
  border-radius: 50%;
  background: currentColor;
  vertical-align: middle;
}
</style>
