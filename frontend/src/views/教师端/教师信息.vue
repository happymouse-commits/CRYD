<template>
  <div class="info-page">
    <div class="page-header">
      <h2>👤 个人信息</h2>
      <p class="subtitle">修改密码、绑定手机号和姓名</p>
    </div>

    <el-card shadow="hover" style="max-width:600px">
      <el-form :model="infoForm" label-width="100px" v-loading="loading">
        <el-form-item label="账号">
          <el-input :value="store.username" disabled />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="infoForm.nickname" placeholder="输入姓名" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="infoForm.phone" placeholder="输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveInfo" :loading="savingInfo">保存信息</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="max-width:600px;margin-top:20px">
      <template #header>修改密码</template>
      <el-form :model="pwdForm" label-width="100px">
        <el-form-item label="原密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="输入原密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="输入新密码" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="changePassword" :loading="changingPwd">修改密码</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import api from '../../api'

const store = useUserStore()
const loading = ref(false)
const savingInfo = ref(false)
const changingPwd = ref(false)

const infoForm = reactive({ nickname: '', phone: '' })
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

async function loadInfo() {
  loading.value = true
  try {
    const res = await api.get('/teacher/' + store.userId + '/info')
    if (res.data) {
      infoForm.nickname = res.data.nickname || ''
      infoForm.phone = res.data.phone || ''
    }
  } catch {}
  loading.value = false
}

async function saveInfo() {
  savingInfo.value = true
  try {
    await api.post('/teacher/' + store.userId + '/info', {
      nickname: infoForm.nickname,
      phone: infoForm.phone
    })
    ElMessage.success('保存成功')
    // 更新本地store
    store.nickname = infoForm.nickname
    localStorage.setItem('cryd_nickname', infoForm.nickname)
  } catch { ElMessage.error('保存失败') }
  savingInfo.value = false
}

async function changePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) return ElMessage.warning('请填写密码')
  if (pwdForm.newPassword !== pwdForm.confirmPassword) return ElMessage.warning('两次密码不一致')
  if (pwdForm.newPassword.length < 6) return ElMessage.warning('密码至少6位')
  changingPwd.value = true
  try {
    const res = await api.post('/teacher/' + store.userId + '/password', {
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    if (res.code === 400) { ElMessage.error(res.message || '原密码错误'); return }
    ElMessage.success('密码修改成功')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
  } catch { ElMessage.error('修改失败') }
  changingPwd.value = false
}

onMounted(() => {
  infoForm.nickname = store.nickname || ''
  infoForm.phone = ''
  loadInfo()
})
</script>

<style scoped>
.info-page { padding: 0; }
.page-header { margin-bottom: 20px; }
.page-header h2 { margin: 0 0 4px 0; }
.subtitle { color: #909399; font-size: 14px; margin: 0; }
</style>