import { get, resolveApiUrl, resolveErrorMessage } from './request'

import type { ChatStreamPayload } from '@/types/chat'
import { parseSseText, requestSse, supportsStreaming, type SseParams } from '@/utils/sse'

/**
 * AI 旅行规划助手（SSE 流式）
 *
 * 对应后端：GET /ai/travel_app/chat/sse?message=xxx&chatId=xxx
 * 返回类型为 Flux<String>，属于标准 SSE 流，逐段实时展示。
 * chatId 用于后端区分不同会话，多轮对话的上下文由后端按 chatId 维护。
 */
export async function doChatWithTravelAppSse(payload: ChatStreamPayload): Promise<void> {
  await chat('/ai/travel_app/chat/sse', { message: payload.message, chatId: payload.chatId }, payload)
}

/**
 * AI 超级智能体 Manus（SSE 流式）
 *
 * 对应后端：GET /ai/manus/chat?message=xxx
 * 后端使用 SseEmitter 推送，接口不接收 chatId，每次请求相互独立。
 */
export async function doChatWithManus(payload: ChatStreamPayload): Promise<void> {
  await chat('/ai/manus/chat', { message: payload.message }, payload)
}

/**
 * 统一的流式调用入口。
 * - 默认使用 fetch + ReadableStream 边收边渲染（真正的 SSE 流）；
 * - 关闭「流式输出」开关、或浏览器不支持流式读取时，退化为 axios 一次性取回全文，
 *   再用同一套 SSE 解析器拆分片段，保证两种模式的展示逻辑完全一致。
 */
async function chat(path: string, params: SseParams, payload: ChatStreamPayload): Promise<void> {
  if (payload.stream !== false && supportsStreaming()) {
    try {
      await requestSse({
        url: resolveApiUrl(path),
        method: 'GET',
        params,
        signal: payload.signal,
        onMessage: payload.onMessage,
      })
    } catch (error) {
      // 统一转换成可展示的中文文案（fetch 抛出的英文原始异常对用户没有意义）
      throw new Error(resolveErrorMessage(error))
    }
    return
  }

  try {
    const text = await get<string>(path, {
      params,
      signal: payload.signal,
      // 大模型响应时间不可控，这里不做超时限制
      timeout: 0,
      responseType: 'text',
      headers: { Accept: 'text/event-stream' },
      // 保持原始 SSE 文本，交给 parseSseText 解析
      transformResponse: [(data: string) => data],
    })

    parseSseText(text).forEach((chunk) => payload.onMessage?.(chunk))
  } catch (error) {
    throw new Error(resolveErrorMessage(error))
  }
}
