<template>
  <el-card header="学习路径">
    <div v-if="path">
      <el-steps :active="currentStep" align-center>
        <el-step v-for="(step, i) in steps" :key="i" :title="step.title" :description="step.description" />
      </el-steps>
      <el-divider />
      <el-timeline>
        <el-timeline-item v-for="(step, i) in steps" :key="i" :type="i < currentStep ? 'success' : i === currentStep ? 'primary' : 'info'">
          <h4>{{ step.title }}</h4>
          <p>{{ step.description }}</p>
          <div v-if="step.resources?.length">
            <el-tag v-for="r in step.resources" :key="r" size="small" style="margin:2px">{{ r }}</el-tag>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>
    <el-empty v-else description="暂无学习路径，请先使用AI辅导" />
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '../../store/user'
import api from '../../api'
const store = useUserStore()
const path = ref(null)
const steps = computed(() => {
  try { return JSON.parse(path.value?.steps || '[]') } catch { return [] }
})
const currentStep = computed(() => path.value?.currentStep || 0)
onMounted(async () => {
  try { const res = await api.get('/learning/path/' + store.userId); path.value = res.data } catch (e) {}
})
</script>
