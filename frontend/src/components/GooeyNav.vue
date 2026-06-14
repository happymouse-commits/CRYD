<template>
  <div class="gooey-nav-container">
    <nav>
      <ul>
        <li
          v-for="(item, index) in items"
          :key="index"
          :class="{ active: activeIndex === index }"
          @click="handleClick(item, index)"
        >
          <router-link :to="item.href">{{ item.label }}</router-link>
        </li>
      </ul>
    </nav>
    <span class="effect filter" ref="filterRef"></span>
    <span class="effect text" ref="textRef"></span>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'

const props = defineProps({
  items: {
    type: Array,
    default: () => []
  },
  initialActiveIndex: {
    type: Number,
    default: 0
  }
})

const route = useRoute()
const activeIndex = ref(props.initialActiveIndex)
const filterRef = ref(null)
const textRef = ref(null)

// 根据当前路由自动匹配激活项
function syncFromRoute() {
  const idx = props.items.findIndex(item => route.path.startsWith(item.href))
  if (idx !== -1) activeIndex.value = idx
}

// 监听路由变化自动切换
watch(() => route.path, () => {
  syncFromRoute()
})

function handleClick(item, index) {
  activeIndex.value = index
  nextTick(() => {
    createParticles()
  })
}

function createParticles() {
  if (!filterRef.value) return
  for (let i = 0; i < 12; i++) {
    const p = document.createElement('span')
    p.className = 'particle'
    p.style.setProperty('--x', `${Math.random() * 120 - 60}px`)
    p.style.setProperty('--y', `${Math.random() * 80 - 40}px`)
    filterRef.value.appendChild(p)
    setTimeout(() => {
      p.remove()
    }, 600)
  }
}

onMounted(() => {
  syncFromRoute()
})
</script>

<style scoped>
.gooey-nav-container {
  position: relative;
}
nav ul {
  display: flex;
  gap: 8px;
  list-style: none;
  margin: 0;
  padding: 0;
}
li {
  position: relative;
  border-radius: 999px;
  overflow: visible;
}
li a {
  display: block;
  padding: 8px 20px;
  text-decoration: none;
  font-size: 14px;
  color: #6a6054;
  position: relative;
  z-index: 2;
  transition: .25s;
}
li.active a {
  color: #b15311;
  font-weight: 600;
}
li.active {
  background: #ead6c2;
  box-shadow: 0 8px 20px rgba(177, 83, 17, .12);
}
.effect {
  position: absolute;
  pointer-events: none;
}
.filter {
  inset: 0;
  z-index: 1;
}
.particle {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #b15311;
  animation: pop .6s ease forwards;
}
@keyframes pop {
  0% {
    transform: translate(0, 0) scale(.3);
    opacity: 1;
  }
  100% {
    transform: translate(var(--x), var(--y)) scale(0);
    opacity: 0;
  }
}
</style>
