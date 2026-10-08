<script setup lang="ts">
import { RouterLink } from 'vue-router'

import { API_BASE_URL } from '@/api/request'

interface AppEntry {
  /** 路由地址 */
  to: string
  name: string
  icon: string
  description: string
  tags: string[]
  /** 后端接口 */
  // endpoint: string
}

/** 主页展示可切换的应用列表，新增应用只需在这里追加一项 + 注册路由 */
const apps: AppEntry[] = [
  {
    to: '/travel',
    name: 'AI 旅行规划助手',
    icon: '🧭',
    description: '聊天式问答，实时生成行程安排、景点推荐与预算建议，支持按会话保留多轮上下文。',
    tags: ['SSE 流式输出', '会话 ID 隔离', '行程规划'],
    // endpoint: 'GET /ai/travel_app/chat/sse',
  },
  {
    to: '/manus',
    name: 'AI 超级智能体',
    icon: '🤖',
    description: '会自主思考并调用工具的超级智能体，适合复杂任务的拆解、执行与结果汇总。',
    tags: ['SSE 流式输出', '工具调用', '任务拆解'],
    // endpoint: 'GET /ai/manus/chat',
  },
]
</script>

<template>
  <div class="home">
    <section class="home__hero">
      <p class="home__badge">ZTS AI Assistant</p>
      <h1 class="home__title">AI 应用中心</h1>
<!--      <p class="home__desc">-->
<!--        选择一个应用开始对话，所有回复都通过 SSE 实时逐字返回。-->
<!--        当前后端地址：<code class="home__code">{{ API_BASE_URL }}</code>-->
<!--      </p>-->
    </section>

    <section class="home__grid">
      <RouterLink v-for="app in apps" :key="app.to" class="app-card" :to="app.to">
        <div class="app-card__head">
          <span class="app-card__icon">{{ app.icon }}</span>
          <span class="app-card__enter">进入 →</span>
        </div>

        <h2 class="app-card__name">{{ app.name }}</h2>
        <p class="app-card__desc">{{ app.description }}</p>

        <div class="app-card__tags">
          <span v-for="tag in app.tags" :key="tag" class="tag">{{ tag }}</span>
        </div>

<!--        <code class="app-card__endpoint">{{ app.endpoint }}</code>-->
      </RouterLink>
    </section>
  </div>
</template>

<style scoped>
.home {
  height: 100%;
  padding: 8px 4px 24px;
  overflow-y: auto;
}

.home__hero {
  max-width: 760px;
  margin: 24px auto 32px;
  text-align: center;
}

.home__badge {
  display: inline-block;
  margin: 0 0 12px;
  padding: 4px 14px;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: var(--accent);
  text-transform: uppercase;
  border: 1px solid rgba(56, 217, 196, 0.35);
  border-radius: 999px;
}

.home__title {
  margin: 0 0 12px;
  font-size: clamp(26px, 4vw, 38px);
  font-weight: 700;
  background: linear-gradient(120deg, #ffffff, #9fc0ff 60%, #6ef0dd);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.home__desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-muted);
}

.home__code {
  padding: 2px 8px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  color: var(--accent);
  background: rgba(56, 217, 196, 0.1);
  border-radius: 6px;
}

.home__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
  max-width: 960px;
  margin: 0 auto;
}

.app-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 22px;
  background: var(--panel);
  border: 1px solid var(--panel-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  transition: transform 0.2s, border-color 0.2s;
}

.app-card:hover {
  transform: translateY(-4px);
  border-color: rgba(91, 140, 255, 0.55);
}

.app-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.app-card__icon {
  font-size: 30px;
}

.app-card__enter {
  font-size: 13px;
  color: var(--primary);
}

.app-card__name {
  margin: 0;
  font-size: 18px;
}

.app-card__desc {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--text-muted);
}

.app-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.app-card__endpoint {
  padding-top: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: rgba(152, 163, 200, 0.85);
  border-top: 1px dashed var(--panel-border);
}
</style>
