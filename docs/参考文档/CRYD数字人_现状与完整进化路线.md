# CRYD 数字人——现状 & 完整进化路线

> 最后更新：2026-06-12 18:25  
> 项目：从容应对（CRYD）· 学生端 · 首页数字人

---

## 一、当前状态（Plan A 已落地）

### 1.1 头像文件

| 项目 | 值 |
|------|-----|
| 文件 | `frontend/public/avatar.png` |
| 来源 | `true_portrait_s0.15.png` |
| 生成方式 | SD1.5 img2img（真人照片 → 写实风格半身像） |
| 风格 | **写实**，非二次元/动漫 |
| 范围 | 头+肩膀，无完整身体 |

### 1.2 组件：`src/components/DigitalHumanAvatar.vue`

**四个 CSS 动画状态**：

| 状态 | 触发条件 | 视觉效果 |
|------|----------|----------|
| `idle` | 默认 / 无操作 | 静态照片 + 微弱呼吸光晕 |
| `listening` | 用户按住录音 | 麦克风波纹 + 头像边框脉冲 |
| `thinking` | AI 等待回复 | 旋转光环 + 渐变色扫过 |
| `speaking` | AI 正在打字回复 | 嘴部区域高亮 + 声波动画 |

Props：
- `state`: `'idle' | 'speaking' | 'listening' | 'thinking' | 'error'`
- `avatarSrc`: 头像图片路径（默认 `/avatar.png`）

### 1.3 在首页的接入（`src/views/学生端/首页.vue`）

```
布局：左列（数字人+聊天） │ 中列（雷达图+画像） │ 右列（学习路径）
```

```js
// 状态映射
const avatarState = computed(() => {
  if (dh.state.isRecording) return 'listening'   // 按住录音中
  if (dh.loading.value) return 'thinking'         // 等待 AI 回复
  if (dh.state.mode === 'speaking') return 'speaking' // AI 回复中
  return 'idle'
})
```

```html
<DigitalHumanAvatar :state="avatarState" :avatarSrc="'/avatar.png'" />
```

### 1.4 当前短板

- **idle 态太死板**：就是一张静态照片，呼吸光晕效果微弱
- **没有身体**：只有头像（头+肩），不是完整人物
- **没有口型同步**：speaking 态只是视觉特效，嘴不动
- **不能换装/换表情**：单张 PNG，无图层分离

---

## 二、完整进化路线（从静态照片 → 全身 Live2D）

```
现在 (v1)          v1.5               v2               v3
静态头像    →   半身分层动画   →   全身 Live2D   →   3D VRM 模型
(PNG一张)       (PSD 分层)        (外包/自制)       (Three.js)
```

---

### 🔵 阶段一：给现在的头像加身体（最小成本）

**目标**：把 `avatar.png` 从"大头照"升级为"半身/全身立绘"

#### 方案 A1：SD 直接出全身图

用 Stable Diffusion img2img，在现有头像基础上 **outpaint（外扩）** 生成肩膀以下的身体：

**步骤**：
1. 准备一张 512×768 或 512×1024 的画布
2. 把 `avatar.png` 放在画布上方（头的位置）
3. 用 SD img2img + ControlNet（Inpaint/Outpaint）扩图
4. Prompt 示例：`1girl, teacher outfit, formal blouse, standing, full body, realistic, clean background, soft lighting`
5. 出图后替换 `public/avatar.png`

**优点**：1小时内搞定，不需要新代码  
**缺点**：还是静态图，只有一张，不能换表情/换衣服

#### 方案 A2：头部+身体分层（推荐作为过渡）

把数字人拆成两层 PNG：
- `body.png` — 身体+背景（不变）
- `head.png` — 头部（可套 CSS 动画：微摆、眨眼）

**改动**：
- `DigitalHumanAvatar.vue` 增加一个 `<img>` 显示 body
- head 层继续用现有四态 CSS 动画

**成本**：约 2-3 小时（SD 出两张图 + 改组件）

---

### 🟡 阶段二：分层 PSD → Live2D 半身（中等成本）

**目标**：把静态立绘变成可动的 Live2D 模型

#### 前置：准备分层 PSD

需要美术把写实头像拆成图层（PhotoShop）：
- 头发前层 / 头发后层
- 脸部
- 眉毛 / 眼睛（左/右）
- 鼻子
- 嘴巴（张嘴/闭嘴两张）
- 身体
- 手臂（可选）

**关键**：当前头像是写实风格，Live2D 默认的网格变形对写实风格效果不如二次元好，但可以做**轻度动画**（眨眼+嘴张合+头微摆）。

#### Live2D 模型生成流程

1. 分层 PSD → 导入 **Live2D Cubism Editor**
2. 建立网格（Mesh）和形变参数（Parameter）
3. 绑定物理效果（呼吸、头发摇摆）
4. 导出 `.moc3` + `.model3.json` + 纹理图集

#### 前端对接

用 `pixi-live2d-display` 库加载 `.model3.json` 并控制动画参数：

```js
// 伪代码示意
import { Live2DModel } from 'pixi-live2d-display'

const model = await Live2DModel.from('/models/tutor.model3.json')
app.stage.addChild(model)

// 控制口型（配合 TTS 音频）
model.internalModel.coreModel.setParameterValueById('ParamMouthOpenY', 0.8)
```

**优点**：专业级效果，可控参数多  
**缺点**：需要 Live2D 建模（外包约 500-2000 元 / 自学 Cubism Editor 约 1-2 周）

---

### 🟢 阶段三：完整身体 → 全身 Live2D（高成本）

在阶段二的基础上，把身体延伸到全身：
- PSD 包含完整躯干 + 手臂 + 下半身（或半身露到桌面）
- 增加手臂手势动画（讲课手势）
- 身体微摆，呼吸起伏

**根据项目场景（讲题老师）推荐姿势**：
- 站立半身，手部有小幅度讲解手势
- 或者坐在桌前，上半身前倾，像在对着屏幕讲题

---

### 🔴 阶段四：VRM 3D 模型（最大成本，之前讨论过）

之前的路线是 VRoid Studio → VRM → Three.js + `@pixiv/three-vrm`：

1. 用 **VRoid Studio**（免费）捏人 → 导出 `.vrm`
2. 前端用 Three.js + `@pixiv/three-vrm` 加载
3. TTS 音频 → 口型同步（LipSync）
4. 可换装、换发型、换表情

**当时放弃的原因**：
- VRoid 默认风格偏二次元，和当前写实头像不搭
- 需要额外开发 Three.js 3D 场景
- 依赖体积大（Three.js ~150KB gzip + VRM loader）

**何时再考虑**：如果后面决定全面转向二次元风格，VRM 是最佳路线。

---

## 三、TTS 语音 + 口型同步路线图

| 阶段 | TTS 方案 | 口型同步 | 备注 |
|------|---------|---------|------|
| 现在 | 后端返回文本，前端打字机 | ❌ 无 | speaking 态只是CSS特效 |
| v1.5 | 同上 | CSS嘴部动画模拟 | 在 speaking 态加一个嘴巴开合的 CSS animation |
| v2 | 后端返回文本 + 可选音频URL | Live2D 参数驱动 | 需要后端 TTS 服务生成音频 |
| v3 | 后端 TTS 流式推送 | Live2D/VRM LipSync | 流式音频 + 实时口型 |

**最快的改进（今天就做）**：  
在 `DigitalHumanAvatar.vue` 的 speaking 态里加一个嘴巴张合的 CSS 动画：

```css
.mouth {
  animation: mouthTalk 0.3s infinite alternate;
}
@keyframes mouthTalk {
  0% { transform: scaleY(1); }
  100% { transform: scaleY(0.3); }
}
```

---

## 四、输入框"发送不了"问题（已修复，待验证）

### 根因

`useDigitalHuman.js` 中 `sendMessage()` 有静默守卫：
```js
if (!msg || loading.value) return  // 防重复提交，但 loading 卡死时什么都不会发生
```

### 已做的修复

| # | 修复 | 文件 |
|---|------|------|
| 1 | 删除 `:disabled="dh.loading"` | `首页.vue` 模板 |
| 2 | onMounted 强制 `dh.loading.value = false` | `首页.vue` |
| 3 | 启动即显示本地欢迎消息（不依赖 API） | `首页.vue` |
| 4 | API 调用加超时 + catch 兜底 | `useDigitalHuman.js` |
| 5 | Proxy 改回 `8.137.186.118:8080`（之前错的 8081 超时） | `vite.config.js` |

### 验证方法

```bash
# 本地验证 API 是否通
curl -X POST http://localhost:5175/api/chat/send \
  -H "Content-Type: application/json" \
  -d '{"message":"hello","studentId":2}'
# 预期返回：{"code":200,"data":{"message":"你好！..."}}
```

### 用户端操作

1. **关掉所有 localhost 标签页**
2. 打开 `http://localhost:5175`
3. `Ctrl+Shift+R` 硬刷新
4. 登录（student1 / 123456）
5. 输入框打字回车

### 故障排查

| 现象 | 可能原因 | 解决 |
|------|----------|------|
| 输入框灰色点不了 | 浏览器缓存了旧 JS | Ctrl+Shift+R 硬刷新 |
| 点了发送没反应 | 还在旧端口（5173/5174） | 检查地址栏是不是 `localhost:5175` |
| 发送后显示网络错误 | Vite 没在跑或 proxy 错了 | `node .\node_modules\vite\bin\vite.js --port 5175` |
| 发送后有回复但乱码 | UTF-8 编码问题 | 后端 response header 加 `Content-Type: application/json;charset=UTF-8` |

---

## 五、关键文件速查

```
F:\从容应对\frontend\
│
├── public/
│   └── avatar.png                    ← 🎭 数字人头像（替换这张=换脸）
│
├── src/
│   ├── components/
│   │   └── DigitalHumanAvatar.vue    ← 🎭 四态CSS动画组件
│   │
│   ├── views/学生端/
│   │   └── 首页.vue                  ← 📄 学生端首页（左=数字人+聊天，中=画像，右=路径）
│   │
│   ├── composables/
│   │   └── useDigitalHuman.js        ← 🧠 聊天状态机（sendMessage/loadHistory/语音/打字机）
│   │
│   ├── api/
│   │   └── index.js                  ← 🔌 Axios 实例（baseURL='/api'，拦截器自动解包 response.data）
│   │
│   └── store/
│       └── user.js                   ← 👤 用户 Store（cryd_id/cryd_token → localStorage）
│
├── vite.config.js                    ← ⚙️ Proxy /api → 8.137.186.118:8080
└── package.json
```

---

## 六、后端模型切换（deepseek v4 pro）

当前 `/api/chat/send` 返回的 model 字段是 `deepseek-chat`。  
要换成 `deepseek v4 pro`，需要登录 ECS 修改后端配置文件：

- 服务器：`8.137.186.118`
- SSH：`ssh kkx01925@8.137.186.118`
- 后端项目路径：`/root/SGHR/` 或 `/root/CRYD/`（需确认）
- 改完后重启：`systemctl restart cryd` 或重新 `java -jar`

**这不是前端能做的事。**
