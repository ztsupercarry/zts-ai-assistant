import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools({
      launchEditor: 'D:/ZTS/JetBrains/WebStorm 2026.2.3/bin/webstorm64.exe',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // 开发环境把 /api 代理到 SpringBoot 后端，避免浏览器跨域（SSE 同样适用）
    proxy: {
      '/api': {
        target: 'http://localhost:8123',
        changeOrigin: true,
      },
    },
  },
})
