import { expect, test } from '@playwright/test'

import { abortSession, createAndStart, SAMPLE_ANSWER, trackPageErrors } from './helpers'

test.describe('F2 面试主流程（文字）', () => {
  test('首页 → 岗位列表 → 岗位详情 → 面试设置导航与默认值', async ({ page }) => {
    const errors = trackPageErrors(page)

    await page.goto('/home')
    await expect(page.getByRole('heading', { level: 1 })).toContainText(
      /上午好|下午好|晚上好|夜深了/,
    )
    await expect(page.getByTestId('home-start')).toBeVisible()

    await page.goto('/positions')
    await expect(page.getByTestId('position-card-1')).toBeVisible()
    await expect(page.getByTestId('position-card-2')).toBeVisible()
    await page.getByTestId('position-detail-1').click()
    await expect(page).toHaveURL(/\/positions\/1$/)
    await expect(page.getByText('权重校验通过')).toBeVisible()
    await expect(page.locator('.skill-row')).toHaveCount(7)

    await page.getByTestId('detail-start').click()
    await expect(page).toHaveURL(/\/interview\/setup\?positionId=1$/)
    await expect(page.getByTestId('setup-submit')).toBeEnabled()
    await expect(
      page.locator('[data-testid="setup-rounds"] .el-radio-button.is-active'),
    ).toContainText('6')
    await expect(
      page.locator('[data-testid="setup-duration"] .el-radio-button.is-active'),
    ).toContainText('15')

    expect(errors).toEqual([])
  })

  test('首页推荐接口失败时分区降级，不白屏', async ({ page }) => {
    const errors = trackPageErrors(page)
    await page.route('**/api/users/me/recommendations**', (route) => route.abort('failed'))

    await page.goto('/home')
    await expect(page.getByTestId('home-start')).toBeVisible()
    await expect(page.getByText('最近面试')).toBeVisible()
    await expect(page.locator('.panel--recommend .panel__error')).toBeVisible()

    expect(errors).toEqual([])
  })

  test('创建 → 自动开始 → 文字作答 → 刷新恢复 → finish → 终态', async ({ page }) => {
    const errors = trackPageErrors(page)

    await page.goto('/interview/setup?positionId=1')
    await expect(page.getByTestId('setup-submit')).toBeEnabled()

    const activeRound = page.locator('[data-testid="setup-rounds"] .el-radio-button.is-active')
    await expect(activeRound).toContainText('6')
    await page.locator('[data-testid="setup-rounds"]').getByText('8', { exact: true }).click()
    await expect(activeRound).toContainText('8')

    await page.getByTestId('setup-submit').click()
    await expect(page).toHaveURL(/\/interview\/\d+$/, { timeout: 20_000 })

    const aiMessages = page.getByTestId('message-ai')
    await expect(aiMessages.first()).toBeVisible({ timeout: 25_000 })
    await expect(page.getByTestId('status-tag').first()).toContainText('等待作答')
    await expect(page.getByTestId('timer-badge')).toContainText('剩余')
    await expect(page.getByTestId('ws-status')).toContainText('实时连接')

    const initialAiCount = await aiMessages.count()
    await page.getByTestId('answer-input').fill(SAMPLE_ANSWER)
    await page.getByTestId('answer-submit').click()
    await expect(page.getByTestId('message-user')).toHaveCount(1)
    await expect
      .poll(async () => aiMessages.count(), { timeout: 30_000 })
      .toBeGreaterThan(initialAiCount)

    await page.reload()
    await expect(aiMessages.first()).toBeVisible({ timeout: 25_000 })
    await expect(page.getByTestId('answer-input')).toBeEnabled()
    await expect(page.getByTestId('message-user')).toHaveCount(1)

    await page.getByTestId('finish-open').click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.getByTestId('finish-confirm').click()
    await expect(page.getByText('面试已结束，报告生成中').first()).toBeVisible({
      timeout: 25_000,
    })
    await expect(page.getByTestId('status-tag').first()).toContainText('已完成')

    await page.getByRole('button', { name: '返回面试历史' }).click()
    await expect(page).toHaveURL(/\/history$/)

    expect(errors).toEqual([])
  })

  test('中止流程与终态刷新恢复', async ({ page, request }) => {
    const errors = trackPageErrors(page)
    const session = await createAndStart(request)

    try {
      await page.goto(`/interview/${session.id}`)
      await expect(page.getByTestId('message-ai').first()).toBeVisible({ timeout: 25_000 })

      await page.getByTestId('abort-open').click()
      await expect(page.getByRole('dialog')).toBeVisible()
      await page.getByTestId('finish-confirm').click()
      await expect(page.getByText('面试已中止', { exact: true })).toBeVisible({
        timeout: 20_000,
      })
      await expect(page.getByTestId('status-tag').first()).toContainText('已中止')

      await page.reload()
      await expect(page.getByText('面试已中止', { exact: true })).toBeVisible({
        timeout: 20_000,
      })
      await expect(page.getByTestId('status-tag').first()).toContainText('已中止')
    } finally {
      await abortSession(request, session.id).catch(() => undefined)
    }

    expect(errors).toEqual([])
  })

  test('另一标签页结束时，本页通过 WS 对账进入终态', async ({ page, context, request }) => {
    const errors = trackPageErrors(page)
    const session = await createAndStart(request)
    const secondPage = await context.newPage()

    try {
      await page.goto(`/interview/${session.id}`)
      await secondPage.goto(`/interview/${session.id}`)
      await expect(page.getByTestId('message-ai').first()).toBeVisible({ timeout: 25_000 })
      await expect(secondPage.getByTestId('message-ai').first()).toBeVisible({
        timeout: 25_000,
      })
      await expect(page.getByTestId('ws-status')).toContainText('实时连接')

      await secondPage.getByTestId('finish-open').click()
      await expect(secondPage.getByRole('dialog')).toBeVisible()
      await secondPage.getByTestId('finish-confirm').click()

      await expect(page.getByText('面试已结束，报告生成中').first()).toBeVisible({
        timeout: 30_000,
      })
      await expect(page.getByTestId('status-tag').first()).toContainText('已完成')
    } finally {
      await secondPage.close()
      await abortSession(request, session.id).catch(() => undefined)
    }

    expect(errors).toEqual([])
  })

  test('WS 断开时展示重连/轮询提示，仍可完成一轮作答', async ({ page, request }) => {
    const errors = trackPageErrors(page)
    const session = await createAndStart(request)

    try {
      await page.routeWebSocket(/\/ws\/interviews\//, (ws) => {
        ws.close({ code: 1006, reason: 'e2e simulated network drop' })
      })

      await page.goto(`/interview/${session.id}`)
      const aiMessages = page.getByTestId('message-ai')
      await expect(aiMessages.first()).toBeVisible({ timeout: 25_000 })
      await expect(page.getByTestId('ws-status')).toContainText(/重连中|断开/)
      await expect(page.locator('.sync-notice')).toBeVisible()

      const initialAiCount = await aiMessages.count()
      await page.getByTestId('answer-input').fill(SAMPLE_ANSWER)
      await page.getByTestId('answer-submit').click()
      await expect
        .poll(async () => aiMessages.count(), { timeout: 35_000 })
        .toBeGreaterThan(initialAiCount)
    } finally {
      await abortSession(request, session.id).catch(() => undefined)
    }

    expect(errors).toEqual([])
  })
})
