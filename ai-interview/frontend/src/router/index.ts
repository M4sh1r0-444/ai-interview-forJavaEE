import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

// 布局与页面全部懒加载：入口 chunk 只保留框架、状态与路由（计划 §4.2）。
const AuthLayout = () => import('@/layouts/AuthLayout.vue')
const StudentLayout = () => import('@/layouts/StudentLayout.vue')
const AdminLayout = () => import('@/layouts/AdminLayout.vue')

const LoginView = () => import('@/views/auth/LoginView.vue')
const RegisterView = () => import('@/views/auth/RegisterView.vue')

const HomeView = () => import('@/views/student/HomeView.vue')
const PositionListView = () => import('@/views/student/PositionListView.vue')
const PositionDetailView = () => import('@/views/student/PositionDetailView.vue')
const SetupView = () => import('@/views/interview/SetupView.vue')
const RoomView = () => import('@/views/interview/RoomView.vue')
const ReportView = () => import('@/views/interview/ReportView.vue')
const HistoryView = () => import('@/views/growth/HistoryView.vue')
const GrowthView = () => import('@/views/growth/GrowthView.vue')
const RecommendationView = () => import('@/views/growth/RecommendationView.vue')
const ProfileView = () => import('@/views/user/ProfileView.vue')
const AiConfigView = () => import('@/views/user/AiConfigView.vue')

const DashboardView = () => import('@/views/admin/DashboardView.vue')
const PositionAdminView = () => import('@/views/admin/PositionAdminView.vue')
const WeightsView = () => import('@/views/admin/WeightsView.vue')
const QuestionAdminView = () => import('@/views/admin/QuestionAdminView.vue')
const KnowledgeAdminView = () => import('@/views/admin/KnowledgeAdminView.vue')
const ResourceAdminView = () => import('@/views/admin/ResourceAdminView.vue')
const UserAdminView = () => import('@/views/admin/UserAdminView.vue')

const ForbiddenView = () => import('@/views/error/ForbiddenView.vue')
const NotFoundView = () => import('@/views/error/NotFoundView.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: AuthLayout,
    children: [
      {
        path: '',
        name: 'login',
        component: LoginView,
        meta: { title: '登录', layout: 'auth', guestOnly: true },
      },
    ],
  },
  {
    path: '/register',
    component: AuthLayout,
    children: [
      {
        path: '',
        name: 'register',
        component: RegisterView,
        meta: { title: '注册', layout: 'auth', guestOnly: true },
      },
    ],
  },
  {
    path: '/',
    component: StudentLayout,
    meta: { requiresAuth: true, roles: ['STUDENT'], layout: 'student' },
    children: [
      {
        // 根路径按角色分流；管理员不会被选中此父级角色，守卫会在下方兜底。
        path: '',
        redirect: () => useAuthStore().homePath,
      },
      {
        path: 'home',
        name: 'home',
        component: HomeView,
        meta: { title: '首页', activeMenu: '/home' },
      },
      {
        path: 'positions',
        name: 'positions',
        component: PositionListView,
        meta: { title: '岗位选择', activeMenu: '/positions' },
      },
      {
        path: 'positions/:id(\\d+)',
        name: 'position-detail',
        component: PositionDetailView,
        meta: { title: '岗位详情', activeMenu: '/positions' },
      },
      {
        path: 'interview/setup',
        name: 'interview-setup',
        component: SetupView,
        meta: { title: '面试设置', activeMenu: '/positions' },
      },
      {
        path: 'interview/:id(\\d+)',
        name: 'interview-room',
        component: RoomView,
        meta: { title: '面试房间', activeMenu: '/history', keepAlive: true },
      },
      {
        path: 'interview/:id(\\d+)/report',
        name: 'interview-report',
        component: ReportView,
        meta: { title: '面试报告', activeMenu: '/history' },
      },
      {
        path: 'history',
        name: 'history',
        component: HistoryView,
        meta: { title: '面试历史', activeMenu: '/history' },
      },
      {
        path: 'growth',
        name: 'growth',
        component: GrowthView,
        meta: { title: '能力成长', activeMenu: '/growth' },
      },
      {
        path: 'recommendations',
        name: 'recommendations',
        component: RecommendationView,
        meta: { title: '学习推荐', activeMenu: '/recommendations' },
      },
      {
        path: 'profile',
        name: 'profile',
        component: ProfileView,
        // 管理员也允许修改自己的资料（计划 §4.1）
        meta: { title: '个人中心', activeMenu: '/profile', roles: ['STUDENT', 'ADMIN'] },
      },
      {
        // 个人中心子页面（V9 用户级 AI 配置）：侧边栏仍高亮「个人中心」
        path: 'profile/ai-config',
        name: 'profile-ai-config',
        component: AiConfigView,
        meta: { title: 'AI 服务配置', activeMenu: '/profile', roles: ['STUDENT', 'ADMIN'] },
      },
    ],
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, roles: ['ADMIN'], layout: 'admin' },
    children: [
      { path: '', redirect: { name: 'admin-dashboard' } },
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: DashboardView,
        meta: { title: '数据看板', activeMenu: '/admin/dashboard' },
      },
      {
        path: 'positions',
        name: 'admin-positions',
        component: PositionAdminView,
        meta: { title: '岗位与能力模型', activeMenu: '/admin/positions' },
      },
      {
        path: 'positions/:id(\\d+)/weights',
        name: 'admin-position-weights',
        component: WeightsView,
        meta: { title: '评分权重', activeMenu: '/admin/positions' },
      },
      {
        // 兼容方案 §11 的独立评估路由，复用评分权重页（D33）
        path: 'evaluation',
        redirect: { name: 'admin-positions', query: { tab: 'weights' } },
      },
      {
        path: 'questions',
        name: 'admin-questions',
        component: QuestionAdminView,
        meta: { title: '题库管理', activeMenu: '/admin/questions' },
      },
      {
        path: 'knowledge',
        name: 'admin-knowledge',
        component: KnowledgeAdminView,
        meta: { title: '知识库', activeMenu: '/admin/knowledge' },
      },
      {
        path: 'resources',
        name: 'admin-resources',
        component: ResourceAdminView,
        meta: { title: '学习资源', activeMenu: '/admin/resources' },
      },
      {
        path: 'users',
        name: 'admin-users',
        component: UserAdminView,
        meta: { title: '用户管理', activeMenu: '/admin/users' },
      },
    ],
  },
  {
    path: '/403',
    name: 'forbidden',
    component: ForbiddenView,
    meta: { title: '无权访问', layout: 'blank', requiresAuth: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundView,
    meta: { title: '页面不存在', layout: 'blank' },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    return savedPosition ?? { top: 0 }
  },
})

export default router
