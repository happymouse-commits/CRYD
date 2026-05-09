<template>
  <div class="users-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <h2>用户管理</h2>
      <el-button type="primary" @click="openAdd">
        <el-icon><Plus /></el-icon> 新增用户
      </el-button>
    </div>

    <!-- 标签页 -->
    <el-card class="main-card" shadow="never">
      <el-tabs v-model="activeTab" @tab-change="loadUsers">
        <el-tab-pane label="管理员" name="admin">
          <template #label>
            <span><el-icon><Avatar /></el-icon> 管理员</span>
          </template>
          <p class="tab-desc">系统管理员拥有全部权限，可以新增和管理其他管理员</p>

          <div class="table-toolbar">
            <el-button type="primary" @click="openAddAdmin">+ 添加管理员</el-button>
            <span class="count-info">共 {{ adminUsers.length }} 人</span>
          </div>

          <el-table :data="adminUsers" stripe style="width:100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="username" label="用户名" width="140" />
            <el-table-column prop="nickname" label="昵称" width="140" />
            <el-table-column prop="department" label="部门" width="140" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'disabled' ? 'danger' : 'success'" size="small" effect="dark">
                  {{ row.status === 'disabled' ? '禁用' : '正常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="180">
              <template #default="{ row }">{{ row.createdAt?.substring(0, 16) }}</template>
            </el-table-column>
            <el-table-column label="操作" min-width="180">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="warning" @click="toggleStatus(row)">
                  {{ row.status === 'disabled' ? '启用' : '禁用' }}
                </el-button>
                <el-popconfirm title="确定删除该管理员？" @confirm="delUser(row.id)">
                  <template #reference><el-button link type="danger">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="教师" name="teacher">
          <template #label><span><el-icon><Reading /></el-icon> 教师</span></template>
          <div class="table-toolbar">
            <span class="count-info">共 {{ filteredCurrentUsers.length }} 人</span>
          </div>
          <el-table :data="filteredCurrentUsers" stripe style="width:100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="username" label="用户名" width="140" />
            <el-table-column prop="nickname" label="昵称" width="140" />
            <el-table-column prop="department" label="院系" width="140" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'disabled' ? 'danger' : 'success'" size="small" effect="dark">
                  {{ row.status === 'disabled' ? '禁用' : '正常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="180">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="warning" @click="toggleStatus(row)">
                  {{ row.status === 'disabled' ? '启用' : '禁用' }}
                </el-button>
                <el-popconfirm title="确定删除？" @confirm="delUser(row.id)">
                  <template #reference><el-button link type="danger">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="学生" name="student">
          <template #label><span><el-icon><User /></el-icon> 学生</span></template>
          <div class="table-toolbar">
            <span class="count-info">共 {{ filteredCurrentUsers.length }} 人</span>
          </div>
          <el-table :data="filteredCurrentUsers" stripe style="width:100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="username" label="用户名" width="140" />
            <el-table-column prop="nickname" label="昵称" width="140" />
            <el-table-column prop="className" label="班级" width="140" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'disabled' ? 'danger' : 'success'" size="small" effect="dark">
                  {{ row.status === 'disabled' ? '禁用' : '正常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="180">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="warning" @click="toggleStatus(row)">
                  {{ row.status === 'disabled' ? '启用' : '禁用' }}
                </el-button>
                <el-popconfirm title="确定删除？" @confirm="delUser(row.id)">
                  <template #reference><el-button link type="danger">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="辅导员" name="counselor">
          <template #label><span><el-icon><Headset /></el-icon> 辅导员</span></template>
          <div class="table-toolbar">
            <span class="count-info">共 {{ filteredCurrentUsers.length }} 人</span>
          </div>
          <el-table :data="filteredCurrentUsers" stripe style="width:100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="username" label="用户名" width="140" />
            <el-table-column prop="nickname" label="昵称" width="140" />
            <el-table-column prop="department" label="部门" width="140" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'disabled' ? 'danger' : 'success'" size="small" effect="dark">
                  {{ row.status === 'disabled' ? '禁用' : '正常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="180">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="warning" @click="toggleStatus(row)">
                  {{ row.status === 'disabled' ? '启用' : '禁用' }}
                </el-button>
                <el-popconfirm title="确定删除？" @confirm="delUser(row.id)">
                  <template #reference><el-button link type="danger">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="460px" destroy-on-close>
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名" required><el-input v-model="form.username" placeholder="登录用户名" /></el-form-item>
        <el-form-item label="密码" :required="!isEdit"><el-input v-model="form.password" placeholder="留空则不修改" show-password /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" placeholder="显示名称" /></el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.role" style="width:100%" :disabled="isEdit">
            <el-option label="🧑‍💻 管理员" value="admin" />
            <el-option label="📖 教师" value="teacher" />
            <el-option label="🎓 学生" value="student" />
            <el-option label="🎧 辅导员" value="counselor" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级/院系"><el-input v-model="form.className" :placeholder="form.role === 'teacher' || form.role === 'counselor' ? '所属院系' : '所属班级'" /></el-form-item>
        <el-form-item v-if="form.role === 'admin' || form.role === 'teacher' || form.role === 'counselor'" label="部门">
          <el-input v-model="form.department" placeholder="部门" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="submitting">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus, Avatar, User, Reading, Headset } from '@element-plus/icons-vue'
import api from '../../api'
import { ElMessage } from 'element-plus'

const users = ref([])
const activeTab = ref('admin')
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const submitting = ref(false)

const form = reactive({
  username: '', password: '', nickname: '', role: 'student', className: '', department: ''
})

// 管理员列表（只看admin角色）
const adminUsers = computed(() => users.value.filter(u => u.role === 'admin'))

// 非管理员标签页的当前角色用户
const filteredCurrentUsers = computed(() => users.value.filter(u => u.role === activeTab.value))

async function loadAll() {
  try { const res = await api.get('/admin/users'); users.value = res.data || [] } catch (e) {}
}

const tabRoles = { admin: 'admin', teacher: 'teacher', student: 'student', counselor: 'counselor' }

function openAdd() {
  isEdit.value = false; editId.value = null
  Object.assign(form, { username: '', password: '123456', nickname: '', role: 'student', className: '', department: '' })
  dialogVisible.value = true
}

function openAddAdmin() {
  isEdit.value = false; editId.value = null
  Object.assign(form, { username: '', password: '123456', nickname: '', role: 'admin', className: '', department: '信息中心' })
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true; editId.value = row.id
  Object.assign(form, {
    username: row.username, password: '', nickname: row.nickname || '',
    role: row.role, className: row.className || '', department: row.department || ''
  })
  dialogVisible.value = true
}

async function submit() {
  if (!form.username) return ElMessage.warning('请输入用户名')
  if (!isEdit.value && !form.password) return ElMessage.warning('请输入密码')
  submitting.value = true
  try {
    if (isEdit.value) {
      const body = { nickname: form.nickname, role: form.role, className: form.className, department: form.department }
      if (form.password) body.password = form.password
      await api.put('/admin/user/' + editId.value, body)
      ElMessage.success('修改成功')
    } else {
      await api.post('/admin/user', form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadAll()
  } catch (e) {} finally { submitting.value = false }
}

async function toggleStatus(row) {
  try {
    await api.put('/admin/user/' + row.id, { status: row.status === 'disabled' ? 'active' : 'disabled' })
    ElMessage.success(row.status === 'disabled' ? '已启用' : '已禁用')
    await loadAll()
  } catch (e) {}
}

async function delUser(id) {
  try { await api.delete('/admin/user/' + id); ElMessage.success('已删除'); await loadAll() } catch (e) {}
}

onMounted(loadAll)
</script>

<style scoped>
.users-page { padding: 0; }
.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
}
.page-header h2 { margin: 0; font-size: 20px; font-weight: 600; color: #1d2129; }
.main-card { border-radius: 8px; }
.main-card :deep(.el-card__body) { padding: 0 0 16px 0; }
.main-card :deep(.el-tabs__header) { padding: 0 20px; margin: 0; background: #f7f8fa; border-radius: 8px 8px 0 0; border-bottom: 1px solid #e5e6eb; }
.main-card :deep(.el-tabs__nav-wrap::after) { display: none; }
.main-card :deep(.el-tabs__content) { padding: 0 20px; }
.tab-desc { color: #86909c; font-size: 13px; margin: 0 0 16px; }
.table-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.count-info { color: #86909c; font-size: 13px; }
</style>
