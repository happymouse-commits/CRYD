<template>
  <div class="evaluation-page">
    <!-- 指标卡片 -->
    <div class="stats-row">
      <div class="stat-card"><div class="stat-icon blue">📝</div><div class="stat-content"><div class="stat-value">{{ stats.totalQuestions }}</div><div class="stat-label">做题总数</div></div></div>
      <div class="stat-card"><div class="stat-icon green">✅</div><div class="stat-content"><div class="stat-value">{{ stats.accuracy }}%</div><div class="stat-label">正确率</div></div></div>
      <div class="stat-card"><div class="stat-icon purple">📚</div><div class="stat-content"><div class="stat-value">{{ stats.studyHours }}h</div><div class="stat-label">学习时长</div></div></div>
      <div class="stat-card"><div class="stat-icon orange">🔥</div><div class="stat-content"><div class="stat-value">{{ stats.streakDays }}</div><div class="stat-label">连续天数</div></div></div>
    </div>

    <!-- 图表区域 -->
    <div class="charts-row">
      <div class="chart-panel">
        <h3 class="panel-title">正确率趋势</h3>
        <div ref="lineChartRef" class="chart-box"></div>
      </div>
    </div>

    <!-- 高频错题知识点 -->
    <div class="weakness-section" v-if="weakPoints.length">
      <h3 class="panel-title">⚠️ 高频错题知识点</h3>
      <div class="weakness-tags">
        <span v-for="wp in weakPoints" :key="wp.name"
              :class="['weakness-tag', wp.level === 'high' ? 'high' : wp.level === 'medium' ? 'medium' : 'low']">
          {{ wp.level === 'high' ? '🔴' : wp.level === 'medium' ? '🟡' : '🟢' }}
          {{ wp.name }} (错{{ wp.count }}次)
        </span>
      </div>
    </div>

    <!-- 近期答题记录 -->
    <div class="records-section">
      <h3 class="panel-title">📋 近期答题记录</h3>
      <el-table :data="recentRecords" stripe style="width:100%" max-height="400">
        <el-table-column prop="time" label="时间" width="160" />
        <el-table-column prop="chapter" label="章节" min-width="180" />
        <el-table-column prop="questionCount" label="题目数" width="80" align="center" />
        <el-table-column prop="score" label="得分" width="100" align="center">
          <template #default="{ row }">
            <span :class="row.score >= 60 ? 'score-pass' : 'score-fail'">{{ row.score }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="result" label="结果" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.score >= 80 ? 'success' : row.score >= 60 ? 'warning' : 'danger'" size="small">
              {{ row.score >= 80 ? '优秀' : row.score >= 60 ? '及格' : '需加强' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!recentRecords.length" description="暂无答题记录" :image-size="60" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useUserStore } from '../../store/user'
import api from '../../api'
import * as echarts from 'echarts'

const store = useUserStore()

const lineChartRef = ref(null)
const stats = reactive({ totalQuestions: 0, accuracy: 0, studyHours: 0, streakDays: 0 })
const weakPoints = ref([])
const recentRecords = ref([])

let lineChart = null

function formatDate(d) {
  if (!d) return ''
  const t = new Date(d)
  return t.getFullYear() + '-' + String(t.getMonth() + 1).padStart(2, '0') + '-' + String(t.getDate()).padStart(2, '0') + ' ' +
    String(t.getHours()).padStart(2, '0') + ':' + String(t.getMinutes()).padStart(2, '0')
}

async function loadData() {
  try {
    // 获取作业数据
    const assignRes = await api.get('/practice/assignments/' + store.id)
    const data = assignRes.data || {}
    const all = [...(data.pending || []), ...(data.completed || []), ...(data.graded || [])]

    let totalQ = 0, totalCorrect = 0, totalScore = 0, scoredCount = 0
    const chapterScores = []
    const records = []

    for (const item of all) {
      totalQ += (item.questionCount || 0)
      if (item.score !== null && item.score !== undefined) {
        totalScore += item.score
        scoredCount++
        const correctQ = Math.round(item.score / 100 * (item.questionCount || 5))
        totalCorrect += correctQ
      }
      if (item.submittedAt) {
        records.push({
          time: formatDate(item.submittedAt),
          chapter: item.name || '',
          questionCount: item.questionCount || 0,
          score: item.score || 0
        })
      }
      if (item.score !== null) {
        chapterScores.push({ name: item.name, score: item.score })
      }
    }

    stats.totalQuestions = totalQ
    stats.accuracy = scoredCount > 0 ? Math.round(totalScore / scoredCount) : 0
    stats.studyHours = Math.round((all.length * 15) / 60 * 10) / 10 || 0.5
    stats.streakDays = Math.min(records.length, 5)

    // 错题分析
    try {
      const errRes = await api.get('/practice/errors/' + store.id)
      const errData = errRes.data || {}
      const kpDist = errData.knowledgePointDistribution || {}
      const sorted = Object.entries(kpDist).sort((a, b) => b[1] - a[1])
      weakPoints.value = sorted.slice(0, 5).map(([name, count]) => ({
        name,
        count,
        level: count >= 3 ? 'high' : count >= 2 ? 'medium' : 'low'
      }))
    } catch {}

    // 近期记录
    records.sort((a, b) => b.time.localeCompare(a.time))
    recentRecords.value = records.slice(0, 10)

    // 渲染图表
    await nextTick()
    renderLine(records)
  } catch (e) { console.error('加载评估数据失败', e) }
}

function renderLine(records) {
  if (!lineChartRef.value) return
  if (lineChart) lineChart.dispose()

  const recent = records.slice().reverse().slice(-10)
  const dates = recent.map(r => r.time?.substring(5, 10) || '?')
  const scores = recent.map(r => r.score)

  lineChart = echarts.init(lineChartRef.value)
  lineChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: dates.length ? dates : ['暂无'], axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', min: 0, max: 100 },
    series: [{
      type: 'line', data: scores.length ? scores : [0], smooth: true,
      lineStyle: { color: '#409EFF', width: 2 },
      areaStyle: { color: 'rgba(64,158,255,0.1)' },
      itemStyle: { color: '#409EFF' }
    }]
  })
}

onMounted(() => { loadData() })
</script>

<style scoped>
.evaluation-page { }

.stats-row { display: flex; gap: 16px; margin-bottom: 20px; flex-wrap: wrap; }
.stat-card {
  flex: 1; min-width: 140px; background: #fff; border-radius: 12px;
  padding: 16px 20px; display: flex; align-items: center; gap: 14px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.stat-icon { font-size: 28px; }
.stat-value { font-size: 24px; font-weight: 700; color: #303133; }
.stat-label { font-size: 13px; color: #909399; }

.charts-row { display: flex; margin-bottom: 20px; }
.chart-panel { flex: 1; background: #fff; border-radius: 12px; padding: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.chart-box { width: 100%; height: 400px; }

.panel-title { font-size: 16px; color: #303133; margin: 0 0 14px 0; padding-left: 8px; border-left: 3px solid #409EFF; }

.weakness-section { margin-bottom: 20px; background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.weakness-tags { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 4px; }
.weakness-tag {
  padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 500;
}
.weakness-tag.high { background: #fef0f0; color: #F56C6C; border: 1px solid #fde2e2; }
.weakness-tag.medium { background: #fdf6ec; color: #E6A23C; border: 1px solid #faecd8; }
.weakness-tag.low { background: #f0f9eb; color: #67C23A; border: 1px solid #e1f3d8; }

.records-section { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.score-pass { color: #67C23A; font-weight: 700; }
.score-fail { color: #F56C6C; font-weight: 700; }
</style>