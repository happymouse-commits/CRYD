<template>
  <el-card :header="'作业提交 - ' + assignmentId">
    <el-table :data="submissions" stripe>
      <el-table-column prop="studentId" label="学生ID" width="100" />
      <el-table-column prop="content" label="提交内容" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'graded' ? 'success' : 'info'">{{ row.status === 'graded' ? '已批' : '待批' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="score" label="分数" width="80" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="openGrade(row)" v-if="row.status !== 'graded'">批改</el-button>
          <span v-else>{{ row.feedback }}</span>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="showGrade" title="批改" width="400px">
      <el-form :model="gradeForm">
        <el-form-item label="分数"><el-input-number v-model="gradeForm.score" :min="0" :max="100" /></el-form-item>
        <el-form-item label="评语"><el-input v-model="gradeForm.feedback" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGrade = false">取消</el-button>
        <el-button type="primary" @click="submitGrade">提交</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '../../api'
import { ElMessage } from 'element-plus'
const route = useRoute()
const assignmentId = route.params.id
const submissions = ref([])
const showGrade = ref(false)
const gradeForm = reactive({ id: null, score: 80, feedback: '' })

async function loadSubmissions() {
  try { const res = await api.get('/teacher/assignment/' + assignmentId + '/submissions'); submissions.value = res.data || [] } catch (e) {}
}

function openGrade(row) {
  gradeForm.id = row.id
  gradeForm.score = 80
  gradeForm.feedback = ''
  showGrade.value = true
}

async function submitGrade() {
  try {
    await api.post('/teacher/submission/' + gradeForm.id + '/grade', { score: gradeForm.score, feedback: gradeForm.feedback, gradedBy: 'teacher' })
    ElMessage.success('批改成功')
    showGrade.value = false
    loadSubmissions()
  } catch (e) {}
}

onMounted(loadSubmissions)
</script>
