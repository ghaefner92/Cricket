<script setup>
import {computed,reactive,ref,watch} from 'vue'
import AvailabilityScene from './AvailabilityScene.vue'
import {MODES,readAvailability,writeAvailability,completeAvailability} from '../stores/availability.js'
import '../styles/availability.css'
const props=defineProps({locale:String,appearance:String,palette:String})
const emit=defineEmits(['ready'])
const text={en:{title:'Your travel kit',question:'Which ways of travelling can you use?',hint:'Choose Yes or No for each mode. You can change this later.',yes:'Yes',no:'No',pending:'Choose an answer',selected:'Available',unavailable:'Not available',required:'Choose at least one available mode.',storage:'Could not save your travel kit.',modes:{walk:'Walking',bike:'Bicycle',pt:'Public transport',car:'Car'}},de:{title:'Deine Mobilitätsausstattung',question:'Welche Fortbewegungsmöglichkeiten kannst du nutzen?',hint:'Wähle für jedes Verkehrsmittel Ja oder Nein. Du kannst dies später ändern.',yes:'Ja',no:'Nein',pending:'Antwort auswählen',selected:'Verfügbar',unavailable:'Nicht verfügbar',required:'Wähle mindestens eine verfügbare Möglichkeit.',storage:'Deine Auswahl konnte nicht gespeichert werden.',modes:{walk:'Zu Fuß',bike:'Fahrrad',pt:'Öffentlicher Verkehr',car:'Auto'}}}
const copy=computed(()=>text[props.locale]||text.en)
let storage;try{storage=window.localStorage}catch{storage={getItem(){return null},setItem(){throw Error('Storage')}}}
const state=reactive(readAvailability(storage)),failed=ref(false)
const ready=computed(()=>completeAvailability(state)&&!failed.value)
watch(ready,v=>emit('ready',v),{immediate:true})
function choose(mode,value){state.answers[mode]=value;try{writeAvailability(storage,state);failed.value=false}catch{failed.value=true}}
</script>
<template><section class="availability-kit" aria-labelledby="kit-title">
<h2 id="kit-title">{{copy.title}}</h2><p class="builder-copy">{{copy.question}}</p><p class="builder-copy field-hint">{{copy.hint}}</p>
<div class="availability-grid"><fieldset v-for="mode in MODES" :key="mode" class="availability-card" :class="{'is-equipped':state.answers[mode]===true}">
<legend>{{copy.modes[mode]}}</legend><AvailabilityScene :mode="mode" :appearance="appearance" :palette="palette" :available="state.answers[mode]" :label="copy.modes[mode]+' — Magdeburg Hbf'"/>
<div class="availability-answers"><label v-for="value in [true,false]" :key="String(value)"><input type="radio" class="nes-radio" :name="'available-'+mode" :checked="state.answers[mode]===value" @change="choose(mode,value)"><span>{{value?copy.yes:copy.no}}</span></label></div>
<p class="availability-status" aria-live="polite">{{state.answers[mode]===null?copy.pending:state.answers[mode]? '✓ '+copy.selected:copy.unavailable}}</p>
</fieldset></div><p v-if="failed" role="alert" class="builder-error">{{copy.storage}}</p><p v-else-if="!ready" class="builder-copy field-hint">{{copy.required}}</p>
</section></template>
