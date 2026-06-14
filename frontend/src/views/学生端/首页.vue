<template>
  <div class="dashboard">
    <!-- 三列主体 -->
    <div class="dash-grid">

      <!-- ===== 左列: 数字人对话 ===== -->
      <div class="col col-left">
        <div class="card card-chat">
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

          </div>
          <!-- 输入区 -->
          <div class="chat-input-row">
            <button v-if="dh.voiceSupported" class="voice-mini-btn" :class="{ recording: dh.state.isRecording }" @mousedown="dh.startRecording" @mouseup="dh.stopRecording" @mouseleave="dh.stopRecording" title="按住说话">🎤</button>
            <input v-model="dh.inputText" placeholder="输入你的问题或答案..." @keyup.enter="sendMsg">
            <button class="send-btn" @click="sendMsg">发送</button>
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
import { ref, reactive, onMounted, nextTick, watch, computed } from 'vue'
// 虚拟人组件已移至 F:\从容应对_虚拟人备份_20260614
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
      axisName: { fontSize: 9, color: '#6a6054' },
      splitArea: { areaStyle: { color: ['#f4efe7', '#ebe2d7', '#f4efe7', '#ebe2d7', '#f4efe7'] } }
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
      areaStyle: { color: 'rgba(177,83,17,0.12)' },
      lineStyle: { color: '#b15311', width: 2 },
      itemStyle: { color: '#b15311' },
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
onMounted(() => {
  // ★ 强制解锁 — 不依赖任何 API
  dh.loading.value = false

  // 直接本地欢迎消息，不依赖后端 API — 输入框秒开
  const welcome = {
    id: 1,
    role: 'ai',
    content: '你好呀！我是你的 AI 学习助手，有什么问题尽管问我～',
    displayContent: '',
    agentName: '小智老师',
    createdAt: new Date().toISOString(),
    typing: false,
  }
  dh.messages.value = [welcome]
  let i = 0
  dh.state.mode = 'speaking'
  const ival = setInterval(() => {
    if (i < welcome.content.length) {
      welcome.displayContent += welcome.content[i++]
      dh.scrollToBottom()
    } else {
      welcome.typing = false
      dh.state.mode = 'idle'
      clearInterval(ival)
    }
  }, 30)

  // 后台异步加载（不阻塞）
  dh.loadHistory().then(() => {
    // 后端有历史 → 替换掉临时欢迎消息
    if (dh.messages.value.length > 1 || (dh.messages.value.length === 1 && dh.messages.value[0].id !== 1)) {
      return // 后端历史已就绪
    }
  }).catch(() => {})

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
  background: var(--t-surface);
  border-radius: 16px;
  box-shadow: var(--t-shadow-soft);
  border: 1px solid var(--t-border);
  display: flex; flex-direction: column;
  overflow: hidden; min-height: 0;
}
.card-head {
  flex-shrink: 0;
  padding: 12px 16px 0;
  display: flex; align-items: center; gap: 6px;
  font-size: 12.5px; font-weight: 700;
  color: var(--t-line);
}
.ch-icon { font-size: 15px; }
.refresh-link {
  margin-left: auto; font-size: 10px; color: var(--t-line-subtle);
  cursor: pointer; font-weight: 500;
}
.refresh-link:hover { color: var(--t-accent); }

/* ===== 数字人对话卡 ===== */
.card-chat { flex: 1; }


.chat-msgs {
  flex: 1; overflow-y: auto; padding: 4px 16px 8px;
  display: flex; flex-direction: column; gap: 7px;
  position: relative;
}
.msg {
  max-width: 78%; padding: 9px 12px; border-radius: 12px;
  font-size: 12px; line-height: 1.7;
}
.msg-ai {
  align-self: flex-start;
  background: var(--t-surface-muted); color: var(--t-line);
  border-bottom-left-radius: 4px;
}
.msg-sys {
  align-self: center;
  background: rgba(161,74,61,0.08); color: var(--t-status-error);
  border-radius: 8px;
  font-size: 11px; padding: 5px 12px;
}
.msg-agent-tag { font-size: 9px; color: var(--t-accent); font-weight: 600; display: block; margin-bottom: 2px; }
.typing-cursor { color: var(--t-accent); animation: blink 0.8s infinite; }
@keyframes blink { 0%,100%{opacity:1} 50%{opacity:0} }
.msg-me {
  align-self: flex-end;
  background: var(--t-accent);
  color: var(--t-surface); border-bottom-right-radius: 4px;
}
.msg code {
  background: var(--t-surface-muted); color: var(--t-accent);
  padding: 1px 6px; border-radius: 5px;
  font-family: var(--t-font-mono); font-size: 11px;
}
.thinking-dots { display: flex; gap: 4px; }
.thinking-dots span {
  width: 6px; height: 6px; border-radius: 50%; background: var(--t-line-subtle);
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
  border: 1px solid var(--t-wash); background: var(--t-surface-muted);
  font-size: 14px; cursor: pointer; display: flex;
  align-items: center; justify-content: center;
  transition: all 0.2s; flex-shrink: 0;
}
.voice-mini-btn:hover { background: var(--t-surface); border-color: var(--t-accent); }
.voice-mini-btn.recording { background: var(--t-status-error); color: var(--t-surface); border-color: var(--t-status-error); animation: pulse-rec 1.5s infinite; }
@keyframes pulse-rec { 0%,100%{transform:scale(1)} 50%{transform:scale(1.08)} }
.chat-input-row input {
  flex: 1;
  background: var(--t-surface-muted); border: 1px solid var(--t-wash);
  border-radius: 12px; padding: 9px 14px;
  font-size: 12px; color: var(--t-line); outline: none;
  transition: border-color 0.2s;
}
.chat-input-row input:focus { border-color: var(--t-accent); background: var(--t-surface); }
.send-btn {
  background: var(--t-accent);
  border: none; color: var(--t-surface); padding: 9px 18px;
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
  flex: 1; background: var(--t-surface-muted); border-radius: 10px;
  padding: 7px 4px; text-align: center; font-size: 9px; color: var(--t-line-subtle);
}
.sc-val { font-size: 16px; font-weight: 800; color: var(--t-line); display: block; }
.pf-radar {
  flex: 1; display: flex; align-items: center; justify-content: center;
  padding: 4px 8px;
}

/* ===== 资源中心 ===== */
.card-res { flex: 2; }
.res-section { padding: 0 16px; }
.res-section + .res-section { margin-top: 4px; }
.res-sec-title {
  font-size: 10.5px; font-weight: 700; color: var(--t-line-dim);
  padding: 4px 0; display: flex; align-items: center; gap: 5px;
}
.pulse-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--t-status-ready); flex-shrink: 0;
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
  background: var(--t-surface-muted); border: 1px solid transparent;
  transition: all 0.18s;
}
.res-row:hover { background: var(--t-accent-soft); border-color: var(--t-wash); }
.res-row .rr-icon {
  font-size: 18px; flex-shrink: 0;
  width: 32px; height: 32px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  background: var(--t-wash);
}
.rr-body { flex: 1; min-width: 0; }
.rr-title { font-weight: 700; font-size: 12px; color: var(--t-line); }
.rr-meta {
  font-size: 9px; color: var(--t-line-subtle); margin-top: 2px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.rr-tag {
  padding: 2px 7px; border-radius: 6px;
  font-size: 9px; font-weight: 700; flex-shrink: 0;
}
.rr-tag.ppt { background: rgba(201,121,48,0.12); color: var(--t-status-warn); }
.rr-tag.mind { background: rgba(139,90,60,0.1); color: var(--t-accent-muted); }
.rr-tag.ex { background: rgba(74,124,78,0.1); color: var(--t-status-ready); }
.rr-tag.art { background: var(--t-accent-soft); color: var(--t-accent); }
.rr-tag.exp { background: rgba(139,90,60,0.1); color: var(--t-accent-muted); }
.rr-tag.code { background: rgba(161,74,61,0.1); color: var(--t-status-error); }
.rr-tag.vid { background: rgba(201,121,48,0.12); color: var(--t-status-warn); }
.res-row.new {
  background: rgba(74,124,78,0.04);
  border-color: rgba(74,124,78,0.12);
}
.res-row.new .rr-title { color: var(--t-status-ready); }

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
  color: var(--t-line-subtle); font-size: 13px;
}
.path-hint { font-size: 11px; margin-top: 6px; color: var(--t-wash); }
.p-node {
  display: flex; align-items: center; gap: 10px;
  padding: 10px; border-radius: 12px; cursor: pointer;
  border: 1.5px solid transparent; transition: all 0.18s;
  background: var(--t-surface-muted); position: relative;
}
.p-node:hover { border-color: var(--t-wash); }
.p-node:not(:last-child)::after {
  content: ''; position: absolute;
  left: 27px; bottom: -6px;
  width: 2px; height: 6px; background: var(--t-wash);
}
.p-node.done {
  background: rgba(74,124,78,0.05);
  border-color: rgba(74,124,78,0.1);
}
.p-node.done:not(:last-child)::after { background: rgba(74,124,78,0.2); }
.p-node.current {
  background: rgba(201,121,48,0.06);
  border-color: rgba(201,121,48,0.2);
  box-shadow: 0 0 14px rgba(201,121,48,0.08);
}
.p-node.locked { opacity: 0.4; cursor: default; }
.pn-icon {
  width: 38px; height: 38px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; flex-shrink: 0; background: var(--t-wash);
}
.p-node.done .pn-icon { background: rgba(74,124,78,0.1); }
.p-node.current .pn-icon {
  background: rgba(201,121,48,0.1);
  animation: node-glow 2s infinite;
}
@keyframes node-glow {
  0%,100% { box-shadow: 0 0 8px rgba(201,121,48,0.1); }
  50% { box-shadow: 0 0 20px rgba(201,121,48,0.2); }
}
.pn-body { flex: 1; min-width: 0; }
.pn-title { font-size: 12px; font-weight: 700; color: var(--t-line); }
.pn-desc { font-size: 10px; color: var(--t-line-subtle); margin-top: 2px; }
.pn-badge {
  font-size: 9px; padding: 3px 8px; border-radius: 7px;
  font-weight: 700; flex-shrink: 0;
}
.done-badge { background: rgba(74,124,78,0.1); color: var(--t-status-ready); }
.cur-badge { background: rgba(201,121,48,0.1); color: var(--t-status-warn); }
.locked-badge { background: var(--t-surface-muted); color: var(--t-line-subtle); }

/* 资源详情 */
.res-detail {
  font-size: 14px; line-height: 1.8; max-height: 400px; overflow-y: auto;
}
.res-detail :deep(h2) { font-size: 18px; margin: 16px 0 10px; color: var(--t-line); }
.res-detail :deep(h3) { font-size: 15px; margin: 12px 0 6px; color: var(--t-line); }
.res-detail :deep(pre) { background: var(--t-surface-muted); padding: 10px; border-radius: 6px; overflow-x: auto; }
.res-detail :deep(code) { font-family: var(--t-font-mono); font-size: 13px; background: var(--t-wash); padding: 1px 4px; border-radius: 3px; }



/* 滚动条 */
::-webkit-scrollbar { width: 3px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: var(--t-wash); border-radius: 3px; }
</style>
