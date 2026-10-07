<script setup>
import {quietMotion} from '../services/appSettings.js'
import {onMounted,onUnmounted,ref,watch} from 'vue'
import {drawElbScene} from '../animation/elbScene.js'
const props=defineProps({mode:String,appearance:String,palette:String,available:{default:null},label:String})
const canvas=ref(null);let ctx,media,frame,start=0,active=true
function draw(t=0){if(ctx)drawElbScene(ctx,{mode:props.mode,appearance:props.appearance,palette:props.palette,value:null,background:'hbf',sceneTime:t},props.available===true?t:0,media?.matches||false)}
function tick(now){if(!active)return;if(!start)start=now;draw(now-start);frame=requestAnimationFrame(tick)}
function run(){cancelAnimationFrame(frame);start=0;draw();if(!media.matches&&!quietMotion()&&!document.hidden)frame=requestAnimationFrame(tick)}
watch(()=>[props.mode,props.appearance,props.palette,props.available],()=>draw())
onMounted(()=>{window.addEventListener('cricket-settings-change',run);window.addEventListener('cricket-transport-sprites-ready',run);ctx=canvas.value.getContext('2d');media=matchMedia('(prefers-reduced-motion: reduce)');media.addEventListener('change',run);document.addEventListener('visibilitychange',run);run()})
onUnmounted(()=>{window.removeEventListener('cricket-settings-change',run);window.removeEventListener('cricket-transport-sprites-ready',run);active=false;cancelAnimationFrame(frame);media?.removeEventListener('change',run);document.removeEventListener('visibilitychange',run)})
</script>
<template><canvas ref="canvas" width="320" height="180" class="availability-canvas" role="img" :aria-label="label"/></template>
