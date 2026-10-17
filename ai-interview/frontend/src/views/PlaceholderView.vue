<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { MagicStick } from '@element-plus/icons-vue'

import GlassPanel from '@/components/common/GlassPanel.vue'

const props = defineProps<{
  title: string
  milestone: string
  description?: string
}>()

const route = useRoute()
const path = computed(() => route.fullPath)
</script>

<template>
  <div class="placeholder">
    <AppPageHeader :title="props.title" :description="props.description" />

    <GlassPanel class="placeholder__card" :padded="false">
      <div class="placeholder__icon" aria-hidden="true">
        <el-icon :size="30"><MagicStick /></el-icon>
      </div>
      <div class="placeholder__content">
        <div class="placeholder__meta">
          <el-tag type="success" effect="light">F1 工程基座已完成</el-tag>
          <el-tag type="info" effect="plain">{{ props.milestone }} 交付</el-tag>
        </div>
        <h2 class="placeholder__title">页面骨架已就位，业务功能待后续里程碑实现</h2>
        <p class="placeholder__description">
          当前路由、布局、登录态、角色守卫与 API 基础设施已可用。该页面的真实数据与交互将在
          <strong>{{ props.milestone }}</strong>
          按前端开发计划实现。
        </p>
        <p class="placeholder__path">
          当前地址：<code>{{ path }}</code>
        </p>
      </div>
    </GlassPanel>

    <div class="placeholder__tips">
      <div class="placeholder__tip">
        <strong>F1 已交付</strong>
        <span
          >Vite + Vue3 + TS 工程、设计令牌、三种布局、Axios 拦截器、Pinia
          auth、路由守卫、登录注册、403/404。</span
        >
      </div>
      <div class="placeholder__tip">
        <strong>如何验证</strong>
        <span
          >使用 demo01 / 123456 可进入学生端；admin / Admin@123456 进入管理端；跨角色访问会跳转
          403。</span
        >
      </div>
    </div>
  </div>
</template>

<style scoped>
.placeholder {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.placeholder__card {
  display: flex;
  align-items: flex-start;
  gap: var(--space-5);
  padding: var(--space-6);
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.06), transparent 45%), var(--color-card);
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-md);
}

.placeholder__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 58px;
  height: 58px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.2), rgba(14, 165, 233, 0.1));
  box-shadow:
    0 8px 20px rgba(37, 99, 235, 0.14),
    0 1px 0 rgba(255, 255, 255, 0.8) inset;
  color: var(--color-primary);
}

.placeholder__content {
  min-width: 0;
}

.placeholder__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.placeholder__title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  line-height: 1.45;
}

.placeholder__description {
  max-width: 760px;
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
}

.placeholder__path {
  margin-top: var(--space-3);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.placeholder__path code {
  padding: 2px 8px;
  border: 1px solid rgba(255, 255, 255, 0.65);
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.5);
  color: var(--color-text);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.placeholder__tips {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: var(--space-4);
}

.placeholder__tip {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-4);
  border: 1px solid var(--glass-border);
  border-radius: var(--glass-radius);
  background: var(--glass-bg-subtle);
  box-shadow: var(--glass-shadow);
  backdrop-filter: blur(var(--glass-blur-subtle)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur-subtle)) saturate(var(--glass-saturate));
}

.placeholder__tip strong {
  color: var(--color-primary);
  font-size: var(--font-size-sm);
}

.placeholder__tip span {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

@media (max-width: 640px) {
  .placeholder__card {
    flex-direction: column;
    padding: var(--space-5);
  }
}
</style>
