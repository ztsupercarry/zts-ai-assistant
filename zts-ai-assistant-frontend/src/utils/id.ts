/**
 * 生成聊天室 id，用于区分不同的会话（每次进入页面 / 点击「新建会话」都会生成一个新的）。
 * 优先使用 crypto.randomUUID（非 HTTPS 的 localhost 环境同样可用），失败时退回时间戳 + 随机串。
 */
export function createChatId(): string {
  return `chat_${randomToken()}`
}

/** 生成消息 id */
export function createMessageId(): string {
  return `msg_${randomToken()}`
}

function randomToken(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID().replace(/-/g, '')
  }
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`
}
