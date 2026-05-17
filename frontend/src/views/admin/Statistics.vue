<template>
  <div class="stats-page">
    <h3>数据统计</h3>

    <!-- 系统概览 -->
    <h4 style="margin: 16px 0">系统整体统计</h4>
    <div class="overview-grid">
      <el-card v-for="s in overviewCards" :key="s.label">
        <div class="o-val">{{ s.value }}</div>
        <div class="o-label">{{ s.label }}</div>
      </el-card>
    </div>

    <!-- 功能使用率 -->
    <h4 style="margin: 24px 0 12px">功能使用率</h4>
    <el-table :data="featureData" stripe v-if="featureData.length">
      <el-table-column prop="feature" label="功能模块" />
      <el-table-column prop="count" label="使用次数" />
    </el-table>
    <el-empty v-else description="暂无功能使用数据" />

    <!-- 今日统计 -->
    <h4 style="margin: 24px 0 12px">今日统计</h4>
    <div class="today-grid">
      <el-card><div class="t-val">{{ todayStats.todayApiCalls || 0 }}</div><div class="t-label">今日API调用</div></el-card>
      <el-card><div class="t-val" :style="{ color: (todayStats.todayErrors || 0) > 0 ? '#F56C6C' : '#67C23A' }">{{ todayStats.todayErrors || 0 }}</div><div class="t-label">今日错误数</div></el-card>
      <el-card><div class="t-val" :style="{ color: anomalyStatus === 'warning' ? '#F56C6C' : '#67C23A' }">{{ anomalies.errorRate24h || '0%' }}</div><div class="t-label">24h错误率</div></el-card>
    </div>

    <!-- 异常监控 -->
    <h4 style="margin: 24px 0 12px">异常监控</h4>
    <el-alert
      :title="anomalyStatus === 'warning' ? '⚠ 异常警告：24小时内错误率超过10%' : '✅ 系统运行正常'"
      :type="anomalyStatus === 'warning' ? 'warning' : 'success'"
      show-icon
      :closable="false"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '../../api'

const overviewCards = ref([])
const featureData = ref([])
const todayStats = ref({})
const anomalies = ref({})
const anomalyStatus = ref('normal')

onMounted(async () => {
  try {
    const s = await adminApi.getStatistics()
    const d = s.data
    overviewCards.value = [
      { label: '总用户数', value: d.totalUsers || 0 },
      { label: '学生数', value: d.students || 0 },
      { label: '教师数', value: d.teachers || 0 },
      { label: '资源总数', value: d.totalResources || 0 },
      { label: '关卡提交', value: d.totalProgress || 0 },
      { label: '总学习时长(分)', value: d.totalStudyMinutes || 0 },
    ]
  } catch(e) {}

  try {
    const f = await adminApi.getFeatureUsage()
    const map = f.data || {}
    featureData.value = Object.entries(map).map(([feature, count]) => ({ feature, count }))
  } catch(e) {}

  try {
    todayStats.value = (await adminApi.getTodayStats()).data || {}
  } catch(e) {}

  try {
    const a = (await adminApi.getAnomalies()).data || {}
    anomalies.value = a
    anomalyStatus.value = a.status || 'normal'
  } catch(e) {}
})
</script>

<style scoped>
.stats-page { padding: 8px; }
h3 { margin: 0 0 8px; }
.overview-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; text-align: center; }
.o-val { font-size: 24px; font-weight: 700; color: #409EFF; }
.o-label { font-size: 13px; color: #909399; margin-top: 4px; }
.today-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; text-align: center; }
.t-val { font-size: 24px; font-weight: 700; }
.t-label { font-size: 13px; color: #909399; margin-top: 4px; }
</style>
