<script setup>
import {computed} from 'vue'
const props=defineProps({value:{default:null}})
const rows=['................','..1111....1111..','.122221..122221.','1222222112222221','1222222222222221','1222222222222221','.12222222222221.','..122222222221..','...1222222221...','....12222221....','.....122221.....','......1221......','.......11.......','................']
const pixels=computed(()=>rows.flatMap((row,y)=>[...row].flatMap((cell,x)=>cell==='.'?[]:[{x,y,edge:cell==='1'}])))
const fill=computed(()=>props.value===null?'#263b64':props.value<0?'#c68ab2':props.value===0?'#a8b4ce':['#e6ab9a','#f29b91','#ff857c'][props.value-1])
</script>
<template><svg viewBox="0 0 16 14" class="pixel-heart" aria-hidden="true" focusable="false" shape-rendering="crispEdges"><rect v-for="pixel in pixels" :key="`${pixel.x}-${pixel.y}`" :x="pixel.x" :y="pixel.y" width="1" height="1" :fill="pixel.edge?'#f2e8e2':fill"/><path v-if="value!==null && value<0" d="M8 2h2v3H8v2h2v2H8v3H6V9h2V7H6V5h2z" fill="#263b64"/><rect v-if="value>0" x="3" y="3" width="2" height="1" fill="#fff1d6"/></svg></template>
