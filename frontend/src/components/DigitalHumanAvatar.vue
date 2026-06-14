<template>
  <div class="dh-avatar" :class="state">
    <!-- 外层旋转光环 -->
    <div class="orbit"></div>
    <!-- 呼吸光晕 -->
    <div class="aura"></div>
    <!-- 人物照片 -->
    <div class="photo-ring">
      <img :src="avatarSrc" alt="数字人" class="photo" />
      <!-- speaking: 下唇高亮模拟嘴动 -->
      <div class="mouth-glow" v-if="state === 'speaking'"></div>
    </div>
    <!-- 状态标记 -->
    <div class="badge" v-if="label">{{ label }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({
  state: { type: String, default: 'idle' },
  avatarSrc: { type: String, default: '/avatar.png' }
})
const label = computed(() => ({
  speaking: '讲解中', listening: '倾听中', thinking: '思考中', idle: ''
}[props.state] || ''))
</script>

<style scoped>
.dh-avatar {
  position: relative;
  display: flex; flex-direction: column; align-items: center;
  width: 130px; height: 140px;
}

/* === 光环 === */
.orbit {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%,-50%);
  width: 132px; height: 132px; border-radius: 50%;
  border: 2px dashed rgba(177,83,17,0.35);
  animation: orbitRun 10s linear infinite;
}
.thinking .orbit { border-color: rgba(139,92,246,0.5); animation-duration: 3.5s; }
.speaking .orbit { border-color: rgba(74,124,78,0.55); animation-duration: 2s; }
.listening .orbit { border-color: rgba(201,121,48,0.5); animation-duration: 5s; }
@keyframes orbitRun { to { transform: translate(-50%,-50%) rotate(360deg); } }

.aura {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%,-50%);
  width: 120px; height: 120px; border-radius: 50%;
  background: radial-gradient(circle, rgba(177,83,17,0.2) 0%, transparent 65%);
  animation: auraBreathe 2.5s ease-in-out infinite;
}
.thinking .aura { background: radial-gradient(circle, rgba(139,92,246,0.25) 0%, transparent 65%); animation-duration: 1.8s; }
.speaking .aura { background: radial-gradient(circle, rgba(74,124,78,0.25) 0%, transparent 65%); animation-duration: 0.9s; }
.listening .aura { background: radial-gradient(circle, rgba(201,121,48,0.25) 0%, transparent 65%); animation-duration: 2s; }
@keyframes auraBreathe {
  0%,100% { opacity: 0.5; transform: translate(-50%,-50%) scale(0.9); }
  50% { opacity: 1; transform: translate(-50%,-50%) scale(1.12); }
}

/* === 照片圈 === */
.photo-ring {
  position: relative; z-index: 1;
  width: 120px; height: 120px; border-radius: 50%;
  overflow: hidden;
  border: 3px solid rgba(255,255,255,0.9);
  box-shadow: 0 3px 18px rgba(177,83,17,0.18);
}
/* 四种状态动画 */
.idle .photo-ring { animation: sway 4s ease-in-out infinite; }
.thinking .photo-ring { animation: think 2s ease-in-out infinite; box-shadow: 0 3px 22px rgba(139,92,246,0.28); }
.speaking .photo-ring { animation: bob 0.7s ease-in-out infinite; box-shadow: 0 3px 22px rgba(74,124,78,0.28); }
.listening .photo-ring { animation: lean 2.5s ease-in-out infinite; box-shadow: 0 3px 22px rgba(201,121,48,0.28); }

@keyframes sway {
  0%,100% { transform: translateY(0) scale(1); }
  30% { transform: translateY(-3px) scale(1.04); }
  60% { transform: translateY(2px) scale(1.03); }
}
@keyframes think {
  0%,100% { transform: rotate(-2deg) scale(1.04); }
  50% { transform: rotate(2deg) scale(1.07); }
}
@keyframes bob {
  0%,100% { transform: translateY(0) scale(1.04); }
  50% { transform: translateY(-4px) scale(1.07); }
}
@keyframes lean {
  0%,100% { transform: translateY(0); }
  50% { transform: translateY(-3px) scale(1.03); }
}

.photo {
  width: 100%; height: 100%; object-fit: cover; object-position: center 20%;
}

/* === 嘴部高亮（模拟说话） === */
.mouth-glow {
  position: absolute; bottom: 14%; left: 50%;
  width: 22px; height: 6px; border-radius: 6px;
  background: rgba(255,255,255,0.7);
  transform: translateX(-50%);
  box-shadow: 0 0 10px rgba(74,124,78,0.5), 0 0 20px rgba(74,124,78,0.2);
  animation: mouthFlash 0.18s ease-in-out infinite alternate;
}
@keyframes mouthFlash {
  0% { transform: translateX(-50%) scaleY(0.3); opacity: 0.3; }
  100% { transform: translateX(-50%) scaleY(1.3); opacity: 0.9; }
}

/* === 状态标记 === */
.badge {
  position: absolute; bottom: -6px;
  padding: 3px 12px; border-radius: 14px;
  background: #f4efe7; box-shadow: 0 1px 6px rgba(0,0,0,0.06);
  font-size: 11px; color: #b15311; white-space: nowrap;
  z-index: 2; animation: popIn 0.3s ease;
}
@keyframes popIn { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }
</style>
