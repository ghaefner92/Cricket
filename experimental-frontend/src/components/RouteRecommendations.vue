<script setup>
import {computed,watch,nextTick,ref,onMounted,onUnmounted} from 'vue'
import RouteJourneyCard from './RouteJourneyCard.vue'
import {beliefMessages} from '../i18n/beliefs.js'
import {modelMode} from '../services/routeExplanation.js'
import {recommendation,modeGroups} from '../services/routePresentation.js'
import {recommendationMessage} from '../services/recommendationMessaging.js'
import {clockText,transitTiming} from '../services/transitPresentation.js'
const props=defineProps({result:{required:true},passport:{required:true},companion:{required:true},locale:{default:'en'},selected:{default:null},paused:{default:false},cardsVisible:{default:true},chosenId:{default:null}})
const emit=defineEmits(['select','choose'])
const now=ref(Date.now()),active=ref({}),expanded=ref({})
let timer
onMounted(()=>{timer=setInterval(()=>{now.value=Date.now()},15000)})
onUnmounted(()=>clearInterval(timer))
function mode(key){return (beliefMessages[props.locale]||beliefMessages.en).modes[modelMode(key)]||key}
const id=card=>`${card.route.rank}-${card.route.mode_key}`
const view=computed(()=>recommendation(props.result)),de=computed(()=>props.locale==='de')
const message=computed(()=>recommendationMessage(view.value,props.locale,mode(view.value.winner)))
const groups=computed(()=>modeGroups(props.result,now.value).map(group=>({
 ...group,card:group.cards.find(c=>id(c)===active.value[group.mode])||group.representative
})).sort((a,b)=>Number(b.mode===view.value.winner)-Number(a.mode===view.value.winner)))
const missingModes=computed(()=>Object.entries(props.result.journey_availability||{}).filter(([m,available])=>available&&!view.value.cards.some(c=>modelMode(c.route.mode_key)===m&&c.route.available===true)).map(([m])=>m))
const providerWarnings=computed(()=>{
 const audit=props.result.routing?.provider_audit||{},messages=[]
 if(audit['imiq-routing']?.status==='UNAVAILABLE')messages.push(de.value?'Der übliche Routendienst ist nicht erreichbar. Verfügbare Wege stammen aus alternativen Quellen.':'The usual route service is unavailable. Available journeys are supplied by alternative sources.')
 if(audit.otp?.later_departures?.status==='UNAVAILABLE')messages.push(de.value?'Weitere Abfahrten konnten nicht geladen werden. Die angezeigten Verbindungen bleiben verfügbar.':'Later departures could not be loaded. The returned connections remain available.')
 return messages
})
watch(()=>props.result,()=>{active.value={};expanded.value={}})
watch(()=>props.selected,async selected=>{
 if(!selected)return
 const group=groups.value.find(g=>g.cards.some(c=>id(c)===selected))
 if(group)active.value={...active.value,[group.mode]:selected}
 await nextTick()
},{immediate:true})
function preview(group,card){active.value={...active.value,[group.mode]:id(card)};emit('select',id(card))}
function minutes(value){return Number.isFinite(value)?new Intl.NumberFormat(props.locale,{maximumFractionDigits:0}).format(value/60):'—'}
</script>
<template>
 <div class="journey-recommendations">
  <h3 v-show="cardsVisible" class="journey-result-intro">{{message.title}}</h3>
  <p v-show="cardsVisible" class="journey-order-note">{{message.message}}</p>
  <p v-for="warning in providerWarnings" :key="warning" v-show="cardsVisible" class="route-note" role="status">{{warning}}</p>
  <p v-for="m in missingModes" :key="m" v-show="cardsVisible" class="route-note" role="status">{{de?`Für ${mode(m)} wurde kein passender Weg zurückgegeben. Andere Möglichkeiten bleiben verfügbar.`:`No matching ${mode(m).toLowerCase()} route was returned. Other options remain available.`}}</p>
  <slot name="map"/>
  <div v-show="cardsVisible" class="journey-mode-options">
   <section v-for="group in groups" :key="group.mode" class="journey-mode-group">
    <p v-if="group.mode==='pt'" class="route-note">{{group.card!==group.representative?(de?'Gewählte Verbindungsvorschau · Fußweg zur Haltestelle eingeschlossen':'Selected connection preview · includes the walk to the stop'):(de?'Nächste erreichbare Verbindung · Fußweg zur Haltestelle eingeschlossen':'Next reachable connection · includes the walk to the stop')}}</p>
    <RouteJourneyCard :card="group.card" :passport="passport" :result="result" :companion="companion" :locale="locale" :primary="view.state==='clear'&&group.mode===view.winner" :neutral="view.state!=='clear'" :state="view.state" :selected="selected" :chosen-id="chosenId" :paused="paused" :now="now" @select="emit('select',$event)" @choose="emit('choose',$event)"/>
    <details v-if="group.mode==='pt'&&group.cards.length>1" class="journey-departures" :open="expanded[group.mode]" @toggle="expanded[group.mode]=$event.target.open">
     <summary>{{de?'Weitere Verbindungen und Abfahrten':'More connections and departures'}} ({{group.cards.length}})</summary>
     <div v-for="pattern in group.departureGroups" :key="pattern.key" class="journey-departure-pattern">
      <h4>{{pattern.label}}</h4>
      <button v-for="card in pattern.cards" :key="id(card)" type="button" class="nes-btn journey-departure" :aria-pressed="id(group.card)===id(card)" @click="preview(group,card)">
       <span>{{de?'Start':'Leave'}} {{clockText(transitTiming(card.route,now).leave,locale)}} · {{de?'Abfahrt':'Board'}} {{clockText(transitTiming(card.route,now).board,locale)}} → {{clockText(transitTiming(card.route,now).arrival,locale)}}</span>
       <span>{{minutes(card.route.summary?.duration_seconds)}} min · {{card.route.summary?.transfers??'—'}} {{de?'Umstiege':'transfers'}} · {{Math.ceil(card.route.summary?.walk_distance_meters||0)}} m {{de?'zu Fuß':'walking'}}</span>
       <small>{{transitTiming(card.route,now).expired?(de?'Startzeit vorbei · Suche aktualisieren':'Start time passed · refresh search'):card.route.transit?.realtime_status==='REALTIME'?'Live':(de?'Fahrplan':'Scheduled')}}</small>
      </button>
     </div>
    </details>
   </section>
  </div>
  <p v-show="cardsVisible" v-if="!view.cards.length" role="status">{{de?'Keine Alternativen gefunden.':'No alternatives found.'}}</p>
 </div>
</template>
<style scoped>
.journey-mode-options{display:grid;gap:24px}.journey-mode-group{min-width:0}.journey-mode-group :deep(.journey-card){margin:0}.journey-order-note{color:#c5d2ef;line-height:2}.journey-result-intro{color:#d0e8fc;font-size:12px;line-height:2}.journey-departures{margin-top:12px;padding:14px;border:2px solid #617c9f;background:#15213c;color:#edf4ff}.journey-departures summary{cursor:pointer;min-height:44px;line-height:2}.journey-departure-pattern{display:grid;gap:12px;margin-top:16px}.journey-departure-pattern h4{font-size:11px;line-height:2}.journey-departure{display:grid;gap:8px;text-align:left;white-space:normal;font-size:10px;line-height:2}.journey-departure[aria-pressed=true]{outline:3px solid #f3d479}.journey-departure small{font-size:inherit}
</style>
