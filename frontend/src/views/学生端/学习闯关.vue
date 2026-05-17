<template>
  <div class="learning-center">
    <!-- 顶部统计卡片 -->
    <div class="stats-row" v-if="summary">
      <div class="stat-card">
        <div class="stat-icon blue">📋</div>
        <div class="stat-content">
          <div class="stat-value">{{ summary.totalAssignments }}</div>
          <div class="stat-label">总作业</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon orange">📝</div>
        <div class="stat-content">
          <div class="stat-value">{{ summary.pendingCount }}</div>
          <div class="stat-label">待完成</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon green">✅</div>
        <div class="stat-content">
          <div class="stat-value">{{ summary.completedCount }}</div>
          <div class="stat-label">已完成</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon red">❌</div>
        <div class="stat-content">
          <div class="stat-value">{{ summary.errorCount }}</div>
          <div class="stat-label">待攻克错题</div>
        </div>
      </div>
    </div>

    <!-- 三栏布局 -->
    <div class="three-columns">
      <!-- 左侧边栏：Tab切换 + 筛选 -->
      <div class="left-panel">
        <div class="tab-buttons">
          <button :class="['tab-btn', { active: activeTab === 'assignments' }]"
                  @click="activeTab = 'assignments'">
            📋 我的作业
          </button>
          <button :class="['tab-btn', { active: activeTab === 'errors' }]"
                  @click="activeTab = 'errors'">
            ❌ 我的错题
            <el-tag v-if="errors.length" size="small" type="danger" class="error-count-tag">
              {{ errors.length }}
            </el-tag>
          </button>
        </div>

        <!-- 错题筛选 -->
        <div v-if="activeTab === 'errors'" class="filter-section">
          <div class="filter-title">筛选条件</div>
          <div class="filter-item">
            <label>知识点</label>
            <el-select v-model="filterKnowledgePoint" placeholder="全部" clearable size="small"
                       @change="loadErrors" style="width:100%">
              <el-option v-for="kp in knowledgePoints" :key="kp" :label="kp" :value="kp" />
            </el-select>
          </div>
          <div class="filter-item">
            <label>错误类型</label>
            <el-select v-model="filterErrorType" placeholder="全部" clearable size="small"
                       @change="loadErrors" style="width:100%">
              <el-option label="概念不清" value="concept" />
              <el-option label="计算错误" value="calculation" />
              <el-option label="审题偏差" value="misread" />
              <el-option label="逻辑错误" value="logic" />
            </el-select>
          </div>
          <div v-if="errorTypeDist" class="filter-stats">
            <div v-for="(count, type) in errorTypeDist" :key="type" class="stat-chip">
              {{ mapErrorType(type) }}: {{ count }}
            </div>
          </div>
        </div>
      </div>

      <!-- 中间主内容区 -->
      <div class="center-panel">
        <!-- 作业Tab -->
        <template v-if="activeTab === 'assignments'">
          <!-- 待完成 -->
          <div v-if="pendingList.length" class="section-block">
            <h3 class="section-title pending-title">🕐 待完成</h3>
            <div class="assignment-cards">
              <div v-for="item in pendingList" :key="item.id"
                   :class="['assignment-card', 'card-pending']"
                   @click="openAssignment(item)">
                <div class="card-badge">待完成</div>
                <h4 class="card-name">{{ item.name }}</h4>
                <p class="card-course">{{ item.courseName || '' }}</p>
                <p class="card-desc">{{ item.description || '' }}</p>
                <div class="card-meta">
                  <span>📝 {{ item.questionCount }}题</span>
                  <el-button type="primary" size="small">开始答题</el-button>
                </div>
              </div>
            </div>
          </div>

          <!-- 已完成 -->
          <div v-if="completedList.length" class="section-block">
            <h3 class="section-title completed-title">✅ 已完成</h3>
            <div class="assignment-cards">
              <div v-for="item in completedList" :key="item.id"
                   :class="['assignment-card', 'card-completed']"
                   @click="openAssignment(item)">
                <div class="card-badge done">已完成</div>
                <h4 class="card-name">{{ item.name }}</h4>
                <p class="card-course">{{ item.courseName || '' }}</p>
                <div class="card-meta">
                  <span>📝 {{ item.questionCount }}题</span>
                  <span class="score-tag">得分: <strong>{{ item.score }}</strong></span>
                  <el-button link type="primary" size="small">查看详情</el-button>
                </div>
              </div>
            </div>
          </div>

          <!-- 已批改 -->
          <div v-if="gradedList.length" class="section-block">
            <h3 class="section-title graded-title">📊 已批改</h3>
            <div class="assignment-cards">
              <div v-for="item in gradedList" :key="item.id"
                   :class="['assignment-card', 'card-graded']"
                   @click="openAssignment(item)">
                <div class="card-badge graded">已批改</div>
                <h4 class="card-name">{{ item.name }}</h4>
                <p class="card-course">{{ item.courseName || '' }}</p>
                <div class="card-meta">
                  <span>📝 {{ item.questionCount }}题</span>
                  <span class="score-tag">得分: <strong>{{ item.score }}</strong></span>
                  <span v-if="item.feedback" class="feedback-preview">
                    {{ truncate(item.feedback, 40) }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <el-empty v-if="!pendingList.length && !completedList.length && !gradedList.length"
                    description="暂无作业，等待老师布置" />
        </template>

        <!-- 错题Tab -->
        <template v-if="activeTab === 'errors'">
          <div v-if="errors.length" class="error-list-section">
            <div v-for="err in errors" :key="err.id"
                 :class="['error-detail-card', { selected: selectedError?.id === err.id }]"
                 @click="selectError(err)">
              <div class="error-card-header">
                <el-tag size="small" :type="err.errorType === 'logic' ? 'danger' :
                  err.errorType === 'calculation' ? 'warning' :
                  err.errorType === 'misread' ? 'info' : 'primary'">
                  {{ err.errorTag || mapErrorType(err.errorType) }}
                </el-tag>
                <el-tag size="small" type="success" v-if="err.knowledgePoint">
                  {{ err.knowledgePoint }}
                </el-tag>
                <el-tag size="small" type="warning" v-if="err.status === 'resolved'">已掌握</el-tag>
              </div>
              <p class="error-question">{{ err.question }}</p>
              <div class="error-answers">
                <span class="wrong-ans">你的答案: {{ err.studentAnswer }}</span>
                <span class="correct-ans">正确答案: {{ err.correctAnswer }}</span>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无错题，继续保持！" :image-size="80" />
        </template>
      </div>

      <!-- 右侧辅助区：突破资源 -->
      <div class="right-panel">
        <template v-if="selectedError">
          <div class="breakthrough-header">
            <h4>🔬 疑难突破</h4>
            <p class="selected-error-info">
              知识点: <strong>{{ selectedError.knowledgePoint || '未分类' }}</strong>
            </p>
          </div>

          <el-button type="primary" size="large" :loading="generating"
                     @click="oneClickBreakthrough" class="breakthrough-btn">
            ⚡ 一键突破
          </el-button>

          <div v-if="generatedResources.length" class="resources-list">
            <div v-for="res in generatedResources" :key="res.resourceType"
                 :class="['resource-card', { active: viewingResource?.resourceType === res.resourceType }]"
                 @click="viewingResource = res">
              <div class="resource-card-header">
                <span class="resource-icon">{{ resourceIcon(res.resourceType) }}</span>
                <span class="resource-name">{{ res.resourceName }}</span>
              </div>
              <p class="resource-preview">{{ truncate(res.content, 80) }}</p>
            </div>
          </div>

          <div v-if="viewingResource" class="resource-detail-popup">
            <div class="popup-header">
              <span>{{ viewingResource.resourceName }}</span>
              <el-button link @click="viewingResource = null">✕</el-button>
            </div>
            <div class="popup-body" v-html="renderMarkdown(viewingResource.content)"></div>
            <div class="popup-footer">
              <el-button size="small" @click="saveResource(viewingResource)">
                保存到资源中心
              </el-button>
            </div>
          </div>
        </template>
        <el-empty v-else description="选择错题后可生成突破资源" :image-size="80" />
      </div>
    </div>

    <!-- 答题弹窗 -->
    <el-dialog v-model="showAnswerDialog" :title="currentChapter?.name || '答题'"
               width="760px" :close-on-click-modal="false" destroy-on-close>
      <div class="question-list" v-if="dialogQuestions.length">
        <div v-for="(q, idx) in dialogQuestions" :key="idx" class="question-item">
          <div class="question-header">
            <el-tag size="small"
                    :type="q.type === 'choice' ? 'primary' : q.type === 'code' ? 'danger' : 'warning'">
              {{ q.type === 'choice' ? '选择题' : q.type === 'code' ? '代码题' : '填空题' }}
            </el-tag>
            <span class="question-num">第{{ idx + 1 }}题</span>
            <el-tag v-if="q.knowledgePoint" size="small" type="info" style="margin-left:auto">
              {{ q.knowledgePoint }}
            </el-tag>
          </div>
          <p class="question-text">{{ q.content }}</p>
          <el-radio-group v-if="q.type === 'choice'" v-model="dialogAnswers[idx + 1]" class="options-group">
            <el-radio v-for="opt in (q.options || [])" :key="opt.key" :value="opt.key" class="option-item">
              <span class="opt-key">{{ opt.key }}</span>. {{ opt.value }}
            </el-radio>
          </el-radio-group>
          <el-input v-else-if="q.type === 'fill'" v-model="dialogAnswers[idx + 1]" placeholder="请输入答案" />
          <div v-else-if="q.type === 'code'" class="code-question">
            <el-input v-model="dialogAnswers[idx + 1]" type="textarea"
                      placeholder="请在此编写C语言代码..."
                      :autosize="{ minRows: 8, maxRows: 20 }"
                      class="code-editor" spellcheck="false" />
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showAnswerDialog = false">取消</el-button>
        <el-button type="primary" @click="submitAnswers" :loading="submitting">提交答案</el-button>
      </template>
    </el-dialog>

    <!-- 结果弹窗 -->
    <el-dialog v-model="showResultDialog" title="答题结果" width="500px">
      <div class="result-content" v-if="lastResult">
        <div class="result-score">
          <el-progress type="circle" :percentage="lastResult.score" :width="120"
                       :color="lastResult.score >= 60 ? '#67C23A' : '#F56C6C'" />
        </div>
        <p class="result-text">正确 {{ lastResult.correctCount }}/{{ lastResult.totalCount }} 题</p>
        <div class="result-feedback" v-if="lastResult.feedback">
          <p v-for="(fb, i) in lastResult.feedback.split('。').filter(Boolean)" :key="i"
             :class="fb.includes('✓') ? 'correct-fb' : 'wrong-fb'">
            {{ fb }}
          </p>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="showResultDialog = false; refreshAll()">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import api from '../../api'

const store = useUserStore()

// 状态
const activeTab = ref('assignments')
const summary = ref(null)
const pendingList = ref([])
const completedList = ref([])
const gradedList = ref([])
const errors = ref([])
const errorTypeDist = ref({})
const knowledgePoints = ref([])
const selectedError = ref(null)
const filterKnowledgePoint = ref('')
const filterErrorType = ref('')
const generating = ref(false)
const generatedResources = ref([])
const viewingResource = ref(null)
const showAnswerDialog = ref(false)
const showResultDialog = ref(false)
const currentChapter = ref(null)
const dialogQuestions = ref([])
const dialogAnswers = reactive({})
const submitting = ref(false)
const lastResult = ref(null)

// 工具函数
function truncate(s, n) { return s && s.length > n ? s.slice(0, n) + '...' : s || '' }
function mapErrorType(type) {
  const map = { concept: '概念不清', calculation: '计算错误', misread: '审题偏差', logic: '逻辑错误' }
  return map[type] || type || '未知'
}
function resourceIcon(type) {
  const map = { explanation: '📖', mindmap: '🧠', exercise: '✏️', code: '💻' }
  return map[type] || '📄'
}

function renderMarkdown(text) {
  if (!text) return ''
  return text
    .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code class="$1">$2</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/^### (.+)$/gm, '<h4>$1</h4>')
    .replace(/^## (.+)$/gm, '<h3>$1</h3>')
    .replace(/^- (.+)$/gm, '<li>$1</li>')
    .replace(/\n/g, '<br>')
}

// 加载作业列表
async function loadAssignments() {
  try {
    const res = await api.get('/practice/assignments/' + store.id)
    const data = res.data || {}
    summary.value = data.summary || null
    pendingList.value = data.pending || []
    completedList.value = data.completed || []
    gradedList.value = data.graded || []
  } catch (e) {
    console.error('加载作业失败', e)
  }
}

// 加载错题列表
async function loadErrors() {
  try {
    const params = {}
    if (filterKnowledgePoint.value) params.knowledgePoint = filterKnowledgePoint.value
    if (filterErrorType.value) params.errorType = filterErrorType.value
    const res = await api.get('/practice/errors/' + store.id, { params })
    const data = res.data || {}
    errors.value = data.errors || []
    errorTypeDist.value = data.errorTypeDistribution || {}
    knowledgePoints.value = Object.keys(data.knowledgePointDistribution || {})
  } catch (e) {
    console.error('加载错题失败', e)
  }
}

// 打开答题弹窗
async function openAssignment(item) {
  currentChapter.value = item
  try {
    const res = await api.get('/practice/assignment/' + item.id + '/questions?studentId=' + store.id)
    const data = res.data || {}
    if (data.questions) {
      try { dialogQuestions.value = JSON.parse(data.questions) } catch { dialogQuestions.value = [] }
    }
    Object.keys(dialogAnswers).forEach(k => delete dialogAnswers[k])
    if (data.previousAnswers) {
      try { Object.assign(dialogAnswers, JSON.parse(data.previousAnswers)) } catch {}
    }
    showAnswerDialog.value = true
  } catch { ElMessage.error('获取题目失败') }
}

// 提交答案
async function submitAnswers() {
  submitting.value = true
  try {
    const res = await api.post('/practice/assignment/' + currentChapter.value.id + '/submit', {
      studentId: store.id,
      answers: JSON.stringify(dialogAnswers)
    })
    lastResult.value = res.data || {}
    showAnswerDialog.value = false
    showResultDialog.value = true
  } catch { ElMessage.error('提交失败') }
  finally { submitting.value = false }
}

// 选中错题
function selectError(err) {
  selectedError.value = err
  generatedResources.value = []
  viewingResource.value = null
}

// 一键突破
async function oneClickBreakthrough() {
  if (!selectedError.value) return
  generating.value = true
  generatedResources.value = []
  try {
    const res = await api.post('/practice/errors/' + selectedError.value.id + '/breakthrough')
    generatedResources.value = res.data?.resources || []
    ElMessage.success('突破资源已生成')
    if (generatedResources.value.length) {
      viewingResource.value = generatedResources.value[0]
    }
  } catch (e) {
    ElMessage.error('生成失败：' + (e.response?.data?.message || '未知错误'))
  } finally {
    generating.value = false
  }
}

// 保存资源到资源中心（已自动保存，此处为提示）
function saveResource(res) {
  ElMessage.success('资源已自动保存到资源中心')
}

// 刷新全部
async function refreshAll() {
  await loadAssignments()
  if (activeTab.value === 'errors') await loadErrors()
}

// 监听tab切换
watch(activeTab, (tab) => {
  if (tab === 'errors') loadErrors()
  if (tab === 'assignments') loadAssignments()
})

onMounted(() => { loadAssignments() })
</script>

<style scoped>
.learning-center { }

/* 统计卡片 */
.stats-row { display: flex; gap: 16px; margin-bottom: 20px; flex-wrap: wrap; }
.stat-card {
  flex: 1; min-width: 140px;
  background: #fff; border-radius: 12px; padding: 16px 20px;
  display: flex; align-items: center; gap: 14px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.stat-icon { font-size: 28px; }
.stat-content { }
.stat-value { font-size: 24px; font-weight: 700; color: #303133; }
.stat-label { font-size: 13px; color: #909399; }

/* 三栏布局 */
.three-columns {
  display: flex; gap: 20px; min-height: calc(100vh - 240px);
}
.left-panel {
  width: 240px; flex-shrink: 0;
  background: #fff; border-radius: 12px; padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  height: fit-content;
  position: sticky; top: 20px;
}
.center-panel { flex: 1; min-width: 0; }
.right-panel {
  width: 320px; flex-shrink: 0;
  background: #fff; border-radius: 12px; padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  height: fit-content;
  position: sticky; top: 20px;
}

/* Tab按钮 */
.tab-buttons { display: flex; flex-direction: column; gap: 6px; margin-bottom: 16px; }
.tab-btn {
  display: flex; align-items: center; justify-content: space-between;
  width: 100%; padding: 10px 14px; border: 2px solid #e8e8f0;
  border-radius: 8px; background: #fff; cursor: pointer;
  font-size: 14px; font-weight: 500; color: #606266;
  transition: all 0.2s;
}
.tab-btn:hover { border-color: #409EFF; color: #409EFF; }
.tab-btn.active { border-color: #409EFF; background: #e8f4ff; color: #409EFF; }
.error-count-tag { margin-left: auto; }

/* 筛选区 */
.filter-section { }
.filter-title { font-size: 13px; color: #909399; margin-bottom: 10px; font-weight: 600; }
.filter-item { margin-bottom: 10px; }
.filter-item label { font-size: 12px; color: #909399; display: block; margin-bottom: 4px; }
.filter-stats { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 8px; }
.stat-chip { font-size: 11px; padding: 2px 8px; background: #f0f2f5; border-radius: 10px; color: #606266; }

/* 作业卡片 */
.section-block { margin-bottom: 24px; }
.section-title { font-size: 16px; margin: 0 0 12px 0; padding-left: 8px; border-left: 3px solid #409EFF; }
.pending-title { border-color: #E6A23C; }
.completed-title { border-color: #67C23A; }
.graded-title { border-color: #409EFF; }
.assignment-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 14px; }
.assignment-card {
  position: relative;
  background: #fff; border-radius: 12px; padding: 20px;
  border: 2px solid #e8e8f0; cursor: pointer;
  transition: all 0.25s;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.assignment-card:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(0,0,0,0.1); }
.card-pending { border-color: #E6A23C; }
.card-pending:hover { border-color: #E6A23C; }
.card-completed { border-color: #67C23A; }
.card-graded { border-color: #409EFF; }
.card-badge {
  position: absolute; top: 10px; right: 12px;
  font-size: 11px; padding: 2px 10px; border-radius: 10px;
  background: #fdf6ec; color: #E6A23C; font-weight: 600;
}
.card-badge.done { background: #f0f9eb; color: #67C23A; }
.card-badge.graded { background: #ecf5ff; color: #409EFF; }
.card-name { margin: 0 0 4px 0; font-size: 16px; color: #303133; }
.card-course { font-size: 13px; color: #909399; margin: 0 0 8px 0; }
.card-desc { font-size: 12px; color: #c0c4cc; margin: 0 0 12px 0; }
.card-meta { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #606266; flex-wrap: wrap; }
.score-tag { color: #409EFF; }
.score-tag strong { font-size: 16px; }
.feedback-preview { font-size: 12px; color: #909399; font-style: italic; }

/* 错题列表 */
.error-list-section { display: flex; flex-direction: column; gap: 10px; }
.error-detail-card {
  padding: 14px; border: 1px solid #f0f0f0; border-radius: 10px;
  cursor: pointer; transition: all 0.2s;
}
.error-detail-card:hover { background: #f8f9ff; border-color: #c0c4ff; }
.error-detail-card.selected { background: #e8f4ff; border-color: #409EFF; }
.error-card-header { display: flex; gap: 6px; margin-bottom: 8px; flex-wrap: wrap; }
.error-question { font-size: 14px; color: #303133; margin: 0 0 8px 0; line-height: 1.5; }
.error-answers { display: flex; gap: 16px; font-size: 13px; }
.wrong-ans { color: #F56C6C; }
.correct-ans { color: #67C23A; font-weight: 500; }

/* 突破区 */
.breakthrough-header { margin-bottom: 12px; }
.breakthrough-header h4 { margin: 0 0 4px 0; font-size: 16px; }
.selected-error-info { font-size: 13px; color: #909399; margin: 0; }
.breakthrough-btn { width: 100%; margin-bottom: 16px; }
.resources-list { display: flex; flex-direction: column; gap: 8px; }
.resource-card {
  padding: 12px; border: 1px solid #f0f0f0; border-radius: 8px;
  cursor: pointer; transition: all 0.2s;
}
.resource-card:hover { border-color: #409EFF; }
.resource-card.active { border-color: #409EFF; background: #ecf5ff; }
.resource-card-header { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.resource-icon { font-size: 16px; }
.resource-name { font-size: 13px; font-weight: 600; color: #303133; }
.resource-preview { font-size: 12px; color: #909399; margin: 0; }

/* 资源详情弹窗 */
.resource-detail-popup {
  margin-top: 12px; border: 1px solid #e8e8f0; border-radius: 8px; overflow: hidden;
}
.popup-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 12px; background: #f8f9fa; font-weight: 600; font-size: 14px;
}
.popup-body {
  padding: 12px; max-height: 400px; overflow-y: auto; font-size: 14px; line-height: 1.7;
}
.popup-body :deep(pre) { background: #f4f4f5; padding: 10px; border-radius: 6px; overflow-x: auto; }
.popup-body :deep(code) { font-family: Consolas, monospace; font-size: 13px; }
.popup-body :deep(h3) { font-size: 15px; margin: 10px 0 6px; }
.popup-body :deep(h4) { font-size: 14px; margin: 8px 0 4px; }
.popup-footer { padding: 8px 12px; border-top: 1px solid #f0f0f0; text-align: right; }

/* 答题弹窗 */
.question-list { max-height: 500px; overflow-y: auto; }
.question-item { margin-bottom: 20px; padding-bottom: 16px; border-bottom: 1px solid #f0f0f0; }
.question-item:last-child { border-bottom: none; }
.question-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.question-num { font-weight: 600; color: #303133; }
.question-text { font-size: 15px; margin-bottom: 12px; line-height: 1.6; white-space: pre-wrap; }
.options-group { display: flex; flex-direction: column; gap: 8px; }
.option-item { display: flex; align-items: center; margin-bottom: 4px; }
.opt-key { font-weight: 600; color: #409EFF; margin-right: 2px; }
.code-question { margin-top: 8px; }
.code-editor :deep(textarea) {
  font-family: 'Consolas', 'Monaco', monospace !important;
  font-size: 14px !important; line-height: 1.6 !important; background: #f5f7fa !important;
}

/* 结果弹窗 */
.result-content { text-align: center; }
.result-score { display: flex; justify-content: center; margin-bottom: 16px; }
.result-text { font-size: 16px; color: #303133; margin-bottom: 16px; }
.result-feedback { text-align: left; max-height: 200px; overflow-y: auto; }
.result-feedback p { margin: 4px 0; font-size: 13px; line-height: 1.6; }
.correct-fb { color: #67C23A; }
.wrong-fb { color: #F56C6C; }

@media (max-width: 1100px) {
  .three-columns { flex-direction: column; }
  .left-panel { width: 100%; }
  .right-panel { width: 100%; }
}
</style>
