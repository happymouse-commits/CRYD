# CRYD 顶部导航栏（Topbar）相关代码整理

## 截图描述
顶部横向导航栏，从左到右：
- 左侧：橙色圆点 + "从容应对" 品牌名
- 中间：导航标签页（AI导学AI导学 / 学习空间学习空间 / 刷题房刷题房），当前选中"AI导学"
- 右侧：用户头像 + "张小泉"

---

## 当前代码位置
文件：`F:\从容应对\frontend\src\views\学生端\布局.vue`

### 当前顶部栏模板（<el-header> 部分）
```html
<!-- 当前是问候式顶栏 -->
<el-header class="topbar">
  <div class="greeting">Hi，<em>{{ store.nickname || store.username }}同学</em> 👋 今天学得怎么样？</div>
  <button class="exit-btn" @click="handleLogout">退出</button>
</el-header>
```

### 当前顶部栏样式
```css
.topbar {
  display: flex; align-items: center; justify-content: space-between;
  height: 50px; padding: 0 22px;
  background: var(--t-surface);        /* #f4efe7 暖白 */
  border-bottom: 1px solid var(--t-wash); /* #dad2c7 */
}
.greeting { font-size: 14px; font-weight: 600; color: var(--t-line); }
.greeting em { font-style: normal; color: var(--t-accent); } /* #b15311 橙色 */
.exit-btn {
  background: transparent; color: var(--t-line-subtle);
  border: 1px solid var(--t-wash); padding: 5px 14px;
  border-radius: 10px; font-size: 12px; cursor: pointer;
}
```

---

## 全局主题变量（暖色 Typer 风格）
```css
:root {
  --t-ground: #ebe2d7;       /* 页面背景 */
  --t-surface: #f4efe7;      /* 卡片/顶栏背景 */
  --t-wash: #dad2c7;         /* 边框色 */
  --t-line: #342618;         /* 主文字 */
  --t-line-dim: #6a6054;     /* 次文字 */
  --t-line-subtle: #b6ada1;  /* 弱文字 */
  --t-accent: #b15311;       /* 强调色-橙棕 */
  --t-accent-soft: #e0d9cd;  /* 强调色浅底 */
  --t-status-ready: #4a7c4e; /* 绿色 */
  --t-surface-muted: #e4dfd8;/* 灰底 */
  --t-shadow: 0 2px 24px rgb(52 38 24 / 8%);
}
```

---

## 当前完整布局结构
```
el-container.student-layout (height: 100vh)
├── el-aside.sidebar (width:140px)     ← 左侧边栏（logo + 导航菜单 + 底部用户信息）
│   ├── logo-box (CR + CRYD)
│   ├── side-nav (首页/AI辅导/刷题房/错题本)
│   └── sidebar-bottom (身份认证 + 用户名)
└── el-container.main-area
    ├── el-header.topbar               ← 顶部栏（问候语 + 退出按钮）【要改的部分】
    └── el-main.content                ← 内容区（router-view）
```

---

## 路由配置
文件：`F:\从容应对\frontend\src\router\index.js`
```js
// 学生端子路由
children: [
  { path: 'home', component: () => import('../views/学生端/首页.vue') },
  { path: 'chat', component: () => import('../views/学生端/AI辅导.vue') },
  { path: 'profile-card', component: () => import('../views/学生端/我的画像.vue') },
  { path: 'practice', component: () => import('../views/学生端/刷题房.vue') },
  { path: 'resources', component: () => import('../views/学生端/学习资源.vue') },
  { path: 'learning-path', component: () => import('../views/学生端/学习路径.vue') },
  { path: 'evaluation', component: () => import('../views/学生端/学习评估.vue') },
  { path: 'my-info', component: () => import('../views/学生端/个人信息.vue') },
]
```

---

## User Store（用户信息来源）
文件：`F:\从容应对\frontend\src\store\user.js`
可用字段：`store.id`, `store.username`, `store.nickname`, `store.onboardingDone`, `store.isLoggedIn`

---

## 要改成的目标样式（根据截图推测）

截图中的顶栏应该是这样的结构：

```html
<el-header class="topbar">
  <!-- 左：品牌 -->
  <div class="brand">
    <span class="brand-dot"></span>
    <span class="brand-name">从容应对</span>
  </div>

  <!-- 中：导航标签 -->
  <nav class="top-nav">
    <router-link to="/student/chat" :class="{ active: ... }">AI导学</router-link>
    <router-link to="/student/home" :class="{ active: ... }">学习空间</router-link>
    <router-link to="/student/practice" :class="{ active: ... }">刷题房</router-link>
  </nav>

  <!-- 右：用户 -->
  <div class="user-area">
    <span class="user-avatar"></span>
    <span class="user-name">{{ store.nickname }}</span>
  </div>
</el-header>
```

关键视觉特征：
- 背景：浅暖白色 (#f4efe7 或更浅)
- 高度：约 48-52px
- 导航选中态：橙色文字 + 浅橙底色圆角胶囊
- 品牌左侧有橙色圆点装饰
- 右侧用户区域有头像+名字
