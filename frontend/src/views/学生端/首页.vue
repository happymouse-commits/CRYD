<template>
  <div class="student-home">
    <div class="welcome-section">
      <h2>欢迎回来，{{ store.nickname || store.username }}</h2>
      <p>继续你的C语言学习之旅吧！</p>
    </div>

    <!-- 学习进度概览 -->
    <div class="stats-row">
      <el-card class="stat-card">
        <div class="stat-value">{{ profile.knowledgeLevel || 0 }}</div>
        <div class="stat-label">知识水平</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-value">{{ profile.streakDays || 0 }}天</div>
        <div class="stat-label">连续打卡</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-value">{{ resources.length }}</div>
        <div class="stat-label">学习资源</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-value">{{ (profile.totalStudyMinutes || 0) }}分</div>
        <div class="stat-label">学习时长</div>
      </el-card>
    </div>

    <!-- 功能导航 -->
    <h3 style="margin: 24px 0 16px">功能导航</h3>
    <div class="feature-grid">
      <el-card class="feature-card" v-for="f in features" :key="f.path" @click="navigateTo(f.path)" shadow="hover">
        <el-icon :size="32" :color="f.color"><component :is="f.icon" /></el-icon>
        <h4>{{ f.name }}</h4>
        <p>{{ f.desc }}</p>
      </el-card>
    </div>

    <!-- 推荐资源 -->
    <h3 style="margin: 24px 0 16px">推荐资源</h3>
    <el-row :gutter="16">
      <el-col :span="8" v-for="r in recommendedResources" :key="r.id">
        <el-card shadow="hover">
          <template #header>{{ r.title }}</template>
          <p class="resource-desc">{{ truncate(r.content, 80) }}</p>
          <el-tag size="small" :type="r.difficulty === '入门' ? 'success' : 'warning'">{{ r.difficulty }}</el-tag>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../store/user'
import { profileApi, resourceApi } from '../../api'
import { ChatDotRound, Reading, DataAnalysis, Collection, EditPen, Trophy } from '@element-plus/icons-vue'

const router = useRouter()
const store = useUserStore()
const profile = ref({})
const resources = ref([])

const features = [
  { name: 'AI辅导', desc: '与AI老师对话学习', path: '/student/chat', icon: ChatDotRound, color: '#409EFF' },
  { name: '我的画像', desc: '查看你的学习画像', path: '/student/profile-card', icon: DataAnalysis, color: '#67C23A' },
  { name: '学习路径', desc: '个性化学习规划', path: '/student/learning-path', icon: Collection, color: '#E6A23C' },
  { name: '资源中心', desc: '学习资源管理', path: '/student/resources', icon: Reading, color: '#F56C6C' },
  { name: '刷题房', desc: '闯关练习提升', path: '/student/practice', icon: EditPen, color: '#909399' },
  { name: '学习评估', desc: '测试与报告', path: '/student/evaluation', icon: Trophy, color: '#8B5CF6' },
]

const recommendedResources = ref([])

onMounted(async () => {
  try {
    const p = await profileApi.get(store.id)
    profile.value = p.data || {}

    const res = await resourceApi.getByStudent(store.id)
    resources.value = res.data || []

    // 取最新3个资源作为推荐
    recommendedResources.value = (res.data || []).slice(0, 3).map(r => ({
      ...r, title: r.title || '学习资源'
    }))
  } catch (e) { /* ignore */ }
})

function navigateTo(path) {
  router.push(path)
}

function truncate(text, len) {
  if (!text) return ''
  return text.length > len ? text.substring(0, len) + '...' : text
}
</script>

<style scoped>
.student-home { padding: 8px; }
.welcome-section { margin-bottom: 24px; }
.welcome-section h2 { font-size: 22px; margin: 0 0 4px; }
.welcome-section p { color: #909399; margin: 0; }
.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.stat-card { text-align: center; }
.stat-value { font-size: 28px; font-weight: 700; color: #409EFF; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.feature-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.feature-card { cursor: pointer; text-align: center; transition: transform .2s; }
.feature-card:hover { transform: translateY(-4px); }
.feature-card h4 { margin: 12px 0 4px; }
.feature-card p { color: #909399; font-size: 13px; margin: 0; }
.resource-desc { color: #606266; font-size: 13px; margin-bottom: 8px; }
</style>
