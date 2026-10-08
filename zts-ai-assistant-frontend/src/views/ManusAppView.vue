<script setup lang="ts">
import ChatRoom from '@/components/ChatRoom.vue'
import { doChatWithManus } from '@/api/ai'

const suggestions = [
  '帮我调研一下 2025 年国内 AI 编程助手市场，输出一份对比结论',
  '把“我想做一个个人博客”拆解成可执行的任务清单',
  '写一个 Python 脚本统计某个目录下所有文件的代码行数',
  '先搜索资料，再帮我总结成 5 条要点',
]
</script>

<template>
  <ChatRoom
    title="AI 超级智能体"
    subtitle="提出你的需求，AI 智能体会自主思考并调用工具"
    placeholder="描述一个复杂任务，交给智能体逐步完成…"
    empty-text="把复杂任务交给我，我会先思考再动手"
    :suggestions="suggestions"
    :use-chat-id="false"
    stream-mode="steps"
    :send="doChatWithManus"
  />
</template>
