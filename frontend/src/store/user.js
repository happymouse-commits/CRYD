import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const id = ref(Number(localStorage.getItem('cryd_id')) || 0)
  const token = ref(localStorage.getItem('cryd_token') || '')
  const username = ref(localStorage.getItem('cryd_username') || '')
  const nickname = ref(localStorage.getItem('cryd_nickname') || '')
  const role = ref(localStorage.getItem('cryd_role') || '')
  const className = ref(localStorage.getItem('cryd_class') || '')
  const studentId = ref(localStorage.getItem('cryd_studentId') || '')

  const isLoggedIn = computed(() => !!token.value)
  const homePath = computed(() => {
    // 教师端/管理员端已打包 (2026-06-13)，统一跳学生端
    const map = { student: '/student/home' }
    return map[role.value] || '/student/home'
  })

  function setUser(data) {
    id.value = data.id || 0
    token.value = data.token || ''
    username.value = data.username || ''
    nickname.value = data.nickname || ''
    role.value = data.role || ''
    className.value = data.className || ''
    studentId.value = data.studentId || ''
    localStorage.setItem('cryd_id', String(id.value))
    localStorage.setItem('cryd_token', token.value)
    localStorage.setItem('cryd_username', username.value)
    localStorage.setItem('cryd_nickname', nickname.value)
    localStorage.setItem('cryd_role', role.value)
    localStorage.setItem('cryd_class', className.value)
    localStorage.setItem('cryd_studentId', studentId.value)
  }

  function logout() {
    id.value = 0
    token.value = ''
    username.value = ''
    nickname.value = ''
    role.value = ''
    className.value = ''
    studentId.value = ''
    localStorage.removeItem('cryd_id')
    localStorage.removeItem('cryd_token')
    localStorage.removeItem('cryd_username')
    localStorage.removeItem('cryd_nickname')
    localStorage.removeItem('cryd_role')
    localStorage.removeItem('cryd_class')
    localStorage.removeItem('cryd_studentId')
  }

  return { id, token, username, nickname, role, className, studentId, isLoggedIn, homePath, setUser, logout }
})