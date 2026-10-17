import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PaginationBar from '@/components/common/PaginationBar.vue'

describe('PaginationBar', () => {
  it('展示总数并转发翻页事件', () => {
    const wrapper = mount(PaginationBar, {
      props: { page: 1, size: 10, total: 25 },
    })

    expect(wrapper.find('.el-pagination').exists()).toBe(true)
    expect(wrapper.get('.el-pagination__total').text()).toContain('25')

    const pagination = wrapper.findComponent({ name: 'ElPagination' })
    pagination.vm.$emit('current-change', 2)
    expect(wrapper.emitted('update:page')).toEqual([[2]])
  })

  it('切换每页条数时向上抛出 size', () => {
    const wrapper = mount(PaginationBar, {
      props: { page: 2, size: 10, total: 25 },
    })

    const pagination = wrapper.findComponent({ name: 'ElPagination' })
    pagination.vm.$emit('size-change', 20)
    expect(wrapper.emitted('update:size')).toEqual([[20]])
  })

  it('禁用时传递 disabled 给分页组件', () => {
    const wrapper = mount(PaginationBar, {
      props: { page: 1, size: 10, total: 0, disabled: true },
    })
    const pagination = wrapper.findComponent({ name: 'ElPagination' })
    expect(pagination.props('disabled')).toBe(true)
  })
})
