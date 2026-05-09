<template>
  <el-card header="作业管理">
    <el-button type="primary" @click="showAdd = true" style="margin-bottom:16px">布置作业</el-button>
    <el-table :data="assignments" stripe>
      <el-table-column prop="title" label="作业标题" />
      <el-table-column prop="difficulty" label="难度" width="100">
        <template #default="{ row }">
          <el-tag :type="diffMap[row.difficulty]">{{ diffLabel[row.difficulty] || row.difficulty }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deadline" label="截止日期" width="120" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push('/teacher/submissions/' + row.id)">查看提交</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="showAdd" title="布置作业" width="500px">
      <el-form :model="aForm">
        <el-form-item label="标题"><el-input v-model="aForm.title" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="aForm.content" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="要求"><el-input v-model="aForm.requirements" type="textarea" /></el-form-item>
        <el-form-item label="难度">
          <el-select v-model="aForm.difficulty"><el-option label="简单" value="easy" /><el-option label="中等" value="medium" /><el-option label="困难" value="hard" /></el-select>
        </el-form-item>
        <el-form-item label="截止日期"><el-input v-model="aForm.deadline" placeholder="2026-05-30" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" @click="addAssignment">发布</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useUserStore } from '../../store/user'
import api from '../../api'
import { ElMessage } from 'element-plus'
const store = useUserStore()
const assignments = ref([])
const showAdd = ref(false)
const aForm = reactive({ title: '', content: '', requirements: '', difficulty: 'medium', deadline: '' })
const diffMap = { easy: 'success', medium: 'warning', hard: 'danger' }
const diffLabel = { easy: '简单', medium: '中等', hard: '困难' }

async function loadAssignments() {
  try { const res = await api.get('/teacher/' + store.userId + '/assignments'); assignments.value = res.data || [] } catch (e) {}
}

async function addAssignment() {
  if (!aForm.title) return ElMessage.warning('请输入标题')
  try {
    await api.post('/teacher/assignment', { ...aForm, teacherId: store.userId, courseId: '1' })
    ElMessage.success('发布成功')
    showAdd.value = false
    loadAssignments()
  } catch (e) {}
}

onMounted(loadAssignments)
</script>
