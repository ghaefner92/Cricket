<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import CompanionAvatar from './CompanionAvatar.vue'
import AvatarReaction from './AvatarReaction.vue'
import ChoiceReflection from './ChoiceReflection.vue'
import {confirmedSnapshot,requestReflection,validReflection} from '../services/choiceReflection.js'
import RecentPlaces from './RecentPlaces.vue'
import AddressSearch from './AddressSearch.vue'
import JourneyControlIcon from './JourneyControlIcon.vue'
const openJourneyPanel=ref(null)
function toggleJourneyPanel(panel){openJourneyPanel.value=openJourneyPanel.value===panel?null:panel}
import RouteMap from './RouteMap.vue'
import { readCompanion } from '../stores/companion.js'
import {readActivePassport} from '../services/passportLifecycle.js'
import {beginSearch,finishSearch,failSearch,addChoice,getSearch,addExplanation} from '../services/simulationHistory.js'
import {readSettings,effectiveAvailability} from '../services/appSettings.js'
import { routePayload, searchRoutes } from '../services/routePlanning.js'
import RouteRecommendations from './RouteRecommendations.vue'
import PixelJourneyIcon from './PixelJourneyIcon.vue'
import {recommendation} from '../services/routePresentation.js'
import {recommendationMessage} from '../services/recommendationMessaging.js'
import {recentPlaces,rememberPlaces} from '../services/journeyExperience.js'
import {beliefMessages} from '../i18n/beliefs.js'
import {modelMode} from '../services/routeExplanation.js'
import '../styles/journey-experience.css'
import '../styles/routes.css'
import '../styles/journey-game.css'
const props = defineProps({locale:{default:'en'},passport:{type:Object,required:true},fingerprint:{type:String,required:true}})
const emit = defineEmits(['back','locale-change'])
let storage; try { storage = localStorage } catch { storage = {getItem(){return null}} }
const companion = ref(readCompanion(storage,{confirmed:true})),preferences=ref(readSettings(storage))
function refreshPreferences(){preferences.value=readSettings(storage);companion.value=readCompanion(storage,{confirmed:true})}
onMounted(()=>window.addEventListener('cricket-settings-change',refreshPreferences))
onUnmounted(()=>window.removeEventListener('cricket-settings-change',refreshPreferences))
const de = computed(() => props.locale === 'de')
const form = reactive({maxWalk:preferences.value.maxWalk})
const origin=ref(null),destination=ref(null),selected=ref(null)
watch([origin,destination,()=>form.maxWalk],()=>{result.value=null;selected.value=null;chosen.value=null;savedChoice.value=null;saved.value=false;error.value=''})
const historyWarning=ref(''),searchId=ref(null),choosing=ref(false)
const busy = ref(false), result = ref(null), error = ref(''), resultHeading = ref(null)
function refreshRecents(){recent.value=recentPlaces(storage)}
onMounted(()=>window.addEventListener('cricket-recents-change',refreshRecents))
onUnmounted(()=>window.removeEventListener('cricket-recents-change',refreshRecents))
const recent=ref(recentPlaces(storage)),recentTarget=ref('origin'),chosen=ref(null),savedChoice=ref(null),saved=ref(false),confirmation=ref(null),view=ref('cards'),mobile=ref(false)
let media
function updateMobile(){mobile.value=media.matches}
onMounted(()=>{media=window.matchMedia('(max-width: 720px)');updateMobile();media.addEventListener('change',updateMobile)})
onUnmounted(()=>media?.removeEventListener('change',updateMobile))
const cardsVisible=computed(()=>true)
const mapVisible=computed(()=>!mobile.value||view.value==='map')
function swap(){const start=origin.value;origin.value=destination.value;destination.value=start}
function useRecent(place){if(recentTarget.value==='origin')origin.value=place;else destination.value=place}
function clearRecent(){try{storage.removeItem('cricket.recent-places.v1')}catch{};recent.value=[]}
function modeLabel(key){return (beliefMessages[props.locale]||beliefMessages.en).modes[modelMode(key)]||key}
async function chooseJourney(card){
 if(choosing.value)return
 try{checkPassport()}catch{error.value='changed';return}
 choosing.value=true
 try{
  saved.value=false;chosen.value=null;reflectionHandle.value=null;historyWarning.value=''
  if(!searchId.value){historyWarning.value='choice';return}
  let event
  try{event=await addChoice(searchId.value,card.route);saved.value=true}catch{historyWarning.value='choice';return}
  reflectionHandle.value={search_id:searchId.value,index:event.choice_index}
  chosen.value=card;savedChoice.value=card;selected.value=`${card.route.rank}-${card.route.mode_key}`
  await nextTick();companionPanel.value?.focus();companionPanel.value?.scrollIntoView({block:'start',behavior:reducedMotion()?'instant':'smooth'})
 }finally{choosing.value=false}
}

const companionPanel=ref(null)
const reflectionHandle=ref(null),reflection=ref(null),reflectionBusy=ref(false),reflectionError=ref(false)
let reflectionController,reflectionVersion=0
function clearReflection(){reflectionVersion++;reflectionController?.abort();reflection.value=null;reflectionBusy.value=false;reflectionError.value=false}
async function loadReflection(force=false){
 clearReflection()
 const handle=reflectionHandle.value
 if(!handle||!chosen.value||!saved.value)return
 const version=reflectionVersion,language=props.locale==='de'?'de':'en'
 reflectionController=new AbortController();const signal=reflectionController.signal
 reflectionBusy.value=true
 try{
  const record=await getSearch(handle.search_id)
  if(!record)throw Error('reflection')
  const existing=!force&&record.explanations?.findLast(e=>e.choice_index===handle.index&&e.language===language)
  let response=existing?.response
  if(!response){response=await requestReflection(record,handle.index,confirmedSnapshot(storage,record),language,signal)
   if(signal.aborted||version!==reflectionVersion)return
   await addExplanation(handle.search_id,handle.index,response)
  }
  if(!validReflection(response,record,handle.index,language))throw Error('reflection')
  if(version===reflectionVersion&&!signal.aborted)reflection.value=response
 }catch{if(version===reflectionVersion&&!signal.aborted)reflectionError.value=true}
 finally{if(version===reflectionVersion)reflectionBusy.value=false}
}
watch([reflectionHandle,chosen,()=>props.locale],()=>{if(chosen.value&&reflectionHandle.value)loadReflection();else clearReflection()})
onUnmounted(clearReflection)

const paused=ref(false),hidden=ref(false)
const motionPaused=computed(()=>paused.value||hidden.value||preferences.value.motion!=='full')
function visibility(){hidden.value=document.hidden}
onMounted(()=>{visibility();document.addEventListener('visibilitychange',visibility)})
onUnmounted(()=>document.removeEventListener('visibilitychange',visibility))
const mapPanel=ref(null)
function reducedMotion(){return motionPaused.value||window.matchMedia?.('(prefers-reduced-motion: reduce)').matches}
async function selectFromMap(id){
 selected.value=id
 if(!id)return
 await nextTick()
 const card=document.getElementById(`route-card-${id}`)
 const disclosure=card?.closest('.journey-alternative')
 if(disclosure)disclosure.open=true
 card?.scrollIntoView({behavior:reducedMotion()?'instant':'smooth',block:'nearest'})
 card?.querySelector('h3')?.focus({preventScroll:true})
}
async function selectFromCard(id){
 selected.value=id
 await nextTick()
 if(selected.value){mapPanel.value?.$el?.scrollIntoView({behavior:reducedMotion()?'instant':'smooth',block:'start'});mapPanel.value?.focusMap()}
}
let controller, alive = true
function checkPassport() { try { if(readActivePassport(storage)?.fingerprint !== props.fingerprint) throw Error('changed') } catch { throw Error('changed') } }
async function search() {
 if(locating.value||busy.value||choosing.value)return
 error.value='';historyWarning.value='';searchId.value=null;result.value=null;selected.value=null;chosen.value=null;savedChoice.value=null;saved.value=false
 try {
  checkPassport()
  if(!origin.value||!destination.value)throw Error('addresses')
  const payload=routePayload(props.passport,{...form,availability:effectiveAvailability(readSettings(storage),props.passport),startLat:origin.value.lat,startLon:origin.value.lon,stopLat:destination.value.lat,stopLon:destination.value.lon})
  controller=new AbortController();busy.value=true
  try{const active=readActivePassport(storage);await beginSearch(payload,{origin:origin.value,destination:destination.value},{passport_id:active?.response.cognitive_passport.lineage.passport_id,revision:active?.response.cognitive_passport.lineage.revision,fingerprint:props.fingerprint,confirmed_at:active?.confirmedAt||active?.createdAt},active);searchId.value=payload.search_id}catch{historyWarning.value='search';throw Error('history')}
  const data=await searchRoutes(payload,controller.signal)
  const presentation=recommendation(data)
  try{await finishSearch(searchId.value,data,{state:presentation.state,winner:presentation.winner,primary_route_id:presentation.primary?.audit?.route_id||null})}catch{historyWarning.value='search';throw Error('history')}
  if(!alive)return
  if(controller.signal.aborted)throw Error('cancelled')
  checkPassport();result.value=data;view.value='cards';recent.value=rememberPlaces(storage,[origin.value,destination.value])
  if(presentation.state==='clear')selected.value=`${presentation.primary.route.rank}-${presentation.primary.route.mode_key}`
  await nextTick(); resultHeading.value?.focus()
 }catch(e){if(searchId.value){try{await failSearch(searchId.value,controller?.signal.aborted?'cancelled':e.message||'network')}catch{historyWarning.value='search'}}if(alive)error.value=controller?.signal.aborted?'cancelled':['availability','addresses','coordinates','walking','changed','passport','timeout','unavailable','response','request','history','cancelled'].includes(e.message)?e.message:'network'}
 finally{if(alive)busy.value=false}
}
function cancel(){controller?.abort()}
onUnmounted(()=>{alive=false;controller?.abort()})
const errorText=computed(()=>({
 availability:de.value?'Bitte verfügbare Verkehrsmittel in den Einstellungen prüfen.':'Check available transport in Settings.',
 history:de.value?'Der Verlauf konnte nicht gespeichert werden. Die Suche wird nicht fortgesetzt.':'History could not be saved. The search cannot continue.',
 addresses:de.value?'Bitte Start und Ziel suchen und jeweils einen Ort auswählen.':'Search for origin and destination and select a place for each.',
 coordinates:de.value?'Bitte gültige Koordinaten eingeben.':'Enter valid coordinates.',
 walking:de.value?'Die Gehstrecke muss zwischen 0 und 10.000 Metern liegen.':'Walking limit must be between 0 and 10,000 metres.',
 changed:de.value?'Deine Antworten haben sich geändert. Kehre zum Passport zurück.':'Your answers changed. Return to your passport.',
 passport:de.value?'Kehre zum Passport zurück und überprüfe dein Profil.':'Return to your passport and review your profile.',
 timeout:de.value?'Die Suche dauert zu lange. Bitte erneut versuchen.':'The search took too long. Please try again.',
 unavailable:de.value?'Der Routendienst ist derzeit nicht erreichbar.':'The route service is currently unavailable.',
 cancelled:de.value?'Suche abgebrochen.':'Search cancelled.',
 network:de.value?'Verbindung fehlgeschlagen. Prüfe, ob der Backend-Server läuft.':'Connection failed. Check that the backend server is running.',
 response:de.value?'Die Antwort konnte nicht gelesen werden. Bitte erneut versuchen.':'The response could not be read. Please try again.',
 request:de.value?'Die Suche konnte nicht verarbeitet werden. Bitte Eingaben überprüfen.':'The search could not be processed. Please review your inputs.'
}[error.value]))
const locating = ref(false), locationError = ref('')
let locationRequest = 0, locationMounted = true

function cancelLocation() {
 locationRequest++
 locating.value = false
}

onUnmounted(() => {
 locationMounted = false
 cancelLocation()
})

const locationErrorText = computed(() => ({
 secure: de.value
  ? 'Standort benötigt HTTPS oder localhost.'
  : 'Location requires HTTPS or localhost.',
 unsupported: de.value
  ? 'Dieser Browser unterstützt keinen Standortzugriff.'
  : 'This browser does not support location access.',
 denied: de.value
  ? 'Standortzugriff verweigert. Berechtigung im Browser prüfen.'
  : 'Location permission denied. Check browser permissions.',
 unavailable: de.value
  ? 'Standort nicht verfügbar. Standortdienste prüfen oder Adresse eingeben.'
  : 'Location unavailable. Check location services or enter an address.',
 timeout: de.value
  ? 'Standortsuche dauert zu lange. Bitte erneut versuchen.'
  : 'Location took too long. Please try again.',
 changed: de.value
  ? 'Start oder Ziel geändert. Bitte Standort erneut anfordern.'
  : 'Origin or destination changed. Please request location again.'
}[locationError.value] || ''))

function useMyLocation() {
 if (locating.value || busy.value || choosing.value) return
 locationError.value = ''

 if (!window.isSecureContext) {
  locationError.value = 'secure'
  return
 }

 if (!navigator.geolocation) {
  locationError.value = 'unsupported'
  return
 }

 locating.value = true
 const request = ++locationRequest
 const startOrigin = origin.value
 const startDestination = destination.value

 const current = () =>
  locationMounted && request === locationRequest

 const fail = code => {
  if (current()) {
   locating.value = false
   locationError.value = code
  }
 }

 try {
  navigator.geolocation.getCurrentPosition(position => {
   if (!current()) return

   if (
    busy.value || choosing.value ||
    origin.value !== startOrigin ||
    destination.value !== startDestination
   ) {
    fail('changed')
    return
   }

   const lat = position.coords.latitude
   const lon = position.coords.longitude

   if (
    !Number.isFinite(lat) || !Number.isFinite(lon) ||
    Math.abs(lat) > 90 || Math.abs(lon) > 180
   ) {
    fail('unavailable')
    return
   }

   const accuracy =
    Number.isFinite(position.coords.accuracy) &&
    position.coords.accuracy >= 0
     ? position.coords.accuracy
     : null

   const label = de.value
    ? 'Mein aktueller Standort'
    : 'My current location'

   locating.value = false
   origin.value = {
    lat,
    lon,
    label,
    id: `geo-${request}-${Date.now()}`,
    source: 'geolocation',
    accuracy_meters: accuracy,
    observed_at: Number.isFinite(position.timestamp)
     ? new Date(position.timestamp).toISOString()
     : null,
    houseNumber: '',
    street: '',
    postcode: '',
    detail: accuracy === null
     ? ''
     : `${Math.ceil(accuracy)} m`
   }
  }, failure => {
   fail(
    failure.code === 1 ? 'denied' :
    failure.code === 3 ? 'timeout' :
    'unavailable'
   )
  }, {
   enableHighAccuracy: true,
   maximumAge: 0,
   timeout: 20000
  })
 } catch (failure) {
  fail(
   failure.name === 'SecurityError' ||
   failure.name === 'NotAllowedError'
    ? 'denied'
    : 'unavailable'
  )
 }
}
</script>
<template>
 <main class="route-page journey-game" :class="{'journey-motion-paused':motionPaused}" :lang="locale">
  <header class="route-topbar"><button type="button" class="nes-btn" :disabled="locating||busy||choosing" @click="emit('back')">← {{de?'Startseite':'Home'}}</button></header>
  <h1 tabindex="-1">{{de?'Deine nächste Reise':'Your next journey'}}</h1>
  <div v-if="!result&&!chosen" class="journey-search-intro">
   <AvatarReaction dialogue :companion="companion" :paused="true" :locale="locale"/>
   <p>{{de?'Wohin geht unsere Reise? Wähle Start und Ziel.':'Where are we going? Choose your origin and destination.'}}</p>
  </div>
  <RouteMap :chosen-id="savedChoice?`${savedChoice.route.rank}-${savedChoice.route.mode_key}`:null" ref="mapPanel" :origin="origin" :destination="destination" :result="result" :locale="locale" :selected="selected" @select="selectFromMap">
   <template #journey-search>
    <form class="journey-compact-form" @submit.prevent="search">
     <div class="journey-endpoints">
      <div class="journey-endpoint"><span class="journey-endpoint-badge badge-origin" aria-hidden="true">A</span><AddressSearch v-model="origin" compact :bias="origin?.source=='geolocation'?origin:null" id="route-origin" :locale="locale" :label="de?'Start auswählen':'Choose origin'" :disabled="locating||busy||choosing"/></div>
      <div class="journey-endpoint"><span class="journey-endpoint-badge badge-destination" aria-hidden="true">B</span><AddressSearch v-model="destination" compact :bias="origin?.source=='geolocation'?origin:null" id="route-destination" :locale="locale" :label="de?'Ziel auswählen':'Choose destination'" :disabled="locating||busy||choosing"/></div>
     </div>
     <div class="journey-search-actions">
      <div class="journey-icon-tools" role="group" :aria-label="de?'Reiseoptionen':'Journey options'">
       <button type="button" class="nes-btn journey-icon-button journey-gps" :class="{'journey-gps--locating':locating}" :disabled="busy||choosing" :aria-busy="locating" :aria-label="locating?(de?'Standortsuche abbrechen':'Cancel location search'):(de?'Meinen Standort verwenden':'Use my location')" :title="locating?(de?'Standortsuche abbrechen':'Cancel location search'):(de?'Meinen Standort verwenden':'Use my location')" @click="locating?cancelLocation():useMyLocation()"><JourneyControlIcon kind="gps"/></button>
       <button type="button" class="nes-btn journey-icon-button" :disabled="locating||busy||choosing||(!origin&&!destination)" :aria-label="de?'Start und Ziel tauschen':'Swap origin and destination'" :title="de?'Start und Ziel tauschen':'Swap origin and destination'" @click="swap"><JourneyControlIcon kind="swap"/></button>
       <button type="button" class="nes-btn journey-icon-button journey-history" :disabled="locating||busy||choosing" :aria-label="de?'Zuletzt verwendete Orte':'Recent places'" :title="de?'Zuletzt verwendete Orte':'Recent places'" :aria-expanded="openJourneyPanel==='recent'" aria-controls="journey-recent-panel" @click="toggleJourneyPanel('recent')"><JourneyControlIcon kind="history"/></button>
       <button type="button" class="nes-btn journey-icon-button journey-personalization" :disabled="locating||busy||choosing" :aria-label="de?'Reisepräferenzen':'Journey preferences'" :title="de?'Reisepräferenzen':'Journey preferences'" :aria-expanded="openJourneyPanel==='preferences'" aria-controls="journey-preferences-panel" @click="toggleJourneyPanel('preferences')"><JourneyControlIcon kind="settings"/></button>
      </div>
      <button type="submit" class="nes-btn is-primary journey-find-routes" :disabled="locating||busy||choosing||!origin||!destination">{{busy?(de?'Wird gesucht…':'Searching…'):(de?'Routen suchen':'Find routes')}} →</button>
     </div>
     <p v-if="locating" class="route-note" role="status">{{de?'Standort wird gesucht…':'Locating…'}}</p>
     <p v-if="locationError" class="address-error" role="alert">{{locationErrorText}}</p>
     <p v-if="origin?.source==='geolocation'" class="route-note" role="status">{{de?'Standort als Start gesetzt.':'Location set as origin.'}} {{Number.isFinite(origin.accuracy_meters)?(de?'Genauigkeit: ca. ':'Accuracy: about ')+Math.ceil(origin.accuracy_meters)+' m.':''}} {{origin.accuracy_meters>100?(de?'Ungenauer Standort: Start auf der Karte prüfen.':'Approximate location: check the origin on the map.'):''}}</p>
     <div v-if="openJourneyPanel==='recent'" id="journey-recent-panel" class="journey-search-drawer" :aria-label="de?'Zuletzt verwendete Orte':'Recent places'"><RecentPlaces embedded :places="recent" :locale="locale" :target="recentTarget" :disabled="locating||busy||choosing" @target-change="recentTarget=$event" @select="useRecent" @clear="clearRecent"/><p v-if="!recent.length" class="route-note">{{de?'Noch keine gespeicherten Orte.':'No recent places yet.'}}</p></div>
     <div v-if="openJourneyPanel==='preferences'" id="journey-preferences-panel" class="journey-search-drawer"><label class="route-walk-limit">{{de?'Maximale Gehstrecke (Meter)':'Maximum walking distance (metres)'}}<input v-model="form.maxWalk" type="number" min="0" max="10000" step="1" class="nes-input" :disabled="locating||busy||choosing" required/></label><p class="route-note">{{de?'Das Limit gilt für den Routendienst. Reine Fußwege können länger sein.':'This limit is sent to the route service. Walking-only paths may be longer.'}}</p></div>
    </form>
   </template>
  </RouteMap>
  <section v-if="saved&&chosen" class="nes-container route-companion journey-quest" ref="companionPanel" tabindex="-1"><div class="journey-quest-sky" aria-hidden="true"><span class="journey-cloud"></span><span class="journey-cloud journey-cloud--second"></span><span class="journey-city"></span></div><ChoiceReflection :choice-card="savedChoice" embedded :companion="companion" :locale="locale" :reflection="reflection" :busy="reflectionBusy" :error="reflectionError" :paused="motionPaused" @retry="loadReflection(true)"/></section>
  <div v-if="busy" class="nes-container route-status" role="status"><PixelJourneyIcon kind="clock" :animated="!motionPaused"/><p>{{de?'Routen werden gesucht und verfügbare Kontextdaten geprüft…':'Finding routes and checking available context…'}}</p><button type="button" class="nes-btn" @click="cancel">{{de?'Abbrechen':'Cancel'}}</button></div>
  <p v-if="historyWarning" class="nes-container route-error" role="alert">{{historyWarning==='choice'?(de?'Die Auswahl konnte nicht im Verlauf gespeichert werden. Bitte erneut wählen.':'Your choice could not be saved in history. Please choose again.'):(de?'Diese Suche konnte nicht vollständig gespeichert werden. Prüfe den Browserspeicher und versuche es erneut.':'This search could not be fully saved. Check browser storage and try again.')}}</p>
  <p v-if="error" class="nes-container route-error" role="alert">{{errorText}}</p>
  <section v-if="result" class="route-results" aria-labelledby="route-results-heading">
   <h2 id="route-results-heading" ref="resultHeading" tabindex="-1">{{de?'Deine Möglichkeiten':'Your options'}}</h2>
   <p v-if="result.journey_availability" class="route-note">{{de?'Verfügbar in dieser Simulation:':'Available in this simulation:'}} {{Object.entries(result.journey_availability).filter(([,available])=>available).map(([mode])=>modeLabel(mode)).join(' · ')}}</p>
   <RouteRecommendations :chosen-id="savedChoice?`${savedChoice.route.rank}-${savedChoice.route.mode_key}`:null" :cards-visible="cardsVisible" :result="result" :passport="passport" :companion="companion" :locale="locale" :selected="selected" :paused="true" @select="selectFromCard" @choose="chooseJourney">
    
   </RouteRecommendations>
  </section>
  <section v-if="chosen" class="nes-container journey-confirmation" ref="confirmation" tabindex="-1" aria-labelledby="journey-confirmed-heading">
   <h2 id="journey-confirmed-heading">{{de?'Dein gewählter Weg':'Your chosen journey'}}</h2>
   <p><strong>{{modeLabel(chosen.route.mode_key)}}</strong> · {{Number.isFinite(chosen.route.summary?.duration_seconds)?new Intl.NumberFormat(locale,{maximumFractionDigits:1}).format(chosen.route.summary.duration_seconds/60)+' min':'—'}} · {{Number.isFinite(chosen.route.summary?.distance_meters)?new Intl.NumberFormat(locale,{maximumFractionDigits:2}).format(chosen.route.summary.distance_meters/1000)+' km':'—'}}</p>
   <dl><dt>{{de?'Start':'From'}}</dt><dd>{{origin.label}}</dd><dt>{{de?'Ziel':'To'}}</dt><dd>{{destination.label}}</dd></dl>
   <p class="route-note">{{saved?(de?'Auswahl mit dieser Simulation im Verlauf gespeichert.':'Choice saved with this simulation in your history.'):(de?'Auswahl konnte nicht gespeichert werden. Sie gilt für diese Sitzung.':'Choice could not be saved. It remains selected for this session.')}}</p>
   <button type="button" class="nes-btn" @click="chosen=null">{{de?'Weiter vergleichen':'Keep comparing'}}</button>
  </section>
 </main>
</template>

<style>
.journey-game .journey-quest{display:block}.journey-idle-dialogue{display:flex;align-items:center;gap:20px}.journey-idle-dialogue>div:last-child{min-width:0}.journey-quest:focus-visible{outline:3px solid #ffdb79;outline-offset:4px}

.journey-geolocation {
 grid-column: 1/-1;
 display: flex;
 flex-wrap: wrap;
 align-items: center;
 gap: 12px;
 margin: 8px 0 12px;
}
.journey-geolocation p {
 width: 100%;
 margin: 0;
}
.journey-geolocation button {
 font-size: 10px;
}
@media(max-width:600px){.journey-idle-dialogue{flex-wrap:wrap;gap:16px}.journey-idle-dialogue>div:last-child{flex-basis:100%}}
.journey-search-intro{display:flex;align-items:center;gap:14px;margin:0 0 16px;min-width:0}.journey-search-intro .avatar-reaction{--dialogue-portrait-size:56px;flex:0 0 56px}.journey-search-intro p{margin:0;min-width:0}
.journey-game .journey-cloud{animation:none!important}.journey-game .journey-card{animation:none!important}
@media(max-width:600px){.journey-search-intro .avatar-reaction{--dialogue-portrait-size:48px;flex-basis:48px}}
</style>
<style scoped>
html body #app .journey-compact-form{margin:12px 0 14px;padding:12px;background:#15213c;border:0;font-family:inherit}
.journey-endpoints{display:grid;gap:10px}.journey-endpoint{display:grid;grid-template-columns:24px minmax(0,1fr);gap:10px;align-items:start;min-width:0}
.journey-endpoint-badge{display:grid;place-items:center;min-height:44px;border:2px solid currentColor;font-size:10px}.badge-origin{color:#a6e6c5}.badge-destination{color:#ffda83}
.journey-search-actions{display:flex;flex-wrap:wrap;justify-content:space-between;align-items:center;gap:12px;margin-top:12px}.journey-icon-tools{display:flex;gap:8px}
html body #app .journey-icon-button{width:44px;height:44px;min-height:44px;min-width:44px;display:grid;place-items:center;padding:8px;margin:0;background:#1c2c49;color:#cbdcf6;border:2px solid #819bc5;box-shadow:2px 2px #090f22}
html body #app .journey-gps{color:#a6e6c5}html body #app .journey-history{color:#aecdff}html body #app .journey-personalization{color:#d0b4ff}
html body #app .journey-icon-button[aria-expanded=true]{background:#35476c;border-color:#ffda83}
html body #app .journey-find-routes{min-height:44px;padding:10px;font-size:10px;margin:0;flex:1;max-width:210px;line-height:1.8}
.journey-search-drawer{margin-top:14px;padding:12px;border-top:2px solid #6d87ae;background:#1b2947}.journey-search-drawer :deep(.recent-panel){margin:0;padding:0;border:0}
.journey-compact-form>.route-note{font-size:9px;line-height:1.8;margin:8px 0 0}.journey-gps--locating :deep(svg){animation:journey-gps-pulse .8s steps(2,end) infinite}
@keyframes journey-gps-pulse{50%{opacity:.35}}.journey-motion-paused .journey-gps--locating :deep(svg){animation:none}
.journey-icon-button:focus-visible{outline:3px solid #ffda83;outline-offset:4px}@media(max-width:480px){html body #app .journey-compact-form{padding:10px}.journey-endpoint{gap:6px;grid-template-columns:22px minmax(0,1fr)}.journey-search-actions{gap:10px}html body #app .journey-find-routes{max-width:none;flex-basis:100%}}
@media(prefers-reduced-motion:reduce){.journey-gps--locating :deep(svg){animation:none}}
@media(max-width:600px){.journey-idle-dialogue{flex-wrap:wrap;gap:16px}.journey-idle-dialogue>div:last-child{flex-basis:100%}}
</style>
