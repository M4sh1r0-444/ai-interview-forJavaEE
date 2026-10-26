/** 个人中心「AI 服务配置」回显（V9；api_key 只回掩码，绝不回明文）。 */
export interface AiConfigVO {
  apiUrl: string
  model: string
  /** 是否已保存 API Key */
  hasApiKey: boolean
  /** 掩码 Key（前 4 + **** + 后 4）；null = 密文无法解密（加密密钥已轮换），需重新保存 */
  apiKeyMasked?: string | null
}

/** 保存/测试 AI 服务配置的请求体（apiKey 留空 = 保留已保存值；首次保存必填）。 */
export interface AiConfigPayload {
  apiUrl: string
  model: string
  apiKey?: string
}

/** 连通性测试结果（失败时 ok=false + 原因摘要，不算协议错误）。 */
export interface AiConfigTestVO {
  ok: boolean
  message: string
  latencyMs?: number | null
}
