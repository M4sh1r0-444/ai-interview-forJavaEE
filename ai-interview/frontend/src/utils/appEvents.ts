/** 应用级事件：解耦 http / router / 组件，避免 api 层直接依赖 UI。 */
export const APP_EVENTS = {
  forbidden: 'app:forbidden',
  globalError: 'app:error',
} as const

export interface ForbiddenDetail {
  code?: number
  message?: string
}

export interface GlobalErrorDetail {
  message: string
  traceId?: string
}

function dispatch<T>(name: string, detail?: T): void {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent<T>(name, { detail }))
}

export function notifyForbidden(detail?: ForbiddenDetail): void {
  dispatch(APP_EVENTS.forbidden, detail)
}

export function onForbidden(handler: (detail?: ForbiddenDetail) => void): () => void {
  const listener = (event: Event) => handler((event as CustomEvent<ForbiddenDetail>).detail)
  window.addEventListener(APP_EVENTS.forbidden, listener)
  return () => window.removeEventListener(APP_EVENTS.forbidden, listener)
}

export function notifyGlobalError(detail: GlobalErrorDetail): void {
  dispatch(APP_EVENTS.globalError, detail)
}

export function onGlobalError(handler: (detail: GlobalErrorDetail) => void): () => void {
  const listener = (event: Event) => handler((event as CustomEvent<GlobalErrorDetail>).detail)
  window.addEventListener(APP_EVENTS.globalError, listener)
  return () => window.removeEventListener(APP_EVENTS.globalError, listener)
}
