<template>
  <div class="chat-container">
    <div class="chat-messages" ref="msgBox">
      <!-- 图片消息显示 -->
      <div v-for="msg in messages" :key="msg.id" :class="['msg', msg.role === 'user' ? 'msg-user' : 'msg-ai']">
        <div class="msg-bubble">
          <div v-if="msg.agentName" class="agent-tag">{{ msg.agentName }}</div>
          <div v-if="msg.imageUrl" class="msg-image">
            <img :src="msg.imageUrl" alt="图片" />
          </div>
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

    <!-- 输入区域 -->
    <div class="chat-input-wrapper">
      <div class="chat-input-container">
        <!-- 模式切换 -->
        <div class="mode-switch">
          <el-dropdown @command="handleModeChange">
            <span class="mode-label">
              <component :is="currentMode.icon" class="mode-icon" />
              <span>{{ currentMode.name }}</span>
              <ArrowDown class="dropdown-icon" />
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="fast">
                  <Promotion class="mode-icon" />
                  <span>快速模式</span>
                </el-dropdown-item>
                <el-dropdown-item command="expert">
                  <School class="mode-icon" />
                  <span>专家模式</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        
        <!-- 输入框 -->
        <div class="input-wrapper">
          <el-input 
            v-model="input" 
            placeholder="发消息或输入/选择技能" 
            @keyup.enter="send" 
            :disabled="loading"
            class="chat-input"
          >
            <template #prepend>
              <el-button class="input-btn" @click="showAttachment = !showAttachment">
                <Plus />
              </el-button>
            </template>
          </el-input>
        </div>
        
        <!-- 语音按钮（不支持时隐藏） -->
        <div class="voice-btn-wrapper" v-if="voiceSupported">
          <el-button 
            class="voice-btn" 
            :class="{ recording: isRecording }"
            @mousedown="startRecording" 
            @mouseup="stopRecording" 
            @mouseleave="stopRecording"
            :disabled="!hasPermission"
            :title="hasPermission ? '按住说话' : '请先允许麦克风权限'"
          >
            <el-icon v-if="!isRecording" :size="20"><Microphone /></el-icon>
            <el-icon v-else :size="20"><CircleClose /></el-icon>
          </el-button>
        </div>
        
        <!-- 图片上传按钮 -->
        <div class="image-btn-wrapper">
          <el-button 
            class="image-btn" 
            @click="triggerImageUpload"
          >
            <el-icon :size="20"><Picture /></el-icon>
          </el-button>
          <input 
            ref="imageInput" 
            type="file" 
            accept="image/*" 
            class="image-input" 
            @change="handleImageUpload" 
          />
        </div>
      </div>
      
      <!-- 语音转文字结果显示 -->
      <div v-if="voiceText" class="voice-result">
        <span class="voice-icon"><Microphone /></span>
        <span class="voice-text">{{ voiceText }}</span>
        <el-button class="voice-confirm" @click="confirmVoiceText">确认发送</el-button>
        <el-button class="voice-cancel" @click="voiceText = ''">取消</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useUserStore } from '../../store/user'
import api from '../../api'
import { ElMessage } from 'element-plus'
import { ArrowDown, Promotion, School, Plus, Microphone, CircleClose, Picture } from '@element-plus/icons-vue'

const store = useUserStore()
const messages = ref([])
const input = ref('')
const loading = ref(false)
const msgBox = ref(null)
const showAttachment = ref(false)
const voiceSupported = ref(typeof MediaRecorder !== 'undefined' && navigator.mediaDevices && !!navigator.mediaDevices.getUserMedia)
const isRecording = ref(false)
const voiceText = ref('')
const hasPermission = ref(false)
const imageInput = ref(null)
let mediaRecorder = null
let audioChunks = []
const currentMode = ref({
  name: '快速',
  icon: Promotion,
  mode: 'fast'
})

// 是否有消息正在打字中
const hasTyping = computed(() => messages.value.some(m => m.typing))

// 模式列表
const modes = [
  { name: '快速', icon: Promotion, mode: 'fast' },
  { name: '专家', icon: School, mode: 'expert' }
]

// 切换模式
function handleModeChange(command) {
  const mode = modes.find(m => m.mode === command)
  if (mode) {
    currentMode.value = mode
  }
}

// 检查麦克风权限
async function checkPermission() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    stream.getTracks().forEach(track => track.stop())
    hasPermission.value = true
  } catch (e) {
    hasPermission.value = false
    console.log('麦克风权限未授权')
  }
}

// 开始录音
async function startRecording() {
  if (!hasPermission.value) {
    await checkPermission()
    if (!hasPermission.value) return
  }
  
  isRecording.value = true
  audioChunks = []
  
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    mediaRecorder = new MediaRecorder(stream)
    
    mediaRecorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        audioChunks.push(event.data)
      }
    }
    
    mediaRecorder.onstop = async () => {
      const audioBlob = new Blob(audioChunks, { type: 'audio/wav' })
      await sendVoiceToServer(audioBlob)
      stream.getTracks().forEach(track => track.stop())
    }
    
    mediaRecorder.start(100)
  } catch (e) {
    console.error('录音失败:', e)
    isRecording.value = false
  }
}

// 停止录音
function stopRecording() {
  if (mediaRecorder && isRecording.value) {
    isRecording.value = false
    mediaRecorder.stop()
  }
}

// 发送语音到服务器转文字
async function sendVoiceToServer(audioBlob) {
  const formData = new FormData()
  formData.append('audio', audioBlob, 'voice.wav')
  
  try {
    const res = await api.post('/chat/voice-to-text', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    voiceText.value = res.data.text
  } catch (e) {
    console.error('语音转文字失败:', e)
    voiceText.value = '语音识别失败，请重试'
  }
}

// 确认语音转文字结果
function confirmVoiceText() {
  if (voiceText.value) {
    input.value = voiceText.value
    voiceText.value = ''
  }
}

// 触发图片上传
function triggerImageUpload() {
  imageInput.value?.click()
}

// 处理图片上传
async function handleImageUpload(event) {
  const file = event.target.files?.[0]
  if (!file) return
  
  const formData = new FormData()
  formData.append('image', file)
  
  try {
    const res = await api.post('/chat/upload-image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    
    // 添加图片消息
    messages.value.push({
      id: Date.now(),
      role: 'user',
      content: '',
      imageUrl: res.data.url,
      imageBase64: res.data.base64,
      createdAt: new Date().toISOString()
    })
    
    // 发送图片给AI
    await sendImageToAI(res.data.base64)
    scrollToBottom()
  } catch (e) {
    console.error('图片上传失败:', e)
    ElMessage.error('图片上传失败')
  }
  
  // 重置input
  event.target.value = ''
}

// 发送图片给AI
async function sendImageToAI(base64Image) {
  loading.value = true
  
  try {
    const res = await api.post('/chat/send-image', {
      studentId: store.id,
      imageBase64: base64Image
    })
    
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
    typewriterEffect(aiMsg, res.data.message, 30)
  } catch (e) {
    console.error('AI处理图片失败:', e)
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

onMounted(async () => {
  checkPermission()
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
    const res = await api.post('/chat/send', { 
      studentId: store.id, 
      message: text,
      mode: currentMode.value.mode
    })
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
.chat-container { display: flex; flex-direction: column; height: calc(100vh - 80px); }
.chat-messages { flex: 1; overflow-y: auto; overflow-x: hidden; padding: 20px; background: #f5f7fa; min-height: 0; }
.chat-messages::-webkit-scrollbar { display: none; }
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

/* 输入区域 */
.chat-input-wrapper {
  padding: 12px 24px 20px; /* 往上调整，减少底部空间 */
  background: transparent;
}

.chat-input-container {
  display: flex;
  align-items: center;
  gap: 8px;
  background: white;
  border-radius: 32px;
  padding: 6px 6px 6px 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  border: 1px solid #e8e8e8;
}

/* 模式切换 */
.mode-switch {
  display: flex;
  align-items: center;
}

.mode-label {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  color: #409EFF;
  font-size: 14px;
  cursor: pointer;
  border-radius: 20px;
  background: rgba(64, 158, 255, 0.1);
  transition: all 0.3s ease;
}

.mode-label:hover {
  background: rgba(64, 158, 255, 0.2);
}

.mode-icon {
  width: 16px;
  height: 16px;
}

.dropdown-icon {
  width: 14px;
  height: 14px;
}

/* 输入框 */
.input-wrapper {
  flex: 1;
}

.chat-input {
  border: none;
  box-shadow: none;
  font-size: 14px;
}

.chat-input :deep(.el-input__inner) {
  border: none;
  box-shadow: none;
  padding: 10px 0;
}

.input-btn {
  color: #999;
  font-size: 18px;
}

.input-btn:hover {
  color: #409EFF;
}

/* 语音按钮 */
.voice-btn-wrapper {
  display: flex;
  align-items: center;
}

.voice-btn {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #f5f7fa;
  color: #666;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.voice-btn :deep(.svg-icon) {
  width: 20px;
  height: 20px;
}

.voice-btn:hover:not(:disabled) {
  background: #409EFF;
  color: white;
}

.voice-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.voice-btn.recording {
  background: #f56c6c;
  color: white;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

/* 图片上传按钮 */
.image-btn-wrapper {
  display: flex;
  align-items: center;
}

.image-btn {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #f5f7fa;
  color: #666;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.image-btn :deep(.svg-icon) {
  width: 20px;
  height: 20px;
}

.image-btn:hover {
  background: #67c23a;
  color: white;
}

.image-input {
  display: none;
}

/* 语音转文字结果 */
.voice-result {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  padding: 12px 16px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 12px;
}

.voice-icon {
  color: #409EFF;
  font-size: 18px;
}

.voice-text {
  flex: 1;
  font-size: 14px;
  color: #666;
}

.voice-confirm {
  padding: 4px 16px;
  font-size: 13px;
  background: #409EFF;
  color: white;
  border: none;
  border-radius: 20px;
  cursor: pointer;
}

.voice-confirm:hover {
  background: #67b8ff;
}

.voice-cancel {
  padding: 4px 16px;
  font-size: 13px;
  background: #f5f7fa;
  color: #666;
  border: none;
  border-radius: 20px;
  cursor: pointer;
}

.voice-cancel:hover {
  background: #e8e8e8;
}

/* 图片消息样式 */
.msg-image {
  max-width: 200px;
  margin-bottom: 8px;
  border-radius: 8px;
  overflow: hidden;
}

.msg-image img {
  width: 100%;
  height: auto;
  display: block;
}

/* 下拉菜单样式 */
.mode-switch :deep(.el-dropdown-menu) {
  border-radius: 12px;
  padding: 8px 0;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
}

.mode-switch :deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  font-size: 14px;
}

.mode-switch :deep(.el-dropdown-menu__item:hover) {
  background: #f5f7fa;
}

.msg-bubble :deep(pre) { background: #f4f4f5; padding: 8px; border-radius: 4px; overflow-x: auto; }
.msg-bubble :deep(code) { font-family: 'Consolas', monospace; font-size: 13px; }
</style>