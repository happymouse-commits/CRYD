<template>
  <div class="vt-container" :class="'vt-' + state">
    <!-- 身体底层 -->
    <img :src="parts.body" class="vt-part vt-body" />
    <!-- 躯干 -->
    <img :src="parts.torso" class="vt-part vt-torso" />
    <!-- 左臂 -->
    <img :src="parts.leftArm" class="vt-part vt-left-arm" />
    <!-- 右臂 -->
    <img :src="parts.rightArm" class="vt-part vt-right-arm" />
    <!-- 头部 -->
    <img :src="parts.head" class="vt-part vt-head" />
  </div>
</template>

<script setup>
import bodySrc from '@/assets/teacher/body.png'
import torsoSrc from '@/assets/teacher/torso.png'
import headSrc from '@/assets/teacher/head.png'
import leftArmSrc from '@/assets/teacher/left_arm.png'
import rightArmSrc from '@/assets/teacher/right_arm.png'

defineProps({ state: { type: String, default: 'idle' } })

const parts = { body: bodySrc, torso: torsoSrc, head: headSrc, leftArm: leftArmSrc, rightArm: rightArmSrc }
</script>

<style scoped>
.vt-container {
  position: relative;
  width: 180px; height: 280px;
  overflow: hidden; border-radius: 24px;
  box-shadow: 0 4px 24px rgba(91,141,239,0.12);
}

.vt-part { position: absolute; display: block; }

.vt-body  { width: 100%; height: 100%; object-fit: cover; object-position: top center; }
.vt-torso { width: 100%; height: 100%; object-fit: cover; object-position: top center; }

/* 头部：定位在身体顶部偏上 */
.vt-head {
  width: 36%; top: 3%; left: 32%;
  transform-origin: 50% 85%;        /* 旋转轴在脖子 */
  transition: transform 0.4s ease;
}

/* 左臂 */
.vt-left-arm {
  width: 28%; top: 20%; left: 0;
  transform-origin: 85% 15%;        /* 旋转轴在肩膀 */
  transition: transform 0.5s ease;
}

/* 右臂 */
.vt-right-arm {
  width: 28%; top: 20%; right: 0;
  transform-origin: 15% 15%;        /* 旋转轴在肩膀 */
  transition: transform 0.5s ease;
}

/* ===== idle 呼吸 + 微晃 ===== */
.vt-idle .vt-torso { animation: breathe 4s ease-in-out infinite; }
.vt-idle .vt-head  { animation: headIdle 5s ease-in-out infinite; }
.vt-idle .vt-left-arm  { animation: armIdle 6s ease-in-out infinite; }
.vt-idle .vt-right-arm { animation: armIdle 6s ease-in-out 0.5s infinite; }

/* ===== speaking 手势 + 点头 ===== */
.vt-speaking .vt-torso      { animation: breathe 1.2s ease-in-out infinite; }
.vt-speaking .vt-head       { animation: headSpeak 0.8s ease-in-out infinite; }
.vt-speaking .vt-right-arm  { animation: armGesture 1.6s ease-in-out infinite; }
.vt-speaking .vt-left-arm   { animation: armIdle 3s ease-in-out infinite; }

/* ===== listening 歪头 + 前倾 ===== */
.vt-listening .vt-head      { animation: headTilt 6s ease-in-out infinite; }
.vt-listening .vt-torso     { animation: listenLean 6s ease-in-out infinite; }
.vt-listening .vt-left-arm  { animation: armRest 6s ease-in-out infinite; }
.vt-listening .vt-right-arm { animation: armRest 6s ease-in-out 0.3s infinite; }

/* ===== thinking 托下巴 + 歪头 ===== */
.vt-thinking .vt-head       { transform: rotate(-4deg) translateY(2px); animation: headThink 3s ease-in-out infinite; }
.vt-thinking .vt-right-arm  { transform: rotate(18deg); }  /* 扶下巴 */
.vt-thinking .vt-torso      { animation: breathe 2.5s ease-in-out infinite; }

/* ========== 关键帧 ========== */

@keyframes breathe {
  0%, 100% { transform: scale(1); }
  50%      { transform: scale(1.008); }
}

@keyframes headIdle {
  0%, 100% { transform: rotate(0deg); }
  30%      { transform: rotate(1.5deg); }
  70%      { transform: rotate(-1deg); }
}

@keyframes headSpeak {
  0%, 100% { transform: rotate(0deg); }
  25%      { transform: rotate(3deg) translateY(-1px); }
  75%      { transform: rotate(-2deg) translateY(1px); }
}

@keyframes headTilt {
  0%, 100% { transform: rotate(0deg); }
  40%      { transform: rotate(6deg) translateY(-2px); }
  80%      { transform: rotate(4deg); }
}

@keyframes headThink {
  0%, 100% { transform: rotate(-4deg) translateY(2px); }
  50%      { transform: rotate(-7deg) translateY(1px); }
}

@keyframes armIdle {
  0%, 100% { transform: rotate(0deg); }
  50%      { transform: rotate(1.5deg); }
}

@keyframes armGesture {
  0%, 100% { transform: rotate(0deg); }
  30%      { transform: rotate(-12deg) translateY(-3px); }
  60%      { transform: rotate(-5deg) translateY(-1px); }
}

@keyframes armRest {
  0%, 100% { transform: rotate(1deg); }
  50%      { transform: rotate(-1deg); }
}

@keyframes listenLean {
  0%, 100% { transform: scale(1) translateX(0); }
  50%      { transform: scale(1.005) translateX(-3px); }
}
</style>
