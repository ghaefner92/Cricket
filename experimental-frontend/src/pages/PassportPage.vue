<script setup>
import {computed,nextTick,onMounted,onUnmounted,ref} from 'vue'
import CompanionAvatar from '../components/CompanionAvatar.vue'
import {readCompanion} from '../stores/companion.js'
import {rankedGoals,formatWeek} from '../stores/weeklyGoals.js'
import {weeklyMessages} from '../i18n/weeklyGoals.js'
import {beliefMessages} from '../i18n/beliefs.js'
import {postJSON} from '../services/adaptiveBeliefs.js'
import {readActivePassport,frozenSource,renewalState,finishRenewal} from '../services/passportLifecycle.js'
import {passportSource,readPassport,savePassport,validPassport} from '../services/passport.js'
import {toleranceMessages} from '../i18n/tolerances.js'
import '../styles/passport.css'
import '../styles/journey-experience.css'
import PixelJourneyIcon from '../components/PixelJourneyIcon.vue'
import RoutePlanner from '../components/RoutePlanner.vue'
const props=defineProps({locale:{default:'en'},startMode:{default:'home'}}),emit=defineEmits(['locale-change','edit','confirmed'])
let storage;try{storage=window.localStorage}catch{storage={getItem(){return null},setItem(){throw Error('storage')}}}
const companion=ref(readCompanion(storage,{confirmed:true})),source=ref(null),passport=ref(null),error=ref(''),busy=ref(false),planning=ref(false)
const home=ref(props.startMode!=='passport'),pending=ref(null)
function refreshCompanion(){companion.value=readCompanion(storage,{confirmed:true})}
onMounted(()=>window.addEventListener('cricket-settings-change',refreshCompanion))
onUnmounted(()=>window.removeEventListener('cricket-settings-change',refreshCompanion))
const de=computed(()=>props.locale==='de'),goals=computed(()=>weeklyMessages[props.locale]||weeklyMessages.en),beliefs=computed(()=>beliefMessages[props.locale]||beliefMessages.en)
const tolerancesCopy=computed(()=>toleranceMessages[props.locale]||toleranceMessages.en)
const priorities=computed(()=>source.value?rankedGoals(source.value.plan.points):[])
async function startPlanning(){planning.value=true;await nextTick();document.querySelector('.route-page h1')?.focus()}
let controller,alive=true
function prepare(){
 try{
  const active=readActivePassport(storage)
  if(active&&!renewalState(storage)){passport.value=active;source.value=frozenSource(active)}
  else {source.value=passportSource(storage);passport.value=null}
  error.value=''
 }catch(e){source.value=null;passport.value=null;error.value=['goals','valences','availability','beliefs','tolerances'].includes(e.message)?e.message:'storage'}
}
function confirmPassport(){
 try{
  const latest=passportSource(storage)
  if(!pending.value||latest.fingerprint!==source.value.fingerprint)throw Error('changed')
  passport.value=savePassport(storage,source.value,pending.value);pending.value=null;finishRenewal(storage);emit('confirmed')
 }catch(e){error.value=e.message==='changed'?'changed':'storage'}
}

async function create(){
 if(busy.value)return
 prepare();if(!source.value||passport.value)return
 const current=source.value;busy.value=true;controller=new AbortController()
 try{
  const result=await postJSON('/api/dyconet/adaptive-passport/bootstrap',current.payload,controller.signal)
  if(!alive)return
  const latest=passportSource(storage)
  if(latest.fingerprint!==current.fingerprint){prepare();error.value='changed';return}
  if(!validPassport(result,current))throw Error('response');pending.value=result
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
 <RoutePlanner v-if="planning && passport" :locale="locale" :passport="passport.response.cognitive_passport" :fingerprint="passport.fingerprint" @back="planning=false;home=true" @locale-change="emit('locale-change',$event)"/>
 <main v-else-if="passport && home" class="passport-page journey-home" :lang="locale">
  <header class="passport-topbar"><span class="journey-wordmark">CRICKET</span><nav :aria-label="de?'Sprache wählen':'Choose language'"><button v-for="language in ['en','de']" :key="language" class="nes-btn" :class="{'is-primary':locale===language}" :aria-pressed="locale===language" @click="emit('locale-change',language)">{{language.toUpperCase()}}</button></nav></header>
  <p class="passport-kicker">{{de?'DEIN REISEBEGLEITER':'YOUR TRAVEL COMPANION'}}</p>
  <h1 tabindex="-1">{{de?'Wohin geht es heute?':'Where shall we go today?'}}</h1>
  <section class="nes-container passport-hero journey-home-hero"><CompanionAvatar :appearance="companion.appearance" :palette="companion.palette" animated/><div><h2>{{companion.name}}</h2><p>{{de?'Dein Passport ist bereit. Lass uns einen Weg finden, der zu dir passt.':'Your passport is ready. Let’s find a way that fits you.'}}</p><button class="nes-btn is-primary" @click="startPlanning">{{de?'Wohin gehen wir?':'Where are we going?'}} →</button></div></section>
  <section class="nes-container journey-home-goals"><h2>{{de?'Diese Woche zählt':'What matters this week'}}</h2><p>{{formatWeek(source.plan.weekStart,locale)}}</p><ul><li v-for="item in priorities.slice(0,2)" :key="item.key"><PixelJourneyIcon kind="star" :animated="false"/><span>{{goals.goals[item.key].title}}</span></li></ul><p class="route-note">{{de?'Prioritäten deines bestätigten Passports.':'Priorities from your confirmed Passport.'}}</p></section>
  <button class="nes-btn journey-passport-link" @click="home=false">{{de?'Meinen Passport ansehen':'View my passport'}}</button>
 </main>
 <main v-else class="passport-page" :lang="locale">
  <header class="passport-topbar"><button v-if="!passport" class="nes-btn" type="button" :disabled="busy" @click="emit('edit','tolerances')">← {{de?'Zurück':'Back'}}</button><nav :aria-label="de?'Sprache wählen':'Choose language'"><button v-for="language in ['en','de']" :key="language" class="nes-btn" :class="{'is-primary':locale===language}" :aria-pressed="locale===language" type="button" @click="emit('locale-change',language)">{{language.toUpperCase()}}</button></nav></header>
  <button v-if="passport" class="nes-btn journey-home-return" @click="home=true">← {{de?'Startseite':'Home'}}</button>
  <p class="passport-kicker">06 / COGNITIVE PASSPORT</p><h1 tabindex="-1">{{passport?(de?'Dein Passport ist bereit':'Your passport is ready'):(de?'Deine Reise beginnt':'Your journey begins')}}</h1>
  <section class="nes-container passport-hero"><CompanionAvatar :appearance="companion.appearance" :palette="companion.palette" animated/><div><h2>{{companion.name}}</h2><p>{{passport?(de?'Alles gespeichert. Wir sind bereit!':'All saved. We’re ready!'):(de?'Überprüfe deine Auswahl und erstelle deinen Passport.':'Review your choices and create your passport.')}}</p></div></section>
  <section v-if="error" class="nes-container passport-error" role="alert"><p>{{errorText}}</p><button v-if="['goals','valences','availability','beliefs','tolerances'].includes(error)" class="nes-btn" type="button" :disabled="busy" @click="emit('edit',error==='availability'?'identity':error)">{{de?'Antworten überprüfen':'Review answers'}}</button></section>
  <template v-if="source">
   <button v-if="passport" class="nes-btn is-primary passport-create" type="button" @click="startPlanning">{{de?'Route planen':'Plan a route'}} →</button>
   <details class="nes-container passport-panel"><summary>{{de?'Mobilitätsausstattung':'Travel kit'}}</summary><div class="passport-section-heading"><h2>{{de?'Mobilitätsausstattung':'Travel kit'}}</h2><button v-if="!passport" class="nes-btn" type="button" :disabled="busy" @click="emit('edit','identity')">{{de?'Ändern':'Edit'}}</button></div><ul class="passport-modes"><li v-for="(available,mode) in source.availability" :key="mode"><span>{{beliefs.modes[mode]}}</span><strong>{{available?(de?'Ja':'Yes'):(de?'Nein':'No')}}</strong></li></ul></details>
   <details class="nes-container passport-panel"><summary>{{de?'Wochenprioritäten':'Weekly priorities'}}</summary><div class="passport-section-heading"><h2>{{de?'Wochenprioritäten':'Weekly priorities'}}</h2><button v-if="!passport" class="nes-btn" type="button" :disabled="busy" @click="emit('edit','goals')">{{de?'Ändern':'Edit'}}</button></div><p>{{formatWeek(source.plan.weekStart,locale)}}</p><ul class="passport-modes"><li v-for="item in priorities" :key="item.key"><span>{{item.rank}}. {{goals.goals[item.key].title}}</span><strong>{{item.points}}/10</strong></li></ul></details>
   <details class="nes-container passport-panel"><summary>{{de?'Mobilitätsgefühle':'Transport feelings'}}</summary><div class="passport-section-heading"><h2>{{de?'Mobilitätsgefühle':'Transport feelings'}}</h2><button v-if="!passport" class="nes-btn" type="button" :disabled="busy" @click="emit('edit','valences')">{{de?'Ändern':'Edit'}}</button></div><ul class="passport-modes"><li v-for="(value,mode) in source.valences.answers" :key="mode"><span>{{beliefs.modes[mode]}}</span><strong>{{value>0?'+':''}}{{value}}</strong></li></ul></details>
   <details class="nes-container passport-panel"><summary>{{de?'Deine Verbindungen':'Your connections'}}</summary><div class="passport-section-heading"><h2>{{de?'Deine Verbindungen':'Your connections'}}</h2><button v-if="!passport" class="nes-btn" type="button" :disabled="busy" @click="emit('edit','beliefs')">{{de?'Ändern':'Edit'}}</button></div><p>{{de?'Alle vier Verbindungen beantwortet und gespeichert.':'All four connections answered and saved.'}}</p><ul v-if="passport" class="passport-modes"><li v-for="(value,cell) in passport.response.cognitive_passport.observed_measurements?.beliefs_raw_1_to_7||{}" :key="cell"><span>{{beliefs.modes[cell.split('__')[0]]||cell.split('__')[0]}} · {{beliefs.needs[cell.split('__')[1]]||cell.split('__')[1]}}</span><strong>{{value}}/7</strong></li></ul></details>
   <details class="nes-container passport-panel"><summary>{{de?'Reisebedingungen':'Travel conditions'}}</summary><div class="passport-section-heading"><h2>{{de?'Reisebedingungen':'Travel conditions'}}</h2><button v-if="!passport" class="nes-btn" type="button" :disabled="busy" @click="emit('edit','tolerances')">{{de?'Ändern':'Edit'}}</button></div><ul class="passport-modes"><li v-for="(value,key) in source.toleranceState.answers" :key="key"><span>{{tolerancesCopy.conditions[key].title}}</span><strong>{{value}}/7</strong></li></ul></details>
   <p v-if="busy" class="passport-status" role="status">{{de?'Dein Passport wird erstellt…':'Creating your passport…'}}</p>
   <button v-if="!passport&&!pending" class="nes-btn is-primary passport-create" type="button" :disabled="busy" @click="create">{{de?'Cognitive Passport erstellen':'Create Cognitive Passport'}}</button>
   <section v-if="pending&&!passport" class="nes-container passport-confirmation"><h2>{{de?'Passport bestätigen':'Confirm your Passport'}}</h2><p>{{de?'Überprüfe deine Antworten oben. Nach dem Bestätigen können sie nicht mehr direkt bearbeitet werden. Ein bisheriger Passport wird erst jetzt ersetzt.':'Review your answers above. After confirmation they cannot be edited directly. Your current Passport is replaced only when you confirm.'}}</p><button class="nes-btn is-success" @click="confirmPassport">{{de?'Bestätigen und speichern':'Confirm and save'}}</button><button class="nes-btn" @click="pending=null">{{de?'Noch nicht':'Not yet'}}</button></section>
   <section v-if="passport" class="nes-container passport-success" role="status"><h2>{{de?'Passport gespeichert!':'Passport saved!'}}</h2><p>{{de?'Dein bestätigtes Profil ist auf diesem Gerät gespeichert und schreibgeschützt.':'Your confirmed profile is saved on this device and is read-only.'}}</p></section>
  </template>
 </main>
</template>
