<script setup>
import {computed} from 'vue'
import CompanionAvatar from './CompanionAvatar.vue'
import DialoguePortrait from './DialoguePortrait.vue'
import {reactionMeaning} from '../services/reactionMeaning.js'
const props=defineProps({companion:{type:Object,required:true},kind:{default:'unknown'},expression:{default:'neutral'},paused:Boolean,talking:Boolean,dialogue:Boolean,locale:{default:'en'}})
const sprite=computed(()=>['robot','explorer','creature','naturalist'].includes(props.companion.appearance))
const de=computed(()=>props.locale==='de')
const label=computed(()=>reactionMeaning(props.kind,props.locale))
const face=computed(()=>({robot:{x:6,y:6},explorer:{x:6,y:8},creature:{x:6,y:9}}[props.companion.appearance]||{x:6,y:6}))
</script>
<template>
 <div class="avatar-reaction" :class="['reaction-'+kind,{'reaction-paused':paused,'reaction-with-sprite':sprite,'reaction-dialogue':dialogue}]">
  <div class="reaction-portrait" :class="sprite?'sprite-portrait':'expression-'+expression">
   <DialoguePortrait v-if="dialogue" :appearance="companion.appearance" :talking="talking" :animated="!paused&&talking"/>
   <CompanionAvatar v-else :appearance="companion.appearance" :palette="companion.palette" :animated="sprite&&!paused" :expression="expression" :talking="talking"/>
   <svg v-if="!dialogue && !sprite && expression!=='neutral'" class="reaction-face" viewBox="0 0 16 24" shape-rendering="crispEdges" aria-hidden="true">
    <path v-if="expression==='joyful'" :d="`M${face.x} ${face.y}h1v1h3v-1h1v2h-5z`" fill="#202439"/>
    <path v-else-if="expression==='reflective'" :d="`M${face.x+1} ${face.y+1}h3v1h-3z`" fill="#202439"/>
    <rect v-else :x="face.x+2" :y="face.y" width="1" height="2" fill="#202439"/>
   </svg>
  </div>
  <span v-if="kind!=='unknown'" class="reaction-symbol" role="img" :aria-label="label" :title="label">
   <svg v-if="kind==='aligned'" class="reaction-star" viewBox="0 0 20 20" shape-rendering="crispEdges" aria-hidden="true"><path d="M9 1h2v4h2v2h6v2h-2v2h-2v2h1v5h-3v-2h-2v-1H9v1H7v2H4v-5h1v-2H3V9H1V7h6V5h2z" fill="#ffdb79" stroke="#8b5b20" stroke-width="1"/><path d="M7 8h2v4H7zm4 0h2v4h-2z" fill="#202439"/></svg>
   <svg v-else-if="kind==='different'" viewBox="0 0 20 20" shape-rendering="crispEdges" aria-hidden="true"><path d="M8 18v-7L3 6M9 11l7-7M1 7V2h5M12 2h6v6" fill="none" stroke="#ffc48a" stroke-width="2"/><path d="M7 8h5v5H7z" fill="#c0a2ee"/></svg>
   <svg v-else viewBox="0 0 20 20" shape-rendering="crispEdges" aria-hidden="true"><path d="M1 8h7v4H1zm11 0h7v4h-7zM8 9h4v2H8z" fill="#b9dcff"/></svg>
  </span>
 </div>
</template>
<style scoped>
.avatar-reaction{position:relative;width:112px;flex:0 0 112px;align-self:flex-start}.reaction-portrait{position:relative;width:96px;height:144px;transform-origin:50% 90%}.reaction-portrait :deep(.companion-avatar),.reaction-face{position:absolute;inset:0;width:96px!important;height:144px!important;image-rendering:pixelated}.reaction-symbol{position:absolute;right:0;top:-4px;width:40px;height:40px;filter:drop-shadow(2px 2px 0 #070d1b)}.reaction-symbol svg{width:100%;height:100%;overflow:visible}.reaction-star{animation:reaction-spin 1.2s linear 2}.expression-joyful{animation:reaction-bob 1.4s steps(2,end) infinite}.expression-reflective{animation:reaction-think 3.2s steps(2,end) infinite}.reaction-paused *{animation:none!important}@keyframes reaction-spin{0%{transform:scaleX(1)}25%{transform:scaleX(.2)}50%{transform:scaleX(-1)}75%{transform:scaleX(-.2)}100%{transform:scaleX(1)}}@keyframes reaction-bob{50%{transform:translateY(-4px)}}@keyframes reaction-think{50%{transform:rotate(-3deg)}}@media(prefers-reduced-motion:reduce){.avatar-reaction *{animation:none!important}}@media(max-width:600px){.avatar-reaction{width:80px;flex-basis:80px}.reaction-portrait{width:64px;height:96px}.reaction-portrait :deep(.companion-avatar),.reaction-face{width:64px!important;height:96px!important}.reaction-symbol{width:28px;height:28px}}
.reaction-with-sprite{width:208px;flex-basis:208px}.reaction-with-sprite .reaction-portrait{width:192px;height:192px}.reaction-with-sprite .reaction-portrait :deep(.companion-avatar){width:192px!important;height:192px!important}@media(max-width:600px){.reaction-with-sprite{width:128px;flex-basis:128px}.reaction-with-sprite .reaction-portrait{width:112px;height:112px}.reaction-with-sprite .reaction-portrait :deep(.companion-avatar){width:112px!important;height:112px!important}}
.reaction-dialogue{width:var(--dialogue-portrait-size,144px);max-width:100%;flex:0 0 var(--dialogue-portrait-size,144px);min-width:0}
.reaction-dialogue .reaction-portrait{width:100%;height:auto;aspect-ratio:1;overflow:hidden;border:3px solid #a9bbdf;background:#152742;box-shadow:inset 0 0 0 3px #26385c;box-sizing:border-box;transform:none;animation:none}
.reaction-dialogue .reaction-symbol{width:30px;height:30px;right:4px;top:4px}
@media(max-width:600px){.reaction-dialogue{--dialogue-portrait-size:104px}.reaction-dialogue .reaction-portrait{width:100%;height:auto}}
</style>
