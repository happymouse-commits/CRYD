<template>
  <div class="resources-page">
    <div class="page-header">
      <h2>资源中心</h2>
      <p class="subtitle">AI生成 + 错题突破资料，搜藏导出随你用</p>
    </div>

    <!-- 顶部操作栏 -->
    <div class="toolbar">
      <div class="filter-group">
        <el-select v-model="filterType" placeholder="全部类型" clearable size="small" style="width:130px">
          <el-option label="全部" value="" />
          <el-option label="文章" value="article" />
          <el-option label="练习题" value="exercise" />
          <el-option label="知识讲解" value="explanation" />
          <el-option label="思维导图" value="mindmap" />
          <el-option label="代码演示" value="code" />
        </el-select>
        <el-select v-model="filterDifficulty" placeholder="难度" clearable size="small" style="width:110px">
          <el-option label="简单" value="easy" />
          <el-option label="中等" value="medium" />
          <el-option label="困难" value="hard" />
        </el-select>
        <el-input v-model="searchKey" placeholder="搜索知识点..." size="small" clearable style="width:200px" />
      </div>
      <el-button type="primary" size="small" @click="aiGenerate" :loading="generating">🤖 AI生成新资源</el-button>
    </div>

    <!-- 资源卡片列表 -->
    <div v-if="filteredResources.length" class="resource-grid">
      <div v-for="r in filteredResources" :key="r.id" class="resource-card" @click="viewResource(r)">
        <div class="card-top">
          <el-tag size="small" :type="typeColor(r.type)">{{ typeLabel(r.type) }}</el-tag>
          <el-tag size="small" :type="diffColor(r.difficulty)" v-if="r.difficulty">{{ diffLabel(r.difficulty) }}</el-tag>
          <span class="card-kp" v-if="r.knowledgePoint">{{ r.knowledgePoint }}</span>
        </div>
        <h4 class="card-title">{{ r.title || '未命名资源' }}</h4>
        <p class="card-preview">{{ truncate(r.content, 100) }}</p>
        <div class="card-bottom">
          <span class="card-time">{{ formatDate(r.createdAt) }}</span>
          <div class="card-actions">
            <el-button link size="small" @click.stop="toggleFavorite(r)">
              {{ favorited[r.id] ? '❤️' : '🤍' }} {{ r.favoriteCount || 0 }}
            </el-button>
            <el-button link size="small" @click.stop="viewComments(r)">💬 {{ r.commentCount || 0 }}</el-button>
            <el-button type="primary" size="small" @click.stop="exportResource(r)">导出</el-button>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无资源，点「AI生成新资源」让星火大模型为你生成" :image-size="80" />

    <!-- 资源详情弹窗 -->
    <el-dialog v-model="showDetail" :title="detailResource?.title || '资源详情'" width="700px">
      <div class="detail-body" v-html="renderMd(detailResource?.content || '')"></div>
    </el-dialog>

    <!-- 评论弹窗 -->
    <el-dialog v-model="showComments" title="评论" width="500px">
      <div class="comment-list" v-if="comments.length">
        <div v-for="c in comments" :key="c.id" class="comment-item">
          <strong>{{ c.userName || '匿名' }}</strong>
          <span class="comment-time">{{ formatDate(c.createdAt) }}</span>
          <p>{{ c.content }}</p>
        </div>
      </div>
      <el-empty v-else description="暂无评论" :image-size="60" />
      <div class="comment-input">
        <el-input v-model="newComment" placeholder="写评论..." size="small" />
        <el-button size="small" type="primary" @click="sendComment">发送</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import api from '../../api'

const store = useUserStore()

const resources = ref([])
const filterType = ref('')
const filterDifficulty = ref('')
const searchKey = ref('')
const generating = ref(false)
const favorited = reactive({})
const showDetail = ref(false)
const showComments = ref(false)
const detailResource = ref(null)
const comments = ref([])
const commentResource = ref(null)
const newComment = ref('')

const filteredResources = computed(() => {
  let list = resources.value
  if (filterType.value) list = list.filter(r => r.type === filterType.value)
  if (filterDifficulty.value) list = list.filter(r => r.difficulty === filterDifficulty.value)
  if (searchKey.value) {
    const kw = searchKey.value.toLowerCase()
    list = list.filter(r => (r.knowledgePoint || '').toLowerCase().includes(kw) ||
      (r.title || '').toLowerCase().includes(kw) || (r.content || '').toLowerCase().includes(kw))
  }
  return list
})

function truncate(s, n) { return s && s.length > n ? s.slice(0, n) + '...' : s || '' }
function formatDate(d) { if (!d) return ''; const t = new Date(d); return t.getFullYear() + '-' + String(t.getMonth() + 1).padStart(2, '0') + '-' + String(t.getDate()).padStart(2, '0') }
function typeLabel(t) { const m = { article: '文章', exercise: '练习题', explanation: '知识讲解', mindmap: '思维导图', code: '代码演示', plan: '学习计划' }; return m[t] || t || '其他' }
function typeColor(t) { const m = { article: 'primary', exercise: 'warning', explanation: 'success', mindmap: '', code: 'danger', plan: 'info' }; return m[t] || 'info' }
function diffLabel(d) { const m = { easy: '简单', medium: '中等', hard: '困难' }; return m[d] || d || '' }
function diffColor(d) { const m = { easy: 'success', medium: 'warning', hard: 'danger' }; return m[d] || 'info' }

function renderMd(text) {
  if (!text) return ''
  return text
    .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code class="$1">$2</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/^### (.+)$/gm, '<h4>$1</h4>')
    .replace(/^## (.+)$/gm, '<h3>$1</h3>')
    .replace(/\n/g, '<br>')
}

async function loadResources() {
  try {
    const res = await api.get('/resources/student/' + store.id)
    resources.value = res.data || []
    // 检查收藏状态
    for (const r of resources.value) {
      try {
        const f = await api.get('/resources/' + r.id + '/favorite/check?userId=' + store.id)
        favorited[r.id] = f.data?.favorited || false
      } catch { favorited[r.id] = false }
    }
  } catch (e) { console.error('加载资源失败', e) }
}

async function aiGenerate() {
  generating.value = true
  try {
    const res = await api.post('/resources/generate/' + store.id, {})
    if (res.code === 200) {
      const newList = res.data || []
      ElMessage.success('AI已生成 ' + (Array.isArray(newList) ? newList.length : 0) + ' 篇新资源')
      await loadResources()
    } else {
      ElMessage.error('生成失败: ' + (res.message || '请重试'))
    }
  } catch (e) {
    ElMessage.error('生成失败: ' + (e.response?.data?.message || '请重试'))
  } finally { generating.value = false }
}

function viewResource(r) { detailResource.value = r; showDetail.value = true }

async function toggleFavorite(r) {
  try {
    const res = await api.post('/resources/' + r.id + '/favorite?userId=' + store.id)
    favorited[r.id] = res.data?.favorited || false
    const countRes = await api.get('/resources/' + r.id + '/favorite/count')
    r.favoriteCount = countRes.data?.count ?? (r.favoriteCount || 0)
  } catch {}
}

async function viewComments(r) {
  commentResource.value = r
  try {
    const res = await api.get('/resources/' + r.id + '/comments')
    comments.value = res.data || []
  } catch { comments.value = [] }
  showComments.value = true
}

async function sendComment() {
  if (!newComment.value.trim() || !commentResource.value) return
  try {
    await api.post('/resources/' + commentResource.value.id + '/comments', {
      userId: store.id,
      userName: store.nickname || store.username,
      content: newComment.value
    })
    commentResource.value.commentCount = (commentResource.value.commentCount || 0) + 1
    newComment.value = ''
    ElMessage.success('评论已发送')
    await viewComments(commentResource.value)
  } catch { ElMessage.error('发送失败') }
}

async function exportResource(r) {
  try {
    const res = await api.get('/resources/' + r.id + '/export')
    const blob = new Blob([res.data?.content || ''], { type: 'text/markdown' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = (r.title || 'resource') + '.md'
    a.click()
    ElMessage.success('已导出')
  } catch { ElMessage.error('导出失败') }
}

onMounted(() => { loadResources() })
</script>

<style scoped>
.resources-page { }
.page-header { margin-bottom: 16px; }
.page-header h2 { margin: 0 0 4px 0; font-size: 22px; }
.subtitle { color: #909399; font-size: 14px; margin: 0; }

.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 10px; }
.filter-group { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }

.resource-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; }
.resource-card {
  background: #fff; border-radius: 12px; padding: 20px;
  border: 1px solid #f0f0f0; cursor: pointer;
  transition: all 0.25s; box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  display: flex; flex-direction: column;
}
.resource-card:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(0,0,0,0.1); border-color: #409EFF; }
.card-top { display: flex; gap: 6px; align-items: center; margin-bottom: 10px; flex-wrap: wrap; }
.card-kp { font-size: 12px; color: #909399; }
.card-title { margin: 0 0 6px 0; font-size: 16px; color: #303133; }
.card-preview { font-size: 13px; color: #606266; line-height: 1.5; margin: 0 0 12px 0; flex: 1; }
.card-bottom { display: flex; justify-content: space-between; align-items: center; }
.card-time { font-size: 12px; color: #c0c4cc; }
.card-actions { display: flex; gap: 4px; }

.detail-body { font-size: 14px; line-height: 1.8; max-height: 500px; overflow-y: auto; }
.detail-body :deep(pre) { background: #f4f4f5; padding: 10px; border-radius: 6px; overflow-x: auto; }
.detail-body :deep(code) { font-family: Consolas, monospace; font-size: 13px; }

.comment-list { max-height: 300px; overflow-y: auto; margin-bottom: 12px; }
.comment-item { padding: 8px 0; border-bottom: 1px solid #f5f5f5; }
.comment-item p { margin: 4px 0 0 0; font-size: 13px; color: #606266; }
.comment-time { font-size: 11px; color: #c0c4cc; margin-left: 8px; }
.comment-input { display: flex; gap: 8px; align-items: center; }
</style>
