<script setup>
// IMIQ experimental flow v7. App.vue and its four-second intro remain unchanged.
import {nextTick,ref} from 'vue'
import CompanionIdentityPage from './CompanionIdentityPage.vue'
import WeeklyGoalsPage from './WeeklyGoalsPage.vue'
import ValencePage from './ValencePage.vue'
import TolerancesPage from './TolerancesPage.vue'
import PassportPage from './PassportPage.vue'
import {passportSource,readPassport} from '../services/passport.js'
import BeliefsPage from './BeliefsPage.vue'
import {readAvailability,completeAvailability} from '../stores/availability.js'
import {readCompanion} from '../stores/companion.js'
import {loadPlan,totalPoints,POINT_BUDGET} from '../stores/weeklyGoals.js'
import {readValences,allAnswered} from '../stores/valences.js'
import '../styles/rpg-theme.css'
const props=defineProps({locale:{type:String,default:'en'}})
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
const step=ref(initial)
async function goTo(value){step.value=value;try{localStorage.setItem(key,value)}catch{};await nextTick();window.scrollTo({top:0,behavior:'instant'});document.querySelector('.companion-builder h1, .weekly-goals h1, .valence-page h1, .belief-page h1, .passport-page h1, .tolerances-page h1')?.focus()}
</script>
<template><div class="rpg-shell">
 <CompanionIdentityPage v-if="step==='identity'" :locale="locale" @locale-change="emit('locale-change',$event)" @continue="goTo('goals')"/>
 <WeeklyGoalsPage v-else-if="step==='goals'" :locale="locale" @locale-change="emit('locale-change',$event)" @back="goTo('identity')" @continue="goTo('valences')"/>
 <ValencePage v-else-if="step==='valences'" :locale="locale" @locale-change="emit('locale-change',$event)" @back="goTo('goals')" @continue="goTo('beliefs')"/>
 <TolerancesPage v-else-if="step==='tolerances'" :locale="locale" @locale-change="emit('locale-change',$event)" @back="goTo('beliefs')" @continue="goTo('passport')"/>
 <PassportPage v-else-if="step==='passport'" :locale="locale" @locale-change="emit('locale-change',$event)" @edit="goTo($event)"/>
 <BeliefsPage v-else :locale="locale" @locale-change="emit('locale-change',$event)" @back="goTo('valences')" @goals="goTo('goals')" @continue="goTo('tolerances')"/>
</div></template>
