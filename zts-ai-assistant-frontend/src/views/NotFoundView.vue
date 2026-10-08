<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="not-found">
    <p class="not-found__code">404</p>
    <h1 class="not-found__title">页面不存在</h1>
    <p class="not-found__desc">你访问的地址可能已被移除或从未存在过。</p>
    <RouterLink class="btn btn--primary" to="/">返回应用中心</RouterLink>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
}

.not-found__code {
  margin: 0;
  font-size: 60px;
  font-weight: 700;
  color: rgba(91, 140, 255, 0.35);
}

.not-found__title {
  margin: 0;
  font-size: 20px;
}

.not-found__desc {
  margin: 0 0 8px;
  font-size: 14px;
  color: var(--text-muted);
}
</style>
