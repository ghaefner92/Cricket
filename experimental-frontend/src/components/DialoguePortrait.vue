<script setup>
import {computed,ref,watch,onMounted,onUnmounted} from 'vue'
import {quietMotion} from '../services/appSettings.js'
import robot from '../art/robot-dialogue-v1.png'
import explorer from '../art/explorer-dialogue-v1.png'
import naturalist from '../art/naturalist-dialogue-v1.png'
import creature from '../art/creature-dialogue-v1.png'
const props=defineProps({appearance:{default:'robot'},talking:Boolean,animated:Boolean})
const sheets={robot,explorer,naturalist,creature}
const sheet=computed(()=>sheets[props.appearance]||robot)
const frame=ref(0),reduced=ref(false),hidden=ref(false),quiet=ref(false)
const stopped=computed(()=>!props.animated||reduced.value||hidden.value||quiet.value)
// Four cells: attentive, small mouth opening, second mouth opening, blink.
const crop=computed(()=>`${frame.value*543} 80 543 580`)
let timer,media,mounted=false,step=0
function stop(){clearTimeout(timer);timer=undefined}
function tick(){if(!mounted||stopped.value)return;const cycle=props.talking?[0,1,2,1,0,3]:[0,3];const delay=props.talking?(frame.value===3?130:260):(frame.value===3?130:3200);timer=setTimeout(()=>{step=(step+1)%cycle.length;frame.value=cycle[step];tick()},delay)}
function reset(){stop();step=0;frame.value=0;tick()}
function motion(){reduced.value=!!media?.matches}
function visibility(){hidden.value=document.hidden}
function settings(){quiet.value=quietMotion()}
watch([stopped,()=>props.talking,()=>props.appearance],reset)
onMounted(()=>{mounted=true;media=window.matchMedia?.('(prefers-reduced-motion: reduce)');motion();visibility();settings();media?.addEventListener?.('change',motion);document.addEventListener('visibilitychange',visibility);window.addEventListener('cricket-settings-change',settings);reset()})
onUnmounted(()=>{mounted=false;stop();media?.removeEventListener?.('change',motion);document.removeEventListener('visibilitychange',visibility);window.removeEventListener('cricket-settings-change',settings)})
</script>
<template>
 <svg class="dialogue-portrait" viewBox="0 0 543 580" aria-hidden="true" focusable="false" :data-dialogue-frame="frame" :data-dialogue-paused="stopped" :data-dialogue-character="appearance">
  <svg width="543" height="580" :viewBox="crop" overflow="hidden"><image :href="sheet" width="2172" height="724" preserveAspectRatio="none"/></svg>
 </svg>
</template>
<style scoped>
.dialogue-portrait{display:block;width:100%;height:100%;image-rendering:pixelated;overflow:hidden}
</style>
