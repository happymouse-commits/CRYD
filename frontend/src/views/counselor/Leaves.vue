<template>
  <el-card header="请假审批">
    <el-radio-group v-model="statusFilter" @change="loadLeaves" style="margin-bottom:16px">
      <el-radio-button label="">全部</el-radio-button>
      <el-radio-button label="pending">待审批</el-radio-button>
      <el-radio-button label="approved">已批准</el-radio-button>
      <el-radio-button label="rejected">已拒绝</el-radio-button>
    </el-radio-group>
    <el-table :data="leaves" stripe>
      <el-table-column prop="studentId" label="学生ID" width="100" />
      <el-table-column prop="startDate" label="开始日期" width="120" />
      <el-table-column prop="endDate" label="结束日期" width="120" />
      <el-table-column prop="reason" label="原因" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]">{{ statusLabel[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <template v-if="row.status === 'pending'">
            <el-button type="success" size="small" @click="approve(row.id, 'approved')">批准</el-button>
            <el-button type="danger" size="small" @click="approve(row.id, 'rejected')">拒绝</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../../api'
import { ElMessage } from 'element-plus'
const leaves = ref([])
const statusFilter = ref('')
const statusMap = { pending: 'warning', approved: 'success', rejected: 'danger' }
const statusLabel = { pending: '待审批', approved: '已批准', rejected: '已拒绝' }

async function loadLeaves() {
  try { const res = await api.get('/counselor/leave-requests', { params: statusFilter.value ? { status: statusFilter.value } : {} }); leaves.value = res.data || [] } catch (e) {}
}

async function approve(id, action) {
  try {
    await api.post('/counselor/leave-request/' + id + '/approve', { action })
    ElMessage.success(action === 'approved' ? '已批准' : '已拒绝')
    loadLeaves()
  } catch (e) {}
}

onMounted(loadLeaves)
</script>
