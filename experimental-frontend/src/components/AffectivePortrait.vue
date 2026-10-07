<script setup>
import {computed} from 'vue'
import CompanionAvatar from './CompanionAvatar.vue'
import {companions,palettes} from '../art/companions.js'
const props=defineProps({appearance:{default:'robot'},palette:{default:'mint'},value:{default:null}})
const colors=computed(()=>palettes.mint)
const mouthY=computed(()=>props.appearance==='robot'?6:props.appearance==='explorer'?8:9)
const pixels=computed(()=>(companions[props.appearance]||companions.robot).flatMap((row,y)=>[...row].flatMap((cell,x)=>cell==='.'?[]:[{x,y,color:colors.value[Number(cell)]}])))
const mouth=computed(()=>{
 const y=mouthY.value
 if(props.value===null)return `M7 ${y}h2`
 if(props.value<0)return `M6 ${y+1}v-1h4v1`
 if(props.value>0)return `M6 ${y-1}v1h4v-1`
 return `M6 ${y}h4`
})
</script>
<template><CompanionAvatar v-if="['robot','explorer','creature','naturalist'].includes(appearance)" class="affective-portrait" :appearance="appearance" :palette="palette" :expression="value>0?'joyful':value<0?'reflective':'neutral'"/><svg v-else viewBox="0 0 16 24" class="affective-portrait" aria-hidden="true" focusable="false" shape-rendering="crispEdges"><rect v-for="pixel in pixels" :key="`${pixel.x}-${pixel.y}`" :x="pixel.x" :y="pixel.y" width="1" height="1" :fill="pixel.color"/><rect x="6" :y="mouthY-1" width="4" height="3" :fill="colors[2]"/><path :d="mouth" :stroke="colors[1]" fill="none" stroke-width="1"/><template v-if="value!==null && Math.abs(value)===3"><rect x="4" :y="mouthY-2" width="2" height="1" fill="#efaa9b"/><rect x="10" :y="mouthY-2" width="2" height="1" fill="#efaa9b"/></template></svg></template>
