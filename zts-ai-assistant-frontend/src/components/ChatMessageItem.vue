<script setup lang="ts">
import { computed, ref } from 'vue'

import type { ChatMessage } from '@/types/chat'
import { formatTime } from '@/utils/format'

/** 超过这个长度就默认折叠（工具返回的 JSON 往往很长） */
const COLLAPSE_LIMIT = 420

const props = defineProps<{
  /** 要渲染的一条聊天记录 */
  message: ChatMessage
}>()

const emit = defineEmits<{
  /** 点击「重新生成」 */
  retry: []
}>()

/** 用户消息靠右，AI 消息靠左 */
const isUser = computed(() => props.message.role === 'user')

/** 正在流式接收 */
const isPending = computed(() => props.message.status === 'pending')

/** 请求失败 */
const isError = computed(() => props.message.status === 'error')

/** 智能体单步执行结果 */
const isStep = computed(() => props.message.variant === 'step')

/** 最终回复 */
const isFinal = computed(() => props.message.variant === 'final')

/** 系统提示（执行完成 / 执行出错） */
const isNotice = computed(() => props.message.variant === 'notice')

/** 气泡标题 */
const displayName = computed(() => props.message.label || 'AI 助手')

/** 头像文字 */
const avatarText = computed(() => {
  if (isUser.value) return '我'
  return isNotice.value ? 'ⓘ' : 'AI'
})

/** 过长的步骤内容默认折叠，避免一屏全是工具返回的 JSON */
const expanded = ref(false)
const collapsible = computed(() => isStep.value && props.message.content.length > COLLAPSE_LIMIT)
const collapsed = computed(() => collapsible.value && !expanded.value)
</script>

<template>
  <div
    class="msg"
    :class="[isUser ? 'msg--user' : 'msg--ai', `msg--${message.variant ?? 'text'}`]"
  >
    <div class="msg__avatar" :class="isUser ? 'msg__avatar--user' : 'msg__avatar--ai'">
      {{ avatarText }}
    </div>

    <div class="msg__main">
      <div v-if="!isNotice" class="msg__meta">
        <span class="msg__name">{{ isUser ? '我' : displayName }}</span>
        <span class="msg__time">{{ formatTime(message.createdAt) }}</span>
      </div>

      <div class="msg__bubble" :class="{ 'msg__bubble--error': isError }">
        <span v-if="message.content" class="msg__text" :class="{ 'msg__text--clamped': collapsed }">
          {{ message.content }}
        </span>
        <span v-else-if="isPending" class="msg__thinking">正在思考…</span>

        <!-- 流式输出中的光标 -->
        <span v-if="isPending && message.content" class="msg__caret" aria-hidden="true"></span>

        <p v-if="isError && message.error" class="msg__error">{{ message.error }}</p>
      </div>

      <div v-if="collapsible || (isError && !isUser)" class="msg__actions">
        <button v-if="collapsible" class="msg__link" type="button" @click="expanded = !expanded">
          {{ expanded ? '收起' : `展开全部（${message.content.length} 字）` }}
        </button>

        <button v-if="isError && !isUser" class="msg__link" type="button" @click="emit('retry')">
          重新生成
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.msg {
  display: flex;
  gap: 10px;
  max-width: 100%;
}

.msg--user {
  flex-direction: row-reverse;
}

.msg__avatar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  font-size: 13px;
  font-weight: 600;
  border-radius: 50%;
  user-select: none;
}

.msg__avatar--ai {
  color: #06121f;
  background: linear-gradient(135deg, #4fd1c5, #63a4ff);
}

.msg__avatar--user {
  color: #eaf0ff;
  background: linear-gradient(135deg, #4b6bff, #8b5cf6);
}

.msg__main {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  max-width: min(760px, 78%);
}

.msg--user .msg__main {
  align-items: flex-end;
}

.msg__meta {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--text-muted);
}

.msg--user .msg__meta {
  flex-direction: row-reverse;
}

.msg__name {
  font-weight: 600;
}

/* 步骤气泡的标题用等宽字体，便于和内容对齐 */
.msg--step .msg__name {
  color: var(--accent);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.msg__bubble {
  position: relative;
  padding: 12px 14px;
  font-size: 14px;
  line-height: 1.72;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  border: 1px solid var(--panel-border);
  border-radius: 14px;
}

.msg--ai .msg__bubble {
  color: var(--text);
  background: var(--bubble-ai);
  border-top-left-radius: 4px;
}

.msg--user .msg__bubble {
  color: #fff;
  background: linear-gradient(135deg, #3f6bff, #6d4dff);
  border-color: rgba(255, 255, 255, 0.12);
  border-top-right-radius: 4px;
}

/* 单步执行结果：等宽小字号 + 稍深的底色，和最终回复区分开 */
.msg--ai.msg--step .msg__bubble {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.68;
  background: rgba(14, 20, 40, 0.92);
}

.msg--ai.msg--final .msg__bubble {
  background: rgba(30, 44, 80, 0.92);
  border-color: rgba(56, 217, 196, 0.42);
}

.msg--ai.msg--notice .msg__bubble {
  padding: 8px 12px;
  font-size: 12.5px;
  color: var(--text-muted);
  background: transparent;
  border-style: dashed;
}

.msg--notice .msg__main {
  max-width: min(760px, 88%);
}

.msg__bubble--error {
  border-color: rgba(255, 107, 129, 0.55);
}

.msg__text--clamped {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 6;
  line-clamp: 6;
}

.msg__thinking {
  color: var(--text-muted);
}

.msg__caret {
  display: inline-block;
  width: 8px;
  height: 15px;
  margin-left: 2px;
  vertical-align: -2px;
  background: currentColor;
  border-radius: 2px;
  animation: caret-blink 1s steps(2, start) infinite;
}

.msg__error {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--danger);
}

.msg__actions {
  display: flex;
  gap: 8px;
}

.msg--user .msg__actions {
  justify-content: flex-end;
}

.msg__link {
  padding: 4px 12px;
  font-size: 12px;
  color: var(--text-muted);
  background: transparent;
  border: 1px solid var(--panel-border);
  border-radius: 999px;
  transition: color 0.2s, border-color 0.2s;
}

.msg__link:hover {
  color: var(--text);
  border-color: var(--primary);
}

@keyframes caret-blink {
  50% {
    opacity: 0;
  }
}
</style>
