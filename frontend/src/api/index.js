import axios from 'axios'
import { useUserStore } from '../store/user'
import router from '../router'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

api.interceptors.request.use(config => {
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = 'Bearer ' + store.token
  }
  return config
})

api.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response && error.response.status === 401) {
      const store = useUserStore()
      store.logout()
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

export default api

// ===== 知识库 API =====
export const knowledgeApi = {
  create: (data) => api.post('/knowledge', data),
  getById: (kbId) => api.get(`/knowledge/${kbId}`),
  getByCourse: (courseId) => api.get(`/knowledge/course/${courseId}`),
  getByTeacher: (teacherId) => api.get(`/knowledge/teacher/${teacherId}`),
  uploadDocument: (kbId, formData) => api.post(`/knowledge/${kbId}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  uploadText: (kbId, data) => api.post(`/knowledge/${kbId}/documents/text`, data),
  getDocuments: (kbId) => api.get(`/knowledge/${kbId}/documents`),
  deleteDocument: (kbId, docId) => api.delete(`/knowledge/${kbId}/documents/${docId}`),
  search: (kbId, query) => api.get(`/knowledge/${kbId}/search`, { params: { q: query } }),
  getTags: (kbId) => api.get(`/knowledge/${kbId}/tags`),
}

// ===== 学情分析 API =====
export const analyticsApi = {
  getClassOverview: (className) => api.get(`/analytics/class/${className}/overview`),
  getStudentProfile: (studentId) => api.get(`/analytics/student/${studentId}/profile`),
  getComparison: (params) => api.get('/analytics/comparison', { params }),
  getAiAnalysis: (className) => api.get(`/analytics/class/${className}/ai-analysis`),
  exportReport: (className) => api.get(`/analytics/class/${className}/export`, { responseType: 'blob' }),
}

// ===== 学习路径 API =====
export const pathApi = {
  getPaths: (studentId) => api.get(`/learning-path/student/${studentId}`),
  getActivePath: (studentId) => api.get(`/learning-path/student/${studentId}/active`),
  updatePath: (pathId, data) => api.put(`/learning-path/${pathId}`, data),
  checkin: (data) => api.post('/learning-path/checkin', data),
  getCalendar: (studentId, month) => api.get(`/learning-path/checkin/calendar/${studentId}`, { params: { month } }),
  todayCheckin: (studentId) => api.get(`/learning-path/checkin/today/${studentId}`),
}

// ===== 资源中心 API =====
export const resourceApi = {
  getByStudent: (studentId) => api.get(`/resources/student/${studentId}`),
  getByType: (studentId, type) => api.get(`/resources/student/${studentId}/type/${type}`),
  getById: (id) => api.get(`/resources/${id}`),
  export: (id) => api.get(`/resources/${id}/export`),
}

// ===== 学习画像 API =====
export const profileApi = {
  get: (sysUserId) => api.get(`/profile/${sysUserId}`),
  update: (sysUserId, data) => api.put(`/profile/${sysUserId}`, data),
  analyze: (sysUserId) => api.post(`/profile/${sysUserId}/analyze`),
}

// ===== 学习评估 API =====
export const evaluationApi = {
  generateTest: (data) => api.post('/evaluation/test/generate', data),
  submitTest: (testId, data) => api.post(`/evaluation/test/${testId}/submit`, data),
  getTests: (studentId) => api.get(`/evaluation/test/student/${studentId}`),
  getErrors: (studentId) => api.get(`/evaluation/errors/student/${studentId}`),
  addError: (data) => api.post('/evaluation/errors', data),
  analyzeError: (errorId) => api.post(`/evaluation/errors/${errorId}/analyze`),
  getWeakness: (studentId) => api.get(`/evaluation/weakness/${studentId}`),
}

// ===== 系统管理 API =====
export const adminApi = {
  getConfig: () => api.get('/admin/config'),
  getConfigByCategory: (cat) => api.get(`/admin/config/category/${cat}`),
  saveConfig: (data) => api.put('/admin/config', data),
  getStatistics: () => api.get('/admin/statistics/overview'),
  getFeatureUsage: () => api.get('/admin/statistics/features'),
  getTodayStats: () => api.get('/admin/statistics/today'),
  getAnomalies: () => api.get('/admin/statistics/anomalies'),
}