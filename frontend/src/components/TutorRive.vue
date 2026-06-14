<template>
  <div class="tutor-rive-container" :class="state">
    <canvas ref="riveCanvas" class="rive-canvas" />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { Rive } from '@rive-app/canvas'

const props = defineProps({
  state: { type: String, default: 'idle' }  // idle | speaking | listening | thinking
})

const riveCanvas = ref(null)
let riveInstance = null

onMounted(async () => {
  if (!riveCanvas.value) return
  riveInstance = new Rive({
    canvas: riveCanvas.value,
    src: '/tutor.riv',
    stateMachines: 'StateMachine',
    autoplay: true,
    artboard: 'Tutor',
    onLoad: () => {
      riveInstance.resizeToCanvas()
      setAnimState(props.state)
    },
    onLoadError: (err) => {
      console.warn('Rive 待加载: /tutor.riv 文件尚未放入 public/', err)
    }
  })
})

onUnmounted(() => {
  riveInstance?.cleanup()
})

watch(() => props.state, (s) => setAnimState(s))

function setAnimState(name) {
  if (!riveInstance) return
  try {
    const inputs = riveInstance.stateMachineInputs('StateMachine')
    inputs?.forEach(i => {
      if (i.name === 'state') i.value = name
    })
  } catch (e) { /* 状态切换未配置 */ }
}
</script>

<style scoped>
.tutor-rive-container {
  width: 100%; height: 100%; min-height: 240px;
  transition: transform 0.3s;
}
.rive-canvas { width: 100%; height: 100%; }
</style>
