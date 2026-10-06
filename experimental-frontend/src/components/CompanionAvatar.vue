<script setup>
import { computed } from 'vue'
import { companions, palettes } from '../art/companions.js'
const props = defineProps({ appearance: { type: String, default: 'robot' }, palette: { type: String, default: 'mint' }, animated: { type: Boolean, default: false } })
const pixels = computed(() => {
  const colors = palettes[props.palette] || palettes.mint
  return (companions[props.appearance] || companions.robot).flatMap((row, y) => [...row].flatMap((color, x) => color === '.' ? [] : [{ x, y, color: colors[Number(color)] }]))
})
</script>
<template>
  <svg class="companion-avatar" :class="{ 'companion-avatar--animated': animated }" viewBox="0 0 16 24" aria-hidden="true" focusable="false" shape-rendering="crispEdges">
    <rect v-for="pixel in pixels" :key="`${pixel.x}-${pixel.y}`" :x="pixel.x" :y="pixel.y" width="1" height="1" :fill="pixel.color" />
  </svg>
</template>
