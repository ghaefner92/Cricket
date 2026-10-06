<script setup>
import {computed} from 'vue'
import PixelJourneyIcon from './PixelJourneyIcon.vue'
import {weatherTiles} from '../services/routePresentation.js'
const props=defineProps({candidate:{default:null},locale:{default:'en'},animated:{default:true}})
const de=computed(()=>props.locale==='de'),tiles=computed(()=>weatherTiles(props.candidate))
const names=computed(()=>de.value?{temperature:'Temperatur',humidity:'Luftfeuchtigkeit',rain:'Regen',windSpeed:'Wind',windGust:'Windböen',uvIndex:'UV-Index',lightIntensity:'Licht'}:{temperature:'Temperature',humidity:'Humidity',rain:'Rain',windSpeed:'Wind',windGust:'Wind gust',uvIndex:'UV index',lightIntensity:'Light'})
const icons={temperature:'temperature',humidity:'humidity',rain:'rain',windSpeed:'wind',windGust:'wind',uvIndex:'sun',lightIntensity:'sun'}
function value(t){const fmt=n=>new Intl.NumberFormat(de.value?'de-DE':'en-GB',{maximumFractionDigits:2}).format(n);return t.min===t.max?fmt(t.min):`${fmt(t.min)}–${fmt(t.max)}`}
</script>
<template>
 <section class="weather-inventory" :aria-label="de?'Umweltdaten':'Environmental data'">
  <ul><li v-for="t in tiles" :key="t.variable" class="weather-item" :class="{'weather-item--unknown':!t.known}"><PixelJourneyIcon :kind="icons[t.variable]" :animated="animated&&t.known"/><span>{{names[t.variable]}}</span><strong>{{t.known?`${value(t)} ${t.unit}`:(de?'Keine Daten':'No data')}}</strong></li></ul>
  <p v-if="tiles.some(t=>t.known&&t.min!==t.max)" class="route-note">{{de?'Spannen zeigen mehrere Messungen entlang des Weges.':'Ranges show multiple readings along the path.'}}</p>
 </section>
</template>
