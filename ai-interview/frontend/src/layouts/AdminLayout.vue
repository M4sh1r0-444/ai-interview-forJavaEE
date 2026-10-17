<script setup lang="ts">
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

const menuItems: ShellMenuItem[] = [
  { path: '/admin/dashboard', label: '数据看板', icon: DataBoard },
  { path: '/admin/positions', label: '岗位与能力模型', icon: OfficeBuilding },
  { path: '/admin/questions', label: '题库管理', icon: Document },
  { path: '/admin/knowledge', label: '知识库', icon: Collection },
  { path: '/admin/resources', label: '学习资源', icon: Link },
  { path: '/admin/users', label: '用户管理', icon: UserFilled },
  { path: '/admin/positions?tab=weights', label: '评分权重', icon: Odometer },
]
</script>

<template>
  <AppShell
    variant="admin"
    :menu-items="menuItems"
    brand-title="AI 模拟面试"
    brand-subtitle="管理控制台"
    home-path="/admin/dashboard"
    content-class="page-container page-container--admin"
  />
</template>
