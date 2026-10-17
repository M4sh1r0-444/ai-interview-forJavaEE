<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Compass } from '@element-plus/icons-vue'

import GlassPanel from '@/components/common/GlassPanel.vue'

import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

async function goHome(): Promise<void> {
  await router.replace(auth.isAuthenticated ? auth.homePath : { name: 'login' })
}
</script>

<template>
  <div class="error-page">
    <GlassPanel tone="strong" class="error-page__card" :padded="false">
      <div class="error-page__icon" aria-hidden="true">
        <el-icon :size="34"><Compass /></el-icon>
      </div>
      <p class="error-page__code">404</p>
      <h1 class="error-page__title">页面或面试记录不存在</h1>
      <p class="error-page__description">
        你访问的地址可能已被移除、拼写有误，或该记录不属于当前账号。请检查链接后重试。
      </p>
      <div class="error-page__actions">
        <el-button type="primary" size="large" @click="goHome">返回首页</el-button>
        <el-button size="large" @click="router.back()">返回上一页</el-button>
      </div>
      <p class="error-page__footer">AI 模拟面试与能力提升平台 · 404 Not Found</p>
    </GlassPanel>
  </div>
</template>

<style scoped>
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: var(--space-5);
  background: transparent;
}

.error-page__card {
  width: 100%;
  max-width: 560px;
  padding: var(--space-7) var(--space-6);
  text-align: center;
}

.error-page__icon {
  display: grid;
  place-items: center;
  width: 68px;
  height: 68px;
  margin: 0 auto var(--space-4);
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.2), rgba(14, 165, 233, 0.1));
  box-shadow:
    0 10px 26px rgba(37, 99, 235, 0.14),
    0 1px 0 rgba(255, 255, 255, 0.8) inset;
  color: var(--color-primary);
}

.error-page__code {
  color: var(--color-text-secondary);
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  letter-spacing: 0.22em;
}

.error-page__title {
  margin-top: var(--space-2);
  font-size: var(--font-size-xl);
  font-weight: 700;
  line-height: 1.4;
}

.error-page__description {
  margin-top: var(--space-3);
  color: var(--color-text-secondary);
}

.error-page__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-3);
  margin-top: var(--space-5);
}

.error-page__footer {
  margin-top: var(--space-6);
  color: var(--color-text-secondary);
  font-size: 11px;
}
</style>
