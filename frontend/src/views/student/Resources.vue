<template>
  <el-card header="学习资源">
    <el-tabs v-model="activeType">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="练习题" name="exercise" />
      <el-tab-pane label="知识点讲解" name="explanation" />
      <el-tab-pane label="学习计划" name="plan" />
      <el-tab-pane label="思维导图" name="mindmap" />
    </el-tabs>
    <el-empty v-if="!resources.length" description="暂无资源，使用AI辅导生成个性化资源" />
    <el-row :gutter="16">
      <el-col :span="8" v-for="r in filteredResources" :key="r.id">
        <el-card shadow="hover" class="resource-card">
          <template #header>
            <el-tag :type="typeTagMap[r.type] || 'info'" size="small">{{ typeMap[r.type] || r.type }}</el-tag>
          </template>
          <div class="resource-content">{{ r.content?.substring(0, 120) }}...</div>
          <div class="resource-meta">{{ r.knowledgePoint }} · {{ formatTime(r.createdAt) }}</div>
        </el-card>
      </el-col>
    </el-row>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '../../store/user'
import api from '../../api'
const store = useUserStore()
const resources = ref([])
const activeType = ref('all')
const typeMap = { exercise: '练习题', explanation: '知识讲解', plan: '学习计划', mindmap: '思维导图', video: '视频', article: '文章' }
const typeTagMap = { exercise: 'warning', explanation: 'success', plan: 'primary', mindmap: 'danger' }
const filteredResources = computed(() => activeType.value === 'all' ? resources.value : resources.value.filter(r => r.type === activeType.value))
function formatTime(t) { return t ? t.replace('T', ' ').substring(0, 10) : '' }
onMounted(async () => {
  try { const res = await api.get('/learning/resources/' + store.userId); resources.value = res.data || [] } catch (e) {}
})
</script>

<style scoped>
.resource-card { margin-bottom: 16px; }
.resource-content { font-size: 13px; color: #606266; max-height: 80px; overflow: hidden; }
.resource-meta { font-size: 12px; color: #c0c4cc; margin-top: 8px; }
</style>
