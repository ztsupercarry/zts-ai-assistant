<script setup lang="ts">
import ChatRoom from '@/components/ChatRoom.vue'
import { doChatWithTravelAppSse } from '@/api/ai'

const suggestions = [
  '帮我规划一份 3 天 2 晚的成都旅行计划，预算 3000 元',
  '周末想去杭州周边爬山，推荐一条适合新手的路线',
  '带 6 岁小朋友去上海玩 4 天，行程怎么安排比较轻松？',
  '去日本关西 5 天，第一次自由行需要注意什么？',
]
</script>

<template>
  <ChatRoom
    title="AI 旅行规划助手"
    assistant-name="旅行规划助手"
    subtitle="通过 SSE 调用 doChatWithTravelAppSse，按会话 ID 保留多轮上下文"
    placeholder="说说你想去哪、几天、预算和同行人，我来帮你规划…"
    empty-text="告诉我你的目的地和出行计划，我帮你安排行程"
    :suggestions="suggestions"
    :send="doChatWithTravelAppSse"
  />
</template>
