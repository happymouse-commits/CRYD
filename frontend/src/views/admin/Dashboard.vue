<template>
  <div class="dashboard">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="16" v-if="dash">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-blue">
          <div class="stat-icon">👤</div>
          <div class="stat-info">
            <div class="stat-value">{{ dash.users?.total || 0 }}</div>
            <div class="stat-label">总用户数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-green">
          <div class="stat-icon">🎓</div>
          <div class="stat-info">
            <div class="stat-value">{{ dash.users?.students || 0 }}</div>
            <div class="stat-label">学生数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-orange">
          <div class="stat-icon">📚</div>
          <div class="stat-info">
            <div class="stat-value">{{ dash.learning?.totalCourses || 0 }}</div>
            <div class="stat-label">课程数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-purple">
          <div class="stat-icon">🤖</div>
          <div class="stat-info">
            <div class="stat-value">6</div>
            <div class="stat-label">AI智能体</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="8">
        <el-card shadow="hover" header="用户分布">
          <div ref="pieChart" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="hover" header="学习活跃趋势（近7天）">
          <div ref="lineChart" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="16">
        <el-card shadow="hover" header="课程人数统计">
          <div ref="barChart" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" header="AI智能体">
          <div class="agent-list">
            <div v-for="a in agents" :key="a.name" class="agent-item">
              <span class="agent-emoji">{{ a.emoji }}</span>
              <span class="agent-name">{{ a.name }}</span>
              <el-tag size="small" type="success">在线</el-tag>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import api from '../../api'

const dash = ref(null)
const pieChart = ref(null)
const lineChart = ref(null)
const barChart = ref(null)

const agents = [
  { name: '画像分析师', emoji: '📊' },
  { name: '辅导老师', emoji: '👩‍🏫' },
  { name: '出题专家', emoji: '📝' },
  { name: '课程设计师', emoji: '🎨' },
  { name: '路径规划师', emoji: '🗺️' },
  { name: '知识库管理', emoji: '🗄️' }
]

let pieInstance = null
let lineInstance = null
let barInstance = null

function initPie(data) {
  if (!pieChart.value) return
  pieInstance = echarts.init(pieChart.value)
  pieInstance.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 12 } },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
      data: [
        { value: data?.students || 0, name: '学生', itemStyle: { color: '#1890ff' } },
        { value: data?.teachers || 0, name: '教师', itemStyle: { color: '#52c41a' } },
        { value: data?.counselors || 0, name: '辅导员', itemStyle: { color: '#fa8c16' } },
        { value: data?.admins || 0, name: '管理员', itemStyle: { color: '#722ed1' } }
      ]
    }]
  })
}

function initLine() {
  if (!lineChart.value) return
  lineInstance = echarts.init(lineChart.value)
  const days = []
  const now = new Date()
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now - i * 86400000)
    days.push(`${d.getMonth() + 1}/${d.getDate()}`)
  }
  // 模拟数据（后端暂无此接口）
  const chatData = [32, 45, 28, 56, 42, 61, 38]
  const loginData = [12, 18, 10, 22, 16, 25, 14]
  lineInstance.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['AI对话次数', '登录次数'], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: days, boundaryGap: false },
    yAxis: { type: 'value' },
    series: [
      { name: 'AI对话次数', type: 'line', smooth: true, data: chatData, areaStyle: { color: 'rgba(24,144,255,0.15)' }, lineStyle: { color: '#1890ff' }, itemStyle: { color: '#1890ff' } },
      { name: '登录次数', type: 'line', smooth: true, data: loginData, areaStyle: { color: 'rgba(82,196,26,0.15)' }, lineStyle: { color: '#52c41a' }, itemStyle: { color: '#52c41a' } }
    ]
  })
}

function initBar() {
  if (!barChart.value) return
  barInstance = echarts.init(barChart.value)
  // 模拟数据
  const courses = ['C语言程序设计', '数据结构', '计算机网络', '操作系统', '数据库原理']
  const counts = [128, 96, 74, 62, 55]
  barInstance.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: courses },
    series: [{
      type: 'bar',
      data: counts,
      barWidth: 20,
      itemStyle: {
        borderRadius: [0, 6, 6, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#1890ff' },
          { offset: 1, color: '#36cfc9' }
        ])
      }
    }]
  })
}

function handleResize() {
  pieInstance?.resize()
  lineInstance?.resize()
  barInstance?.resize()
}

onMounted(async () => {
  try {
    const res = await api.get('/admin/dashboard')
    dash.value = res.data || res
  } catch (e) {
    console.warn('Dashboard API failed, using empty data')
    dash.value = { users: { total: 0, students: 0, teachers: 0, counselors: 0, admins: 0 }, learning: { totalCourses: 0 } }
  }
  await nextTick()
  initPie(dash.value?.users)
  initLine()
  initBar()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  pieInstance?.dispose()
  lineInstance?.dispose()
  barInstance?.dispose()
})
</script>

<style scoped>
.dashboard { padding: 0; }

.stat-card {
  display: flex; align-items: center; padding: 20px;
  border-radius: 10px; overflow: hidden;
}
.stat-card :deep(.el-card__body) { display: flex; align-items: center; width: 100%; padding: 0; }
.stat-icon { font-size: 36px; margin-right: 16px; flex-shrink: 0; }
.stat-info { flex: 1; }
.stat-value { font-size: 28px; font-weight: 700; line-height: 1.2; }
.stat-label { font-size: 13px; color: #8c8c8c; margin-top: 4px; }

.stat-blue .stat-value { color: #1890ff; }
.stat-green .stat-value { color: #52c41a; }
.stat-orange .stat-value { color: #fa8c16; }
.stat-purple .stat-value { color: #722ed1; }

.agent-list { display: flex; flex-direction: column; gap: 12px; padding: 4px 0; }
.agent-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px; border-radius: 8px;
  background: #f6f8fa; transition: background 0.2s;
}
.agent-item:hover { background: #e6f7ff; }
.agent-emoji { font-size: 20px; }
.agent-name { flex: 1; font-size: 14px; color: #262626; }
</style>
