<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import CompanionAvatar from './CompanionAvatar.vue'
import AddressSearch from './AddressSearch.vue'
import RouteMap from './RouteMap.vue'
import { readCompanion } from '../stores/companion.js'
import { passportSource } from '../services/passport.js'
import { routePayload, searchRoutes } from '../services/routePlanning.js'
import RouteRecommendations from './RouteRecommendations.vue'
import PixelJourneyIcon from './PixelJourneyIcon.vue'
import {recommendation} from '../services/routePresentation.js'
import '../styles/routes.css'
import '../styles/journey-game.css'
const props = defineProps({locale:{default:'en'},passport:{type:Object,required:true},fingerprint:{type:String,required:true}})
const emit = defineEmits(['back','locale-change'])
let storage; try { storage = localStorage } catch { storage = {getItem(){return null}} }
const companion = readCompanion(storage,{confirmed:true})
const de = computed(() => props.locale === 'de')
const form = reactive({maxWalk:500})
const origin=ref(null),destination=ref(null),selected=ref(null)
watch([origin,destination],()=>{result.value=null;selected.value=null;error.value=''})
const busy = ref(false), result = ref(null), error = ref(''), resultHeading = ref(null)
const paused=ref(false),hidden=ref(false)
const motionPaused=computed(()=>paused.value||hidden.value)
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
 selected.value=selected.value===id?null:id
 await nextTick()
 if(selected.value){mapPanel.value?.$el?.scrollIntoView({behavior:reducedMotion()?'instant':'smooth',block:'start'});mapPanel.value?.focusMap()}
}
let controller, alive = true
function checkPassport() { try { if(passportSource(storage).fingerprint !== props.fingerprint) throw Error('changed') } catch { throw Error('changed') } }
async function search() {
 if(busy.value)return
 error.value='';result.value=null;selected.value=null
 try {
  checkPassport()
  if(!origin.value||!destination.value)throw Error('addresses')
  const payload=routePayload(props.passport,{...form,startLat:origin.value.lat,startLon:origin.value.lon,stopLat:destination.value.lat,stopLon:destination.value.lon})
  controller=new AbortController();busy.value=true
  const data=await searchRoutes(payload,controller.signal)
  if(!alive)return
  checkPassport(); result.value=data
  const presentation=recommendation(data)
  if(presentation.state==='clear')selected.value=`${presentation.primary.route.rank}-${presentation.primary.route.mode_key}`
  await nextTick(); resultHeading.value?.focus()
 }catch(e){if(alive)error.value=controller?.signal.aborted?'cancelled':['addresses','coordinates','walking','changed','passport','timeout','unavailable','response','request'].includes(e.message)?e.message:'network'}
 finally{if(alive)busy.value=false}
}
function cancel(){controller?.abort()}
onUnmounted(()=>{alive=false;controller?.abort()})
const errorText=computed(()=>({
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
</script>
<template>
 <main class="route-page journey-game" :class="{'journey-motion-paused':motionPaused}" :lang="locale">
  <header class="route-topbar"><button type="button" class="nes-btn" :disabled="busy" @click="emit('back')">← Passport</button><nav :aria-label="de?'Sprache wählen':'Choose language'"><button v-for="language in ['en','de']" :key="language" type="button" class="nes-btn" :class="{'is-primary':locale===language}" :aria-pressed="locale===language" @click="emit('locale-change',language)">{{language.toUpperCase()}}</button></nav></header>
  <div class="journey-motion-control"><button class="nes-btn" type="button" :aria-pressed="paused" @click="paused=!paused">{{paused?(de?'Animationen starten':'Resume animations'):(de?'Animationen pausieren':'Pause animations')}}</button></div>
  <h1 tabindex="-1">{{de?'Deine nächste Reise':'Your next journey'}}</h1>
  <section class="nes-container route-companion journey-quest"><div class="journey-quest-sky" aria-hidden="true"><span class="journey-cloud"></span><span class="journey-cloud journey-cloud--second"></span><span class="journey-city"></span></div><CompanionAvatar :appearance="companion.appearance" :palette="companion.palette" :animated="!motionPaused"/><div><h2>{{companion.name}}</h2><p>{{de?'Wohin möchtest du? Wir prüfen deine Möglichkeiten und die verfügbaren Wetterdaten.':'Where would you like to go? We’ll check your options and the available weather data.'}}</p></div></section>
  <form class="nes-container route-form" @submit.prevent="search">
   <p>{{de?'Suche Start und Ziel und wähle die passenden Adressen. Abfahrt ist jetzt.':'Search for origin and destination and choose the matching addresses. Departure is now.'}}</p>
   <AddressSearch v-model="origin" id="route-origin" :locale="locale" :label="de?'Startadresse':'Origin address'" :disabled="busy"/>
   <AddressSearch v-model="destination" id="route-destination" :locale="locale" :label="de?'Zieladresse':'Destination address'" :disabled="busy"/>
   <p class="route-note">{{de?'Beispiel: Magdeburg Hauptbahnhof → Universitätsplatz 2, Magdeburg. Ortssuche über IMIQ.':'Example: Magdeburg Hauptbahnhof → Universitätsplatz 2, Magdeburg. Place search provided by IMIQ.'}}</p>
   <label class="route-walk-limit">{{de?'Maximale Gehstrecke (Meter)':'Maximum walking distance (metres)'}}<input v-model="form.maxWalk" type="number" min="0" max="10000" step="any" class="nes-input" :disabled="busy" required/></label>
   <p class="route-note">{{de?'Das Limit wird an den Routendienst übergeben. Reine Fußwege können trotzdem länger sein.':'The limit is sent to the route service. Walking-only alternatives can still be longer.'}}</p>
   <div class="route-form-actions"><button type="submit" class="nes-btn is-primary" :disabled="busy||!origin||!destination">{{de?'Routen suchen':'Find routes'}} →</button></div>
  </form>
  <div v-if="busy" class="nes-container route-status" role="status"><PixelJourneyIcon kind="clock" :animated="!motionPaused"/><p>{{de?'Routen und Kontext werden geprüft. Das kann einige Minuten dauern…':'Checking routes and context. This can take a few minutes…'}}</p><button type="button" class="nes-btn" @click="cancel">{{de?'Abbrechen':'Cancel'}}</button></div>
  <p v-if="error" class="nes-container route-error" role="alert">{{errorText}}</p>
  <RouteMap ref="mapPanel" v-if="!result&&(origin||destination)" :origin="origin" :destination="destination" :result="result" :locale="locale" :selected="selected" @select="selectFromMap"/>
  <section v-if="result" class="route-results" aria-labelledby="route-results-heading">
   <h2 id="route-results-heading" ref="resultHeading" tabindex="-1">{{de?'Deine Möglichkeiten':'Your options'}}</h2>
   <RouteRecommendations :result="result" :passport="passport" :companion="companion" :locale="locale" :selected="selected" :paused="motionPaused" @select="selectFromCard">
    <template #map><RouteMap ref="mapPanel" :origin="origin" :destination="destination" :result="result" :locale="locale" :selected="selected" @select="selectFromMap"/></template>
   </RouteRecommendations>
  </section>
 </main>
</template>
