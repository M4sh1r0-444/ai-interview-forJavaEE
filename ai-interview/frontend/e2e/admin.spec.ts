import { expect, test, type Page } from '@playwright/test'

import type { LearningResource, PositionAdminVO, QuestionAdminVO } from '../src/types/admin'
import type { UserVO } from '../src/types/auth'
import { adminAuthHeaders, authHeaders, readAuth } from './helpers'

const API = process.env.E2E_BACKEND_URL || 'http://127.0.0.1:18080'
const WEB = process.env.E2E_BASE_URL || 'http://127.0.0.1:5173'

const RUN = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
const QUESTION_CONTENT = `E2E-F5 题库 ${RUN}`
const POSITION_CODE = `E2E_F5_${RUN.toUpperCase()}`
const POSITION_NAME = `E2E-F5 测试岗位 ${RUN}`
const RESOURCE_TITLE = `E2E-F5 学习资源 ${RUN}`
const RESOURCE_TITLE_EDITED = `${RESOURCE_TITLE}（已编辑）`
const USERNAME = `e2e_f5_${RUN}`
const USER_PASSWORD = 'E2e@123456'
const KNOWLEDGE_TITLE = `E2E-F5 知识片段 ${RUN}`

interface ApiEnvelope<T> {
  code: number
  message?: string
  data?: T
}

interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'content-type': 'application/json',
    ...adminAuthHeaders(),
    ...((init.headers as Record<string, string> | undefined) ?? {}),
  }
  const response = await fetch(`${API}${path}`, { ...init, headers })
  const body = (await response.json()) as ApiEnvelope<T>
  if (!response.ok || body.code !== 0) {
    throw new Error(
      `${init.method ?? 'GET'} ${path} -> HTTP ${response.status()} code=${body.code ?? '?'} ${body.message ?? ''}`,
    )
  }
  return body.data as T
}

async function listAdminQuestions(positionId = 1): Promise<QuestionAdminVO[]> {
  const page = await api<PageResult<QuestionAdminVO>>(
    `/api/admin/questions?positionId=${positionId}&page=1&size=100`,
  )
  return page.records ?? []
}

async function findQuestionByContent(content: string): Promise<QuestionAdminVO> {
  const questions = await listAdminQuestions()
  const found = questions.find((item) => item.content === content)
  if (!found) throw new Error(`未找到题目：${content}`)
  return found
}

async function findQuestionById(id: number, positionId = 1): Promise<QuestionAdminVO> {
  const questions = await listAdminQuestions(positionId)
  const found = questions.find((item) => item.id === id)
  if (!found) throw new Error(`未找到题目 id=${id}`)
  return found
}

async function listAdminPositions(): Promise<PositionAdminVO[]> {
  const page = await api<PageResult<PositionAdminVO>>('/api/admin/positions?page=1&size=100')
  return page.records ?? []
}

async function findPositionByCode(code: string): Promise<PositionAdminVO> {
  const positions = await listAdminPositions()
  const found = positions.find((item) => item.code === code)
  if (!found) throw new Error(`未找到岗位：${code}`)
  return found
}

async function listAdminResources(): Promise<LearningResource[]> {
  const page = await api<PageResult<LearningResource>>(
    '/api/admin/resources?positionId=1&page=1&size=100',
  )
  return page.records ?? []
}

async function findResourceByTitle(title: string): Promise<LearningResource> {
  const resources = await listAdminResources()
  const found = resources.find((item) => item.title === title)
  if (!found) throw new Error(`未找到学习资源：${title}`)
  return found
}

async function listAdminUsers(keyword: string): Promise<UserVO[]> {
  const page = await api<PageResult<UserVO>>(
    `/api/admin/users?keyword=${encodeURIComponent(keyword)}&page=1&size=100`,
  )
  return page.records ?? []
}

async function studentPositions(): Promise<Array<{ id: number; code: string; name: string }>> {
  const response = await fetch(`${API}/api/positions`, { headers: authHeaders(readAuth().token) })
  const body = (await response.json()) as ApiEnvelope<
    Array<{ id: number; code: string; name: string }>
  >
  if (!response.ok || body.code !== 0) {
    throw new Error(`学生岗位列表失败：HTTP ${response.status()} code=${body.code}`)
  }
  return body.data ?? []
}

async function confirmMessageBox(page: Page, label: string): Promise<void> {
  const box = page.locator('.el-message-box')
  await expect(box).toBeVisible()
  await box.getByRole('button', { name: label, exact: true }).click()
}

test.describe.configure({ mode: 'serial' })

test.describe('F5 管理端', () => {
  test.use({ storageState: './e2e/.auth/admin.json' })

  test('学生访问管理端 403；看板四卡与近 7 日柱线图正确', async ({ browser, page }) => {
    // 学生登录态访问管理端：守卫跳 403，而不是渲染管理页面
    const studentContext = await browser.newContext({
      storageState: './e2e/.auth/student.json',
      baseURL: WEB,
    })
    const studentPage = await studentContext.newPage()
    await studentPage.goto('/admin/questions')
    await expect(studentPage).toHaveURL(/\/403$/)
    await expect(studentPage.getByText('当前账号无权访问该页面')).toBeVisible()
    await studentContext.close()

    const dashboard = await api<{
      userCount: number
      sessionCount: number
      questionCount: number
      avgScore?: number | null
      recent7Days: Array<{ date: string; sessionCount: number; avgScore?: number | null }>
    }>('/api/admin/dashboard')

    await page.goto('/admin/dashboard')
    await expect(page.getByTestId('dashboard-page')).toBeVisible()
    await expect(page.getByTestId('stat-user')).toContainText(String(dashboard.userCount))
    await expect(page.getByTestId('stat-session')).toContainText(String(dashboard.sessionCount))
    await expect(page.getByTestId('stat-question')).toContainText(String(dashboard.questionCount))
    await expect(page.getByTestId('stat-avg')).toContainText(
      dashboard.avgScore == null ? '—' : dashboard.avgScore.toFixed(2),
    )
    await expect(page.locator('[data-testid="dashboard-trend"] canvas')).toBeVisible()
    await expect(page.getByTestId('dashboard-trend')).toBeVisible()

    const refreshed = page.waitForResponse(
      (response) =>
        response.url().includes('/api/admin/dashboard') && response.request().method() === 'GET',
    )
    await page.getByTestId('dashboard-refresh').click()
    await refreshed
    await expect(page.getByTestId('stat-user')).toContainText(String(dashboard.userCount))
  })

  test('题库新增/编辑/40003 冲突恢复/逻辑删', async ({ page }) => {
    await page.goto('/admin/questions')
    await expect(page.getByTestId('question-page')).toBeVisible()
    await expect(page.getByTestId('question-add')).toBeEnabled()

    // 新增：默认岗位为第一个启用岗位，题型 TECHNICAL、难度 3
    await page.getByTestId('question-add').click()
    await expect(page.getByTestId('question-dialog')).toBeVisible()
    await page.getByTestId('question-form-content').fill(QUESTION_CONTENT)
    await page.getByTestId('question-form-skills').click()
    await page.getByRole('option').first().click()
    await page.getByTestId('question-form-submit').click()
    await expect(page.getByText('题目已新增')).toBeVisible()

    const created = await findQuestionByContent(QUESTION_CONTENT)
    await expect(page.getByTestId(`question-edit-${created.id}`)).toBeVisible()

    // 编辑：version 从 1 递增，列表回读新内容
    await page.getByTestId(`question-edit-${created.id}`).click()
    await page.getByTestId('question-form-content').fill(`${QUESTION_CONTENT}（已编辑）`)
    await page.getByTestId('question-form-submit').click()
    await expect(page.getByText('题目已更新')).toBeVisible()
    await expect
      .poll(async () => (await findQuestionById(created.id)).content)
      .toBe(`${QUESTION_CONTENT}（已编辑）`)

    // 模拟他人先改：API 用最新 version 更新，UI 保存旧 version → 40003
    const latest = await findQuestionById(created.id)
    await api<QuestionAdminVO>(`/api/admin/questions/${created.id}`, {
      method: 'PUT',
      body: JSON.stringify({
        positionId: latest.positionId,
        type: latest.type,
        content: `${QUESTION_CONTENT}（他人修改）`,
        background: latest.background,
        difficulty: latest.difficulty,
        skillTags: latest.skillTags,
        referenceAnswer: latest.referenceAnswer,
        keyPoints: latest.keyPoints,
        deductionPoints: latest.deductionPoints,
        followupTemplates: latest.followupTemplates,
        version: latest.version,
      }),
    })

    const localContent = `${QUESTION_CONTENT}（本地冲突修改）`
    await page.getByTestId(`question-edit-${created.id}`).click()
    await page.getByTestId('question-form-content').fill(localContent)
    await page.getByTestId('question-form-submit').click()
    await expect(page.getByTestId('question-conflict-dialog')).toBeVisible()
    await expect(page.getByTestId('question-conflict-dialog')).toContainText('数据已被他人修改')
    await expect(page.getByTestId('question-conflict-dialog')).toContainText('最新版本')
    await page.getByTestId('question-conflict-override').click()
    await expect(page.getByText('已用你的内容覆盖最新版本并保存')).toBeVisible()
    await expect.poll(async () => (await findQuestionById(created.id)).content).toBe(localContent)

    // 逻辑删：行仍在列表，状态变为已删除
    await expect(page.getByTestId(`question-delete-${created.id}`)).toBeVisible()
    await page.getByTestId(`question-delete-${created.id}`).click()
    await confirmMessageBox(page, '删除')
    await expect(page.getByText('题目已逻辑删除（DISABLED）')).toBeVisible()
    await expect.poll(async () => (await findQuestionById(created.id)).status).toBe('DISABLED')
    const deletedRow = page.locator('.el-table__row').filter({ hasText: localContent }).first()
    await expect(deletedRow).toContainText('已删除')
  })

  test('岗位新增/技能权重/评分权重/学生可见性/停用', async ({ page }) => {
    await page.goto('/admin/positions')
    await expect(page.getByTestId('position-page')).toBeVisible()
    await page.getByTestId('position-add').click()
    await expect(page.getByTestId('position-dialog')).toBeVisible()
    await page.getByTestId('position-form-code').fill(POSITION_CODE)
    await page.getByTestId('position-form-name').fill(POSITION_NAME)
    await page.getByTestId('skill-row-0-code').fill('e2e_skill')
    await page.getByTestId('skill-row-0-name').fill('E2E 技能')
    await expect(page.getByTestId('position-form-submit')).toBeEnabled()
    await page.getByTestId('position-form-submit').click()
    await expect(page.getByText('岗位已创建，学生端立即生效')).toBeVisible()

    const position = await findPositionByCode(POSITION_CODE)
    expect(position.enabled).toBe(1)
    await expect
      .poll(async () => (await studentPositions()).some((item) => item.code === POSITION_CODE))
      .toBe(true)
    await expect(page.getByTestId(`position-edit-${position.id}`)).toBeVisible()

    // 评分权重：先验证组和错误禁用保存，再恢复默认保存并回读
    await page.goto(`/admin/positions/${position.id}/weights`)
    await expect(page.getByTestId('weights-panel')).toBeVisible()
    const contentSum = page.getByTestId('score-sum-content')
    await expect(contentSum).toContainText('1.00')
    const technical = page.getByTestId('score-content-technical').locator('input')
    await technical.fill('0.5')
    await technical.blur()
    await expect(contentSum).toContainText('1.20')
    await expect(page.getByTestId('weights-save')).toBeDisabled()
    await page.getByTestId('weights-reset').click()
    await expect(contentSum).toContainText('1.00')
    await page.getByTestId('weights-save').click()
    await expect(page.getByText('评分权重已保存，后续面试立即生效')).toBeVisible()

    const savedPosition = await api<PositionAdminVO>(`/api/admin/positions/${position.id}`)
    expect(savedPosition.scoreWeights?.content?.technical).toBe(0.3)

    // /admin/evaluation 别名 → /admin/positions?tab=weights
    await page.goto('/admin/evaluation')
    await expect(page).toHaveURL(/\/admin\/positions\?.*tab=weights/)
    await expect(page.getByTestId('weights-panel')).toBeVisible()

    // 停用后学生端岗位列表不可见
    await page.goto('/admin/positions')
    await expect(page.getByTestId(`position-edit-${position.id}`)).toBeVisible()
    await page.getByTestId(`position-edit-${position.id}`).click()
    await expect(page.getByTestId('position-form-enabled')).toHaveClass(/is-checked/)
    await page.getByTestId('position-form-enabled').click()
    await page.getByTestId('position-form-submit').click()
    await expect(page.getByText('岗位已更新')).toBeVisible()
    await expect
      .poll(async () => (await studentPositions()).some((item) => item.code === POSITION_CODE))
      .toBe(false)
  })

  test('学习资源新增/编辑/下架；用户搜索/启停/自禁用保护', async ({ page }) => {
    // ===== 学习资源 =====
    await page.goto('/admin/resources')
    await expect(page.getByTestId('resource-page')).toBeVisible()
    await page.getByTestId('resource-add').click()
    await expect(page.getByTestId('resource-dialog')).toBeVisible()
    await page.getByTestId('resource-form-title').fill(RESOURCE_TITLE)
    await page.getByTestId('resource-form-submit').click()
    await expect(page.getByText('学习资源已新增')).toBeVisible()

    const resource = await findResourceByTitle(RESOURCE_TITLE)
    await expect(page.getByTestId(`resource-edit-${resource.id}`)).toBeVisible()

    await page.getByTestId(`resource-edit-${resource.id}`).click()
    await page.getByTestId('resource-form-title').fill(RESOURCE_TITLE_EDITED)
    await page.getByTestId('resource-form-submit').click()
    await expect(page.getByText('学习资源已更新')).toBeVisible()
    await expect
      .poll(async () => (await findResourceByTitle(RESOURCE_TITLE_EDITED)).title)
      .toBe(RESOURCE_TITLE_EDITED)

    await expect(page.getByTestId(`resource-delete-${resource.id}`)).toBeVisible()
    await page.getByTestId(`resource-delete-${resource.id}`).click()
    await confirmMessageBox(page, '下架')
    await expect(page.getByText('资源已下架（enabled=0）')).toBeVisible()
    await expect
      .poll(async () => (await findResourceByTitle(RESOURCE_TITLE_EDITED)).enabled)
      .toBe(0)
    const downRow = page
      .locator('.el-table__row')
      .filter({ hasText: RESOURCE_TITLE_EDITED })
      .first()
    await expect(downRow).toContainText('已下架')

    // ===== 用户管理 =====
    const registered = await api<UserVO>('/api/auth/register', {
      method: 'POST',
      headers: {},
      body: JSON.stringify({
        username: USERNAME,
        password: USER_PASSWORD,
        realName: 'E2E 用户',
        major: '软件工程',
      }),
    })
    expect(registered.id).toBeGreaterThan(0)

    await page.goto('/admin/users')
    await expect(page.getByTestId('user-page')).toBeVisible()
    await page.getByTestId('user-search').fill(USERNAME)
    await page.getByTestId('user-search-button').click()
    await expect(page.getByTestId(`user-status-${registered.id}`)).toBeVisible()

    // 禁用 → API 状态 0 → 启用 → API 状态 1
    await page.getByTestId(`user-status-${registered.id}`).click()
    await confirmMessageBox(page, '禁用')
    await expect(page.getByText(`账号「${USERNAME}」已禁用`)).toBeVisible()
    await expect
      .poll(
        async () =>
          (await listAdminUsers(USERNAME)).find((item) => item.id === registered.id)?.status,
      )
      .toBe(0)

    await page.getByTestId(`user-status-${registered.id}`).click()
    await confirmMessageBox(page, '启用')
    await expect(page.getByText(`账号「${USERNAME}」已启用`)).toBeVisible()
    await expect
      .poll(
        async () =>
          (await listAdminUsers(USERNAME)).find((item) => item.id === registered.id)?.status,
      )
      .toBe(1)

    // 当前登录账号：显示「当前」，开关禁用
    await page.getByTestId('user-search').fill('admin')
    await page.getByTestId('user-search-button').click()
    await expect(page.getByTestId('user-self-tag')).toBeVisible()
    const adminRow = page.locator('.el-table__row').filter({ hasText: 'admin' }).first()
    await expect(adminRow.locator('.el-switch')).toHaveClass(/is-disabled/)
  })

  test('知识库新增片段并重建索引，明确无列表能力', async ({ page }) => {
    await page.goto('/admin/knowledge')
    await expect(page.getByTestId('knowledge-page')).toBeVisible()
    await expect(page.getByText('没有知识片段列表/编辑/删除接口')).toBeVisible()

    await page.getByTestId('knowledge-form-title').fill(KNOWLEDGE_TITLE)
    await page
      .getByTestId('knowledge-form-content')
      .fill(
        '缓存穿透指查询不存在的数据导致请求落到数据库；可用布隆过滤器或空值缓存防护。缓存击穿指热点 key 过期瞬间大量并发打库；可用互斥锁或逻辑过期重建。缓存雪崩指大量 key 同时过期；可用随机过期时间与多级缓存。',
      )
    await page
      .getByTestId('knowledge-form-keywords')
      .fill('缓存穿透,缓存击穿,缓存雪崩,布隆过滤器,互斥锁,逻辑过期')
    await page.getByTestId('knowledge-form-skills').click()
    await page.getByRole('option').first().click()
    await page.getByTestId('knowledge-submit').click()
    await expect(page.getByText('知识片段已新增，已进入检索词典；下一场面试即可命中')).toBeVisible()
    await expect(page.getByTestId('knowledge-success')).toContainText(KNOWLEDGE_TITLE)

    await page.getByTestId('knowledge-rebuild').click()
    await confirmMessageBox(page, '重建索引')
    await expect(page.getByText('全文索引已重建（rebuilt: true）')).toBeVisible()
  })
})
