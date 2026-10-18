<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import AppBackdrop from '@/components/common/AppBackdrop.vue'
import AppErrorBoundary from '@/components/common/AppErrorBoundary.vue'
import { useAuthStore } from '@/stores/auth'
import { useReportStore } from '@/stores/report'
import { useUiStore } from '@/stores/ui'
import type { Role } from '@/types/auth'
import { onForbidden, onGlobalError } from '@/utils/appEvents'
import { onAuthExpired } from '@/utils/authSession'
import { STORAGE_KEYS } from '@/utils/constants'
import { storageKey } from '@/utils/storage'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()
const reportStore = useReportStore()

/** 全局 401/403/错误与跨标签页同步（计划 §4.4、§6.2.4、§7.1）。 */
function syncOffline(): void {
  ui.setOffline(!navigator.onLine)
}

function redirectToLogin(): void {
  const current = route.fullPath
  auth.clear()
  if (route.name === 'login') return
  void router.replace({
    name: 'login',
    query: current && current !== '/' ? { redirect: current } : undefined,
  })
}

function onStorage(event: StorageEvent): void {
  if (event.key !== storageKey(STORAGE_KEYS.auth)) return
  if (!event.newValue) {
    redirectToLogin()
    return
  }
  const restored = auth.syncFromStorage()
  if (!restored || !auth.isAuthenticated) {
    redirectToLogin()
    return
  }
  // 他页换了角色或账号：当前页面若无权访问，回到对应首页
  const roles = route.meta.roles as Role[] | undefined
  if (roles && auth.user && !roles.includes(auth.user.role)) {
    void router.replace(auth.homePath)
  }
}

// 报告缓存可能含上一账号的会话；登出/切换账号立即清空（计划 §6.8 隐私要求）。
watch(
  () => auth.token,
  (token, previous) => {
    if (!token || (previous && previous !== token)) reportStore.clearAll()
  },
)

const offAuthExpired = onAuthExpired(redirectToLogin)
const offForbidden = onForbidden(() => {
  if (route.name !== 'forbidden') void router.replace({ name: 'forbidden' })
})
const offGlobalError = onGlobalError((detail) => {
  ElMessage({
    type: 'error',
    message: detail.traceId ? `${detail.message}（traceId: ${detail.traceId}）` : detail.message,
    duration: 5000,
    showClose: true,
  })
})

onMounted(() => {
  syncOffline()
  window.addEventListener('online', syncOffline)
  window.addEventListener('offline', syncOffline)
  window.addEventListener('storage', onStorage)
})

onBeforeUnmount(() => {
  offAuthExpired()
  offForbidden()
  offGlobalError()
  window.removeEventListener('online', syncOffline)
  window.removeEventListener('offline', syncOffline)
  window.removeEventListener('storage', onStorage)
})
</script>

<template>
  <el-config-provider :locale="zhCn">
    <div class="app-root">
      <AppBackdrop />

      <div class="app-content">
        <transition name="offline-slide">
          <div v-if="ui.globalOffline" class="offline-bar" role="status" aria-live="polite">
            <el-icon><WarningFilled /></el-icon>
            <span>网络已断开，部分功能不可用；已填写的内容请勿关闭页面。</span>
          </div>
        </transition>

        <transition name="fade">
          <div v-if="ui.routeLoading" class="route-progress" aria-hidden="true">
            <span class="route-progress__bar" />
          </div>
        </transition>

        <AppErrorBoundary>
          <router-view />
        </AppErrorBoundary>
      </div>
    </div>
  </el-config-provider>
</template>

<style scoped>
.app-root {
  position: relative;
  isolation: isolate;
  min-height: 100vh;
  min-height: 100dvh;
}

.app-content {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  min-height: 100dvh;
}

.offline-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-height: 36px;
  padding: 6px var(--space-4);
  background: rgba(146, 64, 14, 0.88);
  border-bottom: 1px solid rgba(255, 255, 255, 0.22);
  box-shadow: 0 8px 24px rgba(120, 53, 15, 0.18);
  color: #fff;
  font-size: var(--font-size-sm);
  text-align: center;
  backdrop-filter: blur(12px) saturate(150%);
  -webkit-backdrop-filter: blur(12px) saturate(150%);
  z-index: var(--z-offline-bar);
}

.route-progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: transparent;
  z-index: var(--z-route-progress);
  pointer-events: none;
}

.route-progress__bar {
  display: block;
  width: 40%;
  height: 100%;
  border-radius: var(--radius-pill);
  background: linear-gradient(90deg, var(--color-primary), #60a5fa, #a78bfa);
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.5);
  animation: route-progress 0.9s ease-in-out infinite;
}

.offline-slide-enter-active,
.offline-slide-leave-active {
  transition:
    transform var(--transition-base),
    opacity var(--transition-base);
}

.offline-slide-enter-from,
.offline-slide-leave-to {
  opacity: 0;
  transform: translateY(-100%);
}

@keyframes route-progress {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(320%);
  }
}
</style>
