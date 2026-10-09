<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue'

import ChatMessageItem from '@/components/ChatMessageItem.vue'
import type { ChatBlockVariant, ChatMessage, ChatStatus, ChatStreamHandler } from '@/types/chat'
import { createAgentStreamSplitter, type AgentStreamSegment } from '@/utils/agentStream'
import { createChatId, createMessageId } from '@/utils/id'

const props = withDefaults(
  defineProps<{
    /** 应用名称 */
    title: string
    /** 一句话描述 */
    subtitle?: string
    /** AI 气泡上显示的昵称（例如「旅行规划助手」「超级智能体」） */
    assistantName?: string
    /** 输入框占位提示 */
    placeholder?: string
    /** 空状态文案 */
    emptyText?: string
    /** 空状态下的推荐问题 */
    suggestions?: string[]
    /** 是否生成并携带会话 id */
    useChatId?: boolean
    /** 是否允许切换「流式输出」 */
    switchableStream?: boolean
    /**
     * 流式内容如何分段：
     * - text  所有增量追加到同一个气泡（普通对话，如旅行规划助手）
     * - steps 按智能体的 Step 分段，每步一个气泡，并单独展示最终回复（Manus 智能体）
     */
    streamMode?: 'text' | 'steps'
    /** 具体的接口调用实现，由页面注入 */
    send: ChatStreamHandler
  }>(),
  {
    subtitle: '',
    assistantName: 'AI 助手',
    placeholder: '输入你的问题，Enter 发送，Shift + Enter 换行',
    emptyText: '还没有聊天记录，先问一句试试～',
    suggestions: () => [],
    useChatId: true,
    switchableStream: true,
    streamMode: 'text',
  },
)

const messages = ref<ChatMessage[]>([])
const input = ref('')
/** 进入页面时自动生成聊天室 id（只用于请求，不在界面上展示） */
const chatId = ref<string | undefined>(props.useChatId ? createChatId() : undefined)
const loading = ref(false)
const streamEnabled = ref(true)

const bodyRef = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLTextAreaElement | null>(null)

let abortController: AbortController | null = null

const canSend = computed(() => input.value.trim().length > 0 && !loading.value)

/**
 * 滚动到底部。
 * 默认「贴底跟随」：只有用户本来就停在底部附近才自动滚动，避免打断他向上翻看历史；
 * force = true（发送新消息）时无论当前位置都滚到底。
 */
async function scrollToBottom(force = false): Promise<void> {
  const element = bodyRef.value
  const nearBottom =
    force || !element || element.scrollHeight - element.scrollTop - element.clientHeight < 96
  await nextTick()
  const target = bodyRef.value
  if (target && nearBottom) target.scrollTop = target.scrollHeight
}

/** 生成一条 AI 气泡（reactive 便于流式追加内容时触发更新） */
function createBlockMessage(options: {
  content: string
  variant?: ChatBlockVariant
  label?: string
  step?: number
  status?: ChatStatus
  error?: string
}): ChatMessage {
  return reactive<ChatMessage>({
    id: createMessageId(),
    role: 'assistant',
    content: options.content,
    status: options.status ?? 'done',
    variant: options.variant ?? 'text',
    label: options.label,
    step: options.step,
    error: options.error,
    createdAt: Date.now(),
  })
}

/** 发送一条消息（preset 用于点击推荐问题时直接发送） */
async function submit(preset?: string): Promise<void> {
  if (loading.value) return

  const text = (preset ?? input.value).trim()
  if (!text) return

  if (preset === undefined) input.value = ''
  resetComposerHeight()

  messages.value.push({
    id: createMessageId(),
    role: 'user',
    content: text,
    status: 'done',
    createdAt: Date.now(),
  })

  // AI 回复先占位：text 模式下它就是唯一的回复气泡；steps 模式下它会变成第一个步骤气泡
  const reply = createBlockMessage({ content: '', status: 'pending' })
  messages.value.push(reply)

  const controller = new AbortController()
  abortController = controller
  loading.value = true
  void scrollToBottom(true)

  // 本轮请求产生的所有气泡（第一个是上面的占位气泡）
  const blocks: ChatMessage[] = []
  let activeBlock: ChatMessage | null = null
  const splitter = props.streamMode === 'steps' ? createAgentStreamSplitter() : null

  /** 取当前要写入的气泡：需要新开就新建（首个气泡复用占位气泡，避免留下一个空气泡） */
  const openBlock = (segment: AgentStreamSegment): ChatMessage => {
    // 上一个气泡收尾，流式光标跟着当前气泡走
    if (activeBlock) activeBlock.status = 'done'

    if (!blocks.length) {
      reply.variant = segment.variant
      reply.label = segment.label
      reply.step = segment.step
      activeBlock = reply
    } else {
      const block = createBlockMessage({
        content: '',
        status: 'pending',
        variant: segment.variant,
        label: segment.label,
        step: segment.step,
      })
      activeBlock = block
      messages.value.push(block)
    }

    const block = activeBlock
    blocks.push(block)
    return block
  }

  /** 流式回调：把增量写入对应的气泡 */
  const writeChunk = (chunk: string): void => {
    if (!chunk) return

    const segments: AgentStreamSegment[] = splitter
      ? splitter(chunk)
      : [{ startNewBlock: false, variant: 'text', text: chunk }]

    for (const segment of segments) {
      let target = activeBlock
      if (segment.startNewBlock || !target) target = openBlock(segment)
      target.content += segment.text
    }

    void scrollToBottom()
  }

  /** 收尾：把本轮气泡标记完成；没有内容或出错时给出提示 */
  const finishBlocks = (aborted: boolean, errorMessage?: string): void => {
    blocks.forEach((block) => {
      block.status = 'done'
    })

    const written = blocks.filter((block) => block.content.length > 0)

    // 清掉没有产生任何内容的气泡（例如后端只下发了一个空的 Step 事件）
    if (written.length) {
      messages.value = messages.value.filter(
        (block) => block.content.length > 0 || Boolean(block.error),
      )
    }

    if (errorMessage) {
      if (!written.length) {
        // 还没有任何输出：占位气泡直接变成错误提示
        reply.status = 'error'
        reply.error = errorMessage
        return
      }
      messages.value.push(
        createBlockMessage({
          content: '',
          variant: 'notice',
          label: '执行出错',
          status: 'error',
          error: errorMessage,
        }),
      )
      return
    }

    if (!written.length) {
      reply.status = 'done'
      reply.content = aborted ? '（已停止生成）' : '（模型没有返回任何内容）'
      return
    }

    // steps 模式：一直没收到最终回复时补一条提示，避免用户以为界面卡住（用户主动停止时不提示）
    if (!aborted && splitter && !written.some((block) => block.variant === 'final')) {
      const stepCount = written.filter((block) => block.variant === 'step').length
      messages.value.push(
        createBlockMessage({
          content: stepCount
            ? `智能体已执行 ${stepCount} 步，但没有收到最终回复。`
            : '智能体已执行完成。',
          variant: 'notice',
          label: '执行完成',
        }),
      )
    }
  }

  try {
    await props.send({
      message: text,
      chatId: chatId.value,
      stream: streamEnabled.value,
      signal: controller.signal,
      onMessage: writeChunk,
    })

    finishBlocks(controller.signal.aborted)
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    finishBlocks(controller.signal.aborted, controller.signal.aborted ? undefined : message)
  } finally {
    // 只有「当前这次请求」才允许收尾：停止后立刻重发时，旧请求不能覆盖新请求的状态
    if (abortController === controller) {
      abortController = null
      loading.value = false
      await scrollToBottom()
      inputRef.value?.focus()
    }
  }
}

/**
 * 停止生成：中断请求并立即恢复按钮状态。
 * 这里**不**把 abortController 置空，被中断的请求仍能在 finally 里完成收尾（滚动 / 聚焦）；
 * 若用户紧接着重新发送，finally 里的身份判断会保证旧请求不干扰新请求。
 */
function stop(): void {
  abortController?.abort()
  loading.value = false
}

/** 重新发送最后一条用户消息（出错后点「重新生成」） */
function retryLast(): void {
  if (loading.value) return

  const items = messages.value
  const index = items.map((item) => item.role).lastIndexOf('user')
  const lastUserMessage = index === -1 ? undefined : items[index]
  if (!lastUserMessage) return

  const text = lastUserMessage.content
  items.splice(index)
  void submit(text)
}

/** 新建会话：清空聊天记录并重新生成聊天室 id */
async function resetConversation(): Promise<void> {
  stop()
  messages.value = []
  if (props.useChatId) chatId.value = createChatId()
  await scrollToBottom()
  inputRef.value?.focus()
}

/**
 * Enter 发送、Shift + Enter 换行；中文输入法组合期间的回车不发送。
 * 注意：Vue 的 `@keydown.enter` 只要按下的键是 Enter 就会触发（按住 Shift 同样命中），
 * 所以必须在这里显式放行 Shift + Enter，否则会拦掉浏览器默认的换行。
 */
function handleEnter(event: KeyboardEvent): void {
  if (event.isComposing || event.keyCode === 229) return
  if (event.shiftKey) return
  if (loading.value) return
  event.preventDefault()
  void submit()
}

/** 输入框随内容自动增高 */
function autoGrow(): void {
  const element = inputRef.value
  if (!element) return
  element.style.height = 'auto'
  element.style.height = `${Math.min(element.scrollHeight, 168)}px`
}

function resetComposerHeight(): void {
  const element = inputRef.value
  if (element) element.style.height = 'auto'
}

onBeforeUnmount(() => {
  abortController?.abort()
})
</script>

<template>
  <section class="chat">
    <header class="chat__header">
      <div class="chat__heading">
        <h2 class="chat__title">{{ title }}</h2>
<!--        <p v-if="subtitle" class="chat__subtitle">{{ subtitle }}</p>-->
      </div>

      <div class="chat__actions">
        <label
          v-if="switchableStream"
          class="chat__switch"
          title="关闭后改用 axios 一次性获取完整回复"
        >
          <input v-model="streamEnabled" type="checkbox" :disabled="loading" />
          <span>流式输出</span>
        </label>

        <button class="btn btn--ghost" type="button" @click="resetConversation">新建会话</button>
      </div>
    </header>

    <div ref="bodyRef" class="chat__body" role="log" aria-label="聊天记录">
      <div v-if="!messages.length" class="chat__empty">
        <div class="chat__empty-icon">💬</div>
        <p class="chat__empty-text">{{ emptyText }}</p>
        <div v-if="suggestions.length" class="chat__suggestions">
          <button
            v-for="item in suggestions"
            :key="item"
            class="chip"
            type="button"
            @click="submit(item)"
          >
            {{ item }}
          </button>
        </div>
      </div>

      <ChatMessageItem
        v-for="item in messages"
        :key="item.id"
        :message="item"
        :assistant-name="assistantName"
        @retry="retryLast"
      />
    </div>

    <footer class="chat__composer">
      <div class="chat__input-wrap">
        <textarea
          ref="inputRef"
          v-model="input"
          class="chat__input"
          :placeholder="placeholder"
          rows="1"
          @input="autoGrow"
          @keydown.enter="handleEnter"
        />
        <button v-if="loading" class="btn btn--stop" type="button" @click="stop">停止</button>
        <button v-else class="btn btn--primary" type="button" :disabled="!canSend" @click="submit()">
          发送
        </button>
      </div>
      <p class="chat__hint">
        <template v-if="loading">正在生成回复…可以先输入下一句</template>
        <template v-else>Enter 发送 · Shift + Enter 换行</template>
        · 当前模式：{{ streamEnabled ? 'SSE 流式（逐字返回）' : 'Axios 一次性返回' }}
      </p>
    </footer>
  </section>
</template>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: var(--panel);
  border: 1px solid var(--panel-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  backdrop-filter: blur(12px);
}

.chat__header {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--panel-border);
}

.chat__title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

.chat__subtitle {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--text-muted);
}

.chat__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.chat__switch {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 12.5px;
  color: var(--text-muted);
  cursor: pointer;
  user-select: none;
}

.chat__switch input {
  width: 15px;
  height: 15px;
  accent-color: var(--primary);
  cursor: pointer;
}

.chat__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 18px;
  min-height: 0;
  padding: 20px;
  overflow-y: auto;
}

.chat__empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 14px;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.chat__empty-icon {
  font-size: 40px;
}

.chat__empty-text {
  margin: 0;
  color: var(--text-muted);
}

.chat__suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  max-width: 620px;
}

.chat__composer {
  padding: 14px 20px 16px;
  border-top: 1px solid var(--panel-border);
}

.chat__input-wrap {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 8px 8px 8px 14px;
  background: rgba(9, 14, 30, 0.72);
  border: 1px solid var(--panel-border);
  border-radius: var(--radius-md);
  transition: border-color 0.2s;
}

.chat__input-wrap:focus-within {
  border-color: rgba(91, 140, 255, 0.6);
}

.chat__input {
  flex: 1;
  max-height: 168px;
  padding: 8px 0;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text);
  resize: none;
  background: transparent;
  border: none;
  outline: none;
}

.chat__input::placeholder {
  color: rgba(152, 163, 200, 0.75);
}

.chat__hint {
  margin: 8px 2px 0;
  font-size: 12px;
  color: var(--text-muted);
}

@media (max-width: 640px) {
  .chat__body {
    padding: 14px;
  }
}
</style>
