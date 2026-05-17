<template>
  <el-card header="课程管理">
    <el-button type="primary" @click="showAdd = true" style="margin-bottom:16px">新建课程</el-button>
    <el-table :data="courses" stripe>
      <el-table-column prop="name" label="课程名" />
      <el-table-column prop="code" label="课程代码" />
      <el-table-column prop="className" label="班级" />
      <el-table-column prop="semester" label="学期" />
    </el-table>
    <el-dialog v-model="showAdd" title="新建课程" width="500px">
      <el-form :model="courseForm">
        <el-form-item label="课程名"><el-input v-model="courseForm.name" /></el-form-item>
        <el-form-item label="课程代码"><el-input v-model="courseForm.code" /></el-form-item>
        <el-form-item label="班级"><el-input v-model="courseForm.className" /></el-form-item>
        <el-form-item label="学期"><el-input v-model="courseForm.semester" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="courseForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" @click="addCourse">创建</el-button>
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
const courses = ref([])
const showAdd = ref(false)
const courseForm = reactive({ name: '', code: '', className: '', semester: '2025-2026-2', description: '' })

async function loadCourses() {
  try { const res = await api.get('/teacher/' + store.userId + '/courses'); courses.value = res.data || [] } catch (e) {}
}

async function addCourse() {
  if (!courseForm.name) return ElMessage.warning('请输入课程名')
  try {
    await api.post('/teacher/course', { ...courseForm, teacherId: store.userId })
    ElMessage.success('创建成功')
    showAdd.value = false
    loadCourses()
  } catch (e) {}
}

onMounted(loadCourses)
</script>
