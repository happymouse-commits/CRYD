<template>
  <el-card header="班级画像">
    <el-input v-model="className" placeholder="输入班级名称" style="width:300px;margin-bottom:16px">
      <template #append><el-button @click="loadData">查询</el-button></template>
    </el-input>
    <el-row :gutter="16" v-if="overview">
      <el-col :span="6"><el-statistic title="班级人数" :value="overview.totalStudents" /></el-col>
      <el-col :span="6"><el-statistic title="平均知识水平" :value="overview.avgKnowledgeLevel" /></el-col>
      <el-col :span="6"><el-statistic title="预警人数" :value="overview.warningCount" /></el-col>
      <el-col :span="6">
        <div class="stat-title">认知风格分布</div>
        <div v-for="(v, k) in overview.styleDistribution" :key="k" class="style-item">
          <el-tag size="small">{{ styleMap[k] || k }}</el-tag> {{ v }}人
        </div>
      </el-col>
    </el-row>
    <el-divider v-if="students.length" />
    <el-table :data="students" stripe v-if="students.length">
      <el-table-column prop="nickname" label="姓名" />
      <el-table-column prop="knowledgeLevel" label="知识水平">
        <template #default="{ row }"><el-progress :percentage="row.knowledgeLevel || 0" /></template>
      </el-table-column>
      <el-table-column prop="cognitiveStyle" label="认知风格">
        <template #default="{ row }"><el-tag>{{ styleMap[row.cognitiveStyle] || row.cognitiveStyle }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="learningPreference" label="学习偏好" />
    </el-table>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import api from '../../api'
const className = ref('')
const students = ref([])
const overview = ref(null)
const styleMap = { visual: '视觉型', auditory: '听觉型', kinesthetic: '动觉型', reading: '阅读型' }

async function loadData() {
  if (!className.value) return
  try {
    const [oRes, pRes] = await Promise.all([
      api.get('/counselor/class/' + className.value + '/analysis'),
      api.get('/counselor/class/' + className.value + '/profiles')
    ])
    overview.value = oRes.data
    students.value = pRes.data || []
  } catch (e) {}
}
</script>

<style scoped>
.stat-title { font-size: 12px; color: #909399; margin-bottom: 8px; }
.style-item { font-size: 13px; margin: 4px 0; }
</style>
