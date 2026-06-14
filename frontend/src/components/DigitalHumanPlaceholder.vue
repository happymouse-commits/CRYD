<template>
  <div class="dh-placeholder" :class="state">
    <div class="avatar-ring">
      <img :src="avatarSrc" class="avatar-img" alt="数字人" />
      <div class="state-dot" :class="state" />
    </div>
    <span class="state-label">{{ labelMap[state] || '待命中' }}</span>
  </div>
</template>

<script setup>
defineProps({
  state:      { type: String, default: 'idle' },
  avatarSrc:  { type: String, default: '/tutor-body.png' },
})

const labelMap = {
  idle:      '待命中',
  speaking:  '讲解中',
  listening: '聆听中',
  thinking:  '思考中',
}
</script>

<style scoped>
.dh-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  user-select: none;
}
.avatar-ring {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  padding: 3px;
  background: linear-gradient(135deg, #e8edf5, #ebe2d7);
  box-shadow: 0 1px 8px rgba(177,83,17,0.08);
}
.avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}
.state-dot {
  position: absolute;
  bottom: 2px;
  right: 2px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid #f4efe7;
  background: #c0ccda;
  transition: background 0.35s;
}
.state-dot.idle      { background: #c0ccda; }
.state-dot.speaking   { background: #4a7c4e; animation: pulse-dot 0.7s infinite; }
.state-dot.listening  { background: #b15311; animation: pulse-dot 0.7s infinite; }
.state-dot.thinking   { background: #c97930; animation: pulse-dot 1.0s infinite; }

@keyframes pulse-dot {
  0%, 100% { transform: scale(1); opacity: 1; }
  50%      { transform: scale(1.35); opacity: 0.6; }
}

.state-label {
  font-size: 11px;
  color: #8b9bb5;
  letter-spacing: 0.5px;
}
</style>
