#!/usr/bin/env node
/**
 * F6-3 契约检查（前端计划 §9.5）：
 * - 对后端 39 个 REST 接口做关键 shape / 错误码断言；
 * - 只依赖 Node 22 内置 fetch（无第三方依赖）；
 * - 结果写入 artifacts/contract-check.json，任一断言失败退出码为 1。
 *
 * 前置：后端可达（默认 http://127.0.0.1:18080），种子账号 demo01/admin 可用。
 * 用法：
 *   node scripts/contract-check.mjs
 *   CONTRACT_BASE_URL=http://127.0.0.1:8080 node scripts/contract-check.mjs
 *
 * 说明：脚本会创建带 E2E_CONTRACT_/e2e_contract_ 前缀的临时数据（用户/岗位/题目/知识片段/
 * 学习资源/面试会话），并在本次会话内尽量停用/逻辑删；最终由 qa/cleanup_e2e_data.sh 统一清理。
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const BASE = (process.env.CONTRACT_BASE_URL || 'http://127.0.0.1:18080').replace(/\/$/, '')
const OUT = path.resolve('artifacts/contract-check.json')
const RUN = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
const PASSWORD = 'Contract@123456'
const STUDENT = { username: `e2e_contract_${RUN}`, password: PASSWORD }
const POSITION_CODE = `E2E_CONTRACT_${RUN.toUpperCase()}`
const POSITION_NAME = `E2E-CONTRACT 岗位 ${RUN}`
const QUESTION_CONTENT = `E2E-CONTRACT 题目 ${RUN}`
const KNOWLEDGE_TITLE = `E2E-CONTRACT 知识片段 ${RUN}`
const RESOURCE_TITLE = `E2E-CONTRACT 学习资源 ${RUN}`

const STARTED_AT = new Date().toISOString()
const results = []
const ctx = {
  demo: null,
  admin: null,
  student: null,
  studentToken: null,
  adminToken: null,
  textSessionId: null,
  audioSessionId: null,
  audioMessageId: null,
  questionId: null,
  positionId: null,
  resourceId: null,
  userId: null,
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

function assertHttp(response, status) {
  assert(
    response.status === status,
    `HTTP 期望 ${status}，实际 ${response.status}（body=${truncate(response.text)}）`,
  )
}

function assertCode(response, code) {
  assert(response.json != null, `响应不是 JSON：${truncate(response.text)}`)
  assert(
    response.json.code === code,
    `code 期望 ${code}，实际 ${response.json.code}（${response.json.message ?? ''}）`,
  )
}

function assertData(response) {
  assert(response.json?.data != null, 'data 为空')
  return response.json.data
}

function isNumber(value) {
  return typeof value === 'number' && Number.isFinite(value)
}

function isString(value) {
  return typeof value === 'string'
}

function isPage(value) {
  return (
    value != null &&
    Array.isArray(value.records) &&
    isNumber(value.total) &&
    isNumber(value.current) &&
    isNumber(value.size) &&
    isNumber(value.pages)
  )
}

function truncate(text, max = 300) {
  if (typeof text !== 'string') return ''
  return text.length > max ? `${text.slice(0, max)}…` : text
}

async function http(method, pathname, { token, body, form, headers = {} } = {}) {
  const init = { method, headers: { ...headers } }
  if (token) init.headers.Authorization = `Bearer ${token}`
  if (form) {
    init.body = form
  } else if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(body)
  }
  const response = await fetch(`${BASE}${pathname}`, init)
  const contentType = response.headers.get('content-type') || ''
  let text = ''
  let json = null
  if (contentType.includes('application/json')) {
    text = await response.text()
    try {
      json = JSON.parse(text)
    } catch {
      json = null
    }
  } else if (!contentType.startsWith('audio/')) {
    text = await response.text()
  }
  return { status: response.status, headers: response.headers, text, json }
}

async function check(id, name, method, pathname, fn) {
  const started = Date.now()
  try {
    const detail = await fn()
    results.push({
      id,
      name,
      endpoint: `${method} ${pathname}`,
      ok: true,
      ms: Date.now() - started,
      detail: detail ?? null,
    })
    console.log(`✅ ${id} ${name} · ${method} ${pathname}`)
  } catch (error) {
    results.push({
      id,
      name,
      endpoint: `${method} ${pathname}`,
      ok: false,
      ms: Date.now() - started,
      error: error instanceof Error ? error.message : String(error),
    })
    console.error(`❌ ${id} ${name} · ${method} ${pathname}：${error.message}`)
  }
}

async function login(username, password) {
  const response = await http('POST', '/api/auth/login', { body: { username, password } })
  assertHttp(response, 200)
  assertCode(response, 0)
  const data = assertData(response)
  assert(isString(data.token) && data.token.length > 20, '登录 token 不是字符串')
  assert(isNumber(data.expiresAt), '登录 expiresAt 不是 number')
  assert(data.user != null && isString(data.user.role), '登录 user/role 缺失')
  return data
}

async function pollReport(sessionId, token, timeoutMs = 60_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const response = await http('GET', `/api/interviews/${sessionId}/report`, { token })
    if (response.json?.code === 0) return response.json.data
    assert(
      response.json?.code === 10004,
      `报告返回非 10004 的失败：code=${response.json?.code} ${response.json?.message ?? ''}`,
    )
    await sleep(500)
  }
  throw new Error('报告 60 秒内未就绪（10004）')
}

async function pollAudioMessage(sessionId, token, timeoutMs = 15_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const response = await http('GET', `/api/interviews/${sessionId}/messages`, { token })
    assertCode(response, 0)
    const messages = assertData(response)
    assert(Array.isArray(messages), 'messages 不是数组')
    const audio = messages.find(
      (item) => item.speaker === 'USER' && item.inputType === 'AUDIO' && isNumber(item.id),
    )
    if (audio) return audio
    await sleep(300)
  }
  throw new Error('音频 USER 消息未在 15 秒内出现')
}

async function main() {
  console.log(`契约检查后端：${BASE}`)
  console.log(`临时数据前缀：e2e_contract_${RUN} / E2E_CONTRACT_*`)

  // ===== 准备登录态（不占用 39 接口的断言名额） =====
  try {
    ctx.demo = await login('demo01', '123456')
    ctx.admin = await login('admin', 'Admin@123456')
    ctx.adminToken = ctx.admin.token
    console.log('🔑 demo01/admin 登录成功')
  } catch (error) {
    console.error(`❌ 种子账号登录失败：${error.message}`)
    results.push({
      id: 'PRE',
      name: '种子账号登录',
      endpoint: 'POST /api/auth/login',
      ok: false,
      error: error.message,
    })
  }

  // ===== 1~5 认证与用户 =====
  await check('01', '注册返回 UserVO（不含 token）', 'POST', '/api/auth/register', async () => {
    const response = await http('POST', '/api/auth/register', {
      body: {
        username: STUDENT.username,
        password: STUDENT.password,
        realName: 'E2E 契约学生',
        major: '软件工程',
      },
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(isNumber(data.id), '注册返回 id 不是 number')
    assert(data.token === undefined, '注册不应返回 token')
    ctx.userId = data.id
    return { id: data.id, username: data.username, role: data.role }
  })

  await check('02', '登录返回 token/expiresAt/user', 'POST', '/api/auth/login', async () => {
    ctx.student = await login(STUDENT.username, STUDENT.password)
    assert(ctx.student.user.role === 'STUDENT', '注册用户角色不是 STUDENT')
    ctx.studentToken = ctx.student.token
    return { role: ctx.student.user.role, expiresAt: ctx.student.expiresAt }
  })

  await check('04', '当前用户字段', 'GET', '/api/users/me', async () => {
    const response = await http('GET', '/api/users/me', { token: ctx.studentToken })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(data.username === STUDENT.username, 'username 不匹配')
    assert(isString(data.role) && isNumber(data.status), 'role/status 字段缺失')
    return { username: data.username, role: data.role }
  })

  await check('05', '修改资料回显', 'PUT', '/api/users/me', async () => {
    const response = await http('PUT', '/api/users/me', {
      token: ctx.studentToken,
      body: { realName: 'E2E 契约（已改）', major: '计算机科学与技术' },
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(data.realName === 'E2E 契约（已改）', 'realName 未回显')
    return { realName: data.realName, major: data.major }
  })

  // ===== 6~8 岗位 =====
  await check('06', '启用岗位数组', 'GET', '/api/positions', async () => {
    const response = await http('GET', '/api/positions', { token: ctx.demo.token })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(Array.isArray(data) && data.length >= 2, '岗位列表不足 2 个')
    data.forEach((item) => {
      assert(isNumber(item.id) && isString(item.code), '岗位缺少 id/code')
      assert(isNumber(item.defaultRounds), 'defaultRounds 不是 number')
    })
    return { count: data.length, codes: data.map((item) => item.code) }
  })

  await check('07', '岗位详情含 skillCount', 'GET', '/api/positions/1', async () => {
    const response = await http('GET', '/api/positions/1', { token: ctx.demo.token })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(isNumber(data.id) && isNumber(data.skillCount), 'id/skillCount 缺失')
    assert(data.skillCount > 0, 'skillCount 应大于 0')
    return { id: data.id, skillCount: data.skillCount }
  })

  await check('08', '岗位技能权重字段', 'GET', '/api/positions/1/skills', async () => {
    const response = await http('GET', '/api/positions/1/skills', { token: ctx.demo.token })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(Array.isArray(data) && data.length > 0, '技能数组为空')
    data.forEach((item) => {
      assert(isString(item.skillCode), 'skillCode 缺失')
      assert(isNumber(item.weight), 'weight 不是 number')
    })
    return { count: data.length, first: data[0].skillCode }
  })

  // ===== 9~18 面试 =====
  await check('09', '创建 TEXT 会话（CREATED）', 'POST', '/api/interviews', async () => {
    const response = await http('POST', '/api/interviews', {
      token: ctx.studentToken,
      body: { positionId: 1, mode: 'TEXT', difficulty: 2, rounds: 5, durationMinutes: 15 },
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(isNumber(data.id), '会话 id 不是 number')
    assert(data.status === 'CREATED', `status 期望 CREATED，实际 ${data.status}`)
    assert(isNumber(data.plannedRounds) && data.plannedRounds === 5, 'plannedRounds 不符')
    ctx.textSessionId = data.id
    return { id: data.id, status: data.status, plannedRounds: data.plannedRounds }
  })

  await check('10', 'start 返回首题 MessageVO', 'POST', '/api/interviews/{id}/start', async () => {
    const response = await http('POST', `/api/interviews/${ctx.textSessionId}/start`, {
      token: ctx.studentToken,
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(isNumber(data.id) && data.speaker === 'AI', '首题消息字段不符')
    assert(isString(data.contentText) && data.contentText.length > 0, '首题内容为空')
    return { messageId: data.id, roundNo: data.roundNo, speaker: data.speaker }
  })

  await check(
    '16',
    '会话详情含 remainSeconds/currentRound',
    'GET',
    '/api/interviews/{id}',
    async () => {
      const response = await http('GET', `/api/interviews/${ctx.textSessionId}`, {
        token: ctx.studentToken,
      })
      assertHttp(response, 200)
      assertCode(response, 0)
      const data = assertData(response)
      assert(isNumber(data.currentRound), 'currentRound 不是 number')
      assert(isNumber(data.remainSeconds), 'remainSeconds 不是 number')
      assert(data.status === 'WAITING_USER', `status 期望 WAITING_USER，实际 ${data.status}`)
      return {
        status: data.status,
        currentRound: data.currentRound,
        remainSeconds: data.remainSeconds,
      }
    },
  )

  await check(
    '17',
    '消息列表（可按 roundNo 过滤）',
    'GET',
    '/api/interviews/{id}/messages',
    async () => {
      const response = await http(
        'GET',
        `/api/interviews/${ctx.textSessionId}/messages?roundNo=0`,
        {
          token: ctx.studentToken,
        },
      )
      assertHttp(response, 200)
      assertCode(response, 0)
      const data = assertData(response)
      assert(Array.isArray(data) && data.length >= 1, '消息数组为空')
      data.forEach((item) => {
        assert(isNumber(item.id) && isString(item.speaker), '消息缺少 id/speaker')
        assert(item.roundNo === 0, 'roundNo 过滤未生效')
      })
      return { count: data.length }
    },
  )

  await check('11', '文字回答异步受理', 'POST', '/api/interviews/{id}/messages/text', async () => {
    const response = await http('POST', `/api/interviews/${ctx.textSessionId}/messages/text`, {
      token: ctx.studentToken,
      body: { roundNo: 0, content: '就是加锁吧，别的我不太确定。' },
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(data.accepted === true, 'accepted 不为 true')
    assert(data.roundNo === 0, 'roundNo 回显不符')
    return { accepted: data.accepted, roundNo: data.roundNo }
  })

  await check('14', 'finish 异步受理', 'POST', '/api/interviews/{id}/finish', async () => {
    const response = await http('POST', `/api/interviews/${ctx.textSessionId}/finish`, {
      token: ctx.studentToken,
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(data.accepted === true, 'accepted 不为 true')
    assert(data.status === 'FINISHED', `status 期望 FINISHED，实际 ${data.status}`)
    return { accepted: data.accepted, status: data.status }
  })

  await check(
    '18',
    '报告关键 shape（10004 → 就绪）',
    'GET',
    '/api/interviews/{id}/report',
    async () => {
      const data = await pollReport(ctx.textSessionId, ctx.studentToken)
      assert(isNumber(data.reportId) && isNumber(data.sessionId), 'reportId/sessionId 缺失')
      assert(isNumber(data.totalScore), 'totalScore 不是 number')
      assert(data.dimensionScores != null && typeof data.dimensionScores === 'object', '维度缺失')
      assert(Array.isArray(data.questionEvidence), 'questionEvidence 不是数组')
      assert(Array.isArray(data.strengths), 'strengths 不是数组')
      assert(Array.isArray(data.weaknesses), 'weaknesses 不是数组')
      assert(Array.isArray(data.improvementPlan), 'improvementPlan 不是数组')
      const evidence = data.questionEvidence[0]
      if (evidence) {
        assert(isNumber(evidence.roundNo), '证据 roundNo 缺失')
        assert(Array.isArray(evidence.coveredPoints), '证据 coveredPoints 不是数组')
        assert(Array.isArray(evidence.missingPoints), '证据 missingPoints 不是数组')
      }
      return {
        reportId: data.reportId,
        totalScore: data.totalScore,
        dimensions: Object.keys(data.dimensionScores),
        evidenceCount: data.questionEvidence.length,
      }
    },
  )

  await check(
    '12',
    '音频回答 multipart 受理',
    'POST',
    '/api/interviews/{id}/messages/audio',
    async () => {
      const created = await http('POST', '/api/interviews', {
        token: ctx.studentToken,
        body: { positionId: 2, mode: 'AUDIO', difficulty: 2, rounds: 5, durationMinutes: 15 },
      })
      assertHttp(created, 200)
      assertCode(created, 0)
      ctx.audioSessionId = assertData(created).id

      const started = await http('POST', `/api/interviews/${ctx.audioSessionId}/start`, {
        token: ctx.studentToken,
      })
      assertHttp(started, 200)
      assertCode(started, 0)

      const form = new FormData()
      form.append('file', new Blob([new Uint8Array(2048)], { type: 'audio/webm' }), 'answer.webm')
      form.append('roundNo', '0')
      form.append(
        'asrText',
        '虚拟 DOM 是 JS 对象表示的树结构，diff 通过同层比较最小化真实 DOM 操作，key 帮助识别节点复用。',
      )
      form.append('durationMs', '60000')
      form.append('pauseCount', '1')

      const response = await http('POST', `/api/interviews/${ctx.audioSessionId}/messages/audio`, {
        token: ctx.studentToken,
        form,
      })
      assertHttp(response, 200)
      assertCode(response, 0)
      const data = assertData(response)
      assert(data.accepted === true, 'accepted 不为 true')
      return { accepted: data.accepted, sessionId: ctx.audioSessionId }
    },
  )

  await check(
    '13',
    '受保护音频回放（binary）',
    'GET',
    '/api/interviews/{id}/messages/{mid}/audio',
    async () => {
      const message = await pollAudioMessage(ctx.audioSessionId, ctx.studentToken)
      ctx.audioMessageId = message.id
      const response = await fetch(
        `${BASE}/api/interviews/${ctx.audioSessionId}/messages/${message.id}/audio`,
        { headers: { Authorization: `Bearer ${ctx.studentToken}` } },
      )
      assert(response.status === 200, `音频回放 HTTP ${response.status}`)
      const contentType = response.headers.get('content-type') || ''
      assert(contentType.startsWith('audio/'), `音频 Content-Type 异常：${contentType}`)
      const bytes = new Uint8Array(await response.arrayBuffer())
      assert(bytes.byteLength > 0, '音频内容为空')
      return { messageId: message.id, contentType, bytes: bytes.byteLength }
    },
  )

  await check('E10004', '报告未生成 → 10004', 'GET', '/api/interviews/{id}/report', async () => {
    const response = await http('GET', `/api/interviews/${ctx.audioSessionId}/report`, {
      token: ctx.studentToken,
    })
    assertHttp(response, 200)
    assertCode(response, 10004)
    return { code: response.json.code }
  })

  await check('15', 'abort 返回 ABORTED 详情', 'POST', '/api/interviews/{id}/abort', async () => {
    const response = await http('POST', `/api/interviews/${ctx.audioSessionId}/abort`, {
      token: ctx.studentToken,
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(data.status === 'ABORTED', `status 期望 ABORTED，实际 ${data.status}`)
    return { id: data.id, status: data.status }
  })

  // ===== 19~21 成长 =====
  await check('19', '历史分页 Page shape', 'GET', '/api/users/me/history', async () => {
    const response = await http('GET', '/api/users/me/history?page=1&size=5', {
      token: ctx.studentToken,
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(isPage(data), '历史不是 Page 结构')
    assert(data.records.length <= 5, 'size 未生效')
    return { total: data.total, size: data.size, current: data.current }
  })

  await check('20', '成长曲线数组 shape', 'GET', '/api/users/me/growth', async () => {
    const response = await http('GET', '/api/users/me/growth?positionId=1', {
      token: ctx.studentToken,
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(Array.isArray(data) && data.length > 0, '成长数组为空')
    data.forEach((item) => {
      assert(isString(item.skillCode), 'skillCode 缺失')
      assert(Array.isArray(item.records), 'records 不是数组')
      assert(item.current === null || isNumber(item.current), 'current 不是 number/null')
    })
    return { count: data.length, first: data[0].skillCode }
  })

  await check('21', '推荐数组 shape', 'GET', '/api/users/me/recommendations', async () => {
    const response = await http('GET', '/api/users/me/recommendations?positionId=1', {
      token: ctx.studentToken,
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(Array.isArray(data) && data.length >= 1, '推荐数组为空')
    data.forEach((item) => {
      assert(isString(item.skillCode), 'skillCode 缺失')
      assert(isNumber(item.priority), 'priority 不是 number')
      assert(Array.isArray(item.weaknessReasons), 'weaknessReasons 不是数组')
      assert(Array.isArray(item.resources), 'resources 不是数组')
      assert(isString(item.nextInterviewSuggested), 'nextInterviewSuggested 缺失')
    })
    return { count: data.length, top: data[0].skillCode }
  })

  // ===== 22~39 管理端 =====
  await check('28', '看板四卡 + 近 7 日', 'GET', '/api/admin/dashboard', async () => {
    const response = await http('GET', '/api/admin/dashboard', { token: ctx.adminToken })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(isNumber(data.userCount) && isNumber(data.sessionCount), '用户/场次统计缺失')
    assert(isNumber(data.questionCount), '题量统计缺失')
    assert(data.avgScore === null || isNumber(data.avgScore), 'avgScore 不是 number/null')
    assert(
      Array.isArray(data.recent7Days) && data.recent7Days.length === 7,
      'recent7Days 不是 7 天',
    )
    return { users: data.userCount, sessions: data.sessionCount, days: data.recent7Days.length }
  })

  await check('29', '管理端岗位 Page（含技能/权重）', 'GET', '/api/admin/positions', async () => {
    const response = await http('GET', '/api/admin/positions?page=1&size=100', {
      token: ctx.adminToken,
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(isPage(data), '岗位不是 Page 结构')
    const seed = data.records.find((item) => item.id === 1)
    assert(seed != null && Array.isArray(seed.skills), '岗位 1 缺少 skills')
    assert(seed.scoreWeights != null, '岗位 1 缺少 scoreWeights')
    return { total: data.total, skills: seed.skills.length }
  })

  await check('30', '管理端岗位详情', 'GET', '/api/admin/positions/{id}', async () => {
    const response = await http('GET', '/api/admin/positions/1', { token: ctx.adminToken })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(Array.isArray(data.skills) && data.skills.length > 0, 'skills 为空')
    assert(data.scoreWeights?.content != null, 'scoreWeights.content 缺失')
    return { id: data.id, code: data.code, enabled: data.enabled }
  })

  await check('22', '管理端题库 Page', 'GET', '/api/admin/questions', async () => {
    const response = await http(
      'GET',
      '/api/admin/questions?positionId=1&type=TECHNICAL&difficulty=3&page=1&size=5',
      { token: ctx.adminToken },
    )
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(isPage(data), '题库不是 Page 结构')
    data.records.forEach((item) => {
      assert(isNumber(item.id) && isString(item.content), '题目缺少 id/content')
      assert(isNumber(item.version) && isString(item.status), '题目缺少 version/status')
    })
    return { total: data.total, size: data.size }
  })

  await check('23', '新增题目 version=1', 'POST', '/api/admin/questions', async () => {
    const response = await http('POST', '/api/admin/questions', {
      token: ctx.adminToken,
      body: {
        positionId: 1,
        type: 'TECHNICAL',
        content: QUESTION_CONTENT,
        difficulty: 3,
        skillTags: ['java_basics'],
        keyPoints: ['契约检查得分点'],
        deductionPoints: ['契约检查失分点'],
        followupTemplates: ['契约检查追问'],
      },
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(isNumber(data.id) && data.version === 1, '新增题目 version 不为 1')
    ctx.questionId = data.id
    return { id: data.id, version: data.version, status: data.status }
  })

  await check(
    '24',
    '更新题目 version+1（旧版本 40003）',
    'PUT',
    '/api/admin/questions/{id}',
    async () => {
      const response = await http('PUT', `/api/admin/questions/${ctx.questionId}`, {
        token: ctx.adminToken,
        body: {
          positionId: 1,
          type: 'TECHNICAL',
          content: `${QUESTION_CONTENT}（已更新）`,
          difficulty: 4,
          skillTags: ['java_basics'],
          keyPoints: ['契约检查得分点'],
          deductionPoints: ['契约检查失分点'],
          followupTemplates: ['契约检查追问'],
          version: 1,
        },
      })
      assertHttp(response, 200)
      assertCode(response, 0)
      const data = assertData(response)
      assert(data.version === 2, `更新后 version 期望 2，实际 ${data.version}`)
      assert(data.content === `${QUESTION_CONTENT}（已更新）`, '更新内容未回显')

      const conflict = await http('PUT', `/api/admin/questions/${ctx.questionId}`, {
        token: ctx.adminToken,
        body: {
          positionId: 1,
          type: 'TECHNICAL',
          content: `${QUESTION_CONTENT}（冲突）`,
          difficulty: 4,
          skillTags: ['java_basics'],
          version: 1,
        },
      })
      assertHttp(conflict, 200)
      assertCode(conflict, 40003)
      return { version: data.version, conflictCode: conflict.json.code }
    },
  )

  await check('25', '逻辑删除题目（DISABLED）', 'DELETE', '/api/admin/questions/{id}', async () => {
    const response = await http('DELETE', `/api/admin/questions/${ctx.questionId}`, {
      token: ctx.adminToken,
    })
    assertHttp(response, 200)
    assertCode(response, 0)

    const list = await http('GET', '/api/admin/questions?positionId=1&page=1&size=100', {
      token: ctx.adminToken,
    })
    assertCode(list, 0)
    const question = assertData(list).records.find((item) => item.id === ctx.questionId)
    assert(question != null, '逻辑删后题目行不存在')
    assert(question.status === 'DISABLED', `status 期望 DISABLED，实际 ${question.status}`)
    return { id: ctx.questionId, status: question.status }
  })

  await check('26', '新增知识片段', 'POST', '/api/admin/knowledge', async () => {
    const response = await http('POST', '/api/admin/knowledge', {
      token: ctx.adminToken,
      body: {
        positionId: 1,
        title: KNOWLEDGE_TITLE,
        content:
          '契约检查用知识片段：缓存穿透、缓存击穿与缓存雪崩的区别与常见防护手段，包括布隆过滤器、互斥锁、逻辑过期与随机过期时间。',
        keywords: '契约检查,缓存穿透,缓存击穿,缓存雪崩,布隆过滤器',
        skillTags: 'redis_mq',
        difficulty: 3,
      },
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(isNumber(data.id) && data.title === KNOWLEDGE_TITLE, '知识片段回显不符')
    return { id: data.id, title: data.title }
  })

  await check('27', '重建知识索引', 'POST', '/api/admin/knowledge/rebuild', async () => {
    const response = await http('POST', '/api/admin/knowledge/rebuild', { token: ctx.adminToken })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(data.rebuilt === true, 'rebuilt 不为 true')
    return { rebuilt: data.rebuilt }
  })

  await check('31', '新增岗位（技能权重和=1）', 'POST', '/api/admin/positions', async () => {
    const response = await http('POST', '/api/admin/positions', {
      token: ctx.adminToken,
      body: {
        code: POSITION_CODE,
        name: POSITION_NAME,
        description: '契约检查临时岗位，检查结束后停用。',
        defaultRounds: 5,
        enabled: 1,
        skills: [
          {
            skillCode: 'contract_skill',
            skillName: '契约检查技能',
            description: '临时技能',
            weight: 1,
            requiredLevel: 60,
          },
        ],
      },
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(isNumber(data.id) && data.code === POSITION_CODE, '岗位回显不符')
    assert(data.skills?.length === 1, '岗位技能未回显')
    ctx.positionId = data.id
    return { id: data.id, code: data.code, enabled: data.enabled }
  })

  await check('32', '部分更新岗位', 'PUT', '/api/admin/positions/{id}', async () => {
    const response = await http('PUT', `/api/admin/positions/${ctx.positionId}`, {
      token: ctx.adminToken,
      body: { name: `${POSITION_NAME}（更新）`, defaultRounds: 6 },
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(data.name === `${POSITION_NAME}（更新）`, 'name 未更新')
    assert(data.defaultRounds === 6, 'defaultRounds 未更新')
    return { id: data.id, name: data.name, defaultRounds: data.defaultRounds }
  })

  await check(
    '33',
    '调整评分权重 scoreWeights',
    'PUT',
    '/api/admin/positions/{id}/weights',
    async () => {
      const position = await http('GET', '/api/admin/positions/1', { token: ctx.adminToken })
      assertCode(position, 0)
      const weights = assertData(position).scoreWeights
      assert(weights != null, '岗位 1 无 scoreWeights')

      const response = await http('PUT', `/api/admin/positions/${ctx.positionId}/weights`, {
        token: ctx.adminToken,
        body: { scoreWeights: weights },
      })
      assertHttp(response, 200)
      assertCode(response, 0)
      const data = assertData(response)
      assert(isNumber(data.scoreWeights?.content?.technical), '权重回读失败')
      return {
        id: data.id,
        technical: data.scoreWeights.content.technical,
        totalContent: data.scoreWeights.total?.content,
      }
    },
  )

  await check('34', '管理端资源 Page', 'GET', '/api/admin/resources', async () => {
    const response = await http(
      'GET',
      '/api/admin/resources?positionId=1&skillCode=java_basics&resourceType=ARTICLE&page=1&size=5',
      { token: ctx.adminToken },
    )
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(isPage(data), '资源不是 Page 结构')
    return { total: data.total, size: data.size }
  })

  await check('35', '新增学习资源', 'POST', '/api/admin/resources', async () => {
    const response = await http('POST', '/api/admin/resources', {
      token: ctx.adminToken,
      body: {
        positionId: 1,
        skillCode: 'java_basics',
        title: RESOURCE_TITLE,
        resourceType: 'ARTICLE',
        difficulty: 2,
      },
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(isNumber(data.id) && data.title === RESOURCE_TITLE, '资源回显不符')
    assert(data.enabled === 1, '新资源 enabled 应为 1')
    ctx.resourceId = data.id
    return { id: data.id, title: data.title, enabled: data.enabled }
  })

  await check('36', '全量更新学习资源', 'PUT', '/api/admin/resources/{id}', async () => {
    const response = await http('PUT', `/api/admin/resources/${ctx.resourceId}`, {
      token: ctx.adminToken,
      body: {
        positionId: 1,
        skillCode: 'java_basics',
        title: `${RESOURCE_TITLE}（已更新）`,
        resourceType: 'QUESTION',
        contentUrl: 'https://example.com/contract-check',
        difficulty: 3,
        enabled: 1,
      },
    })
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(data.title === `${RESOURCE_TITLE}（已更新）`, 'title 未更新')
    assert(data.resourceType === 'QUESTION', 'resourceType 未更新')
    return { id: data.id, title: data.title, resourceType: data.resourceType }
  })

  await check(
    '37',
    '下架学习资源（enabled=0）',
    'DELETE',
    '/api/admin/resources/{id}',
    async () => {
      const response = await http('DELETE', `/api/admin/resources/${ctx.resourceId}`, {
        token: ctx.adminToken,
      })
      assertHttp(response, 200)
      assertCode(response, 0)

      const list = await http('GET', '/api/admin/resources?positionId=1&page=1&size=100', {
        token: ctx.adminToken,
      })
      assertCode(list, 0)
      const resource = assertData(list).records.find((item) => item.id === ctx.resourceId)
      assert(resource != null, '下架后资源行不存在')
      assert(resource.enabled === 0, `enabled 期望 0，实际 ${resource.enabled}`)
      return { id: ctx.resourceId, enabled: resource.enabled }
    },
  )

  await check('38', '管理端用户搜索 Page', 'GET', '/api/admin/users', async () => {
    const response = await http(
      'GET',
      `/api/admin/users?keyword=${encodeURIComponent(STUDENT.username)}&page=1&size=10`,
      { token: ctx.adminToken },
    )
    assertHttp(response, 200)
    assertCode(response, 0)
    const data = assertData(response)
    assert(isPage(data), '用户不是 Page 结构')
    const user = data.records.find((item) => item.username === STUDENT.username)
    assert(user != null, '搜索不到临时用户')
    return { total: data.total, userId: user.id }
  })

  await check('39', '用户启用/禁用', 'PUT', '/api/admin/users/{id}/status', async () => {
    const disabled = await http('PUT', `/api/admin/users/${ctx.userId}/status`, {
      token: ctx.adminToken,
      body: { status: 0 },
    })
    assertHttp(disabled, 200)
    assertCode(disabled, 0)
    assert(assertData(disabled).status === 0, '禁用未生效')

    const enabled = await http('PUT', `/api/admin/users/${ctx.userId}/status`, {
      token: ctx.adminToken,
      body: { status: 1 },
    })
    assertHttp(enabled, 200)
    assertCode(enabled, 0)
    assert(assertData(enabled).status === 1, '启用未生效')
    return { userId: ctx.userId, status: assertData(enabled).status }
  })

  // ===== 收尾：停用临时岗位，登出并验证 token 失效 =====
  if (ctx.positionId != null) {
    const disabled = await http('PUT', `/api/admin/positions/${ctx.positionId}`, {
      token: ctx.adminToken,
      body: { enabled: 0 },
    })
    if (disabled.json?.code === 0) {
      console.log(`🧹 已停用临时岗位 id=${ctx.positionId}`)
    }
  }

  // ===== 错误码 =====
  await check('E10002', '错误密码 → 10002', 'POST', '/api/auth/login', async () => {
    const response = await http('POST', '/api/auth/login', {
      body: { username: 'demo01', password: 'definitely-wrong' },
    })
    assertHttp(response, 200)
    assertCode(response, 10002)
    return { code: response.json.code }
  })

  await check('E40101', '无 token → HTTP 401/40101', 'GET', '/api/users/me', async () => {
    const response = await http('GET', '/api/users/me')
    assertHttp(response, 401)
    assertCode(response, 40101)
    return { http: response.status, code: response.json.code }
  })

  await check(
    'E40301',
    '学生访问管理端 → HTTP 403/40301',
    'GET',
    '/api/admin/dashboard',
    async () => {
      const response = await http('GET', '/api/admin/dashboard', { token: ctx.demo.token })
      assertHttp(response, 403)
      assertCode(response, 40301)
      return { http: response.status, code: response.json.code }
    },
  )

  await check('E40001', '参数校验 → HTTP 400/40001', 'POST', '/api/interviews', async () => {
    const response = await http('POST', '/api/interviews', {
      token: ctx.demo.token,
      body: {},
    })
    assertHttp(response, 400)
    assertCode(response, 40001)
    return { http: response.status, code: response.json.code }
  })

  await check('E10005', '重复 start → 10005', 'POST', '/api/interviews/{id}/start', async () => {
    const response = await http('POST', `/api/interviews/${ctx.textSessionId}/start`, {
      token: ctx.studentToken,
    })
    assertHttp(response, 200)
    assertCode(response, 10005)
    return { code: response.json.code }
  })

  await check('03', '登出成功并拉黑旧 token', 'POST', '/api/auth/logout', async () => {
    const response = await http('POST', '/api/auth/logout', { token: ctx.studentToken })
    assertHttp(response, 200)
    assertCode(response, 0)
    const expired = await http('GET', '/api/users/me', { token: ctx.studentToken })
    assertHttp(expired, 401)
    assertCode(expired, 40101)
    return { code: response.json.code, afterLogout: expired.json.code }
  })

  await check('E40401', '不存在的会话 → 40401', 'GET', '/api/interviews/999999', async () => {
    const response = await http('GET', '/api/interviews/999999', { token: ctx.demo.token })
    assertHttp(response, 200)
    assertCode(response, 40401)
    return { code: response.json.code }
  })

  const endpointResults = results.filter((item) => /^\d{2}$/.test(item.id))
  const errorResults = results.filter((item) => item.id.startsWith('E'))
  const failed = results.filter((item) => !item.ok)
  const summary = {
    baseUrl: BASE,
    startedAt: STARTED_AT,
    finishedAt: new Date().toISOString(),
    endpoints: {
      total: endpointResults.length,
      passed: endpointResults.filter((i) => i.ok).length,
    },
    errorCodes: { total: errorResults.length, passed: errorResults.filter((i) => i.ok).length },
    failed: failed.length,
    temporaryData: {
      username: STUDENT.username,
      positionCode: POSITION_CODE,
      questionId: ctx.questionId,
      resourceId: ctx.resourceId,
      textSessionId: ctx.textSessionId,
      audioSessionId: ctx.audioSessionId,
    },
    results,
  }

  mkdirSync(path.dirname(OUT), { recursive: true })
  writeFileSync(OUT, `${JSON.stringify(summary, null, 2)}\n`, 'utf8')

  console.log('\n================ 契约检查汇总 ================')
  console.log(`REST 接口：${summary.endpoints.passed}/${summary.endpoints.total}`)
  console.log(`错误码：  ${summary.errorCodes.passed}/${summary.errorCodes.total}`)
  console.log(`失败：    ${summary.failed}`)
  console.log(`报告：    ${OUT}`)
  if (failed.length > 0) {
    console.error('\n失败明细：')
    failed.forEach((item) => console.error(`- ${item.id} ${item.endpoint}: ${item.error}`))
    process.exitCode = 1
  }
}

main().catch((error) => {
  console.error(`契约检查异常退出：${error instanceof Error ? error.stack : error}`)
  process.exitCode = 1
})
