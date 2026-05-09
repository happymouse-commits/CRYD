<template>
  <el-card header="学习画像">
    <div v-if="profile">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="昵称">{{ profile.nickname }}</el-descriptions-item>
        <el-descriptions-item label="知识水平">
          <el-progress :percentage="profile.knowledgeLevel || 0" :color="levelColor" />
        </el-descriptions-item>
        <el-descriptions-item label="认知风格">
          <el-tag>{{ styleMap[profile.cognitiveStyle] || profile.cognitiveStyle }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="学习偏好">
          <el-tag type="success">{{ prefMap[profile.learningPreference] || profile.learningPreference }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="学习节奏">{{ paceMap[profile.learningPace] || profile.learningPace }}</el-descriptions-item>
        <el-descriptions-item label="学习进度">
          <el-progress :percentage="profile.progress || 0" />
        </el-descriptions-item>
      </el-descriptions>
    </div>
    <el-empty v-else description="暂无画像数据，请先使用AI辅导" />
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '../../store/user'
import api from '../../api'
const store = useUserStore()
const profile = ref(null)
const styleMap = { visual: '视觉型', auditory: '听觉型', kinesthetic: '动觉型', reading: '阅读型' }
const prefMap = { mixed: '混合', video: '视频', text: '文本', practice: '实践' }
const paceMap = { steady: '稳步', fast: '快速', slow: '慢速' }
const levelColor = computed(() => {
  const l = profile.value?.knowledgeLevel || 0
  return l < 30 ? '#F56C6C' : l < 70 ? '#E6A23C' : '#67C23A'
})
onMounted(async () => {
  try { const res = await api.get('/student/' + store.userId); profile.value = res.data } catch (e) {}
})
</script>
