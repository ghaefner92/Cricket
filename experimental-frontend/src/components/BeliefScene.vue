<script setup>
import {quietMotion} from '../services/appSettings.js'
import {onMounted,onUnmounted,ref,watch} from 'vue'
import {drawElbScene} from '../animation/elbScene.js'
const props=defineProps({mode:{required:true},appearance:{default:'robot'},palette:{default:'mint'},label:{required:true}})
const canvas=ref(null)
let ctx,media,id,start=0,active=true
function draw(time=0){if(ctx)drawElbScene(ctx,{mode:props.mode,appearance:props.appearance,palette:props.palette,value:null,background:'citadel'},time,media?.matches||false)}
function tick(now){if(!active)return;if(!start)start=now;draw(now-start);id=requestAnimationFrame(tick)}
function run(){cancelAnimationFrame(id);start=0;draw();if(!media.matches&&!quietMotion()&&!document.hidden)id=requestAnimationFrame(tick)}
watch(()=>[props.mode,props.appearance,props.palette],()=>draw())
onMounted(()=>{window.addEventListener('cricket-settings-change',run);window.addEventListener('cricket-transport-sprites-ready',run);ctx=canvas.value.getContext('2d');media=matchMedia('(prefers-reduced-motion: reduce)');media.addEventListener('change',run);document.addEventListener('visibilitychange',run);run()})
onUnmounted(()=>{window.removeEventListener('cricket-settings-change',run);window.removeEventListener('cricket-transport-sprites-ready',run);active=false;cancelAnimationFrame(id);media?.removeEventListener('change',run);document.removeEventListener('visibilitychange',run)})
</script>
<template><canvas ref="canvas" width="320" height="180" class="belief-canvas" role="img" :aria-label="label"/></template>
