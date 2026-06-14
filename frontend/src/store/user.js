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
  const onboardingDone = ref(localStorage.getItem('cryd_onboarding_done') === 'true')

  const isLoggedIn = computed(() => !!token.value)
  const homePath = computed(() => '/student/home')

  function setUser(data) {
    id.value = data.id || 0
    token.value = data.token || ''
    username.value = data.username || ''
    nickname.value = data.nickname || ''
    role.value = data.role || ''
    className.value = data.className || ''
    studentId.value = data.studentId || ''
    onboardingDone.value = !!data.onboardingDone
    localStorage.setItem('cryd_id', String(id.value))
    localStorage.setItem('cryd_token', token.value)
    localStorage.setItem('cryd_username', username.value)
    localStorage.setItem('cryd_nickname', nickname.value)
    localStorage.setItem('cryd_role', role.value)
    localStorage.setItem('cryd_class', className.value)
    localStorage.setItem('cryd_studentId', studentId.value)
    localStorage.setItem('cryd_onboarding_done', String(onboardingDone.value))
  }

  function setOnboardingDone(done) {
    onboardingDone.value = done
    localStorage.setItem('cryd_onboarding_done', String(done))
  }

  function logout() {
    id.value = 0
    token.value = ''
    username.value = ''
    nickname.value = ''
    role.value = ''
    className.value = ''
    studentId.value = ''
    onboardingDone.value = false
    localStorage.removeItem('cryd_id')
    localStorage.removeItem('cryd_token')
    localStorage.removeItem('cryd_username')
    localStorage.removeItem('cryd_nickname')
    localStorage.removeItem('cryd_role')
    localStorage.removeItem('cryd_class')
    localStorage.removeItem('cryd_studentId')
    localStorage.removeItem('cryd_onboarding_done')
  }

  return { id, token, username, nickname, role, className, studentId, onboardingDone, isLoggedIn, homePath, setUser, setOnboardingDone, logout }
})