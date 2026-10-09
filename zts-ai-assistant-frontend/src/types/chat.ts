/** 聊天角色：user = 用户（气泡在右侧），assistant = AI（气泡在左侧） */
export type ChatRole = 'user' | 'assistant'

/** 单条消息的状态：pending = 正在流式接收，done = 已完成，error = 失败 */
export type ChatStatus = 'pending' | 'done' | 'error'

/**
 * 气泡类型：
 * - text   普通文本回复
 * - step   智能体单步执行结果（每步一个气泡）
 * - final  智能体的最终回复
 * - notice 系统提示（执行完成 / 执行出错等）
 */
export type ChatBlockVariant = 'text' | 'step' | 'final' | 'notice'

/** 一条聊天记录（一个气泡） */
export interface ChatMessage {
  /** 消息唯一 id（用于 v-for 的 key） */
  id: string
  role: ChatRole
  /** 消息正文；流式接收过程中会不断追加内容 */
  content: string
  status: ChatStatus
  /** 失败原因，仅 status 为 error 时存在 */
  error?: string
  /** 创建时间戳（毫秒） */
  createdAt: number
  /** 气泡类型，缺省按普通文本渲染 */
  variant?: ChatBlockVariant
  /** 气泡内部的小标题（例如 Step 1 / 最终回复），没有则不显示 */
  label?: string
  /** 步骤序号，仅 variant 为 step 时存在 */
  step?: number
}

/**
 * 聊天室组件向页面抛出的「发送一条消息」载荷。
 * 页面只需把它透传给具体的接口实现（见 src/api/ai.ts）。
 */
export interface ChatStreamPayload {
  /** 用户输入的问题 */
  message: string
  /** 会话 id，用于让后端区分不同会话；接口不需要时为空 */
  chatId?: string
  /** true = SSE 流式逐字返回；false = axios 一次性返回全文 */
  stream: boolean
  /** 中断信号，用于「停止生成」 */
  signal: AbortSignal
  /** 每收到一段增量文本回调一次 */
  onMessage?: (chunk: string) => void
}

/** 具体的接口调用实现，由页面注入给聊天室组件 */
export type ChatStreamHandler = (payload: ChatStreamPayload) => Promise<void>
