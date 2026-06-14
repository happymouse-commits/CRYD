<template>
  <div class="learning-path-page">
    <div class="page-header">
      <h2>AI学习路径</h2>
      <p class="subtitle">基于你的画像和错题数据，星火大模型为你规划个性化学习路径</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon blue">🧠</div>
        <div class="stat-content">
          <div class="stat-value">{{ knowledgeLevel }}</div>
          <div class="stat-label">知识水平</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon orange">📝</div>
        <div class="stat-content">
          <div class="stat-value">{{ totalQuestions }}</div>
          <div class="stat-label">总题数</div>
        </div>
      </div>
    </div>

    <!-- 每日打卡 -->
    <div class="checkin-section">
      <h3>📅 每日打卡</h3>
      <div class="checkin-bar">
        <el-button type="success" :disabled="checkedIn" @click="handleCheckin">
          {{ checkedIn ? '今日已打卡 ✓' : '打卡签到' }}
        </el-button>
        <span class="streak-info">连续打卡 {{ streakDays }} 天</span>
      </div>
    </div>

    <!-- 知识体系树状图 -->
    <div class="tree-section">
      <div class="section-header">
        <h3>🌳 C语言知识体系</h3>
        <span class="tree-hint">点击节点查看知识点介绍</span>
      </div>
      <div class="tree-container">
        <div ref="treeChartRef" class="tree-chart"></div>
      </div>
      <!-- 节点详情弹窗 -->
      <el-dialog v-model="nodeDialogVisible" :title="selectedNode?.name" width="520px" top="8vh">
        <div class="node-detail" v-if="selectedNode">
          <div class="node-detail-header">
            <span class="node-detail-icon">{{ selectedNode.icon || '📖' }}</span>
            <div>
              <h3>{{ selectedNode.name }}</h3>
              <el-tag v-if="selectedNode.difficulty" :type="diffTagType(selectedNode.difficulty)" size="small">
                {{ selectedNode.difficulty }}
              </el-tag>
            </div>
          </div>
          <el-divider />
          <div class="node-detail-body">
            <div class="detail-section">
              <h4>📋 内容概要</h4>
              <p>{{ selectedNode.description }}</p>
            </div>
            <div class="detail-section" v-if="selectedNode.keywords">
              <h4>🔑 关键词</h4>
              <div class="keyword-tags">
                <el-tag v-for="kw in selectedNode.keywords" :key="kw" size="small" effect="plain" round>
                  {{ kw }}
                </el-tag>
              </div>
            </div>
            <div class="detail-section" v-if="selectedNode.estimatedHours">
              <h4>⏱️ 建议学习时长</h4>
              <p>{{ selectedNode.estimatedHours }}</p>
            </div>
            <div class="detail-section" v-if="selectedNode.prerequisites && selectedNode.prerequisites.length">
              <h4>📌 前置知识</h4>
              <p>{{ selectedNode.prerequisites.join('、') }}</p>
            </div>
          </div>
        </div>
      </el-dialog>
    </div>

    <!-- AI学习路径 -->
    <div class="ai-path-section">
      <div class="section-header">
        <h3>🤖 AI个性化学习路径</h3>
        <el-button v-if="!activePath" type="primary" :loading="generating" @click="generatePath">
          生成我的AI学习路径
        </el-button>
        <el-button v-else type="warning" size="small" :loading="generating" @click="generatePath">
          刷新路径
        </el-button>
      </div>

      <div v-if="activePath" class="ai-path-content">
        <div class="path-progress-bar">
          <el-steps :active="activePath.currentStep - 1" finish-status="success" align-center>
            <el-step v-for="i in activePath.totalSteps" :key="i"
                     :title="'阶段' + i" :description="stepDesc(i)" />
          </el-steps>
        </div>
        <div class="path-detail markdown-body" v-html="renderMarkdown(activePath.steps || '')"></div>
      </div>

      <div v-else class="no-path-hint">
        <el-empty description="还没有AI学习路径，点击上方按钮让星火大模型为你生成" :image-size="80" />
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import { useUserStore } from '../../store/user'
import api from '../../api'
import * as echarts from 'echarts'

const store = useUserStore()
const activePath = ref(null)
const generating = ref(false)
const profile = ref({})
const checkedIn = ref(false)
const streakDays = ref(0)
const treeChartRef = ref(null)
const nodeDialogVisible = ref(false)
const selectedNode = ref(null)
let treeChart = null

const knowledgeLevel = computed(() => profile.value.knowledgeLevel || 0)

// ==================== 知识体系树数据 ====================
const knowledgeTreeData = {
  name: 'C语言程序设计',
  icon: '📘',
  description: 'C语言是计算机科学的基石，掌握C语言有助于理解计算机底层原理和培养编程思维。本课程涵盖从基础语法到高级内存管理的完整知识体系。',
  difficulty: '入门',
  estimatedHours: '总计约 120-160 小时',
  keywords: ['C语言', '程序设计', '系统编程', '数据结构'],
  children: [
    {
      name: '基础语法',
      icon: '🔤',
      description: '学习C语言的基本构建块，包括变量声明与初始化、基本数据类型（int、float、char等）、常量定义、运算符（算术、关系、逻辑、位运算）、表达式求值与类型转换。这是后续所有章节的根基。',
      difficulty: '简单',
      estimatedHours: '8-12 小时',
      keywords: ['变量', '数据类型', '运算符', '表达式', '常量', '类型转换'],
      prerequisites: [],
      collapsed: false,
      children: [
        { name: '变量与常量', icon: '📝', difficulty: '简单', description: '变量的声明、初始化、命名规则、作用域初步了解；const常量与#define宏常量的区别与使用场景。', estimatedHours: '2-3 小时', keywords: ['变量声明', '初始化', 'const', '#define', '命名规范'] },
        { name: '数据类型', icon: '🔢', difficulty: '简单', description: '整型（short/int/long）、浮点型（float/double）、字符型（char）、void类型、枚举类型（enum）的详细讲解，以及各类型的存储大小和取值范围。', estimatedHours: '3-4 小时', keywords: ['int', 'float', 'double', 'char', 'void', 'enum', 'sizeof'] },
        { name: '运算符与表达式', icon: '➕', difficulty: '简单', description: '算术运算符（+ - * / %）、关系运算符（> < == !=）、逻辑运算符（&& || !）、位运算符（& | ^ ~ << >>）、赋值运算符、条件运算符（?:）、运算符优先级与结合性。', estimatedHours: '3-5 小时', keywords: ['算术运算', '逻辑运算', '位运算', '优先级', '自增自减'] },
      ]
    },
    {
      name: '控制结构',
      icon: '🔀',
      description: '掌握程序的流程控制，包括条件判断（if/else if/else、switch-case）和循环结构（for、while、do-while）。理解break、continue、goto等跳转语句的使用场景与注意事项。',
      difficulty: '简单',
      estimatedHours: '10-14 小时',
      keywords: ['if-else', 'switch', 'for', 'while', 'break', 'continue'],
      prerequisites: ['基础语法'],
      collapsed: false,
      children: [
        { name: '条件判断', icon: '❓', difficulty: '简单', description: 'if/else if/else多分支结构、嵌套if、switch-case多路选择（含break穿透问题）、条件表达式的短路求值特性。', estimatedHours: '4-6 小时', keywords: ['if', 'else', 'switch', 'case', 'default'] },
        { name: '循环结构', icon: '🔄', difficulty: '简单', description: 'for循环的三要素（初始化/条件/更新）、while前测循环、do-while后测循环、循环嵌套、break与continue控制循环执行流。', estimatedHours: '4-5 小时', keywords: ['for', 'while', 'do-while', '循环嵌套', 'break', 'continue'] },
        { name: '综合练习', icon: '✏️', difficulty: '中等', description: '打印图形（金字塔、菱形）、九九乘法表、素数判断、最大公约数/最小公倍数、简单计算器等经典编程练习。', estimatedHours: '2-3 小时', keywords: ['循环练习', '算法入门', '编程思维'] },
      ]
    },
    {
      name: '函数与作用域',
      icon: '🔧',
      description: '学习函数的定义、声明、调用与参数传递机制（值传递与地址传递）。理解局部变量、全局变量、静态变量的作用域与生命周期，以及递归函数的设计思想。',
      difficulty: '中等',
      estimatedHours: '12-16 小时',
      keywords: ['函数定义', '参数传递', '递归', '作用域', '静态变量'],
      prerequisites: ['控制结构'],
      collapsed: false,
      children: [
        { name: '函数基础', icon: '📦', difficulty: '中等', description: '函数声明（原型）、定义与调用；形式参数与实际参数；返回值与return语句；void函数；函数的模块化设计思想。', estimatedHours: '4-5 小时', keywords: ['函数原型', '参数', '返回值', '模块化'] },
        { name: '作用域与生命周期', icon: '🌐', difficulty: '中等', description: '局部变量（auto）、全局变量（extern）、静态局部变量（static）、寄存器变量（register）的区别；变量的作用域、可见性与生命周期。', estimatedHours: '3-4 小时', keywords: ['局部变量', '全局变量', 'static', 'extern', '作用域'] },
        { name: '递归函数', icon: '🪞', difficulty: '困难', description: '递归的基本原理（基准条件+递归条件）；经典递归问题：阶乘、斐波那契数列、汉诺塔；递归与迭代的比较；递归的栈开销与深度限制。', estimatedHours: '5-7 小时', keywords: ['递归', '基准条件', '阶乘', '斐波那契', '汉诺塔', '栈'] },
      ]
    },
    {
      name: '数组与字符串',
      icon: '📊',
      description: '掌握一维数组和二维数组的定义、初始化与遍历。理解C风格字符串的本质（以\\0结尾的字符数组），学习常用字符串处理函数（strlen、strcpy、strcmp等）及缓冲区安全。',
      difficulty: '中等',
      estimatedHours: '12-16 小时',
      keywords: ['一维数组', '二维数组', '字符串', 'strlen', 'strcpy', '排序'],
      prerequisites: ['函数与作用域'],
      collapsed: false,
      children: [
        { name: '一维数组', icon: '📏', difficulty: '中等', description: '数组定义与初始化、下标访问、数组遍历、数组作为函数参数（数组名即地址）、冒泡排序与选择排序算法。', estimatedHours: '5-6 小时', keywords: ['数组定义', '下标', '遍历', '排序', '冒泡排序', '选择排序'] },
        { name: '二维数组', icon: '📐', difficulty: '中等', description: '二维数组的定义与初始化（行优先存储）、矩阵遍历、矩阵加法/乘法/转置、二维数组作为函数参数。', estimatedHours: '3-4 小时', keywords: ['二维数组', '矩阵', '行优先', '矩阵运算'] },
        { name: '字符串处理', icon: '📝', difficulty: '中等', description: `字符数组与字符串的区别、'\\0'终止符的重要性；strlen/strcpy/strcat/strcmp等标准库函数的使用与实现原理；sprintf/snprintf格式化输出；缓冲区溢出风险与防范。`, estimatedHours: '4-6 小时', keywords: ['字符串', '\\0', 'strlen', 'strcpy', 'strcmp', '缓冲区溢出'] },
      ]
    },
    {
      name: '指针（核心难点）',
      icon: '👉',
      description: 'C语言的灵魂与核心难点。理解指针的本质（存储地址的变量），掌握指针与数组、指针与函数、多级指针、指针运算等概念。这是从入门到精通的分水岭。',
      difficulty: '困难',
      estimatedHours: '18-24 小时',
      keywords: ['指针', '地址', '指针运算', '指针与数组', '指针与函数', '多级指针'],
      prerequisites: ['数组与字符串', '函数与作用域'],
      collapsed: false,
      children: [
        { name: '指针基础', icon: '📍', difficulty: '困难', description: '指针变量的定义与初始化、取地址运算符（&）与解引用运算符（*）、指针的大小（与系统位数相关）、NULL指针与野指针的危害。', estimatedHours: '4-5 小时', keywords: ['指针变量', '&', '*', 'NULL', '野指针'] },
        { name: '指针与数组', icon: '🔗', difficulty: '困难', description: '数组名即指向首元素的指针、指针算术运算（p++/p--/p+n）、通过指针遍历数组、指针与下标的等价关系（*(arr+i) ≡ arr[i]）、指针数组与数组指针的区别。', estimatedHours: '5-7 小时', keywords: ['指针运算', '数组名', '指针数组', '数组指针'] },
        { name: '指针与函数', icon: '🔌', difficulty: '困难', description: '指针作为函数参数实现"传引用"效果、函数返回指针（注意不要返回局部变量地址）、函数指针的定义与使用、回调函数的基本概念。', estimatedHours: '5-6 小时', keywords: ['传引用', '函数指针', '回调函数', '返回指针'] },
        { name: '多级指针与const', icon: '🎯', difficulty: '困难', description: '二级指针（指向指针的指针）的理解与应用、const修饰指针的三种形式（const int *p / int * const p / const int * const p）辨析。', estimatedHours: '4-6 小时', keywords: ['二级指针', 'const指针', '指针常量', '常量指针'] },
      ]
    },
    {
      name: '结构体与联合',
      icon: '🏗️',
      description: '学习使用struct组织相关数据，使用typedef简化类型名，理解结构体数组与结构体指针。了解union（联合体）的内存共享机制，以及enum枚举类型的用法。',
      difficulty: '中等',
      estimatedHours: '10-14 小时',
      keywords: ['struct', 'typedef', 'union', 'enum', '链表'],
      prerequisites: ['指针（核心难点）'],
      collapsed: false,
      children: [
        { name: '结构体基础', icon: '🧱', difficulty: '中等', description: '结构体类型定义与变量声明、成员访问（.运算符）、结构体初始化、结构体数组、typedef简化类型名。', estimatedHours: '4-5 小时', keywords: ['struct', '成员访问', 'typedef', '结构体数组'] },
        { name: '结构体与指针', icon: '🧷', difficulty: '中等', description: '指向结构体的指针、箭头运算符（->）、结构体作为函数参数（值传递vs地址传递）、动态分配结构体内存。', estimatedHours: '3-4 小时', keywords: ['结构体指针', '->', '动态分配', 'malloc'] },
        { name: '联合体与枚举', icon: '🗂️', difficulty: '简单', description: 'union的内存共享原理（所有成员共享同一块内存）、union的大小计算、enum枚举类型的定义与使用、枚举与整型的转换。', estimatedHours: '3-5 小时', keywords: ['union', 'enum', '内存共享', '枚举常量'] },
      ]
    },
    {
      name: '文件操作',
      icon: '📁',
      description: '学习C语言的文件I/O操作，包括文件的打开与关闭（fopen/fclose）、读写操作（fread/fwrite/fprintf/fscanf等）、文件定位（fseek/ftell/rewind）以及错误处理。',
      difficulty: '中等',
      estimatedHours: '10-14 小时',
      keywords: ['fopen', 'fclose', 'fread', 'fwrite', 'fprintf', 'fseek', 'FILE'],
      prerequisites: ['结构体与联合'],
      collapsed: false,
      children: [
        { name: '文件操作基础', icon: '📂', difficulty: '中等', description: '文件指针（FILE*）的概念、fopen的打开模式（r/w/a/r+/w+/a+及二进制b模式）、fclose的重要性（刷新缓冲区）、文本文件与二进制文件的区别。', estimatedHours: '4-5 小时', keywords: ['FILE*', 'fopen', 'fclose', '打开模式', '二进制文件'] },
        { name: '文件读写', icon: '✍️', difficulty: '中等', description: '字符级I/O（fgetc/fputc）、行级I/O（fgets/fputs）、格式化I/O（fprintf/fscanf）、块I/O（fread/fwrite）的使用场景与选择。', estimatedHours: '4-5 小时', keywords: ['fgetc', 'fputc', 'fgets', 'fprintf', 'fscanf', 'fread', 'fwrite'] },
        { name: '文件定位与错误处理', icon: '🎯', difficulty: '中等', description: 'fseek设置文件位置指针、ftell获取当前位置、rewind回到文件开头；ferror与feof的用法、clearerr清除错误标志。', estimatedHours: '2-4 小时', keywords: ['fseek', 'ftell', 'rewind', 'ferror', 'feof'] },
      ]
    },
    {
      name: '动态内存管理',
      icon: '💾',
      description: '掌握malloc/calloc/realloc/free等动态内存管理函数的使用，理解堆内存与栈内存的区别，学会防范内存泄漏、悬垂指针和重复释放等常见问题。',
      difficulty: '困难',
      estimatedHours: '10-14 小时',
      keywords: ['malloc', 'calloc', 'realloc', 'free', '内存泄漏', '堆', '栈'],
      prerequisites: ['指针（核心难点）'],
      collapsed: false,
      children: [
        { name: '动态内存分配', icon: '🗳️', difficulty: '困难', description: 'malloc分配指定字节数的内存、calloc分配并初始化为0、realloc调整已分配内存大小；返回值为NULL时的错误处理；sizeof在分配中的重要性。', estimatedHours: '4-5 小时', keywords: ['malloc', 'calloc', 'realloc', 'sizeof', 'NULL检查'] },
        { name: '内存管理陷阱', icon: '⚠️', difficulty: '困难', description: '内存泄漏（忘记free）、悬垂指针（free后继续使用）、重复释放（double free）、越界访问；动态分配的内存必须手动释放，否则进程结束前一直占用。', estimatedHours: '3-4 小时', keywords: ['内存泄漏', '悬垂指针', 'double free', '越界', 'free'] },
        { name: '动态数据结构', icon: '🔗', difficulty: '困难', description: '使用动态内存分配实现链表（单链表、双向链表）的创建、遍历、插入、删除操作；动态数组的实现。', estimatedHours: '3-5 小时', keywords: ['链表', '动态数组', '节点', '插入', '删除', '遍历'] },
      ]
    },
    {
      name: '预处理器与多文件编译',
      icon: '⚙️',
      description: '理解C语言的编译预处理阶段，掌握宏定义（#define）、文件包含（#include）、条件编译（#ifdef/#ifndef）等预处理指令。学习多文件项目的组织与Makefile基础。',
      difficulty: '中等',
      estimatedHours: '8-12 小时',
      keywords: ['#define', '#include', '#ifdef', '宏', '多文件', 'Makefile', '头文件'],
      prerequisites: ['文件操作'],
      collapsed: false,
      children: [
        { name: '预处理器指令', icon: '📋', difficulty: '中等', description: '#define宏定义（带参数与不带参数）、宏的展开规则与副作用、#undef取消宏定义；#include的尖括号<>与双引号""区别；条件编译（#ifdef/#ifndef/#if/#else/#endif）的使用场景（防止头文件重复包含、调试代码、平台适配）。', estimatedHours: '4-6 小时', keywords: ['#define', '#include', '#ifdef', '#ifndef', '宏展开', '条件编译'] },
        { name: '多文件编译', icon: '📚', difficulty: '中等', description: '头文件（.h）与源文件（.c）的分工：头文件放声明（函数原型、extern变量、类型定义），源文件放实现；#include头文件；避免循环依赖；static函数与全局函数的可见性控制。', estimatedHours: '3-4 小时', keywords: ['头文件', '.h', '.c', '声明', '定义', 'extern', 'static'] },
        { name: '编译链接与Makefile', icon: '🔨', difficulty: '中等', description: '预处理→编译→汇编→链接四阶段概览；gcc基本用法（-c/-o/-I/-L/-l）；Makefile的基本语法（目标、依赖、命令）；自动化构建的概念。', estimatedHours: '1-2 小时', keywords: ['gcc', '编译', '链接', 'Makefile', '目标', '依赖'] },
      ]
    },
  ]
}

// ==================== ECharts 树状图 ====================
function initTreeChart() {
  if (!treeChartRef.value) return
  if (treeChart) treeChart.dispose()

  treeChart = echarts.init(treeChartRef.value)

  const option = {
    tooltip: {
      trigger: 'item',
      triggerOn: 'mousemove',
      backgroundColor: '#f4efe7',
      borderColor: '#b15311',
      borderWidth: 2,
      borderRadius: 8,
      padding: [12, 16],
      textStyle: { color: '#342618', fontSize: 13 },
      formatter: function (params) {
        if (params.dataType !== 'node') return ''
        const d = params.data
        if (!d || d.value === 'root') return `<b>${d.name}</b>`
        return `
          <div style="max-width:260px">
            <b style="font-size:15px;color:#b15311">${d.icon || ''} ${d.name}</b>
            ${d.difficulty ? `<br/><span style="color:#b6ada1">难度：</span>${d.difficulty}` : ''}
            ${d.estimatedHours ? `<br/><span style="color:#b6ada1">建议学时：</span>${d.estimatedHours}` : ''}
            ${d.description ? `<br/><span style="color:#b6ada1">简介：</span><span style="font-size:12px">${d.description.slice(0, 80)}...</span>` : ''}
            <br/><span style="color:#b15311;font-size:12px">💡 点击查看详情</span>
          </div>
        `
      }
    },
    series: [
      {
        type: 'tree',
        data: [knowledgeTreeData],
        top: '2%',
        left: '4%',
        bottom: '2%',
        right: '4%',
        symbol: 'roundRect',
        symbolSize: [16, 10],
        orient: 'LR',
        expandAndCollapse: true,
        initialTreeDepth: 2,
        label: {
          position: 'right',
          verticalAlign: 'middle',
          align: 'left',
          fontSize: 12,
          color: '#342618',
          formatter: function (params) {
            const d = params.data
            const icon = d.icon || ''
            const name = d.name
            // 只在第1层和第2层节点显示标签
            if (params.treePathInfo && params.treePathInfo.length <= 3) {
              return `${icon} ${name}`
            }
            return name.length > 8 ? name.slice(0, 8) + '...' : name
          }
        },
        leaves: {
          label: {
            position: 'right',
            verticalAlign: 'middle',
            align: 'left',
            fontSize: 11,
            color: '#6a6054'
          }
        },
        lineStyle: {
          color: '#b3d8ff',
          width: 2,
          curveness: 0.5
        },
        itemStyle: {
          color: '#b15311',
          borderColor: '#b15311',
          borderWidth: 2
        },
        emphasis: {
          focus: 'descendant',
          lineStyle: { color: '#b15311', width: 3 },
          itemStyle: { shadowBlur: 12, shadowColor: 'rgba(177,83,17,0.5)' }
        },
        // 叶子节点样式
        leavesStyle: {
          color: '#f4efe7',
          borderColor: '#4a7c4e',
          borderWidth: 1.5
        }
      }
    ]
  }

  treeChart.setOption(option)

  // 点击节点事件
  treeChart.on('click', function (params) {
    if (params.dataType === 'node' && params.data && params.data.name) {
      const d = params.data
      // 找到匹配的知识点详情
      const detail = findNodeDetail(knowledgeTreeData, d.name)
      if (detail) {
        selectedNode.value = detail
        nodeDialogVisible.value = true
      }
    }
  })

  // 响应式调整
  window.addEventListener('resize', () => treeChart?.resize())
}

function findNodeDetail(treeData, name) {
  if (treeData.name === name) return treeData
  if (treeData.children) {
    for (const child of treeData.children) {
      const found = findNodeDetail(child, name)
      if (found) return found
    }
  }
  return null
}

function diffTagType(difficulty) {
  const map = { '简单': 'success', '入门': 'success', '中等': 'warning', '困难': 'danger' }
  return map[difficulty] || 'info'
}

function stepDesc(i) { return i <= (activePath.value?.currentStep || 0) ? '已完成' : '待学习' }

function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(text)
}

async function loadData() {
  try {
    const r = await api.get('/learning-path/student/' + store.id + '/active')
    activePath.value = (r.data && r.data.status === 'active') ? r.data : null
  } catch {}
  try {
    const r = await api.get('/profile/' + store.id)
    profile.value = r.data || {}
  } catch {}
  // 加载今日打卡状态
  try {
    const r = await api.get('/learning-path/checkin/today/' + store.id)
    checkedIn.value = r.data?.checkedIn || false
  } catch {}
  // 加载连续打卡天数
  try {
    const r = await api.get('/learning-path/checkin/calendar/' + store.id, {
      params: { month: new Date().toISOString().slice(0, 7) }
    })
    streakDays.value = r.data?.streakDays || 0
  } catch {}
}

async function handleCheckin() {
  try {
    await api.post('/learning-path/checkin', { studentId: store.id })
    checkedIn.value = true
    try {
      const r = await api.get('/learning-path/checkin/calendar/' + store.id, {
        params: { month: new Date().toISOString().slice(0, 7) }
      })
      streakDays.value = r.data?.streakDays || 0
    } catch {}
    ElMessage.success('打卡成功！')
  } catch {
    ElMessage.error('打卡失败')
  }
}

async function generatePath() {
  generating.value = true
  try {
    const r = await api.post('/learning-path/generate/' + store.id)
    activePath.value = r.data
    ElMessage.success('AI学习路径已生成！')
  } catch { ElMessage.error('生成失败，请稍后重试') }
  finally { generating.value = false }
}

onMounted(() => {
  loadData()
  nextTick(() => {
    setTimeout(() => initTreeChart(), 200)
  })
})
</script>

<style scoped>
.learning-path-page { }
.page-header { margin-bottom: 20px; }
.page-header h2 { margin: 0 0 4px 0; font-size: 20px; font-weight: 700; color: #342618; }
.subtitle { color: #b6ada1; font-size: 13px; margin: 0; }

.stats-row { display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; }

.checkin-section { margin-bottom: 20px; }
.checkin-section h3 { margin-bottom: 12px; font-size: 16px; color: #6a6054; }
.checkin-bar { display: flex; align-items: center; gap: 12px; }
.streak-info { font-size: 13px; font-weight: 600; color: #4a7c4e; }

.stat-card {
  flex: 1; min-width: 140px; background: #f4efe7; border-radius: 14px; padding: 14px 18px;
  display: flex; align-items: center; gap: 12px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.04); border: 1px solid #dad2c7;
}
.stat-icon { font-size: 26px; }
.stat-value { font-size: 22px; font-weight: 700; color: #342618; }
.stat-label { font-size: 12px; color: #b6ada1; }

.ai-path-section {
  background: #f4efe7; border-radius: 14px; padding: 20px;
  margin-bottom: 20px; box-shadow: 0 1px 6px rgba(0,0,0,0.04); border: 1px solid #dad2c7;
}
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.section-header h3 { margin: 0; font-size: 16px; color: #6a6054; }
.path-progress-bar { margin-bottom: 20px; padding: 16px 0; }
.path-detail {
  padding: 16px; background: #e4dfd8; border-radius: 10px;
  max-height: 500px; overflow-y: auto; line-height: 1.8; font-size: 14px;
  border: 1px solid #dad2c7;
}
.path-detail :deep(h2) { font-size: 18px; margin: 16px 0 8px; color: #342618; }
.path-detail :deep(h3) { font-size: 15px; margin: 12px 0 6px; color: #342618; }
.path-detail :deep(h4) { font-size: 14px; margin: 10px 0 4px; color: #6a6054; }
.path-detail :deep(p) { margin: 0 0 8px; }
.path-detail :deep(ul), .path-detail :deep(ol) { margin: 6px 0; padding-left: 20px; }
.path-detail :deep(li) { margin-bottom: 3px; }
.path-detail :deep(pre) { background: #e4dfd8; padding: 10px; border-radius: 8px; overflow-x: auto; margin: 8px 0; }
.path-detail :deep(code) { font-family: Consolas, 'Courier New', monospace; font-size: 13px; }
.path-detail :deep(pre code) { background: none; padding: 0; }
.path-detail :deep(blockquote) { border-left: 3px solid #b15311; padding: 4px 12px; margin: 8px 0; background: rgba(177,83,17,0.04); border-radius: 0 6px 6px 0; }
.path-detail :deep(table) { border-collapse: collapse; margin: 8px 0; }
.path-detail :deep(th), .path-detail :deep(td) { border: 1px solid #dad2c7; padding: 6px 10px; }
.no-path-hint { padding: 20px 0; }

/* 知识体系树状图 */
.tree-section {
  background: #f4efe7; border-radius: 14px; padding: 20px;
  margin-bottom: 20px; box-shadow: 0 1px 6px rgba(0,0,0,0.04); border: 1px solid #dad2c7;
}
.tree-section .section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.tree-section .section-header h3 { margin: 0; font-size: 16px; color: #6a6054; }
.tree-hint { font-size: 11px; color: #b6ada1; }
.tree-container { width: 100%; }
.tree-chart { width: 100%; height: 480px; }

/* 节点详情弹窗 */
.node-detail-header { display: flex; align-items: center; gap: 12px; }
.node-detail-header h3 { margin: 0; font-size: 17px; color: #342618; }
.node-detail-icon { font-size: 30px; }
.detail-section { margin-bottom: 14px; }
.detail-section h4 { margin: 0 0 6px 0; font-size: 13px; color: #6a6054; }
.detail-section p { margin: 0; font-size: 13px; color: #6a6054; line-height: 1.8; }
.keyword-tags { display: flex; flex-wrap: wrap; gap: 6px; }

</style>