import { describe, expect, it } from 'vitest'

import type { DashboardTrendPoint, QuestionAdminVO, SkillItem } from '@/types/admin'
import {
  arrayToLines,
  buildDashboardTrendOption,
  cleanSkills,
  cloneScoreWeights,
  DEFAULT_SCORE_WEIGHTS,
  emptyQuestionForm,
  formatWeight,
  formatWeightSum,
  joinCommaList,
  linesToArray,
  normalizeScoreWeights,
  parseDifficulty,
  parsePositiveInt,
  parseQuestionType,
  parseResourceType,
  questionFormToPayload,
  questionToForm,
  scoreWeightsToPayload,
  splitCommaList,
  sumWeights,
  validateQuestionForm,
  validateScoreWeightsForm,
  validateSkillWeights,
  weightDifferenceText,
  weightSumValid,
} from '@/utils/admin'

function skill(overrides: Partial<SkillItem> = {}): SkillItem {
  return {
    skillCode: 'spring',
    skillName: 'Spring生态',
    description: 'IOC/AOP',
    weight: 1,
    requiredLevel: 70,
    ...overrides,
  }
}

function question(overrides: Partial<QuestionAdminVO> = {}): QuestionAdminVO {
  return {
    id: 9,
    positionId: 1,
    type: 'TECHNICAL',
    content: '说说 Spring 事务传播行为？',
    background: null,
    difficulty: 3,
    skillTags: ['spring'],
    referenceAnswer: '参考',
    keyPoints: ['传播行为'],
    deductionPoints: ['只背概念'],
    followupTemplates: ['嵌套事务怎么处理？'],
    status: 'ENABLED',
    version: 2,
    ...overrides,
  }
}

describe('权重工具', () => {
  it('默认三组权重均满足组内和=1', () => {
    for (const group of ['content', 'expression', 'total'] as const) {
      expect(weightSumValid(sumWeights(Object.values(DEFAULT_SCORE_WEIGHTS[group])))).toBe(true)
    }
    expect(sumWeights([0.3, 0.25, 0.2, 0.15, 0.1])).toBe(1)
  })

  it('容差为 ±0.01，展示保留两位小数', () => {
    expect(weightSumValid(0.995)).toBe(true)
    expect(weightSumValid(1.01)).toBe(true)
    expect(weightSumValid(1.0101)).toBe(false)
    expect(weightDifferenceText(0.9)).toBe('还差 0.10')
    expect(weightDifferenceText(1.05)).toBe('超出 0.05')
    expect(weightDifferenceText(1)).toBe('合计正确')
    expect(formatWeight(0.3)).toBe('0.30')
    expect(formatWeight(undefined)).toBe('—')
    expect(formatWeightSum(Number.NaN)).toBe('—')
  })

  it('normalizeScoreWeights 用默认值补齐缺失组/字段', () => {
    const normalized = normalizeScoreWeights({
      content: { technical: 0.5, depth: 0.5, logic: 0.1, evidence: 0.1, match: 0.1 },
      total: { content: 0.7 },
    })
    expect(normalized.content.technical).toBe(0.5)
    expect(normalized.content.logic).toBe(0.1)
    expect(normalized.expression.clarity).toBe(DEFAULT_SCORE_WEIGHTS.expression.clarity)
    expect(normalized.total.content).toBe(0.7)
    expect(normalized.total.match).toBe(DEFAULT_SCORE_WEIGHTS.total.match)
  })

  it('scoreWeightsToPayload 只提交三组必需的 5/5/4 个字段', () => {
    const payload = scoreWeightsToPayload(DEFAULT_SCORE_WEIGHTS)
    expect(Object.keys(payload.content ?? {})).toEqual([
      'technical',
      'depth',
      'logic',
      'evidence',
      'match',
    ])
    expect(Object.keys(payload.expression ?? {})).toHaveLength(5)
    expect(Object.keys(payload.total ?? {})).toEqual([
      'content',
      'expression',
      'adaptability',
      'match',
    ])
  })

  it('评分权重校验：默认通过，改坏某组和/取值/缺字段则失败', () => {
    expect(validateScoreWeightsForm(cloneScoreWeights(DEFAULT_SCORE_WEIGHTS)).valid).toBe(true)

    const badSum = cloneScoreWeights(DEFAULT_SCORE_WEIGHTS)
    badSum.total.content = 0.4
    const sumResult = validateScoreWeightsForm(badSum)
    expect(sumResult.valid).toBe(false)
    expect(sumResult.groups.total.valid).toBe(false)
    expect(sumResult.messages.join()).toContain('总分权重合计须为 1')

    const badRange = cloneScoreWeights(DEFAULT_SCORE_WEIGHTS)
    badRange.content.technical = 1.2
    expect(validateScoreWeightsForm(badRange).groups.content.messages.join()).toContain('0~1')

    const missing = cloneScoreWeights(DEFAULT_SCORE_WEIGHTS)
    delete (missing.expression as unknown as Record<string, unknown>).clarity
    expect(validateScoreWeightsForm(missing).valid).toBe(false)
  })
})

describe('技能权重校验', () => {
  it('合法技能通过', () => {
    const result = validateSkillWeights([
      skill({ skillCode: 'a', weight: 0.4 }),
      skill({ skillCode: 'b', weight: 0.6 }),
    ])
    expect(result.valid).toBe(true)
    expect(result.sum).toBe(1)
  })

  it('空列表、空编码/名称、非法权重、重复编码、和不为 1 均报错', () => {
    expect(validateSkillWeights([]).messages).toContain('能力模型至少包含 1 个技能')

    const invalid = validateSkillWeights([
      skill({ skillCode: ' ', skillName: '', weight: 0, requiredLevel: 101 }),
      skill({ skillCode: 'spring', weight: 0.3 }),
      skill({ skillCode: 'SPRING', weight: 0.3 }),
    ])
    expect(invalid.valid).toBe(false)
    expect(invalid.duplicateCodes).toEqual(['SPRING'])
    expect(invalid.messages.join()).toContain('skillCode 必填')
    expect(invalid.messages.join()).toContain('skillName 必填')
    expect(invalid.messages.join()).toContain('权重须在 (0,1]')
    expect(invalid.messages.join()).toContain('要求等级须在 0~100')
    expect(invalid.messages.join()).toContain('技能编码重复')
    expect(invalid.messages.join()).toContain('技能权重和须为 1')
  })

  it('cleanSkills 去空格并归一化空描述/要求等级', () => {
    const cleaned = cleanSkills([
      skill({
        skillCode: '  spring ',
        skillName: ' Spring ',
        description: '  ',
        requiredLevel: null,
      }),
    ])
    expect(cleaned[0]).toMatchObject({
      skillCode: 'spring',
      skillName: 'Spring',
      description: undefined,
      requiredLevel: null,
    })
  })
})

describe('逗号分隔与每行一条编辑器', () => {
  it('splitCommaList 支持中英文逗号、去重与去空', () => {
    expect(splitCommaList('缓存穿透, 缓存击穿，互斥锁, 缓存穿透 , ')).toEqual([
      '缓存穿透',
      '缓存击穿',
      '互斥锁',
    ])
    expect(splitCommaList('')).toEqual([])
    expect(joinCommaList([' a ', 'b', 'A'])).toBe('a,b')
  })

  it('linesToArray 忽略空行并 trim；arrayToLines 反向换行', () => {
    expect(linesToArray(' 第一行 \n\n第二行\r\n  ')).toEqual(['第一行', '第二行'])
    expect(linesToArray('')).toEqual([])
    expect(arrayToLines(['a', 'b'])).toBe('a\nb')
    expect(arrayToLines(undefined)).toBe('')
  })
})

describe('题库表单', () => {
  it('emptyQuestionForm 使用默认值', () => {
    const form = emptyQuestionForm(1)
    expect(form).toMatchObject({
      positionId: 1,
      type: 'TECHNICAL',
      difficulty: 3,
      skillTags: [],
      keyPoints: [],
    })
  })

  it('questionToForm 回填数组与 version，且不共享原数组引用', () => {
    const source = question()
    const form = questionToForm(source)
    expect(form.version).toBe(2)
    expect(form.skillTags).toEqual(['spring'])
    form.keyPoints.push('新增')
    expect(source.keyPoints).toHaveLength(1)
  })

  it('questionFormToPayload 裁剪文本、省略空可选字段并携带 version', () => {
    const form = emptyQuestionForm(1)
    form.content = '  有效题干内容  '
    form.skillTags = [' spring ', '']
    form.background = '   '
    form.referenceAnswer = '   '
    form.keyPoints = [' 要点一 ', ' ']
    form.version = 3
    const payload = questionFormToPayload(form)
    expect(payload.content).toBe('有效题干内容')
    expect(payload.skillTags).toEqual(['spring'])
    expect(payload.background).toBeUndefined()
    expect(payload.referenceAnswer).toBeUndefined()
    expect(payload.keyPoints).toEqual(['要点一'])
    expect(payload.version).toBe(3)
    expect(payload.deductionPoints).toBeUndefined()
  })

  it('新增表单不带 version，未选岗位时拒绝构建 payload', () => {
    const form = emptyQuestionForm(1)
    form.content = '题目正文'
    form.skillTags = ['spring']
    expect(questionFormToPayload(form).version).toBeUndefined()
    expect(() => questionFormToPayload(emptyQuestionForm())).toThrow('岗位不能为空')
  })

  it('validateQuestionForm 覆盖必填/长度/数组上限', () => {
    const form = emptyQuestionForm()
    const messages = validateQuestionForm(form)
    expect(messages.join()).toContain('请选择岗位')
    expect(messages.join()).toContain('题目内容需为 4~1000 字')
    expect(messages.join()).toContain('至少选择 1 个技能标签')

    const valid = emptyQuestionForm(1)
    valid.content = '这是一个有效题目'
    valid.skillTags = ['spring']
    expect(validateQuestionForm(valid)).toEqual([])

    valid.keyPoints = Array.from({ length: 11 }, (_, index) => `p${index}`)
    valid.skillTags = Array.from({ length: 11 }, (_, index) => `s${index}`)
    expect(validateQuestionForm(valid).join()).toContain('技能标签最多 10 个')
    expect(validateQuestionForm(valid).join()).toContain('得分点最多 10 条')
  })
})

describe('查询参数解析', () => {
  it('只接受正整数/合法枚举', () => {
    expect(parsePositiveInt('3')).toBe(3)
    expect(parsePositiveInt('0')).toBeNull()
    expect(parsePositiveInt('-2')).toBeNull()
    expect(parsePositiveInt(['4'])).toBe(4)
    expect(parseQuestionType('TECHNICAL')).toBe('TECHNICAL')
    expect(parseQuestionType('UNKNOWN')).toBe('')
    expect(parseDifficulty('5')).toBe(5)
    expect(parseDifficulty('6')).toBeNull()
    expect(parseResourceType('PROJECT')).toBe('PROJECT')
    expect(parseResourceType('video')).toBe('')
  })
})

describe('看板柱线组合图 option', () => {
  const points: DashboardTrendPoint[] = [
    { date: '2026-10-12', sessionCount: 0, avgScore: null },
    { date: '2026-10-13', sessionCount: 3, avgScore: 72.5 },
    { date: '2026-10-14', sessionCount: 4, avgScore: null },
  ]

  it('柱=场次、线=均分，null 均分保持断点且使用右轴', () => {
    const option = buildDashboardTrendOption(points) as unknown as {
      xAxis: { data: string[] }
      yAxis: unknown[]
      series: Array<{
        type: string
        data: unknown[]
        yAxisIndex?: number
        connectNulls?: boolean
      }>
    }
    expect(option.xAxis.data).toEqual(['2026-10-12', '2026-10-13', '2026-10-14'])
    expect(option.yAxis).toHaveLength(2)
    expect(option.series).toHaveLength(2)
    expect(option.series[0]).toMatchObject({
      type: 'bar',
      data: [0, 3, 4],
      yAxisIndex: 0,
    })
    expect(option.series[1]).toMatchObject({
      type: 'line',
      data: [null, 72.5, null],
      yAxisIndex: 1,
      connectNulls: false,
    })
  })

  it('空数据也返回合法 option（由组件决定显示空态）', () => {
    const option = buildDashboardTrendOption([]) as unknown as {
      xAxis: { data: string[] }
      series: Array<{ data: unknown[] }>
    }
    expect(option.xAxis.data).toEqual([])
    expect(option.series[0].data).toEqual([])
  })
})
