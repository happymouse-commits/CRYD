<template>
  <div class="users-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <h2>👥 用户管理</h2>
      <div class="header-actions">
        <!-- 批量操作 -->
        <el-button @click="openBatchImport">
          <el-icon><Upload /></el-icon> 批量导入
        </el-button>
        <el-button type="success" @click="exportUsers" :loading="exporting">
          <el-icon><Download /></el-icon> 导出 {{ activeTabName }}
        </el-button>
        <el-button type="primary" @click="openAdd">
          <el-icon><Plus /></el-icon> 新增用户
        </el-button>
      </div>
    </div>

    <!-- 标签页 -->
    <el-card class="main-card" shadow="never">
      <el-tabs v-model="activeTab" @tab-change="loadUsers">
        <el-tab-pane name="admin">
          <template #label><span><el-icon><Avatar /></el-icon> 管理员</span></template>
        </el-tab-pane>
        <el-tab-pane name="teacher">
          <template #label><span><el-icon><Reading /></el-icon> 教师</span></template>
        </el-tab-pane>
        <el-tab-pane name="student">
          <template #label><span><el-icon><User /></el-icon> 学生</span></template>
        </el-tab-pane>
      </el-tabs>

      <!-- 搜索工具栏 -->
      <div class="search-toolbar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索学号 / 昵称"
          clearable
          style="width: 240px"
          @input="filterUsers"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <span class="count-info">共 {{ filteredUsers.length }} 人</span>
        <el-button text type="primary" @click="resetFilter">重置</el-button>
      </div>

      <!-- 用户表格 -->
      <el-table :data="filteredUsers" stripe style="width:100%" v-loading="loading" :row-class-name="tableRowClass">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" :label="activeTab === 'student' ? '学号' : '用户名'" width="140">
          <template #default="{ row }">
            <span class="username-cell">{{ activeTab === 'student' ? (row.studentId || row.username) : row.username }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" width="120">
          <template #default="{ row }">
            <span class="nickname-cell">{{ row.nickname || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="班级/院系" width="150">
          <template #default="{ row }">
            <span>{{ row.className || row.department || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'disabled' ? 'danger' : 'success'" size="small" effect="dark">
              {{ row.status === 'disabled' ? '禁用' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">
            <span class="time-cell">{{ row.createdAt?.substring(0, 19) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="角色权限" width="120">
          <template #default="{ row }">
            <el-select
              v-model="row.role"
              size="small"
              style="width: 100px"
              @change="changeRole(row)"
              :disabled="row.id === 1"
            >
              <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 'disabled' ? 'success' : 'warning'" @click="toggleStatus(row)">
              {{ row.status === 'disabled' ? '启用' : '禁用' }}
            </el-button>
            <el-popconfirm
              :title="'确定删除用户「' + (row.studentId || row.username) + '」？此操作不可恢复'"
              @confirm="delUser(row)"
              :disabled="row.id === 1"
            >
              <template #reference>
                <el-button link type="danger" :disabled="row.id === 1">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '✏️ 编辑用户' : '➕ 新增用户'" width="500px" destroy-on-close>
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" placeholder="登录账号" :disabled="isEdit" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="form.role !== 'student'" label="手机号">
              <el-input v-model="form.phone" placeholder="请输入手机号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="form.nickname" placeholder="显示名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="isEdit ? '新密码' : '密码'" :prop="isEdit ? '' : 'password'">
              <el-input v-model="form.password" :placeholder="isEdit ? '留空不修改' : '初始密码'" show-password />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="角色" prop="role">
              <el-select v-model="form.role" style="width:100%" :disabled="isEdit">
                <el-option label="🧑‍💻 管理员" value="admin" />
                <el-option label="📖 教师" value="teacher" />
                <el-option label="🎓 学生" value="student" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班级/院系">
              <el-input v-model="form.className" :placeholder="form.role === 'student' ? '所属班级' : '所属院系'" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="form.role === 'student'" label="学号" prop="studentId">
          <el-input v-model="form.studentId" placeholder="学生学号" />
        </el-form-item>
        <el-form-item v-if="form.role === 'teacher' || form.role === 'admin'" label="部门">
          <el-input v-model="form.department" placeholder="所属部门" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="active">正常</el-radio>
            <el-radio value="disabled">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="submitting">确认保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="importDialogVisible" title="📥 批量导入用户" width="540px" destroy-on-close>
      <div class="import-tip">
        <el-alert type="info" :closable="false">
          <template #title>
            请上传 <b>Excel</b> 或 <b>CSV</b> 文件，每行一个用户，必填列：
            <code>username</code>、<code>password</code>、<code>role</code>（admin/teacher/student），
            可选：<code>studentId</code>、<code>nickname</code>、<code>className</code>、<code>department</code>
          </template>
        </el-alert>
        <div class="import-example">
          <p>📋 示例格式（请删除此行后上传）：</p>
          <table class="example-table">
            <tr><th>username</th><th>password</th><th>role</th><th>studentId</th><th>nickname</th><th>className</th><th>department</th></tr>
            <tr><td>zhangsan</td><td>123456</td><td>student</td><td>2024001</td><td>张三</td><td>计算机24级1班</td><td></td></tr>
            <tr><td>liteacher</td><td>123456</td><td>teacher</td><td></td><td>李老师</td><td></td><td>计算机学院</td></tr>
          </table>
        </div>
        <el-button type="text" @click="downloadTemplate">📄 下载导入模板</el-button>
      </div>

      <el-divider />

      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls,.csv"
        :on-change="handleFileChange"
        :file-list="fileList"
        drag
      >
        <el-icon><Upload /></el-icon>
        <div>拖拽文件到此处，或 <em>点击上传</em></div>
        <template #tip>
          <div class="upload-tip">支持 .xlsx .xls .csv 格式，建议不超过 500 条</div>
        </template>
      </el-upload>

      <!-- 导入预览 -->
      <div v-if="previewData.length" class="import-preview">
        <p class="preview-title">📋 预览（共 {{ previewData.length }} 条）：</p>
        <el-table :data="previewData.slice(0, 5)" size="small" border max-height="200">
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="password" label="密码" />
          <el-table-column prop="role" label="角色" />
          <el-table-column prop="studentId" label="学号" />
          <el-table-column prop="nickname" label="昵称" />
          <el-table-column prop="className" label="班级" />
        </el-table>
        <p v-if="previewData.length > 5" class="preview-more">…还有 {{ previewData.length - 5 }} 条</p>
      </div>

      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button @click="previewFile" :disabled="!selectedFile" :loading="previewing">预览</el-button>
        <el-button type="primary" @click="confirmImport" :disabled="!previewData.length" :loading="importing">
          确认导入 {{ previewData.length }} 条
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus, Avatar, User, Reading, Upload, Download, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from '../../api'

const users = ref([])
const activeTab = ref('admin')
const loading = ref(false)
const searchKeyword = ref('')
const dialogVisible = ref(false)
const importDialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const submitting = ref(false)
const exporting = ref(false)
const previewing = ref(false)
const importing = ref(false)
const selectedFile = ref(null)
const previewData = ref([])
const fileList = ref([])
const formRef = ref(null)

const roleOptions = [
  { value: 'admin', label: '管理员' },
  { value: 'teacher', label: '教师' },
  { value: 'student', label: '学生' },
]

const activeTabName = computed(() => {
  return { admin: '管理员', teacher: '教师', student: '学生' }[activeTab.value]
})

const form = reactive({
  username: '', phone: '', password: '', nickname: '',
  role: 'student', studentId: '', className: '', department: '', status: 'active'
})

const formRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const filteredUsers = computed(() => {
  const kw = searchKeyword.value.toLowerCase()
  return users.value.filter(u => u.role === activeTab.value).filter(u => {
    if (!kw) return true
    return (u.studentId || '').toLowerCase().includes(kw)
        || (u.username || '').toLowerCase().includes(kw)
        || (u.nickname || '').toLowerCase().includes(kw)
  })
})

function tableRowClass({ row }) {
  if (row.status === 'disabled') return 'row-disabled'
  return ''
}

async function loadUsers() {
  loading.value = true
  try {
    const res = await api.get('/admin/users')
    users.value = res.data || []
  } catch {
    users.value = []
  } finally {
    loading.value = false
  }
}

function filterUsers() {}

function resetFilter() {
  searchKeyword.value = ''
}

function openAdd() {
  isEdit.value = false
  editId.value = null
  Object.assign(form, {
    username: '', phone: '', password: '123456', nickname: '',
    role: 'student', studentId: '', className: '', department: '', status: 'active'
  })
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  editId.value = row.id
  Object.assign(form, {
    username: row.username, phone: row.phone || '', password: '',
    nickname: row.nickname || '', role: row.role,
    studentId: row.studentId || '', className: row.className || '',
    department: row.department || '', status: row.status || 'active'
  })
  dialogVisible.value = true
}

async function submit() {
  await formRef.value?.validate().catch(() => null)
  if (!form.username) return ElMessage.warning('请输入用户名')
  if (!isEdit.value && !form.password) return ElMessage.warning('请输入密码')

  submitting.value = true
  try {
    const body = { ...form }
    if (isEdit.value && !body.password) delete body.password

    if (isEdit.value) {
      await api.put('/admin/user/' + editId.value, body)
      ElMessage.success('✅ 修改成功')
    } else {
      await api.post('/admin/user', body)
      ElMessage.success('✅ 创建成功')
    }
    dialogVisible.value = false
    await loadUsers()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function changeRole(row) {
  if (row.id === 1) { ElMessage.warning('无法修改超级管理员角色'); return }
  try {
    await api.put('/admin/user/' + row.id, { role: row.role })
    ElMessage.success('角色已更新')
  } catch {
    ElMessage.error('更新失败')
    await loadUsers()
  }
}

async function toggleStatus(row) {
  const newStatus = row.status === 'disabled' ? 'active' : 'disabled'
  try {
    await api.put('/admin/user/' + row.id, { status: newStatus })
    ElMessage.success(newStatus === 'active' ? '已启用' : '已禁用')
    await loadUsers()
  } catch {
    ElMessage.error('操作失败')
  }
}

async function delUser(row) {
  if (row.id === 1) return ElMessage.warning('无法删除超级管理员')
  try {
    await api.delete('/admin/user/' + row.id)
    ElMessage.success('已删除')
    await loadUsers()
  } catch {
    ElMessage.error('删除失败')
  }
}

// 批量导入
function openBatchImport() {
  selectedFile.value = null
  previewData.value = []
  fileList.value = []
  importDialogVisible.value = true
}

function handleFileChange(file) {
  selectedFile.value = file.raw
  previewData.value = []
}

async function previewFile() {
  if (!selectedFile.value) return
  previewing.value = true
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    const res = await api.post('/admin/users/preview', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    previewData.value = res.data || []
    if (!previewData.value.length) ElMessage.warning('未识别到有效数据')
  } catch {
    // 预览失败时用模拟数据
    previewData.value = [
      { username: 'test001', password: '123456', role: 'student', nickname: '测试学生', className: '计算机24级1班' },
      { username: 'test002', password: '123456', role: 'teacher', nickname: '测试教师', department: '计算机学院' },
    ]
    ElMessage.warning('预览接口未就绪，已显示示例数据')
  } finally {
    previewing.value = false
  }
}

async function confirmImport() {
  if (!previewData.value.length) return
  importing.value = true
  try {
    const res = await api.post('/admin/users/batch', { users: previewData.value })
    ElMessage.success(res.data?.message || `成功导入 ${previewData.value.length} 个用户`)
    importDialogVisible.value = false
    await loadUsers()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '批量导入失败')
  } finally {
    importing.value = false
  }
}

function downloadTemplate() {
  const csv = 'username,password,role,studentId,nickname,className,department\n' +
    'zhangsan,123456,student,2024001,张三,计算机24级1班,\n' +
    'liteacher,123456,teacher,,李老师,,计算机学院\n' +
    'admin01,123456,admin,,管理员,,信息中心'
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '用户导入模板.csv'
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('模板已下载')
}

// 导出
async function exportUsers() {
  exporting.value = true
  try {
    const role = activeTab.value
    const res = await api.get('/admin/users/export', {
      params: { role },
      responseType: 'blob'
    })
    const blob = new Blob([res.data], { type: 'application/vnd.ms-excel;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${activeTabName.value}列表_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

onMounted(loadUsers)
</script>

<style scoped>
.users-page { padding: 0; }
.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px; flex-wrap: wrap; gap: 12px;
}
.page-header h2 { margin: 0; font-size: 20px; font-weight: 600; color: #1d2129; }
.header-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.main-card { border-radius: 8px; }
.main-card :deep(.el-card__body) { padding: 0 0 16px 0; }
.main-card :deep(.el-tabs__header) {
  padding: 0 20px; margin: 0; background: #f7f8fa;
  border-radius: 8px 8px 0 0;
  border-bottom: 1px solid #e5e6eb;
}
.main-card :deep(.el-tabs__nav-wrap::after) { display: none; }
.main-card :deep(.el-tabs__content) { padding: 0 20px; }
.search-toolbar {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 0;
}
.count-info { color: #86909c; font-size: 13px; }
.username-cell { font-weight: 600; color: #1890ff; }
.nickname-cell { color: #262626; }
.time-cell { color: #909399; font-size: 12px; }
:deep(.row-disabled) { opacity: 0.55; background-color: #fafafa; }

.import-tip { margin-bottom: 16px; }
.import-example { margin-top: 12px; }
.import-example p { font-size: 13px; color: #606266; margin-bottom: 8px; }
.example-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.example-table th, .example-table td {
  border: 1px solid #dcdfe6; padding: 6px 8px; text-align: left;
}
.example-table th { background: #f5f7fa; font-weight: 600; color: #606266; }
.upload-tip { color: #909399; font-size: 12px; margin-top: 6px; }
.import-preview { margin-top: 12px; }
.preview-title { font-size: 13px; color: #606266; margin-bottom: 8px; }
.preview-more { font-size: 12px; color: #909399; margin-top: 4px; }
</style>