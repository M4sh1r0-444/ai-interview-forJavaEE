import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import SkillWeightsEditor from '@/components/admin/SkillWeightsEditor.vue'
import type { SkillItem } from '@/types/admin'

function skills(): SkillItem[] {
  return [
    {
      skillCode: 'spring',
      skillName: 'Spring生态',
      description: 'IOC/AOP',
      weight: 0.4,
      requiredLevel: 70,
    },
    {
      skillCode: 'mysql',
      skillName: 'MySQL数据库',
      description: '索引与事务',
      weight: 0.6,
      requiredLevel: 65,
    },
  ]
}

describe('SkillWeightsEditor', () => {
  it('渲染动态行并实时显示权重合计', () => {
    const wrapper = mount(SkillWeightsEditor, { props: { modelValue: skills() } })
    expect(wrapper.findAll('[data-testid^="skill-row-"][data-testid$="-code"]')).toHaveLength(2)
    expect(wrapper.get('.skill-weights-editor__sum').text()).toContain('1.00')
    expect(wrapper.get('.skill-weights-editor__sum').text()).toContain('合计正确')
  })

  it('添加技能追加一行并 emit 新数组', async () => {
    const wrapper = mount(SkillWeightsEditor, { props: { modelValue: skills() } })
    const add = wrapper.findAll('button').find((button) => button.text().includes('添加技能'))
    expect(add).toBeTruthy()
    await add?.trigger('click')
    const emitted = wrapper.emitted('update:modelValue')?.at(-1)?.[0] as SkillItem[]
    expect(emitted).toHaveLength(3)
    expect(emitted[2].skillCode).toBe('')
    expect(wrapper.findAll('[data-testid^="skill-row-"][data-testid$="-code"]')).toHaveLength(3)
  })

  it('至少保留 1 行，最后一行删除按钮禁用', async () => {
    const wrapper = mount(SkillWeightsEditor, { props: { modelValue: [skills()[0]] } })
    const remove = wrapper.get('[data-testid="skill-row-0-remove"]')
    expect(remove.attributes('disabled')).toBeDefined()
  })

  it('删除行后 emit 剩余行', async () => {
    const wrapper = mount(SkillWeightsEditor, { props: { modelValue: skills() } })
    await wrapper.get('[data-testid="skill-row-1-remove"]').trigger('click')
    const emitted = wrapper.emitted('update:modelValue')?.at(-1)?.[0] as SkillItem[]
    expect(emitted).toHaveLength(1)
    expect(emitted[0].skillCode).toBe('spring')
  })

  it('权重和不为 1 时显示差值提示', () => {
    const wrapper = mount(SkillWeightsEditor, {
      props: { modelValue: [skills()[0], { ...skills()[1], weight: 0.3 }] },
    })
    expect(wrapper.get('.skill-weights-editor__sum').text()).toContain('0.70')
    expect(wrapper.text()).toContain('还差 0.30')
  })

  it('重复 skillCode 时提示重复', () => {
    const wrapper = mount(SkillWeightsEditor, {
      props: {
        modelValue: [
          { ...skills()[0], skillCode: 'spring' },
          { ...skills()[1], skillCode: 'SPRING' },
        ],
      },
    })
    expect(wrapper.text()).toContain('技能编码重复')
    expect(wrapper.text()).toContain('spring')
  })

  it('编辑权重输入后更新合计', async () => {
    const wrapper = mount(SkillWeightsEditor, { props: { modelValue: skills() } })
    const weightInput = wrapper.get('[data-testid="skill-row-0-weight"] input')
    await weightInput.setValue('0.2')
    await weightInput.trigger('blur')
    expect(wrapper.get('.skill-weights-editor__sum').text()).toContain('0.80')
    expect(wrapper.text()).toContain('还差 0.20')
  })
})
