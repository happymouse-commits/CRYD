<template>
  <div class="practice-room">
    <!-- 统计卡片 -->
    <div class="stats-row" v-if="summary">
      <div class="stat-card"><div class="stat-icon blue">📋</div><div class="stat-content"><div class="stat-value">{{ summary.totalAssignments }}</div><div class="stat-label">总作业</div></div></div>
      <div class="stat-card"><div class="stat-icon orange">📝</div><div class="stat-content"><div class="stat-value">{{ summary.pendingCount }}</div><div class="stat-label">待完成</div></div></div>
      <div class="stat-card"><div class="stat-icon green">✅</div><div class="stat-content"><div class="stat-value">{{ summary.completedCount }}</div><div class="stat-label">已完成</div></div></div>
      <div class="stat-card"><div class="stat-icon red">❌</div><div class="stat-content"><div class="stat-value">{{ summary.errorCount }}</div><div class="stat-label">待攻克错题</div></div></div>
    </div>

    <!-- Tab栏 -->
    <div class="tab-bar">
      <button :class="['tab-btn', { active: activeTab === 'assignments' }]" @click="activeTab = 'assignments'">📝 教师作业</button>
      <button :class="['tab-btn', { active: activeTab === 'errors' }]" @click="activeTab = 'errors'; loadErrors()">❌ 错题回练</button>
      <button :class="['tab-btn', { active: activeTab === 'insights' }]" @click="activeTab = 'insights'; loadInsights()">🤖 AI错题解析</button>
    </div>

    <!-- Tab 1: 教师作业 -->
    <div v-if="activeTab === 'assignments'" class="tab-content">
      <div v-if="assignmentsByCourse.length" class="course-groups">
        <div v-for="group in assignmentsByCourse" :key="group.courseName" class="course-group">
          <h3 class="course-name">📘 {{ group.courseName }}</h3>
          <div class="chapter-cards">
            <div v-for="item in group.chapters" :key="item.id"
                 :class="['chapter-card', cardClass(item)]"
                 @click="openAssignment(item)">
              <div class="card-header">
                <span class="chapter-order">第{{ item.orderNum || '?' }}章</span>
                <span :class="['status-tag', item.status]">{{ statusLabel(item) }}</span>
              </div>
              <h4 class="chapter-name">{{ item.name }}</h4>
              <p class="chapter-desc" v-if="item.description">{{ truncate(item.description, 30) }}</p>
              <div class="card-footer">
                <span>📝 {{ item.questionCount || 0 }}题</span>
                <span v-if="item.score !== null && item.score !== undefined" class="score-text">
                  得分: <strong>{{ item.score }}</strong>
                </span>
                <el-button :type="item.status === 'completed' ? 'default' : 'primary'" size="small">
                  {{ item.status === 'completed' ? '查看详情' : item.status === 'pending' ? '开始答题' : '继续做题' }}
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无作业，等待老师布置" />
    </div>

    <!-- Tab 2: 错题回练 -->
    <div v-if="activeTab === 'errors'" class="tab-content">
      <div class="error-toolbar">
        <el-select v-model="errorFilter.course" placeholder="按课程筛选" clearable size="small" style="width:180px" @change="loadErrors">
          <el-option v-for="c in courses" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="errorFilter.chapter" placeholder="按章节筛选" clearable size="small" style="width:180px" @change="loadErrors">
          <el-option v-for="ch in chapterNames" :key="ch" :label="ch" :value="ch" />
        </el-select>
        <el-select v-model="errorFilter.minCount" placeholder="错题次数" clearable size="small" style="width:140px" @change="loadErrors">
          <el-option label="≥1次" :value="1" /><el-option label="≥2次" :value="2" /><el-option label="≥3次" :value="3" />
        </el-select>
        <el-button type="warning" size="small" @click="redoAllErrors" :disabled="!errors.length">全部重做</el-button>
      </div>
      <div v-if="errors.length" class="error-list">
        <div v-for="err in errors" :key="err.id" class="error-item">
          <div class="error-top">
            <el-tag size="small" :type="err.errorType === 'logic' ? 'danger' : err.errorType === 'calculation' ? 'warning' : err.errorType === 'misread' ? 'info' : 'primary'">{{ err.errorTag || mapErrorType(err.errorType) }}</el-tag>
            <el-tag size="small" type="success" v-if="err.knowledgePoint">{{ err.knowledgePoint }}</el-tag>
            <span class="error-count">错了 {{ err.wrongCount || 1 }} 次</span>
            <span class="error-date" v-if="err.createdAt">{{ formatDate(err.createdAt) }}</span>
          </div>
          <p class="error-question">{{ err.question }}</p>
          <div class="error-answers">
            <span class="wrong-ans">你的答案: {{ err.studentAnswer }}</span>
            <span class="correct-ans">正确答案: {{ err.correctAnswer }}</span>
          </div>
          <div class="error-analysis" v-if="err.analysis">{{ err.analysis }}</div>
          <div class="error-actions">
            <el-button size="small" type="primary" @click="redoError(err)">重新做题</el-button>
            <el-button size="small" @click="resolveError(err)">标记已掌握</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无错题，继续保持！" :image-size="80" />
    </div>

    <!-- Tab 3: AI错题解析 -->
    <div v-if="activeTab === 'insights'" class="tab-content">
      <el-alert type="info" :closable="false" style="margin-bottom:16px">
        AI根据你的错题自动生成学习资料。每次提交作业后立即生成，帮助针对性提升。
      </el-alert>
      <div v-if="insights.length" class="insights-list">
        <div v-for="item in insights" :key="item.id" class="insight-card" @click="viewInsight(item)">
          <div class="insight-header">
            <span class="insight-icon">💡</span>
            <div class="insight-info">
              <h4>{{ item.title || item.fileName || 'AI错题解析' }}</h4>
              <span class="insight-date" v-if="item.createdAt || item.uploadTime">
                {{ formatDate(item.createdAt || item.uploadTime) }}
              </span>
            </div>
            <el-tag size="small" type="warning">AI生成</el-tag>
          </div>
          <p class="insight-preview" v-if="item.originalContent || item.content">
            {{ truncate(item.originalContent || item.content, 120) }}
          </p>
        </div>
      </div>
      <div v-if="teacherResources.length" class="teacher-resources">
        <h3 class="section-title">📎 教师辅助资料</h3>
        <div v-for="item in teacherResources" :key="item.id" class="insight-card teacher" @click="viewInsight(item)">
          <div class="insight-header">
            <span class="insight-icon">📄</span>
            <div class="insight-info">
              <h4>{{ item.fileName || item.title || '教师资料' }}</h4>
              <span class="insight-date" v-if="item.createdAt || item.uploadTime">{{ formatDate(item.createdAt || item.uploadTime) }}</span>
            </div>
            <el-tag size="small" type="primary">教师上传</el-tag>
          </div>
        </div>
      </div>
      <el-empty v-if="!insights.length && !teacherResources.length" description="暂无AI解析资料，完成刷题后AI将自动生成" :image-size="80" />
    </div>

    <!-- 答题弹窗 -->
    <el-dialog v-model="showAnswerDialog" :title="currentChapter?.name || '答题'" width="760px" :close-on-click-modal="false" destroy-on-close>
      <div class="question-list" v-if="dialogQuestions.length">
        <div v-for="(q, idx) in dialogQuestions" :key="idx" class="question-item">
          <div class="question-header">
            <el-tag size="small" :type="q.type === 'choice' ? 'primary' : q.type === 'code' ? 'danger' : 'warning'">
              {{ q.type === 'choice' ? '选择题' : q.type === 'code' ? '代码题' : '填空题' }}
            </el-tag>
            <span class="question-num">第{{ idx + 1 }}题</span>
            <el-tag v-if="q.knowledgePoint" size="small" type="info" style="margin-left:auto">{{ q.knowledgePoint }}</el-tag>
          </div>
          <p class="question-text">{{ q.content }}</p>
          <el-radio-group v-if="q.type === 'choice'" v-model="dialogAnswers[idx + 1]" class="options-group">
            <el-radio v-for="opt in (q.options || [])" :key="opt.key" :value="opt.key" class="option-item">
              <span class="opt-key">{{ opt.key }}</span>. {{ opt.value }}
            </el-radio>
          </el-radio-group>
          <el-input v-else-if="q.type === 'fill'" v-model="dialogAnswers[idx + 1]" placeholder="请输入答案" />
          <div v-else-if="q.type === 'code'" class="code-question">
            <el-input v-model="dialogAnswers[idx + 1]" type="textarea" placeholder="请在此编写C语言代码..."
                      :autosize="{ minRows: 8, maxRows: 20 }" class="code-editor" spellcheck="false" />
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
          <el-progress type="circle" :percentage="lastResult.score" :width="120" :color="lastResult.score >= 60 ? '#67C23A' : '#F56C6C'" />
        </div>
        <p class="result-text">正确 {{ lastResult.correctCount }}/{{ lastResult.totalCount }} 题</p>
        <div class="result-feedback" v-if="lastResult.feedback">
          <p v-for="(fb, i) in lastResult.feedback.split('。').filter(Boolean)" :key="i" :class="fb.includes('✓') ? 'correct-fb' : 'wrong-fb'">{{ fb }}</p>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="showResultDialog = false; refreshAll()">知道了</el-button>
      </template>
    </el-dialog>

    <!-- 资料查看弹窗 -->
    <el-dialog v-model="showInsightDialog" :title="viewingInsight?.title || viewingInsight?.fileName || '资料详情'" width="700px">
      <div class="insight-detail" v-html="renderMarkdown(viewingInsight?.originalContent || viewingInsight?.content || '暂无内容')"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import api from '../../api'

const store = useUserStore()

const activeTab = ref('assignments')
const summary = ref(null)
const assignmentsByCourse = ref([])
const errors = ref([])
const insights = ref([])
const teacherResources = ref([])
const courses = ref([])
const chapterNames = ref([])
const errorFilter = reactive({ course: '', chapter: '', minCount: '' })
const showAnswerDialog = ref(false)
const showResultDialog = ref(false)
const showInsightDialog = ref(false)
const currentChapter = ref(null)
const dialogQuestions = ref([])
const dialogAnswers = reactive({})
const submitting = ref(false)
const lastResult = ref(null)
const viewingInsight = ref(null)

function truncate(s, n) { return s && s.length > n ? s.slice(0, n) + '...' : s || '' }
function mapErrorType(type) {
  const map = { concept: '概念不清', calculation: '计算错误', misread: '审题偏差', logic: '逻辑错误' }
  return map[type] || type || '未知'
}
function formatDate(d) {
  if (!d) return ''
  const t = new Date(d)
  return t.getFullYear() + '-' + String(t.getMonth() + 1).padStart(2, '0') + '-' + String(t.getDate()).padStart(2, '0')
}
function cardClass(item) {
  if (item.status === 'completed' && item.score !== null) return 'card-graded'
  if (item.status === 'completed') return 'card-completed'
  return 'card-pending'
}
function statusLabel(item) {
  if (item.status === 'completed' && item.score !== null) return '已批改'
  if (item.status === 'completed') return '已完成'
  return '待完成'
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

async function loadAssignments() {
  try {
    const res = await api.get('/practice/assignments/' + store.id)
    const data = res.data || {}
    summary.value = data.summary || null
    const all = [...(data.pending || []), ...(data.completed || []), ...(data.graded || [])]
    // 按课程分组
    const groups = {}
    for (const item of all) {
      const cn = item.courseName || '未分类'
      if (!groups[cn]) groups[cn] = []
      groups[cn].push(item)
    }
    assignmentsByCourse.value = Object.entries(groups).map(([courseName, chapters]) => ({ courseName, chapters }))
  } catch (e) { console.error('加载作业失败', e) }
}

async function loadErrors() {
  try {
    const params = {}
    if (errorFilter.course) params.course = errorFilter.course
    if (errorFilter.chapter) params.chapter = errorFilter.chapter
    if (errorFilter.minCount) params.minCount = errorFilter.minCount
    const res = await api.get('/practice/errors/' + store.id, { params })
    const data = res.data || {}
    errors.value = data.errors || []
    courses.value = Object.keys(data.knowledgePointDistribution || {}).length ? ['全部'] : []
    chapterNames.value = []
  } catch (e) { console.error('加载错题失败', e) }
}

async function loadInsights() {
  try {
    // 获取AI生成的知识库资料
    const assignmentsRes = await api.get('/practice/assignments/' + store.id)
    const all = [...(assignmentsRes.data?.pending || []), ...(assignmentsRes.data?.completed || []), ...(assignmentsRes.data?.graded || [])]
    // 从courseId获取知识库
    const seen = new Set()
    const allInsights = []
    for (const item of all) {
      if (item.courseId && !seen.has(item.courseId)) {
        seen.add(item.courseId)
        try {
          const kbRes = await api.get('/knowledge/course/' + item.courseId)
          if (kbRes.data && kbRes.data.documents) {
            for (const doc of kbRes.data.documents) {
              allInsights.push({ ...doc, courseName: item.courseName })
            }
          }
        } catch {}
      }
    }
    // 区分AI生成和教师上传
    insights.value = allInsights.filter(d => d.source !== 'teacher' && !d.fileName?.endsWith('.pdf'))
    teacherResources.value = allInsights.filter(d => d.source === 'teacher' || d.fileName?.endsWith('.pdf'))
  } catch (e) { console.error('加载AI解析失败', e) }
}

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

function redoError(err) {
  // 构造一个临时的chapter信息用于答题
  currentChapter.value = { id: err.chapterId, name: err.knowledgePoint || '错题重做' }
  dialogQuestions.value = [{
    type: 'choice', content: err.question,
    options: [], answer: err.correctAnswer, knowledgePoint: err.knowledgePoint
  }]
  Object.keys(dialogAnswers).forEach(k => delete dialogAnswers[k])
  dialogAnswers[1] = ''
  showAnswerDialog.value = true
}

async function resolveError(err) {
  try {
    await api.post('/practice/errors/' + err.id + '/resolve')
    err.status = 'resolved'
    ElMessage.success('已标记为掌握')
  } catch { ElMessage.error('操作失败') }
}

async function redoAllErrors() {
  ElMessage.info('请逐个点击错题的「重新做题」按钮进行练习')
}

function viewInsight(item) {
  viewingInsight.value = item
  showInsightDialog.value = true
}

async function refreshAll() {
  await loadAssignments()
}

onMounted(() => { loadAssignments() })
</script>

<style scoped>
.practice-room { }

.stats-row { display: flex; gap: 16px; margin-bottom: 20px; flex-wrap: wrap; }
.stat-card {
  flex: 1; min-width: 140px; background: #fff; border-radius: 12px;
  padding: 16px 20px; display: flex; align-items: center; gap: 14px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.stat-icon { font-size: 28px; }
.stat-value { font-size: 24px; font-weight: 700; color: #303133; }
.stat-label { font-size: 13px; color: #909399; }

/* Tab栏 */
.tab-bar { display: flex; gap: 0; margin-bottom: 20px; background: #fff; border-radius: 10px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.tab-btn {
  flex: 1; padding: 14px 0; border: none; background: #fff;
  font-size: 15px; font-weight: 600; color: #606266; cursor: pointer;
  transition: all 0.2s; border-bottom: 3px solid transparent;
}
.tab-btn:hover { color: #409EFF; background: #f8f9ff; }
.tab-btn.active { color: #409EFF; border-bottom-color: #409EFF; background: #ecf5ff; }

.tab-content { }

/* 课程分组 */
.course-group { margin-bottom: 24px; }
.course-name { font-size: 17px; color: #303133; margin: 0 0 14px 0; padding-left: 10px; border-left: 4px solid #409EFF; }
.chapter-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 14px; }
.chapter-card {
  background: #fff; border-radius: 12px; padding: 20px; border: 2px solid #e8e8f0;
  cursor: pointer; transition: all 0.25s; box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.chapter-card:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(0,0,0,0.1); }
.card-pending { border-color: #E6A23C; }
.card-completed { border-color: #67C23A; }
.card-graded { border-color: #409EFF; }
.card-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.chapter-order { font-size: 13px; color: #909399; font-weight: 600; }
.status-tag {
  font-size: 11px; padding: 2px 10px; border-radius: 10px; font-weight: 600;
}
.status-tag.pending { background: #fdf6ec; color: #E6A23C; }
.status-tag.completed { background: #f0f9eb; color: #67C23A; }
.chapter-name { margin: 0 0 4px 0; font-size: 16px; color: #303133; }
.chapter-desc { font-size: 12px; color: #c0c4cc; margin: 0 0 12px 0; }
.card-footer { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #606266; flex-wrap: wrap; }
.score-text { color: #409EFF; }
.score-text strong { font-size: 16px; }

/* 错题工具栏 */
.error-toolbar { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }

/* 错题列表 */
.error-list { display: flex; flex-direction: column; gap: 12px; }
.error-item {
  background: #fff; border-radius: 10px; padding: 16px;
  border: 1px solid #f0f0f0; box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}
.error-top { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.error-count { font-size: 12px; color: #F56C6C; font-weight: 600; }
.error-date { font-size: 12px; color: #909399; margin-left: auto; }
.error-question { font-size: 14px; color: #303133; margin: 0 0 8px 0; line-height: 1.5; }
.error-answers { display: flex; gap: 16px; font-size: 13px; margin-bottom: 8px; }
.wrong-ans { color: #F56C6C; }
.correct-ans { color: #67C23A; font-weight: 500; }
.error-analysis { font-size: 12px; color: #606266; padding: 8px 12px; background: #f8f9fa; border-radius: 6px; margin-bottom: 8px; line-height: 1.6; white-space: pre-wrap; }
.error-actions { display: flex; gap: 8px; }

/* AI解析列表 */
.insights-list { display: flex; flex-direction: column; gap: 10px; margin-bottom: 24px; }
.insight-card {
  background: #fff; border-radius: 10px; padding: 16px; border: 1px solid #f0f0f0;
  cursor: pointer; transition: all 0.2s; box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}
.insight-card:hover { border-color: #409EFF; box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
.insight-card.teacher { border-left: 3px solid #67C23A; }
.insight-header { display: flex; align-items: center; gap: 10px; }
.insight-icon { font-size: 22px; flex-shrink: 0; }
.insight-info { flex: 1; }
.insight-info h4 { margin: 0 0 2px 0; font-size: 15px; color: #303133; }
.insight-date { font-size: 12px; color: #909399; }
.insight-preview { font-size: 13px; color: #606266; margin: 8px 0 0 0; line-height: 1.5; }

.section-title { font-size: 16px; color: #303133; margin: 0 0 14px 0; padding-left: 8px; border-left: 3px solid #409EFF; }
.teacher-resources { margin-top: 8px; }

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
.code-editor :deep(textarea) { font-family: 'Consolas', 'Monaco', monospace !important; font-size: 14px !important; line-height: 1.6 !important; background: #f5f7fa !important; }

.result-content { text-align: center; }
.result-score { display: flex; justify-content: center; margin-bottom: 16px; }
.result-text { font-size: 16px; color: #303133; margin-bottom: 16px; }
.result-feedback { text-align: left; max-height: 200px; overflow-y: auto; }
.result-feedback p { margin: 4px 0; font-size: 13px; line-height: 1.6; }
.correct-fb { color: #67C23A; }
.wrong-fb { color: #F56C6C; }

.insight-detail { font-size: 14px; line-height: 1.8; max-height: 500px; overflow-y: auto; }
.insight-detail :deep(pre) { background: #f4f4f5; padding: 10px; border-radius: 6px; overflow-x: auto; }
.insight-detail :deep(code) { font-family: Consolas, monospace; font-size: 13px; }
.insight-detail :deep(h3) { font-size: 16px; margin: 12px 0 6px; }
.insight-detail :deep(h4) { font-size: 14px; margin: 10px 0 4px; }
</style>
