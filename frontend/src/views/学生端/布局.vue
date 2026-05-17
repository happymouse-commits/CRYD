<template>
  <el-container class="student-layout">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <div class="logo-box" @click="isCollapse = !isCollapse">
        <span class="logo-icon">🎓</span>
        <transition name="fade">
          <span v-show="!isCollapse" class="logo-text">CRYD 学生端</span>
        </transition>
      </div>

      <el-menu
        :default-active="$route.path"
        router
        :collapse="isCollapse"
        background-color="#001529"
        text-color="#ffffffb3"
        active-text-color="#fff"
        class="side-menu"
      >
        <el-menu-item index="/student/home">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/student/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>AI辅导</span>
        </el-menu-item>
        <el-menu-item index="/student/profile-card">
          <el-icon><User /></el-icon>
          <span>我的画像</span>
        </el-menu-item>
        <el-menu-item index="/student/practice">
          <el-icon><EditPen /></el-icon>
          <span>刷题房</span>
        </el-menu-item>
        <el-menu-item index="/student/resources">
          <el-icon><FolderOpened /></el-icon>
          <span>资源中心</span>
        </el-menu-item>
        <el-menu-item index="/student/learning-path">
          <el-icon><MapLocation /></el-icon>
          <span>学习路径</span>
        </el-menu-item>
        <el-menu-item index="/student/evaluation">
          <el-icon><DataLine /></el-icon>
          <span>学习评估</span>
        </el-menu-item>
        <el-menu-item index="/student/my-info">
          <el-icon><Setting /></el-icon>
          <span>个人信息</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="main-area">
      <el-header class="topbar">
        <div class="topbar-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse"><Fold /></el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/student/home' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ pageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="topbar-right">
          <el-tooltip content="全屏" placement="bottom"><el-icon class="top-icon" @click="toggleFullscreen"><FullScreen /></el-icon></el-tooltip>
          <el-dropdown trigger="click">
            <span class="user-area">
              <el-avatar :size="32" icon="UserFilled" />
              <span class="nickname">{{ store.nickname || store.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled><el-icon><User /></el-icon> 学生</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout"><el-icon><SwitchButton /></el-icon> 退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="content">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../store/user'
import { ChatDotRound, User, EditPen, FolderOpened, MapLocation, DataLine, HomeFilled, Setting, Fold, FullScreen, ArrowDown, UserFilled, SwitchButton } from '@element-plus/icons-vue'

const store = useUserStore()
const route = useRoute()
const router = useRouter()
const isCollapse = ref(false)

const pageTitle = computed(() => {
  const map = {
    '/student/home': '首页',
    '/student/chat': 'AI辅导',
    '/student/profile-card': '我的画像',
    '/student/practice': '刷题房',
    '/student/resources': '资源中心',
    '/student/learning-path': '学习路径',
    '/student/evaluation': '学习评估',
    '/student/my-info': '个人信息'
  }
  return map[route.path] || '学习中心'
})

function handleLogout() { store.logout(); localStorage.removeItem('token'); localStorage.removeItem('user'); window.location.href = '/login.html' }
function toggleFullscreen() {
  if (!document.fullscreenElement) document.documentElement.requestFullscreen()
  else document.exitFullscreen()
}
</script>

<style scoped>
.student-layout { height: 100vh; }
.sidebar { background: #001529; overflow: hidden; transition: width 0.28s; }
.logo-box {
  height: 56px; display: flex; align-items: center; justify-content: center; gap: 10px;
  cursor: pointer; border-bottom: 1px solid rgba(255,255,255,0.08); overflow: hidden;
}
.logo-icon { font-size: 26px; flex-shrink: 0; }
.logo-text { color: #fff; font-size: 16px; font-weight: 700; white-space: nowrap; letter-spacing: 1px; }
.side-menu { border-right: none; }
.side-menu :deep(.el-menu-item) { height: 48px; line-height: 48px; margin: 4px 8px; border-radius: 8px; }
.side-menu :deep(.el-menu-item:hover) { background: rgba(255,255,255,0.08) !important; }
.side-menu :deep(.el-menu-item.is-active) { background: linear-gradient(90deg, #1890ff, #096dd9) !important; color: #fff !important; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.main-area { background: #f0f2f5; }
.topbar {
  display: flex; align-items: center; justify-content: space-between;
  height: 56px; padding: 0 20px; background: #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06); z-index: 10;
}
.topbar-left { display: flex; align-items: center; gap: 16px; }
.collapse-btn { font-size: 20px; cursor: pointer; color: #595959; }
.collapse-btn:hover { color: #1890ff; }
.topbar-right { display: flex; align-items: center; gap: 18px; }
.top-icon { font-size: 18px; color: #595959; cursor: pointer; }
.top-icon:hover { color: #1890ff; }
.user-area {
  display: flex; align-items: center; gap: 8px; cursor: pointer;
  padding: 4px 8px; border-radius: 6px; transition: background 0.2s;
}
.user-area:hover { background: #f0f2f5; }
.nickname { font-size: 14px; color: #262626; }
.content { padding: 20px; min-height: 0; }
.page-fade-enter-active, .page-fade-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.page-fade-enter-from { opacity: 0; transform: translateY(8px); }
.page-fade-leave-to { opacity: 0; transform: translateY(-8px); }
</style>