<template>
  <div class="character-scene">
    <!-- Purple -->
    <div ref="purpleRef" class="char-body" :style="purpleBodyStyle">
      <div class="eyes-row" :style="purpleEyesStyle">
        <div class="eyeball" :style="{ width: '18px', height: purpleBlinking ? '2px' : '18px', backgroundColor: 'white' }">
          <div v-if="!purpleBlinking" class="pupil" :style="pupilInner(purpleForceLook, 'purple', 5, 7)" />
        </div>
        <div class="eyeball" :style="{ width: '18px', height: purpleBlinking ? '2px' : '18px', backgroundColor: 'white' }">
          <div v-if="!purpleBlinking" class="pupil" :style="pupilInner(purpleForceLook, 'purple', 5, 7)" />
        </div>
      </div>
    </div>

    <!-- Black -->
    <div ref="blackRef" class="char-body" :style="blackBodyStyle">
      <div class="eyes-row" :style="blackEyesStyle">
        <div class="eyeball" :style="{ width: '16px', height: blackBlinking ? '2px' : '16px', backgroundColor: 'white' }">
          <div v-if="!blackBlinking" class="pupil" :style="pupilInner(blackForceLook, 'black', 4, 6)" />
        </div>
        <div class="eyeball" :style="{ width: '16px', height: blackBlinking ? '2px' : '16px', backgroundColor: 'white' }">
          <div v-if="!blackBlinking" class="pupil" :style="pupilInner(blackForceLook, 'black', 4, 6)" />
        </div>
      </div>
    </div>

    <!-- Orange -->
    <div ref="orangeRef" class="char-body" :style="orangeBodyStyle">
      <div class="dot-eyes-row" :style="orangeEyesStyle">
        <div class="pupil-dot" :style="dotPupilStyle('orange', orangeForceLook)" />
        <div class="pupil-dot" :style="dotPupilStyle('orange', orangeForceLook)" />
      </div>
    </div>

    <!-- Yellow -->
    <div ref="yellowRef" class="char-body" :style="yellowBodyStyle">
      <div class="dot-eyes-row" :style="yellowEyesStyle">
        <div class="pupil-dot" :style="dotPupilStyle('yellow', yellowForceLook)" />
        <div class="pupil-dot" :style="dotPupilStyle('yellow', yellowForceLook)" />
      </div>
      <div class="mouth-line" :style="mouthStyle" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  isTyping: { type: Boolean, default: false },
  passwordLength: { type: Number, default: 0 },
  showPassword: { type: Boolean, default: false }
})

const mouseX = ref(0)
const mouseY = ref(0)
const purpleBlinking = ref(false)
const blackBlinking = ref(false)
const isLookingAtEachOther = ref(false)
const isPurplePeeking = ref(false)

const purpleRef = ref(null)
const blackRef = ref(null)
const yellowRef = ref(null)
const orangeRef = ref(null)

const passwordHidden = computed(() => props.passwordLength > 0 && !props.showPassword)

function calcCharPos(el) {
  if (!el) return { faceX: 0, faceY: 0, bodySkew: 0 }
  const rc = el.getBoundingClientRect()
  const dx = mouseX.value - (rc.left + rc.width / 2)
  const dy = mouseY.value - (rc.top + rc.height / 3)
  return {
    faceX: Math.max(-15, Math.min(15, dx / 20)),
    faceY: Math.max(-10, Math.min(10, dy / 30)),
    bodySkew: Math.max(-6, Math.min(6, -dx / 120))
  }
}

const purplePos = computed(() => calcCharPos(purpleRef.value))
const blackPos = computed(() => calcCharPos(blackRef.value))
const yellowPos = computed(() => calcCharPos(yellowRef.value))
const orangePos = computed(() => calcCharPos(orangeRef.value))

const purpleForceLook = computed(() => {
  if (props.passwordLength > 0 && props.showPassword) return { x: isPurplePeeking.value ? 4 : -4, y: isPurplePeeking.value ? 5 : -4 }
  if (isLookingAtEachOther.value) return { x: 3, y: 4 }
  return { x: undefined, y: undefined }
})
const blackForceLook = computed(() => {
  if (props.passwordLength > 0 && props.showPassword) return { x: -4, y: -4 }
  if (isLookingAtEachOther.value) return { x: 0, y: -4 }
  return { x: undefined, y: undefined }
})
const orangeForceLook = computed(() => {
  if (props.passwordLength > 0 && props.showPassword) return { x: -5, y: -4 }
  return { x: undefined, y: undefined }
})
const yellowForceLook = computed(() => {
  if (props.passwordLength > 0 && props.showPassword) return { x: -5, y: -4 }
  return { x: undefined, y: undefined }
})

function getRef(name) {
  const map = { purple: purpleRef, black: blackRef, orange: orangeRef, yellow: yellowRef }
  return map[name].value
}

function calcOffset(name, cap) {
  const el = getRef(name)
  if (!el) return { x: 0, y: 0 }
  const rc = el.getBoundingClientRect()
  const dx = mouseX.value - (rc.left + rc.width / 2)
  const dy = mouseY.value - (rc.top + rc.height / 3)
  const dist = Math.min(Math.sqrt(dx * dx + dy * dy), cap)
  const angle = Math.atan2(dy, dx)
  return { x: Math.cos(angle) * dist, y: Math.sin(angle) * dist }
}

function pupilInner(forceLook, name, cap, size) {
  let tx, ty
  if (forceLook.x !== undefined) { tx = forceLook.x; ty = forceLook.y }
  else { const o = calcOffset(name, cap); tx = o.x; ty = o.y }
  return {
    width: `${size}px`, height: `${size}px`,
    backgroundColor: '#2D2D2D', borderRadius: '50%',
    transform: `translate(${tx}px, ${ty}px)`,
    transition: 'transform 0.1s ease-out'
  }
}

function dotPupilStyle(name, forceLook) {
  let tx, ty
  if (forceLook.x !== undefined) { tx = forceLook.x; ty = forceLook.y }
  else { const o = calcOffset(name, 5); tx = o.x; ty = o.y }
  return {
    width: '12px', height: '12px', backgroundColor: '#2D2D2D', borderRadius: '50%',
    transform: `translate(${tx}px, ${ty}px)`,
    transition: 'transform 0.1s ease-out'
  }
}

// ---- Body styles ----
const purpleBodyStyle = computed(() => {
  const p = purplePos.value
  let skew = p.bodySkew || 0
  if (props.isTyping || passwordHidden.value) skew -= 12
  const tall = props.isTyping || passwordHidden.value
  return {
    left: tall ? '110px' : '70px', width: '180px',
    height: tall ? '440px' : '400px',
    backgroundColor: '#6C3FF5', borderRadius: '10px 10px 0 0',
    zIndex: 1, transform: `skewX(${skew}deg)`, transformOrigin: 'bottom center'
  }
})
const purpleEyesStyle = computed(() => {
  const p = purplePos.value
  if (props.passwordLength > 0 && props.showPassword) return { left: '20px', top: '35px' }
  if (isLookingAtEachOther.value) return { left: '55px', top: '65px' }
  return { left: `${45 + p.faceX}px`, top: `${40 + p.faceY}px` }
})

const blackBodyStyle = computed(() => {
  const p = blackPos.value
  let skew = p.bodySkew || 0
  if (isLookingAtEachOther.value) skew = skew * 1.5 + 10
  else if (props.isTyping || passwordHidden.value) skew *= 1.5
  return {
    left: isLookingAtEachOther.value ? '260px' : '240px', width: '120px', height: '310px',
    backgroundColor: '#2D2D2D', borderRadius: '8px 8px 0 0',
    zIndex: 2, transform: `skewX(${skew}deg)`, transformOrigin: 'bottom center'
  }
})
const blackEyesStyle = computed(() => {
  const p = blackPos.value
  if (props.passwordLength > 0 && props.showPassword) return { left: '10px', top: '28px' }
  if (isLookingAtEachOther.value) return { left: '32px', top: '12px' }
  return { left: `${26 + p.faceX}px`, top: `${32 + p.faceY}px` }
})

const orangeBodyStyle = computed(() => {
  const p = orangePos.value
  const skew = props.passwordLength > 0 && props.showPassword ? 0 : (p.bodySkew || 0)
  return {
    left: '0px', width: '240px', height: '200px',
    backgroundColor: '#FF9B6B', borderRadius: '120px 120px 0 0',
    zIndex: 3, transform: `skewX(${skew}deg)`, transformOrigin: 'bottom center'
  }
})
const orangeEyesStyle = computed(() => {
  const p = orangePos.value
  if (props.passwordLength > 0 && props.showPassword) return { left: '50px', top: '85px' }
  return { left: `${82 + (p.faceX || 0)}px`, top: `${90 + (p.faceY || 0)}px` }
})

const yellowBodyStyle = computed(() => {
  const p = yellowPos.value
  const skew = props.passwordLength > 0 && props.showPassword ? 0 : (p.bodySkew || 0)
  return {
    left: '310px', width: '140px', height: '230px',
    backgroundColor: '#E8D754', borderRadius: '70px 70px 0 0',
    zIndex: 4, transform: `skewX(${skew}deg)`, transformOrigin: 'bottom center'
  }
})
const yellowEyesStyle = computed(() => {
  const p = yellowPos.value
  if (props.passwordLength > 0 && props.showPassword) return { left: '20px', top: '35px' }
  return { left: `${52 + (p.faceX || 0)}px`, top: `${40 + (p.faceY || 0)}px` }
})
const mouthStyle = computed(() => {
  const p = yellowPos.value
  if (props.passwordLength > 0 && props.showPassword) return { left: '10px', top: '88px' }
  return { left: `${40 + (p.faceX || 0)}px`, top: `${88 + (p.faceY || 0)}px` }
})

// ---- Timers ----
let pt = null, bt = null, peekT = null

function schedBlink(r, s) { const t = () => { r.value = true; setTimeout(() => { r.value = false; schedBlink(r, s) }, 150) }; return setTimeout(t, Math.random() * 4000 + 3000) }

watch(() => props.isTyping, (v) => {
  if (v) { isLookingAtEachOther.value = true; setTimeout(() => { isLookingAtEachOther.value = false }, 800) }
  else isLookingAtEachOther.value = false
})

function schedPeek() {
  peekT = setTimeout(() => {
    isPurplePeeking.value = true
    setTimeout(() => { isPurplePeeking.value = false; if (props.passwordLength > 0 && props.showPassword) schedPeek() }, 800)
  }, Math.random() * 3000 + 2000)
}
watch([() => props.passwordLength, () => props.showPassword], () => {
  if (props.passwordLength > 0 && props.showPassword) schedPeek()
  else { isPurplePeeking.value = false; clearTimeout(peekT) }
})

function mm(e) { mouseX.value = e.clientX; mouseY.value = e.clientY }

onMounted(() => {
  window.addEventListener('mousemove', mm)
  pt = schedBlink(purpleBlinking)
  bt = schedBlink(blackBlinking)
})
onUnmounted(() => {
  window.removeEventListener('mousemove', mm)
  ;[pt, bt, peekT].forEach(clearTimeout)
})
</script>

<style scoped>
.character-scene { position: relative; width: 550px; height: 400px; }
.char-body { position: absolute; bottom: 0; transition: all 0.7s ease-in-out; }
.eyes-row { position: absolute; display: flex; gap: 32px; transition: all 0.7s ease-in-out; }
.eyeball { border-radius: 50%; display: flex; align-items: center; justify-content: center; overflow: hidden; transition: all 0.15s; }
.dot-eyes-row { position: absolute; display: flex; gap: 32px; transition: all 0.2s ease-out; }
.mouth-line { position: absolute; width: 80px; height: 4px; background-color: #2D2D2D; border-radius: 9999px; }
</style>
