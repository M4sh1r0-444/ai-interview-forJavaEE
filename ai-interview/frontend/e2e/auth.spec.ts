import { expect, test } from '@playwright/test'

/**
 * E2E-01（计划 §9.4）：登录、错误密码、角色跳转与登出。
 * 独立于 storageState 用例，走真实 UI 登录；演示账号来自种子数据。
 */
test.describe('F1 认证与角色跳转', () => {
  test.use({ storageState: { cookies: [], origins: [] } })

  test('错误密码提示；学生/管理员登录跳转；登出回登录页', async ({ page }) => {
    await page.goto('/login')

    // 10002：用户名或密码错误（不区分账号/密码）
    await page.getByTestId('login-username').fill('demo01')
    await page.getByTestId('login-password').fill('not-the-password')
    await page.getByTestId('login-submit').click()
    await expect(page.getByTestId('login-error')).toContainText('用户名或密码错误')

    // 学生登录 → /home
    await page.getByTestId('login-password').fill('123456')
    await page.getByTestId('login-submit').click()
    await expect(page).toHaveURL(/\/home$/)
    await expect(page.getByTestId('home-start')).toBeVisible()

    // 登出 → /login
    await page.getByTestId('user-trigger').click()
    await page.getByRole('menuitem', { name: '退出登录' }).click()
    await page
      .locator('.el-message-box')
      .getByRole('button', { name: '退出登录', exact: true })
      .click()
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByTestId('login-username')).toBeVisible()

    // 管理员登录 → /admin/dashboard（角色跳转）
    await page.getByTestId('login-username').fill('admin')
    await page.getByTestId('login-password').fill('Admin@123456')
    await page.getByTestId('login-submit').click()
    await expect(page).toHaveURL(/\/admin\/dashboard$/)
    await expect(page.getByTestId('dashboard-page')).toBeVisible()
  })
})
