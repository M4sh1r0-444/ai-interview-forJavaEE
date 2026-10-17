import { BarChart, LineChart, RadarChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

let installed = false

/**
 * ECharts 按需注册（计划 §6.10 / §8.4）：报告雷达 + 成长折线 + 看板柱线组合
 * 所需的图表与组件，避免引入完整 echarts（首屏体积预算）。
 */
export function ensureEcharts(): void {
  if (installed) return
  use([
    RadarChart,
    LineChart,
    BarChart,
    TooltipComponent,
    LegendComponent,
    GridComponent,
    CanvasRenderer,
  ])
  installed = true
}
