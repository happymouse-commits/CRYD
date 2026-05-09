import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', component: () => import('../views/Login.vue') },
  // Student
  { path: '/student', component: () => import('../views/student/Layout.vue'), meta: { role: 'student' },
    children: [
      { path: 'chat', component: () => import('../views/student/Chat.vue') },
      { path: 'profile', component: () => import('../views/student/Profile.vue') },
      { path: 'resources', component: () => import('../views/student/Resources.vue') },
      { path: 'path', component: () => import('../views/student/LearningPath.vue') },
    ]
  },
  // Teacher
  { path: '/teacher', component: () => import('../views/teacher/Layout.vue'), meta: { role: 'teacher' },
    children: [
      { path: 'courses', component: () => import('../views/teacher/Courses.vue') },
      { path: 'assignments', component: () => import('../views/teacher/Assignments.vue') },
      { path: 'submissions', component: () => import('../views/teacher/Submissions.vue') },
      { path: 'analysis', component: () => import('../views/teacher/Analysis.vue') },
    ]
  },
  // Counselor
  { path: '/counselor', component: () => import('../views/counselor/Layout.vue'), meta: { role: 'counselor' },
    children: [
      { path: 'warnings', component: () => import('../views/counselor/Warnings.vue') },
      { path: 'leaves', component: () => import('../views/counselor/Leaves.vue') },
      { path: 'profiles', component: () => import('../views/counselor/Profiles.vue') },
    ]
  },
  // Admin
  { path: '/admin', component: () => import('../views/admin/Layout.vue'), meta: { role: 'admin' },
    children: [
      { path: 'dashboard', component: () => import('../views/admin/Dashboard.vue') },
      { path: 'users', component: () => import('../views/admin/Users.vue') },
      { path: 'system', component: () => import('../views/admin/System.vue') },
    ]
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.path === '/login') {
    // 访问旧登录页时，重定向到新前端登录页
    window.location.href = '/login.html'
    return
  }
  const store = useUserStore()
  if (!store.isLoggedIn) {
    // 未登录，跳转到新前端登录页
    window.location.href = '/login.html'
    return
  }
  if (to.meta.role && to.meta.role !== store.role) return next(store.homePath)
  next()
})

export default router
