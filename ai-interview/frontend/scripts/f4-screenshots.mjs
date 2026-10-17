/**
 * F4 验收截图脚本（可重复执行）：
 * - 注册独立截图账号（shots_f4_*），完成两场 AUDIO 面试（含录音/转写/提交/报告）；
 * - 输出 docs/screenshots/f4/ 下的房间、报告、成长、推荐、个人中心与移动端截图；
 * - 每页检查横向滚动，输出结果到控制台；
 * - 结束后中止未完成会话；账号与数据由 SQL 清理（见 F4 验收记录）。
 *
 * 前置：后端 18080、前端 5173 已启动；执行 `node scripts/f4-screenshots.mjs`。
 */
import { mkdirSync } from 'node:fs'
import path from 'node:path'

import { chromium } from '@playwright/test'

const API = process.env.F4_API_BASE || 'http://127.0.0.1:18080'
const WEB = process.env.F4_WEB_BASE || 'http://127.0.0.1:5173'
const OUT = path.resolve('docs/screenshots/f4')

const USERNAME = `shots_f4_${Date.now()}`
const PASSWORD = 'Shots@123456'
const ANSWER_TEXT =
  '我会先复现问题并用监控确认影响面，再结合日志与火焰图定位根因；修复时优先保证幂等与可回滚，最后补充压测和告警，防止同类问题再次发生。'

mkdirSync(OUT, { recursive: true })

async function api(pathname, options = {}) {
  const response = await fetch(`${API}${pathname}`, options)
  const body = await response.json().catch(() => ({}))
  if (body.code !== 0) {
    throw new Error(`${pathname} -> code=${body.code} ${body.message ?? ''}`)
  }
  return body.data
}

function authHeaders(token) {
  return { authorization: `Bearer ${token}` }
}

async function registerAndLogin() {
  await api('/api/auth/register', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      username: USERNAME,
      password: PASSWORD,
      realName: '截图学生',
      major: '软件工程',
    }),
  })
  return api('/api/auth/login', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username: USERNAME, password: PASSWORD }),
  })
}

async function createSession(auth, mode = 'AUDIO') {
  return api('/api/interviews', {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...authHeaders(auth.token) },
    body: JSON.stringify({ positionId: 1, mode, difficulty: 2, rounds: 5, durationMinutes: 15 }),
  })
}

async function startSession(auth, id) {
  return api(`/api/interviews/${id}/start`, { method: 'POST', headers: authHeaders(auth.token) })
}

async function detail(auth, id) {
  return api(`/api/interviews/${id}`, { headers: authHeaders(auth.token) })
}

async function submitAudioAnswer(auth, id, roundNo, text) {
  const form = new FormData()
  form.append('file', new Blob([new Uint8Array(2048)], { type: 'audio/webm' }), 'answer.webm')
  form.append('roundNo', String(roundNo))
  form.append('asrText', text)
  form.append('durationMs', '30000')
  await api(`/api/interviews/${id}/messages/audio`, {
    method: 'POST',
    headers: authHeaders(auth.token),
    body: form,
  })
}

async function finishSession(auth, id) {
  await api(`/api/interviews/${id}/finish`, { method: 'POST', headers: authHeaders(auth.token) })
}

async function abortSession(auth, id) {
  await fetch(`${API}/api/interviews/${id}/abort`, {
    method: 'POST',
    headers: authHeaders(auth.token),
  })
}

async function waitFor(predicate, timeout = 30_000, interval = 300) {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    const value = await predicate()
    if (value) return value
    await new Promise((resolve) => setTimeout(resolve, interval))
  }
  throw new Error('等待超时')
}

async function waitStatus(auth, id, statuses) {
  return waitFor(async () => {
    const current = await detail(auth, id)
    return statuses.includes(current.status) ? current : null
  })
}

async function waitReport(auth, id) {
  return waitFor(async () => {
    const response = await fetch(`${API}/api/interviews/${id}/report`, {
      headers: authHeaders(auth.token),
    })
    const body = await response.json()
    return body.code === 0 ? body.data : null
  }, 60_000)
}

async function answerOnceByApi(auth, id, roundNo, text = ANSWER_TEXT) {
  await submitAudioAnswer(auth, id, roundNo, text)
  await waitStatus(auth, id, ['WAITING_USER', 'FINISHED', 'TIMEOUT'])
}

async function completionSession(auth) {
  const session = await createSession(auth)
  await startSession(auth, session.id)
  await answerOnceByApi(auth, session.id, 0)
  const current = await detail(auth, session.id)
  if (current.status === 'WAITING_USER') {
    await answerOnceByApi(auth, session.id, current.currentRound, ANSWER_TEXT)
  }
  await finishSession(auth, session.id)
  await waitReport(auth, session.id)
  return session
}

async function installBrowserStubs(page) {
  await page.addInitScript(
    ({ auth }) => {
      window.localStorage.setItem('ai-interview:auth', JSON.stringify(auth))

      const track = {
        kind: 'audio',
        label: 'Screenshot Fake Microphone',
        enabled: true,
        readyState: 'live',
        stop() {},
        addEventListener() {},
        removeEventListener() {},
      }
      const stream = { getTracks: () => [track], getAudioTracks: () => [track] }
      Object.defineProperty(navigator, 'mediaDevices', {
        configurable: true,
        value: { getUserMedia: async () => stream },
      })

      class FakeMediaRecorder {
        static isTypeSupported = () => true
        state = 'inactive'
        ondataavailable = null
        onstop = null
        onerror = null
        constructor(_stream, options) {
          this.mimeType = options?.mimeType ?? 'audio/webm'
        }
        start() {
          this.state = 'recording'
        }
        stop() {
          this.state = 'inactive'
          this.ondataavailable?.({
            data: new Blob([new Uint8Array(2048)], { type: this.mimeType }),
          })
          this.onstop?.(new Event('stop'))
        }
      }
      window.MediaRecorder = FakeMediaRecorder

      class FakeSpeechRecognition {
        lang = ''
        continuous = false
        interimResults = false
        maxAlternatives = 1
        onstart = null
        onresult = null
        onerror = null
        onend = null
        onspeechend = null
        start() {
          this.onstart?.()
          setTimeout(() => {
            const alternative = {
              transcript:
                '我会先复现问题并用监控确认影响面，再结合日志定位缓存击穿的根因，最后用互斥锁与逻辑过期修复并补充压测。',
            }
            const result = Object.assign([alternative], { isFinal: true, length: 1 })
            const results = Object.assign([result], { length: 1 })
            this.onresult?.({ resultIndex: 0, results })
          }, 250)
        }
        stop() {
          this.onend?.()
        }
        abort() {
          this.onend?.()
        }
      }
      window.SpeechRecognition = FakeSpeechRecognition
    },
    { auth: { token: auth.token, expiresAt: auth.expiresAt, user: auth.user } },
  )
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

const auth = await registerAndLogin()
console.log('截图账号', USERNAME)

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  locale: 'zh-CN',
  timezoneId: 'Asia/Shanghai',
})
const page = await context.newPage()
const pageErrors = []
page.on('pageerror', (error) => pageErrors.push(error.message))

// ===== 第一场：UI 录音/转写/提交 → 报告 =====
const roomSession = await createSession(auth)
await startSession(auth, roomSession.id)
await installBrowserStubs(page)
await page.goto(`${WEB}/interview/${roomSession.id}`)
await page.getByTestId('message-ai').first().waitFor({ timeout: 30_000 })
await page.getByTestId('audio-start').click()
await page.getByTestId('audio-recording').waitFor({ timeout: 10_000 })
await page.waitForTimeout(1200)
await page.getByTestId('audio-stop').click()
await page.getByTestId('audio-result').waitFor({ timeout: 10_000 })
await page.waitForTimeout(400)
await page.screenshot({ path: path.join(OUT, '01-audio-room-recording.png') })
await checkNoHorizontalScroll(page, '面试房间（1440）')

await page.getByTestId('audio-submit').click()
await waitFor(async () => {
  const response = await fetch(`${API}/api/interviews/${roomSession.id}/messages`, {
    headers: authHeaders(auth.token),
  })
  const body = await response.json()
  return (body.data ?? []).some((message) => message.speaker === 'USER')
})
await waitStatus(auth, roomSession.id, ['WAITING_USER'])
const current = await detail(auth, roomSession.id)
if (current.status === 'WAITING_USER') {
  await answerOnceByApi(auth, roomSession.id, current.currentRound)
}
await finishSession(auth, roomSession.id)
await waitReport(auth, roomSession.id)
await completionSession(auth)

// ===== 报告（voice） =====
await page.goto(`${WEB}/interview/${roomSession.id}/report`)
await page.getByTestId('report-score').waitFor({ timeout: 45_000 })
await page.waitForTimeout(500)
await page.screenshot({ path: path.join(OUT, '02-audio-report-overview.png') })
await checkNoHorizontalScroll(page, '报告（1440）')
await page.getByTestId('report-evidence').scrollIntoViewIfNeeded()
await page.waitForTimeout(300)
await page.screenshot({ path: path.join(OUT, '03-audio-report-evidence.png') })

// ===== 成长 =====
await page.goto(`${WEB}/growth?positionId=1`)
await page.getByTestId('growth-page').waitFor()
await page.getByTestId('growth-chart').locator('canvas').waitFor({ timeout: 20_000 })
await page.waitForTimeout(700)
await page.screenshot({ path: path.join(OUT, '04-growth.png') })
await checkNoHorizontalScroll(page, '成长（1440）')

await page.getByTestId('growth-position').click()
await page.getByRole('option', { name: 'Web 前端开发工程师' }).click()
await page.getByTestId('growth-empty').waitFor({ timeout: 15_000 })
await page.screenshot({ path: path.join(OUT, '05-growth-empty.png') })

// ===== 推荐 =====
await page.goto(`${WEB}/recommendations?positionId=1`)
await page.locator('[data-testid^="rec-card-"]').first().waitFor({ timeout: 20_000 })
await page.waitForTimeout(500)
await page.screenshot({ path: path.join(OUT, '06-recommendations.png') })
await checkNoHorizontalScroll(page, '推荐（1440）')
await page.locator('[data-testid^="rec-card-"]').first().scrollIntoViewIfNeeded()
await page.waitForTimeout(200)
await page.screenshot({ path: path.join(OUT, '07-recommendations-card.png') })

// ===== 个人中心 =====
await page.goto(`${WEB}/profile`)
await page.getByTestId('profile-page').waitFor()
await page.waitForTimeout(400)
await page.screenshot({ path: path.join(OUT, '08-profile.png') })
await checkNoHorizontalScroll(page, '个人中心（1440）')

// ===== 移动端面试房间（AUDIO） =====
const mobileSession = await createSession(auth)
await startSession(auth, mobileSession.id)
const mobile = await browser.newContext({
  viewport: { width: 390, height: 844 },
  locale: 'zh-CN',
  timezoneId: 'Asia/Shanghai',
  isMobile: true,
  hasTouch: true,
})
const mobilePage = await mobile.newPage()
await installBrowserStubs(mobilePage)
await mobilePage.goto(`${WEB}/interview/${mobileSession.id}`)
await mobilePage.getByTestId('audio-answer-panel').waitFor({ timeout: 30_000 })
await mobilePage.getByTestId('audio-start').click()
await mobilePage.getByTestId('audio-recording').waitFor({ timeout: 10_000 })
await mobilePage.waitForTimeout(800)
await mobilePage.getByTestId('audio-stop').click()
await mobilePage.getByTestId('audio-result').waitFor({ timeout: 10_000 })
await mobilePage.waitForTimeout(300)
await mobilePage.screenshot({ path: path.join(OUT, '09-audio-room-mobile.png'), fullPage: true })
await checkNoHorizontalScroll(mobilePage, '面试房间（390）')
await mobilePage.close()
await mobile.close()
await abortSession(auth, mobileSession.id)

console.log('截图完成，pageerror 数：', pageErrors.length)
if (pageErrors.length > 0) console.log(pageErrors)
await context.close()
await browser.close()
