import { fetchBlob, request } from '@/api/http'
import type {
  AudioAnswerPayload,
  CreateInterviewPayload,
  FinishAccepted,
  InterviewDetailVO,
  MessageVO,
  SubmitAccepted,
  TextAnswerPayload,
} from '@/types/interview'
import type { ReportVO } from '@/types/report'

/** 创建会话（CREATED）。 */
export function createInterview(payload: CreateInterviewPayload): Promise<InterviewDetailVO> {
  return request<InterviewDetailVO>({ url: '/api/interviews', method: 'POST', data: payload })
}

/** 开始面试，返回首题；同时 WS 推 QUESTION。重复 start → 10005。 */
export function startInterview(id: number): Promise<MessageVO> {
  return request<MessageVO>({ url: `/api/interviews/${id}/start`, method: 'POST' })
}

/** 异步受理：只表示 USER 消息已落库，AI 产出走 WS + GET messages。 */
export function submitTextAnswer(id: number, payload: TextAnswerPayload): Promise<SubmitAccepted> {
  return request<SubmitAccepted>({
    url: `/api/interviews/${id}/messages/text`,
    method: 'POST',
    data: payload,
    timeout: 30_000,
  })
}

export function submitAudioAnswer(
  id: number,
  payload: AudioAnswerPayload,
): Promise<SubmitAccepted> {
  const form = new FormData()
  form.append('file', payload.file, 'answer.webm')
  form.append('roundNo', String(payload.roundNo))
  form.append('asrText', payload.asrText)
  form.append('durationMs', String(payload.durationMs))
  if (payload.pauseCount != null) form.append('pauseCount', String(payload.pauseCount))

  return request<SubmitAccepted>({
    url: `/api/interviews/${id}/messages/audio`,
    method: 'POST',
    data: form,
    timeout: 60_000,
  })
}

/** 受保护音频回放（binary）；blob 带 Authorization，不暴露静态 URL。 */
export function fetchAudioBlob(id: number, messageId: number): Promise<Blob> {
  return fetchBlob({
    url: `/api/interviews/${id}/messages/${messageId}/audio`,
    method: 'GET',
    timeout: 60_000,
  })
}

/** finish 异步受理；再次调用是官方允许的报告重试入口（幂等）。 */
export function finishInterview(id: number): Promise<FinishAccepted> {
  return request<FinishAccepted>({
    url: `/api/interviews/${id}/finish`,
    method: 'POST',
    timeout: 30_000,
  })
}

/** 中止，返回 ABORTED 详情。 */
export function abortInterview(id: number): Promise<InterviewDetailVO> {
  return request<InterviewDetailVO>({
    url: `/api/interviews/${id}/abort`,
    method: 'POST',
    timeout: 30_000,
  })
}

export function getInterview(id: number): Promise<InterviewDetailVO> {
  return request<InterviewDetailVO>({ url: `/api/interviews/${id}`, method: 'GET' })
}

/** 学生看不到未问到的主问题（后端过滤）。 */
export function getMessages(id: number, roundNo?: number): Promise<MessageVO[]> {
  return request<MessageVO[]>({
    url: `/api/interviews/${id}/messages`,
    method: 'GET',
    params: roundNo == null ? undefined : { roundNo },
  })
}

/** 报告未生成时返回 10004（正常态，不应弹全局错误）。 */
export function getReport(id: number): Promise<ReportVO> {
  return request<ReportVO>({ url: `/api/interviews/${id}/report`, method: 'GET' })
}
