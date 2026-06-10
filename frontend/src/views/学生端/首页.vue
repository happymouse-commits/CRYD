<template>
  <div class="dashboard">
    <!-- 三列主体 -->
    <div class="dash-grid">

      <!-- ===== 左列: 数字人对话 ===== -->
      <div class="col col-left">
        <div class="card card-chat">
          <div class="card-head">
            <span class="ch-icon">🤖</span>
            <span>数字人对话</span>
            <span class="jump-link" @click="$router.push('/student/chat')">全屏 →</span>
          </div>
          <!-- 数字人身份 -->
          <div class="chat-avatar-bar">
            <div class="ca-img">{{ dh.state.avatarEmoji }}</div>
            <div class="ca-info">
              <span class="ca-name">小智老师</span>
              <span class="ca-online" :class="{ offline: !dh.state.isOnline }">{{ dh.state.statusText }}</span>
            </div>
          </div>
          <!-- 消息区 -->
          <div class="chat-msgs" ref="dh.msgBoxRef">
            <template v-for="msg in dh.messages" :key="msg.id">
              <div v-if="msg.role === 'user'" class="msg msg-me">{{ msg.content }}</div>
              <div v-else-if="msg.role === 'system'" class="msg msg-sys">{{ msg.content }}</div>
              <div v-else class="msg msg-ai">
                <span v-if="msg.agentName" class="msg-agent-tag">{{ msg.agentName }}</span>
                <span v-if="msg.typing">{{ msg.displayContent }}<span class="typing-cursor">▌</span></span>
                <span v-else v-html="renderMd(msg.content)"></span>
              </div>
            </template>
            <div v-if="dh.messages.length === 0 && !dh.loading" class="msg msg-ai">
              你好呀！我是你的 AI 学习助手 🎓<br>有什么 C 语言问题尽管问我～
            </div>
            <div v-if="dh.loading" class="msg msg-ai">
              <span class="thinking-dots"><span>●</span><span>●</span><span>●</span></span>
            </div>

            <!-- ☆ 数字人 3D 虚拟形象 — 左下角 -->
            <div class="digital-human-avatar" :class="dh.state.mode">
              <div class="dh-avatar-ring" :class="dh.state.mode"></div>
              <div class="dh-avatar-inner">
                <model-viewer
                  :src="'https://models.readyplayer.me/' + dh.state.avatarId + '.glb'"
                  camera-target="0m 1.5m 0m"
                  camera-orbit="0deg 75deg 2.5m"
                  field-of-view="30deg"
                  exposure="1"
                  shadow-intensity="0"
                  environment-image="neutral"
                  auto-rotate
                  rotation-per-second="15deg"
                  interaction-prompt="none"
                  class="dh-3d-viewer"
                  loading="lazy"
                ></model-viewer>
              </div>
              <!-- 对话气泡 -->
              <div class="dh-speech-bubble" v-if="dh.state.mode === 'speaking' || dh.state.mode === 'thinking'">
                <span v-if="dh.state.mode === 'thinking'">...</span>
                <span v-else>💬</span>
              </div>
            </div>
          </div>
          <!-- 输入区 -->
          <div class="chat-input-row">
            <button v-if="dh.voiceSupported" class="voice-mini-btn" :class="{ recording: dh.state.isRecording }" @mousedown="dh.startRecording" @mouseup="dh.stopRecording" @mouseleave="dh.stopRecording" title="按住说话">🎤</button>
            <input v-model="dh.inputText" placeholder="输入你的问题或答案..." @keyup.enter="sendMsg" :disabled="dh.loading">
            <button class="send-btn" @click="sendMsg" :disabled="dh.loading">发送</button>
          </div>
        </div>
      </div>

      <!-- ===== 中列: 上·画像 + 下·资源中心 ===== -->
      <div class="col col-mid">

        <!-- 我的画像 -->
        <div class="card card-profile">
          <div class="card-head">
            <span class="ch-icon">📊</span>
            <span>我的画像</span>
            <span class="refresh-link" @click="loadProfile">刷新</span>
          </div>
          <div class="pf-stats">
            <div class="stat-chip"><span class="sc-val">{{ profileStats.completedLevels }}</span>已通关卡</div>
            <div class="stat-chip"><span class="sc-val">{{ profileStats.correctRate }}%</span>正确率</div>
            <div class="stat-chip"><span class="sc-val">{{ profileStats.studyHours }}h</span>学习时长</div>
          </div>
          <div class="pf-radar">
            <div ref="radarChartRef" style="width:100%;height:200px"></div>
          </div>
        </div>

        <!-- 资源中心 -->
        <div class="card card-res">
          <div class="card-head">
            <span class="ch-icon">📁</span>
            <span>资源中心</span>
            <span class="refresh-link" @click="loadResources">刷新</span>
          </div>
          <div class="res-section" v-if="recentResources.length > 0">
            <div class="res-sec-title"><span class="pulse-dot"></span> 最新生成</div>
            <div class="res-list">
              <div v-for="res in recentResources" :key="res.id" class="res-row new" @click="viewResource(res)">
                <div class="rr-icon">{{ getResIcon(res.type) }}</div>
                <div class="rr-body">
                  <div class="rr-title">{{ res.title || '未命名资源' }}</div>
                  <div class="rr-meta">{{ formatTime(res.createdAt || res.updatedAt) }} · 来自: {{ res.knowledgePoint || 'AI生成' }}</div>
                </div>
                <span class="rr-tag" :class="getResTagClass(res.type)">{{ getResTypeName(res.type) }}</span>
              </div>
            </div>
          </div>
          <div class="res-section">
            <div class="res-sec-title">📂 全部资源</div>
            <div class="res-list">
              <div v-for="res in allResources.slice(0, 3)" :key="'all-'+res.id" class="res-row" @click="viewResource(res)">
                <div class="rr-icon">{{ getResIcon(res.type) }}</div>
                <div class="rr-body">
                  <div class="rr-title">{{ res.title || '未命名资源' }}</div>
                  <div class="rr-meta">{{ formatTime(res.createdAt) }} · 来自: {{ res.knowledgePoint || '系统' }}</div>
                </div>
              </div>
              <div v-if="allResources.length === 0 && recentResources.length === 0" class="res-row" style="color:#9ca3af;justify-content:center">
                暂无资源，去AI辅导页对话生成吧～
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== 右列: 学习路径 ===== -->
      <div class="col col-right">
        <div class="card card-path">
          <div class="card-head">
            <span class="ch-icon">🗺️</span>
            <span>学习路径</span>
            <span class="refresh-link" @click="loadPath">刷新</span>
          </div>
          <div class="path-list" v-if="pathSteps.length > 0">
            <div v-for="(step, idx) in pathSteps" :key="idx"
                 :class="['p-node', step.status]">
              <div class="pn-icon">{{ step.icon }}</div>
              <div class="pn-body">
                <div class="pn-title">{{ step.title }}</div>
                <div class="pn-desc">{{ step.desc }}</div>
              </div>
              <span class="pn-badge" :class="step.status + '-badge'">{{ step.badge }}</span>
            </div>
          </div>
          <div v-else class="path-empty">
            <p>暂无学习路径</p>
            <p class="path-hint">完成引导对话后自动生成</p>
          </div>
        </div>
      </div>

    </div>

    <!-- 资源详情弹窗 -->
    <el-dialog v-model="showResDialog" :title="viewingRes?.title" width="600px">
      <div class="res-detail" v-html="renderMd(viewingRes?.content || '暂无内容')"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, watch } from 'vue'
import { useDigitalHuman } from '../../composables/useDigitalHuman'
import { useUserStore } from '../../store/user'
import { marked } from 'marked'
import * as echarts from 'echarts'
import api from '../../api'

const dh = useDigitalHuman()
const store = useUserStore()

// ===== 画像 =====
const radarChartRef = ref(null)
let radarChart = null

const profileStats = reactive({
  completedLevels: 0,
  correctRate: 0,
  studyHours: 0
})

const profileData = ref({
  knowledgeLevel: 0,
  cognitiveStyle: 'visual',
  learningPreference: 'mixed',
  learningPace: 'steady',
  interestDirection: '',
  weakAreas: '',
  studyMotivation: '',
  focusLevel: '',
  totalStudyMinutes: 0,
  progress: 0
})

async function loadProfile() {
  try {
    // 获取学生画像
    const res = await api.get('/profile/' + store.id)
    if (res.data) {
      Object.assign(profileData.value, res.data)
    }
    // 获取错题统计
    try {
      const statsRes = await api.get('/evaluation/weakness/' + store.id)
      if (statsRes.data) {
        // total 是 active 错题总数 → 错题越多正确率越低
        const errorTotal = statsRes.data.total || 0
        profileStats.correctRate = errorTotal === 0 ? 100 : Math.max(30, 100 - errorTotal * 5)
        profileStats.completedLevels = statsRes.data.weakPoints ? statsRes.data.weakPoints.length : 0
      }
    } catch {}
    // 学习时长
    if (profileData.value.totalStudyMinutes) {
      profileStats.studyHours = Math.round(profileData.value.totalStudyMinutes / 60)
    } else {
      profileStats.studyHours = 0
    }

    nextTick(() => renderRadar())
  } catch (e) {
    console.warn('加载画像失败:', e)
  }
}

function renderRadar() {
  if (!radarChartRef.value) return
  if (radarChart) radarChart.dispose()

  radarChart = echarts.init(radarChartRef.value)
  const pd = profileData.value
  radarChart.setOption({
    radar: {
      center: ['50%', '55%'],
      radius: '65%',
      indicator: [
        { name: '知识掌握', max: 100 },
        { name: '学习速度', max: 100 },
        { name: '正确率', max: 100 },
        { name: '学习时长', max: 100 },
        { name: '互动频率', max: 100 },
        { name: '薄弱项识别', max: 100 },
      ],
      axisName: { fontSize: 9, color: '#6b7280' },
      splitArea: { areaStyle: { color: ['#fff', '#f8faff', '#fff', '#f8faff', '#fff'] } }
    },
    series: [{
      type: 'radar',
      data: [{
        value: [
          pd.knowledgeLevel || 0,
          pd.learningPace === 'fast' ? 80 : pd.learningPace === 'steady' ? 50 : 30,
          profileStats.correctRate || 0,
          Math.min(100, (pd.totalStudyMinutes || 0) / 60 * 5),
          Math.min(100, (pd.progress || 0) * 5),
          pd.weakAreas ? Math.min(100, (pd.weakAreas.length || 0) * 10 + 20) : 10,
        ],
        name: '学习画像',
      }],
      areaStyle: { color: 'rgba(91,141,239,0.15)' },
      lineStyle: { color: '#5b8def', width: 2 },
      itemStyle: { color: '#5b8def' },
      symbolSize: 4,
      label: { show: false },
    }]
  })
}

// ===== 资源 =====
const recentResources = ref([])
const allResources = ref([])
const showResDialog = ref(false)
const viewingRes = ref(null)

async function loadResources() {
  try {
    const res = await api.get('/resources/student/' + store.id)
    if (res.data && res.data.length) {
      allResources.value = res.data.filter(r => r.type !== 'mindmap' || r.generatedBy)
      // 最近3个作为"最新生成"
      recentResources.value = allResources.value
        .filter(r => r.generatedBy)
        .sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
        .slice(0, 3)
    }
  } catch (e) {
    console.warn('加载资源失败:', e)
  }
}

function viewResource(res) {
  viewingRes.value = res
  showResDialog.value = true
}

function getResIcon(type) {
  const icons = { article: '📄', exercise: '📝', explanation: '💡', mindmap: '🧠', code: '💻', video: '🎬', ppt: '📊' }
  return icons[type] || '📚'
}

function getResTypeName(type) {
  const names = { article: '文章', exercise: '练习', explanation: '讲解', mindmap: '导图', code: '代码', video: '视频', ppt: 'PPT' }
  return names[type] || type || '其他'
}

function getResTagClass(type) {
  const classes = { article: 'art', exercise: 'ex', explanation: 'exp', mindmap: 'mind', code: 'code', video: 'vid', ppt: 'ppt' }
  return classes[type] || ''
}

// ===== 学习路径 =====
const pathSteps = ref([])

async function loadPath() {
  try {
    const res = await api.get('/learning-path/student/' + store.id + '/active')
    if (res.data && res.data.steps) {
      // 解析 Markdown 步骤
      const rawSteps = res.data.steps
      const lines = rawSteps.split('\n').filter(l => l.trim())
      const currentStep = res.data.currentStep || 1
      pathSteps.value = lines.map((line, idx) => {
        const stepNum = idx + 1
        let status = 'locked'
        let icon = '🔒'
        let badge = '未解锁'
        if (stepNum < currentStep) {
          status = 'done'; icon = '✅'; badge = '通关'
        } else if (stepNum === currentStep) {
          status = 'current'; icon = '⚡'; badge = '攻略中'
        }
        // 清理 markdown 前缀
        const clean = line.replace(/^[#*\-\s\d.]+/, '').trim()
        const parts = clean.split('·')
        return {
          title: '第' + stepNum + '关 · ' + (parts[0] || clean),
          desc: parts[1] || '',
          status, icon, badge
        }
      })
      // 最多显示6关
      if (pathSteps.value.length > 6) pathSteps.value = pathSteps.value.slice(0, 6)
    } else {
      pathSteps.value = []
    }
  } catch (e) {
    console.warn('加载学习路径失败:', e)
  }
}

// ===== 对话 =====
function sendMsg() {
  dh.sendMessage()
}

// ===== 工具函数 =====
function renderMd(text) {
  if (!text) return ''
  return marked.parse(text)
}

function formatTime(t) {
  if (!t) return '更早'
  // "2分钟前" 格式
  const now = new Date()
  const date = new Date(t)
  const diff = Math.floor((now - date) / 1000 / 60)
  if (diff < 1) return '刚刚'
  if (diff < 60) return diff + '分钟前'
  if (diff < 1440) return Math.floor(diff / 60) + '小时前'
  if (diff < 43200) return Math.floor(diff / 1440) + '天前'
  return t.replace('T', ' ').substring(0, 10)
}

// ===== 初始化 =====
onMounted(async () => {
  await dh.loadHistory()
  // ★ 首次访问无历史 → 数字人主动发起引导对话
  if (dh.messages.value.length === 0) {
    setTimeout(() => dh.initOnboarding(), 500)
  }
  loadProfile()
  loadResources()
  loadPath()
})

// 窗口缩放时重绘雷达图
window.addEventListener('resize', () => {
  radarChart?.resize()
})
</script>

<style scoped>
/* ===== 布局 ===== */
.dashboard { height: 100%; min-height: 0; }
.dash-grid {
  display: flex; gap: 12px;
  height: calc(100vh - 50px - 34px);
  min-height: 0;
}

/* ===== 列 ===== */
.col { display: flex; flex-direction: column; gap: 12px; min-width: 0; }
.col-left { flex: 2; }
.col-mid { flex: 1.15; }
.col-right { flex: 0.95; }

/* ===== 通用卡片 ===== */
.card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 1px 8px rgba(0,0,0,0.04), 0 1px 2px rgba(0,0,0,0.03);
  border: 1px solid #eef0f4;
  display: flex; flex-direction: column;
  overflow: hidden; min-height: 0;
}
.card-head {
  flex-shrink: 0;
  padding: 12px 16px 0;
  display: flex; align-items: center; gap: 6px;
  font-size: 12.5px; font-weight: 700;
  color: #374151;
}
.ch-icon { font-size: 15px; }
.jump-link {
  margin-left: auto; font-size: 10px; color: #5b8def;
  cursor: pointer; font-weight: 500;
}
.jump-link:hover { text-decoration: underline; }
.refresh-link {
  margin-left: auto; font-size: 10px; color: #9ca3af;
  cursor: pointer; font-weight: 500;
}
.refresh-link:hover { color: #5b8def; }

/* ===== 数字人对话卡 ===== */
.card-chat { flex: 1; }

.chat-avatar-bar {
  flex-shrink: 0;
  display: flex; align-items: center; gap: 10px;
  padding: 10px 16px; margin: 6px 16px;
  background: #f8faff; border-radius: 12px;
  border: 1px solid #eef0f5;
}
.ca-img {
  width: 40px; height: 40px; border-radius: 12px;
  background: linear-gradient(135deg, #5b8def, #7c5cfc);
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 20px; flex-shrink: 0;
}
.ca-info { display: flex; flex-direction: column; }
.ca-name { font-size: 13px; font-weight: 700; color: #1a1a2e; }
.ca-online { font-size: 10px; color: #34d399; }
.ca-online.offline { color: #f87171; }

.chat-msgs {
  flex: 1; overflow-y: auto; padding: 4px 16px 8px;
  display: flex; flex-direction: column; gap: 7px;
  position: relative; /* 为 3D 虚拟人定位锚点 */
}
.msg {
  max-width: 78%; padding: 9px 12px; border-radius: 12px;
  font-size: 12px; line-height: 1.7;
}
.msg-ai {
  align-self: flex-start;
  background: #f5f7fa; color: #374151;
  border-bottom-left-radius: 4px;
}
.msg-sys {
  align-self: center;
  background: #fef2f2; color: #dc2626;
  border-radius: 8px;
  font-size: 11px; padding: 5px 12px;
}
.msg-agent-tag { font-size: 9px; color: #5b8def; font-weight: 600; display: block; margin-bottom: 2px; }
.typing-cursor { color: #5b8def; animation: blink 0.8s infinite; }
@keyframes blink { 0%,100%{opacity:1} 50%{opacity:0} }
.msg-me {
  align-self: flex-end;
  background: linear-gradient(135deg, #5b8def, #6d9cf7);
  color: #fff; border-bottom-right-radius: 4px;
}
.msg code {
  background: rgba(0,0,0,0.06); color: #5b8def;
  padding: 1px 6px; border-radius: 5px;
  font-family: 'Consolas', 'Courier New', monospace; font-size: 11px;
}
.thinking-dots { display: flex; gap: 4px; }
.thinking-dots span {
  width: 6px; height: 6px; border-radius: 50%; background: #9ca3af;
  animation: bounce-dot 1.2s infinite;
}
.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce-dot { 0%,60%,100%{opacity:0.2;transform:translateY(0)} 30%{opacity:1;transform:translateY(-4px)} }

.chat-input-row {
  flex-shrink: 0; display: flex; gap: 6px;
  padding: 8px 16px 12px;
  align-items: center;
}
.voice-mini-btn {
  width: 32px; height: 32px; border-radius: 10px;
  border: 1px solid #e5e7eb; background: #f5f7fa;
  font-size: 14px; cursor: pointer; display: flex;
  align-items: center; justify-content: center;
  transition: all 0.2s; flex-shrink: 0;
}
.voice-mini-btn:hover { background: #fff; border-color: #5b8def; }
.voice-mini-btn.recording { background: #f87171; color: #fff; border-color: #f87171; animation: pulse-rec 1.5s infinite; }
@keyframes pulse-rec { 0%,100%{transform:scale(1)} 50%{transform:scale(1.08)} }
.chat-input-row input {
  flex: 1;
  background: #f5f7fa; border: 1px solid #e5e7eb;
  border-radius: 12px; padding: 9px 14px;
  font-size: 12px; color: #1a1a2e; outline: none;
  transition: border-color 0.2s;
}
.chat-input-row input:focus { border-color: #5b8def; background: #fff; }
.send-btn {
  background: linear-gradient(135deg, #5b8def, #6d9cf7);
  border: none; color: #fff; padding: 9px 18px;
  border-radius: 12px; cursor: pointer;
  font-size: 13px; font-weight: 600;
  transition: opacity 0.18s; white-space: nowrap;
}
.send-btn:hover { opacity: 0.9; }
.send-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* ===== 我的画像 ===== */
.card-profile { flex: 1; }
.pf-stats {
  display: flex; gap: 6px; padding: 8px 16px 0; flex-shrink: 0;
}
.stat-chip {
  flex: 1; background: #f8faff; border-radius: 10px;
  padding: 7px 4px; text-align: center; font-size: 9px; color: #9ca3af;
}
.sc-val { font-size: 16px; font-weight: 800; color: #1a1a2e; display: block; }
.pf-radar {
  flex: 1; display: flex; align-items: center; justify-content: center;
  padding: 4px 8px;
}

/* ===== 资源中心 ===== */
.card-res { flex: 2; }
.res-section { padding: 0 16px; }
.res-section + .res-section { margin-top: 4px; }
.res-sec-title {
  font-size: 10.5px; font-weight: 700; color: #6b7280;
  padding: 4px 0; display: flex; align-items: center; gap: 5px;
}
.pulse-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: #34d399; flex-shrink: 0;
  animation: pulse 1.6s infinite;
}
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.25; } }

.res-list {
  display: flex; flex-direction: column; gap: 3px;
  overflow-y: auto; max-height: 220px;
}
.res-row {
  display: flex; align-items: center; gap: 8px;
  padding: 7px 10px; border-radius: 10px; cursor: pointer;
  background: #fafbfd; border: 1px solid transparent;
  transition: all 0.18s;
}
.res-row:hover { background: #f0f4ff; border-color: #dbeafe; }
.res-row .rr-icon {
  font-size: 18px; flex-shrink: 0;
  width: 32px; height: 32px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  background: #f5f7fa;
}
.rr-body { flex: 1; min-width: 0; }
.rr-title { font-weight: 700; font-size: 12px; color: #1a1a2e; }
.rr-meta {
  font-size: 9px; color: #9ca3af; margin-top: 2px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.rr-tag {
  padding: 2px 7px; border-radius: 6px;
  font-size: 9px; font-weight: 700; flex-shrink: 0;
}
.rr-tag.ppt { background: rgba(245,158,11,0.1); color: #f59e0b; }
.rr-tag.mind { background: rgba(124,92,252,0.1); color: #7c5cfc; }
.rr-tag.ex { background: rgba(52,211,153,0.1); color: #34d399; }
.rr-tag.art { background: rgba(91,141,239,0.1); color: #5b8def; }
.rr-tag.exp { background: rgba(139,92,246,0.1); color: #8b5cf6; }
.rr-tag.code { background: rgba(236,72,153,0.1); color: #ec4899; }
.rr-tag.vid { background: rgba(249,115,22,0.1); color: #f97316; }
.res-row.new {
  background: rgba(52,211,153,0.03);
  border-color: rgba(52,211,153,0.12);
}
.res-row.new .rr-title { color: #059669; }

/* ===== 学习路径 ===== */
.card-path { flex: 1; }
.path-list {
  flex: 1; overflow-y: auto;
  padding: 6px 12px 10px;
  display: flex; flex-direction: column; gap: 4px;
}
.path-empty {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  color: #9ca3af; font-size: 13px;
}
.path-hint { font-size: 11px; margin-top: 6px; color: #d1d5db; }
.p-node {
  display: flex; align-items: center; gap: 10px;
  padding: 10px; border-radius: 12px; cursor: pointer;
  border: 1.5px solid transparent; transition: all 0.18s;
  background: #fafbfd; position: relative;
}
.p-node:hover { border-color: #dbeafe; }
.p-node:not(:last-child)::after {
  content: ''; position: absolute;
  left: 27px; bottom: -6px;
  width: 2px; height: 6px; background: #e5e7eb;
}
.p-node.done {
  background: rgba(52,211,153,0.03);
  border-color: rgba(52,211,153,0.08);
}
.p-node.done:not(:last-child)::after { background: rgba(52,211,153,0.15); }
.p-node.current {
  background: rgba(245,158,11,0.04);
  border-color: rgba(245,158,11,0.18);
  box-shadow: 0 0 14px rgba(245,158,11,0.06);
}
.p-node.locked { opacity: 0.4; cursor: default; }
.pn-icon {
  width: 38px; height: 38px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; flex-shrink: 0; background: #f5f7fa;
}
.p-node.done .pn-icon { background: rgba(52,211,153,0.08); }
.p-node.current .pn-icon {
  background: rgba(245,158,11,0.1);
  animation: node-glow 2s infinite;
}
@keyframes node-glow {
  0%,100% { box-shadow: 0 0 8px rgba(245,158,11,0.1); }
  50% { box-shadow: 0 0 20px rgba(245,158,11,0.2); }
}
.pn-body { flex: 1; min-width: 0; }
.pn-title { font-size: 12px; font-weight: 700; color: #1a1a2e; }
.pn-desc { font-size: 10px; color: #9ca3af; margin-top: 2px; }
.pn-badge {
  font-size: 9px; padding: 3px 8px; border-radius: 7px;
  font-weight: 700; flex-shrink: 0;
}
.done-badge { background: rgba(52,211,153,0.1); color: #059669; }
.cur-badge { background: rgba(245,158,11,0.1); color: #d97706; }
.locked-badge { background: #f5f7fa; color: #9ca3af; }

/* 资源详情 */
.res-detail {
  font-size: 14px; line-height: 1.8; max-height: 400px; overflow-y: auto;
}
.res-detail :deep(h2) { font-size: 18px; margin: 16px 0 10px; color: #1a1a2e; }
.res-detail :deep(h3) { font-size: 15px; margin: 12px 0 6px; color: #1a1a2e; }
.res-detail :deep(pre) { background: #f4f4f5; padding: 10px; border-radius: 6px; overflow-x: auto; }
.res-detail :deep(code) { font-family: Consolas, monospace; font-size: 13px; background: #f0f2f5; padding: 1px 4px; border-radius: 3px; }

/* ===== 数字人 3D 虚拟形象 ===== */
.digital-human-avatar {
  position: absolute;
  bottom: 8px;
  left: 8px;
  z-index: 5;
  pointer-events: none;
}
.dh-avatar-ring {
  position: absolute;
  inset: -4px;
  border-radius: 50%;
  border: 2px solid transparent;
  transition: all 0.6s ease;
}
.dh-avatar-ring.idle {
  border-color: rgba(52,211,153,0.3);
  animation: ring-pulse 3s infinite;
}
.dh-avatar-ring.thinking {
  border-color: rgba(245,158,11,0.5);
  animation: ring-spin 1.5s infinite;
}
.dh-avatar-ring.speaking {
  border-color: rgba(91,141,239,0.6);
  animation: ring-pulse 0.8s infinite;
}
@keyframes ring-pulse {
  0%,100% { transform: scale(1); opacity: 0.6; }
  50% { transform: scale(1.08); opacity: 1; }
}
@keyframes ring-spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
.dh-avatar-inner {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  overflow: hidden;
  background: transparent;
  box-shadow: 0 2px 16px rgba(91,141,239,0.12), 0 0 0 3px rgba(91,141,239,0.06);
  animation: avatar-float 4s ease-in-out infinite;
}
@keyframes avatar-float {
  0%,100% { transform: translateY(0); }
  50% { transform: translateY(-5px); }
}
.digital-human-avatar.thinking .dh-avatar-inner {
  animation: avatar-float 1.2s ease-in-out infinite;
}
.digital-human-avatar.speaking .dh-avatar-inner {
  animation: avatar-float 0.7s ease-in-out infinite;
}
.dh-3d-viewer {
  width: 100%;
  height: 100%;
  --poster-color: transparent;
}
.dh-speech-bubble {
  position: absolute;
  top: -22px;
  left: 50%;
  transform: translateX(-50%);
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px 10px 10px 2px;
  padding: 2px 8px;
  font-size: 12px;
  color: #5b8def;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  animation: bubble-pop 0.3s ease;
  white-space: nowrap;
}
@keyframes bubble-pop {
  from { opacity: 0; transform: translateX(-50%) translateY(4px); }
  to { opacity: 1; transform: translateX(-50%) translateY(0); }
}

/* 滚动条 */
::-webkit-scrollbar { width: 3px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: #e5e7eb; border-radius: 3px; }
</style>
