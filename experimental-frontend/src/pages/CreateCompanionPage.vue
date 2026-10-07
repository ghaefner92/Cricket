<script setup>
// Confirmed Passports are read-only; renewal uses a recoverable answer draft.
import {nextTick,ref} from 'vue'
import CompanionIdentityPage from './CompanionIdentityPage.vue'
import WeeklyGoalsPage from './WeeklyGoalsPage.vue'
import ValencePage from './ValencePage.vue'
import TolerancesPage from './TolerancesPage.vue'
import PassportPage from './PassportPage.vue'
import {readActivePassport,renewalState,cancelRenewal} from '../services/passportLifecycle.js'
import {passportSource,readPassport} from '../services/passport.js'
import BeliefsPage from './BeliefsPage.vue'
import {readAvailability,completeAvailability} from '../stores/availability.js'
import {readCompanion} from '../stores/companion.js'
import {loadPlan,totalPoints,POINT_BUDGET} from '../stores/weeklyGoals.js'
import {readValences,allAnswered} from '../stores/valences.js'
import '../styles/rpg-theme.css'
const props=defineProps({locale:{type:String,default:'en'},startMode:{default:'home'}})
const emit=defineEmits(['locale-change'])
const key='imiq.experimental.onboarding-step.v1'
let initial='identity'
try{if(readCompanion(localStorage).name.trim()&&completeAvailability(readAvailability(localStorage))){
 const last=localStorage.getItem(key)
 if(['goals','valences','beliefs','tolerances','passport'].includes(last))initial='goals'
 if(['valences','beliefs','tolerances','passport'].includes(last)&&totalPoints(loadPlan(localStorage).points)===POINT_BUDGET){initial='valences';if(['beliefs','tolerances','passport'].includes(last)&&allAnswered(readValences(localStorage)))initial='beliefs'}
 if(['tolerances','passport'].includes(last)){try{passportSource(localStorage,{requireTolerances:false});initial='tolerances'}catch{}}
 if(last==='passport'){try{if(readPassport(localStorage,passportSource(localStorage)))initial='passport'}catch{}}
}}catch{}
if(readActivePassport(localStorage)&&!renewalState(localStorage))initial='passport'
const renewing=ref(!!renewalState(localStorage))
const step=ref(initial)
function abandonRenewal(){try{cancelRenewal(localStorage);renewing.value=false;goTo('passport')}catch{window.alert(props.locale==='de'?'Der vorherige Passport konnte nicht wiederhergestellt werden.':'Could not restore the previous Passport.')}}
async function goTo(value){step.value=value;try{localStorage.setItem(key,value)}catch{};await nextTick();window.scrollTo({top:0,behavior:'instant'});document.querySelector('.companion-builder h1, .weekly-goals h1, .valence-page h1, .belief-page h1, .passport-page h1, .tolerances-page h1')?.focus()}
</script>
<template><div class="rpg-shell"><aside v-if="renewing" class="nes-container renewal-banner"><p>{{locale==='de'?'Du erstellst einen neuen Passport. Dein bestätigter Passport bleibt erhalten.':'You are creating a new Passport. Your confirmed Passport is kept until you replace it.'}}</p><button class="nes-btn" @click="abandonRenewal">{{locale==='de'?'Abbrechen und bisherigen Passport nutzen':'Cancel and use my current Passport'}}</button></aside>
 <CompanionIdentityPage v-if="step==='identity'" :locale="locale" @locale-change="emit('locale-change',$event)" @continue="goTo('goals')"/>
 <WeeklyGoalsPage v-else-if="step==='goals'" :locale="locale" @locale-change="emit('locale-change',$event)" @back="goTo('identity')" @continue="goTo('valences')"/>
 <ValencePage v-else-if="step==='valences'" :locale="locale" @locale-change="emit('locale-change',$event)" @back="goTo('goals')" @continue="goTo('beliefs')"/>
 <TolerancesPage v-else-if="step==='tolerances'" :locale="locale" @locale-change="emit('locale-change',$event)" @back="goTo('beliefs')" @continue="goTo('passport')"/>
 <PassportPage :start-mode="startMode" v-else-if="step==='passport'" :locale="locale" @locale-change="emit('locale-change',$event)" @edit="goTo($event)" @confirmed="renewing=false"/>
 <BeliefsPage v-else :locale="locale" @locale-change="emit('locale-change',$event)" @back="goTo('valences')" @goals="goTo('goals')" @continue="goTo('tolerances')"/>
</div></template>
