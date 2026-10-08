import { createRouter, createWebHistory } from 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** 浏览器标题 */
    title?: string
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { title: 'AI 应用中心' },
    },
    {
      path: '/travel',
      name: 'travel',
      component: () => import('@/views/TravelAppView.vue'),
      meta: { title: 'AI 旅行规划助手' },
    },
    {
      path: '/manus',
      name: 'manus',
      component: () => import('@/views/ManusAppView.vue'),
      meta: { title: 'AI 超级智能体' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '页面不存在' },
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · ZTS AI Assistant` : 'ZTS AI Assistant'
})

export default router
