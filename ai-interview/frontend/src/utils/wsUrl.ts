/**
 * 浏览器 WebSocket 不能自定义 Header，token 走 query（后端既定协议，计划 §2.2.6）。
 * dev 环境由 Vite 代理 /ws；生产由 Nginx 同源转发。
 */
export function buildWsUrl(sessionId: number, token: string): string {
  const configured = import.meta.env.VITE_WS_BASE_URL as string | undefined
  const base =
    configured && configured.length > 0
      ? configured.replace(/\/$/, '')
      : `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}`
  return `${base}/ws/interviews/${sessionId}?token=${encodeURIComponent(token)}`
}
