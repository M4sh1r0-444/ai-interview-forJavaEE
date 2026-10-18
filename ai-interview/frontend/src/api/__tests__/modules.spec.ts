import type { AxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { RegisterPayload, UpdateMePayload } from '@/types/auth'
import type {
  KnowledgeUpsertPayload,
  PositionUpsertPayload,
  QuestionUpsertPayload,
  ResourceUpsertPayload,
  WeightsUpdatePayload,
} from '@/types/admin'
import type {
  AudioAnswerPayload,
  CreateInterviewPayload,
  TextAnswerPayload,
} from '@/types/interview'

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
  fetchBlob: vi.fn(),
}))

vi.mock('@/api/http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/http')>()
  return { ...actual, request: mocks.request, fetchBlob: mocks.fetchBlob }
})

import * as api from '@/api'

const loginPayload = { username: 'demo01', password: '123456' }
const registerPayload: RegisterPayload = {
  username: 'new_user',
  password: 'Passw0rd!',
  realName: '新同学',
  major: '软件工程',
}
const updateMePayload: UpdateMePayload = { realName: '改名', major: '计算机' }
const createInterviewPayload: CreateInterviewPayload = {
  positionId: 1,
  mode: 'TEXT',
  difficulty: 3,
  rounds: 5,
  durationMinutes: 15,
}
const textAnswerPayload: TextAnswerPayload = { roundNo: 2, content: '回答内容' }
const questionPayload: QuestionUpsertPayload = {
  positionId: 1,
  type: 'TECHNICAL',
  content: '题干',
  difficulty: 3,
  skillTags: ['java_basics'],
  keyPoints: ['得分点'],
  version: 2,
}
const positionPayload: PositionUpsertPayload = {
  code: 'E2E_POS',
  name: '测试岗位',
  defaultRounds: 5,
  skills: [{ skillCode: 'java_basics', skillName: 'Java 基础', weight: 1, requiredLevel: 70 }],
}
const weightsPayload: WeightsUpdatePayload = {
  scoreWeights: {
    content: { technical: 0.3, depth: 0.25, logic: 0.2, evidence: 0.15, match: 0.1 },
    expression: { clarity: 0.3, fluency: 0.2, pace: 0.2, confidence: 0.15, completeness: 0.15 },
    total: { content: 0.5, expression: 0.3, adaptability: 0.1, match: 0.1 },
  },
}
const resourcePayload: ResourceUpsertPayload = {
  positionId: 1,
  skillCode: 'java_basics',
  title: '学习资源',
  resourceType: 'ARTICLE',
  difficulty: 2,
  enabled: 1,
}
const knowledgePayload: KnowledgeUpsertPayload = {
  positionId: 1,
  title: '知识片段',
  content: '一段足够长的知识片段内容',
  keywords: '缓存,击穿',
  skillTags: 'redis_mq',
}

/** 最近一次 request() 的配置；API 层测试只关心请求形状。 */
function lastRequestConfig(): AxiosRequestConfig {
  const calls = mocks.request.mock.calls
  return calls[calls.length - 1]?.[0] as AxiosRequestConfig
}

beforeEach(() => {
  mocks.request.mockReset()
  mocks.fetchBlob.mockReset()
  mocks.request.mockResolvedValue(undefined)
  mocks.fetchBlob.mockResolvedValue(new Blob())
})

describe('认证与用户 API', () => {
  it('register/login/logout/getMe/updateMe 使用约定路径与方法', async () => {
    await api.register(registerPayload)
    expect(lastRequestConfig()).toEqual({
      url: '/api/auth/register',
      method: 'POST',
      data: registerPayload,
    })

    await api.login(loginPayload)
    expect(lastRequestConfig()).toEqual({
      url: '/api/auth/login',
      method: 'POST',
      data: loginPayload,
    })

    await api.logout()
    expect(lastRequestConfig()).toEqual({ url: '/api/auth/logout', method: 'POST' })

    await api.getMe()
    expect(lastRequestConfig()).toEqual({ url: '/api/users/me', method: 'GET' })

    await api.updateMe(updateMePayload)
    expect(lastRequestConfig()).toEqual({
      url: '/api/users/me',
      method: 'PUT',
      data: updateMePayload,
    })
  })

  it('透传后端返回值', async () => {
    mocks.request.mockResolvedValueOnce({ token: 't', expiresAt: 1 })
    await expect(api.login(loginPayload)).resolves.toEqual({ token: 't', expiresAt: 1 })
  })
})

describe('岗位 API', () => {
  it('列表/详情/技能三个接口', async () => {
    await api.listPositions()
    expect(lastRequestConfig()).toEqual({ url: '/api/positions', method: 'GET' })

    await api.getPosition(2)
    expect(lastRequestConfig()).toEqual({ url: '/api/positions/2', method: 'GET' })

    await api.getPositionSkills(2)
    expect(lastRequestConfig()).toEqual({ url: '/api/positions/2/skills', method: 'GET' })
  })
})

describe('面试 API', () => {
  it('创建/开始/详情/finish/abort/report', async () => {
    await api.createInterview(createInterviewPayload)
    expect(lastRequestConfig()).toEqual({
      url: '/api/interviews',
      method: 'POST',
      data: createInterviewPayload,
    })

    await api.startInterview(9)
    expect(lastRequestConfig()).toEqual({ url: '/api/interviews/9/start', method: 'POST' })

    await api.getInterview(9)
    expect(lastRequestConfig()).toEqual({ url: '/api/interviews/9', method: 'GET' })

    await api.finishInterview(9)
    expect(lastRequestConfig()).toMatchObject({
      url: '/api/interviews/9/finish',
      method: 'POST',
      timeout: 30_000,
    })

    await api.abortInterview(9)
    expect(lastRequestConfig()).toMatchObject({
      url: '/api/interviews/9/abort',
      method: 'POST',
      timeout: 30_000,
    })

    await api.getReport(9)
    expect(lastRequestConfig()).toEqual({ url: '/api/interviews/9/report', method: 'GET' })
  })

  it('文字回答带 30s 超时，roundNo 原样提交', async () => {
    await api.submitTextAnswer(9, textAnswerPayload)
    expect(lastRequestConfig()).toEqual({
      url: '/api/interviews/9/messages/text',
      method: 'POST',
      data: textAnswerPayload,
      timeout: 30_000,
    })
  })

  it('消息列表按 roundNo 可选过滤', async () => {
    await api.getMessages(9)
    expect(lastRequestConfig()).toEqual({ url: '/api/interviews/9/messages', method: 'GET' })

    await api.getMessages(9, 3)
    expect(lastRequestConfig()).toEqual({
      url: '/api/interviews/9/messages',
      method: 'GET',
      params: { roundNo: 3 },
    })
  })

  it('音频回答构造 multipart（含 pauseCount）', async () => {
    const file = new File([new Uint8Array([1, 2, 3])], 'answer.webm', { type: 'audio/webm' })
    const payload: AudioAnswerPayload = {
      file,
      roundNo: 4,
      asrText: '语音转写文本',
      durationMs: 12_000,
      pauseCount: 2,
    }

    await api.submitAudioAnswer(9, payload)

    const config = lastRequestConfig()
    expect(config).toMatchObject({
      url: '/api/interviews/9/messages/audio',
      method: 'POST',
      timeout: 60_000,
    })
    const form = config.data as FormData
    expect(form).toBeInstanceOf(FormData)
    const submitted = form.get('file') as File
    expect(submitted.name).toBe('answer.webm')
    expect(submitted.type).toBe('audio/webm')
    expect(submitted.size).toBe(3)
    expect(form.get('roundNo')).toBe('4')
    expect(form.get('asrText')).toBe('语音转写文本')
    expect(form.get('durationMs')).toBe('12000')
    expect(form.get('pauseCount')).toBe('2')
  })

  it('音频回答未传 pauseCount 时不拼空字段', async () => {
    const file = new File([new Uint8Array([1])], 'a.webm', { type: 'audio/webm' })
    await api.submitAudioAnswer(1, { file, roundNo: 0, asrText: '文本', durationMs: 1000 })

    const form = lastRequestConfig().data as FormData
    expect(form.get('pauseCount')).toBeNull()
  })

  it('受保护音频回放走 fetchBlob 并返回 Blob', async () => {
    const blob = new Blob(['audio'], { type: 'audio/webm' })
    mocks.fetchBlob.mockResolvedValueOnce(blob)

    await expect(api.fetchAudioBlob(9, 3)).resolves.toBe(blob)
    expect(mocks.fetchBlob).toHaveBeenCalledWith({
      url: '/api/interviews/9/messages/3/audio',
      method: 'GET',
      timeout: 60_000,
    })
  })
})

describe('成长 API', () => {
  it('历史分页参数清洗空值', async () => {
    await api.getHistory()
    expect(lastRequestConfig()).toEqual({ url: '/api/users/me/history', method: 'GET', params: {} })

    await api.getHistory({ page: 2, size: 20 })
    expect(lastRequestConfig()).toEqual({
      url: '/api/users/me/history',
      method: 'GET',
      params: { page: 2, size: 20 },
    })
  })

  it('成长曲线/推荐按 positionId 可选', async () => {
    await api.getGrowth()
    expect(lastRequestConfig()).toEqual({ url: '/api/users/me/growth', method: 'GET' })

    await api.getGrowth(1)
    expect(lastRequestConfig()).toEqual({
      url: '/api/users/me/growth',
      method: 'GET',
      params: { positionId: 1 },
    })

    await api.getRecommendations()
    expect(lastRequestConfig()).toEqual({
      url: '/api/users/me/recommendations',
      method: 'GET',
    })

    await api.getRecommendations(2)
    expect(lastRequestConfig()).toEqual({
      url: '/api/users/me/recommendations',
      method: 'GET',
      params: { positionId: 2 },
    })
  })
})

describe('管理端 API', () => {
  it('看板与题库 CRUD', async () => {
    await api.adminDashboard()
    expect(lastRequestConfig()).toEqual({ url: '/api/admin/dashboard', method: 'GET' })

    await api.adminListQuestions()
    expect(lastRequestConfig()).toEqual({
      url: '/api/admin/questions',
      method: 'GET',
      params: {},
    })

    await api.adminListQuestions({
      positionId: 1,
      type: 'TECHNICAL',
      difficulty: 3,
      page: 2,
      size: 20,
    })
    expect(lastRequestConfig()).toEqual({
      url: '/api/admin/questions',
      method: 'GET',
      params: { positionId: 1, type: 'TECHNICAL', difficulty: 3, page: 2, size: 20 },
    })

    await api.adminCreateQuestion(questionPayload)
    expect(lastRequestConfig()).toEqual({
      url: '/api/admin/questions',
      method: 'POST',
      data: questionPayload,
    })

    await api.adminUpdateQuestion(5, questionPayload)
    expect(lastRequestConfig()).toEqual({
      url: '/api/admin/questions/5',
      method: 'PUT',
      data: questionPayload,
    })

    await api.adminDeleteQuestion(5)
    expect(lastRequestConfig()).toEqual({ url: '/api/admin/questions/5', method: 'DELETE' })
  })

  it('岗位/技能/评分权重', async () => {
    await api.adminListPositions()
    expect(lastRequestConfig()).toEqual({
      url: '/api/admin/positions',
      method: 'GET',
      params: {},
    })

    await api.adminListPositions({ page: 1, size: 10 })
    expect(lastRequestConfig()).toMatchObject({ params: { page: 1, size: 10 } })

    await api.adminGetPosition(1)
    expect(lastRequestConfig()).toEqual({ url: '/api/admin/positions/1', method: 'GET' })

    await api.adminCreatePosition(positionPayload)
    expect(lastRequestConfig()).toEqual({
      url: '/api/admin/positions',
      method: 'POST',
      data: positionPayload,
    })

    await api.adminUpdatePosition(1, { name: '新名字' })
    expect(lastRequestConfig()).toEqual({
      url: '/api/admin/positions/1',
      method: 'PUT',
      data: { name: '新名字' },
    })

    await api.adminUpdateWeights(1, weightsPayload)
    expect(lastRequestConfig()).toEqual({
      url: '/api/admin/positions/1/weights',
      method: 'PUT',
      data: weightsPayload,
    })
  })

  it('学习资源 CRUD', async () => {
    await api.adminListResources()
    expect(lastRequestConfig()).toEqual({
      url: '/api/admin/resources',
      method: 'GET',
      params: {},
    })

    await api.adminListResources({
      positionId: 1,
      skillCode: 'java_basics',
      resourceType: 'ARTICLE',
      page: 1,
      size: 10,
    })
    expect(lastRequestConfig()).toEqual({
      url: '/api/admin/resources',
      method: 'GET',
      params: {
        positionId: 1,
        skillCode: 'java_basics',
        resourceType: 'ARTICLE',
        page: 1,
        size: 10,
      },
    })

    await api.adminCreateResource(resourcePayload)
    expect(lastRequestConfig()).toEqual({
      url: '/api/admin/resources',
      method: 'POST',
      data: resourcePayload,
    })

    await api.adminUpdateResource(3, resourcePayload)
    expect(lastRequestConfig()).toEqual({
      url: '/api/admin/resources/3',
      method: 'PUT',
      data: resourcePayload,
    })

    await api.adminDeleteResource(3)
    expect(lastRequestConfig()).toEqual({ url: '/api/admin/resources/3', method: 'DELETE' })
  })

  it('知识库新增/重建', async () => {
    await api.adminCreateKnowledge(knowledgePayload)
    expect(lastRequestConfig()).toEqual({
      url: '/api/admin/knowledge',
      method: 'POST',
      data: knowledgePayload,
    })

    await api.adminRebuildKnowledge()
    expect(lastRequestConfig()).toEqual({ url: '/api/admin/knowledge/rebuild', method: 'POST' })
  })

  it('用户列表/启停', async () => {
    await api.adminListUsers()
    expect(lastRequestConfig()).toEqual({
      url: '/api/admin/users',
      method: 'GET',
      params: {},
    })

    await api.adminListUsers({ keyword: 'e2e', page: 1, size: 10 })
    expect(lastRequestConfig()).toEqual({
      url: '/api/admin/users',
      method: 'GET',
      params: { keyword: 'e2e', page: 1, size: 10 },
    })

    await api.adminUpdateUserStatus(7, 0)
    expect(lastRequestConfig()).toEqual({
      url: '/api/admin/users/7/status',
      method: 'PUT',
      data: { status: 0 },
    })
  })
})

describe('统一 API 入口', () => {
  it('导出计划约定的 39 个 REST 函数', () => {
    const functionNames = [
      'register',
      'login',
      'logout',
      'getMe',
      'updateMe',
      'listPositions',
      'getPosition',
      'getPositionSkills',
      'createInterview',
      'startInterview',
      'submitTextAnswer',
      'submitAudioAnswer',
      'fetchAudioBlob',
      'finishInterview',
      'abortInterview',
      'getInterview',
      'getMessages',
      'getReport',
      'getHistory',
      'getGrowth',
      'getRecommendations',
      'adminListQuestions',
      'adminCreateQuestion',
      'adminUpdateQuestion',
      'adminDeleteQuestion',
      'adminCreateKnowledge',
      'adminRebuildKnowledge',
      'adminDashboard',
      'adminListPositions',
      'adminGetPosition',
      'adminCreatePosition',
      'adminUpdatePosition',
      'adminUpdateWeights',
      'adminListResources',
      'adminCreateResource',
      'adminUpdateResource',
      'adminDeleteResource',
      'adminListUsers',
      'adminUpdateUserStatus',
    ]

    expect(functionNames).toHaveLength(39)
    for (const name of functionNames) {
      expect(typeof (api as Record<string, unknown>)[name], `${name} 应导出为函数`).toBe('function')
    }
  })
})
