<template>
  <el-container class="admin-layout">
    <!-- 深色侧栏 Vben风格 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <div class="logo-box" @click="isCollapse = !isCollapse">
        <span class="logo-icon">🎓</span>
        <transition name="fade">
          <span v-show="!isCollapse" class="logo-text">CRYD Admin</span>
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
        <el-menu-item index="/admin/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据面板</span>
        </el-menu-item>
        <el-menu-item index="/admin/users">
          <el-icon><UserFilled /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/system">
          <el-icon><Monitor /></el-icon>
          <span>系统状态</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 右侧主体 -->
    <el-container class="main-area">
      <!-- 顶部栏 -->
      <el-header class="topbar">
        <div class="topbar-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse"><Fold /></el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
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
                <el-dropdown-item disabled><el-icon><User /></el-icon> {{ store.role }}</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout"><el-icon><SwitchButton /></el-icon> 退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
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
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../store/user'
import { DataAnalysis, UserFilled, Monitor, Fold, FullScreen, ArrowDown, User, SwitchButton } from '@element-plus/icons-vue'

const store = useUserStore()
const route = useRoute()
const router = useRouter()
const isCollapse = ref(false)

// 动态标题
const pageTitle = computed(() => {
  const map = {
    '/admin/dashboard': '数据面板',
    '/admin/users': '用户管理',
    '/admin/system': '系统状态'
  }
  return map[route.path] || '管理后台'
})

function handleLogout() { store.logout(); localStorage.removeItem('token'); localStorage.removeItem('user'); window.location.href = '/login.html' }
function toggleFullscreen() {
  if (!document.fullscreenElement) document.documentElement.requestFullscreen()
  else document.exitFullscreen()
}
</script>

<style scoped>
.admin-layout { height: 100vh; }

/* ===== 侧栏 ===== */
.sidebar {
  background: #001529;
  overflow: hidden;
  transition: width 0.28s;
}

.logo-box {
  height: 56px;
  display: flex; align-items: center; justify-content: center; gap: 10px;
  cursor: pointer;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  overflow: hidden;
}
.logo-icon { font-size: 26px; flex-shrink: 0; }
.logo-text { color: #fff; font-size: 16px; font-weight: 700; white-space: nowrap; letter-spacing: 1px; }

.side-menu { border-right: none; }
.side-menu :deep(.el-menu-item) {
  height: 48px; line-height: 48px; margin: 4px 8px; border-radius: 8px;
}
.side-menu :deep(.el-menu-item:hover) { background: rgba(255,255,255,0.08) !important; }
.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, #1890ff, #096dd9) !important;
  color: #fff !important;
}

/* 折叠时隐藏文字 */
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* ===== 顶部栏 ===== */
.main-area { background: #f0f2f5; }
.topbar {
  display: flex; align-items: center; justify-content: space-between;
  height: 56px; padding: 0 20px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  z-index: 10;
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

/* ===== 内容区 ===== */
.content { padding: 20px; min-height: 0; }

/* 页面切换动画 */
.page-fade-enter-active, .page-fade-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.page-fade-enter-from { opacity: 0; transform: translateY(8px); }
.page-fade-leave-to { opacity: 0; transform: translateY(-8px); }
</style>
