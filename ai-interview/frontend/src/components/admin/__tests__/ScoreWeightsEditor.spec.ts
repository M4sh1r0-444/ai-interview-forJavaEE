import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ScoreWeightsEditor from '@/components/admin/ScoreWeightsEditor.vue'
import { cloneScoreWeights, DEFAULT_SCORE_WEIGHTS, type ScoreWeightsForm } from '@/utils/admin'

function brokenWeights(): ScoreWeightsForm {
  const weights = cloneScoreWeights(DEFAULT_SCORE_WEIGHTS)
  weights.total.content = 0.4
  return weights
}

describe('ScoreWeightsEditor', () => {
  it('渲染三组权重与合计', () => {
    const wrapper = mount(ScoreWeightsEditor, { props: { modelValue: DEFAULT_SCORE_WEIGHTS } })
    expect(wrapper.text()).toContain('内容分权重')
    expect(wrapper.text()).toContain('表达分权重')
    expect(wrapper.text()).toContain('总分权重')
    expect(wrapper.findAll('[data-testid^="score-sum-"]')).toHaveLength(3)
    expect(wrapper.get('[data-testid="score-sum-total"]').text()).toContain('1.00')
  })

  it('某组和不为 1 时显示差值并提示错误', () => {
    const wrapper = mount(ScoreWeightsEditor, { props: { modelValue: brokenWeights() } })
    expect(wrapper.get('[data-testid="score-sum-total"]').text()).toContain('0.90')
    expect(wrapper.text()).toContain('总分权重合计须为 1')
  })

  it('恢复默认 emit 后端默认权重', async () => {
    const wrapper = mount(ScoreWeightsEditor, { props: { modelValue: brokenWeights() } })
    await wrapper.get('[data-testid="score-weights-reset"]').trigger('click')
    const emitted = wrapper.emitted('update:modelValue')?.at(-1)?.[0] as ScoreWeightsForm
    expect(emitted.total.content).toBe(0.5)
    expect(emitted.content.technical).toBe(0.3)
    expect(emitted.expression.clarity).toBe(0.3)
  })

  it('编辑输入后 emit 新权重', async () => {
    const wrapper = mount(ScoreWeightsEditor, { props: { modelValue: DEFAULT_SCORE_WEIGHTS } })
    const input = wrapper.get('[data-testid="score-content-technical"] input')
    await input.setValue('0.4')
    await input.trigger('blur')
    const emitted = wrapper.emitted('update:modelValue')?.at(-1)?.[0] as ScoreWeightsForm
    expect(emitted.content.technical).toBe(0.4)
  })

  it('showReset=false 时不显示恢复默认，disabled 时输入禁用', () => {
    const wrapper = mount(ScoreWeightsEditor, {
      props: {
        modelValue: DEFAULT_SCORE_WEIGHTS,
        showReset: false,
        disabled: true,
      },
    })
    expect(wrapper.find('[data-testid="score-weights-reset"]').exists()).toBe(false)
    expect(
      wrapper.get('[data-testid="score-content-technical"] input').attributes('disabled'),
    ).toBeDefined()
  })
})
