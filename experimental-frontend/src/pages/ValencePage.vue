<script setup>
import {computed,nextTick,ref,watch} from 'vue'
import TransportScene from '../components/TransportScene.vue'
import PixelHeart from '../components/PixelHeart.vue'
import AffectivePortrait from '../components/AffectivePortrait.vue'
import {readCompanion} from '../stores/companion.js'
import {MODES,isValence,readValences,writeValences,allAnswered,stepValence} from '../stores/valences.js'
import {valenceMessages} from '../i18n/valences.js'
import '../styles/valences.css'
const props=defineProps({locale:{default:'en'}})
const emit=defineEmits(['locale-change','back','continue'])
let storage
try{storage=window.localStorage}catch{storage={getItem(){return null},setItem(){throw Error('Storage unavailable')}}}
const companion=readCompanion(storage),draft=ref(readValences(storage))
const copy=computed(()=>valenceMessages[props.locale]||valenceMessages.en)
const mode=computed(()=>MODES[draft.value.index])
const value=computed(()=>draft.value.answers[mode.value])
const label=computed(()=>isValence(value.value)?copy.value.labels[value.value+3]:copy.value.choose)
const phrase=computed(()=>isValence(value.value)?copy.value.phrases[mode.value][value.value+3]:copy.value.prompt)
const count=computed(()=>MODES.filter(mode=>isValence(draft.value.answers[mode])).length)
const saved=ref(false),error=ref(false),draftSaved=ref(false),question=ref(null)
watch(draft,()=>{saved.value=false;try{writeValences(storage,draft.value);error.value=false;draftSaved.value=true}catch{error.value=true;draftSaved.value=false}},{deep:true,flush:'sync'})
// IMIQ valence reactions v2
function step(direction){select(stepValence(value.value,direction))}
function select(next){if(isValence(next))draft.value={...draft.value,answers:{...draft.value.answers,[mode.value]:next}}}
async function navigate(index){draft.value={...draft.value,index};await nextTick();question.value?.focus()}
function back(){if(draft.value.index===0)emit('back');else navigate(draft.value.index-1)}
function next(){if(!isValence(value.value))return;if(draft.value.index<3){navigate(draft.value.index+1);return}if(!allAnswered(draft.value))return;try{writeValences(storage,draft.value,true);saved.value=true;error.value=false;emit('continue')}catch{error.value=true}}
</script>
<template>
 <main class="valence-page" :lang="locale">
  <header class="valence-topbar"><button type="button" class="nes-btn" @click="back">← {{ copy.back }}</button><nav :aria-label="copy.language"><button v-for="language in ['en','de']" :key="language" type="button" class="nes-btn" :class="{'is-primary':locale===language}" :aria-pressed="locale===language" @click="emit('locale-change',language)">{{language.toUpperCase()}}</button></nav></header>
  <header class="valence-heading"><p class="valence-kicker">{{copy.step}}</p><h1 tabindex="-1">{{copy.title}}</h1><p class="valence-copy valence-progress">{{draft.index+1}} / 4 · {{count}} / 4 {{copy.answered}}</p></header>
  <nav class="mode-tabs" :aria-label="copy.review"><button v-for="(item,index) in MODES" :key="item" type="button" class="nes-btn" :class="{'is-primary':index===draft.index}" :aria-pressed="index===draft.index" @click="navigate(index)"><span>{{copy.modes[item]}}</span><span v-if="isValence(draft.answers[item])" class="mode-check" aria-hidden="true">✓</span></button></nav>
  <section class="nes-container valence-card">
   <h2 ref="question" tabindex="-1" class="mode-question">{{copy.questions[mode]}}</h2>
   <div class="nes-container scene-border"><TransportScene :mode="mode" :appearance="companion.appearance" :palette="companion.palette" :value="value" :label="`${copy.modes[mode]}. ${copy.scene}`"/></div>
   <div class="companion-dialogue"><AffectivePortrait :appearance="companion.appearance" :palette="companion.palette" :value="value"/><div class="nes-balloon from-left"><p class="dialogue-name">{{companion.name}}</p><p class="valence-copy" role="status" aria-atomic="true">{{phrase}}</p></div></div>
   <section class="heart-control" :aria-label="copy.slider">
    <p class="valence-copy control-instruction">{{copy.instruction}}</p>
    <div class="heart-range">
     <button type="button" class="nes-btn heart-extreme" :class="{'is-selected':value===-3}" :aria-label="copy.moreNegative" :disabled="value===-3" @click="step(-1)"><PixelHeart :value="-3"/></button>
     <div class="range-track"><input class="affect-slider" type="range" min="-3" max="3" step="1" :value="value===null?0:value" :aria-label="copy.slider" :aria-valuetext="label" @input="select(Number($event.target.value))" @keydown.home.prevent="select(-3)" @keydown.end.prevent="select(3)"><div class="range-ticks" aria-hidden="true"><span v-for="n in 7" :key="n">·</span></div></div>
     <button type="button" class="nes-btn heart-extreme" :class="{'is-selected':value===3}" :aria-label="copy.morePositive" :disabled="value===3" @click="step(1)"><PixelHeart :value="3"/></button>
    </div>
    <div class="range-labels"><span>{{copy.labels[0]}}</span><span>{{copy.labels[6]}}</span></div>
    <button type="button" class="nes-btn neutral-button" :class="{'is-selected':value===0}" :aria-pressed="value===0" @click="select(0)">{{copy.neutral}}</button>
    <div class="selected-feeling"><PixelHeart :value="value"/><p class="valence-copy"><span v-if="value!==null" class="valence-selection-label">{{copy.selection}}</span>{{label}}</p></div>
   </section>
  </section>
  <footer class="valence-actions"><p v-if="error" class="valence-copy valence-error" role="alert">{{copy.storage}}</p><button type="button" class="nes-btn is-primary valence-next" :disabled="!isValence(value)||(draft.index===3&&!allAnswered(draft))" @click="next">{{draft.index===3?(locale==='de'?'Weiter: Verbindungen':'Next: connections'):copy.next}} →</button><p class="valence-copy valence-draft">{{draftSaved&&!error?copy.draft:''}}</p></footer>
  <section v-if="saved" class="nes-container valence-saved" role="status"><h2>{{copy.saved}}</h2><p class="valence-copy">{{copy.savedBody}}</p><ul><li v-for="item in MODES" :key="item"><span>{{copy.modes[item]}}</span><strong>{{copy.labels[draft.answers[item]+3]}}</strong></li></ul></section>
 </main>
</template>
