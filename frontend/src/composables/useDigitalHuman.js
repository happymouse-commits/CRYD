/**
 * useDigitalHuman — 数字人交互接口
 *
 * 提供"数字人对话卡片"所需的所有接口：
 * - 发送消息到后端 AI
 * - 加载历史消息
 * - 语音输入（按住说话）
 * - 打字机效果
 */

import { ref, reactive, computed, nextTick } from 'vue'
import { useUserStore } from '../store/user'
import api from '../api'

export function useDigitalHuman() {
  const store = useUserStore()

  // ----- 状态 -----
  const messages = ref([])
  const loading = ref(false)
  const inputText = ref('')
  const msgBoxRef = ref(null)

  const state = reactive({
    mode: 'idle',           // idle | listening | thinking | speaking | error
    avatarEmoji: '🤖',
    avatarUrl: '/avatar.jpg',  // 数字人形象照片（首页左下角显示）
    avatar3dUrl: '',        // 3D 模型 URL（.glb，渐进增强）
    modelViewerLoaded: false, // model-viewer 组件是否加载成功（默认false，渐进增强）
    statusText: '在线中',
    isOnline: true,
    isRecording: false,
    lastError: null,
  })

  const hasTyping = computed(() => messages.value.some(m => m.typing))

  // ----- 初始化 -----
  // 检测 model-viewer 是否加载成功（渐进增强）
  if (typeof window !== 'undefined' && window.__modelViewerReady) {
    state.modelViewerLoaded = true
  }

  async function loadHistory() {
    try {
      const res = await api.get('/chat/history/' + store.id)
      messages.value = res.data || []
      scrollToBottom()
    } catch (e) {
      console.warn('加载历史失败:', e)
    }
  }

  // ----- 核心：发送消息 -----
  async function sendMessage(text) {
    const msg = (text || inputText.value).trim()
    if (!msg || loading.value) return

    // 1. 添加用户消息
    messages.value.push({
      id: Date.now(),
      role: 'user',
      content: msg,
      createdAt: new Date().toISOString()
    })
    inputText.value = ''
    loading.value = true
    state.mode = 'thinking'
    state.statusText = '思考中...'
    scrollToBottom()

    try {
      // 调用真实后端 API
      const res = await api.post('/chat/send', {
        studentId: store.id,
        message: msg
      })
      const data = res.data

      // 2. 添加 AI 消息
      const aiMsg = {
        id: Date.now() + 1,
        role: 'ai',
        content: data.message,
        displayContent: '',
        agentName: data.agentName || '小智老师',
        createdAt: new Date().toISOString(),
        typing: false,
      }
      messages.value.push(aiMsg)

      // 3. 启动打字效果
      typewriteEffect(aiMsg, data.message, 18)

      state.mode = 'idle'
      state.statusText = '在线中'
    } catch (e) {
      state.mode = 'error'
      state.lastError = e.message || '发送失败'
      state.statusText = '连接失败'

      // 添加错误消息
      messages.value.push({
        id: Date.now() + 1,
        role: 'system',
        content: '消息发送失败，请重试',
        createdAt: new Date().toISOString(),
        error: true,
      })
    } finally {
      loading.value = false
      scrollToBottom()
    }
  }

  // ----- 语音输入 -----
  const voiceSupported = computed(() => {
    return typeof MediaRecorder !== 'undefined' &&
           navigator.mediaDevices &&
           !!navigator.mediaDevices.getUserMedia
  })

  let mediaRecorder = null
  let audioChunks = []

  async function startRecording() {
    if (!voiceSupported.value) return
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      mediaRecorder = new MediaRecorder(stream)
      state.isRecording = true
      state.mode = 'listening'
      audioChunks = []

      mediaRecorder.ondataavailable = e => {
        if (e.data.size > 0) audioChunks.push(e.data)
      }
      mediaRecorder.onstop = async () => {
        stream.getTracks().forEach(t => t.stop())
        state.isRecording = false
        state.mode = 'idle'

        // 发送语音到后端转文字
        const blob = new Blob(audioChunks, { type: 'audio/wav' })
        const formData = new FormData()
        formData.append('audio', blob, 'voice.wav')
        try {
          const res = await api.post('/chat/voice-to-text', formData, {
            headers: { 'Content-Type': 'multipart/form-data' }
          })
          if (res.data && res.data.text) {
            inputText.value = res.data.text
          }
        } catch (e) {
          console.warn('语音转文字失败:', e)
          inputText.value = '语音识别失败，请重试'
        }
      }
      mediaRecorder.start(100)
    } catch (e) {
      state.isRecording = false
      state.mode = 'idle'
      console.warn('录音失败:', e)
    }
  }

  function stopRecording() {
    if (mediaRecorder && state.isRecording) {
      mediaRecorder.stop()
    }
  }

  // ----- 打字机效果 -----
  function typewriteEffect(msgObj, fullText, speed = 20) {
    let i = 0
    msgObj.displayContent = ''
    msgObj.typing = true
    state.mode = 'speaking'
    state.statusText = '说话中...'

    const timer = setInterval(() => {
      if (i < fullText.length) {
        const step = /[一-鿿]/.test(fullText[i]) ? 1 : Math.min(3, fullText.length - i)
        msgObj.displayContent = fullText.substring(0, i + step)
        i += step
        scrollToBottom()
      } else {
        clearInterval(timer)
        msgObj.typing = false
        msgObj.displayContent = fullText
        state.mode = 'idle'
        state.statusText = '在线中'
      }
    }, speed)
  }

  // ----- 滚动 -----
  function scrollToBottom() {
    nextTick(() => {
      const el = msgBoxRef.value
      if (el) el.scrollTop = el.scrollHeight
    })
  }

  // ----- 清空对话 -----
  function clearMessages() {
    messages.value = []
  }

  // ----- 数字人主动发起引导 -----
  async function initOnboarding() {
    if (messages.value.length > 0 || loading.value) return
    loading.value = true
    state.mode = 'thinking'
    state.statusText = '思考中...'
    try {
      // 先查画像完整度
      let needOnboarding = true
      try {
        const statusRes = await api.get('/onboarding/status/' + store.id)
        needOnboarding = statusRes.data.needOnboarding !== false
      } catch {}
      // 发引导触发词给 AI
      const res = await api.post('/chat/send', {
        studentId: store.id,
        message: needOnboarding ? '__START_ONBOARDING__' : '__GREETING__'
      })
      const data = res.data
      const aiMsg = {
        id: Date.now(),
        role: 'ai',
        content: data.message,
        displayContent: '',
        agentName: data.agentName || '小智老师',
        createdAt: new Date().toISOString(),
        typing: false,
      }
      messages.value.push(aiMsg)
      typewriteEffect(aiMsg, data.message, 18)
      state.mode = 'idle'
      state.statusText = '在线中'
    } catch (e) {
      state.mode = 'idle'
      state.statusText = '在线中'
      console.warn('数字人首发失败:', e)
    } finally {
      loading.value = false
      scrollToBottom()
    }
  }

  // ============================================================
  // 返回给组件使用的接口
  // ============================================================
  return {
    messages,
    inputText,
    loading,
    msgBoxRef,
    state,
    hasTyping,
    voiceSupported,
    sendMessage,
    loadHistory,
    startRecording,
    stopRecording,
    clearMessages,
    initOnboarding,
    /**
     * 更换数字人形象
     * @param {string} urlOrId - 图片URL 或 ReadyPlayerMe avatar ID
     * @param {'2d'|'3d'} type - 类型
     */
    setAvatar(urlOrId, type = '2d') {
      if (type === '3d') {
        state.avatar3dUrl = 'https://models.readyplayer.me/' + urlOrId + '.glb';
      } else {
        state.avatarUrl = urlOrId;
      }
    },
  }
}
