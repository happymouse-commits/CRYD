<template>
  <div class="assign-page">
    <div class="page-header">
      <h2>布置作业</h2>
      <p class="subtitle">两种方式：从题库选题 或 AI一键出题，选好后直接布置给全班</p>
    </div>

    <!-- 课程选择 -->
    <div class="toolbar">
      <el-select v-model="selectedCourseId" placeholder="选择课程" @change="onCourseChange" style="width:260px">
        <el-option v-for="c in courses" :key="c.id" :label="c.name + ' (' + (c.className || '?') + ')'" :value="c.id" />
      </el-select>
      <el-button type="primary" @click="showCourseDialog = true">新建课程</el-button>
      <el-button v-if="selectedCourseId" type="danger" plain @click="deleteCourse">删除课程</el-button>
    </div>

    <div v-if="!selectedCourseId" style="text-align:center;padding:60px 0">
      <el-empty description="请先选择或新建一个课程" :image-size="80" />
    </div>

    <template v-else>
      <!-- 两种模式 Tab -->
      <div class="mode-tabs">
        <button :class="['mode-btn', { active: mode === 'bank' }]" @click="mode = 'bank'">📚 从题库选题</button>
        <button :class="['mode-btn', { active: mode === 'ai' }]" @click="mode = 'ai'">🤖 AI智能出题</button>
      </div>

      <!-- ====== 模式一：从题库选题 ====== -->
      <div v-if="mode === 'bank'" class="mode-panel">
        <!-- 筛选栏 -->
        <div class="filter-bar">
          <el-select v-model="filterChapter" placeholder="按章节" clearable size="small" style="width:200px" @change="loadQuestions">
            <el-option v-for="cn in chapterNames" :key="cn" :label="cn" :value="cn" />
          </el-select>
          <el-select v-model="filterDifficulty" placeholder="按难度" clearable size="small" style="width:130px" @change="loadQuestions">
            <el-option label="简单" value="easy" /><el-option label="中等" value="medium" /><el-option label="困难" value="hard" />
          </el-select>
          <el-select v-model="filterType" placeholder="按题型" clearable size="small" style="width:110px" @change="loadQuestions">
            <el-option label="选择题" value="choice" /><el-option label="代码题" value="code" />
          </el-select>
          <span class="filter-summary">共 {{ questions.length }} 题 (选择{{ choiceCount }} + 代码{{ codeCount }})</span>
          <el-button size="small" @click="selectAllQuestions">{{ allSelected ? '取消全选' : '全选当前' }}</el-button>
        </div>

        <!-- 题目列表 -->
        <div v-if="questions.length" class="question-list">
          <div v-for="q in questions" :key="q.id"
               :class="['question-row', { selected: selectedIds.includes(q.id) }]"
               @click="toggleQuestion(q.id)">
            <el-checkbox :model-value="selectedIds.includes(q.id)" @click.stop="toggleQuestion(q.id)" />
            <el-tag size="small" :type="q.type === 'choice' ? 'primary' : 'danger'">{{ q.type === 'choice' ? '选择' : '代码' }}</el-tag>
            <el-tag size="small" :type="q.difficulty === 'easy' ? 'success' : q.difficulty === 'hard' ? 'danger' : 'warning'">
              {{ q.difficulty === 'easy' ? '简单' : q.difficulty === 'hard' ? '困难' : '中等' }}
            </el-tag>
            <span class="q-chapter">{{ q.chapterName }}</span>
            <span class="q-content">{{ truncate(q.content, 60) }}</span>
            <el-tag size="small" type="info" v-if="q.knowledgePoint">{{ truncate(q.knowledgePoint, 12) }}</el-tag>
          </div>
        </div>
        <el-empty v-else description="暂无题目，切换到「AI智能出题」生成" :image-size="60" />

        <!-- 选中后布置 -->
        <div v-if="selectedIds.length" class="assign-bar">
          <el-alert type="info" :closable="false">
            已选 <strong>{{ selectedIds.length }}</strong> 道题
          </el-alert>
          <el-input v-model="assignChapterName" placeholder="作业名称(如：第3章测验)" size="small" style="width:200px" />
          <el-button type="primary" size="large" @click="assignSelected" :loading="assigning">
            布置给全班
          </el-button>
        </div>
      </div>

      <!-- ====== 模式二：AI智能出题 ====== -->
      <div v-if="mode === 'ai'" class="mode-panel">
        <div class="ai-form">
          <div class="ai-form-row">
            <el-select v-model="aiChapterName" placeholder="选择章节" style="width:220px">
              <el-option v-for="cn in chapterNames" :key="cn" :label="cn" :value="cn" />
            </el-select>
            <el-select v-model="aiQuestionType" placeholder="题目类型" style="width:120px">
              <el-option label="全部" value="all" />
              <el-option label="选择题" value="choice" />
              <el-option label="填空题" value="fill" />
              <el-option label="编程题" value="code" />
            </el-select>
            <el-select v-model="aiDifficulty" style="width:110px">
              <el-option label="简单" value="easy" /><el-option label="中等" value="medium" /><el-option label="困难" value="hard" />
            </el-select>
            <el-input-number v-model="aiCount" :min="1" :max="20" style="width:100px" />
            <span style="color:#b6ada1;font-size:13px">道题</span>
            <el-button type="warning" @click="aiGenerate" :loading="aiGenerating">
              🤖 生成题目
            </el-button>
          </div>
          <p class="ai-hint">AI参考题库格式生成，自动存入题库，生成后可立即选中布置</p>
        </div>

        <!-- AI生成结果 -->
        <div v-if="aiGenerated.length" class="ai-result">
          <h4>生成结果 ({{ aiGenerated.length }}题) <el-button link size="small" @click="selectAllGenerated">全选</el-button></h4>
          <div v-for="q in aiGenerated" :key="q.id"
               :class="['question-row', 'ai-row', { selected: selectedIds.includes(q.id) }]"
               @click="toggleQuestion(q.id)">
            <el-checkbox :model-value="selectedIds.includes(q.id)" @click.stop="toggleQuestion(q.id)" />
            <el-tag size="small" :type="q.type === 'choice' ? 'primary' : 'danger'">{{ q.type === 'choice' ? '选择' : '代码' }}</el-tag>
            <span class="q-content">{{ truncate(q.content, 60) }}</span>
            <el-tag size="small" type="warning">AI生成</el-tag>
            <el-tag size="small" type="info" v-if="q.knowledgePoint">{{ truncate(q.knowledgePoint, 15) }}</el-tag>
          </div>

          <div v-if="selectedIds.length" class="assign-bar">
            <el-alert type="success" :closable="false">AI生成 {{ aiGenerated.length }} 题，已选 {{ selectedIds.length }} 题</el-alert>
            <el-input v-model="assignChapterName" placeholder="作业名称" size="small" style="width:200px" />
            <el-button type="primary" size="large" @click="assignSelected" :loading="assigning">布置给全班</el-button>
          </div>
        </div>

        <el-empty v-if="!aiGenerated.length" description="设置章节、难度、数量，点击生成" :image-size="60" />
      </div>
    </template>

    <!-- 新建课程弹窗 -->
    <el-dialog v-model="showCourseDialog" title="新建课程" width="460px">
      <el-form :model="courseForm" label-width="80px">
        <el-form-item label="课程名称"><el-input v-model="courseForm.name" placeholder="如：C语言程序设计" /></el-form-item>
        <el-form-item label="课程编号"><el-input v-model="courseForm.code" placeholder="CS101" /></el-form-item>
        <el-form-item label="授课班级"><el-input v-model="courseForm.className" placeholder="如：计科2301" /></el-form-item>
        <el-form-item label="学期"><el-input v-model="courseForm.semester" placeholder="2025-2026-2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCourseDialog = false">取消</el-button>
        <el-button type="primary" @click="createCourse" :loading="creating">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../../store/user'
import api from '../../api'

const store = useUserStore()

const mode = ref('bank')
const courses = ref([])
const selectedCourseId = ref(null)
const questions = ref([])
const chapterNames = ref([])
const selectedIds = ref([])
const filterChapter = ref('')
const filterDifficulty = ref('')
const filterType = ref('')
const assignChapterName = ref('')
const assigning = ref(false)

const aiChapterName = ref('')
const aiQuestionType = ref('all')
const aiDifficulty = ref('medium')
const aiCount = ref(5)
const aiGenerating = ref(false)
const aiGenerated = ref([])

const showCourseDialog = ref(false)
const creating = ref(false)
const courseForm = reactive({ name: '', code: '', className: '', semester: '2025-2026-2' })

const choiceCount = computed(() => questions.value.filter(q => q.type === 'choice').length)
const codeCount = computed(() => questions.value.filter(q => q.type === 'code').length)
const allSelected = computed(() => questions.value.length > 0 && selectedIds.value.length >= questions.value.length)

function truncate(s, n) { return s && s.length > n ? s.slice(0, n) + '...' : s || '' }

async function loadCourses() {
  try { const r = await api.get('/teacher/' + store.id + '/courses'); courses.value = r.data || [] } catch { ElMessage.error('加载课程失败') }
}

async function onCourseChange() {
  chapterNames.value = []
  questions.value = []
  selectedIds.value = []
  aiGenerated.value = []
  if (!selectedCourseId.value) return
  await Promise.all([loadQuestions(), loadChapters()])
}

async function loadChapters() {
  try {
    const r = await api.get('/teacher/questions/chapters')
    const chapters = r.data || []
    if (chapters.length > 0) {
      chapterNames.value = chapters.map(c => c.chapterName || c.name)
    } else {
      chapterNames.value = FALLBACK_CHAPTERS
    }
  } catch (e) {
    chapterNames.value = FALLBACK_CHAPTERS
  }
}
const FALLBACK_CHAPTERS = [
  '一、程序设计基础', '二、顺序结构程序设计', '三、选择结构程序设计',
  '四、循环结构程序设计', '五、数组', '六、函数',
  '七、指针', '八、结构体与共用体', '九、预处理命令', '十、文件操作'
]

async function loadQuestions() {
  if (!selectedCourseId.value) return
  try {
    const params = { courseId: selectedCourseId.value }
    if (filterChapter.value) params.chapterName = filterChapter.value
    if (filterDifficulty.value) params.difficulty = filterDifficulty.value
    if (filterType.value) params.type = filterType.value
    const r = await api.get('/teacher/questions', { params })
    questions.value = r.data?.questions || []
    const chNames = r.data?.chapterNames || []
    chapterNames.value = chNames.length > 0 ? chNames : FALLBACK_CHAPTERS
    if (aiGenerated.value.length && !filterChapter.value && !filterDifficulty.value && !filterType.value) {
      questions.value = [...aiGenerated.value, ...questions.value]
    }
  } catch (e) { console.error('加载题库失败', e) }
}

function toggleQuestion(id) {
  const idx = selectedIds.value.indexOf(id)
  if (idx >= 0) selectedIds.value.splice(idx, 1)
  else selectedIds.value.push(id)
}

function selectAllQuestions() {
  if (allSelected.value) { selectedIds.value = [] }
  else { selectedIds.value = questions.value.map(q => q.id) }
}

function selectAllGenerated() {
  const genIds = aiGenerated.value.map(q => q.id)
  const allGenSelected = genIds.every(id => selectedIds.value.includes(id))
  if (allGenSelected) { selectedIds.value = selectedIds.value.filter(id => !genIds.includes(id)) }
  else { selectedIds.value = [...new Set([...selectedIds.value, ...genIds])] }
}

// 模式一：从题库选择并布置
async function assignSelected() {
  if (!selectedIds.value.length) return ElMessage.warning('请选择题目')
  if (!assignChapterName.value.trim()) return ElMessage.warning('请输入作业名称')
  assigning.value = true
  try {
    await api.post('/teacher/questions/assign', {
      courseId: selectedCourseId.value,
      teacherId: store.id,
      questionIds: selectedIds.value,
      chapterName: assignChapterName.value
    })
    ElMessage.success('布置成功！学生可在刷题房看到')
    selectedIds.value = []
    assignChapterName.value = ''
    await loadQuestions()
  } catch (e) { ElMessage.error('布置失败: ' + (e.response?.data?.message || '')) }
  finally { assigning.value = false }
}

// 模式二：AI出题
async function aiGenerate() {
  if (!aiChapterName.value) return ElMessage.warning('请选择章节')
  aiGenerating.value = true
  try {
    const r = await api.post('/teacher/questions/ai-generate', {
      courseId: selectedCourseId.value,
      chapterName: aiChapterName.value,
      questionType: aiQuestionType.value,
      difficulty: aiDifficulty.value,
      count: aiCount.value
    })
    aiGenerated.value = r.data?.generated || []
    // 刷新题库列表
    await loadQuestions()
    ElMessage.success('AI已生成 ' + aiGenerated.value.length + ' 道题并存入题库')
  } catch (e) { ElMessage.error('AI生成失败: ' + (e.response?.data?.message || '')) }
  finally { aiGenerating.value = false }
}

async function createCourse() {
  if (!courseForm.name) return ElMessage.warning('请输入课程名称')
  creating.value = true
  try {
    await api.post('/teacher/course', { ...courseForm, teacherId: store.id })
    ElMessage.success('创建成功')
    showCourseDialog.value = false
    await loadCourses()
    if (courses.value.length) { selectedCourseId.value = courses.value[courses.value.length - 1].id; await onCourseChange() }
  } catch { ElMessage.error('创建失败') }
  finally { creating.value = false }
}

async function deleteCourse() {
  try {
    await ElMessageBox.confirm('确定删除该课程及所有章节？', '删除', { type: 'warning' })
    await api.delete('/teacher/course/' + selectedCourseId.value)
    ElMessage.success('已删除')
    selectedCourseId.value = null
    questions.value = []
    aiGenerated.value = []
    await loadCourses()
  } catch { ElMessage.error('删除失败，请重试') }
}

onMounted(async () => {
  await loadCourses()
  if (courses.value.length) { selectedCourseId.value = courses.value[0].id; await onCourseChange() }
})
</script>

<style scoped>
.assign-page { }
.page-header { margin-bottom: 16px; }
.page-header h2 { margin: 0 0 4px 0; font-size: 22px; }
.subtitle { color: #b6ada1; font-size: 14px; margin: 0; }
.toolbar { display: flex; gap: 12px; margin-bottom: 20px; align-items: center; flex-wrap: wrap; }

.mode-tabs { display: flex; gap: 0; margin-bottom: 20px; border-radius: 10px; overflow: hidden; }
.mode-btn {
  flex: 1; padding: 12px 0; border: 2px solid #e8e8f0; background: #f4efe7;
  font-size: 15px; font-weight: 600; cursor: pointer; transition: all 0.2s;
}
.mode-btn:first-child { border-radius: 10px 0 0 10px; }
.mode-btn:last-child { border-radius: 0 10px 10px 0; }
.mode-btn:hover { border-color: #b15311; }
.mode-btn.active { background: #b15311; color: #f4efe7; border-color: #b15311; }

.mode-panel { background: #f4efe7; border-radius: 14px; padding: 20px; box-shadow: 0 1px 6px rgba(0,0,0,0.04); }

.filter-bar { display: flex; gap: 10px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.filter-summary { font-size: 13px; color: #b6ada1; }

.question-list { display: flex; flex-direction: column; gap: 4px; max-height: 480px; overflow-y: auto; margin-bottom: 16px; }
.question-row {
  display: flex; align-items: center; gap: 8px; padding: 10px 12px;
  border: 1px solid #f0f0f0; border-radius: 8px; cursor: pointer; transition: all 0.15s;
}
.question-row:hover { background: #f8f9ff; border-color: #c0c4ff; }
.question-row.selected { background: rgba(177,83,17,0.06); border-color: #b15311; }
.ai-row { border-left: 3px solid #c97930; }
.q-chapter { font-size: 12px; color: #b6ada1; min-width: 100px; }
.q-content { flex: 1; font-size: 13px; color: #342618; }

.assign-bar { display: flex; gap: 12px; align-items: center; margin-top: 12px; flex-wrap: wrap; }

.ai-form { }
.ai-form-row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.ai-hint { font-size: 12px; color: #b6ada1; margin-top: 8px; }
.ai-result { margin-top: 20px; border-top: 1px solid #f0f0f0; padding-top: 16px; }
.ai-result h4 { margin: 0 0 10px 0; font-size: 15px; }
</style>
