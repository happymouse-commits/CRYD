# CRYD AI导学页面 — 完整代码（给 GPT 修改用）

> **项目**：从容应对（CRYD）· C语言智能学习平台
> **页面**：AI导学（daoxue）
> **配色**：Typer 暖沙色主题（#ebe2d7 底、#b15311 铜橙主色）
> **导航**：GooeyNav 胶囊风格 + 12 粒子扩散动画
> **双击 `.html` 文件即可浏览器预览**

---


## 完整代码

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>CRYD — AI导学 · 页面抽取</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Manrope:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
<style>
/* ═══════════════════════════════════════════
   CRYD AI导学页面 — 完整独立抽取
   从 cryd-redesign-prototype.html 抽取
   ═══════════════════════════════════════════ */

/* ═══ 全局变量 ═══ */
:root {
  --bg:#ebe2d7; --border:rgba(52,36,24,.08); --text:#342618; --muted:#6a6054;
  --accent:#b15311; --accent2:#4a7c4e; --accent3:#c97930; --accent4:#c0564a;
  --nav-h:3.6rem; --radius:14px;
  --font:'Manrope','HarmonyOS Sans SC','PingFang SC','Microsoft YaHei',sans-serif;
}
*{box-sizing:border-box;margin:0;padding:0}
html{scroll-behavior:smooth;overflow-x:hidden}
body{
  background:var(--bg); color:var(--text); font-family:var(--font);
  font-weight:400; line-height:1.6; -webkit-font-smoothing:antialiased;
  padding-top:var(--nav-h);
  min-height:100vh;
}
body::before{content:'';position:fixed;inset:0;z-index:-1;background:radial-gradient(ellipse 80% 60% at 50% -10%,rgba(177,83,17,.06),transparent),radial-gradient(ellipse 40% 30% at 80% 70%,rgba(74,124,78,.04),transparent),radial-gradient(ellipse 30% 40% at 20% 40%,rgba(201,121,48,.04),transparent);pointer-events:none}
::-webkit-scrollbar{width:6px}::-webkit-scrollbar-track{background:var(--bg)}::-webkit-scrollbar-thumb{background:#c4b9a8;border-radius:3px}

/* ═══ 导航栏 - GooeyNav 风格 ═══ */
.navbar{position:fixed;top:0;left:0;right:0;z-index:100;height:var(--nav-h);display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:2rem;padding:0 2.5rem;backdrop-filter:blur(16px) saturate(180%);-webkit-backdrop-filter:blur(16px) saturate(180%);background:rgba(235,226,215,.82);border-bottom:1px solid var(--border)}
.nav-logo{display:flex;align-items:center;gap:10px;font-weight:700;font-size:18px;letter-spacing:-.3px}
.nav-logo .dot{width:10px;height:10px;border-radius:50%;background:var(--accent);box-shadow:0 0 10px var(--accent)}
.gooey-nav{display:flex;justify-content:center;position:relative}
.gooey-nav ul{display:flex;gap:6px;list-style:none;margin:0;padding:4px}
.gooey-nav li{position:relative;border-radius:999px;overflow:visible;transition:all .3s cubic-bezier(.16,1,.3,1)}
.gooey-nav li a{display:block;padding:8px 22px;text-decoration:none;font-size:13px;font-weight:500;letter-spacing:.4px;color:rgba(52,36,24,.55);position:relative;z-index:2;transition:all .25s cubic-bezier(.16,1,.3,1);white-space:nowrap;border:1px solid transparent;border-radius:999px}
.gooey-nav li a:hover{color:#342618;background:rgba(52,36,24,.05);transform:translateY(-1px)}
.gooey-nav li.active a{color:#b15311;font-weight:600}
.gooey-nav li.active{background:#ead6c2;box-shadow:0 8px 24px rgba(177,83,17,.15),0 0 0 1px rgba(177,83,17,.1)}
.gooey-nav li.active a:hover{transform:translateY(-1px)}
.gooey-nav li.daoxue-tab.active{background:rgba(201,121,48,.15);box-shadow:0 8px 24px rgba(201,121,48,.12),0 0 0 1px rgba(201,121,48,.15)}
.gooey-nav li.daoxue-tab.active a{color:var(--accent3)}
.gooey-particle{position:absolute;left:50%;top:50%;width:10px;height:10px;border-radius:50%;background:var(--accent);pointer-events:none;z-index:0;animation:gooeyPop .6s ease forwards}
@keyframes gooeyPop{0%{transform:translate(0,0) scale(.4);opacity:1}100%{transform:translate(var(--gx),var(--gy)) scale(0);opacity:0}}
.nav-actions{display:flex;align-items:center;gap:10px;font-size:13px;color:var(--muted)}

/* ═══ 按钮 ═══ */
.btn-solid{position:relative;background:var(--accent);border:none;color:#342618;padding:7px 18px;border-radius:10px;font-size:13px;font-weight:600;cursor:pointer;transition:all .3s;font-family:var(--font);box-shadow:0 0 0 0 rgba(177,83,17,.4)}
.btn-solid:hover{transform:translateY(-2px);box-shadow:0 0 24px 4px rgba(177,83,17,.35),0 4px 12px rgba(0,0,0,.3)}
.btn-solid:active{transform:translateY(0)}
.btn-solid-lg{padding:12px 28px;font-size:15px}

/* ═══ AI导学页面 ═══ */
#page-daoxue{min-height:100vh;padding-top:2rem}
.dx-layout{max-width:1400px;margin:0 auto;padding:0 2.5rem 3rem;display:grid;grid-template-columns:1fr 580px;gap:3rem;align-items:start}
.dx-left{padding-top:2rem}
.dx-badge{display:inline-flex;align-items:center;gap:8px;padding:6px 14px;border-radius:20px;font-size:12px;font-weight:600;letter-spacing:.5px;margin-bottom:1.5rem;border:1px solid rgba(74,124,78,.25);color:var(--accent2);background:rgba(74,124,78,.06);transition:all .4s}
.dx-badge .pulse{width:7px;height:7px;border-radius:50%;background:var(--accent2);animation:pulse 2s infinite}
@keyframes pulse{0%,100%{opacity:1;box-shadow:0 0 0 0 rgba(74,124,78,.4)}50%{opacity:.6;box-shadow:0 0 0 6px rgba(74,124,78,0)}}
.dx-left h1{font-size:clamp(2rem,4vw,2.6rem);font-weight:800;line-height:1.2;letter-spacing:-1px}
.dx-left h1 span{background:linear-gradient(135deg,var(--accent),var(--accent2));-webkit-background-clip:text;-webkit-text-fill-color:transparent}
.dx-left .sub{color:var(--muted);font-size:15px;margin-top:1rem;max-width:420px}
.dx-progress{margin-top:2rem;transition:opacity .3s}
.dx-progress .prog-label{font-size:12px;color:var(--muted);letter-spacing:.5px;margin-bottom:8px}
.prog-bar{height:4px;border-radius:2px;background:rgba(52,36,24,.08);overflow:hidden;max-width:320px}
.prog-bar .fill{height:100%;border-radius:2px;background:var(--accent);transition:width .4s}
.step-summary{margin-top:1rem;list-style:none}
.step-summary li{font-size:13px;padding:4px 0;display:flex;align-items:center;gap:8px;transition:color .3s;color:var(--muted)}
.step-summary li::before{content:'';width:6px;height:6px;border-radius:50%;background:rgba(52,36,24,.1)}
.step-summary li.done{color:var(--accent2)}.step-summary li.done::before{background:var(--accent2)}
.step-summary li.active{color:var(--text);font-weight:500}.step-summary li.active::before{background:var(--accent);box-shadow:0 0 6px rgba(177,83,17,.5)}
.dx-summary{display:none;text-align:center;background:rgba(244,239,231,.85);border:1px solid rgba(74,124,78,.15);border-radius:var(--radius);padding:28px 32px;margin-top:2rem;max-width:460px}
.dx-summary.show{display:block}
.dx-summary h3{font-size:18px;font-weight:700;color:var(--accent2)}
.dx-summary .tags{display:flex;flex-wrap:wrap;gap:8px;justify-content:center;margin-top:12px}
.dx-summary .tag{padding:6px 14px;border-radius:20px;font-size:12px;font-weight:500;border:1px solid}
.tag-strong{border-color:rgba(74,124,78,.3)!important;color:var(--accent2)!important}
.tag-weak{border-color:rgba(201,121,48,.3)!important;color:var(--accent3)!important}
.tag-new{border-color:rgba(167,139,250,.3)!important;color:#a78bfa!important}

/* ═══ 聊天卡片 ═══ */
.chat-card{background:rgba(244,239,231,.85);backdrop-filter:blur(20px);-webkit-backdrop-filter:blur(20px);border:1px solid rgba(255,255,255,.08);border-radius:18px;overflow:hidden;display:flex;flex-direction:column;height:clamp(520px,70vh,680px);box-shadow:0 24px 80px rgba(0,0,0,.4),inset 0 1px 0 rgba(255,255,255,.03)}
.chat-card-header{padding:16px 20px;border-bottom:1px solid var(--border);display:flex;align-items:center;gap:12px}
.chat-avatar{width:36px;height:36px;border-radius:50%;background:var(--accent);display:flex;align-items:center;justify-content:center;font-size:14px;font-weight:700;box-shadow:0 0 14px rgba(177,83,17,.3)}
.chat-card-body{flex:1;overflow-y:auto;padding:20px;display:flex;flex-direction:column;gap:14px}
.msg-bot{display:flex;gap:10px;align-items:flex-start;max-width:88%}
.msg-bot .bubble{background:rgba(52,36,24,.06);border-radius:0 14px 14px 14px;padding:12px 16px;font-size:14px;line-height:1.6}
.msg-bot .bubble .label{color:var(--accent);font-weight:600;font-size:12px;margin-bottom:4px;letter-spacing:.5px}
.msg-user{display:flex;justify-content:flex-end}
.msg-user .bubble{background:var(--accent);border-radius:14px 0 14px 14px;padding:10px 16px;font-size:14px;max-width:70%}
.chat-card-footer{padding:14px 20px;border-top:1px solid var(--border);display:flex;gap:10px}
.chat-card-footer input{flex:1;background:rgba(52,36,24,.05);border:1px solid var(--border);border-radius:10px;padding:10px 14px;color:var(--text);font-size:14px;font-family:var(--font);outline:none;transition:border-color .2s}
.chat-card-footer input:focus{border-color:var(--accent)}
.chat-card-footer input::placeholder{color:#4a5568}
.chat-card-footer button{background:var(--accent);border:none;border-radius:10px;padding:10px 16px;color:#342618;cursor:pointer;font-size:14px;font-weight:600;font-family:var(--font);transition:all .3s}
.chat-card-footer button:hover{box-shadow:0 0 20px 3px rgba(177,83,17,.35)}
.typing-dots{display:flex;gap:4px;padding:4px 0}
.typing-dots span{width:6px;height:6px;border-radius:50%;background:var(--accent);animation:typing 1.4s infinite}
.typing-dots span:nth-child(2){animation-delay:.2s}.typing-dots span:nth-child(3){animation-delay:.4s}
@keyframes typing{0%,60%,100%{opacity:.3;transform:translateY(0)}30%{opacity:1;transform:translateY(-4px)}}
.next-step-bar{display:none;text-align:center;padding:1.5rem 0 2rem}
.next-step-bar.show{display:block}

/* ═══ 浮动进度环 ═══ */
#floatProgress{position:fixed;top:120px;right:24px;z-index:90;width:60px;height:60px;cursor:grab;user-select:none;transition:transform .3s,opacity .3s}
#floatProgress.dragging{cursor:grabbing;transform:scale(1.1)}
#floatProgress:hover #floatProgress .box{transform:scale(1.05)}
#floatProgress .tooltip{display:none;position:absolute;bottom:calc(100% + 14px);right:-20px;background:rgba(244,239,231,.95);border:1px solid var(--border);border-radius:12px;padding:14px 18px;font-size:12px;text-align:center;white-space:nowrap;box-shadow:0 12px 40px rgba(0,0,0,.2);backdrop-filter:blur(12px)}
#floatProgress.ready .tooltip{display:block}
#floatProgress .tooltip button{margin-top:8px;background:var(--accent2);border:none;color:#fff;padding:6px 14px;border-radius:8px;font-size:11px;cursor:pointer;font-family:var(--font)}
#floatProgress .box{width:60px;height:60px;border-radius:50%;background:rgba(244,239,231,.9);border:1px solid var(--border);display:flex;align-items:center;justify-content:center;backdrop-filter:blur(8px);box-shadow:0 8px 24px rgba(0,0,0,.15)}
#floatProgress .box .lbl{position:absolute;font-size:11px;font-weight:700;color:var(--text)}
.fgCirc{fill:none;stroke:var(--accent);stroke-width:3;stroke-linecap:round;transform:rotate(-90deg);transform-origin:center;transition:stroke-dashoffset .6s}
.bgCirc{fill:none;stroke:rgba(52,36,24,.08);stroke-width:3}

/* ═══ 响应式 ═══ */
@media(max-width:1024px){
  .navbar{padding:0 1.2rem;gap:1rem}
  .nav-logo{font-size:15px}
  .dx-layout{grid-template-columns:1fr;padding:0 1.2rem 2rem}
  .dx-left{padding-top:1rem}
}
@media(max-width:768px){
  .gooey-nav li a{font-size:11px;padding:6px 10px}
  .chat-card{height:clamp(380px,55vh,500px)}
  #floatProgress{display:none!important}
}
</style>
</head>
<body>

<!-- GooeyNav 导航栏 -->
<nav class="navbar">
  <div class="nav-logo"><span class="dot"></span>从容应对</div>
  <div class="gooey-nav" id="gooeyNav">
    <ul>
      <li class="daoxue-tab active" data-page="daoxue"><a href="#">AI导学</a></li>
      <li data-page="learn"><a href="#">学习空间</a></li>
      <li data-page="practice-room"><a href="#">刷题房</a></li>
    </ul>
  </div>
  <div class="nav-actions">👤 张小泉</div>
</nav>

<!-- 浮动进度环 -->
<div id="floatProgress">
  <div class="tooltip">
    学得差不多了！<br>回到AI导学进行下一轮考核？
    <br><button onclick="backToDaoxue()">去考核 →</button>
  </div>
  <div class="box">
    <svg width="48" height="48" viewBox="0 0 48 48">
      <circle class="bgCirc" cx="24" cy="24" r="21"/>
      <circle class="fgCirc" id="fgCirc" cx="24" cy="24" r="21" stroke-dasharray="131.95" stroke-dashoffset="131.95"/>
    </svg>
    <span class="lbl" id="fpLabel">0%</span>
  </div>
</div>

<!-- AI导学页面 -->
<div id="page-daoxue">
<div class="dx-layout">
  <div class="dx-left">
    <div class="dx-badge" id="dxBadge"><span class="pulse"></span><span id="badgeText">新用户 · 智能诊断中</span></div>
    <h1 id="dxTitle">Hi，我是<span>小容</span> 👋<br>帮你搞定C语言</h1>
    <p class="sub" id="dxSub">通过AI对话了解你的C语言水平，自动生成个性化学习画像、路径和资源，持续追踪成长。</p>
    <div class="dx-progress" id="dxProgress">
      <div class="prog-label" id="progLabel">诊断进度 0 / 5</div>
      <div class="prog-bar"><div class="fill" id="progFill" style="width:0%"></div></div>
      <ul class="step-summary" id="stepSummary">
        <li class="active">学习背景</li><li>C语言基础</li><li>指针理解</li><li>数据结构</li><li>学习目标</li>
      </ul>
    </div>
    <div class="dx-summary" id="dxSummary">
      <h3 id="summaryTitle">✅ 诊断完成</h3>
      <p style="color:var(--muted);margin:8px 0;font-size:13px" id="summaryDesc"></p>
      <div class="tags" id="summaryTags"></div>
    </div>
    <div class="next-step-bar" id="nextStepBar">
      <button class="btn-solid btn-solid-lg" onclick="enterLearningSpace()">进入学习空间 →</button>
      <p style="color:var(--muted);font-size:12px;margin-top:8px" id="nextStepHint"></p>
    </div>
  </div>
  <div>
    <div class="chat-card">
      <div class="chat-card-header">
        <div class="chat-avatar">🤖</div>
        <div>
          <div style="font-weight:600;font-size:14px">小容 AI</div>
          <div style="font-size:11px;color:var(--muted)">C语言学习助手 · 多智能体</div>
        </div>
      </div>
      <div class="chat-card-body" id="chatBody"></div>
      <div class="chat-card-footer">
        <input id="chatInput" placeholder="输入你的回答..." autocomplete="off">
        <button id="sendBtn">发送</button>
      </div>
    </div>
  </div>
</div>
</div>

<script>
/* ═══ 诊断对话数据 ═══ */
const flows = [
  { questions:[
    { ask:'同学你好！👋 我是小容。先简单了解一下——你之前学过 C 语言吗？大概到什么程度？', hint:'比如：学过一学期 / 自学过一点 / 完全零基础...' },
    { ask:'基础部分：下面哪些你能熟练写出来？\n① for/while 循环 ② if/switch ③ 数组遍历 ④ 函数定义与调用', hint:'说你会哪些就行' },
    { ask:'指针这块很多同学觉得难——`int *p = &a; printf("%d",*p);` 这行代码，你能解释它干了什么吗？', hint:'简单描述就行' },
    { ask:'数据结构和动态内存呢？malloc/free、struct、链表，这些接触过吗？', hint:'可以说 "学过但忘了" "没学过"...' },
    { ask:'最后一个小问题～你这次想重点提升什么？考试 / 比赛 / 找工作？', hint:'随便说' },
  ]},
  { questions:[
    { ask:'欢迎回来！现在检验一下：二级指针 `int **pp` 是什么？什么时候会用到？', hint:'能说多少说多少' },
    { ask:'`malloc(sizeof(int)*10)` 返回的指针和普通指针有什么区别？不 free 会怎样？', hint:'堆内存相关' },
    { ask:'`int arr[5]; int *p = arr;` 那么 `p[2]` 和 `*(arr+2)` 等价吗？', hint:'想想数组名是什么' },
    { ask:'函数指针见过吗？`int (*f)(int,int)` 这行代码是什么意思？', hint:'比如回调函数、qsort' },
    { ask:'`char *s = "hello";` sizeof(s) 和 strlen(s) 分别是多少？', hint:'指针大小 vs 字符串长度' },
  ]},
  { questions:[
    { ask:'用 C 语言实现一个链表节点 struct，包含数据域和指针域，怎么写？', hint:'struct Node { ... };' },
    { ask:'malloc 失败会返回什么？不检查就直接用会有什么后果？', hint:'NULL 和段错误' },
    { ask:'栈和堆的区别：局部变量 `int a[1000000]` 放哪？改为 malloc 呢？', hint:'栈溢出 vs 堆分配' },
    { ask:'`free(ptr)` 之后 ptr 还指向原来的地址，叫什么问题？怎么避免？', hint:'悬垂指针 + 置 NULL' },
    { ask:'文件 I/O：`fopen` 打开文件失败返回什么？', hint:'NULL 和资源泄漏' },
  ]},
];

const masteryByRound = [
  { data_types:88, branches:82, arrays:75, pointers:38, structs:0, files:0 },
  { data_types:90, branches:85, arrays:78, pointers:55, structs:10, files:0 },
  { data_types:92, branches:88, arrays:82, pointers:68, structs:40, files:15 },
];

/* ═══ 全局状态 ═══ */
const ST = {
  round: 0, mode: 'onboard', step: 0, messages: [],
  mastery: {}, sections: { profile:false, path:false, practice:false, resources:false }
};
let currentFlow = flows[0];

/* ═══ 初始化 ═══ */
window.addEventListener('DOMContentLoaded', () => {
  const saved = localStorage.getItem('cryd_round');
  if (saved !== null) {
    ST.round = Math.min(parseInt(saved), flows.length-1);
    ST.mode = 'normal';
    ST.mastery = masteryByRound[ST.round];
  } else {
    ST.round = 0;
    ST.mode = 'onboard';
    ST.mastery = {};
  }
  if (ST.mode === 'normal') setupNormalMode();
  else setupOnboardMode();

  document.getElementById('sendBtn').addEventListener('click', sendMessage);
  document.getElementById('chatInput').addEventListener('keydown', e => {
    if (e.key === 'Enter') sendMessage();
  });
});

/* ═══ GooeyNav 粒子效果 ═══ */
function createGooeyParticles(li) {
  for (let i = 0; i < 12; i++) {
    const p = document.createElement('span');
    p.className = 'gooey-particle';
    p.style.setProperty('--gx', `${Math.random() * 120 - 60}px`);
    p.style.setProperty('--gy', `${Math.random() * 80 - 40}px`);
    li.appendChild(p);
    setTimeout(() => p.remove(), 600);
  }
}

/* ═══ AI导学模式 ═══ */
function setupOnboardMode() {
  currentFlow = flows[0];
  document.getElementById('chatInput').placeholder = currentFlow.questions[0].hint;
  botSay(currentFlow.questions[0].ask, null, 200);
  updateProgress();
}

function setupNormalMode() {
  const badge = document.getElementById('dxBadge');
  const progress = document.getElementById('dxProgress');
  const summary = document.getElementById('dxSummary');
  const nextBar = document.getElementById('nextStepBar');

  badge.style.borderColor = 'rgba(74,124,78,.25)';
  badge.style.color = 'var(--accent2)';
  document.getElementById('badgeText').textContent = 'AI 自由对话';
  document.getElementById('dxTitle').innerHTML = 'Hi，<span>张小泉</span> 👋<br>欢迎回来';
  document.getElementById('dxSub').textContent = '随时问我 C 语言问题，或直接开始新一轮挑战。';
  progress.style.opacity = '0';
  summary.classList.remove('show');
  nextBar.classList.remove('show');

  const flow = flows[ST.round];
  ST.messages = [{ role: 'bot', text: 'Hi 张小泉 👋 欢迎回来！准备好了吗？', label: '小容' }];
  renderChat();
  setTimeout(() => {
    botSay('那我们开始吧——' + flow.questions[0].ask, null, 600);
  }, 2000);
  document.getElementById('chatInput').placeholder = flow.questions[0].hint;
  ST.mode = 'onboard';
  ST.step = 0;
  currentFlow = flow;
}

/* ═══ 聊天渲染 ═══ */
function renderChat() {
  const body = document.getElementById('chatBody');
  body.innerHTML = ST.messages.map(m =>
    m.role === 'bot'
      ? `<div class="msg-bot"><div class="bubble"><div class="label">🤖 ${m.label || '小容'}</div>${m.text}</div></div>`
      : `<div class="msg-user"><div class="bubble">${m.text}</div></div>`
  ).join('');
  body.scrollTop = body.scrollHeight;
}

function botSay(text, label, delay) {
  const body = document.getElementById('chatBody');
  const typing = document.createElement('div');
  typing.className = 'msg-bot';
  typing.innerHTML = `<div class="bubble" style="min-width:60px;display:flex;align-items:center;gap:8px;font-size:13px;color:var(--muted)">正在输入...<div class="typing-dots"><span></span><span></span><span></span></div></div>`;
  body.appendChild(typing);
  body.scrollTop = body.scrollHeight;

  setTimeout(() => {
    typing.remove();
    ST.messages.push({ role: 'bot', text, label });
    renderChat();
    if (ST.mode === 'onboard') {
      ST.step++;
      updateProgress();
      if (ST.step >= currentFlow.questions.length) completeOnboarding();
    }
  }, delay || 1400);
}

/* ═══ 进度流程 ═══ */
function updateProgress() {
  const total = currentFlow.questions.length, s = ST.step;
  document.getElementById('progLabel').textContent = `诊断进度 ${s} / ${total}`;
  document.getElementById('progFill').style.width = `${(s / total) * 100}%`;
  const items = document.querySelectorAll('#stepSummary li');
  items.forEach((li, i) => {
    li.className = '';
    if (i < s) li.className = 'done';
    else if (i === s && s < total) li.className = 'active';
  });
}

function completeOnboarding() {
  ST.mode = 'done';
  ST.mastery = masteryByRound[Math.min(ST.round, masteryByRound.length - 1)];
  const r = ST.round;

  document.getElementById('dxProgress').style.opacity = '0';
  document.getElementById('dxSummary').classList.add('show');
  document.getElementById('nextStepBar').classList.add('show');

  if (r === 0) {
    document.getElementById('summaryTitle').textContent = '✅ 诊断完成';
    document.getElementById('summaryDesc').textContent = '基础部分掌握不错，指针和数据结构是重点提升方向。';
    document.getElementById('summaryTags').innerHTML =
      '<span class="tag tag-strong">✅ 数据类型</span>' +
      '<span class="tag tag-strong">✅ 分支循环</span>' +
      '<span class="tag tag-weak">⚠ 指针</span>' +
      '<span class="tag tag-strong">✅ 函数</span>' +
      '<span class="tag tag-weak">⚠ 动态内存</span>' +
      '<span class="tag tag-new">🆕 结构体</span>';
    document.getElementById('nextStepHint').textContent = '预计 5 分钟学完指针入门，再回来考核';
  } else {
    buildRoundSummary(r);
    document.getElementById('summaryTitle').textContent = '✅ 考核完成';
    document.getElementById('summaryDesc').textContent = '你的 C 语言能力在持续提升！';
    document.getElementById('nextStepHint').textContent = '进入学习空间继续学习，完成后来 AI 导学进入下一轮';
  }
  document.getElementById('badgeText').textContent = '诊断完成';
}

function buildRoundSummary(r) {
  const m = masteryByRound[r];
  const names = {
    data_types: '数据类型', branches: '分支循环', arrays: '数组函数',
    pointers: '指针', structs: '结构体', files: '文件操作'
  };
  let html = '';
  for (const [k, v] of Object.entries(m)) {
    if (v === 0) continue;
    if (v >= 70) html += `<span class="tag tag-strong">✅ ${names[k]}</span>`;
    else if (v >= 30) html += `<span class="tag tag-weak">⚠ ${names[k]}</span>`;
    else html += `<span class="tag tag-new">🆕 ${names[k]}</span>`;
  }
  document.getElementById('summaryTags').innerHTML = html;
}

/* ═══ 发送消息 ═══ */
function sendMessage() {
  const input = document.getElementById('chatInput');
  const text = input.value.trim();
  if (!text) return;
  input.value = '';
  ST.messages.push({ role: 'user', text });
  renderChat();

  if (ST.mode === 'onboard' && ST.step < currentFlow.questions.length - 1) {
    botSay(currentFlow.questions[ST.step + 1].ask, null, 1000);
  } else if (ST.mode === 'onboard' && ST.step === currentFlow.questions.length - 1) {
    botSay('好的！信息收集完毕——', '分析中', 800);
    setTimeout(() => completeOnboarding(), 1200);
  } else {
    botSay('收到！还有什么 C 语言问题随时问我～ 😊', null, 600);
  }

  if (ST.mode === 'onboard' && ST.step < currentFlow.questions.length) {
    input.placeholder = currentFlow.questions[ST.step]?.hint || '输入你的回答...';
  } else {
    input.placeholder = '输入你的 C 语言问题...';
  }
}

/* ═══ 返回导学 ═══ */
function backToDaoxue() {
  document.getElementById('floatProgress').style.display = 'none';
  window.scrollTo({ top: 0, behavior: 'smooth' });
  document.querySelectorAll('#gooeyNav li').forEach(x => x.classList.remove('active'));
  document.querySelector('#gooeyNav li[data-page="daoxue"]').classList.add('active');

  const done = Object.values(ST.sections).filter(Boolean).length;
  if (done >= 4) {
    ST.round = Math.min(ST.round + 1, flows.length - 1);
    localStorage.setItem('cryd_round', String(ST.round));
    ST.mode = 'onboard'; ST.step = 0; ST.messages = [];
    currentFlow = flows[ST.round];

    document.getElementById('dxProgress').style.opacity = '1';
    document.getElementById('dxSummary').classList.remove('show');
    document.getElementById('nextStepBar').classList.remove('show');
    document.getElementById('dxTitle').innerHTML = '准备好了吗？<span>开始挑战</span> 🔥';
    document.getElementById('dxSub').textContent = '难度升级！';
    document.getElementById('progFill').style.width = '0%';
    document.getElementById('progLabel').textContent = `考核进度 0 / ${currentFlow.questions.length}`;
    document.getElementById('stepSummary').innerHTML = currentFlow.questions.map((_, i) =>
      `<li${i === 0 ? ' class="active"' : ''}>环节 ${i + 1}</li>`
    ).join('');
    document.getElementById('chatInput').placeholder = currentFlow.questions[0].hint;
    botSay(currentFlow.questions[0].ask, null, 500);
    document.getElementById('badgeText').textContent = '智能考核中';
  } else {
    ST.messages = []; ST.mode = 'normal';
    botSay('回来了～ 还有一些章节没看完，要不要先回去转转？', null, 400);
    document.getElementById('chatInput').placeholder = '输入你的 C 语言问题...';
  }
}

/* ═══ 进入学习空间 (占位) ═══ */
function enterLearningSpace() {
  alert('🔜 即将进入学习空间\n\n这里对接后会跳转到画像、路径、刷题、评估、资源等模块。');
}

/* ═══ 浮动进度环 ═══ */
function updateFloatProgress() {
  const done = Object.values(ST.sections).filter(Boolean).length;
  const total = 4, pct = Math.round((done / total) * 100);
  const circ = 131.95;
  document.getElementById('fgCirc').style.strokeDashoffset = circ - (pct / 100) * circ;
  document.getElementById('fpLabel').textContent = pct + '%';
  const el = document.getElementById('floatProgress');
  if (pct >= 75) el.classList.add('ready');
  else el.classList.remove('ready');
}

(function initFloatProgress() {
  const el = document.getElementById('floatProgress');
  el.style.display = 'none';
  let dragging = false, ox, oy;
  el.addEventListener('mousedown', e => {
    if (e.target.closest('.tooltip button')) return;
    dragging = true; el.classList.add('dragging');
    ox = e.clientX - el.offsetLeft; oy = e.clientY - el.offsetTop;
    e.preventDefault();
  });
  document.addEventListener('mousemove', e => {
    if (!dragging) return;
    el.style.left = (e.clientX - ox) + 'px';
    el.style.top = (e.clientY - oy) + 'px';
    el.style.right = 'auto';
  });
  document.addEventListener('mouseup', () => {
    if (!dragging) return;
    dragging = false; el.classList.remove('dragging');
    const r = el.getBoundingClientRect(), ww = window.innerWidth;
    if (r.left + r.width / 2 < ww / 2) { el.style.left = '24px'; el.style.right = 'auto'; }
    else { el.style.left = 'auto'; el.style.right = '24px'; }
    if (r.top < 70) el.style.top = '70px';
    if (r.top + r.height > window.innerHeight - 20) el.style.top = (window.innerHeight - r.height - 20) + 'px';
    localStorage.setItem('fp_pos', JSON.stringify({ left: el.style.left, right: el.style.right, top: el.style.top }));
  });
  const saved = localStorage.getItem('fp_pos');
  if (saved) {
    try {
      const p = JSON.parse(saved);
      el.style.left = p.left || 'auto'; el.style.right = p.right || '24px'; el.style.top = p.top || '120px';
    } catch {}
  }
})();
</script>
</body>
</html>
```

---

## 合并回原项目时注意

改完后把 HTML 的 3 个部分替换回 `cryd-redesign-prototype.html`：
- `<style>` 中的 AI导学相关 CSS
- `#page-daoxue` 的 HTML
- `<script>` 中的 `sendMessage`/`botSay`/`completeOnboarding`/`setupOnboardMode`/`backToDaoxue` 等函数

`enterLearningSpace()` 当前是占位，合并时替换为原项目的页面跳转逻辑。
