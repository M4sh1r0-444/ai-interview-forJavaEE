import type { Router } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { resolveDocumentTitle } from '@/utils/constants'
import { resolveForbiddenTarget } from '@/utils/navigation'
import type { Role } from '@/types/auth'

/**
 * 全局守卫：
 * 1. 首次导航前 restore 登录态（单飞，避免与 App 启动重复请求）；
 * 2. exp 过期清 token → 登录页，携带站内 redirect；
 * 3. 已登录访问登录/注册 → 角色默认首页；
 * 4. 角色不匹配 → 403；管理员访问学生路由 → 管理端首页（profile 例外）；
 * 5. 管理员误入学生区时不暴露 403，直接回管理端。
 */
export function setupGuards(router: Router): void {
  router.beforeEach(async (to) => {
    const ui = useUiStore()
    ui.startRouteLoading()

    const auth = useAuthStore()
    if (!auth.initialized) {
      await auth.restore()
    }
    // computed 不会随时间自动 tick，导航时显式兜底一次
    auth.ensureFresh()

    if (to.meta.guestOnly && auth.isAuthenticated) {
      return auth.homePath
    }

    const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)
    if (requiresAuth && !auth.isAuthenticated) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }

    const roles = to.meta.roles as Role[] | undefined
    if (requiresAuth && roles && roles.length > 0) {
      const currentRole = auth.user?.role
      if (!currentRole) {
        return { name: 'login', query: { redirect: to.fullPath } }
      }
      // 管理员误入学生区：不展示 403，直接回管理端；个人中心允许
      if (
        currentRole === 'ADMIN' &&
        roles.includes('STUDENT') &&
        to.name !== 'profile' &&
        to.name !== 'forbidden'
      ) {
        return resolveForbiddenTarget(currentRole)
      }
      if (!roles.includes(currentRole)) {
        return { name: 'forbidden' }
      }
    }

    return true
  })

  router.afterEach((to) => {
    const ui = useUiStore()
    ui.stopRouteLoading()
    document.title = resolveDocumentTitle(to.meta.title)
  })

  router.onError(() => {
    useUiStore().stopRouteLoading()
  })
}
