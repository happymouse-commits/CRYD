<template>
  <div class="chat-container">
    <!-- 顶部工具栏 -->
    <div class="chat-toolbar">
      <div class="toolbar-left">
        <span class="toolbar-title">AI 辅导</span>
      </div>
      <div class="toolbar-right">
        <el-button v-if="ttsPlaying" class="stop-tts-btn" @click="stopTts" size="small" text>
          <el-icon><CircleCloseFilled /></el-icon> 停止朗读
        </el-button>
      </div>
    </div>

    <div class="chat-messages" ref="msgBox">
      <div v-for="msg in messages" :key="msg.id" :class="['msg', msg.role === 'user' ? 'msg-user' : 'msg-ai']">
        <div class="msg-bubble">
          <div v-if="msg.agentName" class="agent-tag">{{ msg.agentName }}</div>
          <div v-if="msg.imageUrl" class="msg-image">
            <img :src="msg.imageUrl" alt="图片" />
          </div>
          <div class="msg-text" v-html="renderMd(msg.typing ? msg.displayContent : msg.content)"></div>
          <span v-if="msg.typing" class="cursor">▌</span>
          <div class="msg-footer">
            <span class="msg-time">{{ formatTime(msg.createdAt) }}</span>
            <!-- AI消息播放按钮 -->
            <el-button
              v-if="msg.role === 'assistant' && !msg.typing && msg.content"
              class="play-msg-btn"
              @click="playMessage(msg)"
              :loading="msg._playing"
              size="small"
              text
            >
              <el-icon :size="14"><VideoPlay v-if="!msg._playing" /><VideoPause v-else /></el-icon>
            </el-button>
          </div>
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

        <!-- 语音按钮 -->
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
          <el-button class="image-btn" @click="triggerImageUpload">
            <el-icon :size="20"><Picture /></el-icon>
          </el-button>
          <input ref="imageInput" type="file" accept="image/*" class="image-input" @change="handleImageUpload" />
        </div>
      </div>

      <!-- 语音转文字结果 -->
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
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useUserStore } from '../../store/user'
import { marked } from 'marked'
import api from '../../api'
import { ElMessage } from 'element-plus'
import { Plus, Microphone, CircleClose, Picture, VideoPlay, VideoPause, CircleCloseFilled } from '@element-plus/icons-vue'

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

// ========== TTS 朗读状态 ==========
const ttsPlaying = ref(false)
let currentAudio = null

const hasTyping = computed(() => messages.value.some(m => m.typing))

// ========== 初始化 ==========
onMounted(async () => {
  checkPermission()
  try {
    const res = await api.get('/chat/history/' + store.id)
    messages.value = res.data || []
    scrollToBottom()
  } catch (e) {}
})

// ========== TTS 相关 ==========
function stopTts() {
  if (currentAudio) {
    currentAudio.pause()
    currentAudio = null
  }
  ttsPlaying.value = false
}

async function playMessage(msg) {
  // 如果正在播放，则停止
  if (msg._playing) {
    stopTts()
    msg._playing = false
    return
  }
  await speakText(msg.content, msg)
}

async function speakText(text, msgRef) {
  stopTts()
  if (!text) return

  ttsPlaying.value = true
  if (msgRef) msgRef._playing = true

  try {
    const res = await api.post('/chat/tts', { text }, { responseType: 'blob' })

    if (res instanceof Blob) {
      const url = URL.createObjectURL(res)
      currentAudio = new Audio(url)
      currentAudio.onended = () => {
        ttsPlaying.value = false
        if (msgRef) msgRef._playing = false
        currentAudio = null
        URL.revokeObjectURL(url)
      }
      currentAudio.onerror = () => {
        ttsPlaying.value = false
        if (msgRef) msgRef._playing = false
        currentAudio = null
        URL.revokeObjectURL(url)
      }
      currentAudio.play()
    }
  } catch (e) {
    console.error('TTS失败:', e)
    ttsPlaying.value = false
    if (msgRef) msgRef._playing = false
  }
}

// 监听打字机完成 → 自动朗读
watch(
  () => messages.value.filter(m => m.typing).length,
  (typingCount, oldCount) => {
    // 打字结束 → 自动朗读
    if (typingCount === 0 && oldCount > 0 && !ttsPlaying.value) {
      // 找到刚完成的最后一条AI消息
      const lastAi = [...messages.value].reverse().find(m => m.role === 'assistant' && m.content && !m.typing)
      if (lastAi) {
        speakText(lastAi.content, lastAi)
      }
    }
  }
)

// ========== 语音输入（讯飞STT）—— 自适应采样率 PCM WAV ==========
const TARGET_RATE = 16000
let audioCtx = null
let pcmBuffer = []       // Float32 chunks at native sample rate
let nativeSampleRate = 0 // actual device sample rate

async function checkPermission() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    stream.getTracks().forEach(track => track.stop())
    hasPermission.value = true
  } catch (e) {
    hasPermission.value = false
  }
}

async function startRecording() {
  if (!hasPermission.value) {
    await checkPermission()
    if (!hasPermission.value) return
  }
  isRecording.value = true
  pcmBuffer = []
  nativeSampleRate = 0

  try {
    // 不强制采样率，让浏览器选设备原生支持的
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    audioCtx = new AudioContext()
    nativeSampleRate = audioCtx.sampleRate
    console.log('录音采样率:', nativeSampleRate, 'Hz')

    const source = audioCtx.createMediaStreamSource(stream)
    const processor = audioCtx.createScriptProcessor(4096, 1, 1)
    source.connect(processor)
    processor.connect(audioCtx.destination)

    processor._stream = stream
    mediaRecorder = processor

    processor.onaudioprocess = (e) => {
      if (!isRecording.value) return
      pcmBuffer.push(new Float32Array(e.inputBuffer.getChannelData(0)))
    }
  } catch (e) {
    console.error('录音失败:', e)
    isRecording.value = false
  }
}

function stopRecording() {
  if (!isRecording.value) return
  isRecording.value = false

  const processor = mediaRecorder
  if (!processor) return
  mediaRecorder = null

  try {
    processor.disconnect()
    if (processor._stream) {
      processor._stream.getTracks().forEach(t => t.stop())
    }
    if (audioCtx) { audioCtx.close(); audioCtx = null }
  } catch (e) {}

  if (pcmBuffer.length === 0) return

  // 合并所有 PCM 块
  const totalLen = pcmBuffer.reduce((s, a) => s + a.length, 0)
  const merged = new Float32Array(totalLen)
  let off = 0
  for (const c of pcmBuffer) { merged.set(c, off); off += c.length }

  // 如果原生采样率不是 16000，降采样
  let samples = merged
  if (nativeSampleRate !== TARGET_RATE) {
    samples = resample(merged, nativeSampleRate, TARGET_RATE)
    console.log(`重采样: ${nativeSampleRate} -> ${TARGET_RATE} Hz, ${merged.length} -> ${samples.length} samples`)
  }

  // Float32[-1,1] → Int16
  const int16 = new Int16Array(samples.length)
  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]))
    int16[i] = s < 0 ? s * 0x8000 : s * 0x7FFF
  }

  const wav = buildWav(int16, TARGET_RATE)
  sendVoiceToServer(new Blob([wav], { type: 'audio/wav' }))
}

/** 线性插值重采样 */
function resample(data, fromRate, toRate) {
  const ratio = fromRate / toRate
  const newLen = Math.floor(data.length / ratio)
  const out = new Float32Array(newLen)
  for (let i = 0; i < newLen; i++) {
    const srcIdx = i * ratio
    const srcFloor = Math.floor(srcIdx)
    const frac = srcIdx - srcFloor
    const a = data[srcFloor] || 0
    const b = data[Math.min(srcFloor + 1, data.length - 1)] || 0
    out[i] = a + (b - a) * frac
  }
  return out
}

function buildWav(samples, sampleRate) {
  const byteRate = sampleRate * 2
  const dataSize = samples.length * 2
  const buf = new ArrayBuffer(44 + dataSize)
  const view = new DataView(buf)
  const w = (o, s) => { for (let i = 0; i < s.length; i++) view.setUint8(o + i, s.charCodeAt(i)) }
  w(0, 'RIFF'); view.setUint32(4, 36 + dataSize, true)
  w(8, 'WAVE'); w(12, 'fmt ')
  view.setUint32(16, 16, true); view.setUint16(20, 1, true)
  view.setUint16(22, 1, true); view.setUint32(24, sampleRate, true)
  view.setUint32(28, byteRate, true); view.setUint16(32, 2, true)
  view.setUint16(34, 16, true); w(36, 'data')
  view.setUint32(40, dataSize, true)
  for (let i = 0; i < samples.length; i++) view.setInt16(44 + i * 2, samples[i], true)
  return buf
}

async function sendVoiceToServer(audioBlob) {
  const formData = new FormData()
  formData.append('audio', audioBlob, 'voice.wav')
  try {
    const res = await api.post('/chat/voice-to-text', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    voiceText.value = res.data.text
  } catch (e) {
    voiceText.value = '语音识别失败，请重试'
  }
}

function confirmVoiceText() {
  if (voiceText.value) {
    input.value = voiceText.value
    voiceText.value = ''
  }
}

// ========== 图片上传 ==========
function triggerImageUpload() { imageInput.value?.click() }

async function handleImageUpload(event) {
  const file = event.target.files?.[0]
  if (!file) return
  const formData = new FormData()
  formData.append('image', file)
  try {
    const res = await api.post('/chat/upload-image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    messages.value.push({
      id: Date.now(), role: 'user', content: '',
      imageUrl: res.data.url, imageBase64: res.data.base64,
      createdAt: new Date().toISOString()
    })
    await sendImageToAI(res.data.base64)
    scrollToBottom()
  } catch (e) {
    ElMessage.error('图片上传失败')
  }
  event.target.value = ''
}

async function sendImageToAI(base64Image) {
  loading.value = true
  try {
    const res = await api.post('/chat/send-image', { studentId: store.id, imageBase64: base64Image })
    const aiMsg = {
      id: Date.now() + 1, role: 'assistant', content: res.data.message,
      displayContent: '', agentName: res.data.agentName,
      createdAt: new Date().toISOString(), typing: false
    }
    messages.value.push(aiMsg)
    typewriterEffect(aiMsg, res.data.message, 20)
  } catch (e) {
    ElMessage.error('图片处理失败，请重试')
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

// ========== 文字消息 ==========
async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return

  stopTts()

  messages.value.push({ id: Date.now(), role: 'user', content: text, createdAt: new Date().toISOString() })
  input.value = ''
  loading.value = true
  await nextTick()
  scrollToBottom()

  try {
    const res = await api.post('/chat/send', { studentId: store.id, message: text })
    // 兼容两种返回格式：res.data.data.message 或 res.data.message
    const payload = res.data?.data || res.data
    const reply = payload?.message || ''
    const agent = payload?.agentName || '辅导老师'

    const aiMsg = {
      id: Date.now() + 1, role: 'assistant', content: reply,
      displayContent: '', agentName: agent,
      createdAt: new Date().toISOString(), typing: false
    }
    messages.value.push(aiMsg)
    await nextTick()
    scrollToBottom()
    if (reply) typewriterEffect(aiMsg, reply, 20)
  } catch (e) {
    console.error('发送失败', e)
    ElMessage.error('发送失败，请重试')
    messages.value.pop()
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

// ========== 工具函数 ==========
function renderMd(text) {
  if (!text) return ''
  return marked.parse(text)
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

function typewriterEffect(msg, fullText, speed = 20) {
  let i = 0
  msg.displayContent = ''
  msg.typing = true
  const timer = setInterval(() => {
    if (i < fullText.length) {
      const step = /[一-鿿]/.test(fullText[i]) ? 1 : Math.min(3, fullText.length - i)
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
</script>

<style scoped>
.chat-container { display: flex; flex-direction: column; height: calc(100vh - 80px); }

/* 顶部工具栏 */
.chat-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 20px; background: var(--t-surface); border-bottom: 1px solid var(--t-wash);
  flex-shrink: 0;
}
.toolbar-left { display: flex; align-items: center; gap: 8px; }
.toolbar-title { font-size: 16px; font-weight: 600; color: var(--t-line); }
.toolbar-right { display: flex; align-items: center; gap: 12px; }
.stop-tts-btn { color: #a14a3d; }

.chat-messages { flex: 1; overflow-y: auto; overflow-x: hidden; padding: 20px; background: var(--t-ground); min-height: 0; }
.chat-messages::-webkit-scrollbar { display: none; }
.msg { display: flex; margin-bottom: 16px; animation: msgIn 0.3s ease; }
@keyframes msgIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }

.msg-user { justify-content: flex-end; }
.msg-ai { justify-content: flex-start; }
.msg-bubble { max-width: 70%; padding: 12px 16px; border-radius: 12px; font-size: 14px; line-height: 1.6; position: relative; }
.msg-user .msg-bubble { background: var(--t-accent); color: var(--t-surface); }
.msg-ai .msg-bubble { background: var(--t-surface); color: var(--t-line); box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.agent-tag { font-size: 12px; color: var(--t-accent); margin-bottom: 4px; font-weight: bold; }

.msg-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 4px; }
.msg-time { font-size: 11px; color: var(--t-line-subtle); }
.play-msg-btn { color: var(--t-line-dim); padding: 2px 4px; }
.play-msg-btn:hover { color: var(--t-accent); }

.cursor { color: var(--t-accent); font-weight: bold; animation: blink 0.8s infinite; }
@keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0; } }

.thinking { display: flex; align-items: center; gap: 4px; padding: 16px 20px; }
.dot {
  width: 8px; height: 8px; border-radius: 50%; background: var(--t-accent);
  animation: bounce 1.2s infinite;
}
.dot:nth-child(2) { animation-delay: 0.2s; } .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce { 0%,60%,100% { transform: translateY(0); opacity: 0.4; } 30% { transform: translateY(-8px); opacity: 1; } }

/* 输入区域 */
.chat-input-wrapper { padding: 12px 24px 20px; background: transparent; }
.chat-input-container {
  display: flex; align-items: center; gap: 8px;
  background: var(--t-surface); border-radius: 32px; padding: 6px 6px 6px 12px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08); border: 1px solid var(--t-wash);
}
.input-wrapper { flex: 1; }
.chat-input { border: none; box-shadow: none; font-size: 14px; }
.chat-input :deep(.el-input__inner) { border: none; box-shadow: none; padding: 10px 0; }
.input-btn { color: var(--t-line-dim); font-size: 18px; }
.input-btn:hover { color: var(--t-accent); }

.voice-btn-wrapper, .image-btn-wrapper { display: flex; align-items: center; }
.voice-btn, .image-btn {
  width: 44px; height: 44px; border-radius: 50%;
  background: var(--t-surface-muted); color: var(--t-line-dim); font-size: 18px;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.3s ease;
}
.voice-btn:hover:not(:disabled) { background: var(--t-accent); color: var(--t-surface); }
.voice-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.voice-btn.recording { background: #a14a3d; color: var(--t-surface); animation: pulse 1.5s infinite; }
@keyframes pulse { 0%,100% { transform: scale(1); } 50% { transform: scale(1.05); } }
.image-btn:hover { background: var(--t-status-ready); color: var(--t-surface); }
.image-input { display: none; }

.voice-result {
  display: flex; align-items: center; gap: 12px; margin-top: 12px;
  padding: 12px 16px; background: var(--t-accent-soft); border: 1px solid var(--t-wash); border-radius: 12px;
}
.voice-icon { color: var(--t-accent); font-size: 18px; }
.voice-text { flex: 1; font-size: 14px; color: var(--t-line-dim); }
.voice-confirm {
  padding: 4px 16px; font-size: 13px; background: var(--t-accent); color: var(--t-surface);
  border: none; border-radius: 20px; cursor: pointer;
}
.voice-confirm:hover { background: #c97930; }
.voice-cancel {
  padding: 4px 16px; font-size: 13px; background: var(--t-surface-muted); color: var(--t-line-dim);
  border: none; border-radius: 20px; cursor: pointer;
}
.voice-cancel:hover { background: var(--t-wash); }

/* 图片消息 */
.msg-image { max-width: 200px; margin-bottom: 8px; border-radius: 8px; overflow: hidden; }
.msg-image img { width: 100%; height: auto; display: block; }

.msg-text :deep(p) { margin: 0 0 6px; }
.msg-text :deep(ul), .msg-text :deep(ol) { margin: 4px 0; padding-left: 18px; }
.msg-text :deep(li) { margin-bottom: 2px; }
.msg-text :deep(h2) { font-size: 17px; margin: 12px 0 6px; }
.msg-text :deep(h3) { font-size: 15px; margin: 10px 0 4px; }
.msg-text :deep(h4) { font-size: 14px; margin: 8px 0 4px; }
.msg-text :deep(blockquote) { border-left: 3px solid var(--t-accent); padding: 4px 10px; margin: 6px 0; background: var(--t-accent-soft); border-radius: 0 4px 4px 0; }
.msg-text :deep(table) { border-collapse: collapse; margin: 6px 0; }
.msg-text :deep(th), .msg-text :deep(td) { border: 1px solid var(--t-wash); padding: 4px 8px; }
.msg-bubble :deep(pre) { background: var(--t-surface-muted); padding: 8px; border-radius: 4px; overflow-x: auto; }
.msg-bubble :deep(code) { font-family: 'Consolas', monospace; font-size: 13px; }
.msg-bubble :deep(pre code) { background: none; padding: 0; }
</style>
