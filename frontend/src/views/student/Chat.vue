<template>
  <div class="chat-container">
    <div class="chat-messages" ref="msgBox">
      <div v-for="msg in messages" :key="msg.id" :class="['msg', msg.role === 'user' ? 'msg-user' : 'msg-ai']">
        <div class="msg-bubble">
          <div v-if="msg.agentName" class="agent-tag">{{ msg.agentName }}</div>
          <div class="msg-text" v-html="renderMd(msg.typing ? msg.displayContent : msg.content)"></div>
          <span v-if="msg.typing" class="cursor">▌</span>
          <div class="msg-time">{{ formatTime(msg.createdAt) }}</div>
        </div>
      </div>
      <div v-if="loading && !hasTyping" class="msg msg-ai">
        <div class="msg-bubble thinking">
          <span class="dot"></span><span class="dot"></span><span class="dot"></span>
        </div>
      </div>
    </div>
    <div class="chat-input">
      <el-input v-model="input" placeholder="输入问题，如：什么是C语言的指针？" @keyup.enter="send" size="large" :disabled="loading">
        <template #append>
          <el-button @click="send" :loading="loading" type="primary">发送</el-button>
        </template>
      </el-input>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useUserStore } from '../../store/user'
import api from '../../api'

const store = useUserStore()
const messages = ref([])
const input = ref('')
const loading = ref(false)
const msgBox = ref(null)

// 是否有消息正在打字中
const hasTyping = computed(() => messages.value.some(m => m.typing))

onMounted(async () => {
  try {
    const res = await api.get('/chat/history/' + store.id)
    messages.value = res.data || []
    scrollToBottom()
  } catch (e) {}
})

function renderMd(text) {
  if (!text) return ''
  return text
    .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code class="$1">$2</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
}

function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

function scrollToBottom() {
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

/**
 * 打字机效果：把AI回复逐字显示
 * @param {object} msg - 消息对象
 * @param {string} fullText - 完整回复内容
 * @param {number} speed - 每个字间隔(ms)
 */
function typewriterEffect(msg, fullText, speed = 30) {
  let i = 0
  msg.displayContent = ''
  msg.typing = true

  const timer = setInterval(() => {
    if (i < fullText.length) {
      // 每次加1-3个字，中文1个字，英文可多加几个
      const step = /[\u4e00-\u9fff]/.test(fullText[i]) ? 1 : Math.min(3, fullText.length - i)
      msg.displayContent = fullText.substring(0, i + step)
      i += step
      scrollToBottom()
    } else {
      clearInterval(timer)
      msg.typing = false
      msg.displayContent = fullText
    }
  }, speed)
}

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return
  messages.value.push({ id: Date.now(), role: 'user', content: text, createdAt: new Date().toISOString() })
  input.value = ''
  loading.value = true
  scrollToBottom()

  try {
    const res = await api.post('/chat/send', { studentId: store.id, message: text })
    const aiMsg = {
      id: Date.now() + 1,
      role: 'assistant',
      content: res.data.message,
      displayContent: '',
      agentName: res.data.agentName,
      createdAt: new Date().toISOString(),
      typing: false
    }
    messages.value.push(aiMsg)
    // 启动打字机效果
    typewriterEffect(aiMsg, res.data.message, 30)
  } catch (e) {
  } finally {
    loading.value = false
    scrollToBottom()
  }
}
</script>

<style scoped>
.chat-container { display: flex; flex-direction: column; height: calc(100vh - 60px); }
.chat-messages { flex: 1; overflow-y: auto; padding: 20px; background: #f5f7fa; }
.msg { display: flex; margin-bottom: 16px; animation: msgIn 0.3s ease; }
@keyframes msgIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }

.msg-user { justify-content: flex-end; }
.msg-ai { justify-content: flex-start; }
.msg-bubble { max-width: 70%; padding: 12px 16px; border-radius: 12px; font-size: 14px; line-height: 1.6; position: relative; }
.msg-user .msg-bubble { background: #409EFF; color: white; }
.msg-ai .msg-bubble { background: white; color: #303133; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.agent-tag { font-size: 12px; color: #409EFF; margin-bottom: 4px; font-weight: bold; }
.msg-time { font-size: 11px; color: #c0c4cc; margin-top: 4px; }

/* 打字光标 */
.cursor {
  color: #409EFF; font-weight: bold;
  animation: blink 0.8s infinite;
}
@keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0; } }

/* 思考中的三个跳动的点 */
.thinking { display: flex; align-items: center; gap: 4px; padding: 16px 20px; }
.dot {
  width: 8px; height: 8px; border-radius: 50%; background: #409EFF;
  animation: bounce 1.2s infinite;
}
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce {
  0%,60%,100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-8px); opacity: 1; }
}

.chat-input { padding: 16px; background: white; border-top: 1px solid #eee; }
.msg-bubble :deep(pre) { background: #f4f4f5; padding: 8px; border-radius: 4px; overflow-x: auto; }
.msg-bubble :deep(code) { font-family: 'Consolas', monospace; font-size: 13px; }
</style>
