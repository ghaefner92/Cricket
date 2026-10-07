<script setup>
import {quietMotion} from '../services/appSettings.js'
import {onMounted,onUnmounted,ref,watch} from 'vue'
import {drawGoalScene,drawGoalIcon} from '../animation/goalScene.js'
const props=defineProps({goal:{type:String,required:true},appearance:{default:'robot'},palette:{default:'mint'},label:{type:String,required:true},icon:{type:Boolean,default:false},pulse:{default:0}})
const canvas=ref(null)
let ctx,media,id,start=0,visible=true
function draw(time=0){if(!ctx)return;if(props.icon){ctx.clearRect(0,0,40,36);drawGoalIcon(ctx,props.goal,4,5,3,0,true)}else drawGoalScene(ctx,{goal:props.goal,appearance:props.appearance,palette:props.palette,pulse:props.pulse},time,media?.matches)}
function tick(now){if(!start)start=now;draw(now-start);id=requestAnimationFrame(tick)}
function run(){cancelAnimationFrame(id);draw();if(!props.icon&&!media.matches&&!quietMotion()&&visible)id=requestAnimationFrame(tick)}
function visibility(){visible=!document.hidden;run()}
watch(()=>[props.goal,props.appearance,props.palette,props.pulse],()=>draw(performance.now()-start))
onMounted(()=>{window.addEventListener('cricket-settings-change',run);window.addEventListener('cricket-robot-canvas-ready',run);window.addEventListener('cricket-transport-sprites-ready',run);ctx=canvas.value.getContext('2d');media=matchMedia('(prefers-reduced-motion: reduce)');media.addEventListener('change',run);document.addEventListener('visibilitychange',visibility);visibility()})
onUnmounted(()=>{window.removeEventListener('cricket-settings-change',run);window.removeEventListener('cricket-robot-canvas-ready',run);window.removeEventListener('cricket-transport-sprites-ready',run);cancelAnimationFrame(id);media?.removeEventListener('change',run);document.removeEventListener('visibilitychange',visibility)})
</script>
<template><canvas ref="canvas" :width="icon?40:320" :height="icon?36:152" :class="icon?'goal-pixel-icon':'goal-scene-canvas'" role="img" :aria-label="label"></canvas></template>
