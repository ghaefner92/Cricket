<script setup>
import {onMounted,onUnmounted,ref,watch} from 'vue'
import {drawToleranceScene} from '../animation/toleranceScene.js'
const props=defineProps({condition:{type:String,required:true},appearance:{default:'robot'},palette:{default:'mint'},label:{type:String,required:true},rating:{default:null}})
const canvas=ref(null);let ctx,media,id,start=0
function draw(time=0){if(ctx)drawToleranceScene(ctx,{condition:props.condition,appearance:props.appearance,palette:props.palette,rating:props.rating},time,media?.matches)}
function tick(now){if(!start)start=now;draw(now-start);id=requestAnimationFrame(tick)}
function run(){cancelAnimationFrame(id);draw();if(!media.matches&&!document.hidden)id=requestAnimationFrame(tick)}
watch(()=>[props.condition,props.appearance,props.palette,props.rating],()=>{start=0;run()})
onMounted(()=>{ctx=canvas.value.getContext('2d');media=matchMedia('(prefers-reduced-motion: reduce)');media.addEventListener('change',run);document.addEventListener('visibilitychange',run);run()})
onUnmounted(()=>{cancelAnimationFrame(id);media?.removeEventListener('change',run);document.removeEventListener('visibilitychange',run)})
</script>
<template><canvas ref="canvas" width="320" height="180" class="tolerance-canvas" role="img" :aria-label="label"></canvas></template>
