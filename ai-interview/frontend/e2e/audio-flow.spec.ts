import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

import {
  abortAs,
  authHeaders,
  createAndStartAs,
  createStudent,
  finishAs,
  installAuth,
  trackPageErrors,
  waitForInterviewStatus,
  type StudentAuth,
} from './helpers'

/**
 * F4-7 语音/离线 E2E（计划 §9.4 E2E-04/06）：
 * 1) 伪造麦克风录音 → 注入 Web Speech stub → 提交 → AUDIO 报告含 voice → 受保护回放；
 * 2) ASR network 错误时手工转写兜底（断网演练关键路径）；
 * 3) 成长曲线/岗位切换/空态、学习推荐外链与下场建议、个人中心改资料与退出登录。
 *
 * 登录限流 5 次/分钟：本文件串行并共享一个独立学生；不与 demo01 演示数据混用。
 */
test.describe.configure({ mode: 'serial' })

let auth: StudentAuth

async function installFakeSpeech(page: Page, mode: 'final' | 'network'): Promise<void> {
  await page.addInitScript(
    ({ speechMode }) => {
      class FakeSpeechRecognition {
        lang = ''
        continuous = false
        interimResults = false
        maxAlternatives = 1
        onstart: (() => void) | null = null
        onresult: ((event: unknown) => void) | null = null
        onerror: ((event: { error: string }) => void) | null = null
        onend: (() => void) | null = null
        onspeechend: (() => void) | null = null

        start(): void {
          this.onstart?.()
          if (speechMode === 'network') {
            window.setTimeout(() => {
              this.onerror?.({ error: 'network' })
              this.onend?.()
            }, 200)
            return
          }
          window.setTimeout(() => {
            const alternative = {
              transcript:
                '我会先复现问题，结合监控与日志定位缓存击穿的根因，再用互斥锁和逻辑过期修复，并补充压测防止复发。',
              confidence: 0.9,
            }
            const result = Object.assign([alternative], { isFinal: true, length: 1 })
            const results = Object.assign([result], { length: 1 })
            this.onresult?.({ resultIndex: 0, results })
          }, 250)
        }

        stop(): void {
          this.onend?.()
        }

        abort(): void {
          this.onend?.()
        }
      }
      ;(window as unknown as Record<string, unknown>).SpeechRecognition = FakeSpeechRecognition
    },
    { speechMode: mode },
  )
}

async function installFakeAudioCapture(page: Page): Promise<void> {
  // 不依赖系统麦克风：伪造 MediaStream + MediaRecorder，让录音链路在任意 CI 环境可跑。
  await page.addInitScript(() => {
    const track = {
      kind: 'audio',
      label: 'E2E Fake Microphone',
      enabled: true,
      readyState: 'live',
      stop(): void {},
      addEventListener(): void {},
      removeEventListener(): void {},
    }
    const stream = {
      getTracks: () => [track],
      getAudioTracks: () => [track],
    }
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: {
        getUserMedia: async () => stream,
      },
    })

    class FakeMediaRecorder {
      static instances: FakeMediaRecorder[] = []
      static isTypeSupported = (): boolean => true

      state = 'inactive'
      mimeType: string
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      onstop: ((event: Event) => void) | null = null
      onerror: ((event: Event) => void) | null = null

      constructor(
        public streamArg: unknown,
        options?: { mimeType?: string },
      ) {
        this.mimeType = options?.mimeType ?? 'audio/webm'
        FakeMediaRecorder.instances.push(this)
      }

      start(): void {
        this.state = 'recording'
      }

      stop(): void {
        this.state = 'inactive'
        this.ondataavailable?.({ data: new Blob([new Uint8Array(2048)], { type: this.mimeType }) })
        this.onstop?.(new Event('stop'))
      }

      abort(): void {
        this.stop()
      }
    }

    ;(window as unknown as Record<string, unknown>).MediaRecorder = FakeMediaRecorder
  })
}

async function waitForAudioAnswer(
  request: APIRequestContext,
  sessionId: number,
  expectedText: string,
): Promise<void> {
  await expect
    .poll(
      async () => {
        const response = await request.get(`/api/interviews/${sessionId}/messages`, {
          headers: authHeaders(auth.token),
        })
        const body = (await response.json()) as {
          data?: { speaker?: string; inputType?: string; contentText?: string }[]
        }
        return (body.data ?? []).some(
          (message) =>
            message.speaker === 'USER' &&
            message.inputType === 'AUDIO' &&
            (message.contentText ?? '').includes(expectedText),
        )
      },
      { timeout: 30_000, intervals: [250, 500, 1000] },
    )
    .toBe(true)
}

async function startRecording(page: Page): Promise<void> {
  await page.getByTestId('audio-start').click()
  await expect(page.getByTestId('audio-recording')).toBeVisible({ timeout: 15_000 })
  await page.waitForTimeout(1200)
  await page.getByTestId('audio-stop').click()
  await expect(page.getByTestId('audio-result')).toBeVisible({ timeout: 15_000 })
}

test.beforeAll(async ({ request }) => {
  auth = await createStudent(request)
})

test.describe('F4 语音 + 成长 + 推荐 + 个人中心', () => {
  test('语音面试：录音、转写、提交、voice 报告与受保护回放', async ({ page, request }) => {
    const errors = trackPageErrors(page)
    const session = await createAndStartAs(request, auth, {
      mode: 'AUDIO',
      rounds: 5,
      difficulty: 2,
    })

    await installAuth(page, auth)
    await installFakeSpeech(page, 'final')
    await installFakeAudioCapture(page)
    await page.goto(`/interview/${session.id}`)

    await expect(page.getByTestId('message-ai').first()).toBeVisible({ timeout: 30_000 })
    await expect(page.getByTestId('audio-answer-panel')).toBeVisible()
    await expect(page.getByTestId('audio-unsupported-room')).toHaveCount(0)

    await startRecording(page)
    const transcript = page.getByTestId('transcript-input')
    await expect(transcript).toBeVisible()
    await expect
      .poll(async () => (await transcript.inputValue()).trim().length, { timeout: 10_000 })
      .toBeGreaterThan(0)

    await page.getByTestId('audio-submit').click()
    const userMessage = page.getByTestId('message-user').first()
    await expect(userMessage).toBeVisible({ timeout: 30_000 })
    await expect(userMessage).toContainText('语音')
    await waitForAudioAnswer(request, session.id, '缓存击穿')
    await waitForInterviewStatus(request, auth, session.id, ['WAITING_USER'])

    await finishAs(request, auth, session.id)
    await page.goto(`/interview/${session.id}/report`)

    await expect(page.getByTestId('report-score')).toBeVisible({ timeout: 45_000 })
    await expect(page.getByTestId('dimension-voice')).toBeVisible()
    await expect(page.getByTestId('radar-chart').locator('canvas')).toBeVisible()

    const replayButton = page.getByTestId('evidence-replay-0')
    await expect(replayButton).toBeVisible({ timeout: 20_000 })
    const audioResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        response.url().includes(`/api/interviews/${session.id}/messages/`) &&
        response.url().endsWith('/audio'),
    )
    await replayButton.click()
    const response = await audioResponse
    expect(response.status()).toBe(200)
    expect(response.headers()['content-type'] ?? '').toContain('audio/')
    // 伪造 blob 不是真实 Opus 音频，播放器可能拒绝；这里只验证"带鉴权的回放请求成功"。
    await expect
      .poll(
        async () => {
          const text = await replayButton.innerText()
          const errorCount = await page.locator('.evidence__replay-error').count()
          return text.includes('回放中') || errorCount > 0
        },
        { timeout: 15_000 },
      )
      .toBe(true)

    // 报告页 CTA 可进入成长与完整推荐（F4-4/F4-5）
    await expect(page.getByTestId('report-action-growth')).toBeVisible()
    await expect(page.getByTestId('report-action-recommendations')).toBeVisible()

    expect(errors).toEqual([])
  })

  test('ASR 断网降级：手工填写转写仍可提交语音回答', async ({ page, request }) => {
    const errors = trackPageErrors(page)
    const session = await createAndStartAs(request, auth, {
      mode: 'AUDIO',
      rounds: 5,
      difficulty: 2,
    })

    await installAuth(page, auth)
    await installFakeSpeech(page, 'network')
    await installFakeAudioCapture(page)
    await page.goto(`/interview/${session.id}`)
    await expect(page.getByTestId('message-ai').first()).toBeVisible({ timeout: 30_000 })

    await startRecording(page)
    await expect(page.getByTestId('transcript-error')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByTestId('transcript-error')).toContainText('需要网络')

    const manualText =
      '断网时我会手工填写转写：先说明缓存击穿的成因，再给出互斥锁、逻辑过期与限流兜底方案。'
    await page.getByTestId('transcript-input').fill(manualText)
    await expect(page.getByTestId('audio-submit')).toBeEnabled()
    await page.getByTestId('audio-submit').click()

    await expect(page.getByTestId('message-user').first()).toContainText('断网时我会手工填写转写', {
      timeout: 30_000,
    })
    await waitForAudioAnswer(request, session.id, '断网时我会手工填写转写')
    await waitForInterviewStatus(request, auth, session.id, ['WAITING_USER'])
    await abortAs(request, auth, session.id)

    expect(errors).toEqual([])
  })

  test('成长曲线：多技能、岗位切换与空态', async ({ page }) => {
    const errors = trackPageErrors(page)
    await installAuth(page, auth)
    await page.goto('/growth?positionId=1')

    await expect(page.getByTestId('growth-page')).toBeVisible()
    await expect(page.getByTestId('growth-chart').locator('canvas')).toBeVisible({
      timeout: 20_000,
    })
    await expect(page.locator('[data-testid^="growth-skill-"]').first()).toBeVisible()
    await expect(page.getByTestId('growth-toolbar')).toContainText('已记录')

    // 切换到前端岗位：该用户未做过前端面试 → 空态
    await page.getByTestId('growth-position').click()
    await page.getByRole('option', { name: 'Web 前端开发工程师' }).click()
    await expect(page.getByTestId('growth-empty')).toBeVisible({ timeout: 15_000 })
    await expect(page).toHaveURL(/positionId=2/)

    // 切回 Java 岗位：曲线恢复
    await page.getByTestId('growth-position').click()
    await page.getByRole('option', { name: 'Java 后端开发工程师' }).click()
    await expect(page.getByTestId('growth-chart').locator('canvas')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page).toHaveURL(/positionId=1/)

    expect(errors).toEqual([])
  })

  test('学习推荐：Top3、外链安全与按建议开始下一场', async ({ page }) => {
    const errors = trackPageErrors(page)
    await installAuth(page, auth)
    await page.goto('/recommendations?positionId=1')

    await expect(page.getByTestId('recommendations-page')).toBeVisible()
    await expect(page.locator('[data-testid^="rec-card-"]').first()).toBeVisible({
      timeout: 20_000,
    })
    expect(await page.locator('[data-testid^="rec-card-"]').count()).toBeGreaterThanOrEqual(1)
    await expect(page.getByTestId('rec-suggestion')).toBeVisible()
    await expect(page.getByText('错题重练').first()).toBeVisible()

    const externalLinks = page.locator('.rec-resource__title[href]')
    if ((await externalLinks.count()) > 0) {
      const first = externalLinks.first()
      await expect(first).toHaveAttribute('target', '_blank')
      await expect(first).toHaveAttribute('rel', 'noopener noreferrer')
      expect(await first.getAttribute('href')).toMatch(/^https?:\/\//)
    }

    await page.getByTestId('rec-start-next').click()
    await expect(page).toHaveURL(/\/interview\/setup\?positionId=1/)
    await expect(page.getByTestId('setup-submit')).toBeEnabled({ timeout: 15_000 })

    expect(errors).toEqual([])
  })

  test('个人中心：资料回显、保存、不展示密码与退出登录', async ({ page }) => {
    const errors = trackPageErrors(page)
    await installAuth(page, auth)
    await page.goto('/profile')

    await expect(page.getByTestId('profile-page')).toBeVisible()
    await expect(page.getByTestId('profile-username')).toHaveText(auth.user.username)
    await expect(page.getByTestId('profile-account')).toContainText('学生')
    const body = await page.locator('body').innerText()
    expect(body).not.toContain('password')
    expect(body).not.toContain('passwordHash')

    await page.getByTestId('profile-realname').fill('E2E 语音学生')
    await page.getByTestId('profile-major').fill('软件工程（E2E）')
    await expect(page.getByTestId('profile-save')).toBeEnabled()
    await page.getByTestId('profile-save').click()
    await expect(page.getByTestId('profile-account')).toContainText('E2E 语音学生', {
      timeout: 15_000,
    })
    await expect(page.getByTestId('profile-account')).toContainText('软件工程（E2E）')

    await page.getByTestId('profile-logout').click()
    const dialog = page.locator('.el-message-box')
    await expect(dialog).toBeVisible()
    await dialog.getByRole('button', { name: '退出登录' }).click()
    await expect(page).toHaveURL(/\/login/, { timeout: 15_000 })
    const stored = await page.evaluate(() => window.localStorage.getItem('ai-interview:auth'))
    expect(stored).toBeNull()

    expect(errors).toEqual([])
  })
})
