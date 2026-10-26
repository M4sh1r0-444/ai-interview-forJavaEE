import { expect, test, type APIRequestContext } from '@playwright/test'

import { authHeaders, createStudent, installAuth, trackPageErrors, type StudentAuth } from './helpers'

/**
 * V9 用户级 AI 服务配置 E2E：个人中心入口 → 子页面填写保存 → 掩码回显
 * （明文绝不出现）→ 刷新回填留空 Key 占位 → 清除回未配置态。
 * 连通性测试不打真实外网；加密落库/协议码由后端 UserAiConfigIT 覆盖。
 */
test.describe.configure({ mode: 'serial' })

let auth: StudentAuth

test.beforeAll(async ({ request }: { request: APIRequestContext }) => {
  auth = await createStudent(request)
})

test('个人中心入口可进入 AI 服务配置子页面', async ({ page }) => {
  const errors = trackPageErrors(page)
  await installAuth(page, auth)
  await page.goto('/profile')
  await expect(page.getByTestId('profile-ai-config-entry')).toBeVisible()
  await page.getByTestId('profile-ai-config-entry').click()
  await expect(page).toHaveURL(/\/profile\/ai-config/)
  await expect(page.getByTestId('ai-config-page')).toBeVisible()
  await expect(page.getByTestId('ai-config-status')).toContainText('未配置')
  expect(errors).toEqual([])
})

test('保存后回显掩码 Key，明文不出现在页面与接口', async ({ page, request }) => {
  const errors = trackPageErrors(page)
  await installAuth(page, auth)
  await page.goto('/profile/ai-config')
  await expect(page.getByTestId('ai-config-page')).toBeVisible()
  // 等 loading 结束（未配置态渲染完成）再填写，避免回填覆盖表单
  await expect(page.getByTestId('ai-config-status')).toContainText('未配置')

  await page.getByTestId('ai-config-url').fill('https://dashscope.example.com/compatible-mode/v1')
  await page.getByTestId('ai-config-key').fill('sk-e2e-1234567890abcdef')
  await page.getByTestId('ai-config-model').fill('qwen-plus')
  await page.getByTestId('ai-config-save').click()

  await expect(page.getByTestId('ai-config-status')).toContainText('已启用')
  await expect(page.getByTestId('ai-config-summary')).toContainText('qwen-plus')
  await expect(page.getByTestId('ai-config-summary')).toContainText('sk-e****cdef')

  // 接口回显同样只含掩码；Key 输入框清空并提示“已保存，留空则不修改”
  const response = await request.get('/api/users/me/ai-config', { headers: authHeaders(auth.token) })
  const body = (await response.json()) as { code: number; data: { apiKeyMasked?: string } }
  expect(body.code).toBe(0)
  expect(body.data.apiKeyMasked).toBe('sk-e****cdef')
  expect(JSON.stringify(body)).not.toContain('sk-e2e-1234567890abcdef')

  // 刷新回填：url/model 回显、Key 留空占位
  await page.reload()
  await expect(page.getByTestId('ai-config-url')).toHaveValue('https://dashscope.example.com/compatible-mode/v1')
  await expect(page.getByTestId('ai-config-model')).toHaveValue('qwen-plus')
  await expect(page.getByTestId('ai-config-key')).toHaveAttribute('placeholder', '已保存，留空则不修改')
  expect(errors).toEqual([])
})

test('清除配置回到未配置态', async ({ page, request }) => {
  const errors = trackPageErrors(page)
  await installAuth(page, auth)
  await page.goto('/profile/ai-config')
  await expect(page.getByTestId('ai-config-status')).toContainText('已启用')

  await page.getByTestId('ai-config-delete').click()
  await page.locator('.el-message-box').getByRole('button', { name: '清除配置' }).click()

  await expect(page.getByTestId('ai-config-status')).toContainText('未配置')
  await expect(page.getByTestId('ai-config-summary')).toHaveCount(0)
  await expect(page.getByTestId('ai-config-delete')).toHaveCount(0)

  const response = await request.get('/api/users/me/ai-config', { headers: authHeaders(auth.token) })
  const body = (await response.json()) as { code: number; data?: unknown }
  expect(body.code).toBe(0)
  expect(body.data).toBeUndefined()
  expect(errors).toEqual([])
})
