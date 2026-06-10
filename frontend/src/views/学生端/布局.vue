<template>
  <el-container class="student-layout">
    <!-- 侧边栏 - 始终展开 -->
    <el-aside width="140px" class="sidebar">
      <div class="logo-box">
        <div class="logo-icon">CR</div>
        <span class="logo-text">CRYD</span>
      </div>

      <nav class="side-nav">
        <router-link to="/student/home" class="nav-item" :class="{ active: $route.path === '/student/home' }">
          <span class="ni-icon">🏠</span>
          <span class="ni-label">首页</span>
        </router-link>
        <router-link to="/student/evaluation" class="nav-item" :class="{ active: $route.path === '/student/evaluation' }">
          <span class="ni-icon">📊</span>
          <span class="ni-label">学习评估</span>
        </router-link>
        <router-link to="/student/chat" class="nav-item" :class="{ active: $route.path === '/student/chat' }">
          <span class="ni-icon">💬</span>
          <span class="ni-label">AI辅导</span>
        </router-link>
        <router-link to="/student/practice" class="nav-item" :class="{ active: $route.path === '/student/practice' }">
          <span class="ni-icon">🔔</span>
          <span class="ni-label">刷题房</span>
        </router-link>
        <router-link to="/student/my-info" class="nav-item" :class="{ active: $route.path === '/student/my-info' }">
          <span class="ni-icon">📝</span>
          <span class="ni-label">错题本</span>
        </router-link>
      </nav>

      <div class="sidebar-bottom">
        <div class="nav-item sb-login">
          <span class="ni-icon">🔐</span>
          <span class="ni-label">身份认证</span>
        </div>
        <div class="nav-item sb-avatar-item">
          <span class="ni-avatar">🧑</span>
          <span class="ni-label">{{ store.nickname || store.username }}</span>
        </div>
      </div>
    </el-aside>

    <!-- 主内容 -->
    <el-container class="main-area">
      <el-header class="topbar">
        <div class="greeting">Hi，<em>{{ store.nickname || store.username }}同学</em> 👋 今天学得怎么样？</div>
        <button class="exit-btn" @click="handleLogout">退出</button>
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
import { useRouter } from 'vue-router'
import { useUserStore } from '../../store/user'

const router = useRouter()
const store = useUserStore()

function handleLogout() {
  store.logout()
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  window.location.href = '/login.html'
}
</script>

<style scoped>
/* ===== 整体 ===== */
.student-layout { height: 100vh; }

/* ===== 侧边栏 ===== */
.sidebar {
  background: #fff;
  border-right: 1px solid #e8ecf1;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  padding: 16px 0 0;
  box-shadow: 2px 0 16px rgba(0,0,0,0.03);
  z-index: 10;
}
.logo-box {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px 14px;
  border-bottom: 1px solid #f0f2f5;
  margin-bottom: 8px;
}
.logo-icon {
  width: 34px; height: 34px;
  border-radius: 9px;
  background: linear-gradient(135deg, #5b8def, #7c5cfc);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 800;
  flex-shrink: 0;
}
.logo-text { font-size: 16px; font-weight: 700; color: #1a1a2e; letter-spacing: 1px; white-space: nowrap; }

/* 导航 */
.side-nav { flex: 1; display: flex; flex-direction: column; gap: 2px; padding: 0 8px; }
.nav-item {
  display: flex; align-items: center; gap: 10px;
  padding: 9px 10px; border-radius: 10px;
  cursor: pointer; text-decoration: none;
  color: #6b7280; font-size: 13px; font-weight: 500;
  transition: all 0.18s; border-left: 2px solid transparent;
  white-space: nowrap;
}
.nav-item:hover { background: #f5f7fa; color: #1a1a2e; }
.nav-item.active {
  background: rgba(91,141,239,0.06);
  color: #5b8def;
  border-left-color: #5b8def;
}
.ni-icon {
  font-size: 17px; width: 20px; text-align: center; flex-shrink: 0;
}
.ni-label { font-size: 12.5px; }

/* 底部 */
.sidebar-bottom {
  padding: 8px 8px 12px;
  border-top: 1px solid #f0f2f5;
  display: flex; flex-direction: column; gap: 2px;
  margin-top: 8px;
}
.sb-login .ni-icon {
  width: 34px; height: 34px;
  border-radius: 9px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(245,158,11,0.08);
  border: 1px solid rgba(245,158,11,0.15);
  color: #f59e0b;
}
.sb-login:hover { background: rgba(245,158,11,0.04); }
.sb-avatar-item { gap: 10px; }
.ni-avatar {
  width: 34px; height: 34px;
  border-radius: 9px;
  background: linear-gradient(135deg, #34d399, #5b8def);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 15px; flex-shrink: 0;
}

/* ===== 主内容区 ===== */
.main-area { background: #f3f5f9; }
.topbar {
  display: flex; align-items: center; justify-content: space-between;
  height: 50px; padding: 0 22px;
  background: #fff;
  border-bottom: 1px solid #e8ecf1;
}
.greeting { font-size: 14px; font-weight: 600; color: #1a1a2e; }
.greeting em { font-style: normal; color: #5b8def; }
.exit-btn {
  background: transparent; color: #9ca3af;
  border: 1px solid #e5e7eb; padding: 5px 14px;
  border-radius: 10px; font-size: 12px; cursor: pointer;
  transition: all 0.18s;
}
.exit-btn:hover { color: #f87171; border-color: rgba(248,113,113,0.3); background: #fef2f2; }

.content {
  padding: 16px 18px 18px 14px;
  min-height: 0; overflow-y: auto;
}

.page-fade-enter-active, .page-fade-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.page-fade-enter-from { opacity: 0; transform: translateY(6px); }
.page-fade-leave-to { opacity: 0; transform: translateY(-6px); }
</style>
