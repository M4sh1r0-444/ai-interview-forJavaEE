<script setup lang="ts">
import {
  Briefcase,
  Clock,
  HomeFilled,
  MagicStick,
  TrendCharts,
  User,
} from '@element-plus/icons-vue'

import AppShell from '@/components/layout/AppShell.vue'
import type { ShellMenuItem } from '@/components/layout/types'

const menuItems: ShellMenuItem[] = [
  { path: '/home', label: '首页', icon: HomeFilled },
  { path: '/positions', label: '岗位选择', icon: Briefcase },
  { path: '/history', label: '面试历史', icon: Clock },
  { path: '/growth', label: '能力成长', icon: TrendCharts },
  { path: '/recommendations', label: '学习推荐', icon: MagicStick },
  { path: '/profile', label: '个人中心', icon: User },
]
</script>

<template>
  <AppShell
    variant="student"
    :menu-items="menuItems"
    brand-title="AI 模拟面试"
    brand-subtitle="学生端"
    home-path="/home"
  />
</template>
