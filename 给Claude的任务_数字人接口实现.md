# 任务文档：CRYD 数字人对话接口接入

> 本文档交给 Claude，由其完成具体代码实现。
> 项目路径：`F:\从容应对`

---

## 一、任务目标

将 `首页.vue` 左侧「数字人对话」卡片，从**桩数据（假回复）** 改为**真实调用后端 API**，实现完整的 AI 对话功能。

当前状态：
- `前端页面修改指南.md` ✅ 已创建（配色规范）
- `CLAUDE.md` ✅ 已创建（项目上下文）
- `composables/useDigitalHuman.js` ⚠️ 桩实现（需替换）
- `首页.vue` 数字人卡片 ⚠️ 已绑定接口但未真实调用

---

## 二、需要修改的文件

| 文件 | 修改内容 | 优先级 |
|------|---------|---------|
| `frontend/src/composables/useDigitalHuman.js` | 替换桩函数为真实 API 调用 | **P0** |
| `frontend/src/views/学生端/首页.vue` | 确认绑定正确，无需大改 | P1 |
| `frontend/src/api/index.js` | 确认已有 `api` 实例可用 | P1 |

---

## 三、具体任务（Claude 执行）

### Task 1：替换 `useDigitalHuman.js` 中的桩实现

#### 1.1 替换 `loadHistory()`

```js
// 当前（桩）：
async function loadHistory() {
  try {
    // TODO: Claude → const res = await api.get(`/chat/history/${store.id}`)
    // TODO: Claude → messages.value = res.data || []
    messages.value = []
    scrollToBottom()
  } catch (e) {
    console.warn('加载历史失败:', e)
  }
}

// 改为真实调用：
async function loadHistory() {
  try {
    const res = await api.get(`/chat/history/${store.id}`)
    messages.value = res.data || []
    scrollToBottom()
  } catch (e) {
    console.warn('加载历史失败:', e)
    messages.value = []
  }
}
```

#### 1.2 替换 `sendMessage()` 中的 `_stubFetchReply` 调用

```js
// 当前（桩）：
const data = await _stubFetchReply(msg)

// 改为真实调用：
const res = await api.post('/chat/send', {
  studentId: store.id,
  message: msg
})
const data = res.data
```

#### 1.3 实现语音录音 → 转文字流程

`startRecording()` 的 `mediaRecorder.onstop` 里，当前是桩：

```js
// 当前（桩）：
inputText.value = '语音识别功能待实现'

// 改为真实调用：
const blob = new Blob(audioChunks, { type: 'audio/wav' })
const formData = new FormData()
formData.append('audio', blob, 'voice.wav')
const res = await api.post('/chat/voice-to-text', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
})
inputText.value = res.data.text
```

#### 1.4（可选）实现图片上传

当前 `AI辅导.vue` 已有图片上传逻辑，首页卡片暂不实现图片，但接口已预留。

---

### Task 2：检查 `首页.vue` 的模板绑定

确认以下绑定存在（应该已有，检查即可）：

```html
<!-- 消息列表 -->
<div v-for="msg in dh.messages" :key="msg.id">
  <!-- 用户消息 -->
  <div v-if="msg.role === 'user'">{{ msg.content }}</div>
  <!-- AI 消息 -->
  <div v-else-if="msg.role === 'ai'">
    <span v-if="msg.agentName" class="msg-agent-tag">{{ msg.agentName }}</span>
    <span v-if="msg.typing">{{ msg.displayContent }}<span class="typing-cursor">▌</span></span>
    <span v-else>{{ msg.content }}</span>
  </div>
  <!-- 系统错误消息 -->
  <div v-else class="msg-sys">{{ msg.content }}</div>
</div>

<!-- 输入框 -->
<input v-model="dh.inputText" @keyup.enter="sendMsg" :disabled="dh.loading">

<!-- 发送按钮 -->
<button @click="sendMsg" :disabled="dh.loading">发送</button>
```

---

### Task 3：验证 API 实例可用

检查 `frontend/src/api/index.js` 导出的 `api` 实例是否被 `useDigitalHuman.js` 正确导入：

```js
// useDigitalHuman.js 顶部应有：
import api from '../api'
```

如果没有，需要加上（但当前项目应该已有）。

---

## 四、后端 API 契约（已实现，直接调用）

| 接口 | 方法 | 路径 | 请求体 | 响应 |
|------|------|------|--------|------|
| 发送消息 | `POST` | `/api/chat/send` | `{ studentId, message }` | `{ code, data: { agentName, message } }` |
| 获取历史 | `GET` | `/api/chat/history/{studentId}` | - | `{ code, data: ChatMessage[] }` |
| 语音转文字 | `POST` | `/api/chat/voice-to-text` | `FormData { audio }` | `{ code, data: { text } }` |
| 上传图片 | `POST` | `/api/chat/upload-image` | `FormData { image }` | `{ code, data: { url, base64 } }` |
| 图片分析 | `POST` | `/api/chat/send-image` | `{ studentId, imageBase64 }` | `{ code, data: { agentName, message } }` |

**注意**：前端调用时不需要写 `/api` 前缀（axios 实例已配置 `baseURL: '/api'`），直接写 `/chat/send` 即可。

---

## 五、消息数据结构（Msg）

```js
{
  id: Number,          // 时间戳，唯一标识
  role: 'user' | 'ai' | 'system',
  content: String,      // 纯文本内容
  displayContent: String, // 打字效果用（ai 消息）
  agentName: String,    // ai 消息的回复智能体名称
  createdAt: String,    // ISO 时间戳
  typing: Boolean,      // 是否正在打字
  error: Boolean        // 是否发送失败（system 消息）
}
```

---

## 六、数字人状态（DigitalHumanState）

```js
{
  mode: 'idle' | 'listening' | 'thinking' | 'speaking' | 'error',
  avatarEmoji: String,  // 当前头像表情（🤖/😊/🤔...）
  statusText: String,    // 状态文字（"在线中" / "思考中..." / "连接失败"）
  isOnline: Boolean,    // 服务是否在线
  isRecording: Boolean, // 是否正在录音
  lastError: String     // 最近一次错误信息
}
```

---

## 七、测试验证

完成修改后，在 `frontend/` 目录下运行：

```bash
npm run dev
```

然后在浏览器中：
1. 访问 `http://localhost:5173`
2. 登录学生账号（student1 / 123456）
3. 查看首页左侧「数字人对话」卡片
4. 输入 "指针怎么用？" → 应收到真实 AI 回复（非桩数据）
5. 检查控制台是否有 API 调用日志
6. 检查消息是否持久化（刷新页面后历史消息应还在）

---

## 八、预期交付物

- [ ] `useDigitalHuman.js` 中所有 `_stubFetchReply` 调用已替换为真实 API
- [ ] 语音录音 → 转文字流程可实现（调用 `/chat/voice-to-text`）
- [ ] `loadHistory()` 可加载历史消息
- [ ] 打字机效果正常工作（`typing` + `displayContent`）
- [ ] 错误消息正确处理（`try/catch` → `msg.role = 'system'`）
- [ ] 构建通过（`npm run build` 无报错）
- [ ] 提供简要说明：改了哪些文件、遇到什么问题、如何解决

---

## 九、注意事项

1. **不要破坏现有功能**：只改 `useDigitalHuman.js`，不要改 `AI辅导.vue`（那是独立的完整聊天页）
2. **保持亮色主题**：如果顺手改了 CSS，必须遵循 `前端页面修改指南.md` 的配色规范
3. **API 基础路径**：axios 实例已配置 `baseURL: '/api'`，调用时写 `/chat/send` 即可，不要写 `/api/chat/send`
4. **错误处理**：网络请求必须包 `try/catch`，失败时给用户友好提示（通过 `msg.role = 'system'` 显示）
5. **不要提交敏感信息**：API Key 已在后端用环境变量，前端不需要配置 Key

---

## 十、参考资料

- 项目概述：`CLAUDE.md`
- 前端配色规范：`前端页面修改指南.md`
- 后端控制器：`后端/src/main/java/com/happymouse/cryd/controller/ChatController.java`
- 已有的完整实现参考：`frontend/src/views/学生端/AI辅导.vue`（第 101-351 行）

---

**最后**：完成任务后，在 `给Claude的任务_数字人接口实现_完成报告.md` 中记录做了什么、遇到什么问题。
