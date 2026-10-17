import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { describe, expect, it } from 'vitest'

import JsonArrayEditor from '@/components/admin/JsonArrayEditor.vue'

describe('JsonArrayEditor', () => {
  it('把数组渲染为每行一条，并显示条数/上限', () => {
    const wrapper = mount(JsonArrayEditor, {
      props: { modelValue: ['第一点', '第二点'], max: 10, label: '得分点' },
    })
    expect(wrapper.get('textarea').element.value).toBe('第一点\n第二点')
    expect(wrapper.text()).toContain('2 / 10 条')
  })

  it('输入时忽略空行并 trim，同步 string[]', async () => {
    const wrapper = mount(JsonArrayEditor, { props: { modelValue: [] } })
    await wrapper.get('textarea').setValue('  第一点  \n\n第二点\r\n ')
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted?.at(-1)?.[0]).toEqual(['第一点', '第二点'])
  })

  it('超过上限时展示错误，不静默截断', async () => {
    const wrapper = mount(JsonArrayEditor, { props: { modelValue: [], max: 2 } })
    await wrapper.get('textarea').setValue('a\nb\nc')
    expect(wrapper.text()).toContain('最多 2 条')
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toEqual(['a', 'b', 'c'])
  })

  it('父级更新 modelValue 时同步 textarea', async () => {
    const wrapper = mount(JsonArrayEditor, { props: { modelValue: ['a'] } })
    await wrapper.setProps({ modelValue: ['a', 'b'] })
    await nextTick()
    expect(wrapper.get('textarea').element.value).toBe('a\nb')
  })

  it('支持自定义 error/hint 与 testid', () => {
    const wrapper = mount(JsonArrayEditor, {
      props: { modelValue: [], error: '至少一条', testid: 'points' },
    })
    expect(wrapper.find('[data-testid="points"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('至少一条')
  })
})
