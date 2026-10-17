<script setup lang="ts">
import { ref } from 'vue'
import { CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  traceId: string
  label?: string
}>()

const copied = ref(false)

async function writeClipboard(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    // 剪贴板权限被拒时走兜底
  }
  try {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.setAttribute('readonly', '')
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(textarea)
    return ok
  } catch {
    return false
  }
}

async function copyTraceId(): Promise<void> {
  const ok = await writeClipboard(props.traceId)
  if (!ok) {
    ElMessage.warning('复制失败，请手动选中 traceId')
    return
  }
  copied.value = true
  ElMessage.success('traceId 已复制，可反馈给管理员排查')
  window.setTimeout(() => {
    copied.value = false
  }, 2000)
}
</script>

<template>
  <span class="trace-id" :title="traceId">
    <span class="trace-id__label">{{ label ?? 'traceId' }}</span>
    <code class="trace-id__value">{{ traceId }}</code>
    <el-button
      link
      type="primary"
      size="small"
      class="trace-id__copy"
      aria-label="复制 traceId"
      @click.stop="copyTraceId"
    >
      <el-icon><CopyDocument /></el-icon>
      {{ copied ? '已复制' : '复制' }}
    </el-button>
  </span>
</template>

<style scoped>
.trace-id {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 2px 8px;
  border: 1px dashed rgba(255, 255, 255, 0.8);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.46);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.6) inset;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.8;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.trace-id__label {
  flex: none;
  font-weight: 600;
}

.trace-id__value {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text);
}

.trace-id__copy {
  flex: none;
  font-size: 12px;
}
</style>
