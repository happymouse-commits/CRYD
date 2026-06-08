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
        <div class="stat-icon orange">📝</div>
        <div class="stat-content">
          <div class="stat-value">{{ totalQuestions }}</div>
          <div class="stat-label">总题数</div>
        </div>
      </div>
    </div>

    <!-- 每日打卡 -->
    <div class="checkin-section">
      <h3>📅 每日打卡</h3>
      <div class="checkin-bar">
        <el-button type="success" :disabled="checkedIn" @click="handleCheckin">
          {{ checkedIn ? '今日已打卡 ✓' : '打卡签到' }}
        </el-button>
        <span class="streak-info">连续打卡 {{ streakDays }} 天</span>
      </div>
    </div>

    <!-- 知识体系树状图 -->
    <div class="tree-section">
      <div class="section-header">
        <h3>🌳 C语言知识体系</h3>
        <span class="tree-hint">点击节点查看知识点介绍</span>
      </div>
      <div class="tree-container">
        <div ref="treeChartRef" class="tree-chart"></div>
      </div>
      <!-- 节点详情弹窗 -->
      <el-dialog v-model="nodeDialogVisible" :title="selectedNode?.name" width="520px" top="8vh">
        <div class="node-detail" v-if="selectedNode">
          <div class="node-detail-header">
            <span class="node-detail-icon">{{ selectedNode.icon || '📖' }}</span>
            <div>
              <h3>{{ selectedNode.name }}</h3>
              <el-tag v-if="selectedNode.difficulty" :type="diffTagType(selectedNode.difficulty)" size="small">
                {{ selectedNode.difficulty }}
              </el-tag>
            </div>
          </div>
          <el-divider />
          <div class="node-detail-body">
            <div class="detail-section">
              <h4>📋 内容概要</h4>
              <p>{{ selectedNode.description }}</p>
            </div>
            <div class="detail-section" v-if="selectedNode.keywords">
              <h4>🔑 关键词</h4>
              <div class="keyword-tags">
                <el-tag v-for="kw in selectedNode.keywords" :key="kw" size="small" effect="plain" round>
                  {{ kw }}
                </el-tag>
              </div>
            </div>
            <div class="detail-section" v-if="selectedNode.estimatedHours">
              <h4>⏱️ 建议学习时长</h4>
              <p>{{ selectedNode.estimatedHours }}</p>
            </div>
            <div class="detail-section" v-if="selectedNode.prerequisites && selectedNode.prerequisites.length">
              <h4>📌 前置知识</h4>
              <p>{{ selectedNode.prerequisites.join('、') }}</p>
            </div>
          </div>
        </div>
      </el-dialog>
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

  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import { useUserStore } from '../../store/user'
import api from '../../api'

const store = useUserStore()
const activePath = ref(null)
const generating = ref(false)
const profile = ref({})
const checkedIn = ref(false)
const streakDays = ref(0)

const knowledgeLevel = computed(() => profile.value.knowledgeLevel || 0)

function stepDesc(i) { return i <= (activePath.value?.currentStep || 0) ? '已完成' : '待学习' }

function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(text)
}

async function loadData() {
  try {
    const r = await api.get('/learning-path/student/' + store.id + '/active')
    activePath.value = (r.data && r.data.status === 'active') ? r.data : null
  } catch {}
  try {
    const r = await api.get('/profile/' + store.id)
    profile.value = r.data || {}
  } catch {}
  // 加载今日打卡状态
  try {
    const r = await api.get('/learning-path/checkin/today/' + store.id)
    checkedIn.value = r.data?.checkedIn || false
  } catch {}
  // 加载连续打卡天数
  try {
    const r = await api.get('/learning-path/checkin/calendar/' + store.id, {
      params: { month: new Date().toISOString().slice(0, 7) }
    })
    streakDays.value = r.data?.streakDays || 0
  } catch {}
}

async function handleCheckin() {
  try {
    await api.post('/learning-path/checkin', { studentId: store.id })
    checkedIn.value = true
    // 重新获取连续打卡天数
    try {
      const r = await api.get('/learning-path/checkin/calendar/' + store.id, {
        params: { month: new Date().toISOString().slice(0, 7) }
      })
      streakDays.value = r.data?.streakDays || 0
    } catch {}
    ElMessage.success('打卡成功！')
  } catch {
    ElMessage.error('打卡失败')
  }
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

onMounted(() => { loadData() })
</script>

<style scoped>
.learning-path-page { }
.page-header { margin-bottom: 20px; }
.page-header h2 { margin: 0 0 4px 0; font-size: 22px; }
.subtitle { color: #909399; font-size: 14px; margin: 0; }

.stats-row { display: flex; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }

.checkin-section { margin-bottom: 20px; }
.checkin-section h3 { margin-bottom: 12px; font-size: 17px; }
.checkin-bar { display: flex; align-items: center; gap: 16px; }
.streak-info { font-size: 14px; font-weight: 600; color: #67C23A; }

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
.path-detail :deep(h2) { font-size: 19px; margin: 18px 0 8px; color: #303133; }
.path-detail :deep(h3) { font-size: 16px; margin: 14px 0 6px; color: #303133; }
.path-detail :deep(h4) { font-size: 14px; margin: 10px 0 4px; color: #303133; }
.path-detail :deep(p) { margin: 0 0 8px; }
.path-detail :deep(ul), .path-detail :deep(ol) { margin: 6px 0; padding-left: 20px; }
.path-detail :deep(li) { margin-bottom: 3px; }
.path-detail :deep(pre) { background: #e8e8f0; padding: 10px; border-radius: 6px; overflow-x: auto; margin: 8px 0; }
.path-detail :deep(code) { font-family: Consolas, monospace; font-size: 13px; }
.path-detail :deep(pre code) { background: none; padding: 0; }
.path-detail :deep(blockquote) { border-left: 3px solid #409EFF; padding: 4px 12px; margin: 8px 0; background: #ecf5ff; border-radius: 0 4px 4px 0; }
.path-detail :deep(table) { border-collapse: collapse; margin: 8px 0; }
.path-detail :deep(th), .path-detail :deep(td) { border: 1px solid #dcdfe6; padding: 6px 10px; }
.no-path-hint { padding: 20px 0; }

</style>