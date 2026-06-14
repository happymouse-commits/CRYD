# AI导学对话页面 — 代码提取说明

## 文件位置
**提取文件**: `C:\Users\404NotFound\Desktop\cryd-daoxue-extract.html`
**原始文件**: `F:\从容应对\后端\src\main\resources\static\index.html` (第1~1892行)

## 提取范围
截图对应的 **AI导学对话页面**，包含：

### HTML结构
- 左侧面板：诊断徽章 + 标题(Hi我是小容) + 副标题 + 进度条(5步) + 诊断完成摘要卡片 + "进入学习空间"按钮
- 右侧面板：聊天卡片（头像+名称栏 + 消息列表 + 输入框+发送按钮）
- 顶部导航栏（保留上下文）

### CSS样式
- `--root` 变量定义（Typer暖色主题：#ebe2d7底色, #b15311铜橙主色）
- `.dx-layout` 网格布局 (1fr + 580px)
- `.chat-card` 聊天卡片样式（毛玻璃、圆角、阴影）
- 消息气泡 `.msg-bot` / `.msg-user`
- 打字动画 `.typing-dots`
- 进度条 `.prog-bar` + 步骤列表 `.step-summary`
- 诊断摘要 `.dx-summary` + 标签 `.tag-strong/.tag-weak/.tag-new`

### JavaScript逻辑
1. **API工具函数** — `apiFetch()`, `getUserId()`, `showOnboarding()`
2. **状态管理** — `ST` 对象 (round/mode/step/messages)
3. **问题流数据** — `flows[]` 3轮×5题诊断题目
4. **模式切换**:
   - `setupOnboardMode()` — 新用户引导式诊断
   - `setupNormalMode()` — 老用户自由对话+考核
5. **聊天核心**:
   - `renderChat()` — 渲染消息列表
   - `botSay(text, label, delay)` — AI回复+打字动画
   - `sendMessage()` — 用户发送，4种场景分支：
     - 场景A: onboard + 有userId → POST `/api/onboarding/chat`
     - 场景B: onboard + 无userId → mock流自动下一题
     - 场景C: 正常聊天 → SSE流式 `/api/chat/send`
     - 场景D: 无userId兜底
6. **进度管理** — `updateProgress()`, `completeOnboarding()`, `buildRoundSummary()`
7. **SSE流式解析** — `data:` 行解析，逐字渲染到 `#sseStream`

## 对接的后端API
| API | 方法 | 用途 |
|-----|------|------|
| `/api/onboarding/chat` | POST | 引导式对话（body: {studentId, message}） |
| `/api/chat/send` | POST | SSE流式聊天（body: {studentId, message}） |

## 给其他AI修改的注意事项
1. **CSS变量已保留**，修改颜色只需改 `:root` 变量
2. **`enterLearningSpace()` 是占位函数**，跳转逻辑需替换
3. **localStorage key 前缀为 `cryd_`**，不要冲突
4. **SSE流式解析兼容两种格式**：`{content:"..."}` 和 `{reply:"..."}`
5. **消息中的换行符不会渲染为 `<br>`**，如需支持需加 `.textContent` → `.innerHTML` 转换
6. **聊天区域高度 `clamp(520px,70vh,680px)`**，移动端自适应

## 改完后怎么插回原项目
把改好的代码替换回 `F:\从容应对\后端\src\main\resources\static\index.html` 中对应位置：
- CSS → `<style>` 标签内 `.dx-*` 和 `.chat-*` 相关部分
- HTML → `<!-- PAGE 1: AI导学 -->` 到 `</div><!-- /page-daoxue -->` 之间的部分
- JS → `/* ═══ CHAT ═══ */` 到 `// 更新输入框提示` 之间的函数

或者直接告诉我改好的内容，我帮你合并回去。
