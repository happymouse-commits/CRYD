<template>
  <div class="learn-space">
    <!-- ═══ 我的画像 ═══ -->
    <section id="profile">
      <h2 class="section-title">🧬 我的画像</h2>
      <div class="profile-layout">
        <!-- 学生信息卡 -->
        <div class="student-card">
          <div class="sc-avatar">👩‍🎓</div>
          <div class="sc-name">{{ profile.name }}</div>
          <div class="sc-meta">{{ profile.major }} · {{ profile.grade }}</div>
          <div class="sc-grade">
            <span class="g-icon">{{ levelIcon }}</span>
            <span>{{ profile.level }}</span>
          </div>
          <div class="growth-track">
            <div class="gt-round" v-for="(s, i) in growthStages" :key="i" :class="{ active: s.active }">
              <span class="gt-num">R{{ i + 1 }}</span>
              <span class="gt-desc">{{ s.label }}</span>
            </div>
            <span v-if="i < growthStages.length - 1" class="gt-arrow">→</span>
          </div>
          <div class="sc-summary">{{ profile.summary }}</div>
        </div>

        <!-- 六维雷达 + 维度卡片 -->
        <div class="dimensions-panel">
          <div class="radar-card">
            <h3>📊 知识基础 · 六维雷达</h3>
            <div ref="radarRef" class="radar-chart"></div>
            <div class="radar-legend">
              <div class="legend-item" v-for="dim in dims" :key="dim.key">
                <span class="legend-dot" :style="{ background: dim.color }"></span>
                {{ dim.name }}
                <span class="legend-val" :style="{ color: dim.color }">{{ radar[dim.key] || 0 }}%</span>
              </div>
            </div>
          </div>

          <div class="dim-grid">
            <div class="dim-card" v-for="card in dimCards" :key="card.icon">
              <div class="dim-icon">{{ card.icon }}</div>
              <div class="dim-title">{{ card.title }}</div>
              <div class="dim-value" v-html="card.val"></div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ═══ 学习路径 ═══ -->
    <section class="path-section" id="path">
      <h2 class="section-title">🗺️ 学习路径</h2>
      <div class="game-map">
        <div class="map-row">
          <template v-for="(node, i) in pathFirstRow" :key="node.id">
            <div class="map-node" :class="'mn-' + node.status" @click="engage('path')">
              <div class="mn-circle">{{ node.icon }}</div>
              <div class="mn-stars">
                <span v-for="s in 5" :key="s" :class="{ on: s <= Math.round(node.pct / 20) }">★</span>
              </div>
              <div class="mn-label">{{ node.name }}</div>
              <div class="mn-pct">{{ node.status === 'locked' ? '🔒 未解锁' : node.pct + '%' }}</div>
            </div>
            <div v-if="i < pathFirstRow.length - 1" class="map-connector" :class="'mc-' + (node.status === 'locked' ? 'locked' : node.status === 'done' ? 'done' : 'active')"></div>
          </template>
        </div>
        <div class="map-branch"><div class="map-branch-down"></div></div>
        <div class="map-row">
          <template v-for="(node, i) in pathSecondRow" :key="node.id">
            <div class="map-node" :class="'mn-' + node.status" @click="engage('path')">
              <div class="mn-circle">{{ node.icon }}</div>
              <div class="mn-stars">
                <span v-for="s in 5" :key="s" :class="{ on: s <= Math.round(node.pct / 20) }">★</span>
              </div>
              <div class="mn-label">{{ node.name }}</div>
              <div class="mn-pct">{{ node.status === 'locked' ? '🔒 未解锁' : node.pct + '%' }}</div>
            </div>
            <div v-if="i < pathSecondRow.length - 1" class="map-connector" :class="'mc-' + (node.status === 'locked' ? 'locked' : node.status === 'done' ? 'done' : 'active')"></div>
          </template>
        </div>
      </div>
    </section>

    <!-- ═══ 刷题速览 ═══ -->
    <section class="lite" id="practice">
      <h2 class="section-title">📝 刷题速览</h2>
      <p class="section-sub">以下是推荐练习题，完整题库请进入 <strong>刷题房</strong> 页面</p>
      <div class="practice-grid">
        <div class="practice-card" v-for="(p, i) in practiceList" :key="i">
          <span class="pc-diff">{{ p.d }}</span>
          <h3>{{ p.q }}</h3>
          <p class="pc-stat">{{ p.t.join(' · ') }}</p>
        </div>
      </div>
    </section>

    <!-- ═══ 学习评估 ═══ -->
    <section class="lite" id="evaluation">
      <h2 class="section-title">📊 学习评估</h2>
      <div class="eval-grid-full">
        <div class="eval-mod mod-trend">
          <h3>📈 阶段成绩趋势</h3>
          <div class="trend-chart">
            <div class="trend-bar-col" v-for="(t, i) in evalData.trend" :key="i">
              <div class="trend-bar" :style="{ height: (t.score / 100) * 100 + '%', background: 'var(--accent)', opacity: 0.5 + 0.25 * i }"></div>
              <span class="trend-label">{{ t.label }}</span>
              <span class="trend-score">{{ t.score }}</span>
            </div>
          </div>
        </div>

        <div class="eval-mod mod-errors">
          <h3>🔴 易错类型分布</h3>
          <div class="err-bar" v-for="err in evalData.errors" :key="err.name">
            <div class="err-label"><span>{{ err.name }}</span><span class="err-tag">{{ err.pct }}%</span></div>
            <div class="err-track"><div class="err-fill" :class="err.pct > 40 ? 'hot' : err.pct > 25 ? 'warm' : 'cool'" :style="{ width: err.pct + '%' }"></div></div>
          </div>
        </div>

        <div class="eval-mod mod-weak">
          <h3>⚠️ 薄弱知识点 Top3</h3>
          <div class="weak-card" v-for="(w, i) in evalData.weakTop3" :key="i">
            <span class="weak-rank">#{{ i + 1 }}</span>
            <div class="weak-info"><strong>{{ w.kp }}</strong><span class="weak-err">{{ w.err }}道错题</span></div>
            <span class="weak-rec">📖 {{ w.rec }}</span>
          </div>
        </div>

        <div class="eval-mod mod-compare">
          <h3>📊 本轮 vs 上轮</h3>
          <div class="cmp-grid">
            <div class="cmp-col">
              <h4 style="color:var(--accent2)">进步项</h4>
              <ul><li class="cmp-up" v-for="u in evalData.compare.up" :key="u">↑ {{ u }}</li></ul>
            </div>
            <div class="cmp-col">
              <h4 style="color:var(--accent4)">待改进</h4>
              <ul><li class="cmp-down" v-for="d in evalData.compare.down" :key="d">↓ {{ d }}</li></ul>
            </div>
          </div>
        </div>

        <div class="eval-mod mod-ai">
          <h3>🧠 AI 诊断建议</h3>
          <p>{{ evalData.aiSummary }}</p>
        </div>
      </div>
    </section>

    <!-- ═══ 学习资源 ═══ -->
    <section class="resource-section" id="resources">
      <h2 class="section-title">📚 学习资源</h2>
      <p class="section-sub">点击卡片查看详情，按需学习</p>
      <div class="resource-sort">
        <button class="resource-sort-btn" :class="{ active: resFilter === 'all' }" @click="resFilter = 'all'">📋 全部</button>
        <button class="resource-sort-btn" :class="{ active: resFilter === 'doc' }" @click="resFilter = 'doc'">📖 课程讲解</button>
        <button class="resource-sort-btn" :class="{ active: resFilter === 'mindmap' }" @click="resFilter = 'mindmap'">🧠 思维导图</button>
        <button class="resource-sort-btn" :class="{ active: resFilter === 'exercise' }" @click="resFilter = 'exercise'">📝 练习题</button>
        <button class="resource-sort-btn" :class="{ active: resFilter === 'article' }" @click="resFilter = 'article'">📄 拓展阅读</button>
        <button class="resource-sort-btn" :class="{ active: resFilter === 'code' }" @click="resFilter = 'code'">💻 代码示例</button>
      </div>
      <div class="resource-grid">
        <div class="resource-card" v-for="(r, i) in filteredResources" :key="i" @click="notifyResourceClick">
          <div class="rc-header">
            <span class="rc-type" :class="r.t">{{ typeLabels[r.t] }}</span>
            <span class="rc-dur">{{ r.author }}</span>
          </div>
          <h3>{{ r.h }}</h3>
          <p class="rc-desc">{{ r.p }}</p>
          <div class="rc-meta"><span>🏷️ {{ r.d }}</span><span class="rc-tag">{{ r.level }}</span></div>
          <div class="rc-tags"><span class="rc-tag" v-for="tag in r.tags" :key="tag">{{ tag }}</span></div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { useUserStore } from '../../store/user'
import api, { profileApi, pathApi, resourceApi, evaluationApi } from '../../api'

const userStore = useUserStore()
const radarRef = ref(null)
const resFilter = ref('all')
const sectionEngaged = reactive({ profile: false, path: false, practice: false, resources: false })

// 轮次
const round = ref(0)

// 雷达维度
const dims = [
  { key: 'grammar', name: '语法掌握力', color: '#b15311' },
  { key: 'logic', name: '逻辑推理', color: '#4a7c4e' },
  { key: 'coding', name: '代码实践', color: '#a78bfa' },
  { key: 'debug', name: '调试能力', color: '#c97930' },
  { key: 'abstract', name: '抽象思维', color: '#c0564a' },
  { key: 'selfLearn', name: '自主学习', color: '#38bdf8' }
]

// 学生画像
const profile = reactive({
  name: '张小泉', major: '计算机科学与技术', grade: '大一',
  level: '初级探索者', levelIdx: 1, summary: 'C语言基础中等偏上，指针和数据结构是重点提升方向。偏好视频+实战练习，目标是通过期末考试。'
})

const levelIcon = computed(() => ['⚡', '🔥', '👑'][(profile.levelIdx || 1) - 1] || '⚡')

const growthStages = computed(() => [
  { label: '语法入门', active: round.value === 0 },
  { label: '指针进阶', active: round.value === 1 },
  { label: '数据结构', active: round.value === 2 }
])

// 雷达分数
const radar = reactive({ grammar: 0, logic: 0, coding: 0, debug: 0, abstract: 0, selfLearn: 0 })

// 默认雷达数据（按轮次）
const radarByRound = [
  { grammar: 85, logic: 72, coding: 68, debug: 40, abstract: 35, selfLearn: 55 },
  { grammar: 88, logic: 78, coding: 75, debug: 55, abstract: 45, selfLearn: 60 },
  { grammar: 92, logic: 85, coding: 82, debug: 68, abstract: 58, selfLearn: 70 }
]

// 熟练度（按轮次）
const masteryByRound = [
  { data_types: 88, branches: 82, arrays: 75, pointers: 38, structs: 0, files: 0 },
  { data_types: 90, branches: 85, arrays: 78, pointers: 55, structs: 10, files: 0 },
  { data_types: 92, branches: 88, arrays: 82, pointers: 68, structs: 40, files: 15 }
]

// 学习路径节点
const pathFirstRow = ref([])
const pathSecondRow = ref([])

// 默认路径数据
const pathNodes = [
  { id: 'data_types', icon: '📦', name: '变量与类型' },
  { id: 'branches', icon: '🔀', name: '分支与循环' },
  { id: 'arrays', icon: '📊', name: '数组与函数' },
  { id: 'pointers', icon: '🎯', name: '指针与内存' },
  { id: 'structs', icon: '🏗️', name: '结构体' },
  { id: 'files', icon: '📁', name: '文件操作' }
]

// 刷题速览
const practiceList = ref([])
const practiceByRound = [
  [{ d: '★ 基础', q: '下列哪个运算符用于获取变量地址？', t: ['指针', '基础'] }],
  [
    { d: '★★ 进阶', q: 'int *p; int a=10; p=&a; 则 *p 的值是？', t: ['指针', '变量'] },
    { d: '★★ 进阶', q: '二级指针 int **pp 存储的是什么？', t: ['指针', '内存'] }
  ],
  [
    { d: '★★ 进阶', q: 'malloc(sizeof(int)*10) 分配的内存在哪个区域？', t: ['内存', '动态分配'] },
    { d: '★★★ 挑战', q: 'free(ptr) 后 ptr 应该做什么？', t: ['内存', '安全'] },
    { d: '★★★ 挑战', q: '下面哪个是正确的函数指针声明？', t: ['指针', '函数指针'] }
  ]
]

// 评估数据
const evalData = reactive({
  trend: [],
  errors: [],
  weakTop3: [],
  aiSummary: '',
  compare: { up: [], down: [] }
})

const evalByRound = [
  {
    trend: [{ label: 'R1', score: 45 }],
    errors: [
      { name: '概念混淆', pct: 55 }, { name: '逻辑错误', pct: 40 },
      { name: '内存问题', pct: 25 }, { name: '语法陷阱', pct: 15 }, { name: '粗心失误', pct: 10 }
    ],
    weakTop3: [
      { kp: '指针基础', err: 8, rec: '指针入门讲解' },
      { kp: '函数调用', err: 5, rec: '函数参数传递精讲' },
      { kp: '数组遍历', err: 3, rec: '数组与指针对比练习' }
    ],
    aiSummary: '首次诊断完成，基础语法掌握不错。指针和数据结构是重点提升方向，建议先打牢指针基础再推进。偏好视频+实战练习。',
    compare: { up: ['基础语法 +5', '流程控制 +3'], down: ['指针概念混淆较多'] }
  },
  {
    trend: [{ label: 'R1', score: 45 }, { label: 'R2', score: 58 }],
    errors: [
      { name: '概念混淆', pct: 45 }, { name: '逻辑错误', pct: 38 },
      { name: '内存问题', pct: 30 }, { name: '语法陷阱', pct: 18 }, { name: '粗心失误', pct: 12 }
    ],
    weakTop3: [
      { kp: '二级指针', err: 6, rec: '多级指针知识图谱' },
      { kp: 'malloc/free', err: 5, rec: '动态内存分配深入讲解' },
      { kp: '函数指针', err: 4, rec: '回调函数实战案例' }
    ],
    aiSummary: '指针掌握进步明显（38→55），结构体初步接触。继续保持实战学习风格，注意内存管理相关概念还需加强。',
    compare: { up: ['指针 +17', '语法 +2', '流程控制 +3'], down: ['内存错误占比上升'] }
  },
  {
    trend: [{ label: 'R1', score: 45 }, { label: 'R2', score: 58 }, { label: 'R3', score: 72 }],
    errors: [
      { name: '概念混淆', pct: 35 }, { name: '逻辑错误', pct: 32 },
      { name: '内存问题', pct: 28 }, { name: '语法陷阱', pct: 20 }, { name: '粗心失误', pct: 15 }
    ],
    weakTop3: [
      { kp: '链表实现', err: 5, rec: '链表完整实现与内存图解' },
      { kp: '动态内存', err: 4, rec: '动态内存管理全解' },
      { kp: '未定义行为', err: 3, rec: 'C语言UB大全' }
    ],
    aiSummary: '六维能力趋于均衡（语法92、逻辑85、代码82），可以挑战更复杂的项目了。数据结构是最后一关。',
    compare: { up: ['指针 +13', '数据结构 +25', '文件IO +15'], down: ['链表细节还需打磨'] }
  }
]

// 资源数据
const allResources = ref([])
const resourcesByRound = [
  [
    { t: 'doc', h: 'C语言指针快速入门', p: '什么是指针？& 和 * 操作符图文详解', d: '课程设计师', author: '课程设计师', level: '入门', tags: ['指针', '基础'] },
    { t: 'mindmap', h: '指针核心概念脑图', p: '地址→指针变量→解引用→指针运算', d: '思维导图师', author: '思维导图师', level: '入门', tags: ['脑图', '结构'] },
    { t: 'code', h: 'C指针必知必会20题', p: '分级练习，每道含详细解析', d: '代码示例师', author: '代码示例师', level: '入门', tags: ['练习', '指针'] },
    { t: 'article', h: '指针的前世今生', p: '从内存模型到指针的底层原理', d: '知识讲解师', author: '知识讲解师', level: '入门', tags: ['科普', '内存'] },
    { t: 'exercise', h: '指针入门自测卷', p: '15道选择题+5道编程', d: '出题专家', author: '出题专家', level: '入门', tags: ['测试', '入门'] }
  ],
  [
    { t: 'doc', h: 'C语言指针完全指南', p: '一级→二级→函数指针→动态内存', d: '课程设计师', author: '课程设计师', level: '进阶', tags: ['指针', '全面'] },
    { t: 'mindmap', h: '多级指针知识图谱', p: '二级指针、指针数组、函数指针', d: '思维导图师', author: '思维导图师', level: '进阶', tags: ['脑图', '进阶'] },
    { t: 'code', h: '指针经典实战100行', p: 'swap/数组指针/字符串指针完整实现', d: '代码示例师', author: '代码示例师', level: '进阶', tags: ['练习', '指针'] },
    { t: 'article', h: '动态内存分配深度解析', p: 'malloc/free 内部机制与常见陷阱', d: '知识讲解师', author: '知识讲解师', level: '进阶', tags: ['内存', '深度'] },
    { t: 'exercise', h: '指针与内存综合测验', p: '30题覆盖二级指针到函数指针', d: '出题专家', author: '出题专家', level: '进阶', tags: ['测试', '全面'] }
  ],
  [
    { t: 'doc', h: '动态内存管理全解', p: 'malloc/free/realloc 最佳实践', d: '课程设计师', author: '课程设计师', level: '高级', tags: ['内存', '实战'] },
    { t: 'mindmap', h: '链表与内存管理脑图', p: '节点结构→增删查改→内存泄漏排查', d: '思维导图师', author: '思维导图师', level: '高级', tags: ['脑图', '链表'] },
    { t: 'code', h: '链表完整实现与内存图解', p: '单链表+双向链表，含valgrind验证', d: '代码示例师', author: '代码示例师', level: '高级', tags: ['链表', '完整'] },
    { t: 'article', h: 'C语言未定义行为大全', p: '数组越界、悬垂指针、缓冲区溢出', d: '知识讲解师', author: '知识讲解师', level: '高级', tags: ['UB', '安全'] },
    { t: 'exercise', h: '数据结构与内存综合卷', p: '15道中高难度综合题+链表设计', d: '出题专家', author: '出题专家', level: '高级', tags: ['综合', '挑战'] },
    { t: 'doc', h: '从零实现数据结构', p: '手写链表与栈，含内存可视化', d: '课程设计师', author: '课程设计师', level: '高级', tags: ['数据结构', '手写'] },
    { t: 'code', h: '链表与内存综合练习', p: '15道中高难度综合题', d: '代码示例师', author: '代码示例师', level: '高级', tags: ['练习', '综合'] }
  ]
]

const typeLabels = { doc: '📖 课程讲解', mindmap: '🧠 思维导图', exercise: '📝 练习题', article: '📄 拓展阅读', code: '💻 代码示例' }

const filteredResources = computed(() => {
  if (resFilter.value === 'all') return allResources.value
  return allResources.value.filter(r => r.t === resFilter.value)
})

// 维度卡片
const dimCards = computed(() => {
  const r = round.value
  const data = radarByRound[Math.min(r, radarByRound.length - 1)]
  const stats = statByRound[Math.min(r, statByRound.length - 1)]
  return [
    { icon: '🧠', title: '认知风格', val: `视觉型 75% · 实践型 80%<br><span style="font-size:11px;color:var(--accent)">你是 <b>视觉+实践型</b> 学习者</span>` },
    {
      icon: '🌱', title: '成长轨迹',
      val: `<div class="growth-track"><div class="gt-round"><span class="gt-num">R1</span><span class="gt-desc">语法入门<br>${radarByRound[0].grammar}分</span></div><span class="gt-arrow">→</span><div class="gt-round"><span class="gt-num">R2</span><span class="gt-desc">指针进阶<br>${radarByRound[1].grammar}分</span></div><span class="gt-arrow">→</span><div class="gt-round active"><span class="gt-num">R3</span><span class="gt-desc">数据结构<br>${radarByRound[2].grammar}分</span></div></div>`
    },
    { icon: '🎯', title: '学习策略', val: `顺序型 60% · 跳跃型 30%<br><span style="font-size:11px;color:var(--accent)">倾向于 <b>稳扎稳打</b> 按顺序学习</span>` },
    {
      icon: '💡', title: '思维能力',
      val: `<div class="thinking-badge lv-2">🔧 进阶</div><p style="font-size:12px;color:var(--muted);margin-top:6px">指针和内存理解加深，可以挑战算法题</p>`
    },
    {
      icon: '📈', title: '学习投入',
      val: `<div class="eg-stats"><div class="eg-stat"><div class="eg-val">5</div><div class="eg-lbl">活跃天数</div></div><div class="eg-stat"><div class="eg-val">32min</div><div class="eg-lbl">日均时长</div></div><div class="eg-stat"><div class="eg-val">3天</div><div class="eg-lbl">最长连续</div></div></div>`
    },
    {
      icon: '📊', title: '学习统计',
      val: `<div class="eg-stats"><div class="eg-stat"><div class="eg-val">${stats.q}</div><div class="eg-lbl">总题数</div></div><div class="eg-stat"><div class="eg-val">${stats.rate}%</div><div class="eg-lbl">正确率</div></div><div class="eg-stat"><div class="eg-val">${stats.weak}</div><div class="eg-lbl">薄弱项</div></div></div>`
    }
  ]
})

const statByRound = [
  { q: 5, rate: 80, weak: 3 },
  { q: 23, rate: 78, weak: 2 },
  { q: 47, rate: 82, weak: 1 }
]

// 重新计算路径
function buildPath(mastery) {
  const nodes = pathNodes.map(nd => {
    const pct = mastery[nd.id] || 0
    let status = 'next'
    if (pct >= 70) status = 'done'
    else if (nd.id === 'pointers' && round.value === 0) status = 'current'
    else if (nd.id === 'pointers' && round.value >= 1) status = 'done'
    else if (nd.id === 'structs' && round.value === 1) status = 'current'
    else if (nd.id === 'structs' && round.value >= 2) status = 'done'
    else if (nd.id === 'files' && round.value >= 2) status = 'current'
    if (pct === 0 && status === 'next') status = 'locked'
    return { ...nd, pct, status }
  })
  pathFirstRow.value = nodes.slice(0, 3)
  pathSecondRow.value = nodes.slice(3, 6)
}

// ECharts 雷达图
function renderRadar() {
  if (!radarRef.value) return
  const chart = echarts.init(radarRef.value)
  const indicator = dims.map(d => ({ name: d.name, max: 100 }))
  const values = dims.map(d => radar[d.key] || 0)
  chart.setOption({
    radar: {
      indicator,
      center: ['50%', '50%'],
      radius: '65%',
      axisName: { color: '#6a6054', fontSize: 11 }
    },
    series: [{
      type: 'radar',
      data: [{ value: values, name: '学习画像', areaStyle: { color: 'rgba(177,83,17,0.12)' } }],
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { color: 'rgba(177,83,17,0.5)', width: 2 },
      itemStyle: { color: '#b15311', borderColor: '#fff', borderWidth: 1.5 }
    }]
  })
}

// 加载数据
async function loadData() {
  const sysUserId = userStore.id
  const studentId = userStore.studentId
  const isDemo = !sysUserId

  // 加载轮次
  const savedRound = localStorage.getItem('cryd_round')
  if (savedRound !== null) round.value = Math.min(parseInt(savedRound), 2)

  const r = round.value

  // 1. 画像
  if (!isDemo && sysUserId) {
    try {
      const res = await profileApi.get(sysUserId)
      if (res) {
        const d = res.data || res
        profile.name = d.name || d.username || profile.name
        profile.major = d.className || d.department || profile.major
        profile.grade = d.grade || d.level || profile.grade
        profile.levelIdx = d.levelIdx || d.thinkingLevel || 1
        profile.summary = d.summary || d.aiSummary || profile.summary
        const lvs = ['初级探索者', '进阶挑战者', '高手程序员']
        profile.level = lvs[(profile.levelIdx || 1) - 1] || lvs[0]

        if (d.dimensions || d.scores || d.radar) {
          const src = d.dimensions || d.scores || d.radar
          dims.forEach(dim => { if (src[dim.key] != null) radar[dim.key] = src[dim.key] })
        }
      }
    } catch { /* fallback */ }
  }

  // Fallback radar
  if (Object.values(radar).every(v => v === 0)) {
    const fb = radarByRound[Math.min(r, radarByRound.length - 1)]
    Object.assign(radar, fb)
  }

  // 2. 学习路径
  let mastery = masteryByRound[Math.min(r, masteryByRound.length - 1)]
  if (!isDemo && studentId) {
    try {
      const res = await pathApi.getPaths(studentId)
      if (res) {
        const d = res.data || res
        if (d.nodes || d.pathNodes) {
          const nodes = (d.nodes || d.pathNodes).map(nd => ({
            id: nd.id || nd.nodeId,
            icon: nd.icon || '📦',
            name: nd.name || nd.title || nd.knowledgePoint || '环节',
            pct: nd.progress || nd.percent || nd.mastery || (nd.completed ? 100 : 0),
            status: nd.status || (nd.completed ? 'done' : nd.current ? 'current' : 'next')
          }))
          pathFirstRow.value = nodes.slice(0, Math.ceil(nodes.length / 2))
          pathSecondRow.value = nodes.slice(Math.ceil(nodes.length / 2))
          mastery = null
        }
        if (d.mastery) mastery = { ...mastery, ...d.mastery }
      }
    } catch { /* fallback */ }
  }
  if (mastery) buildPath(mastery)

  // 3. 刷题速览
  practiceList.value = practiceByRound[Math.min(r, practiceByRound.length - 1)] || practiceByRound[0]

  // 4. 评估
  let ev = evalByRound[Math.min(r, evalByRound.length - 1)]
  if (!isDemo && studentId) {
    try {
      const [weakRes, errRes] = await Promise.all([
        evaluationApi.getWeakness(studentId).catch(() => null),
        evaluationApi.getErrors(studentId).catch(() => null)
      ])
      if (weakRes?.data) {
        const d = weakRes.data
        if (d.weakPoints?.length) {
          ev = { ...ev, weakTop3: d.weakPoints.map((w, i) => ({ kp: w.name || w.knowledgePoint || '未知', err: w.errorCount || 0, rec: w.recommendation || '复习相关知识' })) }
        }
        if (d.aiSummary) ev = { ...ev, aiSummary: d.aiSummary }
      }
      if (errRes?.data) {
        const d = errRes.data
        if (d.errors?.length) {
          ev = { ...ev, errors: d.errors.map(e => ({ name: e.name || e.type || '其他', pct: e.percentage || e.pct || 0 })) }
        }
      }
    } catch { /* fallback */ }
  }
  Object.assign(evalData, ev)

  // 5. 资源
  let resources = resourcesByRound[Math.min(r, resourcesByRound.length - 1)]
  if (!isDemo && studentId) {
    try {
      const res = await resourceApi.getByStudent(studentId)
      if (res) {
        const d = res.data || res
        const list = d.resources || d.list || (Array.isArray(d) ? d : null)
        if (list?.length) {
          const map = { doc: 'doc', mindmap: 'mindmap', exercise: 'exercise', article: 'article', code: 'code' }
          resources = list.map(r => ({
            t: map[r.type] || r.t || 'doc',
            h: r.title || r.name || r.h || '未命名资源',
            p: r.description || r.summary || r.p || '',
            d: r.author || r.source || r.d || 'AI 生成',
            author: r.author || r.source || 'AI 生成',
            level: r.level || '通用',
            tags: r.tags || r.topics || []
          }))
        }
      }
    } catch { /* fallback */ }
  }
  allResources.value = resources

  await nextTick()
  renderRadar()
}

function engage(id) {
  if (!sectionEngaged[id]) sectionEngaged[id] = true
}

function notifyResourceClick() {
  engage('resources')
}

// 监听窗口大小变化重新渲染雷达
watch(() => radar, () => nextTick(() => renderRadar()), { deep: true })

onMounted(() => loadData())
</script>

<style scoped>
/* ═══ 全局变量 ═══ */
.learn-space {
  --bg: #ebe2d7;
  --border: rgba(52, 36, 24, 0.08);
  --text: #342618;
  --muted: #6a6054;
  --accent: #b15311;
  --accent2: #4a7c4e;
  --accent3: #c97930;
  --accent4: #c0564a;
  --radius: 14px;
  font-family: 'Manrope', 'HarmonyOS Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: var(--text);
  background: var(--bg);
  padding-bottom: 4rem;
}

section {
  padding: 5rem 2.5rem;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
section.lite {
  min-height: auto;
  padding: 4rem 2.5rem;
}
.section-title {
  text-align: center;
  font-size: 2rem;
  font-weight: 700;
  margin-bottom: 2.5rem;
  letter-spacing: -0.5px;
}
.section-sub {
  text-align: center;
  color: var(--muted);
  font-size: 13px;
  margin-bottom: 1.5rem;
}

/* ═══ 画像布局 ═══ */
.profile-layout {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 3rem;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  align-items: start;
}
.student-card {
  background: rgba(244, 239, 231, 0.85);
  backdrop-filter: blur(12px);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 40px 32px;
  text-align: center;
  position: sticky;
  top: 5rem;
}
.student-card .sc-avatar { width: 80px; height: 80px; border-radius: 50%; background: linear-gradient(135deg, var(--accent), var(--accent2)); display: flex; align-items: center; justify-content: center; font-size: 36px; margin: 0 auto 1.2rem; box-shadow: 0 0 30px rgba(177, 83, 17, 0.25); }
.student-card .sc-name { font-size: 22px; font-weight: 700; margin-bottom: 4px; }
.student-card .sc-meta { font-size: 13px; color: var(--muted); margin-bottom: 4px; }
.student-card .sc-grade { display: inline-flex; align-items: center; gap: 6px; padding: 6px 16px; border-radius: 20px; font-size: 12px; font-weight: 600; margin: 10px 0; border: 1px solid rgba(177, 83, 17, 0.25); background: rgba(177, 83, 17, 0.06); }
.student-card .sc-summary { font-size: 13px; line-height: 1.7; color: var(--muted); text-align: left; margin-top: 1.5rem; padding: 16px; background: rgba(52, 36, 24, 0.03); border-radius: 12px; border: 1px solid rgba(52, 36, 24, 0.05); }

/* 成长轨迹 */
.growth-track { display: flex; align-items: center; gap: 6px; margin: 12px 0; flex-wrap: wrap; justify-content: center; }
.gt-round { text-align: center; padding: 6px 10px; border-radius: 8px; background: rgba(52, 36, 24, 0.03); border: 1px solid rgba(52, 36, 24, 0.05); min-width: 50px; }
.gt-round.active { border-color: rgba(177, 83, 17, 0.3); background: rgba(177, 83, 17, 0.06); }
.gt-num { font-size: 10px; font-weight: 700; color: var(--accent); display: block; margin-bottom: 2px; }
.gt-desc { font-size: 10px; color: var(--muted); line-height: 1.3; }
.gt-arrow { color: var(--muted); font-size: 12px; }

/* 雷达面板 */
.dimensions-panel { display: flex; flex-direction: column; gap: 1.5rem; }
.radar-card {
  background: rgba(244, 239, 231, 0.85);
  backdrop-filter: blur(12px);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 32px;
}
.radar-card h3 { text-align: center; font-size: 16px; font-weight: 600; margin-bottom: 1rem; }
.radar-chart { width: 320px; height: 320px; margin: 0 auto; }
.radar-legend { display: flex; flex-wrap: wrap; gap: 8px 16px; justify-content: center; margin-top: 12px; font-size: 12px; }
.legend-item { display: flex; align-items: center; gap: 6px; }
.legend-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.legend-val { font-weight: 700; }

/* 维度卡片 */
.dim-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; }
.dim-card {
  background: rgba(244, 239, 231, 0.85);
  backdrop-filter: blur(12px);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 24px 20px;
  transition: transform 0.3s, border-color 0.3s;
}
.dim-card:hover { transform: translateY(-3px); }
.dim-card .dim-icon { font-size: 24px; margin-bottom: 8px; }
.dim-card .dim-title { font-size: 13px; font-weight: 600; margin-bottom: 4px; }
.dim-card .dim-value { font-size: 12px; color: var(--muted); line-height: 1.6; }

/* 思维徽章 */
.thinking-badge { display: inline-flex; align-items: center; gap: 6px; padding: 8px 18px; border-radius: 20px; font-size: 14px; font-weight: 700; margin-bottom: 1rem; }
.thinking-badge.lv-1 { background: rgba(74, 124, 78, 0.08); border: 1px solid rgba(74, 124, 78, 0.2); color: var(--accent2); }
.thinking-badge.lv-2 { background: rgba(177, 83, 17, 0.06); border: 1px solid rgba(177, 83, 17, 0.2); color: var(--accent); }
.thinking-badge.lv-3 { background: rgba(201, 121, 48, 0.08); border: 1px solid rgba(201, 121, 48, 0.2); color: var(--accent3); }

/* 投入统计 */
.eg-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 1rem; }
.eg-stat { text-align: center; padding: 16px 12px; background: rgba(52, 36, 24, 0.03); border-radius: 10px; border: 1px solid rgba(255, 255, 255, 0.03); }
.eg-stat .eg-val { font-size: 22px; font-weight: 800; color: var(--accent); }
.eg-stat .eg-lbl { font-size: 11px; color: var(--muted); margin-top: 2px; }

/* ═══ 学习路径 ═══ */
.path-section { min-height: auto; padding: 4rem 2.5rem; max-width: 1100px; margin: 0 auto; width: 100%; }
.game-map { display: flex; flex-direction: column; align-items: center; gap: 0; width: 100%; padding: 2rem 0; }
.map-row { display: flex; align-items: center; gap: 0; }
.map-node { position: relative; cursor: pointer; display: flex; flex-direction: column; align-items: center; transition: transform 0.3s; z-index: 1; }
.map-node:hover { transform: scale(1.08); }
.map-node .mn-circle { width: 64px; height: 64px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 22px; font-weight: 800; transition: all 0.3s; }
.mn-done .mn-circle { background: var(--accent2); color: #000; box-shadow: 0 0 24px rgba(74, 124, 78, 0.35); }
.mn-current .mn-circle { background: var(--accent); color: #342618; box-shadow: 0 0 28px rgba(177, 83, 17, 0.5); animation: mnPulse 2s infinite; }
.mn-next .mn-circle { background: rgba(52, 36, 24, 0.08); border: 2px solid var(--border); color: var(--muted); }
.mn-locked .mn-circle { background: rgba(255, 255, 255, 0.03); border: 2px solid rgba(52, 36, 24, 0.06); color: #333; }
@keyframes mnPulse { 0%, 100% { box-shadow: 0 0 28px rgba(177, 83, 17, 0.5); } 50% { box-shadow: 0 0 48px rgba(177, 83, 17, 0.7); } }
.map-node .mn-stars { display: flex; gap: 2px; margin-top: 8px; }
.map-node .mn-stars span { font-size: 10px; opacity: 0.7; }
.map-node .mn-stars span.on { opacity: 1; }
.map-node .mn-label { font-size: 12px; font-weight: 600; margin-top: 6px; text-align: center; max-width: 90px; }
.map-node .mn-pct { font-size: 10px; color: var(--muted); margin-top: 2px; }
.map-connector { position: relative; width: 80px; height: 4px; flex-shrink: 0; }
.map-connector::before { content: ''; position: absolute; top: 50%; left: 0; right: 0; height: 3px; border-radius: 2px; transform: translateY(-50%); }
.mc-done::before { background: var(--accent2); box-shadow: 0 0 8px rgba(74, 124, 78, 0.3); }
.mc-active::before { background: linear-gradient(90deg, var(--accent2), rgba(177, 83, 17, 0.3)); }
.mc-next::before { background: rgba(52, 36, 24, 0.06); }
.mc-locked::before { background: rgba(255, 255, 255, 0.03); }
.map-branch { display: flex; align-items: center; justify-content: center; margin: 1rem 0; }
.map-branch-down { width: 3px; height: 40px; background: rgba(177, 83, 17, 0.2); border-radius: 2px; }

/* ═══ 刷题速览 ═══ */
.practice-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px; max-width: 1100px; margin: 0 auto; width: 100%; }
.practice-card {
  background: rgba(244, 239, 231, 0.85);
  backdrop-filter: blur(12px);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 24px;
  transition: all 0.3s;
}
.practice-card:hover { transform: translateY(-4px); box-shadow: 0 12px 40px rgba(0, 0, 0, 0.3); }
.practice-card .pc-diff { font-size: 11px; padding: 3px 10px; border-radius: 10px; background: rgba(52, 36, 24, 0.05); margin-bottom: 10px; display: inline-block; }
.practice-card h3 { font-size: 15px; font-weight: 600; line-height: 1.5; margin-bottom: 4px; }
.practice-card .pc-stat { font-size: 12px; color: var(--muted); }

/* ═══ 评估 ═══ */
.eval-grid-full { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; max-width: 1100px; margin: 0 auto; width: 100%; }
.eval-mod {
  background: rgba(244, 239, 231, 0.85);
  backdrop-filter: blur(12px);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 28px 24px;
}
.eval-mod h3 { font-size: 14px; font-weight: 600; margin-bottom: 16px; color: var(--muted); }
.mod-ai { grid-column: 1 / -1; }

/* 趋势柱状图 */
.trend-chart { display: flex; align-items: flex-end; gap: 20px; height: 140px; padding-top: 8px; }
.trend-bar-col { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; height: 100%; justify-content: flex-end; }
.trend-bar { width: 40px; border-radius: 6px 6px 0 0; min-height: 4px; }
.trend-label { font-size: 10px; color: var(--muted); }
.trend-score { font-size: 13px; font-weight: 700; color: var(--accent); }

/* 易错热力条 */
.err-bar { margin-bottom: 10px; }
.err-bar .err-label { display: flex; justify-content: space-between; font-size: 12px; margin-bottom: 3px; }
.err-bar .err-tag { color: var(--muted); }
.err-bar .err-track { height: 6px; border-radius: 3px; background: rgba(52, 36, 24, 0.06); overflow: hidden; }
.err-bar .err-fill { height: 100%; border-radius: 3px; transition: width 1.2s cubic-bezier(0.16, 1, 0.3, 1); }
.err-fill.hot { background: var(--accent4); }
.err-fill.warm { background: var(--accent3); }
.err-fill.cool { background: var(--accent2); }

/* 薄弱卡片 */
.weak-card { display: flex; align-items: center; gap: 12px; padding: 12px; border-radius: 10px; background: rgba(52, 36, 24, 0.03); margin-bottom: 8px; border: 1px solid rgba(52, 36, 24, 0.05); }
.weak-rank { font-size: 18px; font-weight: 800; color: var(--accent4); min-width: 28px; }
.weak-info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.weak-info strong { font-size: 13px; }
.weak-err { font-size: 11px; color: var(--muted); }
.weak-rec { font-size: 11px; color: var(--accent2); }

/* 对比 */
.cmp-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.cmp-col h4 { font-size: 12px; margin-bottom: 8px; }
.cmp-col ul { list-style: none; padding: 0; margin: 0; }
.cmp-col li { font-size: 12px; padding: 4px 0; color: var(--muted); }
.cmp-up { color: var(--accent2) !important; }
.cmp-down { color: var(--accent4) !important; }

/* ═══ 资源 ═══ */
.resource-section { padding: 4rem 2.5rem; max-width: 1100px; margin: 0 auto; width: 100%; }
.resource-sort { display: flex; gap: 8px; margin-bottom: 1.5rem; flex-wrap: wrap; justify-content: center; }
.resource-sort-btn {
  padding: 8px 18px; border-radius: 20px; border: 1px solid var(--border);
  background: transparent; color: var(--muted); cursor: pointer;
  font-size: 12px; font-weight: 500; transition: all 0.25s; font-family: inherit;
}
.resource-sort-btn:hover, .resource-sort-btn.active {
  color: #342618; background: rgba(177, 83, 17, 0.1); border-color: rgba(177, 83, 17, 0.25);
}
.resource-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 1rem; max-width: 1100px; margin: 0 auto; width: 100%; }
.resource-card {
  background: rgba(244, 239, 231, 0.85);
  backdrop-filter: blur(12px);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 24px;
  transition: all 0.3s;
  cursor: pointer;
  position: relative;
  overflow: hidden;
}
.resource-card:hover { transform: translateY(-4px); box-shadow: 0 12px 40px rgba(0, 0, 0, 0.3); }
.resource-card .rc-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 10px; }
.resource-card .rc-type { font-size: 10px; font-weight: 600; letter-spacing: 0.4px; padding: 3px 10px; border-radius: 4px; }
.resource-card .rc-type.doc { background: rgba(177, 83, 17, 0.1); color: var(--accent); }
.resource-card .rc-type.mindmap { background: rgba(167, 139, 250, 0.1); color: #a78bfa; }
.resource-card .rc-type.exercise { background: rgba(201, 121, 48, 0.1); color: var(--accent3); }
.resource-card .rc-type.article { background: rgba(74, 124, 78, 0.1); color: var(--accent2); }
.resource-card .rc-type.code { background: rgba(192, 86, 74, 0.1); color: var(--accent4); }
.resource-card .rc-dur { font-size: 11px; color: var(--muted); }
.resource-card h3 { font-size: 14px; font-weight: 600; line-height: 1.5; margin-bottom: 6px; }
.resource-card .rc-desc { font-size: 12px; color: var(--muted); line-height: 1.5; margin-bottom: 10px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.resource-card .rc-meta { display: flex; align-items: center; gap: 10px; font-size: 11px; color: var(--muted); }
.resource-card .rc-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.resource-card .rc-tag { font-size: 10px; padding: 3px 8px; border-radius: 4px; background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(52, 36, 24, 0.05); }

/* ═══ 响应式 ═══ */
@media (max-width: 900px) {
  .profile-layout { grid-template-columns: 1fr; }
  .dim-grid { grid-template-columns: 1fr 1fr; }
  .eval-grid-full { grid-template-columns: 1fr; }
  .resource-grid { grid-template-columns: 1fr; }
  section { padding: 3rem 1.2rem; }
}
</style>
