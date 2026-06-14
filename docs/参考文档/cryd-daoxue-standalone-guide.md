# AI导学页面 — 独立抽取

## 文件
📄 **`C:\Users\404NotFound\Desktop\cryd-daoxue-standalone.html`** (26KB)
→ 双击浏览器即可预览，Typer 暖色主题 + GooeyNav 胶囊导航。

## 包含的模块（完整、可独立运行）

| 模块 | 说明 |
|------|------|
| **GooeyNav 导航栏** | 3个标签(AI导学/学习空间/刷题房)，点击粒子扩散，铜橙激活态 |
| **AI导学页面** | 左侧标题+进度+诊断摘要，右侧聊天卡片 |
| **诊断对话** | 3轮 flows，每轮5题，onboard/normal 模式切换 |
| **`sendMessage()`** | 用户输入→bot回复→进度推进→完成诊断 |
| **`botSay()`** | 打字动画→气泡渲染 |
| **`completeOnboarding()`** | 生成诊断摘要标签（✅ ⚠ 🆕） |
| **`backToDaoxue()`** | 从进度环返回，≥4 sections 时进入新一轮考核 |
| **浮动进度环** | 可拖拽定位，≥75% 弹出"去考核"气泡 |
| **全部数据** | `flows`(3轮诊断题)、`masteryByRound`(掌握度) |

## 与原项目的接口点（修改后合并时需要对齐）

1. **`enterLearningSpace()`** — 当前是 `alert()` 占位，合并时替换为原项目的页面跳转逻辑
2. **`switchPage()`** — 原型是3页面单页切换，独立抽取省略了学习空间和刷题房的 HTML/JS，合并时不需要动
3. **`createGooeyParticles()`** — 粒子函数，合并时原项目已有，可直接覆盖
4. **GooeyNav 点击事件** — 独立抽取省略了页面切换（因为没有其他两个页面），合并时原项目的导航点击逻辑不变
5. **CSS 变量** — `:root` 里 Typer 暖色调，和原项目完全一致

## 合并回原项目的方式

```
原项目: Desktop/cryd-redesign-prototype.html  (1401行)
独立抽取 AI导学部分的:
  HTML → 替换 #page-daoxue 区块
  CSS  → 替换 daoxue 相关样式
  JS   → 替换 sendMessage/botSay/completeOnboarding/setupOnboardMode 等函数
```

## 数据对接路线（下一步）

当前全部是 mock 数据，对接后端 API 时：
- `sendMessage()` → `/api/onboarding/chat` (SSE)
- 诊断完成 → `/api/onboarding/dimensions/{id}`
- 自由对话 → `/api/chat/send`
