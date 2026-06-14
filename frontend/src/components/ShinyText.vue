<template>
  <span
    class="shiny-text"
    :style="{
      '--shine-speed': speed + 's',
      '--shine-color-1': color1,
      '--shine-color-2': color2,
      '--shine-color-3': color3,
      fontSize: size + 'px',
    }"
  >
    <slot />
  </span>
</template>

<script setup>
defineProps({
  speed: { type: Number, default: 3 },           // 动画速度(秒)
  size: { type: Number, default: 20 },            // 字号
  color1: { type: String, default: '#d4af37' },   // 暗金
  color2: { type: String, default: '#fff8dc' },   // 亮光
  color3: { type: String, default: '#d4af37' },   // 暗金
})
</script>

<style scoped>
.shiny-text {
  display: inline-block;
  font-weight: 700;
  background: linear-gradient(110deg,
    var(--shine-color-1, #d4af37) 25%,
    var(--shine-color-2, #fff8dc) 42%,
    var(--shine-color-3, #d4af37) 58%
  );
  background-size: 200% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent; /* fallback */
  animation: shiny-slide var(--shine-speed, 3s) ease-in-out infinite;
}

@keyframes shiny-slide {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
