import { expect, test } from '@playwright/test'

import {
  abortAs,
  createAndStartAs,
  createStudent,
  finishAs,
  installAuth,
  SAMPLE_ANSWER,
  submitTextAs,
  trackPageErrors,
  waitForInterviewStatus,
  type StudentAuth,
} from './helpers'

// 登录限流 5 次/分钟：F3 串行执行，报告用例共享一个 E2E 用户，避免并发登录触发 429。
test.describe.configure({ mode: 'serial' })

let reportAuth: StudentAuth

test.beforeAll(async ({ request }) => {
  reportAuth = await createStudent(request)
})

test.describe('F3 报告与历史', () => {
  test('完整报告：总分环、5 维雷达、维度条、逐题证据与推荐', async ({ page, request }) => {
    const errors = trackPageErrors(page)
    const auth = reportAuth
    const session = await createAndStartAs(request, auth)

    await submitTextAs(request, auth, session.id, 0, SAMPLE_ANSWER)
    await waitForInterviewStatus(request, auth, session.id, ['WAITING_USER'])
    await finishAs(request, auth, session.id)

    await installAuth(page, auth)
    await page.goto(`/interview/${session.id}/report`)

    // 报告由后台异步生成，页面需自行从 10004 轮询到就绪。
    await expect(page.getByTestId('report-score')).toBeVisible({ timeout: 45_000 })
    await expect(page.getByTestId('score-ring-value')).toHaveText(/\d+\.\d{2}/)
    await expect(page.getByTestId('score-ring')).toContainText('总分')
    await expect(page.getByTestId('radar-chart').locator('canvas')).toBeVisible()
    await expect(page.locator('.dimension-row')).toHaveCount(5)
    await expect(page.getByTestId('dimension-voice')).toHaveCount(0)

    await expect(page.getByTestId('report-strengths')).toBeVisible()
    await expect(page.getByTestId('report-weaknesses')).toBeVisible()
    await expect(page.getByTestId('report-plan')).toBeVisible()

    const evidence = page.getByTestId('evidence-accordion')
    await expect(evidence).toBeVisible()
    await expect(evidence).toContainText('第 1 题')
    await expect(evidence).toContainText('命中的得分点')
    await expect(evidence).toContainText('未覆盖的失分点')
    await expect(evidence).toContainText('评分理由')

    await expect(page.getByTestId('report-recommendation').first()).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('report-print')).toBeVisible()
    await expect(page.getByTestId('report-action-restart')).toBeVisible()
    await expect(page.getByTestId('report-action-growth')).toBeVisible()
    await expect(page.getByTestId('report-action-recommendations')).toBeVisible()

    expect(errors).toEqual([])
  })

  test('报告未就绪（10004）时展示生成中并自动恢复', async ({ page, request }) => {
    const errors = trackPageErrors(page)
    const auth = reportAuth
    const session = await createAndStartAs(request, auth)
    await finishAs(request, auth, session.id)
    await installAuth(page, auth)

    let reportCalls = 0
    await page.route(`**/api/interviews/${session.id}/report`, async (route) => {
      reportCalls += 1
      if (reportCalls === 1) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 10004,
            message: '报告尚未生成',
            data: null,
            traceId: 'e2e-10004',
          }),
        })
        return
      }
      await route.continue()
    })

    await page.goto(`/interview/${session.id}/report`)
    await expect(page.getByTestId('report-pending')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('report-pending')).toContainText('报告生成中')

    await expect(page.getByTestId('report-score')).toBeVisible({ timeout: 45_000 })
    expect(reportCalls).toBeGreaterThanOrEqual(2)
    await expect(page.getByTestId('report-pending')).toHaveCount(0)

    expect(errors).toEqual([])
  })

  test('60 秒未就绪时允许重新生成，finish 幂等并恢复报告', async ({ page, request }) => {
    const errors = trackPageErrors(page)
    const auth = reportAuth
    const session = await createAndStartAs(request, auth)
    await finishAs(request, auth, session.id)
    await installAuth(page, auth)
    await page.clock.install()

    let allowReport = false
    let finishCalls = 0
    await page.route(`**/api/interviews/${session.id}/report`, async (route) => {
      if (!allowReport) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 10004,
            message: '报告尚未生成',
            data: null,
            traceId: 'e2e-retry',
          }),
        })
        return
      }
      await route.continue()
    })
    await page.route(`**/api/interviews/${session.id}/finish`, async (route) => {
      finishCalls += 1
      await route.continue()
    })

    await page.goto(`/interview/${session.id}/report`)
    await expect(page.getByTestId('report-pending')).toBeVisible({ timeout: 15_000 })
    for (let index = 0; index < 22; index += 1) {
      await page.clock.runFor(3000)
      await page.waitForTimeout(30)
    }

    await expect(page.getByTestId('report-retry')).toBeVisible({ timeout: 10_000 })
    allowReport = true
    await page.getByTestId('report-retry').click()
    await expect(page.getByTestId('report-score')).toBeVisible({ timeout: 30_000 })
    expect(finishCalls).toBe(1)

    expect(errors).toEqual([])
  })

  test('历史：状态筛选、分页与 URL 同步', async ({ page, request }) => {
    const errors = trackPageErrors(page)
    // 历史用例单独用户，避免报告用例的场次混入 11 场分页断言。
    const auth = await createStudent(request)

    // 独立 E2E 用户创建 11 场已中止会话，验证服务端分页；不污染 demo01。
    for (let index = 0; index < 11; index += 1) {
      const session = await createAndStartAs(request, auth)
      await abortAs(request, auth, session.id)
    }

    await installAuth(page, auth)
    await page.goto('/history?page=1&size=10')

    await expect(page.getByTestId('history-page')).toBeVisible()
    await expect(page.locator('[data-testid^="history-action-"]')).toHaveCount(10)
    await expect(page.getByTestId('pagination')).toContainText('11')
    await expect(page).toHaveURL(/\/history\?.*page=1/)

    await page.locator('.el-pagination .btn-next').click()
    await expect(page).toHaveURL(/\/history\?.*page=2/)
    await expect(page.locator('[data-testid^="history-action-"]')).toHaveCount(1)

    await page.goto('/history?page=1&size=10')
    await expect(page.locator('[data-testid^="history-action-"]')).toHaveCount(10)
    await page
      .getByTestId('history-filter')
      .getByText(/已中止/)
      .click()
    await expect(page).toHaveURL(/status=aborted/)
    await expect(page.locator('[data-testid^="history-action-"]')).toHaveCount(10)

    // size 变化由 URL 驱动并回到第 1 页（10/20/50 允许值）。
    await page.goto('/history?page=1&size=20')
    await expect(page.locator('[data-testid^="history-action-"]')).toHaveCount(11)
    await expect(page.getByTestId('pagination')).toContainText('20条/页')

    expect(errors).toEqual([])
  })
})
