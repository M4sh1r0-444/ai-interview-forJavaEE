import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElMessageBox } from 'element-plus'

import 'element-plus/dist/index.css'
import '@/styles/tokens.css'
import '@/styles/element-overrides.scss'
import '@/styles/global.scss'
import '@/styles/print.scss'

import App from '@/App.vue'
import router from '@/router'
import { setupGuards } from '@/router/guards'
import { isBizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { notifyGlobalError } from '@/utils/appEvents'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

// 守卫必须在 router 安装前注册，否则首次导航会绕过权限判断。
setupGuards(router)
app.use(router)

// 未捕获的组件错误：记录并给出可关闭的提示，避免白屏（计划 §4.6）。
app.config.errorHandler = (error, _instance, info) => {
  console.error('[vue-error]', info, error)
  notifyGlobalError({
    message: isBizError(error) ? error.message : '页面发生未捕获异常，请刷新后重试',
    traceId: isBizError(error) ? error.traceId : undefined,
  })
}

window.addEventListener('unhandledrejection', (event) => {
  const reason: unknown = event.reason
  if (isBizError(reason)) {
    // 页面没有就地处理的业务错误，走全局兜底；已处理过的 Promise 不会到这里。
    notifyGlobalError({ message: reason.message, traceId: reason.traceId })
    return
  }
  console.error('[unhandled-rejection]', reason)
})

// 发版后旧 chunk 失效：提示刷新，而不是停留在空白路由（计划 §4.6）。
router.onError((error) => {
  const message = error instanceof Error ? error.message : String(error)
  if (
    /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module/i.test(
      message,
    )
  ) {
    void ElMessageBox.confirm(
      '检测到前端资源已更新，需要刷新页面才能继续使用。',
      '页面资源已更新',
      {
        type: 'warning',
        confirmButtonText: '立即刷新',
        cancelButtonText: '稍后',
        closeOnClickModal: false,
      },
    )
      .then(() => window.location.reload())
      .catch(() => undefined)
  }
})

async function bootstrap(): Promise<void> {
  // 启动序列：恢复登录态（含 /me 校验）→ 首次导航完成 → 挂载，避免闪屏与无权页面闪现。
  const auth = useAuthStore(pinia)
  try {
    await auth.restore()
  } catch (error) {
    console.error('[bootstrap] restore auth failed', error)
  }
  try {
    await router.isReady()
  } catch (error) {
    console.error('[bootstrap] router ready failed', error)
  }
  app.mount('#app')
}

void bootstrap()
