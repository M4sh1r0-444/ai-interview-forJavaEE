import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { init, type ECharts, type EChartsCoreOption } from 'echarts/core'

import { ensureEcharts } from '@/utils/echarts'

export interface UseEchartOptions {
  /** ECharts 主题：默认不指定，使用组件内 option 的显式配色。 */
  theme?: string | object
}

/**
 * ECharts 生命周期封装（计划 §6.5）：初始化 / 增量 setOption / ResizeObserver /
 * 卸载 dispose。图表在挂载前调用 setOption 时先暂存，挂载后自动应用。
 */
export function useEchart(options: UseEchartOptions = {}) {
  const el = ref<HTMLElement | null>(null)
  const chart = shallowRef<ECharts | null>(null)
  const ready = ref(false)

  let pendingOption: EChartsCoreOption | null = null
  let observer: ResizeObserver | null = null
  let frame: number | null = null
  let disposed = false

  function scheduleResize(): void {
    if (frame != null) return
    const run = (): void => {
      frame = null
      if (disposed) return
      chart.value?.resize()
    }
    if (typeof window.requestAnimationFrame === 'function') {
      frame = window.requestAnimationFrame(run)
    } else {
      frame = window.setTimeout(run, 16)
    }
  }

  function ensureChart(): ECharts | null {
    if (chart.value || disposed) return chart.value
    if (!el.value) return null
    try {
      ensureEcharts()
      const instance = init(el.value, options.theme)
      chart.value = instance
      ready.value = true
      if (pendingOption) {
        instance.setOption(pendingOption, { notMerge: true })
        pendingOption = null
      }
      if (typeof ResizeObserver !== 'undefined') {
        observer = new ResizeObserver(() => scheduleResize())
        observer.observe(el.value)
      }
      window.addEventListener('resize', scheduleResize)
    } catch (error) {
      // 无 canvas 的测试/降级环境：只告警，不影响页面其他内容。
      console.warn('[echart] 初始化失败，已跳过图表渲染', error)
    }
    return chart.value
  }

  function setOption(option: EChartsCoreOption): void {
    const instance = ensureChart()
    if (!instance) {
      pendingOption = option
      return
    }
    instance.setOption(option, { notMerge: true })
  }

  function resize(): void {
    chart.value?.resize()
  }

  function dispose(): void {
    disposed = true
    if (frame != null) {
      if (typeof window.cancelAnimationFrame === 'function') {
        window.cancelAnimationFrame(frame)
      } else {
        window.clearTimeout(frame)
      }
      frame = null
    }
    observer?.disconnect()
    observer = null
    window.removeEventListener('resize', scheduleResize)
    chart.value?.dispose()
    chart.value = null
    ready.value = false
  }

  onMounted(() => {
    ensureChart()
  })

  onBeforeUnmount(dispose)

  return { el, chart, ready, setOption, resize, dispose }
}
