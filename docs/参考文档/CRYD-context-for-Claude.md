# CRYD 项目当前状态 — 给 Claude 的上下文

## 项目概况
**从容应对（CRYD）** — 个性化学习多智能体系统，参赛作品（CDE三类）。
技术栈：Spring Boot + Vue 3 + Element Plus，H2 本地数据库。

## 前端架构变更

### 当前路由结构
- `/login.html` → Vue SPA 登录页（`Login.vue`，仅用户名+密码，无角色选择）
- `/student/home` → 首页
- `/student/chat` → AI辅导（含三阶段导学流程）
- `/student/profile-card` → 我的画像
- `/student/practice` → 刷题房
- `/student/resources` → 学习资源
- `/student/learning-path` → 学习路径
- `/student/evaluation` → 学习评估
- `/student/my-info` → 个人信息

### 布局结构
- **顶部导航栏**：品牌("从容应对" + 橙色圆点) + GooeyNav导航(AI导学/学习空间/刷题房，带粒子气泡动效) + 用户区(头像+名字)
- **侧边栏**：仍在，包含首页/学习评估/AI辅导/刷题房/错题本 导航 + 底部身份认证+用户信息
- ⚠️ 侧边栏和顶部GooeyNav导航功能重复，后续可能移除侧边栏

### GooeyNav 组件
- 路径：`src/components/GooeyNav.vue`
- 特性：粘稠导航效果，点击切换时粒子气泡动画，根据当前路由自动匹配激活项
- 接收 props：`items`（数组，每项 `{label, href}`）、`initialActiveIndex`

### 登录页
- 新版：Vue 组件 `Login.vue`，仅用户名+密码，注册固定角色为 student
- 旧版：已移至 `static/legacy/login_legacy.html`（独立HTML+CSS+JS，有动画角色，备用）

### 后端 SPA 支持
- `WebConfig.java` 配置了 `/login.html`、`/student/**` 转发到 `index.html`
- Vue Router 使用 `createWebHistory()` history 模式

## 暖色主题（Typer 风格）
```css
--t-ground: #ebe2d7;      /* 页面背景 */
--t-surface: #f4efe7;     /* 卡片/顶栏背景 */
--t-wash: #dad2c7;        /* 边框色 */
--t-line: #342618;        /* 主文字 */
--t-line-dim: #6a6054;    /* 次文字 */
--t-accent: #b15311;      /* 强调色-橙棕 */
--t-accent-soft: #e0d9cd; /* 强调色浅底 */
```

## AI 导学流程
新用户首次登录 → 自动进入 AI 辅导页三阶段导学：
1. **画像采集**：AI 对话采集 8 个维度，完整度 ≥80% 才能进入下一阶段
2. **出题测评**：3 道 C 语言题（从简到难），测出薄弱点
3. **生成资源**：8 个智能体并行生成个性化学习内容

未完成导学的用户：前端路由守卫只能访问 AI辅导 + 个人信息；后端 API 守卫返回 403。

## 文件位置
- 前端：`F:\从容应对\frontend\`（Vite + Vue 3）
- 后端：`F:\从容应对\后端\`（Spring Boot）
- 静态资源：`F:\从容应对\后端\src\main\resources\static\`（构建产物部署于此）
- 本地访问：`http://localhost:8080`
- ECS 部署：`8.137.186.118` 用户名 `kkx01925`

## 已完成的改造
- ✅ 登录/注册简化为仅用户名+密码
- ✅ 移除教师端/管理员端（.bak 已删除）
- ✅ AI 导学三阶段流程
- ✅ 前后端导学守卫
- ✅ GooeyNav 顶部导航融合
- ✅ 刷题房"教师作业"→"AI出题"
