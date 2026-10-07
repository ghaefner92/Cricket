<script setup>
import {computed} from 'vue'
import CompanionAvatar from './CompanionAvatar.vue'
import PixelJourneyIcon from './PixelJourneyIcon.vue'
import WeatherInventory from './WeatherInventory.vue'
import {beliefMessages} from '../i18n/beliefs.js'
import {ROUTE_COLORS} from '../services/routeMap.js'
import {goalLinks} from '../services/routePresentation.js'
import {explainRoute,modelMode} from '../services/routeExplanation.js'
import {environmentalRows,finiteNumber} from '../services/routePlanning.js'
const props=defineProps({card:{required:true},passport:{required:true},result:{required:true},companion:{required:true},locale:{default:'en'},primary:{type:Boolean,default:false},neutral:{type:Boolean,default:false},state:{default:'uncertain'},selected:{default:null},chosenId:{default:null},paused:{default:false}})
const emit=defineEmits(['select','choose'])
const de=computed(()=>props.locale==='de'),labels=computed(()=>beliefMessages[props.locale]||beliefMessages.en)
const id=computed(()=>`${props.card.route.rank}-${props.card.route.mode_key}`)
const mode=computed(()=>labels.value.modes[modelMode(props.card.route.mode_key)]||props.card.route.mode_key)
const explanation=computed(()=>explainRoute(props.card)),goals=computed(()=>goalLinks(props.passport,props.card.route.mode_key))
const conditionLabels=computed(()=>de.value?{rain:'Regen',wind:'Wind',heat:'Hitze',cold:'Kälte',darkness:'Dunkelheit',traffic:'Verkehr',crowding:'Gedränge'}:{rain:'rain',wind:'wind',heat:'heat',cold:'cold',darkness:'darkness',traffic:'traffic',crowding:'crowding'})
function number(v,d=2){return finiteNumber(v)?new Intl.NumberFormat(de.value?'de-DE':'en-GB',{maximumFractionDigits:d}).format(v):'—'}
const featured=computed(()=>props.primary&&props.state==='clear')
const title=computed(()=>featured.value?(de.value?'Passt im Modell zu deinem Profil':'Fits your profile in this simulation'):(de.value?'Reisemöglichkeit':'Route option'))
</script>
<template>
 <article :id="`route-card-${id}`" class="nes-container route-card journey-card" :class="{'journey-card--primary':featured,'route-card--selected':selected===id,'route-card--unavailable':card.route.available===false}">
  <div class="journey-card-label"><PixelJourneyIcon :kind="featured?'star':modelMode(card.route.mode_key)" :animated="!paused"/><span>{{title}}</span><span v-if="featured" class="journey-status">{{de?'Modell':'Model'}}</span></div>
  <header class="route-card-heading"><h3 tabindex="-1"><i class="journey-route-colour" :style="{background:ROUTE_COLORS[card.route.mode_key]||'#2766a0'}" aria-hidden="true"></i><PixelJourneyIcon :kind="modelMode(card.route.mode_key)" :animated="!paused"/>{{mode}}</h3><span class="route-badge">{{card.route.available===false?(de?'Nicht verfügbar':'Unavailable'):(de?'Verfügbar':'Available')}}</span></header>
  <div v-if="chosenId===id||selected===id" class="route-state-badges">
   <span v-if="chosenId===id" class="route-state-badge route-state-badge--saved">{{de?'Gewählt und gespeichert':'Chosen and saved'}}</span>
   <span v-if="selected===id" class="route-state-badge">{{de?'Kartenvorschau':'Map preview'}}</span>
  </div>
  <div class="journey-metrics"><span><PixelJourneyIcon kind="clock" :animated="!paused"/>{{number(finiteNumber(card.route.summary?.duration_seconds)?card.route.summary.duration_seconds/60:null,1)}} min</span><span>{{number(finiteNumber(card.route.summary?.distance_meters)?card.route.summary.distance_meters/1000:null,2)}} km</span></div>
  <p v-if="card.route.available===false">{{de?'Mit deiner angegebenen Ausstattung nicht verfügbar.':'Unavailable with your declared travel kit.'}}</p>
  <template v-else>
   <div v-if="!neutral" class="route-avatar-explanation journey-dialogue">
    <CompanionAvatar :appearance="companion.appearance" :palette="companion.palette" :animated="!paused"/>
    <div class="nes-balloon from-left"><h4>{{companion.name}}</h4>
     <p v-if="featured">{{de?'Dieser Modus hebt sich in deinem Modell klar ab.':'This mode stands out clearly in your model.'}}</p>
     <p v-if="goals.length">{{de?'In deinem Profil ist dieser Modus verbunden mit':'In your profile, this mode is linked with'}} {{goals.slice(0,2).map(g=>labels.needs[g.name]||g.name).join(de?' und ':' and ')}}.</p>
     <p v-else>{{de?'Vergleiche Zeit, Weg und deine Prioritäten.':'Compare the time, path and your priorities.'}}</p>

    </div>
   </div>
   <ul v-if="goals.length" class="journey-goals" :aria-label="de?'Verbundene Wochenprioritäten':'Linked weekly priorities'"><li v-for="g in goals" :key="g.name"><PixelJourneyIcon :kind="modelMode(card.route.mode_key)" :animated="false"/>{{labels.needs[g.name]||g.name}}</li></ul>
   <h4 v-if="featured||neutral">{{de?'Dein Umweltinventar':'Your environment inventory'}}</h4>
   <WeatherInventory v-if="featured||neutral" :compact="true" :candidate="card.candidate" :locale="locale" :animated="!paused"/>
   <div class="journey-card-actions"><button type="button" class="nes-btn is-success" @click="emit('choose',card)">{{de?'Diesen Weg wählen':'Choose this journey'}}</button><button v-if="card.audit?.context_ready" type="button" class="nes-btn" :class="{'is-primary':selected!==id}" :aria-pressed="selected===id" @click="emit('select',id)">{{de?'Auf Karte ansehen':'View on map'}}</button></div>
   <details class="route-details journey-why"><summary>{{de?'Warum diese Einordnung?':'Why this assessment?'}}</summary>
    <WeatherInventory v-if="!featured&&!neutral" :candidate="card.candidate" :locale="locale" :animated="!paused"/>
    <p v-if="explanation.available">{{explanation.changes.length?(de?'Der Kontext verändert folgende Bedürfnisse: ':'Context changes these needs: ')+explanation.changes.map(([n])=>labels.needs[n]||n).join(', '):(de?'Die verfügbaren Kontextdaten erzeugen keine zusätzliche Bedürfnisaktivierung.':'Available context data add no need activation.')}}</p>
    <p>{{de?'Das Modell berücksichtigt deine Wochenprioritäten, deine Verbindungen zwischen Zielen und Verkehrsmitteln, deine affektiven Bewertungen im Profil und die aktuelle Verfügbarkeit. Die oben genannten Verbindungen beschreiben dein Profil; sie sind keine isolierten Ursachen der Empfehlung.':'The model uses your weekly priorities, goal–mode connections, affective ratings in your profile and current availability. The links shown above describe your profile; they are not isolated causes of the recommendation.'}}</p>
    <p v-if="featured">{{de?'Hervorgehoben wird der Modus, den alle verfügbaren Kontextauswertungen eindeutig bevorzugen. Verschiedene Wege desselben Modus sind damit nicht gegeneinander bewertet.':'The highlighted mode is the clear leader in every available context assessment. Different paths for the same mode are not ranked against each other by this rule.'}}</p>
    <p v-else-if="state!=='clear'"> {{de?'Bei Gleichstand, unterschiedlichen Ergebnissen oder fehlenden Auswertungen wird keine Empfehlung behauptet. Die Reihenfolge folgt dann der Routensuche und ist keine Rangliste von HOTCO.':'Ties, differing results or missing assessments do not produce a recommendation. The order then follows route search, not a HOTCO ranking.'}}</p>
    <p v-if="explanation.unknown.length">{{de?'Nicht bewertet:':'Not assessed:'}} {{explanation.unknown.map(n=>conditionLabels[n]).join(', ')}}.</p>
    <p v-if="explanation.supplemental">{{de?'Der Umweltkontext gehört zum ergänzenden GraphHopper-Weg auf der Karte. Seine Identität mit der ursprünglichen Route ist nicht bestätigt.':'Environmental context belongs to the supplemental GraphHopper path on the map. Its identity with the original itinerary is unverified.'}} {{number(card.candidate?.route_context?.total_duration_seconds/60,1)}} min · {{number(card.candidate?.route_context?.total_distance_meters/1000,2)}} km.</p>
    <p v-if="explanation.measurementAgeUnknown">{{de?'Die Aktualität einiger Messungen ist nicht verifiziert.':'The freshness of some readings is unverified.'}}</p>
    <details class="route-details"><summary>{{de?'Technische Details':'Technical details'}}</summary>
     <ul class="route-readings"><li v-for="(o,i) in environmentalRows(card.candidate)" :key="i"><span>{{o.variable}}</span><strong>{{number(o.numeric_value)}} {{o.unit||''}}</strong><small>{{o.source_entity_id}} · {{o.timestamp||(de?'Messzeit unbekannt':'Measurement time unknown')}}<template v-if="o.source_metadata?.normalization_eligible===false"> · {{de?'Nicht für Berechnung geeignet':'Ineligible for calculation'}}</template></small></li></ul>
     <div class="route-table-scroll"><table class="route-comparison"><caption>{{de?'Modellaktivierung ohne / mit Kontext':'Model activation without / with context'}}</caption><thead><tr><th>{{de?'Modus':'Mode'}}</th><th>{{de?'Ohne':'Without'}}</th><th>{{de?'Mit':'With'}}</th><th>Δ</th></tr></thead><tbody><tr v-for="key in ['car','bike','pt','walk']" :key="key"><th>{{labels.modes[key]}}</th><td>{{number(result.contextual_deliberation?.baseline?.final_action_activations?.[key],3)}}</td><td>{{number(card.candidate?.hotco?.final_action_activations?.[key],3)}}</td><td>{{number(card.candidate?.difference_from_baseline?.final_action_activations?.[key],3)}}</td></tr></tbody></table></div>
     <p>{{de?'Modellaktivierungen sind keine gemessenen Wahlwahrscheinlichkeiten. Fehlende Daten werden nicht durch Null ersetzt.':'Model activations are not measured choice probabilities. Missing data are not replaced with zero.'}}</p>
    </details>
   </details>
  </template>
 </article>
</template>

<style scoped>.journey-card:not(.journey-card--primary) .journey-card-label{color:#d0e8fc}.journey-card .journey-why p{font-family:inherit;font-size:10px;line-height:2}</style>
