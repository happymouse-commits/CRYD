<template>
  <div ref="containerRef" class="mesh-container">
    <canvas ref="canvasRef" class="mesh-canvas" />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as THREE from 'three'

import bodySrc from '../assets/teacher/body.png'
import torsoSrc from '../assets/teacher/torso.png'
import headSrc from '../assets/teacher/head.png'
import leftArmSrc from '../assets/teacher/left_arm.png'
import rightArmSrc from '../assets/teacher/right_arm.png'

const props = defineProps({
  state: { type: String, default: 'idle' },
  width:  { type: Number, default: 200 },
  height: { type: Number, default: 300 },
})

const containerRef = ref(null)
const canvasRef = ref(null)

// ── Three.js 对象 ──
let renderer, scene, camera, textureLoader
let meshes = {}          // { body, torso, head, leftArm, rightArm }
let basePositions = {}   // 原始顶点位置（用于重置）
let animTime = 0
let animId = 0
let currentState = 'idle'
let transition = 0       // 状态过渡插值 [0..1]

// ── 动画参数 ──
const BREATH = { speed: 1.8, amplitude: 0.012 }
const HEAD_TILT = { maxAngle: 0.12, speed: 0.7 }
const HEAD_NOD = { amplitude: 0.015, speed: 2.5 }
const ARM_WAVE = { amplitude: 0.06, speed: 2.0 }
const ARM_THINK = { angle: 0.35 }

// ══════════════════════════════════════
//  初始化
// ══════════════════════════════════════

function init() {
  const canvas = canvasRef.value
  const W = props.width, H = props.height

  // Renderer
  renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true })
  renderer.setSize(W, H, false)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))

  // 正交相机：左上角原点
  camera = new THREE.OrthographicCamera(0, W, H, 0, -1, 10)
  camera.position.z = 5

  scene = new THREE.Scene()
  textureLoader = new THREE.TextureLoader()

  // 加载 5 层
  const layers = [
    { key: 'body',      src: bodySrc,      segW: 4,  segH: 6  },
    { key: 'torso',     src: torsoSrc,     segW: 6,  segH: 8  },
    { key: 'head',      src: headSrc,      segW: 6,  segH: 8  },
    { key: 'leftArm',   src: leftArmSrc,   segW: 8,  segH: 4  },
    { key: 'rightArm',  src: rightArmSrc,  segW: 8,  segH: 4  },
  ]

  layers.forEach(({ key, src, segW, segH }) => {
    const tex = textureLoader.load(src)
    tex.minFilter = THREE.LinearFilter
    tex.magFilter = THREE.LinearFilter
    tex.colorSpace = THREE.SRGBColorSpace
    tex.generateMipmaps = false

    const geom = new THREE.PlaneGeometry(W, H, segW, segH)
    // 保存原始顶点
    basePositions[key] = new Float32Array(geom.attributes.position.array)

    const mat = new THREE.MeshBasicMaterial({
      map: tex,
      transparent: true,
      depthWrite: false,
      depthTest: false,
    })

    const mesh = new THREE.Mesh(geom, mat)
    mesh.renderOrder = layers.indexOf({ key, src, segW, segH }) // 按层序渲染
    scene.add(mesh)
    meshes[key] = mesh
  })
}

// ══════════════════════════════════════
//  顶点变形函数
// ══════════════════════════════════════

function getPositions(key) {
  return meshes[key].geometry.attributes.position.array
}

function resetPositions(key) {
  const arr = getPositions(key)
  const base = basePositions[key]
  for (let i = 0; i < arr.length; i++) arr[i] = base[i]
  meshes[key].geometry.attributes.position.needsUpdate = true
}

// 呼吸：X 轴鼓胀
function applyBreathing(t, amplitude = BREATH.amplitude) {
  const key = 'torso'
  const arr = getPositions(key)
  const base = basePositions[key]
  const W = props.width
  const factor = Math.sin(t * BREATH.speed) * amplitude
  for (let i = 0; i < arr.length; i += 3) {
    const bx = base[i]
    const by = base[i + 1]
    // 胸部区域 (上方 25%~55%)
    const chestY = by / props.height
    const chestWeight = smoothstep(chestY, 0.15, 0.25) * (1 - smoothstep(chestY, 0.45, 0.55))
    const dx = (bx - W / 2) * factor * chestWeight
    arr[i] = bx + dx
    arr[i + 1] = by
    arr[i + 2] = base[i + 2]
  }
  meshes[key].geometry.attributes.position.needsUpdate = true
}

// 歪头：绕脖子旋转
function applyHeadTilt(t, maxAngle = HEAD_TILT.maxAngle) {
  const key = 'head'
  const arr = getPositions(key)
  const base = basePositions[key]
  const angle = Math.sin(t * HEAD_TILT.speed) * maxAngle
  const cosA = Math.cos(angle), sinA = Math.sin(angle)
  // 脖子位置（头部下沿）
  const pivotX = props.width * 0.5
  const pivotY = props.height * 0.14
  for (let i = 0; i < arr.length; i += 3) {
    const bx = base[i], by = base[i + 1]
    const dx = bx - pivotX, dy = by - pivotY
    arr[i]     = pivotX + dx * cosA - dy * sinA
    arr[i + 1] = pivotY + dx * sinA + dy * cosA
    arr[i + 2] = base[i + 2]
  }
  meshes[key].geometry.attributes.position.needsUpdate = true
}

// 点头
function applyHeadNod(t, amplitude = HEAD_NOD.amplitude) {
  const key = 'head'
  const arr = getPositions(key)
  const base = basePositions[key]
  const dy = Math.sin(t * HEAD_NOD.speed) * amplitude * props.height
  const H = props.height
  for (let i = 0; i < arr.length; i += 3) {
    const by = base[i + 1]
    // 越靠近头顶，位移越大
    const weight = 1 - clamp(by / H, 0, 1)  // 顶=1, 底=0
    arr[i]     = base[i]
    arr[i + 1] = by + dy * weight * weight
    arr[i + 2] = base[i + 2]
  }
  meshes[key].geometry.attributes.position.needsUpdate = true
}

// 手臂波浪
function applyArmWave(key, t, amplitude = ARM_WAVE.amplitude) {
  const arr = getPositions(key)
  const base = basePositions[key]
  const H = props.height
  for (let i = 0; i < arr.length; i += 3) {
    const bx = base[i], by = base[i + 1]
    // 波浪沿 Y 轴传播
    const wave = Math.sin(by / H * 3 + t * ARM_WAVE.speed) * amplitude * props.width
    arr[i]     = bx + wave
    arr[i + 1] = by
    arr[i + 2] = base[i + 2]
  }
  meshes[key].geometry.attributes.position.needsUpdate = true
}

// 手臂旋转（思考托下巴）
function applyArmRotate(key, angle) {
  const arr = getPositions(key)
  const base = basePositions[key]
  const isRight = key === 'rightArm'
  // 肩膀位置
  const pivotX = isRight ? props.width * 0.78 : props.width * 0.22
  const pivotY = props.height * 0.22
  const cosA = Math.cos(angle), sinA = Math.sin(angle)
  for (let i = 0; i < arr.length; i += 3) {
    const bx = base[i], by = base[i + 1]
    const dx = bx - pivotX, dy = by - pivotY
    arr[i]     = pivotX + dx * cosA - dy * sinA
    arr[i + 1] = pivotY + dx * sinA + dy * cosA
    arr[i + 2] = base[i + 2]
  }
  meshes[key].geometry.attributes.position.needsUpdate = true
}

// ══════════════════════════════════════
//  状态动画
// ══════════════════════════════════════

function animateIdle(t) {
  applyBreathing(t, BREATH.amplitude)
  applyHeadTilt(t, HEAD_TILT.maxAngle * 0.5)
}

function animateSpeaking(t) {
  applyBreathing(t, BREATH.amplitude * 1.6)    // 说话时呼吸加重
  applyHeadNod(t, HEAD_NOD.amplitude)           // 点头
  applyArmWave('rightArm', t, ARM_WAVE.amplitude)  // 右手打手势
}

function animateListening(t) {
  applyBreathing(t, BREATH.amplitude * 0.4)     // 轻微呼吸
  applyHeadTilt(t, HEAD_TILT.maxAngle * 1.3)    // 歪头更明显
}

function animateThinking(t) {
  applyBreathing(t, BREATH.amplitude * 0.3)     // 几乎不动
  applyHeadTilt(t - 1, HEAD_TILT.maxAngle * 0.9) // 歪头
  applyArmRotate('rightArm', ARM_THINK.angle)    // 手托下巴
}

// ══════════════════════════════════════
//  渲染循环
// ══════════════════════════════════════

function loop() {
  animTime += 0.016  // ~60fps delta

  // 过渡插值
  if (transition < 1) {
    transition = Math.min(1, transition + 0.04)
  }

  // 重置所有顶点
  Object.keys(meshes).forEach(k => resetPositions(k))

  // 应用当前状态动画
  const t = animTime
  const fn = { idle: animateIdle, speaking: animateSpeaking, listening: animateListening, thinking: animateThinking }[currentState]
  if (fn) fn(t)

  renderer.render(scene, camera)
  animId = requestAnimationFrame(loop)
}

// ══════════════════════════════════════
//  状态切换
// ══════════════════════════════════════

watch(() => props.state, (s) => {
  if (s !== currentState) {
    currentState = s
    transition = 0
  }
})

// ══════════════════════════════════════
//  生命周期
// ══════════════════════════════════════

onMounted(() => {
  init()
  loop()
})

onUnmounted(() => {
  cancelAnimationFrame(animId)
  renderer?.dispose()
})

// ══════════════════════════════════════
//  工具函数
// ══════════════════════════════════════

function clamp(v, lo, hi) { return v < lo ? lo : v > hi ? hi : v }
function smoothstep(x, a, b) {
  const t = clamp((x - a) / (b - a), 0, 1)
  return t * t * (3 - 2 * t)
}
</script>

<style scoped>
.mesh-container {
  display: inline-block;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 2px 16px rgba(91, 141, 239, 0.10);
}
.mesh-canvas { display: block; }
</style>
