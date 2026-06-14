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
        <el-select v-model="errorFilter.course" placeholder="按课程筛选" clearable size="small" style="width:180px">
          <el-option v-for="c in courses" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="errorFilter.chapter" placeholder="按章节/知识点筛选" clearable size="small" style="width:180px">
          <el-option v-for="ch in chapterNames" :key="ch" :label="ch" :value="ch" />
        </el-select>
        <el-select v-model="errorFilter.minCount" placeholder="错题次数" clearable size="small" style="width:140px">
          <el-option label="≥1次" :value="1" /><el-option label="≥2次" :value="2" /><el-option label="≥3次" :value="3" />
        </el-select>
        <el-button type="warning" size="small" @click="redoAllErrors" :disabled="!errors.length">全部重做</el-button>
      </div>
      <div v-if="filteredErrors.length" class="error-list">
        <div v-for="err in filteredErrors" :key="err.id" class="error-item">
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
      <el-empty v-if="!filteredErrors.length" description="暂无错题，继续保持！" :image-size="80" />
    </div>

    <!-- Tab 3: AI错题解析 -->
    <div v-if="activeTab === 'insights'" class="tab-content">
      <el-alert type="info" :closable="false" style="margin-bottom:16px">
        AI根据你的错题自动生成学习资料。刷完题后点击下方按钮，AI根据你的错题生成对应知识点总结，帮助针对性提升。
      </el-alert>

      <div style="text-align:right; margin-bottom:20px">
        <el-button type="primary" size="large" @click="generateInsights" :loading="generatingInsights" :disabled="!errors.length && !insights.length">
          🤖 {{ insights.length ? '重新生成知识点总结' : '生成错题知识点总结' }}
        </el-button>
      </div>

      <!-- 生成中的加载状态 -->
      <div v-if="generatingInsights" style="text-align:center;padding:30px">
        <el-icon class="is-loading" :size="36" color="#b15311"><svg viewBox="0 0 1024 1024" width="1em" height="1em"><path d="M512 64a32 32 0 0132 32v192a32 32 0 01-64 0V96a32 32 0 0132-32zm0 640a32 32 0 0132 32v192a32 32 0 01-64 0V736a32 32 0 0132-32zm448-192a32 32 0 01-32 32H736a32 32 0 010-64h192a32 32 0 0132 32zm-640 0a32 32 0 01-32 32H96a32 32 0 010-64h192a32 32 0 0132 32z" fill="currentColor"/></svg></el-icon>
        <p style="color:#6a6054;margin-top:10px">AI正在分析你的错题并生成知识点总结...</p>
      </div>

      <!-- 生成的知识点总结列表 -->
      <div v-if="insights.length" class="insights-list">
        <div v-for="(item, idx) in insights" :key="idx" class="insight-card" @click="viewInsight(item)">
          <div class="insight-header">
            <span class="insight-icon">💡</span>
            <div class="insight-info">
              <h4>{{ item.knowledgePoint }}</h4>
              <span class="insight-meta">错题 {{ item.errorCount }} 次 · {{ formatDate(item.generatedAt) }}</span>
            </div>
            <el-tag size="small" type="warning">AI生成</el-tag>
          </div>
          <p class="insight-preview">{{ truncate(item.content, 120) }}</p>
        </div>
      </div>

      <el-empty v-if="!insights.length && !generatingInsights" description="暂无AI解析资料，刷完题后点击上方按钮自动生成" :image-size="80" />

      <!-- 查看知识点总结弹窗 -->
      <el-dialog v-model="showInsightDialog" :title="viewingInsight?.knowledgePoint || '知识点总结'" width="700px">
        <div class="insight-detail" v-html="renderMarkdown(viewingInsight?.content || '')"></div>
      </el-dialog>
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
          <el-progress type="circle" :percentage="lastResult.score" :width="120" :color="lastResult.score >= 60 ? '#4a7c4e' : '#a14a3d'" />
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

    <!-- 错题AI解析弹窗 -->
    <el-dialog v-model="showRedoDialog" title="AI错题解析" width="700px">
      <div class="redo-analysis" v-html="renderMarkdown(redoAnalysis)"></div>
      <template #footer>
        <el-button type="primary" @click="showRedoDialog = false">关闭</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import { useUserStore } from '../../store/user'
import api from '../../api'

const store = useUserStore()

const activeTab = ref('assignments')
const summary = ref(null)
const assignmentsByCourse = ref([])
const errors = ref([])
const courses = ref([])
const chapterNames = ref([])
const errorFilter = reactive({ course: '', chapter: '', minCount: '' })
const showAnswerDialog = ref(false)
const showResultDialog = ref(false)
const currentChapter = ref(null)
const dialogQuestions = ref([])
const dialogAnswers = reactive({})
const submitting = ref(false)
const lastResult = ref(null)

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
  return marked.parse(text)
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
    const res = await api.get('/practice/errors/' + store.id)
    const data = res.data || {}
    errors.value = data.errors || []
    courses.value = data.courses || []
    chapterNames.value = data.chapters || []
  } catch (e) { console.error('加载错题失败', e) }
}

// 前端筛选（不再调后端）
const filteredErrors = computed(() => {
  let list = errors.value
  if (errorFilter.course) list = list.filter(e => e.courseName === errorFilter.course)
  if (errorFilter.chapter) list = list.filter(e => e.chapterName === errorFilter.chapter || e.knowledgePoint === errorFilter.chapter)
  if (errorFilter.minCount) list = list.filter(e => (e.wrongCount || 1) >= errorFilter.minCount)
  return list
})

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

const redoAnalysis = ref('')
const showRedoDialog = ref(false)
const insights = ref([])
const generatingInsights = ref(false)
const showInsightDialog = ref(false)
const viewingInsight = ref(null)

function loadInsights() {
  // 已生成的知识总结保留在本地，切换到Tab时直接展示
}

async function generateInsights() {
  generatingInsights.value = true
  try {
    const res = await api.post('/practice/errors/' + store.id + '/generate-insights')
    const data = res.data || {}
    insights.value = data.insights || []
    if (insights.value.length) {
      ElMessage.success(data.message || 'AI已生成知识点总结')
    } else {
      ElMessage.info(data.message || '暂无错题需要分析')
    }
  } catch (e) {
    ElMessage.error('AI生成失败: ' + (e.response?.data?.message || '请重试'))
  } finally {
    generatingInsights.value = false
  }
}

function viewInsight(item) {
  viewingInsight.value = item
  showInsightDialog.value = true
}

function redoError(err) {
  redoAnalysis.value = err.analysis || '暂无AI解析，请先提交作业触发自动分析'
  showRedoDialog.value = true
}

async function resolveError(err) {
  try {
    await api.post('/practice/errors/' + err.id + '/resolve')
    ElMessage.success('已标记为掌握')
    errors.value = errors.value.filter(e => e.id !== err.id)
  } catch { ElMessage.error('操作失败') }
}

async function redoAllErrors() {
  const list = filteredErrors.value.length ? filteredErrors.value : errors.value
  if (!list.length) return
  let summary = ''
  for (const err of list) {
    summary += `### 【${err.knowledgePoint || '未知知识点'}】\n`
    summary += `**题目：** ${err.question?.substring(0, 100) || '无'}\n\n`
    summary += `**你的答案：** ${err.studentAnswer || '无'}\n\n`
    summary += `**正确答案：** ${err.correctAnswer || '无'}\n\n`
    summary += `**AI解析：** ${err.analysis || '暂无AI解析'}\n\n---\n\n`
  }
  redoAnalysis.value = summary
  showRedoDialog.value = true
}

async function refreshAll() {
  await Promise.all([loadAssignments(), loadErrors()])
}

onMounted(() => { loadAssignments() })
</script>

<style scoped>
.practice-room { }

.stats-row { display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; }
.stat-card {
  flex: 1; min-width: 140px; background: #f4efe7; border-radius: 14px;
  padding: 14px 18px; display: flex; align-items: center; gap: 12px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.04); border: 1px solid #dad2c7;
}
.stat-icon { font-size: 26px; }
.stat-value { font-size: 22px; font-weight: 700; color: #342618; }
.stat-label { font-size: 12px; color: #b6ada1; }

/* Tab栏 */
.tab-bar { display: flex; gap: 0; margin-bottom: 20px; background: #f4efe7; border-radius: 12px; overflow: hidden; box-shadow: 0 1px 6px rgba(0,0,0,0.04); border: 1px solid #dad2c7; }
.tab-btn {
  flex: 1; padding: 13px 0; border: none; background: #f4efe7;
  font-size: 14px; font-weight: 600; color: #6a6054; cursor: pointer;
  transition: all 0.2s; border-bottom: 2px solid transparent;
}
.tab-btn:hover { color: #b15311; background: rgba(177,83,17,0.03); }
.tab-btn.active { color: #b15311; border-bottom-color: #b15311; background: rgba(177,83,17,0.05); }

.course-group { margin-bottom: 22px; }
.course-name { font-size: 16px; color: #6a6054; margin: 0 0 12px 0; padding-left: 8px; border-left: 3px solid #b15311; }
.chapter-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
.chapter-card {
  background: #f4efe7; border-radius: 14px; padding: 18px;
  border: 1px solid #dad2c7; cursor: pointer;
  transition: all 0.25s; box-shadow: 0 1px 6px rgba(0,0,0,0.03);
}
.chapter-card:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,0,0,0.06); border-color: #e0d9cd; }
.card-pending { border-color: #fde68a; }
.card-completed { border-color: #a7f3d0; }
.card-graded { border-color: #bfdbfe; }
.card-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.chapter-order { font-size: 12px; color: #b6ada1; font-weight: 600; }
.status-tag { font-size: 11px; padding: 2px 10px; border-radius: 10px; font-weight: 600; }
.status-tag.pending { background: #fefce8; color: #a86220; }
.status-tag.completed { background: #ecfdf5; color: #3a6340; }
.chapter-name { margin: 0 0 4px 0; font-size: 15px; color: #342618; font-weight: 600; }
.chapter-desc { font-size: 12px; color: #dad2c7; margin: 0 0 10px 0; }
.card-footer { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #6a6054; flex-wrap: wrap; }
.score-text { color: #b15311; }
.score-text strong { font-size: 15px; }

.error-toolbar { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; align-items: center; }

.error-list { display: flex; flex-direction: column; gap: 10px; }
.error-item {
  background: #f4efe7; border-radius: 12px; padding: 14px;
  border: 1px solid #dad2c7; box-shadow: 0 1px 4px rgba(0,0,0,0.03);
}
.error-top { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; flex-wrap: wrap; }
.error-count { font-size: 11px; color: #a14a3d; font-weight: 600; }
.error-date { font-size: 11px; color: #b6ada1; margin-left: auto; }
.error-question { font-size: 13px; color: #6a6054; margin: 0 0 8px 0; line-height: 1.5; }
.error-answers { display: flex; gap: 14px; font-size: 12px; margin-bottom: 6px; }
.wrong-ans { color: #a14a3d; }
.correct-ans { color: #3a6340; font-weight: 500; }
.error-analysis { font-size: 12px; color: #6a6054; padding: 8px 12px; background: #e4dfd8; border-radius: 8px; margin-bottom: 8px; line-height: 1.6; white-space: pre-wrap; border: 1px solid #dad2c7; }
.error-actions { display: flex; gap: 6px; }

.question-list { max-height: 500px; overflow-y: auto; }
.question-item { margin-bottom: 18px; padding-bottom: 14px; border-bottom: 1px solid #dad2c7; }
.question-item:last-child { border-bottom: none; }
.question-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.question-num { font-weight: 600; color: #6a6054; }
.question-text { font-size: 14px; margin-bottom: 10px; line-height: 1.6; white-space: pre-wrap; }
.options-group { display: flex; flex-direction: column; gap: 6px; align-items: flex-start; }
.option-item { display: flex; align-items: center; margin-bottom: 2px; width: 100%; }
.opt-key { font-weight: 600; color: #b15311; margin-right: 2px; }
.code-editor :deep(textarea) { font-family: 'Consolas', monospace !important; font-size: 14px !important; line-height: 1.6 !important; background: #e4dfd8 !important; }

.result-content { text-align: center; }
.result-score { display: flex; justify-content: center; margin-bottom: 14px; }
.result-text { font-size: 15px; color: #6a6054; margin-bottom: 14px; }
.result-feedback { text-align: left; max-height: 200px; overflow-y: auto; }
.result-feedback p { margin: 4px 0; font-size: 13px; line-height: 1.6; }
.correct-fb { color: #3a6340; }
.wrong-fb { color: #a14a3d; }

.insights-list { display: flex; flex-direction: column; gap: 10px; }
.insight-card {
  background: #f4efe7; border-radius: 12px; padding: 14px;
  border: 1px solid #dad2c7; cursor: pointer;
  transition: all 0.2s; box-shadow: 0 1px 4px rgba(0,0,0,0.03);
}
.insight-card:hover { border-color: #bfdbfe; box-shadow: 0 4px 14px rgba(0,0,0,0.05); }
.insight-header { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.insight-icon { font-size: 22px; }
.insight-info h4 { margin: 0 0 2px 0; font-size: 14px; color: #342618; }
.insight-meta { font-size: 11px; color: #b6ada1; }
.insight-preview { font-size: 13px; color: #6a6054; margin: 0; line-height: 1.5; }
.insight-detail { font-size: 14px; line-height: 1.8; max-height: 500px; overflow-y: auto; }
.insight-detail :deep(h2) { font-size: 17px; margin: 14px 0 8px; color: #342618; }
.insight-detail :deep(h3) { font-size: 15px; margin: 12px 0 6px; color: #b15311; }
.insight-detail :deep(h4) { font-size: 14px; margin: 10px 0 4px; }
.insight-detail :deep(p) { margin: 0 0 8px; }
.insight-detail :deep(ul), .insight-detail :deep(ol) { margin: 6px 0; padding-left: 20px; }
.insight-detail :deep(li) { margin-bottom: 4px; }
.insight-detail :deep(pre) { background: #e4dfd8; padding: 10px; border-radius: 8px; overflow-x: auto; margin: 8px 0; }
.insight-detail :deep(code) { font-family: Consolas, monospace; font-size: 13px; }
.insight-detail :deep(pre code) { background: none; padding: 0; }
.insight-detail :deep(blockquote) { border-left: 3px solid #b15311; padding: 4px 10px; margin: 8px 0; background: rgba(177,83,17,0.04); border-radius: 0 6px 6px 0; }
.insight-detail :deep(strong) { color: #6a6054; }

</style>