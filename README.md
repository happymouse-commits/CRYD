# CRYD — 智能体协同自适应学习平台

基于多智能体协作的高校 C 语言课程自适应学习平台，通过 AI 辅导对话实时构建学生画像，动态生成个性化学习路径。

## 核心特性

- **AI 辅导对话** — 基于讯飞星火大模型，学生与智能体自然语言交互，实时答疑
- **动态学生画像** — 8 维度学习画像自动生成（知识基础、认知风格、学习偏好、学习节奏、兴趣方向、薄弱环节、学习动机、专注力）
- **个性化学习路径** — 画像分析师智能体检测薄弱点，路径规划师自动生成补强路径
- **智能出题** — 4 档难度题库，支持章节练习与 AI 动态生成
- **RAG 知识库** — 文档分块 + 向量检索 + 上下文增强，提升辅导准确性
- **多角色管理** — 学生端、教师端、管理员端，各角色独立界面与功能

## 技术栈

| 层 | 技术 |
|---|------|
| 前端 | Vue 3 + Element Plus + Pinia + Vite |
| 后端 | Spring Boot 3.5.0 + Java 21 |
| 数据库 | H2（开发环境） |
| AI 大模型 | 讯飞星火（LangChain4j 接入） |
| 向量存储 | In-memory VectorStore（可扩展至 Milvus） |
| 构建 | Maven + npm |

## 项目结构

```
从容应对/
├── 前端/                      # 旧版静态页面（登录入口）
├── 后端/                      # Spring Boot 后端
│   └── src/main/java/com/happymouse/cryd/
│       ├── agent/             # 智能体核心框架
│       │   ├── core/          # Pipeline/调度/审核/聚合
│       │   └── memory/        # 智能体记忆管理
│       ├── config/            # 安全配置/Web配置/数据初始化
│       ├── controller/        # REST API（15+ 控制器）
│       ├── model/entity/      # 数据实体（15+ 实体类）
│       ├── repository/        # JPA 数据访问层
│       ├── service/
│       │   ├── agent/         # 7 大专业智能体
│       │   ├── knowledge/     # 知识库服务
│       │   ├── rag/           # RAG 检索增强生成
│       │   └── spark/         # 讯飞星火 API 客户端
│       └── resources/
│           ├── question-bank/ # C 语言题库（10 章 200 题）
│           └── static/        # 前端构建产物
├── frontend/                  # Vue 3 前端源码
│   └── src/
│       ├── views/
│       │   ├── 学生端/        # AI辅导/画像/刷题/资源/闯关/路径
│       │   ├── 教师端/        # 布置作业/数据分析/知识库/课程管理
│       │   └── 管理员/        # 仪表盘/数据统计/用户管理/系统配置
│       ├── store/             # Pinia 状态管理
│       ├── api/               # Axios API 封装
│       └── router/            # Vue Router 路由
└── data/                      # H2 数据库文件（运行时生成，不入库）
```

## 智能体架构

```
学生输入 → AgentOrchestrator（调度）
              ├── ProfileAnalystAgent   → 学习画像分析
              ├── TutorAgent            → 辅导答疑（兜底）
              ├── QuestionExpertAgent   → 出题（4档难度）
              ├── CourseDesignerAgent   → 课程设计（认知风格适配）
              ├── PathPlannerAgent      → 学习路径规划
              └── KnowledgeManagerAgent → 知识库管理
```

核心流程：**感知 → 分析 → 规划 → 生成 → 反馈** 五阶段长链推理闭环。

## 快速开始

### 环境要求

- JDK 21+
- Node.js 18+
- Maven 3.8+

### 后端启动

```bash
cd 后端
mvn spring-boot:run
```

### 前端开发

```bash
cd frontend
npm install
npm run dev
```

### 生产构建

```bash
cd frontend
npm run build
# 构建产物自动复制到 后端/src/main/resources/static/
```

应用启动后访问 `http://localhost:8080`

### 默认账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin1 | 123456 |
| 学生 | student1 | 123456 |
| 教师 | teacher1 | 123456 |

## 主要 API

| 接口 | 说明 |
|------|------|
| `POST /api/auth/login` | 登录 |
| `POST /api/auth/register` | 注册 |
| `POST /api/chat` | AI 辅导对话 |
| `GET /api/student/profile` | 获取学习画像 |
| `GET /api/student/chapter/{id}/questions` | 获取章节题目 |
| `POST /api/student/chapter/{id}/submit` | 提交答案 |
| `GET /api/teacher/info` | 教师信息 |
| `GET /api/admin/statistics` | 管理员统计 |

## License

MIT
