<script setup>
import {computed,nextTick,onMounted,onUnmounted,ref,watch} from 'vue'
import {mapRoutes} from '../services/routeMap.js'
import PixelJourneyIcon from './PixelJourneyIcon.vue'
import '../vendor/leaflet/leaflet.css'

const props=defineProps({
 result:{default:null},
 origin:{default:null},
 destination:{default:null},
 locale:{default:'en'},
 selected:{default:null},
 chosenId:{default:null}
})
const emit=defineEmits(['select'])
const element=ref(null),failed=ref(false),tileError=ref(false)
const ready=ref(false),expanded=ref(false)
const de=computed(()=>props.locale==='de')
const routes=computed(()=>mapRoutes(props.result).map(r=>({
 ...r,
 summary:props.result?.routing?.routes?.find(
  a=>a.rank===r.rank&&a.mode_key===r.mode
 )?.summary
})))

let L,map,lines,markers,observer,alive=true

function mode(key){
 return (de.value
  ? {car:'Auto',bike:'Fahrrad',foot:'Zu Fuß',walk:'Zu Fuß',pt:'ÖPNV'}
  : {car:'Car',bike:'Bike',foot:'Walk',walk:'Walk',pt:'Public transport'}
 )[key]||key
}
function icon(key){return key==='foot'?'walk':key}
function metric(r){
 const s=r.summary
 const n=new Intl.NumberFormat(props.locale,{maximumFractionDigits:1})
 return [
  Number.isFinite(s?.duration_seconds)
   ? n.format(s.duration_seconds/60)+' min' : null,
  Number.isFinite(s?.distance_meters)
   ? n.format(s.distance_meters/1000)+' km' : null
 ].filter(Boolean).join(' · ')
}
function safeText(text){
 const el=document.createElement('span')
 el.textContent=text
 return el
}

function paint(fit=false){
 if(!map)return
 lines.clearLayers()
 markers.clearLayers()
 const bounds=[]
 const ordered=[...routes.value].sort(
  (a,b)=>Number(a.id===props.selected)-Number(b.id===props.selected)
 )

 for(const r of ordered){
  const selected=props.selected===r.id
  for(const path of r.paths){
   bounds.push(...path)
   const dim=props.selected&&!selected
   const dash=r.supplemental?'9 5':null

   L.polyline(path,{
    color:'#14213b',
    weight:selected?10:7,
    opacity:dim?0.3:0.8,
    dashArray:dash,
    interactive:false
   }).addTo(lines)

   const line=L.polyline(path,{
    color:r.color,
    weight:selected?7:4,
    opacity:dim?0.45:1,
    dashArray:dash
   }).addTo(lines)

   line.bindTooltip(safeText(
    `${r.rank}. ${mode(r.mode)}${r.supplemental
     ? (de.value?' · Ergänzender Weg':' · Supplemental path')
     : ''}`
   ))
   line.on('click',()=>emit('select',r.id))
  }
 }

 for(const [point,letter,label] of [
  [props.origin,'A',de.value?'Start':'Origin'],
  [props.destination,'B',de.value?'Ziel':'Destination']
 ]){
  if(!point||!Number.isFinite(point.lat)||!Number.isFinite(point.lon))continue
  const pos=[point.lat,point.lon]
  bounds.push(pos)

  const markerIcon=L.divIcon({
   className:`journey-map-pin journey-map-pin--${letter}`,
   html:`<span>${letter}</span>`,
   iconSize:[28,32],
   iconAnchor:[14,32]
  })

  L.marker(pos,{icon:markerIcon})
   .addTo(markers)
   .bindTooltip(safeText(label+': '+point.label))
 }

 if(fit&&bounds.length){
  map.fitBounds(bounds,{padding:[28,28],maxZoom:16,animate:false})
 }
}

onMounted(async()=>{
 window.addEventListener('keydown',escapeMap)
 try{
  L=await import('../vendor/leaflet/leaflet-src.esm.js')
  if(!alive)return

  map=L.map(element.value,{
   scrollWheelZoom:false,
   zoomControl:false
  }).setView([52.13,11.63],13)

  L.tileLayer(
   import.meta.env.VITE_MAP_TILE_URL||
   'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
   {
    maxZoom:19,
    attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
   }
  ).on('tileerror',()=>{
   if(alive)tileError.value=true
  }).addTo(map)

  lines=L.layerGroup().addTo(map)
  markers=L.layerGroup().addTo(map)
  observer=new ResizeObserver(()=>map?.invalidateSize({pan:false}))
  observer.observe(element.value)

  ready.value=true
  await nextTick()
  paint(true)
  fitSelected()
 }catch{
  if(alive)failed.value=true
 }
})

watch(
 ()=>[props.result,props.origin,props.destination],
 ()=>{paint(true);fitSelected()},
 {deep:true}
)
watch(()=>props.selected,()=>{paint(false);fitSelected()})
watch(()=>props.locale,()=>paint(false))

onUnmounted(()=>{
 alive=false
 window.removeEventListener('keydown',escapeMap)
 observer?.disconnect()
 map?.remove()
 map=null
})

function fitSelected(){
 const chosen=routes.value.find(r=>r.id===props.selected)
 if(map&&chosen?.paths.length){
  map.fitBounds(chosen.paths.flat(),{
   padding:[32,32],maxZoom:16,animate:false
  })
 }
}
function fitAll(){paint(true)}
function focusMap(){element.value?.focus({preventScroll:true})}
function zoom(amount){
 if(map)map.setZoom(map.getZoom()+amount,{animate:false})
}
async function toggleExpanded(){
 expanded.value=!expanded.value
 await nextTick()
 map?.invalidateSize({pan:false})
 focusMap()
}
function escapeMap(event){
 if(event.key==='Escape'&&expanded.value){
  event.preventDefault()
  toggleExpanded()
 }
}
defineExpose({focusMap})
</script>

<template>
 <section
  class="nes-container route-map-panel journey-map-v2"
  :class="{'journey-map-expanded':expanded}"
  aria-labelledby="route-map-heading"
 >
  <header class="route-map-heading">
   <h2 id="route-map-heading">
    {{de?'Dein Reiseplan':'Your journey map'}}
   </h2>
   <button type="button" class="nes-btn" :aria-pressed="expanded" @click="toggleExpanded">
    {{expanded?(de?'Verkleinern':'Collapse'):(de?'Vergrößern':'Expand')}}
   </button>
  </header>

  <slot name="journey-search">
  <div class="journey-map-endpoints">
   <p><strong class="map-label-a">A</strong> {{origin?.label||(de?'Start auswählen':'Choose origin')}}</p>
   <p><strong class="map-label-b">B</strong> {{destination?.label||(de?'Ziel auswählen':'Choose destination')}}</p>
  </div>

  </slot>

  <div class="journey-map-tools" role="group" :aria-label="de?'Kartensteuerung':'Map controls'">
   <button class="nes-btn" type="button" :disabled="!ready" @click="zoom(1)" :aria-label="de?'Hineinzoomen':'Zoom in'">+</button>
   <button class="nes-btn" type="button" :disabled="!ready" @click="zoom(-1)" :aria-label="de?'Herauszoomen':'Zoom out'">−</button>
   <button class="nes-btn" type="button" :disabled="!ready" @click="fitAll">{{de?'Alle anzeigen':'Show all'}}</button>
  </div>

  <div
   ref="element"
   class="route-map"
   tabindex="0"
   :aria-label="de?'Interaktive Karte mit Start, Ziel und verfügbaren Wegen':'Interactive map with origin, destination and available paths'"
  ></div>

  <p v-if="failed" role="alert">
   {{de?'Die Karte konnte nicht geladen werden. Die Routen bleiben unten verfügbar.':'The map could not load. Route results remain available below.'}}
  </p>
  <p v-if="tileError" class="route-note" role="status">
   {{de?'Einige Kartenbilder fehlen.':'Some map tiles could not load.'}}
  </p>

  <ul v-if="routes.length" class="route-map-legend journey-map-routes">
   <li v-for="r in routes" :key="r.id">
    <button
     type="button"
     class="nes-btn"
     :style="{'--route-color':r.color}"
     :aria-pressed="selected===r.id"
     @click="emit('select',r.id)"
    >
     <PixelJourneyIcon :kind="icon(r.mode)" :animated="false"/>
     <span>
      <strong>{{mode(r.mode)}}</strong>
      <small v-if="metric(r)">{{metric(r)}}</small>
      <small v-if="chosenId===r.id" class="route-state-badge route-state-badge--saved">{{de?'Gewählt und gespeichert':'Chosen and saved'}}</small>
      <small>{{selected===r.id?(de?'Kartenvorschau':'Map preview'):(de?'Auf Karte ansehen':'View on map')}}</small>
     </span>
    </button>
   </li>
  </ul>

  <template v-if="routes.some(r=>r.supplemental)">
   <p class="journey-map-warning">
    {{de?'Ergänzender Weg · Übereinstimmung nicht bestätigt':'Supplemental path · match unverified'}}
   </p>
   <details class="journey-map-evidence">
    <summary>{{de?'Hinweis zum Verlauf':'About this path'}}</summary>
    <p>{{de?'Gestrichelte Linien sind ergänzende GraphHopper-Wege. Ihre Identität mit dem ursprünglichen Routing-Weg ist nicht bestätigt.':'Dashed lines are supplemental GraphHopper paths. Their identity with the original routing itinerary is unverified.'}}</p>
   </details>
  </template>

  <p v-else-if="result&&!routes.length" class="route-note">
   {{de?'Keine Weggeometrien empfangen. Die Ergebnisse stehen unten.':'No path geometries received. Results are available below.'}}
  </p>
  <p v-else-if="!result" class="route-note">
   {{de?'Wähle Start und Ziel. Wege erscheinen nach der Routensuche.':'Choose origin and destination. Paths appear after searching.'}}
  </p>
 </section>
</template>

<style>
.journey-map-v2{box-shadow:4px 4px #090f22}
.journey-map-v2 .route-map-heading h2{color:#ffda83}
.journey-map-endpoints{display:grid;gap:8px;background:#15213c;padding:12px;margin:12px 0}
.journey-map-endpoints p{margin:0;overflow-wrap:anywhere}
.map-label-a,.map-label-b{display:inline-block;padding:4px 8px;border:2px solid currentColor;margin-right:8px}
.map-label-a{color:#a6e6c5}
.map-label-b{color:#ffda83}
.journey-map-tools{display:flex;gap:12px;flex-wrap:wrap;margin:12px 0}
.journey-map-tools button{min-width:44px}
.journey-map-v2 .route-map{height:340px;min-height:260px;border:3px solid #92b3df}
.journey-map-v2 .journey-map-routes{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px}
.journey-map-routes li{min-width:0}
.journey-map-routes .nes-btn{display:flex;align-items:center;gap:12px;width:100%;text-align:left;padding:12px;background:#15213c;color:#f4f3ff;border:3px solid #6c83ab;border-left:6px solid var(--route-color)}
.journey-map-routes button[aria-pressed=true]{background:#263c61;outline:2px solid var(--route-color);outline-offset:2px}
.journey-map-routes .journey-pixel-icon{width:32px;height:32px;flex-shrink:0}
.journey-map-routes strong,.journey-map-routes small{display:block}
.journey-map-routes small{font-size:9px;line-height:1.9;margin-top:6px;color:#cfddf5}
.journey-map-warning{border-left:4px solid #ffda83;padding:10px;color:#ffe4ad;background:#342d28}
.journey-map-evidence summary{cursor:pointer;color:#cfddf5;min-height:44px}
.journey-map-pin{background:transparent!important;border:0!important}
.journey-map-pin span{display:grid;place-items:center;width:28px;height:28px;border:3px solid #14213b;background:#a6e6c5;color:#14213b;font:12px/1 'Press Start 2P',monospace;box-shadow:2px 2px #14213b}
.journey-map-pin--B span{background:#ffda83}
.journey-map-expanded{position:fixed!important;inset:12px!important;z-index:2000!important;overflow:auto;margin:0!important}
.journey-map-expanded .route-map{height:55dvh}
.journey-map-v2 .leaflet-control-attribution{max-width:100%;white-space:normal}
@media(max-width:600px){
 .journey-map-v2 .route-map{height:280px}
 .journey-map-v2 .journey-map-routes{grid-template-columns:1fr}
 .journey-map-expanded{inset:6px!important}
 .journey-map-expanded .route-map{height:55dvh}
}
</style>