<template>
  <el-card header="AI学情分析">
    <el-input v-model="className" placeholder="输入班级名称查询" style="width:300px;margin-bottom:16px">
      <template #append><el-button @click="loadAnalysis">查询</el-button></template>
    </el-input>
    <el-empty v-if="!analyses.length" description="输入班级名称查看AI分析" />
    <el-table :data="analyses" stripe v-else>
      <el-table-column prop="studentId" label="学生ID" width="100" />
      <el-table-column prop="type" label="分析类型" width="120" />
      <el-table-column prop="content" label="分析内容" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import api from '../../api'
const className = ref('')
const analyses = ref([])
function formatTime(t) { return t ? t.replace('T', ' ').substring(0, 16) : '' }
async function loadAnalysis() {
  if (!className.value) return
  try { const res = await api.get('/teacher/class/' + className.value + '/analysis'); analyses.value = res.data || [] } catch (e) {}
}
</script>
