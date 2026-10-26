import { request } from '@/api/http'
import type { AiConfigPayload, AiConfigTestVO, AiConfigVO } from '@/types/aiConfig'

/** 查询我的 AI 服务配置（未配置返回 null）。 */
export function getAiConfig(): Promise<AiConfigVO | null> {
  return request<AiConfigVO | null>({
    url: '/api/users/me/ai-config',
    method: 'GET',
  })
}

/** 保存/更新我的 AI 服务配置（apiKey 留空 = 保留已保存值；保存后面试即时生效）。 */
export function saveAiConfig(payload: AiConfigPayload): Promise<AiConfigVO> {
  return request<AiConfigVO>({
    url: '/api/users/me/ai-config',
    method: 'PUT',
    data: payload,
  })
}

/** 清除我的 AI 服务配置，回到平台默认（幂等）。 */
export function deleteAiConfig(): Promise<{ deleted: boolean }> {
  return request<{ deleted: boolean }>({
    url: '/api/users/me/ai-config',
    method: 'DELETE',
  })
}

/** 连通性测试（apiKey 留空则用已保存值；发一条极小请求，结果不落库）。 */
export function testAiConfig(payload: AiConfigPayload): Promise<AiConfigTestVO> {
  return request<AiConfigTestVO>({
    url: '/api/users/me/ai-config/test',
    method: 'POST',
    data: payload,
  })
}
