import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import EvidenceAccordion from '@/components/report/EvidenceAccordion.vue'
import type { QuestionEvidenceVO } from '@/types/report'

function evidence(overrides: Partial<QuestionEvidenceVO> = {}): QuestionEvidenceVO {
  return {
    roundNo: 0,
    questionId: 14,
    questionContent: '什么情况下索引会失效？如何分析一条慢 SQL？',
    answerExcerpt: '先从执行计划看 type 与 key，再看 rows 与 Extra。',
    difficulty: 3,
    targetSkills: ['mysql'],
    scores: { technical: 53, depth: 41, logic: 50, match: 55, communication: 88 },
    contentScore: 49,
    coveredPoints: ['能用 EXPLAIN 关键列解读计划'],
    missingPoints: ['能列举 4 种以上失效场景'],
    evidence: ['EXPLAIN 里 type=ALL 就代表全表扫描'],
    reason: '命中得分点 1/2，失分信号 0 处',
    ...overrides,
  }
}

describe('EvidenceAccordion', () => {
  it('按轮次升序渲染并默认展开首题', () => {
    const wrapper = mount(EvidenceAccordion, {
      props: {
        items: [evidence({ roundNo: 1, questionContent: '第二题' }), evidence({ roundNo: 0 })],
        mode: 'TEXT',
        skillNames: { mysql: 'MySQL' },
      },
    })

    const items = wrapper.findAll('.el-collapse-item')
    expect(items).toHaveLength(2)
    expect(items[0].classes()).toContain('is-active')
    expect(wrapper.text()).toContain('第 1 题')
    expect(wrapper.text()).toContain('第 2 题')
    expect(wrapper.text()).toContain('MySQL')
    expect(wrapper.text()).toContain('内容分 49.0')
  })

  it('点击标题可展开第二题并保持多题同时展开', async () => {
    const wrapper = mount(EvidenceAccordion, {
      props: { items: [evidence({ roundNo: 0 }), evidence({ roundNo: 1 })], mode: 'TEXT' },
    })
    const headers = wrapper.findAll('.el-collapse-item__header')
    await headers[1].trigger('click')

    const items = wrapper.findAll('.el-collapse-item')
    expect(items[1].classes()).toContain('is-active')
    expect(headers[0].attributes('aria-expanded')).toBe('true')
    expect(headers[1].attributes('aria-expanded')).toBe('true')

    await headers[0].trigger('click')
    expect(headers[0].attributes('aria-expanded')).toBe('false')
  })

  it('空数组降级为明确文案', () => {
    const wrapper = mount(EvidenceAccordion, {
      props: {
        items: [
          evidence({
            coveredPoints: [],
            missingPoints: [],
            evidence: [],
            reason: '',
            contentScore: 0,
          }),
        ],
        mode: 'AUDIO',
      },
    })

    expect(wrapper.text()).toContain('未命中得分点')
    expect(wrapper.text()).toContain('无失分点')
    expect(wrapper.text()).toContain('未提取到证据句')
    expect(wrapper.text()).toContain('未提供评分理由')
    expect(wrapper.text()).toContain('本题无录音回放')
  })

  it('可回放题目展示按钮并向上抛出轮次', async () => {
    const wrapper = mount(EvidenceAccordion, {
      props: {
        items: [evidence({ roundNo: 0 })],
        mode: 'AUDIO',
        replayableRounds: [0],
        playingRound: 0,
      },
    })

    const button = wrapper.get('[data-testid="evidence-replay-0"]')
    expect(button.text()).toContain('回放中')
    await button.trigger('click')
    expect(wrapper.emitted('replay')).toEqual([[0]])
  })

  it('无逐题证据时展示空态', () => {
    const wrapper = mount(EvidenceAccordion, { props: { items: [] } })
    expect(wrapper.text()).toContain('本场没有可展示的逐题证据')
  })
})
