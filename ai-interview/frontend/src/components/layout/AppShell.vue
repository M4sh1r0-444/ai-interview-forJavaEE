<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
    /** 管理端菜单含 query（如评分权重）时，由布局显式指定高亮项。 */
    activeMenu?: string
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
const isNarrow = ref(false)
/** 视口锁定布局下，页面滚动发生在内容区而不是 window。 */
const contentEl = ref<HTMLElement | null>(null)

const pageTitle = computed(() => route.meta.title ?? '')
const activeMenu = computed(() => props.activeMenu ?? route.meta.activeMenu ?? route.path)
const menuCollapsed = computed(() => ui.sidebarCollapsed && !isMobile.value)
const avatarText = computed(() => auth.displayName.slice(0, 1).toUpperCase())
const variantLabel = computed(() => (props.variant === 'admin' ? '管理控制台' : '学生端'))

function updateViewport(): void {
  isMobile.value = window.innerWidth < 900
  isNarrow.value = window.innerWidth < 768
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
  async () => {
    mobileOpen.value = false
    // window 不再滚动，路由切换后需要手动把内容区拉回顶部。
    await nextTick()
    if (contentEl.value) contentEl.value.scrollTop = 0
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
            <button type="button" class="user-trigger" data-testid="user-trigger">
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

      <main ref="contentEl" class="content">
        <div
          v-if="variant === 'admin' && isNarrow"
          class="admin-mobile-hint"
          role="note"
          data-testid="admin-mobile-hint"
        >
          管理端建议使用桌面端（≥1024px）；表格可左右滑动查看全部字段与操作。
        </div>
        <div :class="contentClass">
          <router-view />
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
/*
 * 视口锁定布局：外层只负责留白与间距，滚动全部交给 .content。
 * 侧栏与主区各自是一块圆角玻璃面板，避免圆角与直角直接相接。
 */
.app-layout {
  display: flex;
  gap: var(--space-3);
  width: 100%;
  height: 100vh;
  height: 100dvh;
  min-height: 0;
  padding: var(--space-3);
  overflow: hidden;
  background: transparent;
}

/* ===== 侧栏 ===== */
.sidebar {
  position: relative;
  display: flex;
  flex: 0 0 var(--sidebar-width);
  flex-direction: column;
  width: var(--sidebar-width);
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--glass-border);
  border-radius: var(--glass-radius-lg);
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

/*
 * Element Plus 折叠态默认按 64px 宽的菜单项、左右 20px 内边距居中图标；
 * 面板有 1px 边框且菜单项带外边距，图标会偏右。这里改为按面板内宽重新居中。
 */
.sidebar--collapsed .sidebar__menu {
  width: 100%;
}

.sidebar--collapsed .sidebar__menu .el-menu-item {
  justify-content: center;
  padding: 0;
  margin: 2px 7px;
}

/* 折叠时 Element Plus 会把图标包进内部 trigger（无 scoped 属性），需用 :deep 命中。 */
.sidebar--collapsed .sidebar__menu :deep(.el-menu-tooltip__trigger) {
  justify-content: center;
  padding: 0;
}

.sidebar--collapsed .sidebar__menu .el-menu-item .el-icon {
  margin: 0;
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
  min-height: 0;
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--glass-border);
  border-radius: var(--glass-radius-lg);
  background: var(--glass-bg-strong);
  box-shadow: var(--glass-shadow);
  backdrop-filter: blur(var(--glass-blur-strong)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur-strong)) saturate(var(--glass-saturate));
}

.topbar {
  position: relative;
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  height: var(--topbar-height);
  padding: 0 var(--space-5) 0 var(--space-3);
  border-bottom: 1px solid var(--glass-border);
  /* 磨砂由 .main 面板统一承担，顶栏只做一层更轻的色差。 */
  background: rgba(255, 255, 255, 0.34);
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
  /*
   * 供 RoomView 等需要占满内容区的页面使用：
   * 可视高度 = 视口 - 外层上下留白 - 主面板上下边框 - 顶栏 - 内容区上下内边距。
   */
  --content-viewport-height: calc(
    100vh - var(--space-3) - var(--space-3) - var(--topbar-height) - var(--space-5) -
      var(--space-5) - 2px
  );

  position: relative;
  flex: 1;
  width: 100%;
  min-width: 0;
  min-height: 0;
  padding: var(--space-5);
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
}

@supports (height: 100dvh) {
  .content {
    --content-viewport-height: calc(
      100dvh - var(--space-3) - var(--space-3) - var(--topbar-height) - var(--space-5) -
        var(--space-5) - 2px
    );
  }
}

.sidebar-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.28);
  backdrop-filter: blur(3px);
  -webkit-backdrop-filter: blur(3px);
  z-index: calc(var(--z-sidebar) - 1);
}

.admin-mobile-hint {
  margin-bottom: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border: 1px solid rgba(217, 119, 6, 0.24);
  border-radius: var(--radius-sm);
  background: rgba(255, 251, 235, 0.72);
  color: #92400e;
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .sidebar,
  .topbar,
  .main {
    background: var(--glass-bg-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .sidebar,
  .topbar,
  .main {
    background: var(--glass-bg-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

@media (max-width: 899px) {
  .sidebar {
    position: fixed;
    top: var(--space-3);
    bottom: var(--space-3);
    left: var(--space-3);
    width: var(--sidebar-width);
    height: auto;
    flex-basis: var(--sidebar-width);
    transform: translateX(calc(-100% - var(--space-3)));
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
    --content-viewport-height: calc(
      100vh - var(--space-3) - var(--space-3) - var(--topbar-height) - var(--space-4) -
        var(--space-4) - 2px
    );

    padding: var(--space-4);
  }

  @supports (height: 100dvh) {
    .content {
      --content-viewport-height: calc(
        100dvh - var(--space-3) - var(--space-3) - var(--topbar-height) - var(--space-4) -
          var(--space-4) - 2px
      );
    }
  }
}

@media (max-width: 520px) {
  .topbar__variant,
  .topbar__divider {
    display: none;
  }
}
</style>
