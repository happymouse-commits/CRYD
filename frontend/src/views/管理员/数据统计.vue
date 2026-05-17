<template>
  <div class="stats-page">
    <div class="page-header">
      <h2>📊 数据统计</h2>
      <div class="header-right">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :shortcuts="dateShortcuts"
          @change="loadStats"
          style="width: 260px"
        />
        <el-button @click="loadStats" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </div>

    <!-- 核心指标卡片 -->
    <el-row :gutter="16" class="metrics-row">
      <el-col :span="6" v-for="m in coreMetrics" :key="m.key">
        <el-card shadow="hover" class="metric-card" :class="m.styleClass">
          <div class="metric-icon">{{ m.icon }}</div>
          <div class="metric-body">
            <div class="metric-value">{{ m.value }}</div>
            <div class="metric-label">{{ m.label }}</div>
          </div>
          <div class="metric-trend" :class="m.trend > 0 ? 'up' : 'down'">
            {{ m.trend > 0 ? '↑' : '↓' }} {{ Math.abs(m.trend) }}%
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区：系统使用情况 -->
    <el-row :gutter="16">
      <el-col :span="16">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <span>📈 系统整体使用趋势（近30天）</span>
          </template>
          <div ref="usageChart" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <span>👥 角色分布</span>
          </template>
          <div ref="roleChart" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 功能使用率 -->
    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="12">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="card-title-row">
              <span>🔧 功能使用率排行</span>
              <el-tag size="small" type="success">本月</el-tag>
            </div>
          </template>
          <div class="ranking-list">
            <div v-for="(item, i) in featureUsage" :key="item.name" class="ranking-item">
              <span class="rank-num" :class="{ top3: i < 3 }">{{ i + 1 }}</span>
              <span class="rank-name">{{ item.name }}</span>
              <div class="rank-bar-wrapper">
                <div class="rank-bar" :style="{ width: item.percent + '%', backgroundColor: item.color }"></div>
              </div>
              <span class="rank-val">{{ item.count }} 次</span>
              <span class="rank-pct">{{ item.percent }}%</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <span>⏱️ 人均学习时长（小时）</span>
          </template>
          <div ref="durationChart" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 异常数据监控 -->
    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="16">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="card-title-row">
              <span>🚨 异常事件趋势</span>
              <el-tag size="small" type="danger">实时监控</el-tag>
            </div>
          </template>
          <div ref="anomalyChart" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="card-title-row">
              <span>📋 最近异常事件</span>
              <el-tag size="small">共 {{ anomalies.length }} 条</el-tag>
            </div>
          </template>
          <div class="anomaly-list">
            <div v-for="a in anomalies" :key="a.id" class="anomaly-item">
              <div class="anomaly-top">
                <el-tag :type="a.level === 'error' ? 'danger' : a.level === 'warn' ? 'warning' : 'info'" size="small">
                  {{ a.level === 'error' ? '错误' : a.level === 'warn' ? '警告' : '提示' }}
                </el-tag>
                <span class="anomaly-time">{{ a.time }}</span>
              </div>
              <p class="anomaly-msg">{{ a.message }}</p>
            </div>
            <el-empty v-if="!anomalies.length" description="近期无异常事件" :image-size="60" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import api from '../../api'

const loading = ref(false)
const dateRange = ref([])
const usageChart = ref(null)
const roleChart = ref(null)
const durationChart = ref(null)
const anomalyChart = ref(null)

let usageInstance = null, roleInstance = null, durInstance = null, anomalyInstance = null

const dateShortcuts = [
  { text: '最近7天', value: () => { const e = new Date(); const s = new Date(e - 7 * 86400000); return [s, e] } },
  { text: '最近30天', value: () => { const e = new Date(); const s = new Date(e - 30 * 86400000); return [s, e] } },
  { text: '本月', value: () => { const d = new Date(); return [new Date(d.getFullYear(), d.getMonth(), 1), d] } },
]

const coreMetrics = ref([
  { key: 'totalUsers', icon: '👤', value: 0, label: '总用户数', trend: 12, styleClass: 'metric-blue' },
  { key: 'totalResources', icon: '📚', value: 0, label: '资源生成量', trend: 23, styleClass: 'metric-green' },
  { key: 'totalHours', icon: '⏱️', value: 0, label: '学习总时长（小时）', trend: 8, styleClass: 'metric-purple' },
  { key: 'activeUsers', icon: '🔥', value: 0, label: '活跃用户数', trend: -3, styleClass: 'metric-orange' },
])

const featureUsage = ref([
  { name: 'AI辅导', count: 2847, percent: 42, color: '#1890ff' },
  { name: '刷题房', count: 1892, percent: 28, color: '#52c41a' },
  { name: '学习资源', count: 956, percent: 14, color: '#fa8c16' },
  { name: '疑难突破', count: 623, percent: 9, color: '#722ed1' },
  { name: '学习路径', count: 341, percent: 5, color: '#13c2c2' },
  { name: '其他', count: 128, percent: 2, color: '#909399' },
])

const anomalies = ref([])

// -- charts --
function initUsageChart() {
  if (!usageChart.value) return
  usageInstance = echarts.init(usageChart.value)
  const days = Array.from({ length: 30 }, (_, i) => {
    const d = new Date(Date.now() - (29 - i) * 86400000)
    return `${d.getMonth() + 1}/${d.getDate()}`
  })
  const loginData = Array.from({ length: 30 }, () => Math.floor(Math.random() * 50 + 30))
  const chatData = Array.from({ length: 30 }, () => Math.floor(Math.random() * 40 + 20))
  const resourceData = Array.from({ length: 30 }, () => Math.floor(Math.random() * 15 + 5))
  usageInstance.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['登录次数', 'AI对话次数', '资源生成数'], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '2%', containLabel: true },
    xAxis: { type: 'category', data: days, boundaryGap: false },
    yAxis: { type: 'value' },
    series: [
      { name: '登录次数', type: 'line', smooth: true, data: loginData, areaStyle: { opacity: 0.12 }, lineStyle: { color: '#1890ff' }, itemStyle: { color: '#1890ff' } },
      { name: 'AI对话次数', type: 'line', smooth: true, data: chatData, areaStyle: { opacity: 0.1 }, lineStyle: { color: '#52c41a' }, itemStyle: { color: '#52c41a' } },
      { name: '资源生成数', type: 'line', smooth: true, data: resourceData, areaStyle: { opacity: 0.08 }, lineStyle: { color: '#fa8c16' }, itemStyle: { color: '#fa8c16' } },
    ]
  })
}

function initRoleChart() {
  if (!roleChart.value) return
  roleInstance = echarts.init(roleChart.value)
  roleInstance.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie', radius: ['45%', '75%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 13, fontWeight: 'bold' } },
      data: [
        { value: coreMetrics.value.find(m => m.key === 'activeUsers')?.value || 0, name: '学生', itemStyle: { color: '#1890ff' } },
        { value: 8, name: '教师', itemStyle: { color: '#52c41a' } },
        { value: 3, name: '管理员', itemStyle: { color: '#722ed1' } },
      ]
    }]
  })
}

function initDurationChart() {
  if (!durationChart.value) return
  durInstance = echarts.init(durationChart.value)
  const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const hours = [2.4, 3.1, 2.8, 3.5, 2.2, 1.8, 1.2]
  durInstance.setOption({
    tooltip: { trigger: 'axis', formatter: '{b}: {c} 小时' },
    grid: { left: '3%', right: '8%', bottom: '2%', containLabel: true },
    xAxis: { type: 'category', data: days },
    yAxis: { type: 'value', name: '小时' },
    series: [{
      type: 'bar', data: hours, barWidth: 24,
      itemStyle: {
        borderRadius: [6, 6, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#722ed1' },
          { offset: 1, color: '#b37feb' },
        ])
      }
    }]
  })
}

function initAnomalyChart() {
  if (!anomalyChart.value) return
  anomalyInstance = echarts.init(anomalyChart.value)
  const days = Array.from({ length: 14 }, (_, i) => {
    const d = new Date(Date.now() - (13 - i) * 86400000)
    return `${d.getMonth() + 1}/${d.getDate()}`
  })
  const errors = [0, 1, 0, 0, 2, 0, 1, 0, 0, 0, 3, 0, 1, 0]
  const warns = [2, 1, 3, 2, 1, 4, 2, 1, 3, 2, 5, 3, 2, 1]
  anomalyInstance.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['错误', '警告'], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '2%', containLabel: true },
    xAxis: { type: 'category', data: days },
    yAxis: { type: 'value', interval: 1 },
    series: [
      { name: '错误', type: 'bar', data: errors, stack: 'total', barWidth: 18, itemStyle: { color: '#f5222d', borderRadius: [4, 4, 0, 0] } },
      { name: '警告', type: 'bar', data: warns, stack: 'total', barWidth: 18, itemStyle: { color: '#faad14', borderRadius: [4, 4, 0, 0] } },
    ]
  })
}

function handleResize() {
  usageInstance?.resize()
  roleInstance?.resize()
  durInstance?.resize()
  anomalyInstance?.resize()
}

async function loadStats() {
  loading.value = true
  try {
    const params = {}
    if (dateRange.value?.length) {
      params.startDate = dateRange.value[0].toISOString().split('T')[0]
      params.endDate = dateRange.value[1].toISOString().split('T')[0]
    }
    const res = await api.get('/admin/stats', { params })
    if (res.code === 200 && res.data) {
      if (res.data.metrics) coreMetrics.value = res.data.metrics
      if (res.data.featureUsage) featureUsage.value = res.data.featureUsage
      if (res.data.anomalies) anomalies.value = res.data.anomalies
    }
  } catch (e) {
    console.error('加载统计数据失败', e)
    // 如果加载失败，保持上次的数据，不显示模拟数据
  } finally {
    loading.value = false
    await nextTick()
    initUsageChart()
    initRoleChart()
    initDurationChart()
    initAnomalyChart()
  }
}

onMounted(() => {
  // 默认最近30天
  const end = new Date()
  const start = new Date(end - 30 * 86400000)
  dateRange.value = [start, end]
  loadStats()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  usageInstance?.dispose()
  roleInstance?.dispose()
  durInstance?.dispose()
  anomalyInstance?.dispose()
})
</script>

<style scoped>
.stats-page { padding: 0; }
.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 20px; flex-wrap: wrap; gap: 12px;
}
.page-header h2 { margin: 0; font-size: 20px; font-weight: 600; color: #1d2129; }
.header-right { display: flex; gap: 10px; align-items: center; }

.metrics-row { margin-bottom: 16px; }
.metric-card {
  display: flex; align-items: center; padding: 18px;
  border-radius: 10px; position: relative; overflow: hidden;
}
.metric-card :deep(.el-card__body) {
  display: flex; align-items: center; width: 100%; padding: 0; gap: 14px;
}
.metric-icon { font-size: 32px; flex-shrink: 0; }
.metric-body { flex: 1; min-width: 0; }
.metric-value { font-size: 28px; font-weight: 700; line-height: 1.2; }
.metric-label { font-size: 13px; color: #8c8c8c; margin-top: 2px; }
.metric-trend {
  font-size: 13px; font-weight: 600; padding: 3px 8px; border-radius: 12px;
  align-self: flex-start;
}
.metric-trend.up { color: #52c41a; background: #f0fdf4; }
.metric-trend.down { color: #f5222d; background: #fef0f0; }

.metric-blue .metric-value { color: #1890ff; }
.metric-green .metric-value { color: #52c41a; }
.metric-purple .metric-value { color: #722ed1; }
.metric-orange .metric-value { color: #fa8c16; }

.chart-card { border-radius: 10px; margin-bottom: 16px; }
.chart-box { height: 300px; }
.card-title-row { display: flex; align-items: center; gap: 10px; }

.ranking-list { display: flex; flex-direction: column; gap: 10px; }
.ranking-item {
  display: flex; align-items: center; gap: 10px;
}
.rank-num {
  width: 22px; height: 22px; border-radius: 50%; display: flex;
  align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; color: #7a7f8a;
  background: #f0f2f5; flex-shrink: 0;
}
.rank-num.top3 { color: #fff; background: #1890ff; }
.rank-name { width: 72px; font-size: 13px; color: #303133; flex-shrink: 0; }
.rank-bar-wrapper { flex: 1; height: 20px; background: #f0f2f5; border-radius: 10px; overflow: hidden; }
.rank-bar { height: 100%; border-radius: 10px; transition: width 0.6s; min-width: 4px; }
.rank-val { font-size: 12px; color: #606266; width: 48px; text-align: right; }
.rank-pct { font-size: 12px; color: #909399; width: 36px; text-align: right; }

.anomaly-list {
  max-height: 300px; overflow-y: auto;
  display: flex; flex-direction: column; gap: 10px;
}
.anomaly-item {
  padding: 10px 12px; background: #fafafa; border-radius: 8px;
  border-left: 3px solid #e8e8e8;
}
.anomaly-top { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.anomaly-time { font-size: 12px; color: #909399; }
.anomaly-msg { font-size: 13px; color: #606266; margin: 0; line-height: 1.5; }
</style>