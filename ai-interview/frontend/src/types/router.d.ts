import 'vue-router'

import type { Role } from '@/types/auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题（document.title 后缀） */
    title?: string
    /** 是否需要登录 */
    requiresAuth?: boolean
    /** 允许访问的角色；缺省表示登录即可（或按 requiresAuth 判断） */
    roles?: Role[]
    /** 布局标识，仅用于调试与面包屑 */
    layout?: 'auth' | 'student' | 'admin' | 'blank'
    /** 是否保持组件状态 */
    keepAlive?: boolean
    /** 侧栏高亮项（详情页归属列表菜单） */
    activeMenu?: string
    /** 仅匿名可访问（登录/注册） */
    guestOnly?: boolean
    /** F1 占位页对应的后续里程碑 */
    milestone?: string
  }
}
