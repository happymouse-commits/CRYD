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

    <!-- 左右布局：学生信息卡 + 雷达图 -->
    <div class="profile-body">
      <!-- 左侧：学生信息卡 -->
      <div class="student-card">
        <div class="sc-avatar">👩‍🎓</div>
        <div class="sc-name">{{ profileName }}</div>
        <div class="sc-meta">{{ profileMajor }} · {{ profileGrade }}</div>
        <div class="sc-level">
          <span class="level-icon">{{ levelIcon }}</span>
          <span>{{ profileLevel }}</span>
        </div>

        <!-- 成长轨迹 R1→R2→R3 -->
        <div class="growth-track" v-if="growthStages.length">
          <template v-for="(s, i) in growthStages" :key="i">
            <div class="gt-round" :class="{ active: s.active }">
              <span class="gt-num">R{{ i + 1 }}</span>
              <span class="gt-desc">{{ s.label }}</span>
            </div>
            <span v-if="i < growthStages.length - 1" class="gt-arrow">→</span>
          </template>
        </div>

        <!-- AI 学情摘要 -->
        <div class="sc-summary">
          <span class="summary-label">🤖 AI 学情</span>
          <p>{{ aiSummary }}</p>
        </div>
      </div>

      <!-- 右侧：雷达图 -->
      <div class="radar-panel">
        <div ref="radarChartRef" class="radar-chart"></div>
        <div class="radar-legend">
          <span class="legend-item" v-for="dim in dimensions" :key="dim.key">
            <span class="legend-dot" :style="{ background: dim.color }"></span>
            {{ dim.name }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import api from '../../api'
import * as echarts from 'echarts'
import { Refresh } from '@element-plus/icons-vue'

const store = useUserStore()
const analyzing = ref(false)
const lastUpdate = ref('')
const radarChartRef = ref(null)
let radarChart = null

// ── 维度定义（保留后端原有的 8 维，接口不变） ──
const dimensions = ref([
  { key: 'grammar',       name: '语法掌握力', icon: '📝', value: null, score: null, color: '#b15311' },
  { key: 'logic',         name: '逻辑推理',   icon: '🧩', value: null, score: null, color: '#4a7c4e' },
  { key: 'coding',        name: '代码实践',   icon: '💻', value: null, score: null, color: '#c97930' },
  { key: 'debug',         name: '调试能力',   icon: '🔍', value: null, score: null, color: '#a14a3d' },
  { key: 'abstract',      name: '抽象思维',   icon: '💡', value: null, score: null, color: '#8b5a3c' },
  { key: 'selfLearn',     name: '自主学习',   icon: '📖', value: null, score: null, color: '#a14a3d' },
])

const completedDims = computed(() => dimensions.value.filter(d => d.score !== null && d.score > 0).length)

// ── 学生信息（从 store / profile 派生） ──
const profileData = ref(null)
const profileName = computed(() => profileData.value?.name || store.name || '同学')
const profileMajor = computed(() => profileData.value?.major || '计算机科学与技术')
const profileGrade = computed(() => profileData.value?.grade || '大一')

const levelMap = [
  { icon: '⚡', label: '初级探索者', min: 0 },
  { icon: '🔥', label: '进阶挑战者', min: 30 },
  { icon: '👑', label: '高手程序员', min: 60 },
]
const profileLevel = computed(() => {
  const avg = completedDims.value > 0
    ? dimensions.value.reduce((s, d) => s + (d.score || 0), 0) / dimensions.value.length
    : 0
  const match = [...levelMap].reverse().find(l => avg >= l.min)
  return match ? match.label : levelMap[0].label
})
const levelIcon = computed(() => {
  const avg = completedDims.value > 0
    ? dimensions.value.reduce((s, d) => s + (d.score || 0), 0) / dimensions.value.length
    : 0
  const match = [...levelMap].reverse().find(l => avg >= l.min)
  return match ? match.icon : levelMap[0].icon
})

// ── 成长轨迹（R1→R2→R3） ──
const growthStages = computed(() => {
  const scores = dimensions.value.map(d => d.score || 0)
  const avg = scores.length ? Math.round(scores.reduce((a, b) => a + b, 0) / scores.length) : 0
  if (avg === 0) return []
  return [
    { active: avg < 35,  label: `入门 · ${Math.max(0, avg - 15)}分` },
    { active: avg >= 35 && avg < 60, label: `进阶 · ${avg}分` },
    { active: avg >= 60, label: `掌握 · ${Math.min(100, avg + 10)}分` },
  ]
})

// ── AI 摘要（优先用后端返回，否则兜底） ──
const aiSummary = computed(() => {
  if (profileData.value?.aiSummary) return profileData.value.aiSummary
  if (completedDims.value === 0) return '完成诊断对话后，AI 将自动生成学情摘要，帮你精准定位学习方向。'
  const dims = dimensions.value
  const sorted = [...dims].sort((a, b) => (b.score || 0) - (a.score || 0))
  const top = sorted[0]
  const low = sorted[sorted.length - 1]
  return `${top?.name || '综合能力'}表现最好，${low?.name || '薄弱环节'}是重点提升方向。建议从薄弱项入手，每天保持30分钟练习。`
})

// ── 匹配后端返回字段 → 维度 ──
const fieldToDimKey = {
  knowledgeLevel: 'grammar',
  cognitiveStyle: 'logic',
  learningPreference: 'coding',
  learningPace: 'debug',
  interestDirection: 'abstract',
  weakAreas: 'selfLearn',
  studyMotivation: 'grammar',
  focusLevel: 'coding',
}

const labelScoreMap = {
  cognitiveStyle: { visual: 75, auditory: 65, kinesthetic: 55, reading: 70 },
  learningPreference: { video: 70, exercise: 75, doc: 60, mixed: 65 },
  learningPace: { fast: 80, steady: 65, slow: 40 },
  focusLevel: { high: 85, medium: 55, low: 30 },
  studyMotivation: { intrinsic: 85, hybrid: 65, extrinsic: 50 },
}

function updateDimensions(profile) {
  if (!profile) return
  profileData.value = profile

  // 重置所有维度
  for (const dim of dimensions.value) {
    dim.value = null
    dim.score = null
  }

  // 从后端字段映射到新维度
  for (const [fieldKey, dimKey] of Object.entries(fieldToDimKey)) {
    const raw = profile[fieldKey]
    if (raw === null || raw === undefined) continue

    const dim = dimensions.value.find(d => d.key === dimKey)
    if (!dim) continue

    let score = 0
    if (fieldKey === 'knowledgeLevel') {
      score = Number(raw) || 0
      dim.value = score >= 80 ? '扎实' : score >= 60 ? '中等' : '基础'
    } else if (labelScoreMap[fieldKey]) {
      score = labelScoreMap[fieldKey][raw] || 0
      dim.value = raw
    } else if (fieldKey === 'interestDirection' || fieldKey === 'weakAreas') {
      score = raw ? 50 : 20
      dim.value = raw || '待识别'
    }
    dim.score = Math.max(dim.score || 0, score)
  }

  // 兜底：无数据的维度给默认低分
  for (const dim of dimensions.value) {
    if (dim.score === null) dim.score = 15
  }
}

// ── ECharts 雷达图 ──
function buildRadarOption() {
  const scores = dimensions.value.map(d => d.score ?? 0)
  const names = dimensions.value.map(d => d.name)
  const maxVal = Math.max(...scores, 80)

  return {
    radar: {
      center: ['50%', '52%'],
      radius: '65%',
      axisName: {
        color: '#6a6054',
        fontSize: 12,
        fontWeight: 600,
        formatter: (name) => name.length > 4 ? name.slice(0, 4) + '\n' + name.slice(4) : name,
      },
      indicator: names.map((name) => ({ name, max: maxVal })),
      shape: 'circle',
      splitNumber: 5,
      axisNameGap: 15,
      splitArea: {
        areaStyle: { color: ['#f4efe7', '#e4dfd8', '#f4efe7', '#e4dfd8', '#f4efe7'] },
      },
      axisLine: { lineStyle: { color: '#dad2c7' } },
      splitLine: { lineStyle: { color: '#e4e7ed' } },
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: scores,
            name: '学习画像',
            areaStyle: {
              color: {
                type: 'radial',
                x: 0.5, y: 0.5, r: 0.5,
                colorStops: [
                  { offset: 0, color: 'rgba(177,83,17,0.3)' },
                  { offset: 1, color: 'rgba(177,83,17,0.06)' },
                ],
              },
            },
            lineStyle: { color: '#b15311', width: 2 },
            itemStyle: { color: '#b15311', borderColor: '#b15311', borderWidth: 2 },
            symbol: 'circle',
            symbolSize: 6,
            label: { show: true, fontSize: 10, color: '#b15311', fontWeight: 600 },
          },
        ],
        emphasis: {
          lineStyle: { width: 3 },
          areaStyle: { color: 'rgba(177,83,17,0.4)' },
        },
      },
    ],
  }
}

function initRadarChart() {
  if (!radarChartRef.value) return
  if (radarChart) radarChart.dispose()
  radarChart = echarts.init(radarChartRef.value)
  radarChart.setOption(buildRadarOption())
  window.addEventListener('resize', () => radarChart?.resize())
}

// ── API ──
async function loadProfile() {
  try {
    const res = await api.get('/student/by-sysuser/' + store.id)
    if (res.data) {
      updateDimensions(res.data)
      if (res.data.profileUpdatedAt) {
        lastUpdate.value = new Date(res.data.profileUpdatedAt).toLocaleString('zh-CN')
      }
      nextTick(() => {
        if (radarChart) {
          radarChart.setOption(buildRadarOption(), true)
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
.profile-page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}
.page-header h2 {
  margin: 0 0 4px 0;
  font-size: 20px;
  font-weight: 700;
  color: #342618;
}
.subtitle {
  color: #b6ada1;
  font-size: 13px;
  margin: 0;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.update-time {
  color: #b6ada1;
  font-size: 12px;
}

/* ── 左右布局 ── */
.profile-body {
  display: flex;
  gap: 24px;
}

/* ── 左侧：学生信息卡 ── */
.student-card {
  flex: 0 0 320px;
  width: 320px;
  background: #f4efe7;
  border-radius: 20px;
  border: 1px solid #dad2c7;
  box-shadow: 0 1px 8px rgba(0,0,0,0.04);
  padding: 28px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.sc-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #b15311, #8b5a3c);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
  margin-bottom: 14px;
}
.sc-name {
  font-size: 22px;
  font-weight: 700;
  color: #342618;
  margin-bottom: 4px;
}
.sc-meta {
  font-size: 13px;
  color: #b6ada1;
  margin-bottom: 12px;
}
.sc-level {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  color: #b15311;
  background: rgba(177,83,17,0.08);
  margin-bottom: 20px;
}
.level-icon {
  font-size: 16px;
}

/* 成长轨迹 */
.growth-track {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  width: 100%;
  justify-content: center;
}
.gt-round {
  text-align: center;
  padding: 8px 10px;
  border-radius: 12px;
  background: #e4dfd8;
  border: 1px solid #dad2c7;
  min-width: 58px;
}
.gt-round.active {
  background: rgba(177,83,17,0.08);
  border-color: #b15311;
}
.gt-num {
  display: block;
  font-size: 13px;
  font-weight: 700;
  color: #b15311;
}
.gt-desc {
  font-size: 10px;
  color: #b6ada1;
}
.gt-round.active .gt-desc {
  color: #b15311;
}
.gt-arrow {
  color: #dad2c7;
  font-size: 16px;
}

/* AI 摘要 */
.sc-summary {
  width: 100%;
  text-align: left;
  font-size: 13px;
  line-height: 1.7;
  color: #6a6054;
  background: #e4dfd8;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #dad2c7;
  position: relative;
}
.summary-label {
  position: absolute;
  top: -10px;
  left: 16px;
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 10px;
  background: #f4efe7;
  border: 1px solid #dad2c7;
  color: #b15311;
  font-weight: 600;
}
.sc-summary p {
  margin: 0;
}

/* ── 右侧：雷达图 ── */
.radar-panel {
  flex: 1;
  background: #f4efe7;
  border-radius: 20px;
  border: 1px solid #dad2c7;
  box-shadow: 0 1px 8px rgba(0,0,0,0.04);
  padding: 28px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.radar-chart {
  width: 400px;
  height: 400px;
}
.radar-legend {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px 14px;
  margin-top: 8px;
}
.legend-item {
  font-size: 11px;
  color: #6a6054;
  display: flex;
  align-items: center;
  gap: 5px;
}
.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

@media (max-width: 860px) {
  .profile-body {
    flex-direction: column;
  }
  .student-card {
    flex: none;
    width: 100%;
  }
  .radar-chart {
    width: 100%;
    max-width: 360px;
    height: 340px;
  }
}
</style>
