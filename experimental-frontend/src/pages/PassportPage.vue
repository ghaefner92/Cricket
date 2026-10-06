<script setup>
import {computed,nextTick,onMounted,onUnmounted,ref} from 'vue'
import CompanionAvatar from '../components/CompanionAvatar.vue'
import {readCompanion} from '../stores/companion.js'
import {rankedGoals,formatWeek} from '../stores/weeklyGoals.js'
import {weeklyMessages} from '../i18n/weeklyGoals.js'
import {beliefMessages} from '../i18n/beliefs.js'
import {postJSON} from '../services/adaptiveBeliefs.js'
import {passportSource,readPassport,savePassport} from '../services/passport.js'
import {toleranceMessages} from '../i18n/tolerances.js'
import '../styles/passport.css'
import RoutePlanner from '../components/RoutePlanner.vue'
const props=defineProps({locale:{default:'en'}}),emit=defineEmits(['locale-change','edit'])
let storage;try{storage=window.localStorage}catch{storage={getItem(){return null},setItem(){throw Error('storage')}}}
const companion=readCompanion(storage,{confirmed:true}),source=ref(null),passport=ref(null),error=ref(''),busy=ref(false),planning=ref(false)
const de=computed(()=>props.locale==='de'),goals=computed(()=>weeklyMessages[props.locale]||weeklyMessages.en),beliefs=computed(()=>beliefMessages[props.locale]||beliefMessages.en)
const tolerancesCopy=computed(()=>toleranceMessages[props.locale]||toleranceMessages.en)
const priorities=computed(()=>source.value?rankedGoals(source.value.plan.points):[])
async function startPlanning(){planning.value=true;await nextTick();document.querySelector('.route-page h1')?.focus()}
let controller,alive=true
function prepare(){try{source.value=passportSource(storage);passport.value=readPassport(storage,source.value);error.value=''}catch(e){source.value=null;passport.value=null;error.value=['goals','valences','availability','beliefs','tolerances'].includes(e.message)?e.message:'storage'}}
async function create(){
 if(busy.value)return
 prepare();if(!source.value||passport.value)return
 const current=source.value;busy.value=true;controller=new AbortController()
 try{
  const result=await postJSON('/api/dyconet/adaptive-passport/bootstrap',current.payload,controller.signal)
  if(!alive)return
  const latest=passportSource(storage)
  if(latest.fingerprint!==current.fingerprint){prepare();error.value='changed';return}
  try{passport.value=savePassport(storage,current,result)}catch(e){error.value=e.message==='response'?'network':'storage'}
 }catch(e){if(alive)error.value=['goals','valences','availability','beliefs','tolerances'].includes(e.message)?e.message:'network'}
 finally{if(alive)busy.value=false}
}
const errorText=computed(()=>({
 tolerances:de.value?'Bitte beantworte und speichere alle sieben Reisebedingungen.':'Please answer and save all seven travel conditions.',
 goals:de.value?'Bitte überprüfe deine Wochenziele.':'Please review your weekly goals.',valences:de.value?'Bitte beantworte alle vier Mobilitätsgefühle.':'Please answer all four transport feelings.',availability:de.value?'Bitte bestätige deine Mobilitätsausstattung.':'Please confirm your travel kit.',beliefs:de.value?'Bitte speichere deine vier Verbindungen erneut.':'Please save your four connections again.',changed:de.value?'Deine Antworten haben sich geändert. Bitte überprüfe sie erneut.':'Your answers changed. Please review them again.',storage:de.value?'Dein Browser konnte den Passport nicht speichern.':'Your browser could not save the passport.',network:de.value?'Der Passport konnte nicht erstellt werden. Prüfe deinen lokalen Server und versuche es erneut.':'The passport could not be created. Check your local server and try again.'
}[error.value]))
onMounted(prepare);onUnmounted(()=>{alive=false;controller?.abort()})
</script>
<template>
 <RoutePlanner v-if="planning && passport" :locale="locale" :passport="passport.response.cognitive_passport" :fingerprint="passport.fingerprint" @back="planning=false" @locale-change="emit('locale-change',$event)"/>
 <main v-else class="passport-page" :lang="locale">
  <header class="passport-topbar"><button class="nes-btn" type="button" :disabled="busy" @click="emit('edit','tolerances')">← {{de?'Zurück':'Back'}}</button><nav :aria-label="de?'Sprache wählen':'Choose language'"><button v-for="language in ['en','de']" :key="language" class="nes-btn" :class="{'is-primary':locale===language}" :aria-pressed="locale===language" type="button" @click="emit('locale-change',language)">{{language.toUpperCase()}}</button></nav></header>
  <p class="passport-kicker">06 / COGNITIVE PASSPORT</p><h1 tabindex="-1">{{passport?(de?'Dein Passport ist bereit':'Your passport is ready'):(de?'Deine Reise beginnt':'Your journey begins')}}</h1>
  <section class="nes-container passport-hero"><CompanionAvatar :appearance="companion.appearance" :palette="companion.palette" animated/><div><h2>{{companion.name}}</h2><p>{{passport?(de?'Alles gespeichert. Wir sind bereit!':'All saved. We’re ready!'):(de?'Überprüfe deine Auswahl und erstelle deinen Passport.':'Review your choices and create your passport.')}}</p></div></section>
  <section v-if="error" class="nes-container passport-error" role="alert"><p>{{errorText}}</p><button v-if="['goals','valences','availability','beliefs','tolerances'].includes(error)" class="nes-btn" type="button" :disabled="busy" @click="emit('edit',error==='availability'?'identity':error)">{{de?'Antworten überprüfen':'Review answers'}}</button></section>
  <template v-if="source">
   <button v-if="passport" class="nes-btn is-primary passport-create" type="button" @click="startPlanning">{{de?'Route planen':'Plan a route'}} →</button>
   <details class="nes-container passport-panel"><summary>{{de?'Mobilitätsausstattung':'Travel kit'}}</summary><div class="passport-section-heading"><h2>{{de?'Mobilitätsausstattung':'Travel kit'}}</h2><button class="nes-btn" type="button" :disabled="busy" @click="emit('edit','identity')">{{de?'Ändern':'Edit'}}</button></div><ul class="passport-modes"><li v-for="(available,mode) in source.availability" :key="mode"><span>{{beliefs.modes[mode]}}</span><strong>{{available?(de?'Ja':'Yes'):(de?'Nein':'No')}}</strong></li></ul></details>
   <details class="nes-container passport-panel"><summary>{{de?'Wochenprioritäten':'Weekly priorities'}}</summary><div class="passport-section-heading"><h2>{{de?'Wochenprioritäten':'Weekly priorities'}}</h2><button class="nes-btn" type="button" :disabled="busy" @click="emit('edit','goals')">{{de?'Ändern':'Edit'}}</button></div><p>{{formatWeek(source.plan.weekStart,locale)}}</p><ul class="passport-modes"><li v-for="item in priorities" :key="item.key"><span>{{item.rank}}. {{goals.goals[item.key].title}}</span><strong>{{item.points}}/10</strong></li></ul></details>
   <details class="nes-container passport-panel"><summary>{{de?'Mobilitätsgefühle':'Transport feelings'}}</summary><div class="passport-section-heading"><h2>{{de?'Mobilitätsgefühle':'Transport feelings'}}</h2><button class="nes-btn" type="button" :disabled="busy" @click="emit('edit','valences')">{{de?'Ändern':'Edit'}}</button></div><ul class="passport-modes"><li v-for="(value,mode) in source.valences.answers" :key="mode"><span>{{beliefs.modes[mode]}}</span><strong>{{value>0?'+':''}}{{value}}</strong></li></ul></details>
   <details class="nes-container passport-panel"><summary>{{de?'Deine Verbindungen':'Your connections'}}</summary><div class="passport-section-heading"><h2>{{de?'Deine Verbindungen':'Your connections'}}</h2><button class="nes-btn" type="button" :disabled="busy" @click="emit('edit','beliefs')">{{de?'Ändern':'Edit'}}</button></div><p>{{de?'Alle vier Verbindungen beantwortet und gespeichert.':'All four connections answered and saved.'}}</p></details>
   <details class="nes-container passport-panel"><summary>{{de?'Reisebedingungen':'Travel conditions'}}</summary><div class="passport-section-heading"><h2>{{de?'Reisebedingungen':'Travel conditions'}}</h2><button class="nes-btn" type="button" :disabled="busy" @click="emit('edit','tolerances')">{{de?'Ändern':'Edit'}}</button></div><ul class="passport-modes"><li v-for="(value,key) in source.toleranceState.answers" :key="key"><span>{{tolerancesCopy.conditions[key].title}}</span><strong>{{value}}/7</strong></li></ul></details>
   <p v-if="busy" class="passport-status" role="status">{{de?'Dein Passport wird erstellt…':'Creating your passport…'}}</p>
   <button v-if="!passport" class="nes-btn is-primary passport-create" type="button" :disabled="busy" @click="create">{{de?'Cognitive Passport erstellen':'Create Cognitive Passport'}}</button>
   <section v-else class="nes-container passport-success" role="status"><h2>{{de?'Passport gespeichert!':'Passport saved!'}}</h2><p>{{de?'Dein Profil ist auf diesem Gerät verfügbar.':'Your profile is available on this device.'}}</p></section>
  </template>
 </main>
</template>
