import { defineConfig, devices } from '@playwright/test'

const BASE_URL = process.env.E2E_BASE_URL || 'http://127.0.0.1:5173'

/**
 * F2 起启用真实 E2E：
 * - globalSetup 校验后端 health，并只登录一次 demo01 写入 storageState（避开 5 次/分钟登录限流）；
 * - webServer 复用本机 5173 的 Vite（未启动时自动 `npm run dev`），代理目标由 .env.development.local 决定；
 * - 使用系统 Chrome（与 F1 验收一致），单用例 180s。
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  timeout: 180_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  globalSetup: './e2e/global-setup.ts',
  use: {
    baseURL: BASE_URL,
    storageState: './e2e/.auth/student.json',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    viewport: { width: 1440, height: 900 },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], channel: 'chrome' },
    },
  ],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1',
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
