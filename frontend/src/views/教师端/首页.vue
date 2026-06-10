<template>
  <div class="teacher-home">
    <div class="welcome-section">
      <h2>教师工作台</h2>
      <p>欢迎回来，{{ store.nickname || store.username }}</p>
    </div>

    <!-- 快捷入口 -->
    <div class="quick-actions">
      <el-card v-for="action in actions" :key="action.path" shadow="hover" class="action-card" @click="router.push(action.path)">
        <el-icon :size="28" :color="action.color"><component :is="action.icon" /></el-icon>
        <span>{{ action.name }}</span>
      </el-card>
    </div>

    <!-- 班级概览 -->
    <h3 style="margin: 24px 0 12px">班级概览</h3>
    <div class="stats-grid" v-if="classOverview">
      <el-card><div class="s-val">{{ classOverview.totalStudents }}</div><div class="s-label">学生总数</div></el-card>
      <el-card><div class="s-val">{{ classOverview.avgKnowledgeLevel }}</div><div class="s-label">平均知识水平</div></el-card>
      <el-card><div class="s-val">{{ classOverview.avgProgress }}</div><div class="s-label">平均进度</div></el-card>
      <el-card><div class="s-val">{{ classOverview.totalResourcesGenerated }}</div><div class="s-label">生成资源</div></el-card>
    </div>

    <!-- AI分析 -->
    <el-card style="margin-top: 16px" v-if="aiAnalysis">
      <template #header>AI学情分析</template>
      <div v-html="aiAnalysis.replace(/\n/g, '<br>')" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../store/user'
import { analyticsApi } from '../../api'
import { Collection, Document, DataAnalysis, User } from '@element-plus/icons-vue'

const router = useRouter()
const store = useUserStore()
const classOverview = ref(null)
const aiAnalysis = ref('')

const actions = [
  { name: '知识库管理', path: '/teacher/knowledge-base', icon: Collection, color: '#5b8def' },
  { name: '布置作业', path: '/teacher/assignments', icon: Document, color: '#34d399' },
  { name: '学情分析', path: '/teacher/analysis', icon: DataAnalysis, color: '#f59e0b' },
  { name: '个人信息', path: '/teacher/info', icon: User, color: '#9ca3af' },
]

onMounted(async () => {
  try {
    const cls = store.className || '计科2301'
    const res = await analyticsApi.getClassOverview(cls)
    classOverview.value = res.data

    const ai = await analyticsApi.getAiAnalysis(cls)
    aiAnalysis.value = ai.data?.analysis || ''
  } catch(e) { /* ignore */ }
})
</script>

<style scoped>
.teacher-home { padding: 8px; }
.welcome-section { margin-bottom: 24px; }
.welcome-section h2 { font-size: 22px; margin: 0 0 4px; }
.welcome-section p { color: #9ca3af; margin: 0; }
.quick-actions { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.action-card { cursor: pointer; text-align: center; padding: 16px; transition: transform .2s; }
.action-card:hover { transform: translateY(-4px); }
.action-card span { display: block; margin-top: 8px; font-size: 15px; }
.stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; text-align: center; }
.s-val { font-size: 28px; font-weight: 700; color: #5b8def; }
.s-label { font-size: 13px; color: #9ca3af; margin-top: 4px; }
</style>
