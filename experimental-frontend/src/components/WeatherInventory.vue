<script setup>
import {computed} from 'vue'
import {explainRoute} from '../services/routeExplanation.js'
import PixelJourneyIcon from './PixelJourneyIcon.vue'
import {weatherTiles} from '../services/routePresentation.js'
const props=defineProps({candidate:{default:null},locale:{default:'en'},animated:{default:true},compact:{default:false},dialogue:Boolean})
const de=computed(()=>props.locale==='de'),tiles=computed(()=>weatherTiles(props.candidate))
const names=computed(()=>de.value?{temperature:'Temperatur',humidity:'Luftfeuchtigkeit',rain:'Regen',windSpeed:'Wind',windGust:'Windböen',uvIndex:'UV-Index',lightIntensity:'Licht'}:{temperature:'Temperature',humidity:'Humidity',rain:'Rain',windSpeed:'Wind',windGust:'Wind gust',uvIndex:'UV index',lightIntensity:'Light'})
const visibleTiles=computed(()=>props.compact?tiles.value.filter(t=>['temperature','rain','windSpeed'].includes(t.variable)):tiles.value)
const icons={temperature:'temperature',humidity:'humidity',rain:'rain',windSpeed:'wind',windGust:'wind',uvIndex:'sun',lightIntensity:'sun'}
function value(t){const fmt=n=>new Intl.NumberFormat(de.value?'de-DE':'en-GB',{maximumFractionDigits:2}).format(n);return t.min===t.max?fmt(t.min):`${fmt(t.min)}–${fmt(t.max)}`}
</script>
<template>
 <section class="weather-inventory" :class="{'weather-inventory--dialogue':dialogue}" :aria-label="de?'Umweltdaten':'Environmental data'">
  <ul><li v-for="t in visibleTiles" :key="t.variable" class="weather-item" :class="{'weather-item--unknown':!t.known}"><PixelJourneyIcon :kind="icons[t.variable]" :animated="animated&&t.known"/><span>{{names[t.variable]}}</span><strong>{{t.known?`${value(t)} ${t.unit}`:(de?'Keine Daten':'No data')}}</strong></li></ul>
  <details v-if="compact&&!dialogue" class="journey-weather-more"><summary>{{de?'Weitere Umweltwerte':'More environmental readings'}}</summary><WeatherInventory :candidate="candidate" :locale="locale" :animated="animated"/></details>
  <p v-if="!dialogue&&explainRoute({candidate}).measurementAgeUnknown" class="route-note">{{de?'Aktualität der Messungen nicht bestätigt.':'Reading freshness is unverified.'}}</p>
  <p v-if="!dialogue&&tiles.some(t=>t.known&&t.min!==t.max)" class="route-note">{{de?'Spannen zeigen mehrere Messungen entlang des Weges.':'Ranges show multiple readings along the path.'}}</p>
  <details v-if="dialogue" class="dialogue-weather-details"><summary>{{de?'Hinweise zu Messwerten':'About these readings'}}</summary>
   <p class="route-note">{{de?'Messwerte aus dieser gespeicherten Simulation; keine laufende Wetteranzeige.':'Readings from this saved simulation; not a live weather display.'}}</p>
   <p v-if="explainRoute({candidate}).measurementAgeUnknown" class="route-note">{{de?'Aktualität der Messungen nicht bestätigt.':'Reading freshness is unverified.'}}</p>
   <p v-if="visibleTiles.some(t=>t.known&&t.min!==t.max)" class="route-note">{{de?'Spannen zeigen mehrere Messungen entlang des Weges.':'Ranges show multiple readings along the path.'}}</p>
   <WeatherInventory :candidate="candidate" :locale="locale" :animated="false"/>
  </details>
 </section>
</template>

<style scoped>
.weather-inventory--dialogue{margin:0 0 18px;min-width:0}
.weather-inventory--dialogue>ul{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,150px),1fr));gap:10px;list-style:none;padding:0;margin:0}
.weather-inventory--dialogue>ul>.weather-item{display:grid;grid-template-columns:24px minmax(0,1fr);align-items:center;gap:8px;padding:12px;border:2px solid #617c9f;background:#172b46;min-width:0}
.weather-inventory--dialogue .weather-item>svg{width:24px;height:24px;grid-row:1/3}
.weather-inventory--dialogue .weather-item>strong{grid-column:2;overflow-wrap:anywhere;color:#eaf6ff}
.weather-inventory--dialogue .weather-item--unknown{border-style:dashed}
.dialogue-weather-details{margin-top:10px}.dialogue-weather-details summary{min-height:44px;cursor:pointer;padding:8px 0;color:#c3d8f4}
</style>
