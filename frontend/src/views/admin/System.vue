<template>
  <el-card header="系统状态">
    <div v-if="status">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-card shadow="hover" header="CPU">
            <el-descriptions :column="1">
              <el-descriptions-item label="处理器数">{{ status.cpu?.availableProcessors }}</el-descriptions-item>
              <el-descriptions-item label="系统负载">{{ status.cpu?.systemLoadAverage?.toFixed(2) }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="hover" header="内存">
            <el-descriptions :column="1">
              <el-descriptions-item label="已用">{{ status.memory?.heapUsedMB }} MB</el-descriptions-item>
              <el-descriptions-item label="最大">{{ status.memory?.heapMaxMB }} MB</el-descriptions-item>
              <el-descriptions-item label="使用率">
                <el-progress :percentage="status.memory?.heapUsagePercent" :color="status.memory?.heapUsagePercent > 85 ? '#F56C6C' : '#67C23A'" />
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
      </el-row>
      <el-row :gutter="16" style="margin-top:16px">
        <el-col :span="12">
          <el-card shadow="hover" header="JVM">
            <el-descriptions :column="1">
              <el-descriptions-item label="Java版本">{{ status.jvm?.javaVersion }}</el-descriptions-item>
              <el-descriptions-item label="Spring Boot">{{ status.jvm?.springBoot }}</el-descriptions-item>
              <el-descriptions-item label="运行时间">{{ status.jvm?.uptimeHours }} 小时</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="hover" header="业务统计">
            <el-descriptions :column="1">
              <el-descriptions-item label="学生数">{{ status.statistics?.totalStudents }}</el-descriptions-item>
              <el-descriptions-item label="教师数">{{ status.statistics?.totalTeachers }}</el-descriptions-item>
              <el-descriptions-item label="课程数">{{ status.statistics?.totalCourses }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
      </el-row>
      <div style="margin-top:16px;text-align:center">
        <el-tag :type="status.status === 'healthy' ? 'success' : 'warning'" size="large">
          系统状态：{{ status.status === 'healthy' ? '正常' : '警告' }}
        </el-tag>
        <span style="margin-left:16px;color:#909399;font-size:12px">{{ status.timestamp }}</span>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../../api'
const status = ref(null)
onMounted(async () => {
  try { const res = await api.get('/admin/server-status'); status.value = res.data } catch (e) {}
})
</script>
