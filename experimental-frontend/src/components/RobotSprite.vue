<script setup>
import {computed,ref,watch,onMounted,onUnmounted} from 'vue'
import {quietMotion} from '../services/appSettings.js'
import robotSheet from '../art/robot-mint-sheet-v1.png'
import explorerSheet from '../art/explorer-mint-sheet-v1.png'
import naturalistSheet from '../art/naturalist-sheet-v1.png'
import creatureSheet from '../art/creature-mint-sheet-v1.png'
const props=defineProps({animated:Boolean,appearance:{default:'robot'},expression:{default:'neutral'},talking:Boolean})
const sheet=computed(()=>props.appearance==='creature'?creatureSheet:props.appearance==='naturalist'?naturalistSheet:props.appearance==='explorer'?explorerSheet:robotSheet)
const sheetWidth=computed(()=>['explorer','naturalist','creature'].includes(props.appearance)?1278:256)
const sheetHeight=computed(()=>['explorer','naturalist','creature'].includes(props.appearance)?1230:256)
const crop=computed(()=>{const rows=props.appearance==='creature'?[[0,322],[322,620],[620,916],[916,1230]]:props.appearance==='naturalist'?[[0,312],[312,607],[607,904],[904,1230]]:[[0,315],[315,610],[610,907],[907,1230]];return ['explorer','naturalist','creature'].includes(props.appearance)?`${frame.value*319.5} ${rows[row.value][0]} 319.5 ${rows[row.value][1]-rows[row.value][0]}`:`${frame.value*64} ${row.value*64} 64 64`})
const frame=ref(0),reduced=ref(false),hidden=ref(false),appQuiet=ref(false)
const state=computed(()=>props.talking?'speaking':props.expression==='joyful'?'joyful':['reflective','curious'].includes(props.expression)?'reflective':'idle')
const row=computed(()=>({idle:0,speaking:1,joyful:2,reflective:3}[state.value]))
const stopped=computed(()=>!props.animated||reduced.value||hidden.value||appQuiet.value)
let timer,media,mounted=false
const delays={idle:[2400,120,110,180],speaking:[360,360,420,360],joyful:[1300,360,240,500],reflective:[1700,700,850,1200]}
function stop(){clearTimeout(timer);timer=undefined}
function tick(){if(stopped.value||!mounted)return;timer=setTimeout(()=>{frame.value=(frame.value+1)%4;tick()},(props.appearance==='naturalist'&&state.value==='idle'?260:delays[state.value][frame.value]))}
function reset(){stop();frame.value=0;tick()}
function motion(){reduced.value=!!media?.matches}
function settingsChanged(){appQuiet.value=quietMotion()}
function visibility(){hidden.value=document.hidden}
watch([state,stopped,()=>props.appearance],reset)
onMounted(()=>{mounted=true;media=window.matchMedia?.('(prefers-reduced-motion: reduce)');motion();visibility();settingsChanged();window.addEventListener('cricket-settings-change',settingsChanged);media?.addEventListener?.('change',motion);document.addEventListener('visibilitychange',visibility);reset()})
onUnmounted(()=>{mounted=false;stop();window.removeEventListener('cricket-settings-change',settingsChanged);media?.removeEventListener?.('change',motion);document.removeEventListener('visibilitychange',visibility)})
</script>
<template>
 <svg class="companion-avatar companion-avatar--sprite" viewBox="0 0 64 64" aria-hidden="true" focusable="false" :data-sprite-state="state" :data-sprite-frame="frame" :data-sprite-paused="stopped">
  <svg x="0" y="0" width="64" height="64" :viewBox="crop" overflow="hidden">
   <image :href="sheet" :width="sheetWidth" :height="sheetHeight" preserveAspectRatio="none"/>
  </svg>
 </svg>
</template>
<style scoped>
.companion-avatar--sprite{image-rendering:pixelated;overflow:hidden}
</style>
