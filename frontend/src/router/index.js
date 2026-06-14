import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const routes = [
  { path: '/', redirect: '/login.html' },
  // 学生端
  { path: '/student', component: () => import('../views/学生端/布局.vue'), meta: { role: 'student' },
    children: [
      { path: 'home', component: () => import('../views/学生端/首页.vue') },
      { path: 'chat', component: () => import('../views/学生端/AI辅导.vue') },
      { path: 'profile-card', component: () => import('../views/学生端/我的画像.vue') },
      { path: 'practice', component: () => import('../views/学生端/刷题房.vue') },
      { path: 'resources', component: () => import('../views/学生端/学习资源.vue') },
      { path: 'learning-path', component: () => import('../views/学生端/学习路径.vue') },
      { path: 'evaluation', component: () => import('../views/学生端/学习评估.vue') },
      { path: 'my-info', component: () => import('../views/学生端/个人信息.vue') },
      // 旧路由重定向
      { path: 'learning-center', redirect: '/student/practice' },
      { path: 'breakthrough', redirect: '/student/practice' },
    ]
  },

  // ========== 教师端 & 管理员端已打包 (2026-06-13) ==========
  // 题库数据已迁移至 AI 知识库，用于新用户诊断后智能出题
  // 如需恢复，取消下方注释即可
  /*
  // 教师端
  { path: '/teacher', component: () => import('../views/教师端/布局.vue'), meta: { role: 'teacher' },
    children: [
      { path: 'home', component: () => import('../views/教师端/首页.vue') },
      { path: 'knowledge-base', component: () => import('../views/教师端/知识库管理.vue') },
      { path: 'assignments', component: () => import('../views/教师端/布置作业.vue') },
      { path: 'analysis', component: () => import('../views/教师端/数据分析.vue') },
      { path: 'students/:id', component: () => import('../views/教师端/学生画像.vue') },
      { path: 'info', component: () => import('../views/教师端/教师信息.vue') },
    ]
  },
  // 管理员
  { path: '/admin', component: () => import('../views/管理员/布局.vue'), meta: { role: 'admin' },
    children: [
      { path: 'dashboard', component: () => import('../views/管理员/仪表盘.vue') },
      { path: 'users', component: () => import('../views/管理员/用户管理.vue') },
      { path: 'config', component: () => import('../views/管理员/系统配置.vue') },
      { path: 'statistics', component: () => import('../views/管理员/数据统计.vue') },
    ]
  },
  */
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
  if (to.meta.role && to.meta.role !== store.role) return next(store.homePath)
  next()
})

export default router
