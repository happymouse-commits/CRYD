<template>
  <div class="student-profile-page">
    <el-button @click="router.back()" style="margin-bottom: 16px">← 返回</el-button>
    <h3>学生画像</h3>

    <el-descriptions v-if="profile" :column="2" border>
      <el-descriptions-item label="姓名">{{ profile.nickname || profile.username }}</el-descriptions-item>
      <el-descriptions-item label="班级">{{ profile.className }}</el-descriptions-item>
      <el-descriptions-item label="学号">{{ profile.studentId }}</el-descriptions-item>
      <el-descriptions-item label="知识水平">{{ profile.knowledgeLevel }}/100</el-descriptions-item>
      <el-descriptions-item label="认知风格">{{ styleLabel(profile.cognitiveStyle) }}</el-descriptions-item>
      <el-descriptions-item label="学习偏好">{{ profile.learningPreference }}</el-descriptions-item>
      <el-descriptions-item label="学习节奏">{{ paceLabel(profile.learningPace) }}</el-descriptions-item>
      <el-descriptions-item label="薄弱环节">{{ profile.weakAreas || '暂无' }}</el-descriptions-item>
      <el-descriptions-item label="连续打卡">{{ profile.streakDays || 0 }}天</el-descriptions-item>
      <el-descriptions-item label="总学习时长">{{ profile.totalStudyMinutes || 0 }}分钟</el-descriptions-item>
    </el-descriptions>

    <el-empty v-else description="未找到学生画像" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { analyticsApi } from '../../api'

const route = useRoute()
const router = useRouter()
const profile = ref(null)

onMounted(async () => {
  const studentId = route.params.id
  if (studentId) {
    try {
      const res = await analyticsApi.getStudentProfile(studentId)
      profile.value = res.data
    } catch(e) {}
  }
})

function styleLabel(s) {
  const map = { visual: '视觉型', auditory: '听觉型', kinesthetic: '动觉型', reading: '阅读型' }
  return map[s] || s || '未知'
}
function paceLabel(p) {
  const map = { fast: '快速型', steady: '稳扎稳打型', slow: '慢热型' }
  return map[p] || p || '未知'
}
</script>

<style scoped>
.student-profile-page { padding: 8px; }
h3 { margin: 0 0 16px; }
</style>
