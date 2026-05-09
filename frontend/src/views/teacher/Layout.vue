<template>
  <el-container class="teacher-layout">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <div class="logo-box" @click="isCollapse = !isCollapse">
        <span class="logo-icon">🎓</span>
        <transition name="fade">
          <span v-show="!isCollapse" class="logo-text">CRYD 教师端</span>
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
        <el-menu-item index="/teacher/courses">
          <el-icon><Reading /></el-icon>
          <span>课程管理</span>
        </el-menu-item>
        <el-menu-item index="/teacher/assignments">
          <el-icon><EditPen /></el-icon>
          <span>作业管理</span>
        </el-menu-item>
        <el-menu-item index="/teacher/analysis">
          <el-icon><DataAnalysis /></el-icon>
          <span>学情分析</span>
        </el-menu-item>
        <el-menu-item index="/teacher/submissions">
          <el-icon><Document /></el-icon>
          <span>作业批改</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="main-area">
      <el-header class="topbar">
        <div class="topbar-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse"><Fold /></el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/teacher/courses' }">首页</el-breadcrumb-item>
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
                <el-dropdown-item disabled><el-icon><User /></el-icon> 教师</el-dropdown-item>
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
import { Reading, EditPen, DataAnalysis, Document, Fold, FullScreen, ArrowDown, User, UserFilled, SwitchButton } from '@element-plus/icons-vue'

const store = useUserStore()
const route = useRoute()
const router = useRouter()
const isCollapse = ref(false)

const pageTitle = computed(() => {
  const map = {
    '/teacher/courses': '课程管理',
    '/teacher/assignments': '作业管理',
    '/teacher/analysis': '学情分析',
    '/teacher/submissions': '作业批改'
  }
  return map[route.path] || '教学中心'
})

function handleLogout() { store.logout(); localStorage.removeItem('token'); localStorage.removeItem('user'); window.location.href = '/login.html' }
function toggleFullscreen() {
  if (!document.fullscreenElement) document.documentElement.requestFullscreen()
  else document.exitFullscreen()
}
</script>

<style scoped>
.teacher-layout { height: 100vh; }
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
