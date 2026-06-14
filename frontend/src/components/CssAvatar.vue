<template>
  <div class="css-avatar" :class="state">
    <!-- 光环 -->
    <div class="ring outer-ring"></div>
    <div class="ring inner-ring"></div>

    <!-- 头部 -->
    <div class="head">
      <!-- 头发 -->
      <div class="hair"></div>
      <div class="hair-bangs"></div>

      <!-- 眉毛 -->
      <div class="eyebrow left"></div>
      <div class="eyebrow right"></div>

      <!-- 眼睛 -->
      <div class="eye left">
        <div class="eyelid"></div>
        <div class="pupil"></div>
      </div>
      <div class="eye right">
        <div class="eyelid"></div>
        <div class="pupil"></div>
      </div>

      <!-- 鼻子 -->
      <div class="nose"></div>

      <!-- 嘴巴 -->
      <div class="mouth-wrap">
        <div class="mouth-closed"></div>
        <div class="mouth-open"></div>
      </div>

      <!-- 腮红 -->
      <div class="blush left"></div>
      <div class="blush right"></div>

      <!-- 身体 -->
      <div class="body">
        <div class="collar"></div>
      </div>
    </div>

    <!-- 状态文字 -->
    <div class="state-tag" v-if="state !== 'idle'">{{ label }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({
  state: { type: String, default: 'idle' },
})
const label = computed(() => ({
  speaking: '正在讲解…', listening: '正在倾听…', thinking: '正在思考…', idle: ''
}[props.state] || ''))
</script>

<style scoped>
.css-avatar {
  position: relative;
  width: 120px; height: 150px;
  display: flex; align-items: center; justify-content: center;
}

/* ===== 光环 ===== */
.ring {
  position: absolute;
  top: 50%; left: 50%; transform: translate(-50%, -50%);
  border-radius: 50%;
  pointer-events: none;
}
.outer-ring {
  width: 118px; height: 118px;
  border: 2px dashed rgba(177,83,17,0.3);
  animation: ringSpin 8s linear infinite;
}
.inner-ring {
  width: 104px; height: 104px;
  background: radial-gradient(circle, rgba(177,83,17,0.12) 0%, transparent 70%);
  animation: ringPulse 2.5s ease-in-out infinite;
}
.css-avatar.thinking .outer-ring { border-color: rgba(139,92,246,0.5); animation-duration: 3s; }
.css-avatar.thinking .inner-ring { background: radial-gradient(circle, rgba(139,92,246,0.2) 0%, transparent 70%); animation-duration: 1.5s; }
.css-avatar.speaking .outer-ring { border-color: rgba(74,124,78,0.5); animation-duration: 1.5s; }
.css-avatar.speaking .inner-ring { background: radial-gradient(circle, rgba(74,124,78,0.2) 0%, transparent 70%); animation-duration: 0.8s; }
.css-avatar.listening .outer-ring { border-color: rgba(201,121,48,0.5); animation-duration: 5s; }
.css-avatar.listening .inner-ring { background: radial-gradient(circle, rgba(201,121,48,0.2) 0%, transparent 70%); animation-duration: 2s; }

@keyframes ringSpin { to { transform: translate(-50%,-50%) rotate(360deg); } }
@keyframes ringPulse {
  0%,100% { opacity: 0.5; transform: translate(-50%,-50%) scale(0.95); }
  50% { opacity: 1; transform: translate(-50%,-50%) scale(1.06); }
}

/* ===== 头部 ===== */
.head {
  position: relative;
  width: 88px; height: 96px;
  border-radius: 44px 44px 36px 36px;
  background: linear-gradient(170deg, #fde8d0 0%, #f5d5b8 50%, #e8c9a8 100%);
  z-index: 1;
  overflow: hidden;
  animation: headFloat 3.5s ease-in-out infinite;
}
.css-avatar.thinking .head { animation-duration: 1.5s; }
.css-avatar.speaking .head { animation-duration: 0.7s; }
@keyframes headFloat {
  0%,100% { transform: translateY(0) rotate(0deg); }
  25% { transform: translateY(-2px) rotate(-0.5deg); }
  75% { transform: translateY(1px) rotate(0.5deg); }
}

/* 头发 */
.hair {
  position: absolute; top: 0; left: -4px; right: -4px;
  height: 52px;
  background: linear-gradient(180deg, #3d2b1f 0%, #4a3628 60%, #5c4033 100%);
  border-radius: 50px 50px 0 0;
}
.hair-bangs {
  position: absolute; top: 38px; left: 6px; right: 6px;
  height: 16px;
  background: linear-gradient(180deg, #4a3628 0%, #5c4033 60%, transparent 100%);
  border-radius: 0 0 50% 50%;
}

/* 眉毛 */
.eyebrow {
  position: absolute; top: 50px;
  width: 18px; height: 3px;
  background: #3d2b1f;
  border-radius: 3px;
}
.eyebrow.left { left: 18px; }
.eyebrow.right { right: 18px; }
.css-avatar.thinking .eyebrow.left { transform: rotate(-8deg) translateY(-2px); }
.css-avatar.thinking .eyebrow.right { transform: rotate(8deg) translateY(-2px); }
.css-avatar.speaking .eyebrow { animation: browLift 0.6s ease-in-out infinite alternate; }
@keyframes browLift {
  0% { transform: translateY(0); }
  100% { transform: translateY(-3px); }
}

/* 眼睛 */
.eye {
  position: absolute; top: 54px;
  width: 22px; height: 14px;
  background: #f4efe7;
  border-radius: 50%;
  border: 1.5px solid #3d2b1f;
  overflow: hidden;
}
.eye.left { left: 14px; }
.eye.right { right: 14px; }

/* 瞳孔 */
.pupil {
  position: absolute; top: 50%; left: 50%;
  width: 8px; height: 8px;
  background: #1a0f08;
  border-radius: 50%;
  transform: translate(-50%, -50%);
}
.css-avatar.thinking .pupil {
  animation: pupilLook 2s ease-in-out infinite;
}
@keyframes pupilLook {
  0%,100% { transform: translate(-50%, -50%); }
  25% { transform: translate(-70%, -40%); }
  75% { transform: translate(-30%, -40%); }
}

/* 眨眼眼皮 */
.eyelid {
  position: absolute; top: -14px; left: -2px; right: -2px;
  height: 14px;
  background: #fde8d0;
  animation: blinkEyes 4s ease-in-out infinite;
}
@keyframes blinkEyes {
  0%, 44%, 56%, 100% { top: -14px; }
  50% { top: 0; }
}
.css-avatar.speaking .eyelid { animation-duration: 3s; }

/* 鼻子 */
.nose {
  position: absolute; top: 66px; left: 50%;
  width: 10px; height: 6px;
  background: rgba(0,0,0,0.06);
  border-radius: 50%;
  transform: translateX(-50%);
}

/* 嘴巴 */
.mouth-wrap {
  position: absolute; top: 74px; left: 50%;
  transform: translateX(-50%);
}
.mouth-closed {
  width: 16px; height: 4px;
  background: linear-gradient(180deg, #c97a6a 0%, #d48373 100%);
  border-radius: 0 0 10px 10px;
  border-bottom: 2px solid #b06558;
}
.mouth-open {
  width: 14px; height: 8px;
  background: #c43d3d;
  border-radius: 4px 4px 50% 50%;
  position: absolute; top: 1px; left: 1px;
  opacity: 0;
  transform: scaleY(0);
  transition: all 0.1s ease;
}
.css-avatar.speaking .mouth-closed { opacity: 0; }
.css-avatar.speaking .mouth-open {
  opacity: 1;
  animation: mouthAnim 0.2s ease-in-out infinite alternate;
}
@keyframes mouthAnim {
  0% { transform: scaleY(0.4); height: 4px; }
  100% { transform: scaleY(1); height: 9px; }
}

/* 腮红 */
.blush {
  position: absolute; top: 64px;
  width: 14px; height: 8px;
  background: rgba(255,150,150,0.25);
  border-radius: 50%;
}
.blush.left { left: 6px; }
.blush.right { right: 6px; }

/* 身体 */
.body {
  position: absolute; top: 90px; left: 50%;
  width: 70px; height: 36px;
  background: linear-gradient(180deg, #b15311 0%, #4a7de0 100%);
  border-radius: 14px 14px 4px 4px;
  transform: translateX(-50%);
}
.collar {
  position: absolute; top: -2px; left: 50%;
  width: 22px; height: 8px;
  background: #f4efe7;
  border-radius: 2px 2px 4px 4px;
  transform: translateX(-50%);
}

/* 状态标签 */
.state-tag {
  position: absolute; bottom: -8px; left: 50%;
  transform: translateX(-50%);
  padding: 3px 12px;
  border-radius: 14px;
  background: #f4efe7;
  font-size: 11px; color: #b15311;
  white-space: nowrap;
  z-index: 2;
  box-shadow: 0 1px 6px rgba(0,0,0,0.06);
  animation: tagPop 0.3s ease;
}
@keyframes tagPop {
  from { opacity: 0; transform: translateX(-50%) translateY(4px); }
  to { opacity: 1; transform: translateX(-50%) translateY(0); }
}
</style>
