<template>
  <div class="login-page">
    <!-- ===== LEFT: Purple Panel ===== -->
    <div class="left-panel">
      <!-- Grid overlay -->
      <div class="grid-overlay"></div>
      <!-- Decorative blurs -->
      <div class="blur-circle blur-1"></div>
      <div class="blur-circle blur-2"></div>

      <!-- Top: Branding -->
      <div class="brand-area">
        <div class="brand-icon">🎓</div>
        <span class="brand-name">CRYD</span>
      </div>

      <!-- Middle: Animated Characters -->
      <div class="characters-area">
        <AnimatedCharacters
          :isTyping="isTyping"
          :passwordLength="form.password.length"
          :showPassword="showPassword"
        />
      </div>

      <!-- Bottom: Links -->
      <div class="footer-links">
        <a href="#">隐私政策</a>
        <a href="#">服务条款</a>
        <a href="#">联系我们</a>
      </div>
    </div>

    <!-- ===== RIGHT: Login Form Panel ===== -->
    <div class="right-panel">
      <!-- Glass Card -->
      <div class="glass-card">
        <div class="logo-area">
          <h1 class="title">Welcome back!</h1>
          <p class="subtitle">基于大模型的个性化学习多智能体系统</p>
        </div>

        <el-form :model="form" @keyup.enter="handleLogin" class="login-form">
          <el-form-item>
            <el-input
              v-model="form.username"
              placeholder="用户名"
              prefix-icon="User"
              size="large"
              class="glass-input"
              @focus="isTyping = true"
              @blur="isTyping = false"
            />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="密码"
              prefix-icon="Lock"
              size="large"
              class="glass-input"
            >
              <template #suffix>
                <el-icon
                  @click.stop="showPassword = !showPassword"
                  class="pwd-toggle"
                >
                  <View v-if="showPassword" />
                  <Hide v-else />
                </el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              size="large"
              @click="handleLogin"
              :loading="loading"
              class="login-btn"
            >
              登 录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="demo-section">
          <span class="demo-label">演示账号</span>
          <div class="demo-tags">
            <el-tag @click="fillDemo('student1','123456')" type="success" effect="dark">🎓 学生</el-tag>
            <el-tag @click="fillDemo('teacher1','123456')" type="warning" effect="dark">📖 教师</el-tag>
            <el-tag @click="fillDemo('counselor1','123456')" type="danger" effect="dark">🎧 辅导员</el-tag>
            <el-tag @click="fillDemo('admin1','123456')" type="info" effect="dark">⚙️ 管理员</el-tag>
          </div>
        </div>

        <el-button link type="primary" @click="showRegister = true" class="register-link">
          没有账号？注册
        </el-button>
      </div>
    </div>

    <!-- ===== Register Dialog ===== -->
    <el-dialog v-model="showRegister" title="注册新账号" width="420px" destroy-on-close>
      <el-form :model="regForm" label-width="80px" :rules="regRules" ref="regFormRef">
        <el-form-item label="用户身份" prop="role">
          <el-select v-model="regForm.role" style="width:100%" @change="handleRoleChange">
            <el-option label="🎓 学生" value="student" />
            <el-option label="📖 教师" value="teacher" />
            <el-option label="🎧 辅导员" value="counselor" />
          </el-select>
        </el-form-item>
        <el-form-item label="学号" prop="studentId" v-if="regForm.role === 'student'">
          <el-input v-model="regForm.studentId" placeholder="请输入学号" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="regForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="设置密码" prop="password">
          <el-input v-model="regForm.password" type="password" placeholder="至少6位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="regForm.confirmPassword" type="password" placeholder="请再次输入密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRegister = false">取消</el-button>
        <el-button type="primary" @click="handleRegister" :loading="registerLoading">注册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import api from '../api'
import { ElMessage } from 'element-plus'
import { View, Hide } from '@element-plus/icons-vue'
import AnimatedCharacters from '../components/AnimatedCharacters.vue'

const router = useRouter()
const store = useUserStore()
const loading = ref(false)
const showRegister = ref(false)
const isTyping = ref(false)
const showPassword = ref(false)

const form = reactive({ username: '', password: '' })
const regForm = reactive({
  role: 'student',
  studentId: '',
  phone: '',
  password: '',
  confirmPassword: ''
})

const regFormRef = ref(null)
const registerLoading = ref(false)

const regRules = {
  role: [{ required: true, message: '请选择用户身份', trigger: 'change' }],
  studentId: [
    { required: true, message: '请输入学号', trigger: 'blur' },
    { min: 4, message: '学号至少4位', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { 
      validator: (rule, value, callback) => {
        if (value !== regForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
}

function handleRoleChange() {
  regForm.studentId = ''
}

function fillDemo(u, p) {
  form.username = u
  form.password = p
}

async function handleLogin() {
  if (!form.username || !form.password) return ElMessage.warning('请输入用户名和密码')
  loading.value = true
  try {
    const res = await api.post('/auth/login', form)
    store.setUser(res.data)
    const rolePath = {
      student: '/student/chat',
      teacher: '/teacher/courses',
      counselor: '/counselor/warnings',
      admin: '/admin/dashboard'
    }
    router.push(rolePath[res.data.role] || '/student/chat')
    ElMessage.success('登录成功')
  } catch (e) {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (!regFormRef.value) return
  
  await regFormRef.value.validate(async (valid) => {
    if (!valid) return
    
    registerLoading.value = true
    try {
      const registerData = {
        phone: regForm.phone,
        password: regForm.password,
        role: regForm.role,
        studentId: regForm.role === 'student' ? regForm.studentId : null
      }
      
      await api.post('/auth/register', registerData)
      ElMessage.success('注册成功，请登录')
      form.username = regForm.phone
      form.password = regForm.password
      showRegister.value = false
      
      regForm.role = 'student'
      regForm.studentId = ''
      regForm.phone = ''
      regForm.password = ''
      regForm.confirmPassword = ''
    } catch (e) {
      // error handled by interceptor
    } finally {
      registerLoading.value = false
    }
  })
}
</script>

<style scoped>
/* ===== Page Layout ===== */
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1fr 1fr;
  position: relative;
  overflow: hidden;
}

/* ===== LEFT: Purple Panel ===== */
.left-panel {
  position: relative;
  z-index: 0;
  background: linear-gradient(135deg, #6C3FF5 0%, #5533CC 50%, #4422AA 100%);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: center;
  padding: 40px;
  color: white;
  overflow: hidden;
}

/* Grid overlay */
.grid-overlay {
  position: absolute;
  inset: 0;
  z-index: 0;
  background-image:
    linear-gradient(rgba(255,255,255,0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.05) 1px, transparent 1px);
  background-size: 20px 20px;
  pointer-events: none;
}

/* Decorative blur circles */
.blur-circle {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}
.blur-1 {
  width: 256px;
  height: 256px;
  background: rgba(255,255,255,0.1);
  top: 25%;
  right: 25%;
}
.blur-2 {
  width: 384px;
  height: 384px;
  background: rgba(255,255,255,0.05);
  bottom: 25%;
  left: 25%;
}

/* Branding */
.brand-area {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 8px;
  align-self: flex-start;
}
.brand-icon {
  font-size: 24px;
}
.brand-name {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

/* Characters area */
.characters-area {
  position: relative;
  z-index: 10;
  flex: 1;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 0;
  width: 100%;
}

/* Footer links */
.footer-links {
  position: relative;
  z-index: 10;
  display: flex;
  gap: 24px;
  font-size: 13px;
  opacity: 0.6;
}
.footer-links a {
  color: rgba(255,255,255,0.8);
  text-decoration: none;
  transition: opacity 0.2s;
}
.footer-links a:hover {
  opacity: 1;
  color: white;
}

/* ===== RIGHT: Login Form Panel ===== */
.right-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: linear-gradient(135deg, #0f0c29, #302b63, #24243e);
  position: relative;
  z-index: 0;
}

/* ===== Glass Card ===== */
.glass-card {
  position: relative;
  z-index: 1;
  width: 420px;
  padding: 44px 40px 36px;
  border-radius: 20px;
  background: rgba(255,255,255,0.08);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255,255,255,0.15);
  box-shadow: 0 8px 40px rgba(0,0,0,0.3), inset 0 1px 0 rgba(255,255,255,0.1);
  animation: cardIn 0.6s ease-out;
}

@keyframes cardIn {
  from { opacity: 0; transform: translateY(30px) scale(0.95); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

/* Logo area */
.logo-area { text-align: center; margin-bottom: 28px; }
.title {
  font-size: 30px; font-weight: 700; margin: 0 0 8px;
  background: linear-gradient(135deg, #a8c0ff, #f093fb);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
  background-clip: text;
}
.subtitle { color: rgba(255,255,255,0.45); margin: 0; font-size: 13px; letter-spacing: 0.5px; }

/* Input glass styling */
.glass-input :deep(.el-input__wrapper) {
  background: rgba(255,255,255,0.08) !important;
  border: 1px solid rgba(255,255,255,0.12) !important;
  box-shadow: none !important;
  border-radius: 10px;
  transition: all 0.3s;
}
.glass-input :deep(.el-input__wrapper:hover) {
  border-color: rgba(255,255,255,0.25) !important;
  background: rgba(255,255,255,0.12) !important;
}
.glass-input :deep(.el-input__wrapper.is-focus) {
  border-color: rgba(168,192,255,0.6) !important;
  background: rgba(255,255,255,0.12) !important;
  box-shadow: 0 0 0 2px rgba(168,192,255,0.15) !important;
}
.glass-input :deep(.el-input__inner) { color: #fff !important; }
.glass-input :deep(.el-input__inner::placeholder) { color: rgba(255,255,255,0.35) !important; }
.glass-input :deep(.el-input__prefix) { color: rgba(255,255,255,0.4) !important; }

/* Password toggle icon */
.pwd-toggle {
  color: rgba(255,255,255,0.4);
  cursor: pointer;
  font-size: 16px;
  transition: color 0.2s;
}
.pwd-toggle:hover {
  color: rgba(255,255,255,0.7);
}

/* Login button */
.login-btn {
  width: 100%;
  height: 46px;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 4px;
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
  transition: transform 0.2s, box-shadow 0.2s;
}
.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 24px rgba(102,126,234,0.4);
}

/* Demo accounts */
.demo-section { text-align: center; margin-top: 20px; }
.demo-label { color: rgba(255,255,255,0.4); font-size: 12px; display: block; margin-bottom: 10px; }
.demo-tags { display: flex; justify-content: center; gap: 8px; flex-wrap: wrap; }
.demo-tags :deep(.el-tag) { cursor: pointer; transition: transform 0.2s; }
.demo-tags :deep(.el-tag:hover) { transform: translateY(-2px); }

/* Register link */
.register-link { display: block; margin: 16px auto 0; font-size: 13px; }

/* ===== Responsive ===== */
@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }
  .left-panel {
    display: none;
  }
  .right-panel {
    padding: 24px;
  }
}
</style>