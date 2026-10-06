<script setup>
import {computed,nextTick,onMounted,onUnmounted,ref} from 'vue'
import BeliefScene from '../components/BeliefScene.vue'
import CompanionAvatar from '../components/CompanionAvatar.vue'
import {readCompanion} from '../stores/companion.js'
import {loadPlan} from '../stores/weeklyGoals.js'
import {readValences} from '../stores/valences.js'
import {beliefMessages} from '../i18n/beliefs.js'
import {buildInputs,validStart,validAnswers,isRating,stepRating,readSession,writeSession,completePayload,postJSON,PROFILE_KEY} from '../services/adaptiveBeliefs.js'
import {passportSource} from '../services/passport.js'
import '../styles/beliefs.css'
const props=defineProps({locale:{default:'en'}})
const emit=defineEmits(['locale-change','back','goals','continue'])
let storage
try{storage=window.localStorage}catch{storage={getItem(){return null},setItem(){throw Error('storage')}}}
const companion=readCompanion(storage),copy=computed(()=>beliefMessages[props.locale]||beliefMessages.en)
const start=ref(null),answers=ref({}),index=ref(0),busy=ref(false),error=ref(''),saved=ref(false),draftSaved=ref(false),heading=ref(null)
let inputs,controller,alive=true
const question=computed(()=>start.value?.questions[index.value])
const mode=computed(()=>question.value?copy.value.modes[question.value.hotco_mode]:'')
const need=computed(()=>question.value?copy.value.needs[question.value.model_need]:'')
const rating=computed(()=>question.value&&isRating(answers.value[question.value.cell_id])?answers.value[question.value.cell_id]:null)
const level=computed(()=>rating.value===null?0:rating.value-4)
const label=computed(()=>rating.value===null?copy.value.unanswered:copy.value.labels[rating.value-1])
const answered=computed(()=>Object.values(answers.value).filter(isRating).length)
const edgePath=computed(()=>{const x=15+(rating.value===null?3:rating.value-1)*70/6;return `M50 0 C50 24 ${x} 25 ${x} 50 C${x} 75 50 76 50 100`})
function persist(){try{writeSession(storage,inputs,start.value,answers.value,index.value);draftSaved.value=true;error.value=''}catch{error.value='storage';draftSaved.value=false}}
async function prepare(force=false){
 if(busy.value)return
 controller?.abort();controller=new AbortController();busy.value=true;error.value='';saved.value=false
 try{
  let agent
  try{agent=storage.getItem('imiq.experimental.agent-id.v1');if(!agent){agent=`exp-${crypto.randomUUID()}`;storage.setItem('imiq.experimental.agent-id.v1',agent)}}catch{throw Error('storage')}
  inputs=buildInputs(agent,loadPlan(storage),readValences(storage))
  const cached=readSession(storage,inputs)
  if(cached&&!force){start.value=cached.start;answers.value=cached.answers;index.value=cached.index;try{passportSource(storage,{requireTolerances:false});saved.value=true}catch{};return}
  const response=await postJSON('/api/dyconet/adaptive-passport/start',inputs.payload,controller.signal)
  if(!alive)return
  if(!validStart(response)||response.agent_id!==agent)throw Error('Invalid server response')
  start.value=response;answers.value=cached&&validAnswers(response,cached.answers)?cached.answers:{};index.value=cached?cached.index:0;persist()
 }catch(failure){if(alive){const reason=failure.message;error.value=reason==='goals'?'goals':reason==='valences'?'valences':reason==='storage'?'storage':'network'}}
 finally{if(alive)busy.value=false}
}
function select(value){if(!isRating(value)||busy.value||!question.value)return;answers.value={...answers.value,[question.value.cell_id]:value};saved.value=false;persist()}
async function navigate(nextIndex){index.value=nextIndex;saved.value=false;persist();await nextTick();heading.value?.focus()}
function back(){if(busy.value)return;if(index.value>0&&start.value)navigate(index.value-1);else emit('back')}
async function next(){
 if(!isRating(rating.value)||busy.value)return
 if(index.value<3){navigate(index.value+1);return}
 busy.value=true;error.value='';controller?.abort();controller=new AbortController()
 try{
  const latest=buildInputs(inputs.payload.agent_id,loadPlan(storage),readValences(storage))
  if(latest.fingerprint!==inputs.fingerprint){error.value='goals';return}
  const result=await postJSON('/api/dyconet/adaptive-passport/complete',completePayload(start.value,answers.value),controller.signal)
  if(!alive)return
  if(result?.schema_version!==start.value.schema_version||result.stage!=='complete'||result.agent_id!==start.value.agent_id||!result.profile?.beliefs||result.ready_for_hotco!==true)throw Error('Invalid completion')
  try{storage.setItem(PROFILE_KEY,JSON.stringify({version:1,fingerprint:inputs.fingerprint,weekStart:inputs.weekStart,goalPoints:inputs.goalPoints,needsSource:inputs.needsSource,conversion:inputs.conversion,completedAt:new Date().toISOString(),response:result}));persist();if(error.value==='storage')return}catch{error.value='storage';return}
  saved.value=true
 }catch{if(alive)error.value='network'}finally{if(alive)busy.value=false}
}
onMounted(()=>prepare())
onUnmounted(()=>{alive=false;controller?.abort()})
</script>
<template>
 <main class="belief-page" :lang="locale">
  <header class="belief-topbar"><button type="button" class="nes-btn" :disabled="busy" @click="back">← {{copy.back}}</button><nav :aria-label="copy.language"><button v-for="language in ['en','de']" :key="language" type="button" class="nes-btn" :class="{'is-primary':locale===language}" :aria-pressed="locale===language" @click="emit('locale-change',language)">{{language.toUpperCase()}}</button></nav></header>
  <header class="belief-heading"><p class="belief-kicker">{{copy.step}}</p><h1 tabindex="-1">{{copy.title}}</h1><p v-if="start" class="belief-copy">{{index+1}} / 4 · {{answered}} / 4 {{copy.answered}}</p></header>
  <p v-if="busy" class="belief-copy belief-status" role="status">{{copy.loading}}</p>
  <section v-if="error" class="nes-container belief-error" role="alert">
   <p class="belief-copy">{{error==='goals'?copy.goalsMissing:error==='valences'?copy.valencesMissing:error==='storage'?copy.storage:copy.error}}</p>
   <button v-if="error==='goals'" type="button" class="nes-btn" @click="emit('goals')">{{copy.returnGoals}}</button>
   <button v-else-if="error==='valences'" type="button" class="nes-btn" @click="emit('back')">{{copy.returnValences}}</button>
   <button v-else-if="!busy" type="button" class="nes-btn" @click="error==='storage'&&start?persist():prepare(true)">{{copy.retry}}</button>
  </section>
  <section v-if="question" class="nes-container belief-card">
   <h2 ref="heading" tabindex="-1" class="belief-question">{{copy.question(mode,need)}}</h2>
   <div class="nes-container belief-scene"><BeliefScene :mode="question.hotco_mode" :appearance="companion.appearance" :palette="companion.palette" :label="`${mode}. ${copy.scene}`"/></div>
   <p class="belief-mode">{{mode}}</p>
   <div class="connection-stage" :class="{'edge-negative':level<0,'edge-positive':level>0,'edge-neutral':level===0}">
    <svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true"><path :d="edgePath" class="connection-line" :stroke-width="rating===null?1:1+Math.abs(level)" vector-effect="non-scaling-stroke"/><path v-if="level>0" :d="edgePath" class="connection-flow" vector-effect="non-scaling-stroke"/></svg>
    <div class="connection-controls"><button type="button" class="nes-btn" :disabled="rating===1||busy" :aria-label="copy.negative" @click="select(stepRating(rating,-1))">−</button><input type="range" min="1" max="7" step="1" :value="rating===null?4:rating" :disabled="busy" :aria-label="copy.slider" :aria-valuetext="label" @input="select(Number($event.target.value))"><button type="button" class="nes-btn" :disabled="rating===7||busy" :aria-label="copy.positive" @click="select(stepRating(rating,1))">+</button></div>
   </div>
   <div class="nes-container goal-node"><span class="goal-emblem" aria-hidden="true">◆</span><h3>{{need}}</h3></div>
   <div class="connection-labels"><span>{{copy.labels[0]}}</span><span>{{copy.labels[6]}}</span></div>
   <p class="belief-copy connection-choice" role="status">{{label}}</p>
   <button type="button" class="nes-btn belief-neutral" :disabled="busy" @click="select(4)">{{copy.neutral}}</button>
   <p class="belief-copy connection-hint">{{copy.instruction}}</p>
   <div class="belief-dialogue"><CompanionAvatar :appearance="companion.appearance" :palette="companion.palette"/><div class="nes-balloon from-left"><p class="belief-name">{{companion.name}}</p><p class="belief-copy">{{copy.dialogue(mode,need,rating)}}</p></div></div>
  </section>
  <footer v-if="start && !saved" class="belief-actions"><button type="button" class="nes-btn is-primary belief-next" :disabled="busy||!isRating(rating)||index===3&&answered!==4" @click="next">{{index===3?copy.save:copy.next}} →</button><p class="belief-copy belief-draft">{{draftSaved?copy.draft:''}}</p></footer>
  <section v-if="saved" class="nes-container belief-saved" role="status"><h2>{{copy.saved}}</h2><p class="belief-copy">{{copy.savedBody}}</p><button type="button" class="nes-btn is-primary belief-next" @click="emit('continue')">{{locale==='de'?'Weiter: Reisebedingungen':'Next: travel conditions'}} →</button></section>
 </main>
</template>
