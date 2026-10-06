<script setup>
import {computed,watch,nextTick} from 'vue'
import RouteJourneyCard from './RouteJourneyCard.vue'
import {beliefMessages} from '../i18n/beliefs.js'
import {modelMode} from '../services/routeExplanation.js'
import {recommendation} from '../services/routePresentation.js'
const props=defineProps({result:{required:true},passport:{required:true},companion:{required:true},locale:{default:'en'},selected:{default:null},paused:{default:false}})
const emit=defineEmits(['select'])
function mode(key){return (beliefMessages[props.locale]||beliefMessages.en).modes[modelMode(key)]||key}
const view=computed(()=>recommendation(props.result)),de=computed(()=>props.locale==='de')
watch(()=>props.selected,async(id)=>{await nextTick();if(id){const card=document.getElementById(`route-card-${id}`);const details=card?.closest('.journey-alternative');if(details)details.open=true}},{immediate:true})
</script>
<template>
 <div class="journey-recommendations">
  <p class="journey-result-intro">{{view.state==='clear'?(de?'Ein Modus hebt sich im Modell klar ab. Entdecke ihn und vergleiche die Alternativen.':'One mode stands out clearly in the model. Explore it and compare the alternatives.'):(de?'Vergleiche deine Möglichkeiten. Das Modell liefert noch keine eindeutige Empfehlung.':'Compare your options. The model does not yet give a clear recommendation.')}}</p>
  <RouteJourneyCard v-if="view.primary" :card="view.primary" :passport="passport" :result="result" :companion="companion" :locale="locale" primary :state="view.state" :selected="selected" :paused="paused" @select="emit('select',$event)"/>
  <slot name="map"/>
  <h3 v-if="view.others.length" class="journey-alternatives-heading">{{de?'Weitere Wege für deine Reise':'Other paths for your journey'}}</h3>
  <details v-for="c in view.others" :key="`${c.route.rank}-${c.route.mode_key}`" class="journey-alternative">
   <summary><span>{{c.route.rank}}. {{mode(c.route.mode_key)}}</span><span>{{c.route.available===false?(de?'Nicht verfügbar':'Unavailable'):(Number.isFinite(c.route.summary?.duration_seconds)?`${new Intl.NumberFormat(locale,{maximumFractionDigits:1}).format(c.route.summary.duration_seconds/60)} min`:'—')}} · {{Number.isFinite(c.route.summary?.distance_meters)?`${new Intl.NumberFormat(locale,{maximumFractionDigits:2}).format(c.route.summary.distance_meters/1000)} km`:'—'}}</span></summary>
   <RouteJourneyCard :card="c" :passport="passport" :result="result" :companion="companion" :locale="locale" :state="view.state" :selected="selected" :paused="paused" @select="emit('select',$event)"/>
  </details>
  <p v-if="!view.cards.length" role="status">{{de?'Keine Alternativen gefunden.':'No alternatives found.'}}</p>
 </div>
</template>
