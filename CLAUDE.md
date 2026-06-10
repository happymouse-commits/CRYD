# CLAUDE.md — CRYD 智能体协同自适应学习平台

> 克隆此仓库后，让 Claude 直接理解项目上下文再开工。

## 项目概述

**CRYD** 是基于多智能体协作的高校 C 语言课程自适应学习平台。

- 学生通过 AI 辅导对话实时答疑，系统自动构建 8 维学习画像，动态生成个性化学习路径
- 教师可布置作业、智能出题、查看班级学情
- 管理员管理用户和系统配置

**比赛定位**：参加 C 类（课件资源类）+ D 类（智能交互类）+ E 类（综合应用类）。

---

## 技术栈

| 层 | 技术 |
|---|------|
| 前端 | Vue 3 + Element Plus + Pinia + ECharts + Vite |
| 后端 | Spring Boot 3.5.0 + Java 21 |
| 数据库 | openGauss 6.0（PostgreSQL 兼容，有自定义 Dialect） |
| AI 模型 | DeepSeek V4 Pro（OpenAI 兼容接口） |
| 向量检索 | 本地 n-gram 哈希投影（1024 维，零外部依赖） |
| 构建 | Maven 3.8+ / Node.js 18+ |

---

## 项目结构

```
从容应对/
├── README.md                      # 项目说明（已存在，不要覆盖）
├── CLAUDE.md                      # 本文件
├── .env                           # API Key（不要提交到 Git）
├── .env.example                   # 环境变量模板
├── 作品说明表.md / .docx           # 比赛材料
├── AI协同过程记录表.md / .docx     # 比赛材料
├── 展示PPT.pptx                    # 比赛材料
├── frontend/                      # Vue 3 前端
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── 入口.js                 # App 入口
│       ├── 根组件.vue              # 根组件
│       ├── api/                   # Axios 封装
│       ├── router/                # 路由定义
│       ├── store/user.js          # Pinia 用户状态
│       └── views/
│           ├── 学生端/            # 9 个学生端页面
│           ├── 教师端/            # 教师端页面
│           └── 管理员/            # 管理员页面
├── 后端/                          # Spring Boot
│   ├── pom.xml
│   └── src/main/java/com/happymouse/cryd/
│       ├── CrydApplication.java
│       ├── agent/core/            # 智能体管道调度核心
│       ├── config/                # Security/Web/OpenGaussDialect
│       ├── controller/            # 15 个 REST 控制器
│       ├── model/entity/          # 20 个 JPA 实体
│       ├── model/dto/             # DTO
│       ├── repository/            # 20 个 JPA Repository
│       ├── service/agent/         # 6 大专业智能体
│       ├── service/knowledge/     # 知识库管理
│       ├── service/rag/           # RAG + 防幻觉四道门
│       ├── service/spark/         # LLM HTTP 客户端
│       └── resources/
│           ├── application.yml
│           ├── question-bank/     # C 语言题库（10 章 97 题）
│           └── static/            # 前端构建产物
└── uploads/                       # 上传文件目录
```

---

## 智能体架构（6 个 Agent）

```
学生输入
  → PipelineOrchestrator 管道调度
     ├── ProfileAnalystAgent   → 8 维学习画像分析
     ├── TutorAgent            → 辅导答疑（兜底）
     ├── QuestionExpertAgent   → 智能出题
     ├── CourseDesignerAgent   → 课程设计（认知风格适配）
     ├── PathPlannerAgent      → 学习路径规划
     └── KnowledgeManagerAgent → 知识库管理
```

核心闭环：**感知 → 分析 → 规划 → 生成 → 反馈**

---

## RAG 防幻觉四道门

```
用户提问
  → 1. 检索门控：向量检索知识库，无结果拒绝生成
  → 2. Prompt 约束：强制仅基于参考资料回答
  → 3. 事实校验：关键词 + 数值 + LLM 语义三重校验（置信度 ≥ 0.6）
  → 4. 格式锁：5 种任务模板校验输出格式
  → 输出
```

---

## 构建与运行

### 环境要求
- JDK 21+
- Node.js 18+
- Maven 3.8+

### 环境变量（`.env` 文件）
```
LLM_API_KEY=sk-your-key
LLM_BASE_URL=https://api.deepseek.com
LLM_MODEL=deepseek-chat
```

### 后端
```bash
cd 后端
mvn spring-boot:run
# 默认 http://localhost:8080
```

### 前端开发
```bash
cd frontend
npm install
npm run dev
# 默认 http://localhost:5173
```

### 生产构建
```bash
cd frontend && npm run build
# 输出到 dist/，手动复制到 后端/src/main/resources/static/
```

### 默认账号
| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin1 | 123456 |
| 学生 | student1 | 123456 |
| 教师 | teacher1 | 123456 |

---

## 前端 UI 约定（重要）

**已完成亮色主题迁移**（2026-06-10）。后续修改必须沿用此风格：

- 页面背景: `#f3f5f9`
- 卡片: 白色 `#fff`，`border-radius: 14-16px`，`box-shadow: 0 1px 6px rgba(0,0,0,0.04)`，`border: 1px solid #eef0f4`
- 主色调: `#5b8def`（替代 Element Plus 默认 `#409EFF`）
- 成功绿: `#34d399` / 警告橙: `#f59e0b` / 危险红: `#f87171`
- 文字色: `#1a1a2e`（标题）→ `#374151`（正文）→ `#6b7280`（次要）→ `#9ca3af`（提示）
- 侧边栏: 140px 白色常展，蓝色渐变 logo
- 使用 `scoped` CSS，组件内颜色不要硬编码 `#409EFF`，统一用新配色

---

## 当前状态（2026-06-10）

- ✅ 3 个比赛材料已完成（作品说明表、AI协同记录表、展示PPT）
- ✅ API Key 安全修复（已从代码中移除，改用 `.env`）
- ✅ 学生端 9 个页面亮色主题迁移完成
- ✅ 布局/首页重构为参考图三栏式设计
- ❌ ECS 服务器 `8.137.186.118` 失联（Ping/SSH/HTTP 全超时）
- ⚠️ 比赛材料可能需要根据最新 UI 更新截图和描述
- ⚠️ 数字人对话方案尚未集成（仅做了方案调研，Fay + Edge TTS）

---

## 重要约定

### 安全
- **API Key 绝不写入源码**。所有密钥通过 `.env` 注入，`.env` 已在 `.gitignore` 中
- 不要提交 `target/`、`node_modules/`、`dist/`

### 编码风格
- 前端组件名用中文（如 `AI辅导.vue`），路径用 `@/` 别名字 `src/`
- 后端遵循 Spring Boot 标准分层（Controller → Service → Repository）
- 数据库实体使用 JPA + `BIGSERIAL`（OpenGaussDialect 兼容）
- 不要修改 README.md 和比赛材料文件（.docx/.pptx），除非用户明确要求

### Git
- 仓库: `happymouse-commits/SGHR`（GitHub）
- GitHub Actions 已配置自动部署到 ECS（`deploy.yml`，但目前 ECS 失联）
- 提交信息用中文，记录做了什么而非怎么做

### 文件路径
- 项目根目录: `F:\从容应对`
- 不要往 C 盘写文件
- 前端构建产物在 `frontend/dist/`
