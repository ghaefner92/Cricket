<script setup>
import {computed} from 'vue'
import PixelJourneyIcon from './PixelJourneyIcon.vue'
import {clockText,delayText,transitLegs,transitTiming} from '../services/transitPresentation.js'
const props=defineProps({route:{required:true},locale:{default:'en'},now:{default:()=>Date.now()}})
const de=computed(()=>props.locale==='de'),legs=computed(()=>transitLegs(props.route))
const timing=computed(()=>transitTiming(props.route,props.now))
const number=v=>typeof v==='number'&&Number.isFinite(v)?new Intl.NumberFormat(props.locale,{maximumFractionDigits:1}).format(v):'—'
</script>
<template>
 <div class="transit-journey">
  <p v-if="timing.known">{{de?'Start spätestens':'Leave by'}} <strong>{{clockText(timing.leave,locale)}}</strong> · {{de?'Einstieg':'Board'}} <strong>{{clockText(timing.board,locale)}}</strong> {{timing.stop}} · {{timing.accessMinutes}} min {{de?'für Zugang und Warten':'for access and waiting'}}</p>
  <p v-if="timing.expired" role="status">{{de?'Die Startzeit dieser Verbindung ist vorbei. Bitte die Suche aktualisieren.':'The start time for this connection has passed. Refresh your search.'}}</p>
  <div class="transit-summary">
   <span><PixelJourneyIcon kind="pt" :animated="false"/>{{clockText(route.transit?.start_time_ms,locale)}} → {{clockText(route.transit?.end_time_ms,locale)}}</span>
   <span>{{route.summary?.transfers}} {{de?'Umstiege':'transfers'}}</span>
   <span><PixelJourneyIcon kind="walk" :animated="false"/>{{number(route.summary?.walk_distance_meters)}} m</span>
   <span>{{number(route.summary?.waiting_seconds/60)}} min {{de?'Wartezeit':'waiting'}}</span>
  </div>
  <ol class="transit-sequence" :aria-label="de?'Reiseabschnitte':'Journey legs'">
   <li v-for="leg in legs" :key="leg.index" :class="{'transit-leg--walk':leg.mode==='walk'}">
    <PixelJourneyIcon :kind="leg.mode==='walk'?'walk':'pt'" :animated="false"/>
    <div>
     <strong>{{leg.mode==='walk'?(de?'Zu Fuß':'Walk'):(leg.details.source_mode==='TRAM'?(de?'Tram':'Tram'):(de?'ÖPNV':'Transit'))}} {{leg.line||''}}</strong>
     <span>{{leg.details.from?.name||'—'}} → {{leg.details.to?.name||'—'}}</span>
     <span>{{clockText(leg.details.start_time_ms,locale)}} → {{clockText(leg.details.end_time_ms,locale)}} · {{number(leg.duration/60)}} min</span>
     <span v-if="leg.mode==='pt'" class="transit-prediction" :class="{'transit-prediction--live':leg.details.realtime}">
      {{leg.details.realtime?(de?'Live':'Live'):(de?'Fahrplan':'Scheduled')}} · {{delayText(leg.details.departure_delay_seconds,locale)}}
     </span>
    </div>
   </li>
  </ol>
  <small>{{de?'Tram-/Busverlauf auf der Karte ungefähr.':'Transit path on the map is approximate.'}}</small>
  <details><summary>{{de?'Fahrplan und Datenquelle':'Schedule and source'}}</summary>
   <p v-for="leg in legs.filter(l=>l.mode==='pt')" :key="leg.index">
    {{leg.line||leg.details.source_mode}} · {{de?'Geplante Abfahrt':'Scheduled departure'}}: {{clockText(leg.details.scheduled_start_time_ms,locale)}} · {{de?'Ankunftsprognose':'Arrival prediction'}}: {{delayText(leg.details.arrival_delay_seconds,locale)}}
   </p>
   <p>GTFS.de · MVB · OpenStreetMap contributors · CC BY-SA 4.0</p>
  </details>
 </div>
</template>
<style scoped>
.transit-journey{font-family:inherit;margin:16px 0;padding:12px;border:3px solid #c5943b;box-shadow:4px 4px 0 #15233b;background:#182b3f;color:#f6edcc;font-size:10px;line-height:1.9}
.transit-summary{display:flex;flex-wrap:wrap;gap:10px;padding-bottom:12px;border-bottom:2px solid #526378}.transit-summary span{display:flex;align-items:center;gap:6px}.transit-sequence{list-style:none;margin:12px 0;padding:0;display:grid;gap:12px}.transit-sequence li{display:flex;align-items:flex-start;gap:10px;padding:8px;border-left:4px solid #e6b55c;background:#23394d}.transit-sequence .transit-leg--walk{border-left-color:#85b7ff}.transit-sequence li div{min-width:0;display:grid;gap:4px;overflow-wrap:anywhere}.transit-prediction{color:#e2d2af}.transit-prediction--live{color:#b9eccf}.transit-journey small{display:block;font-size:inherit;color:#e4dfcf}.transit-journey details{margin-top:10px}.transit-journey summary{cursor:pointer}.transit-journey p{margin:10px 0;font-size:inherit}
</style>
