import { RadarChart } from 'echarts/charts'
import { LegendComponent, TooltipComponent } from 'echarts/components'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

let installed = false

/**
 * ECharts 按需注册（计划 §6.10 / §8.4）：只打包报告雷达所需的图表与组件，
 * 避免引入完整 echarts（首屏体积预算）。F4/F5 新增折线/柱线时在此追加。
 */
export function ensureEcharts(): void {
  if (installed) return
  use([RadarChart, TooltipComponent, LegendComponent, CanvasRenderer])
  installed = true
}
