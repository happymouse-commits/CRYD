<template>
  <el-card header="学业预警">
    <el-input v-model="className" placeholder="输入班级名称" style="width:300px;margin-bottom:16px">
      <template #append><el-button @click="loadWarnings">查询</el-button></template>
    </el-input>
    <el-empty v-if="!warnings.length" description="输入班级查看预警学生" />
    <el-table :data="warnings" stripe v-else>
      <el-table-column prop="nickname" label="姓名" width="120" />
      <el-table-column prop="knowledgeLevel" label="知识水平" width="120">
        <template #default="{ row }"><el-progress :percentage="row.knowledgeLevel" :color="row.knowledgeLevel < 15 ? '#F56C6C' : '#E6A23C'" /></template>
      </el-table-column>
      <el-table-column prop="warningLevel" label="预警等级" width="100">
        <template #default="{ row }"><el-tag :type="row.warningLevel === 'high' ? 'danger' : 'warning'">{{ row.warningLevel === 'high' ? '高危' : '中等' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="suggestion" label="建议" />
    </el-table>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import api from '../../api'
const className = ref('')
const warnings = ref([])
async function loadWarnings() {
  if (!className.value) return
  try { const res = await api.get('/counselor/warning/' + className.value); warnings.value = res.data || [] } catch (e) {}
}
</script>
