<template>
  <div class="kb-page">
    <div class="page-header">
      <h2>知识库管理</h2>
      <p class="subtitle">AI自动生成学习资料 + 教师上传辅助内容</p>
    </div>

    <!-- 课程自动同步 -->
    <div class="section" v-if="courses.length">
      <h3 class="section-title">📘 同步课程</h3>
      <div class="course-tags">
        <el-tag v-for="c in courses" :key="c.id" :type="selectedCourseId === c.id ? 'primary' : 'info'"
                size="large" @click="selectCourse(c)" style="cursor:pointer; margin:4px">
          {{ c.name }}
        </el-tag>
      </div>
      <p class="hint">课程从「布置作业」自动同步，新建课程后此处自动出现</p>
    </div>

    <!-- 教师上传资料 -->
    <div class="section">
      <div class="section-header">
        <h3 class="section-title">📎 教师辅助资料</h3>
        <el-button type="primary" size="small" @click="showUpload = true">上传文档</el-button>
      </div>
      <div class="doc-list" v-if="teacherDocs.length">
        <div v-for="doc in teacherDocs" :key="doc.id" class="doc-card teacher">
          <div class="doc-header">
            <span class="doc-icon">📄</span>
            <div class="doc-info">
              <h4>{{ doc.fileName || '教师资料' }}</h4>
              <span class="doc-date" v-if="doc.createdAt">{{ formatDate(doc.createdAt) }}</span>
            </div>
            <el-tag size="small" type="primary">教师上传</el-tag>
          </div>
          <div class="doc-actions">
            <el-button size="small" type="primary" @click="viewDoc(doc)">查看</el-button>
            <el-button size="small" type="danger" @click="deleteDoc(doc)">删除</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无教师上传资料" :image-size="60" />
    </div>

    <el-empty v-if="!courses.length" description="暂无课程，请先在「布置作业」中创建课程" :image-size="80" />

    <!-- 上传弹窗 -->
    <el-dialog v-model="showUpload" title="上传辅助资料" width="500px">
      <el-tabs v-model="uploadTab">
        <el-tab-pane label="上传文件" name="file">
          <el-upload drag :auto-upload="false" :on-change="handleFile" accept=".txt,.md,.pdf,.docx">
            <el-icon :size="48"><component :is="'UploadFilled'" /></el-icon>
            <div>拖拽或点击上传</div>
            <template #tip>支持 TXT / MD / PDF / DOCX</template>
          </el-upload>
        </el-tab-pane>
        <el-tab-pane label="粘贴文本" name="text">
          <el-input v-model="manualContent" type="textarea" :rows="8" placeholder="直接粘贴文本内容..." />
          <el-button type="primary" style="margin-top:12px" @click="uploadText" :disabled="!manualContent.trim()">
            提交文本
          </el-button>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 查看文档弹窗 -->
    <el-dialog v-model="showViewDialog" :title="viewingDoc?.fileName || '文档'" width="700px">
      <div class="doc-detail" v-html="renderMarkdown(viewingDoc?.originalContent || '')"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import { useUserStore } from '../../store/user'
import api from '../../api'

const store = useUserStore()

const courses = ref([])
const selectedCourseId = ref(null)
const teacherDocs = ref([])
const showUpload = ref(false)
const showViewDialog = ref(false)
const viewingDoc = ref(null)
const uploadTab = ref('file')
const manualContent = ref('')

function truncate(s, n) { return s && s.length > n ? s.slice(0, n) + '...' : s || '' }
function formatDate(d) {
  if (!d) return ''
  const t = new Date(d)
  return t.getFullYear() + '-' + String(t.getMonth() + 1).padStart(2, '0') + '-' + String(t.getDate()).padStart(2, '0')
}

function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(text)
}

async function loadCourses() {
  try {
    const r = await api.get('/teacher/' + store.id + '/courses')
    courses.value = r.data || []
    if (courses.value.length) {
      selectedCourseId.value = courses.value[0].id
      await loadDocs()
    }
  } catch { ElMessage.error('操作失败，请重试') }
}

async function selectCourse(c) {
  selectedCourseId.value = c.id
  await loadDocs()
}

async function loadDocs() {
  if (!selectedCourseId.value) return
  try {
    const r = await api.get('/knowledge/course/' + selectedCourseId.value)
    const docs = r.data?.documents || r.data || []
    teacherDocs.value = Array.isArray(docs) ? docs : []
  } catch {
    teacherDocs.value = []
  }
}

async function handleFile(file) {
  const formData = new FormData()
  formData.append('file', file.raw)
  try {
    await api.post('/knowledge/' + selectedCourseId.value + '/documents', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success('上传成功')
    showUpload.value = false
    await loadDocs()
  } catch { ElMessage.error('上传失败') }
}

async function uploadText() {
  try {
    await api.post('/knowledge/' + selectedCourseId.value + '/documents/text', {
      fileName: '教师资料_' + new Date().toISOString().slice(0, 10) + '.txt',
      content: manualContent.value
    })
    manualContent.value = ''
    ElMessage.success('已提交')
    showUpload.value = false
    await loadDocs()
  } catch { ElMessage.error('提交失败') }
}

function viewDoc(doc) {
  viewingDoc.value = doc
  showViewDialog.value = true
}

async function deleteDoc(doc) {
  try {
    await ElMessageBox.confirm('确定删除此文档？', '确认', { type: 'warning' })
    await api.delete('/knowledge/' + selectedCourseId.value + '/documents/' + doc.id)
    ElMessage.success('已删除')
    await loadDocs()
  } catch { ElMessage.error('操作失败，请重试') }
}

onMounted(() => { loadCourses() })
</script>

<style scoped>
.kb-page { }
.page-header { margin-bottom: 20px; }
.page-header h2 { margin: 0 0 4px 0; font-size: 22px; }
.subtitle { color: #909399; font-size: 14px; margin: 0; }

.section { background: #fff; border-radius: 12px; padding: 20px; margin-bottom: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.section-title { font-size: 16px; color: #303133; margin: 0 0 14px 0; padding-left: 8px; border-left: 3px solid #409EFF; }
.hint { font-size: 12px; color: #909399; margin-top: 8px; }
.course-tags { display: flex; flex-wrap: wrap; gap: 4px; }

.doc-list { display: flex; flex-direction: column; gap: 10px; }
.doc-card {
  border: 1px solid #f0f0f0; border-radius: 10px; padding: 14px;
  transition: all 0.2s;
}
.doc-card:hover { border-color: #409EFF; }
.doc-card.ai { border-left: 3px solid #E6A23C; }
.doc-card.teacher { border-left: 3px solid #67C23A; }
.doc-header { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.doc-icon { font-size: 20px; flex-shrink: 0; }
.doc-info { flex: 1; }
.doc-info h4 { margin: 0 0 2px 0; font-size: 14px; color: #303133; }
.doc-date { font-size: 12px; color: #909399; }
.doc-preview { font-size: 13px; color: #606266; margin: 0 0 8px 0; line-height: 1.5; }
.doc-actions { display: flex; gap: 8px; }

.doc-detail { font-size: 14px; line-height: 1.8; max-height: 500px; overflow-y: auto; }
.doc-detail :deep(h2) { font-size: 18px; margin: 16px 0 8px; }
.doc-detail :deep(h3) { font-size: 16px; margin: 12px 0 6px; }
.doc-detail :deep(h4) { font-size: 14px; margin: 10px 0 4px; }
.doc-detail :deep(p) { margin: 0 0 8px; }
.doc-detail :deep(ul), .doc-detail :deep(ol) { margin: 6px 0; padding-left: 20px; }
.doc-detail :deep(li) { margin-bottom: 4px; }
.doc-detail :deep(pre) { background: #f4f4f5; padding: 10px; border-radius: 6px; overflow-x: auto; margin: 8px 0; }
.doc-detail :deep(code) { font-family: Consolas, monospace; font-size: 13px; background: #f0f2f5; padding: 1px 4px; border-radius: 3px; }
.doc-detail :deep(pre code) { background: none; padding: 0; }
.doc-detail :deep(blockquote) { border-left: 3px solid #409EFF; padding: 4px 10px; margin: 8px 0; background: #ecf5ff; }
.doc-detail :deep(table) { border-collapse: collapse; margin: 8px 0; }
.doc-detail :deep(th), .doc-detail :deep(td) { border: 1px solid #dcdfe6; padding: 6px 10px; }
</style>
