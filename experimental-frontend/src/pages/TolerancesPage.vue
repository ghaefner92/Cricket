<script setup>
import {computed,nextTick,ref} from 'vue'
import ToleranceScene from '../components/ToleranceScene.vue'
import {readCompanion} from '../stores/companion.js'
import {TOLERANCES,readTolerances,writeTolerances,validRating,allTolerancesAnswered,stepTolerance} from '../stores/tolerances.js'
import {toleranceMessages} from '../i18n/tolerances.js'
import '../styles/tolerances.css'
const props=defineProps({locale:{default:'en'}}),emit=defineEmits(['locale-change','back','continue'])
let storage;try{storage=window.localStorage}catch{storage={getItem(){return null},setItem(){throw Error('storage')}}}
const companion=readCompanion(storage),state=ref(readTolerances(storage)),error=ref(false),draftSaved=ref(false),heading=ref(null)
const copy=computed(()=>toleranceMessages[props.locale]||toleranceMessages.en),key=computed(()=>TOLERANCES[state.value.index]),condition=computed(()=>copy.value.conditions[key.value]),rating=computed(()=>state.value.answers[key.value]),label=computed(()=>validRating(rating.value)?copy.value.labels[rating.value-1]:copy.value.unanswered)
function persist(confirm=false){try{state.value=writeTolerances(storage,state.value,confirm);error.value=false;draftSaved.value=true;return true}catch{error.value=true;draftSaved.value=false;return false}}
function select(value){if(!validRating(value))return;state.value={...state.value,confirmed:false,answers:{...state.value.answers,[key.value]:value}};persist()}
async function navigate(delta){state.value={...state.value,index:state.value.index+delta};persist(state.value.confirmed);await nextTick();heading.value?.focus()}
function back(){if(state.value.index>0)navigate(-1);else emit('back')}
function next(){if(!validRating(rating.value))return;if(state.value.index<TOLERANCES.length-1){if(persist(state.value.confirmed))navigate(1)}else if(allTolerancesAnswered(state.value)&&persist(true))emit('continue')}
</script>
<template>
 <main class="tolerances-page" :lang="locale">
  <header class="tolerance-topbar"><button class="nes-btn" type="button" @click="back">← {{copy.back}}</button><nav :aria-label="locale==='de'?'Sprache wählen':'Choose language'"><button v-for="language in ['en','de']" :key="language" type="button" class="nes-btn" :class="{'is-primary':locale===language}" :aria-pressed="locale===language" @click="emit('locale-change',language)">{{language.toUpperCase()}}</button></nav></header>
  <p class="tolerance-kicker">{{copy.step}}</p><h1 tabindex="-1">{{copy.title}}</h1><p class="tolerance-copy">{{copy.intro}}</p>
  <section class="nes-container tolerance-card"><p class="tolerance-kicker">{{state.index+1}} / {{TOLERANCES.length}} · {{companion.name}}</p><h2 ref="heading" tabindex="-1">{{condition.title}}</h2><ToleranceScene :condition="key" :rating="rating" :appearance="companion.appearance" :palette="companion.palette" :label="condition.scene"/><p class="tolerance-copy">{{condition.detail}}</p><h3 id="tolerance-question">{{copy.question}}</h3>
   <div class="tolerance-controls"><button class="nes-btn" type="button" :disabled="rating===1" :aria-label="copy.minus" @click="select(stepTolerance(rating,-1))">−</button><input type="range" min="1" max="7" step="1" :value="rating===null?4:rating" aria-labelledby="tolerance-question" :aria-valuetext="label" @input="select(Number($event.target.value))"><button class="nes-btn" type="button" :disabled="rating===7" :aria-label="copy.plus" @click="select(stepTolerance(rating,1))">+</button></div>
   <div class="tolerance-extremes"><span>{{copy.low}}</span><span>{{copy.high}}</span></div><p class="tolerance-choice" role="status">{{label}}</p><p class="tolerance-copy tolerance-dialogue">{{copy.dialogue(rating)}}</p><button class="nes-btn tolerance-middle" type="button" @click="select(4)">{{copy.neutral}}</button>
  </section>
  <section v-if="error" class="nes-container tolerance-error" role="alert"><p class="tolerance-copy">{{copy.storage}}</p><button class="nes-btn" type="button" @click="persist(state.confirmed)">{{copy.retry}}</button></section>
  <button class="nes-btn is-primary tolerance-next" type="button" :disabled="!validRating(rating)||state.index===TOLERANCES.length-1&&!allTolerancesAnswered(state)" @click="next">{{state.index===TOLERANCES.length-1?copy.save:copy.next}} →</button><p class="tolerance-copy tolerance-draft">{{draftSaved?copy.draft:''}}</p>
 </main>
</template>
