<script setup>
import {onMounted,onUnmounted,ref,watch} from 'vue'
import {drawElbScene} from '../animation/elbScene.js'
const props=defineProps({mode:{default:'walk'},appearance:{default:'robot'},palette:{default:'mint'},value:{default:null},label:{type:String,required:true}})
const canvas=ref(null)
let ctx,media,frameId,start=0,active=true
const settings=()=>({mode:props.mode,appearance:props.appearance,palette:props.palette,value:props.value})
function draw(time=0){if(ctx)drawElbScene(ctx,settings(),time,media?.matches||false)}
function tick(now){if(!active)return;if(!start)start=now;draw(now-start);frameId=requestAnimationFrame(tick)}
function run(){cancelAnimationFrame(frameId);start=0;draw();if(!media.matches&&!document.hidden)frameId=requestAnimationFrame(tick)}
watch(()=>[props.mode,props.appearance,props.palette,props.value],()=>draw())
onMounted(()=>{ctx=canvas.value.getContext('2d');media=window.matchMedia('(prefers-reduced-motion: reduce)');media.addEventListener('change',run);document.addEventListener('visibilitychange',run);run()})
onUnmounted(()=>{active=false;cancelAnimationFrame(frameId);media?.removeEventListener('change',run);document.removeEventListener('visibilitychange',run)})
</script>
<template><canvas ref="canvas" width="320" height="180" class="transport-canvas" role="img" :aria-label="label"></canvas></template>
