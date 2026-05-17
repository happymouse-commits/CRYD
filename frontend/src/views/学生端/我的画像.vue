<template>
  <div class="profile-page">
    <div class="page-header">
      <div class="header-left">
        <h2>我的画像</h2>
        <p class="subtitle">学习画像基于AI辅导对话自动分析生成</p>
      </div>
      <div class="header-right">
        <span v-if="lastUpdate" class="update-time">最近更新：{{ lastUpdate }}</span>
        <el-button type="primary" :loading="analyzing" @click="triggerAnalysis">
          <el-icon><Refresh /></el-icon> 从AI辅导同步更新
        </el-button>
      </div>
    </div>

    <el-card shadow="hover" class="profile-card" body-style="padding: 0;">
      <template #header>
        <div class="card-header">
          <el-icon><TrendCharts /></el-icon>
          <span>学习画像</span>
          <el-tag v-if="completedDims >= 8" type="success" size="small">画像完善</el-tag>
          <el-tag v-else-if="completedDims >= 4" type="warning" size="small">收集中（{{ completedDims }}/8）</el-tag>
          <el-tag v-else type="info" size="small">待完善（{{ completedDims }}/8）</el-tag>
        </div>
      </template>

      <div class="profile-grid">
        <div v-for="dim in dimensions" :key="dim.key"
          class="dim-card" :class="{ active: dim.value !== null }">
          <div class="dim-top">
            <span class="dim-icon">{{ dim.icon }}</span>
            <span class="dim-name">{{ dim.name }}</span>
          </div>
          <div class="dim-value" v-if="dim.value !== null">
            <span class="dim-score" v-if="dim.score !== undefined">{{ dim.score }}</span>
            <span class="dim-text">{{ dim.value }}</span>
          </div>
          <div class="dim-empty" v-else>
            <el-icon><QuestionFilled /></el-icon>
            待识别
          </div>
          <div class="dim-desc">{{ dim.desc }}</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import api from '../../api'
import { TrendCharts, QuestionFilled, Refresh } from '@element-plus/icons-vue'

const store = useUserStore()
const analyzing = ref(false)
const lastUpdate = ref('')

const dimensions = ref([
  { key: 'knowledgeLevel', name: '知识基础', icon: '📚', value: null, score: null, desc: '' },
  { key: 'cognitiveStyle', name: '认知风格', icon: '🧠', value: null, desc: '' },
  { key: 'learningPreference', name: '学习偏好', icon: '🎯', value: null, desc: '' },
  { key: 'learningPace', name: '学习节奏', icon: '⏱️', value: null, desc: '' },
  { key: 'interestDirection', name: '兴趣方向', icon: '⭐', value: null, desc: '' },
  { key: 'weakAreas', name: '薄弱环节', icon: '⚠️', value: null, desc: '' },
  { key: 'studyMotivation', name: '学习动机', icon: '💪', value: null, desc: '' },
  { key: 'focusLevel', name: '专注力', icon: '🔥', value: null, desc: '' },
])

const completedDims = computed(() => dimensions.value.filter(d => d.value !== null).length)

const labelMaps = {
  cognitiveStyle: { visual: '视觉型', auditory: '听觉型', kinesthetic: '动觉型', reading: '阅读型' },
  learningPreference: { video: '视频偏好', doc: '文档偏好', exercise: '实践偏好', mixed: '混合型' },
  learningPace: { fast: '快速型', steady: '稳健型', slow: '慢速型' },
  studyMotivation: { intrinsic: '内在驱动', extrinsic: '外部驱动', hybrid: '混合驱动' },
  focusLevel: { high: '优秀', medium: '一般', low: '有待提升' },
}

function updateDimensions(profile) {
  if (!profile) return
  for (const dim of dimensions.value) {
    const v = profile[dim.key]
    if (v === null || v === undefined) continue
    if (dim.key === 'knowledgeLevel') {
      dim.score = Number(v)
      dim.value = v >= 80 ? '扎实' : v >= 60 ? '中等' : '基础'
    } else {
      const map = labelMaps[dim.key]
      dim.value = map ? map[v] || v : v
    }
  }
}

async function loadProfile() {
  try {
    const res = await api.get('/student/by-sysuser/' + store.id)
    if (res.data) {
      updateDimensions(res.data)
      if (res.data.profileUpdatedAt) {
        lastUpdate.value = new Date(res.data.profileUpdatedAt).toLocaleString('zh-CN')
      }
    }
  } catch {}
}

async function triggerAnalysis() {
  analyzing.value = true
  try {
    const res = await api.post('/student/profile/analyze', { sysUserId: store.id })
    const data = res.data || {}
    if (data.profile) {
      updateDimensions(data.profile)
      lastUpdate.value = new Date().toLocaleString('zh-CN')
    }
    ElMessage.success(data.message || '画像已更新')
  } catch {
    ElMessage.error('分析失败，请稍后重试')
  } finally {
    analyzing.value = false
  }
}

onMounted(() => { loadProfile() })
</script>

<style scoped>
.profile-page { }
.page-header {
  display: flex; justify-content: space-between; align-items: flex-start;
  margin-bottom: 20px; flex-wrap: wrap; gap: 12px;
}
.page-header h2 { margin: 0 0 4px 0; font-size: 22px; }
.subtitle { color: #909399; font-size: 14px; margin: 0; }
.header-right { display: flex; align-items: center; gap: 12px; }
.update-time { color: #909399; font-size: 12px; }

.card-header { display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 15px; }

.profile-card { }
.profile-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  padding: 20px;
}
.dim-card {
  background: #f5f7fa; border-radius: 12px;
  padding: 20px 16px; text-align: center;
  border: 2px solid transparent;
  transition: all 0.3s;
}
.dim-card.active { background: #e8f4ff; border-color: #409EFF; }
.dim-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
.dim-top {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  margin-bottom: 10px;
}
.dim-icon { font-size: 28px; }
.dim-name { font-size: 14px; color: #909399; font-weight: 500; }
.dim-value { margin-bottom: 4px; }
.dim-score { display: block; font-size: 32px; color: #409EFF; font-weight: 700; margin-bottom: 4px; }
.dim-text { font-size: 15px; font-weight: 600; color: #303133; }
.dim-empty {
  font-size: 14px; color: #c0c4cc;
  display: flex; align-items: center; justify-content: center; gap: 4px;
}
.dim-desc { font-size: 12px; color: #c0c4cc; margin-top: 6px; display: none; }

@media (max-width: 1200px) {
  .profile-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 600px) {
  .profile-grid { grid-template-columns: 1fr; }
}
</style>
