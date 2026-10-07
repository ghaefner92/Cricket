<script setup>
import {computed,watch,nextTick} from 'vue'
import RouteJourneyCard from './RouteJourneyCard.vue'
import {beliefMessages} from '../i18n/beliefs.js'
import {modelMode} from '../services/routeExplanation.js'
import {goalLinks} from '../services/routePresentation.js'
import {ROUTE_COLORS} from '../services/routeMap.js'
import {recommendation} from '../services/routePresentation.js'
import {recommendationMessage} from '../services/recommendationMessaging.js'
const props=defineProps({result:{required:true},passport:{required:true},companion:{required:true},locale:{default:'en'},selected:{default:null},paused:{default:false},cardsVisible:{default:true},chosenId:{default:null}})
const emit=defineEmits(['select','choose'])
function mode(key){return (beliefMessages[props.locale]||beliefMessages.en).modes[modelMode(key)]||key}
const view=computed(()=>recommendation(props.result)),de=computed(()=>props.locale==='de')
const message=computed(()=>recommendationMessage(view.value,props.locale,mode(view.value.winner)))
watch([()=>props.selected,()=>view.value.state],async([id])=>{await nextTick();if(id){const card=document.getElementById(`route-card-${id}`);const details=card?.closest('.journey-alternative');if(details)details.open=true}},{immediate:true})
</script>
<template>
 <div class="journey-recommendations">
  <h3 v-show="cardsVisible" class="journey-result-intro">{{message.title}}</h3>
  <p v-if="view.state!=='clear'&&view.cards.length" v-show="cardsVisible" class="journey-order-note">{{message.order}}</p>
  <RouteJourneyCard v-show="cardsVisible" v-if="view.state==='clear'&&view.primary" :card="view.primary" :passport="passport" :result="result" :companion="companion" :locale="locale" primary :state="view.state" :selected="selected" :chosen-id="chosenId" :paused="paused" @select="emit('select',$event)" @choose="emit('choose',$event)"/>
  <slot name="map"/>
  <h3 v-show="cardsVisible" v-if="view.state==='clear'&&view.others.length" class="journey-alternatives-heading">{{de?'Weitere Wege für deine Reise':'Other paths for your journey'}}</h3>
  <details v-show="cardsVisible" v-for="c in view.state==='clear'?view.others:[]" :key="`${c.route.rank}-${c.route.mode_key}`" class="journey-alternative">
   <summary><span><i class="journey-route-colour" :style="{background:ROUTE_COLORS[c.route.mode_key]||'#2766a0'}" aria-hidden="true"></i> {{mode(c.route.mode_key)}}</span><span>{{c.route.available===false?(de?'Nicht verfügbar':'Unavailable'):(Number.isFinite(c.route.summary?.duration_seconds)?`${new Intl.NumberFormat(locale,{maximumFractionDigits:1}).format(c.route.summary.duration_seconds/60)} min`:'—')}} · {{Number.isFinite(c.route.summary?.distance_meters)?`${new Intl.NumberFormat(locale,{maximumFractionDigits:2}).format(c.route.summary.distance_meters/1000)} km`:'—'}}</span><small v-if="chosenId===`${c.route.rank}-${c.route.mode_key}`" class="route-state-badge route-state-badge--saved">{{de?'Gewählt und gespeichert':'Chosen and saved'}}</small><small v-if="goalLinks(passport,c.route.mode_key).length" class="journey-alternative-goal">{{de?'In deinem Profil verbunden mit: ':'Linked in your profile with: '}}{{(beliefMessages[locale]||beliefMessages.en).needs[goalLinks(passport,c.route.mode_key)[0].name]||goalLinks(passport,c.route.mode_key)[0].name}}</small></summary>
   <RouteJourneyCard :card="c" :passport="passport" :result="result" :companion="companion" :locale="locale" :state="view.state" :selected="selected" :chosen-id="chosenId" :paused="paused" @select="emit('select',$event)" @choose="emit('choose',$event)"/>
  </details>
  <div v-if="view.state!=='clear'" v-show="cardsVisible" class="journey-equal-options"><RouteJourneyCard v-for="c in view.cards" :key="`${c.route.rank}-${c.route.mode_key}`" neutral :card="c" :passport="passport" :result="result" :companion="companion" :locale="locale" :state="view.state" :selected="selected" :chosen-id="chosenId" :paused="paused" @select="emit('select',$event)" @choose="emit('choose',$event)"/></div>
  <p v-show="cardsVisible" v-if="!view.cards.length" role="status">{{de?'Keine Alternativen gefunden.':'No alternatives found.'}}</p>
 </div>
</template>

<style scoped>.journey-equal-options{display:grid;grid-template-columns:minmax(0,1fr);gap:20px}.journey-equal-options :deep(.journey-card){margin:0}.journey-order-note{color:#c5d2ef;line-height:2}.journey-result-intro{color:#d0e8fc;font-size:12px;line-height:2}</style>
