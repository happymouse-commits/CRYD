<template>
  <div class="learning-path-page">
    <div class="page-header">
      <h2>AI学习路径</h2>
      <p class="subtitle">基于你的画像和错题数据，星火大模型为你规划个性化学习路径</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon blue">🧠</div>
        <div class="stat-content">
          <div class="stat-value">{{ knowledgeLevel }}</div>
          <div class="stat-label">知识水平</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon green">✅</div>
        <div class="stat-content">
          <div class="stat-value">{{ completedCount }}</div>
          <div class="stat-label">已通关卡</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon orange">📝</div>
        <div class="stat-content">
          <div class="stat-value">{{ totalQuestions }}</div>
          <div class="stat-label">总题数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon purple">🔥</div>
        <div class="stat-content">
          <div class="stat-value">{{ streakDays }}</div>
          <div class="stat-label">连续打卡</div>
        </div>
      </div>
    </div>

    <!-- AI学习路径 -->
    <div class="ai-path-section">
      <div class="section-header">
        <h3>🤖 AI个性化学习路径</h3>
        <el-button v-if="!activePath" type="primary" :loading="generating" @click="generatePath">
          生成我的AI学习路径
        </el-button>
        <el-button v-else type="warning" size="small" :loading="generating" @click="generatePath">
          刷新路径
        </el-button>
      </div>

      <div v-if="activePath" class="ai-path-content">
        <div class="path-progress-bar">
          <el-steps :active="activePath.currentStep - 1" finish-status="success" align-center>
            <el-step v-for="i in activePath.totalSteps" :key="i"
                     :title="'阶段' + i" :description="stepDesc(i)" />
          </el-steps>
        </div>
        <div class="path-detail markdown-body" v-html="renderMarkdown(activePath.steps || '')"></div>
      </div>

      <div v-else class="no-path-hint">
        <el-empty description="还没有AI学习路径，点击上方按钮让星火大模型为你生成" :image-size="80" />
      </div>
    </div>

    <!-- 每日打卡 -->
    <div class="checkin-section">
      <h3>📅 每日打卡</h3>
      <div class="checkin-bar">
        <el-button type="success" :disabled="todayChecked" @click="doCheckin">
          {{ todayChecked ? '今日已打卡 ✓' : '打卡签到' }}
        </el-button>
        <span class="streak-info">连续打卡 {{ streakDays }} 天</span>
      </div>
    </div>

    <!-- 闯关进度 -->
    <div v-if="courses.length" class="progress-section">
      <h3>🏰 闯关进度</h3>
      <div v-for="course in courses" :key="course.id" class="course-block">
        <h4 class="course-name">{{ course.name }}</h4>
        <div class="chapters-grid">
          <div v-for="ch in course.chapters" :key="ch.id"
               :class="['chapter-card', ch.status === 'completed' ? 'done' : 'pending']">
            <el-progress type="circle" :percentage="ch.progress || 0" :width="56"
                         :color="ch.status === 'completed' ? '#67C23A' : '#409EFF'" />
            <span class="chapter-name">{{ ch.name }}</span>
            <el-tag :type="ch.status === 'completed' ? 'success' : 'warning'" size="small">
              {{ ch.status === 'completed' ? '已通关' : '闯关中' }}
            </el-tag>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import api from '../../api'

const store = useUserStore()
const activePath = ref(null)
const courses = ref([])
const generating = ref(false)
const todayChecked = ref(false)
const profile = ref({})
const streakDays = ref(0)

const knowledgeLevel = computed(() => profile.value.knowledgeLevel || 0)
const completedCount = computed(() =>
  courses.value.reduce((s, c) => s + (c.chapters || []).filter(ch => ch.status === 'completed').length, 0)
)
const totalQuestions = computed(() =>
  courses.value.reduce((s, c) => s + (c.chapters || []).reduce((a, ch) => a + (ch.questionCount || 0), 0), 0)
)

function stepDesc(i) { return i <= (activePath.value?.currentStep || 0) ? '已完成' : '待学习' }

function renderMarkdown(text) {
  if (!text) return ''
  return text
    .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code class="$1">$2</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/^### (.+)$/gm, '<h4>$1</h4>')
    .replace(/^## (.+)$/gm, '<h3>$1</h3>')
    .replace(/^- (.+)$/gm, '<li>$1</li>')
    .replace(/\n\n/g, '<br><br>')
    .replace(/\n/g, '<br>')
}

async function loadData() {
  try {
    const r = await api.get('/learning-path/student/' + store.id + '/active')
    activePath.value = (r.data && r.data.status === 'active') ? r.data : null
  } catch {}
  try {
    const r = await api.get('/student/progress/' + store.id)
    courses.value = r.data || []
  } catch {}
  try {
    const r = await api.get('/profile/' + store.id)
    profile.value = r.data || {}
  } catch {}
  try {
    const r = await api.get('/learning-path/checkin/today/' + store.id)
    todayChecked.value = r.data?.checkedIn || false
  } catch {}
  try {
    const r = await api.get('/learning-path/checkin/calendar/' + store.id, { params: { month: new Date().toISOString().slice(0, 7) } })
    streakDays.value = r.data?.streakDays || 0
  } catch {}
}

async function generatePath() {
  generating.value = true
  try {
    const r = await api.post('/learning-path/generate/' + store.id)
    activePath.value = r.data
    ElMessage.success('AI学习路径已生成！')
  } catch { ElMessage.error('生成失败，请稍后重试') }
  finally { generating.value = false }
}

async function doCheckin() {
  try {
    await api.post('/learning-path/checkin', { studentId: store.id })
    todayChecked.value = true
    // 从后端重新获取连续打卡天数
    try {
      const r = await api.get('/learning-path/checkin/calendar/' + store.id, { params: { month: new Date().toISOString().slice(0, 7) } })
      streakDays.value = r.data?.streakDays || 0
    } catch {}
    ElMessage.success('打卡成功！')
  } catch { ElMessage.error('打卡失败') }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.learning-path-page { }
.page-header { margin-bottom: 20px; }
.page-header h2 { margin: 0 0 4px 0; font-size: 22px; }
.subtitle { color: #909399; font-size: 14px; margin: 0; }

.stats-row { display: flex; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }
.stat-card {
  flex: 1; min-width: 140px; background: #fff; border-radius: 12px; padding: 16px 20px;
  display: flex; align-items: center; gap: 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.stat-icon { font-size: 28px; }
.stat-value { font-size: 24px; font-weight: 700; color: #303133; }
.stat-label { font-size: 13px; color: #909399; }

.ai-path-section { background: #fff; border-radius: 12px; padding: 20px; margin-bottom: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.section-header h3 { margin: 0; font-size: 17px; }
.ai-path-content { }
.path-progress-bar { margin-bottom: 20px; padding: 16px 0; }
.path-detail { padding: 16px; background: #f8f9fa; border-radius: 8px; max-height: 500px; overflow-y: auto; line-height: 1.8; font-size: 14px; }
.path-detail :deep(pre) { background: #e8e8f0; padding: 10px; border-radius: 6px; overflow-x: auto; }
.path-detail :deep(code) { font-family: Consolas, monospace; font-size: 13px; }
.no-path-hint { padding: 20px 0; }

.checkin-section { margin-bottom: 20px; }
.checkin-section h3 { font-size: 17px; margin-bottom: 12px; }
.checkin-bar { display: flex; align-items: center; gap: 16px; }
.streak-info { font-size: 14px; color: #67C23A; font-weight: 600; }

.progress-section { }
.progress-section h3 { font-size: 17px; margin-bottom: 14px; }
.course-block { margin-bottom: 20px; }
.course-name { font-size: 15px; margin: 0 0 10px 0; color: #303133; }
.chapters-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 12px; }
.chapter-card {
  background: #fff; border-radius: 10px; padding: 14px;
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  box-shadow: 0 2px 6px rgba(0,0,0,0.04); border: 2px solid transparent;
}
.chapter-card.done { border-color: #67C23A; }
.chapter-card.pending { border-color: #409EFF; }
.chapter-name { font-size: 13px; text-align: center; line-height: 1.3; }
</style>
