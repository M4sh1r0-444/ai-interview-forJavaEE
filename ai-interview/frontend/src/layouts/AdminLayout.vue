<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  Collection,
  DataBoard,
  Document,
  Link,
  Odometer,
  OfficeBuilding,
  UserFilled,
} from '@element-plus/icons-vue'

import AppShell from '@/components/layout/AppShell.vue'
import type { ShellMenuItem } from '@/components/layout/types'

/** 管理端 7 页菜单；评分权重页同时支持 /admin/positions?tab=weights 与 /admin/positions/:id/weights。 */
const route = useRoute()

const WEIGHTS_MENU = '/admin/positions?tab=weights'

const menuItems: ShellMenuItem[] = [
  { path: '/admin/dashboard', label: '数据看板', icon: DataBoard },
  { path: '/admin/positions', label: '岗位与能力模型', icon: OfficeBuilding },
  { path: WEIGHTS_MENU, label: '评分权重', icon: Odometer },
  { path: '/admin/questions', label: '题库管理', icon: Document },
  { path: '/admin/knowledge', label: '知识库', icon: Collection },
  { path: '/admin/resources', label: '学习资源', icon: Link },
  { path: '/admin/users', label: '用户管理', icon: UserFilled },
]

const activeMenu = computed(() => {
  if (route.name === 'admin-position-weights') return WEIGHTS_MENU
  if (route.name === 'admin-positions' && route.query.tab === 'weights') return WEIGHTS_MENU
  return undefined
})
</script>

<template>
  <AppShell
    variant="admin"
    :menu-items="menuItems"
    :active-menu="activeMenu"
    brand-title="AI 模拟面试"
    brand-subtitle="管理控制台"
    home-path="/admin/dashboard"
    content-class="page-container page-container--admin"
  />
</template>
