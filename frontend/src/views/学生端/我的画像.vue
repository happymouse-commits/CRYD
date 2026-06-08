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

      <!-- 雷达图 + 八维画像框 左右布局 -->
      <div class="profile-body">
        <!-- 左侧：雷达图 -->
        <div class="radar-panel">
          <div ref="radarChartRef" class="radar-chart"></div>
          <div class="radar-legend">
            <span class="legend-item" v-for="dim in dimensions" :key="dim.key">
              <span class="legend-dot" :style="{ background: dim.color }"></span>
              {{ dim.name }}
            </span>
          </div>
        </div>

        <!-- 右侧：八维画像框 -->
        <div class="dim-panel">
          <div class="profile-grid">
            <div v-for="dim in dimensions" :key="dim.key"
              class="dim-card" :class="{ active: dim.value !== null }"
              :style="{ borderLeftColor: dim.color }">
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
              <div class="dim-bar">
                <div class="dim-bar-fill" :style="{ width: (dim.score || 0) + '%', background: dim.color }"></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import api from '../../api'
import * as echarts from 'echarts'
import { TrendCharts, QuestionFilled, Refresh } from '@element-plus/icons-vue'

const store = useUserStore()
const analyzing = ref(false)
const lastUpdate = ref('')
const radarChartRef = ref(null)
let radarChart = null

const dimensions = ref([
  { key: 'knowledgeLevel',  name: '知识基础', icon: '📚', value: null, score: null, color: '#409EFF', desc: '已掌握知识的深度与广度' },
  { key: 'cognitiveStyle',  name: '认知风格', icon: '🧠', value: null, score: null, color: '#67C23A', desc: '获取与处理信息的方式偏好' },
  { key: 'learningPreference', name: '学习偏好', icon: '🎯', value: null, score: null, color: '#E6A23C', desc: '偏好的学习资源与活动类型' },
  { key: 'learningPace',    name: '学习节奏', icon: '⏱️', value: null, score: null, color: '#F56C6C', desc: '学习速度与进度倾向' },
  { key: 'interestDirection', name: '兴趣方向', icon: '⭐', value: null, score: null, color: '#909399', desc: '感兴趣的知识领域与方向' },
  { key: 'weakAreas',       name: '薄弱环节', icon: '⚠️', value: null, score: null, color: '#E6A23C', desc: '需要加强的知识薄弱点' },
  { key: 'studyMotivation', name: '学习动机', icon: '💪', value: null, score: null, color: '#409EFF', desc: '驱动学习的动力来源类型' },
  { key: 'focusLevel',      name: '专注力',   icon: '🔥', value: null, score: null, color: '#67C23A', desc: '学习过程中的注意力水平' },
])

const dimensionKeys = [
  'knowledgeLevel', 'cognitiveStyle', 'learningPreference', 'learningPace',
  'interestDirection', 'weakAreas', 'studyMotivation', 'focusLevel'
]

const completedDims = computed(() => dimensions.value.filter(d => d.value !== null).length)

const labelMaps = {
  cognitiveStyle: { visual: '视觉型', auditory: '听觉型', kinesthetic: '动觉型', reading: '阅读型' },
  learningPreference: { video: '视频偏好', doc: '文档偏好', exercise: '实践偏好', mixed: '混合型' },
  learningPace: { fast: '快速型', steady: '稳健型', slow: '慢速型' },
  studyMotivation: { intrinsic: '内在驱动', extrinsic: '外部驱动', hybrid: '混合驱动' },
  focusLevel: { high: '优秀', medium: '一般', low: '有待提升' },
}

// 将文字型维度映射为雷达图分数
function mapTextToScore(key, rawValue) {
  if (rawValue === null || rawValue === undefined) return 0
  const maps = {
    cognitiveStyle: { visual: 75, auditory: 65, kinesthetic: 55, reading: 70 },
    learningPreference: { video: 70, exercise: 75, doc: 60, mixed: 65 },
    learningPace: { fast: 80, steady: 65, slow: 40 },
    studyMotivation: { intrinsic: 85, hybrid: 65, extrinsic: 50 },
    focusLevel: { high: 85, medium: 55, low: 30 },
  }
  if (maps[key]) {
    return maps[key][rawValue] || 0
  }
  if (key === 'interestDirection') return rawValue ? 60 : 0
  if (key === 'weakAreas') return rawValue ? 50 : 0
  return 0
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
      dim.value = map ? (map[v] || v) : v
      // 计算雷达图分数
      if (dim.key === 'interestDirection' || dim.key === 'weakAreas') {
        dim.score = v ? (dim.key === 'interestDirection' ? 60 : 50) : 20
      } else {
        dim.score = mapTextToScore(dim.key, v)
      }
    }
  }
}

// ==================== 雷达图 ====================
function buildRadarOption() {
  const scores = dimensions.value.map(d => d.score ?? 0)
  const names = dimensions.value.map(d => d.name)
  const colors = dimensions.value.map(d => d.color)
  const maxVal = Math.max(...scores, 80)

  return {
    radar: {
      center: ['50%', '52%'],
      radius: '65%',
      axisName: {
        color: '#606266',
        fontSize: 11,
        borderRadius: 3,
        padding: [3, 5],
        formatter: (name) => {
          // 超过4个字就换行
          return name.length > 4 ? name.slice(0, 4) + '\n' + name.slice(4) : name
        }
      },
      indicator: names.map((name, i) => ({
        name,
        max: maxVal,
        color: colors[i]
      })),
      shape: 'circle',
      splitNumber: 5,
      axisNameGap: 15,
      splitArea: {
        areaStyle: {
          color: ['#fff', '#f5f7fa', '#fff', '#f5f7fa', '#fff']
        }
      },
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      splitLine: { lineStyle: { color: '#e4e7ed' } }
    },
    series: [{
      type: 'radar',
      data: [{
        value: scores,
        name: '学习画像',
        areaStyle: {
          color: {
            type: 'radial',
            x: 0.5, y: 0.5, r: 0.5,
            colorStops: [
              { offset: 0, color: 'rgba(64,158,255,0.35)' },
              { offset: 1, color: 'rgba(64,158,255,0.08)' }
            ]
          }
        },
        lineStyle: { color: '#409EFF', width: 2 },
        itemStyle: { color: '#409EFF', borderColor: '#409EFF', borderWidth: 2 },
        symbol: 'circle',
        symbolSize: 6,
        label: { show: true, fontSize: 10, color: '#409EFF', fontWeight: 600 }
      }],
      emphasis: {
        lineStyle: { width: 3 },
        areaStyle: { color: 'rgba(64,158,255,0.5)' }
      }
    }]
  }
}

function initRadarChart() {
  if (!radarChartRef.value) return
  if (radarChart) radarChart.dispose()

  radarChart = echarts.init(radarChartRef.value)
  radarChart.setOption(buildRadarOption())

  window.addEventListener('resize', () => radarChart?.resize())
}

async function loadProfile() {
  try {
    const res = await api.get('/student/by-sysuser/' + store.id)
    if (res.data) {
      updateDimensions(res.data)
      if (res.data.profileUpdatedAt) {
        lastUpdate.value = new Date(res.data.profileUpdatedAt).toLocaleString('zh-CN')
      }
      // 数据就绪后渲染雷达图
      nextTick(() => {
        if (radarChart) {
          radarChart.setOption(buildRadarOption())
        } else {
          setTimeout(() => initRadarChart(), 200)
        }
      })
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
      // 刷新雷达图
      nextTick(() => {
        if (radarChart) {
          radarChart.setOption(buildRadarOption(), true)
        }
      })
    }
    ElMessage.success(data.message || '画像已更新')
  } catch {
    ElMessage.error('分析失败，请稍后重试')
  } finally {
    analyzing.value = false
  }
}

onMounted(() => {
  loadProfile().then(() => {
    nextTick(() => {
      setTimeout(() => initRadarChart(), 300)
    })
  })
})
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

/* 左右布局 */
.profile-body {
  display: flex;
  gap: 0;
}
.radar-panel {
  flex: 0 0 400px;
  width: 400px;
  padding: 20px 10px 16px 10px;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.radar-chart {
  width: 380px;
  height: 380px;
}
.radar-legend {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px 14px;
  margin-top: 4px;
}
.legend-item {
  font-size: 12px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 4px;
}
.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.dim-panel {
  flex: 1;
  padding: 20px 20px 16px 20px;
  min-width: 0;
}
.profile-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.dim-card {
  background: #f5f7fa; border-radius: 12px;
  padding: 16px 14px; text-align: center;
  border: 1.5px solid transparent;
  border-left: 4px solid #dcdfe6;
  transition: all 0.3s;
  position: relative;
  overflow: hidden;
}
.dim-card.active { background: #f0f5ff; border-color: #d9ecff; }
.dim-card:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(0,0,0,0.1); }

.dim-top {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  margin-bottom: 8px;
}
.dim-icon { font-size: 26px; }
.dim-name { font-size: 13px; color: #606266; font-weight: 500; }

.dim-value { margin-bottom: 2px; }
.dim-score {
  display: block; font-size: 28px; color: #409EFF; font-weight: 700;
  margin-bottom: 2px;
}
.dim-card:nth-child(2) .dim-score { color: #67C23A; }
.dim-card:nth-child(3) .dim-score { color: #E6A23C; }
.dim-card:nth-child(4) .dim-score { color: #F56C6C; }
.dim-card:nth-child(5) .dim-score { color: #909399; }
.dim-card:nth-child(6) .dim-score { color: #E6A23C; }
.dim-card:nth-child(7) .dim-score { color: #409EFF; }
.dim-card:nth-child(8) .dim-score { color: #67C23A; }

.dim-text { font-size: 14px; font-weight: 600; color: #303133; }
.dim-empty {
  font-size: 13px; color: #c0c4cc;
  display: flex; align-items: center; justify-content: center; gap: 4px;
  margin-bottom: 2px;
}

/* 进度条 */
.dim-bar {
  margin-top: 10px;
  height: 4px;
  background: #e4e7ed;
  border-radius: 2px;
  overflow: hidden;
}
.dim-bar-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.6s ease;
}

@media (max-width: 1200px) {
  .profile-body { flex-direction: column; }
  .radar-panel {
    flex: none; width: 100%; border-right: none;
    border-bottom: 1px solid #ebeef5;
  }
  .radar-chart { width: 100%; max-width: 400px; height: 340px; }
  .profile-grid { grid-template-columns: repeat(4, 1fr); }
}
@media (max-width: 900px) {
  .profile-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 600px) {
  .profile-grid { grid-template-columns: 1fr; }
}
</style>
