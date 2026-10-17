import { LineChart, RadarChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

let installed = false

/**
 * ECharts 按需注册（计划 §6.10 / §8.4）：报告雷达 + 成长折线所需的图表与组件，
 * 避免引入完整 echarts（首屏体积预算）。F5 新增柱线组合时在此追加 BarChart。
 */
export function ensureEcharts(): void {
  if (installed) return
  use([RadarChart, LineChart, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])
  installed = true
}
