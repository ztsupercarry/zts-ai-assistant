<script setup lang="ts">
import { RouterLink, RouterView, useRoute } from 'vue-router'

const route = useRoute()

const navItems = [
  { to: '/', label: '应用中心' },
  { to: '/travel', label: '旅行规划助手' },
  { to: '/manus', label: '超级智能体' },
]

/** 首页需要精确匹配，其余按前缀匹配 */
function isActive(path: string): boolean {
  return path === '/' ? route.path === '/' : route.path.startsWith(path)
}
</script>

<template>
  <div class="app">
    <header class="app__nav">
      <RouterLink class="app__brand" to="/">
        <span class="app__logo">AI</span>
        <span class="app__brand-text">ZTS AI Assistant</span>
      </RouterLink>

      <nav class="app__links">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          class="app__link"
          :class="{ 'app__link--active': isActive(item.to) }"
          :to="item.to"
        >
          {{ item.label }}
        </RouterLink>
      </nav>
    </header>

    <main class="app__main">
      <RouterView />
    </main>
  </div>
</template>
