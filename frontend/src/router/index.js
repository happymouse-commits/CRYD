import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const routes = [
  { path: '/', redirect: '/student/home' },
  // 登录页由老版 login.html 独立处理，不走 Vue SPA
  // 学生端
  { path: '/student', component: () => import('../views/学生端/布局.vue'), meta: { role: 'student' },
    children: [
      { path: 'home', component: () => import('../views/学生端/首页.vue') },
      { path: 'chat', component: () => import('../views/学生端/AI辅导.vue') },
      { path: 'profile-card', component: () => import('../views/学生端/我的画像.vue') },
      { path: 'practice', component: () => import('../views/学生端/刷题房.vue') },
      { path: 'resources', component: () => import('../views/学生端/学习资源.vue') },
      { path: 'learn-space', component: () => import('../views/学生端/学习空间.vue') },
      { path: 'learning-path', component: () => import('../views/学生端/学习路径.vue') },
      { path: 'evaluation', component: () => import('../views/学生端/学习评估.vue') },
      { path: 'my-info', component: () => import('../views/学生端/个人信息.vue') },
      // 旧路由重定向
      { path: 'learning-center', redirect: '/student/practice' },
      { path: 'breakthrough', redirect: '/student/practice' },
    ]
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const store = useUserStore()

  // 白名单：不需要登录就能访问
  if (!store.isLoggedIn && to.path !== '/login.html') {
    window.location.href = '/login.html'
    return
  }

  // AI导学监控：未完成导学的用户只能访问 AI辅导 和 个人信息
  if (store.isLoggedIn && !store.onboardingDone) {
    const allowedPaths = ['/student/chat', '/student/my-info']
    if (!allowedPaths.some(p => to.path.startsWith(p)) && to.path.startsWith('/student/')) {
      // 重定向到 AI辅导，让用户完成导学
      next('/student/chat')
      return
    }
  }

  next()
})

export default router
