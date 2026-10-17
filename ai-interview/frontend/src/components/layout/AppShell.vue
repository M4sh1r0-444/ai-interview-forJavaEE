<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, ChatDotRound, Expand, Fold } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import type { ShellMenuItem } from '@/components/layout/types'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const props = withDefaults(
  defineProps<{
    variant: 'student' | 'admin'
    menuItems: ShellMenuItem[]
    brandTitle: string
    brandSubtitle: string
    homePath: string
    contentClass?: string
  }>(),
  {
    contentClass: 'page-container',
  },
)

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const mobileOpen = ref(false)
const isMobile = ref(false)

const pageTitle = computed(() => route.meta.title ?? '')
const activeMenu = computed(() => route.meta.activeMenu ?? route.path)
const menuCollapsed = computed(() => ui.sidebarCollapsed && !isMobile.value)
const avatarText = computed(() => auth.displayName.slice(0, 1).toUpperCase())
const variantLabel = computed(() => (props.variant === 'admin' ? '管理控制台' : '学生端'))

function updateViewport(): void {
  isMobile.value = window.innerWidth < 900
  if (!isMobile.value) mobileOpen.value = false
}

onMounted(() => {
  updateViewport()
  window.addEventListener('resize', updateViewport)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateViewport)
})

watch(
  () => route.fullPath,
  () => {
    mobileOpen.value = false
  },
)

function toggleSidebar(): void {
  if (isMobile.value) {
    mobileOpen.value = !mobileOpen.value
    return
  }
  ui.toggleSidebar()
}

async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('退出后需要重新登录，确认退出当前账号？', '退出登录', {
      type: 'warning',
      confirmButtonText: '退出登录',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    })
  } catch {
    return
  }
  await auth.logout()
  ElMessage.success('已退出登录')
  await router.replace({ name: 'login' })
}

async function handleCommand(command: string): Promise<void> {
  if (command === 'profile') {
    await router.push({ name: 'profile' })
    return
  }
  if (command === 'admin') {
    await router.push({ name: 'admin-dashboard' })
    return
  }
  if (command === 'logout') {
    await handleLogout()
  }
}
</script>

<template>
  <div class="app-layout" :class="`app-layout--${variant}`">
    <aside class="sidebar" :class="{ 'sidebar--collapsed': menuCollapsed, 'is-open': mobileOpen }">
      <router-link :to="homePath" class="sidebar__brand">
        <span class="sidebar__logo" aria-hidden="true">
          <el-icon :size="20"><ChatDotRound /></el-icon>
        </span>
        <span v-if="!menuCollapsed" class="sidebar__brand-text">
          <strong>{{ brandTitle }}</strong>
          <small>{{ brandSubtitle }}</small>
        </span>
      </router-link>

      <el-menu
        :default-active="activeMenu"
        :collapse="menuCollapsed"
        :collapse-transition="false"
        router
        class="sidebar__menu"
        @select="mobileOpen = false"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.label }}</template>
        </el-menu-item>
      </el-menu>

      <div v-if="!menuCollapsed" class="sidebar__footer">
        <span class="sidebar__footer-dot" aria-hidden="true" />
        Java EE 课程设计 · v1.0
      </div>
    </aside>

    <div v-if="mobileOpen" class="sidebar-mask" @click="mobileOpen = false" />

    <div class="main">
      <header class="topbar">
        <div class="topbar__left">
          <el-button
            text
            circle
            class="topbar__toggle"
            :aria-label="menuCollapsed ? '展开侧栏' : '收起侧栏'"
            @click="toggleSidebar"
          >
            <el-icon :size="18">
              <Expand v-if="menuCollapsed || isMobile" />
              <Fold v-else />
            </el-icon>
          </el-button>
          <div class="topbar__crumb">
            <span class="topbar__variant">{{ variantLabel }}</span>
            <span class="topbar__divider" aria-hidden="true">/</span>
            <strong class="topbar__page">{{ pageTitle }}</strong>
          </div>
        </div>

        <div class="topbar__right">
          <el-tooltip
            v-if="auth.serverUnavailable"
            content="登录态已保留，但服务暂时不可达，请检查网络或后端"
            placement="bottom"
          >
            <el-tag type="warning" effect="light" class="topbar__offline-tag">服务暂不可达</el-tag>
          </el-tooltip>

          <el-dropdown trigger="click" @command="handleCommand">
            <button type="button" class="user-trigger">
              <el-avatar :size="30" class="user-trigger__avatar">{{ avatarText }}</el-avatar>
              <span class="user-trigger__name">{{ auth.displayName }}</span>
              <el-icon :size="12"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ auth.user?.username }}</el-dropdown-item>
                <el-dropdown-item command="profile" divided>个人中心</el-dropdown-item>
                <el-dropdown-item v-if="variant === 'student' && auth.isAdmin" command="admin">
                  管理控制台
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <div :class="contentClass">
          <router-view />
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
  width: 100%;
  overflow-x: hidden;
  background: transparent;
}

/* ===== 侧栏 ===== */
.sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex: 0 0 var(--sidebar-width);
  flex-direction: column;
  width: var(--sidebar-width);
  height: 100vh;
  overflow: hidden;
  border-right: 1px solid var(--glass-border);
  border-radius: 0 var(--glass-radius-lg) var(--glass-radius-lg) 0;
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.66), rgba(255, 255, 255, 0.42));
  box-shadow:
    var(--glass-shadow),
    24px 0 60px rgba(30, 64, 175, 0.06);
  backdrop-filter: blur(var(--glass-blur-strong)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur-strong)) saturate(var(--glass-saturate));
  transition:
    width var(--transition-base),
    flex-basis var(--transition-base);
  z-index: var(--z-sidebar);
}

.sidebar--collapsed {
  flex-basis: var(--sidebar-collapsed-width);
  width: var(--sidebar-collapsed-width);
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  height: var(--topbar-height);
  padding: 0 var(--space-4);
  color: var(--color-text);
  border-bottom: 1px solid rgba(255, 255, 255, 0.55);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.35) inset;
  flex: none;
}

.sidebar__brand:hover {
  color: var(--color-text);
}

.sidebar__logo {
  display: grid;
  flex: none;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid rgba(255, 255, 255, 0.42);
  border-radius: 12px;
  background: linear-gradient(135deg, #1e40af 0%, #2563eb 55%, #3b82f6 100%);
  box-shadow:
    0 6px 18px rgba(37, 99, 235, 0.32),
    0 1px 0 rgba(255, 255, 255, 0.4) inset;
  color: #fff;
}

.sidebar__brand-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.3;
}

.sidebar__brand-text strong {
  overflow: hidden;
  font-size: var(--font-size-base);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar__brand-text small {
  margin-top: 2px;
  color: var(--color-text-tertiary);
  font-size: 11px;
  letter-spacing: 0.04em;
}

.sidebar--collapsed .sidebar__brand {
  justify-content: center;
  padding: 0;
}

.sidebar__menu {
  flex: 1;
  padding-top: var(--space-3);
  overflow-x: hidden;
  overflow-y: auto;
}

.sidebar__footer {
  flex: none;
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid rgba(255, 255, 255, 0.55);
  color: var(--color-text-secondary);
  font-size: 11px;
  line-height: 1.5;
}

.sidebar__footer-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  border-radius: 50%;
  background: var(--color-success);
  vertical-align: middle;
}

/* ===== 主区 ===== */
.main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 100vh;
}

.topbar {
  position: sticky;
  top: 0;
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  height: var(--topbar-height);
  padding: 0 var(--space-5) 0 var(--space-3);
  border-bottom: 1px solid var(--glass-border);
  background: var(--glass-bg-strong);
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.55) inset,
    0 8px 28px rgba(15, 23, 42, 0.04);
  backdrop-filter: blur(var(--glass-blur-strong)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur-strong)) saturate(var(--glass-saturate));
  z-index: 900;
}

.topbar__left,
.topbar__right {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.topbar__toggle {
  flex: none;
}

.topbar__crumb {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  min-width: 0;
  font-size: var(--font-size-sm);
}

.topbar__variant {
  flex: none;
  color: var(--color-text-tertiary);
}

.topbar__divider {
  color: var(--color-border-strong);
}

.topbar__page {
  overflow: hidden;
  color: var(--color-text);
  font-size: var(--font-size-base);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar__offline-tag {
  flex: none;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 4px 6px;
  border: 1px solid transparent;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
  transition:
    background var(--transition-fast),
    border-color var(--transition-fast);
}

.user-trigger:hover {
  border-color: rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.48);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.7) inset;
}

.user-trigger__avatar {
  border: 1px solid rgba(255, 255, 255, 0.7);
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.18), rgba(14, 165, 233, 0.12));
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.8) inset;
  color: var(--color-primary);
  font-weight: 600;
}

.user-trigger__name {
  max-width: 120px;
  overflow: hidden;
  font-size: var(--font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content {
  position: relative;
  flex: 1;
  width: 100%;
  min-width: 0;
  padding: var(--space-5);
  overflow-x: hidden;
}

.sidebar-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.28);
  backdrop-filter: blur(3px);
  -webkit-backdrop-filter: blur(3px);
  z-index: calc(var(--z-sidebar) - 1);
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .sidebar,
  .topbar {
    background: var(--glass-bg-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .sidebar,
  .topbar {
    background: var(--glass-bg-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

@media (max-width: 899px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    width: var(--sidebar-width);
    flex-basis: var(--sidebar-width);
    transform: translateX(-100%);
    transition: transform var(--transition-base);
  }

  .sidebar.is-open {
    transform: translateX(0);
  }

  .sidebar--collapsed {
    width: var(--sidebar-width);
    flex-basis: var(--sidebar-width);
  }

  .topbar {
    padding-right: var(--space-4);
  }

  .user-trigger__name {
    display: none;
  }

  .content {
    padding: var(--space-4);
  }
}

@media (max-width: 520px) {
  .topbar__variant,
  .topbar__divider {
    display: none;
  }
}
</style>
