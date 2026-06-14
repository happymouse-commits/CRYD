<template>
  <div class="resources-page">
    <div class="page-header">
      <h2>资源中心</h2>
      <p class="subtitle">AI生成 + 错题突破资料 + B站视频教程，搜藏导出随你用</p>
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
          <el-option label="视频教程" value="video" />
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
        <p class="card-preview" v-if="r.type !== 'video'">{{ truncate(r.content, 100) }}</p>
        <p class="card-preview video-preview" v-else>点击观看视频教程</p>
        <div class="card-bottom">
          <span class="card-time">{{ formatDate(r.createdAt) }}</span>
          <div class="card-actions">
            <el-button type="primary" size="small" @click.stop="exportResource(r)">导出</el-button>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无资源，点「AI生成新资源」让星火大模型为你生成" :image-size="80" />

    <!-- 资源详情弹窗 -->
    <el-dialog v-model="showDetail" :title="detailResource?.title || '资源详情'" :width="detailResource?.type === 'video' ? '900px' : '700px'">
      <div v-if="detailResource?.type === 'video'" class="video-wrapper">
        <iframe
          :src="detailResource?.content"
          scrolling="no"
          border="0"
          frameborder="no"
          framespacing="0"
          allowfullscreen="true"
          class="bilibili-player"
        ></iframe>
      </div>
      <div v-else class="detail-body" v-html="renderMd(detailResource?.content || '')"></div>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import { marked } from 'marked'
import api from '../../api'

const store = useUserStore()

const resources = ref([])
const filterType = ref('')
const filterDifficulty = ref('')
const searchKey = ref('')
const generating = ref(false)
const showDetail = ref(false)
const detailResource = ref(null)

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
function typeLabel(t) { const m = { article: '文章', exercise: '练习题', explanation: '知识讲解', mindmap: '思维导图', code: '代码演示', video: '视频教程', plan: '学习计划' }; return m[t] || t || '其他' }
function typeColor(t) { const m = { article: 'primary', exercise: 'warning', explanation: 'success', mindmap: '', code: 'danger', video: '', plan: 'info' }; return m[t] || 'info' }
function diffLabel(d) { const m = { easy: '简单', medium: '中等', hard: '困难' }; return m[d] || d || '' }
function diffColor(d) { const m = { easy: 'success', medium: 'warning', hard: 'danger' }; return m[d] || 'info' }

function renderMd(text) {
  if (!text) return ''
  return marked.parse(text)
}

async function loadResources() {
  try {
    const res = await api.get('/resources/student/' + store.id)
    resources.value = res.data || []
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
.page-header h2 { margin: 0 0 4px 0; font-size: 20px; font-weight: 700; color: #342618; }
.subtitle { color: #b6ada1; font-size: 13px; margin: 0; }

.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 10px; }
.filter-group { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }

.resource-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; }
.resource-card {
  background: #f4efe7; border-radius: 14px; padding: 18px;
  border: 1px solid #dad2c7; cursor: pointer;
  transition: all 0.25s; box-shadow: 0 1px 6px rgba(0,0,0,0.03);
  display: flex; flex-direction: column;
}
.resource-card:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,0,0,0.06); border-color: #e0d9cd; }
.card-top { display: flex; gap: 5px; align-items: center; margin-bottom: 8px; flex-wrap: wrap; }
.card-kp { font-size: 11px; color: #b6ada1; }
.card-title { margin: 0 0 6px 0; font-size: 15px; color: #342618; font-weight: 600; }
.card-preview { font-size: 13px; color: #6a6054; line-height: 1.5; margin: 0 0 10px 0; flex: 1; }
.card-bottom { display: flex; justify-content: space-between; align-items: center; }
.card-time { font-size: 11px; color: #dad2c7; }
.card-actions { display: flex; gap: 4px; }

.detail-body { font-size: 14px; line-height: 1.85; max-height: 560px; overflow-y: auto; color: #6a6054; }
.detail-body :deep(h2) { font-size: 19px; margin: 18px 0 10px; padding-bottom: 6px; border-bottom: 1px solid #dad2c7; color: #342618; }
.detail-body :deep(h3) { font-size: 16px; margin: 14px 0 8px; color: #342618; }
.detail-body :deep(h4) { font-size: 14px; margin: 12px 0 6px; color: #6a6054; }
.detail-body :deep(p) { margin: 0 0 10px; }
.detail-body :deep(ul), .detail-body :deep(ol) { margin: 8px 0; padding-left: 20px; }
.detail-body :deep(li) { margin-bottom: 4px; }
.detail-body :deep(pre) { background: #e4dfd8; padding: 12px 16px; border-radius: 8px; overflow-x: auto; margin: 10px 0; }
.detail-body :deep(code) { font-family: Consolas, 'Courier New', monospace; font-size: 13px; background: #dad2c7; padding: 1px 5px; border-radius: 3px; color: #e03968; }
.detail-body :deep(pre code) { background: none; padding: 0; color: #6a6054; }
.detail-body :deep(blockquote) { border-left: 3px solid #b15311; padding: 6px 14px; margin: 10px 0; background: rgba(177,83,17,0.04); border-radius: 0 6px 6px 0; color: #6a6054; }
.detail-body :deep(table) { border-collapse: collapse; width: 100%; margin: 10px 0; }
.detail-body :deep(th), .detail-body :deep(td) { border: 1px solid #dad2c7; padding: 8px 12px; text-align: left; }
.detail-body :deep(th) { background: #e4dfd8; font-weight: 600; color: #6a6054; }
.detail-body :deep(strong) { color: #6a6054; }

.video-wrapper { position: relative; padding-bottom: 56.25%; height: 0; overflow: hidden; }
.bilibili-player { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: none; border-radius: 8px; }
.video-preview { color: #b15311; font-weight: 500; }
.video-preview::before { content: '▶ '; }
.detail-body :deep(hr) { border: none; border-top: 1px solid #dad2c7; margin: 16px 0; }

</style>
