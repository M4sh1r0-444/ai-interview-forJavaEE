/**
 * F5 管理端验收截图脚本（可重复执行）：
 * - 使用种子管理员登录，不创建/修改业务数据；
 * - 输出 docs/screenshots/f5/ 下的看板、岗位、权重、题库、知识库、资源、用户与移动端截图；
 * - 每页检查横向滚动；弹窗截图后按 Escape 关闭。
 *
 * 前置：后端 18080、前端 5173 已启动；执行 `node scripts/f5-screenshots.mjs`。
 */
import { mkdirSync } from 'node:fs'
import path from 'node:path'

import { chromium } from '@playwright/test'

const API = process.env.F5_API_BASE || 'http://127.0.0.1:18080'
const WEB = process.env.F5_WEB_BASE || 'http://127.0.0.1:5173'
const OUT = path.resolve('docs/screenshots/f5')
const ACCOUNT = {
  username: process.env.F5_ADMIN_USERNAME || 'admin',
  password: process.env.F5_ADMIN_PASSWORD || 'Admin@123456',
}

mkdirSync(OUT, { recursive: true })

async function login() {
  const response = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(ACCOUNT),
  })
  const body = await response.json().catch(() => ({}))
  if (body.code !== 0 || !body.data?.token) {
    throw new Error(
      `管理员登录失败：HTTP ${response.status()} code=${body.code ?? '?'} ${body.message ?? ''}`,
    )
  }
  return body.data
}

async function checkNoHorizontalScroll(page, label) {
  const overflow = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }))
  const ok = overflow.scrollWidth <= overflow.innerWidth + 1
  console.log(
    `${ok ? 'OK ' : 'FAIL'} 横向滚动 ${label}: ${overflow.scrollWidth}/${overflow.innerWidth}`,
  )
  return ok
}

async function installAuth(page, auth) {
  await page.addInitScript(
    ({ auth }) => {
      window.localStorage.setItem('ai-interview:auth', JSON.stringify(auth))
    },
    { auth: { token: auth.token, expiresAt: auth.expiresAt, user: auth.user } },
  )
}

const auth = await login()
console.log('截图账号', auth.user?.username)

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  locale: 'zh-CN',
  timezoneId: 'Asia/Shanghai',
})
const page = await context.newPage()
await installAuth(page, auth)
const pageErrors = []
page.on('pageerror', (error) => pageErrors.push(error.message))

// ===== 看板 =====
await page.goto(`${WEB}/admin/dashboard`)
await page.getByTestId('dashboard-page').waitFor({ timeout: 30_000 })
await page.locator('[data-testid="dashboard-trend"] canvas').waitFor({ timeout: 20_000 })
await page.waitForTimeout(700)
await page.screenshot({ path: path.join(OUT, '01-admin-dashboard.png') })
await checkNoHorizontalScroll(page, '看板（1440）')

// ===== 岗位列表 + 编辑弹窗 =====
await page.goto(`${WEB}/admin/positions`)
await page.getByTestId('position-table').waitFor({ timeout: 30_000 })
await page.waitForTimeout(400)
await page.screenshot({ path: path.join(OUT, '02-admin-positions.png') })
await checkNoHorizontalScroll(page, '岗位列表（1440）')

await page.getByTestId('position-edit-1').click()
await page.getByTestId('position-dialog').waitFor({ timeout: 10_000 })
await page.waitForTimeout(300)
await page.screenshot({ path: path.join(OUT, '03-admin-position-dialog.png') })
await page.keyboard.press('Escape')

// ===== 评分权重 =====
await page.goto(`${WEB}/admin/positions/1/weights`)
await page.getByTestId('weights-panel').waitFor({ timeout: 30_000 })
await page.getByTestId('score-content-technical').waitFor({ timeout: 15_000 })
await page.waitForTimeout(500)
await page.screenshot({ path: path.join(OUT, '04-admin-weights.png'), fullPage: true })
await checkNoHorizontalScroll(page, '评分权重（1440）')

// ===== 题库列表 + 编辑弹窗 =====
await page.goto(`${WEB}/admin/questions`)
await page.getByTestId('question-table').waitFor({ timeout: 30_000 })
await page.waitForTimeout(400)
await page.screenshot({ path: path.join(OUT, '05-admin-questions.png') })
await checkNoHorizontalScroll(page, '题库列表（1440）')

const firstEdit = page.locator('[data-testid^="question-edit-"]').first()
if ((await firstEdit.count()) > 0) {
  await firstEdit.click()
  await page.getByTestId('question-dialog').waitFor({ timeout: 10_000 })
  await page.waitForTimeout(300)
  await page.screenshot({ path: path.join(OUT, '06-admin-question-dialog.png') })
  await page.keyboard.press('Escape')
}

// ===== 知识库 =====
await page.goto(`${WEB}/admin/knowledge`)
await page.getByTestId('knowledge-submit').waitFor({ timeout: 30_000 })
await page.waitForTimeout(500)
await page.screenshot({ path: path.join(OUT, '07-admin-knowledge.png'), fullPage: true })
await checkNoHorizontalScroll(page, '知识库（1440）')

// ===== 学习资源 =====
await page.goto(`${WEB}/admin/resources`)
await page.getByTestId('resource-table').waitFor({ timeout: 30_000 })
await page.waitForTimeout(400)
await page.screenshot({ path: path.join(OUT, '08-admin-resources.png') })
await checkNoHorizontalScroll(page, '学习资源（1440）')

// ===== 用户管理 =====
await page.goto(`${WEB}/admin/users`)
await page.getByTestId('user-table').waitFor({ timeout: 30_000 })
await page.waitForTimeout(400)
await page.screenshot({ path: path.join(OUT, '09-admin-users.png') })
await checkNoHorizontalScroll(page, '用户管理（1440）')

// ===== 移动端看板 =====
const mobile = await browser.newContext({
  viewport: { width: 390, height: 844 },
  locale: 'zh-CN',
  timezoneId: 'Asia/Shanghai',
  isMobile: true,
  hasTouch: true,
})
const mobilePage = await mobile.newPage()
await installAuth(mobilePage, auth)
await mobilePage.goto(`${WEB}/admin/dashboard`)
await mobilePage.getByTestId('dashboard-page').waitFor({ timeout: 30_000 })
await mobilePage.getByTestId('admin-mobile-hint').waitFor({ timeout: 10_000 })
await mobilePage.waitForTimeout(600)
await mobilePage.screenshot({
  path: path.join(OUT, '10-admin-dashboard-mobile.png'),
  fullPage: true,
})
await checkNoHorizontalScroll(mobilePage, '看板（390）')
await mobilePage.close()
await mobile.close()

console.log('截图完成，pageerror 数：', pageErrors.length)
if (pageErrors.length > 0) console.log(pageErrors)
await context.close()
await browser.close()
