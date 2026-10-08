/**
 * 基于 fetch + ReadableStream 的 SSE（Server-Sent Events）客户端。
 *
 * 为什么要用 fetch 而不是 axios 读流？
 * 浏览器端 axios 底层是 XMLHttpRequest，拿不到「边到达边读取」的增量响应，
 * 因此 SSE 必须用 fetch + ReadableStream；axios 实例仍在 api/request.ts 中统一维护
 * baseURL / 超时 / 错误信息，并在「关闭流式输出」时用于一次性获取完整回复。
 */

/** 查询参数，值为 null / undefined / '' 时会被自动忽略 */
export type SseParams = Record<string, string | number | boolean | null | undefined>

export interface SseRequestOptions {
  /** 完整请求地址（已包含 baseURL，可直接被 fetch 使用） */
  url: string
  method?: 'GET' | 'POST'
  /** 拼接在 url 后面的查询参数 */
  params?: SseParams
  /** POST 时发送的 JSON 数据 */
  body?: unknown
  headers?: Record<string, string>
  /** 中断信号（对应「停止生成」按钮） */
  signal?: AbortSignal
  /** 是否携带 Cookie 等凭证 */
  withCredentials?: boolean
  /** 连接建立（响应 200）后触发 */
  onOpen?: () => void
  /** 每收到一段增量文本触发一次 */
  onMessage?: (chunk: string) => void
  /** 流正常结束时触发 */
  onDone?: () => void
  /** 出错时触发；主动中断不会触发 */
  onError?: (error: Error) => void
}

/** SSE 中表示「流结束」的哨兵值（部分后端会发送 data:[DONE]） */
const DONE_FLAG = '[DONE]'

/** SSE 事件之间的分隔符（空行） */
const EVENT_SEPARATOR = '\n\n'

/** 兼容后端返回 JSON 的情况：按优先级尝试这些字段作为增量文本 */
const CONTENT_KEYS = [
  'content',
  'text',
  'data',
  'delta',
  'message',
  'result',
  'output',
  'answer',
  'v',
  'chunk',
] as const

interface SseFrame {
  event: string
  data: string
}

/** 当前环境是否支持流式读取（不支持时调用方应退化为一次性请求） */
export function supportsStreaming(): boolean {
  return (
    typeof fetch === 'function' &&
    typeof ReadableStream === 'function' &&
    typeof TextDecoder === 'function'
  )
}

/**
 * 发起一次 SSE 请求并持续回调增量文本。
 * 正常结束（或收到 [DONE]）时 resolve；出错时抛出（主动中断则静默返回）。
 */
export async function requestSse(options: SseRequestOptions): Promise<void> {
  const {
    url,
    method = 'GET',
    params,
    body,
    headers,
    signal,
    withCredentials = false,
    onOpen,
    onMessage,
    onDone,
    onError,
  } = options

  const requestUrl = appendParams(url, params)
  const hasBody = body !== undefined && body !== null
  let reader: ReadableStreamDefaultReader<Uint8Array> | undefined

  try {
    const response = await fetch(requestUrl, {
      method,
      headers: {
        Accept: 'text/event-stream',
        ...(hasBody ? { 'Content-Type': 'application/json' } : {}),
        ...headers,
      },
      body: hasBody ? JSON.stringify(body) : undefined,
      credentials: withCredentials ? 'include' : 'same-origin',
      signal,
    })

    if (!response.ok) {
      // 后端没启动时 Vite 代理会返回一段 HTML 错误页，这里只保留可读的简短信息
      const detail = await response.text().catch(() => '')
      const summary = detail.trim().replace(/\s+/g, ' ')
      const hint = summary && !summary.startsWith('<') ? `：${summary.slice(0, 120)}` : ''
      throw new Error(`请求失败：HTTP ${response.status} ${response.statusText}${hint}`.trim())
    }
    if (!response.body) {
      throw new Error('当前浏览器不支持流式响应，可关闭「流式输出」开关后重试')
    }

    onOpen?.()

    reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let finished = false

    const handleFrame = (raw: string): void => {
      const frame = parseFrame(raw)
      if (!frame) return
      if (frame.event === 'error') {
        throw new Error(frame.data || '服务端返回了错误事件')
      }
      if (frame.event === 'done' || frame.data.trim() === DONE_FLAG) {
        finished = true
        return
      }
      const chunk = extractChunk(frame.data)
      if (chunk) onMessage?.(chunk)
    }

    while (!finished) {
      const { done, value } = await reader.read()
      if (done || !value) break

      buffer += decoder.decode(value, { stream: true })

      // 末尾孤立的 \r 可能是被截断的 \r\n（恰好分属两次 read），先留到下一轮再处理，避免劈帧丢数据
      let tail = ''
      if (buffer.endsWith('\r')) {
        tail = '\r'
        buffer = buffer.slice(0, -1)
      }
      buffer = normalizeNewlines(buffer) + tail

      let separatorIndex = buffer.indexOf(EVENT_SEPARATOR)
      while (separatorIndex !== -1 && !finished) {
        handleFrame(buffer.slice(0, separatorIndex))
        buffer = buffer.slice(separatorIndex + EVENT_SEPARATOR.length)
        separatorIndex = buffer.indexOf(EVENT_SEPARATOR)
      }
    }

    // 收尾：兼容服务端最后一条消息没有以空行结束的情况
    buffer = normalizeNewlines(buffer + decoder.decode())
    if (!finished && buffer.trim()) handleFrame(buffer)

    onDone?.()
  } catch (error) {
    if (isAbortError(error)) return
    const normalized = error instanceof Error ? error : new Error(String(error))
    if (onError) {
      onError(normalized)
      return
    }
    throw normalized
  } finally {
    void reader?.cancel().catch(() => {})
  }
}

/**
 * 把一次性拿到的完整 SSE 文本解析成增量片段。
 * 用于「非流式」兜底：axios 取回全文后仍然按 SSE 规范解析，保证展示逻辑一致。
 */
export function parseSseText(text: string): string[] {
  const chunks: string[] = []
  for (const raw of normalizeNewlines(text).split(EVENT_SEPARATOR)) {
    const frame = parseFrame(raw)
    if (!frame) continue
    if (frame.event === 'done' || frame.data.trim() === DONE_FLAG) break
    const chunk = extractChunk(frame.data)
    if (chunk) chunks.push(chunk)
  }
  return chunks
}

/** 从一帧 data 中取出真正的增量文本：纯文本直接返回，JSON 则按常见字段名递归提取 */
export function extractChunk(payload: string): string {
  if (!payload) return ''

  const trimmed = payload.trim()
  if (trimmed === DONE_FLAG) return ''
  // 纯空白（例如 markdown 段落之间的换行）也是有效增量，必须原样保留
  if (!trimmed) return payload

  const firstChar = trimmed.charAt(0)
  if (firstChar !== '{' && firstChar !== '[' && firstChar !== '"') return payload

  try {
    return readText(JSON.parse(trimmed))
  } catch {
    return payload
  }
}

/** 解析一帧 SSE 数据，返回事件名与 data（多行 data 用 \n 连接） */
function parseFrame(raw: string): SseFrame | null {
  const frame: SseFrame = { event: '', data: '' }
  const dataLines: string[] = []
  let matched = false

  for (const line of normalizeNewlines(raw).split('\n')) {
    // 空行是分隔符，以 : 开头的是注释（常用于心跳保活）
    if (!line || line.startsWith(':')) continue

    const colonIndex = line.indexOf(':')
    const field = colonIndex === -1 ? line : line.slice(0, colonIndex)
    let value = colonIndex === -1 ? '' : line.slice(colonIndex + 1)
    if (value.startsWith(' ')) value = value.slice(1)

    if (field === 'data') {
      dataLines.push(value)
      matched = true
    } else if (field === 'event') {
      frame.event = value.trim()
      matched = true
    } else if (field !== 'id' && field !== 'retry' && dataLines.length) {
      // 兼容：有些实现（如 Spring 的 SseEmitter.send(String)）不把字符串内部的换行拆成多条
      // data 行，续行既不是已知字段也不带前缀，这里按数据续行处理，避免多行内容被丢掉
      dataLines.push(line)
    }
  }

  if (!matched) return null
  frame.data = dataLines.join('\n')
  return frame
}

function readText(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  if (Array.isArray(value)) return value.map((item) => readText(item)).join('')

  if (typeof value === 'object') {
    const record = value as Record<string, unknown>
    for (const key of CONTENT_KEYS) {
      const nested = readText(record[key])
      if (nested) return nested
    }
  }
  return ''
}

function appendParams(url: string, params?: SseParams): string {
  if (!params) return url

  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value === null || value === undefined || value === '') return
    search.append(key, String(value))
  })

  const query = search.toString()
  if (!query) return url
  return url.includes('?') ? `${url}&${query}` : `${url}?${query}`
}

function normalizeNewlines(text: string): string {
  return text.replace(/\r\n?/g, '\n')
}

function isAbortError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false
  return (error as { name?: string }).name === 'AbortError'
}
