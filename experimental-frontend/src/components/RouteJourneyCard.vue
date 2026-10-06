<script setup>
import {computed} from 'vue'
import CompanionAvatar from './CompanionAvatar.vue'
import PixelJourneyIcon from './PixelJourneyIcon.vue'
import WeatherInventory from './WeatherInventory.vue'
import {beliefMessages} from '../i18n/beliefs.js'
import {goalLinks} from '../services/routePresentation.js'
import {explainRoute,modelMode} from '../services/routeExplanation.js'
import {environmentalRows,finiteNumber} from '../services/routePlanning.js'
const props=defineProps({card:{required:true},passport:{required:true},result:{required:true},companion:{required:true},locale:{default:'en'},primary:{type:Boolean,default:false},state:{default:'uncertain'},selected:{default:null},paused:{default:false}})
const emit=defineEmits(['select'])
const de=computed(()=>props.locale==='de'),labels=computed(()=>beliefMessages[props.locale]||beliefMessages.en)
const id=computed(()=>`${props.card.route.rank}-${props.card.route.mode_key}`)
const mode=computed(()=>labels.value.modes[modelMode(props.card.route.mode_key)]||props.card.route.mode_key)
const explanation=computed(()=>explainRoute(props.card)),goals=computed(()=>goalLinks(props.passport,props.card.route.mode_key))
const conditionLabels=computed(()=>de.value?{rain:'Regen',wind:'Wind',heat:'Hitze',cold:'Kälte',darkness:'Dunkelheit',traffic:'Verkehr',crowding:'Gedränge'}:{rain:'rain',wind:'wind',heat:'heat',cold:'cold',darkness:'darkness',traffic:'traffic',crowding:'crowding'})
function number(v,d=2){return finiteNumber(v)?new Intl.NumberFormat(de.value?'de-DE':'en-GB',{maximumFractionDigits:d}).format(v):'—'}
const title=computed(()=>props.primary?(props.state==='clear'?(de.value?'Dein empfohlener Modus':'Your recommended mode'):props.state==='similar'?(de.value?'Ähnlich passende Möglichkeiten':'Similarly fitting options'):props.state==='disagreement'?(de.value?'Der Kontext macht den Unterschied':'Context makes a difference'):(de.value?'Entdecke deine Möglichkeiten':'Explore your options')):(de.value?'Weitere Möglichkeit':'Another option'))
</script>
<template>
 <article :id="`route-card-${id}`" class="nes-container route-card journey-card" :class="{'journey-card--primary':primary,'route-card--selected':selected===id,'route-card--unavailable':card.route.available===false}">
  <div class="journey-card-label"><PixelJourneyIcon :kind="primary?'star':modelMode(card.route.mode_key)" :animated="!paused"/><span>{{title}}</span><span v-if="primary&&state==='clear'" class="journey-status">{{de?'Modell':'Model'}}</span></div>
  <header class="route-card-heading"><h3 tabindex="-1"><PixelJourneyIcon :kind="modelMode(card.route.mode_key)" :animated="!paused"/>{{mode}}</h3><span class="route-badge">{{card.route.available===false?(de?'Nicht verfügbar':'Unavailable'):(de?'Verfügbar':'Available')}}</span></header>
  <div class="journey-metrics"><span><PixelJourneyIcon kind="clock" :animated="!paused"/>{{number(finiteNumber(card.route.summary?.duration_seconds)?card.route.summary.duration_seconds/60:null,1)}} min</span><span>{{number(finiteNumber(card.route.summary?.distance_meters)?card.route.summary.distance_meters/1000:null,2)}} km</span></div>
  <p v-if="card.route.available===false">{{de?'Mit deiner angegebenen Ausstattung nicht verfügbar.':'Unavailable with your declared travel kit.'}}</p>
  <template v-else>
   <div class="route-avatar-explanation journey-dialogue">
    <CompanionAvatar :appearance="companion.appearance" :palette="companion.palette" :animated="!paused"/>
    <div class="nes-balloon from-left"><h4>{{companion.name}}</h4>
     <p v-if="primary&&state==='clear'">{{de?'Die eindeutigen Auswertungen der verfügbaren Routenkontexte zeigen auf denselben Modus.':'The clear assessments of the available route contexts point to the same mode.'}}</p>
     <p v-else-if="primary">{{de?'Es gibt noch keine eindeutige Empfehlung. Vergleiche die Möglichkeiten und entscheide, was für diese Reise zählt.':'There is no clear recommendation yet. Compare the options and choose what matters for this journey.'}}</p>
     <p v-if="goals.length">{{de?'Dein Passport verbindet diesen Modus positiv mit diesen Wochenprioritäten:':'Your passport links this mode positively with these weekly priorities:'}} {{goals.map(g=>labels.needs[g.name]||g.name).join(', ')}}.</p>
     <p v-else>{{de?'Für diesen Modus sind keine positiven Verbindungen zu deinen aktiven Wochenprioritäten verfügbar.':'No positive links to your active weekly priorities are available for this mode.'}}</p>
     <template v-if="explanation.available">
      <p v-if="explanation.changes.length">{{de?'Der verfügbare Kontext verändert diese Bedürfnisse:':'The available context changes these needs:'}} {{explanation.changes.map(([n])=>labels.needs[n]||n).join(', ')}}.</p>
      <p v-else>{{de?'Der verfügbare Kontext erzeugt im Modell keine zusätzliche Bedürfnisaktivierung.':'The available context adds no need activation in the model.'}}</p>
      <p v-if="explanation.direction==='up'">{{de?'Mit diesem Kontext steigt die Aktivierung dieses Modus gegenüber der Auswertung ohne Kontext.':'With this context, activation for this mode rises compared with the assessment without context.'}}</p>
      <p v-if="explanation.direction==='down'">{{de?'Mit diesem Kontext sinkt die Aktivierung dieses Modus gegenüber der Auswertung ohne Kontext.':'With this context, activation for this mode falls compared with the assessment without context.'}}</p>
     </template>
     <p v-else>{{de?'Für diesen Weg fehlt die Kontextauswertung.':'Context assessment is unavailable for this path.'}}</p>
    </div>
   </div>
   <ul v-if="goals.length" class="journey-goals" :aria-label="de?'Verbundene Wochenprioritäten':'Linked weekly priorities'"><li v-for="g in goals" :key="g.name"><PixelJourneyIcon kind="star" :animated="false"/>{{labels.needs[g.name]||g.name}}</li></ul>
   <h4>{{de?'Dein Umweltinventar':'Your environment inventory'}}</h4>
   <WeatherInventory :candidate="card.candidate" :locale="locale" :animated="!paused"/>
   <div class="journey-card-actions"><button v-if="card.audit?.context_ready" type="button" class="nes-btn" :class="{'is-primary':selected!==id}" :aria-pressed="selected===id" @click="emit('select',id)">{{selected===id?(de?'Auswahl aufheben':'Clear selection'):(de?'Auf Karte ansehen':'View on map')}}</button></div>
   <details class="route-details journey-why"><summary>{{de?'Warum diese Einordnung?':'Why this assessment?'}}</summary>
    <p>{{de?'Das Modell berücksichtigt deine Wochenprioritäten, deine Verbindungen zwischen Zielen und Verkehrsmitteln, deine Gefühle und die Verfügbarkeit. Die oben genannten Verbindungen beschreiben dein Profil; sie sind keine isolierten Ursachen der Empfehlung.':'The model uses your weekly priorities, goal–mode connections, feelings and availability. The links shown above describe your profile; they are not isolated causes of the recommendation.'}}</p>
    <p v-if="primary&&state==='clear'">{{de?'Hervorgehoben wird der Modus, den alle verfügbaren Kontextauswertungen eindeutig bevorzugen. Verschiedene Wege desselben Modus sind damit nicht gegeneinander bewertet.':'The highlighted mode is the clear leader in every available context assessment. Different paths for the same mode are not ranked against each other by this rule.'}}</p>
    <p v-else-if="primary">{{de?'Bei Gleichstand, unterschiedlichen Ergebnissen oder fehlenden Auswertungen wird keine Empfehlung behauptet. Die erste verfügbare Route eröffnet den Vergleich.':'Ties, differing results or missing assessments do not produce a recommendation. The first available route starts the comparison.'}}</p>
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
