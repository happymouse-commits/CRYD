<template>
  <div class="kb-page">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <h3 style="margin:0">课程知识库管理</h3>
      <div>
        <el-button type="primary" @click="showCreateKb = true">创建知识库</el-button>
        <el-button @click="showUpload = true" :disabled="!currentKb">上传文档</el-button>
      </div>
    </div>

    <!-- 知识库选择 -->
    <div class="kb-selector" v-if="kbList.length">
      <el-radio-group v-model="currentKbId" @change="selectKb">
        <el-radio-button v-for="kb in kbList" :key="kb.id" :value="kb.id">{{ kb.name }}</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 文档列表 -->
    <div v-if="currentKb">
      <div style="margin: 12px 0">
        <el-input v-model="searchQuery" placeholder="搜索知识库内容..." style="max-width: 400px" clearable @keyup.enter="searchKb" />
        <el-button style="margin-left: 8px" @click="searchKb">搜索</el-button>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="文档列表" name="docs">
          <el-table :data="documents" stripe>
            <el-table-column prop="fileName" label="文件名" />
            <el-table-column prop="fileType" label="类型" width="80" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'vectorized' ? 'success' : 'warning'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="tags" label="标签" />
            <el-table-column prop="createdAt" label="上传时间" width="160">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button type="danger" size="small" link @click="deleteDoc(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="标签管理" name="tags">
          <div class="tag-list">
            <el-tag v-for="tag in tags" :key="tag" style="margin: 4px" type="" closable @close="removeTag(tag)">{{ tag }}</el-tag>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-empty v-else description="暂无知识库，请先创建一个" />

    <!-- 创建知识库弹窗 -->
    <el-dialog v-model="showCreateKb" title="创建知识库" width="400px">
      <el-form>
        <el-form-item label="名称"><el-input v-model="kbForm.name" placeholder="如：C语言程序设计" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="kbForm.desc" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateKb = false">取消</el-button>
        <el-button type="primary" @click="createKb">创建</el-button>
      </template>
    </el-dialog>

    <!-- 上传文档弹窗 -->
    <el-dialog v-model="showUpload" title="上传文档" width="500px">
      <el-upload drag :auto-upload="false" :on-change="handleFile" accept=".txt,.md,.pdf,.docx">
        <el-icon :size="48"><component :is="'UploadFilled'" /></el-icon>
        <div>拖拽或点击上传</div>
        <template #tip>支持 TXT / MD / PDF / DOCX 格式，上传后自动向量化</template>
      </el-upload>
      <div style="margin-top: 16px">
        <el-input v-model="manualContent" type="textarea" :rows="6" placeholder="或直接粘贴文本内容..." />
        <el-button type="primary" style="margin-top: 8px" @click="uploadText" :disabled="!manualContent.trim()">
          提交文本
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useUserStore } from '../../store/user'
import { knowledgeApi, analyticsApi } from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const store = useUserStore()
const kbList = ref([])
const currentKbId = ref(null)
const currentKb = ref(null)
const documents = ref([])
const tags = ref([])
const searchQuery = ref('')
const activeTab = ref('docs')
const showCreateKb = ref(false)
const showUpload = ref(false)
const manualContent = ref('')
const kbForm = ref({ name: '', desc: '' })

onMounted(async () => {
  try {
    kbList.value = (await knowledgeApi.getByTeacher(store.id)).data || []
    if (kbList.value.length) {
      currentKbId.value = kbList.value[0].id
      await selectKb(currentKbId.value)
    }
  } catch(e) { /* 初始化时teacherId可能没有kb */ }
})

async function selectKb(val) {
  currentKb.value = kbList.value.find(k => k.id === val)
  try { documents.value = (await knowledgeApi.getDocuments(val)).data || []; } catch(e) {}
  try { tags.value = (await knowledgeApi.getTags(val)).data || []; } catch(e) {}
}

async function createKb() {
  try {
    await knowledgeApi.create({ courseId: 1, teacherId: store.id, name: kbForm.value.name, description: kbForm.value.desc })
    showCreateKb.value = false
    ElMessage.success('知识库已创建')
    kbList.value = (await knowledgeApi.getByTeacher(store.id)).data || []
  } catch(e) { ElMessage.error('创建失败') }
}

async function handleFile(file) {
  const formData = new FormData()
  formData.append('file', file.raw)
  try {
    await knowledgeApi.uploadDocument(currentKbId.value, formData)
    ElMessage.success('文档上传成功，正在处理...')
    setTimeout(() => selectKb(currentKbId.value), 2000)
  } catch(e) { ElMessage.error('上传失败') }
}

async function uploadText() {
  try {
    await knowledgeApi.uploadText(currentKbId.value, { fileName: '手动输入.txt', content: manualContent.value })
    manualContent.value = ''
    ElMessage.success('文本已提交，正在处理...')
    setTimeout(() => selectKb(currentKbId.value), 2000)
  } catch(e) { ElMessage.error('提交失败') }
}

async function deleteDoc(row) {
  try {
    await ElMessageBox.confirm('确认删除此文档？', '确认', { type: 'warning' })
    await knowledgeApi.deleteDocument(currentKbId.value, row.id)
    ElMessage.success('已删除')
    selectKb(currentKbId.value)
  } catch(e) { /* cancel */ }
}

async function searchKb() {
  if (!searchQuery.value.trim()) return
  try {
    const res = await knowledgeApi.search(currentKbId.value, searchQuery.value)
    ElMessage.success('搜索完成')
  } catch(e) { ElMessage.error('搜索失败') }
}

function removeTag(tag) { ElMessage.info('标签管理功能') }

function formatTime(t) { return t ? new Date(t).toLocaleString() : '' }
</script>

<style scoped>
.kb-page { padding: 8px; }
.kb-selector { margin-bottom: 16px; }
.tag-list { padding: 8px; }
</style>
