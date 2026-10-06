<script setup>
import {computed,nextTick,onMounted,onUnmounted,ref,watch} from 'vue'
import {mapRoutes} from '../services/routeMap.js'
import '../vendor/leaflet/leaflet.css'
const props=defineProps({result:{default:null},origin:{default:null},destination:{default:null},locale:{default:'en'},selected:{default:null}})
const emit=defineEmits(['select'])
const element=ref(null),failed=ref(false),tileError=ref(false)
const routes=computed(()=>mapRoutes(props.result)),de=computed(()=>props.locale==='de')
let L,map,lines,markers,observer,alive=true
function mode(key){return (de.value?{car:'Auto',bike:'Fahrrad',foot:'Zu Fuß',walk:'Zu Fuß',pt:'ÖPNV'}:{car:'Car',bike:'Bike',foot:'Walk',walk:'Walk',pt:'Public transport'})[key]||key}
function safeText(text){const el=document.createElement('span');el.textContent=text;return el}
function paint(fit=false){
 if(!map)return
 lines.clearLayers();markers.clearLayers();const bounds=[]
 for(const r of routes.value){
  const selected=props.selected===r.id
  for(const path of r.paths){
   bounds.push(...path)
   const line=L.polyline(path,{color:r.color,weight:selected?7:4,opacity:props.selected&&!selected?0.35:0.9,dashArray:r.supplemental?'9 5':null}).addTo(lines)
   line.bindTooltip(safeText(`${r.rank}. ${mode(r.mode)}${r.supplemental?(de.value?' · Ergänzender Weg':' · Supplemental path'):''}`))
   line.on('click',()=>emit('select',props.selected===r.id?null:r.id))
  }
 }
 for(const [point,label,color]of [[props.origin,de.value?'Start':'Origin','#16774f'],[props.destination,de.value?'Ziel':'Destination','#ce4b4b']]){
  if(!point)continue
  const pos=[point.lat,point.lon];bounds.push(pos)
  L.circleMarker(pos,{radius:8,color:'#fff',weight:2,fillColor:color,fillOpacity:1}).addTo(markers).bindTooltip(safeText(label+': '+point.label))
 }
 if(fit&&bounds.length){map.fitBounds(bounds,{padding:[28,28],maxZoom:16,animate:false})}
 if(props.selected){const chosen=routes.value.find(r=>r.id===props.selected);if(chosen)lines.eachLayer(layer=>{if(layer.options.weight===7)layer.bringToFront()})}
}
onMounted(async()=>{
 try{
  L=await import('../vendor/leaflet/leaflet-src.esm.js');if(!alive)return
  map=L.map(element.value,{scrollWheelZoom:false}).setView([52.13,11.63],13)
  L.tileLayer(import.meta.env.VITE_MAP_TILE_URL||'https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}).on('tileerror',()=>{if(alive)tileError.value=true}).addTo(map)
  lines=L.layerGroup().addTo(map);markers=L.layerGroup().addTo(map)
  observer=new ResizeObserver(()=>map?.invalidateSize({pan:false}));observer.observe(element.value)
  await nextTick();paint(true);fitSelected()
 }catch{if(alive)failed.value=true}
})
watch(()=>[props.result,props.origin,props.destination],()=>paint(true),{deep:true})
watch(()=>props.selected,()=>{paint(false);fitSelected()})
watch(()=>props.locale,()=>paint(false))
onUnmounted(()=>{alive=false;observer?.disconnect();map?.remove();map=null})
function fitSelected(){
 if(!map||!props.selected)return
 const chosen=routes.value.find(r=>r.id===props.selected)
 if(chosen?.paths.length)map.fitBounds(chosen.paths.flat(),{padding:[32,32],maxZoom:16,animate:false})
}
function fitAll(){if(map)paint(true)}
function focusMap(){element.value?.focus({preventScroll:true})}
defineExpose({focusMap})
</script>
<template>
 <section class="nes-container route-map-panel" aria-labelledby="route-map-heading">
  <div class="route-map-heading"><h2 id="route-map-heading">{{de?'Deine Wege auf der Karte':'Your paths on the map'}}</h2><button type="button" class="nes-btn" @click="fitAll">{{de?'Alle anzeigen':'Show all'}}</button></div>
  <div ref="element" class="route-map" tabindex="0" :aria-label="de?'Interaktive Karte mit Start, Ziel und verfügbaren Wegen':'Interactive map with origin, destination and available paths'"></div>
  <p v-if="failed" role="alert">{{de?'Die Karte konnte nicht geladen werden. Die Routen bleiben unten verfügbar.':'The map could not load. Route results remain available below.'}}</p>
  <p v-if="tileError" class="route-note" role="status">{{de?'Einige Kartenbilder fehlen. Die verfügbaren Weglinien bleiben sichtbar.':'Some map tiles could not load. Available path lines remain visible.'}}</p>
  <ul v-if="routes.length" class="route-map-legend"><li v-for="r in routes" :key="r.id"><button type="button" class="nes-btn" :class="{'is-primary':selected===r.id}" :aria-pressed="selected===r.id" @click="emit('select',selected===r.id?null:r.id)"><i :style="{background:r.color}" aria-hidden="true"></i>{{r.rank}}. {{mode(r.mode)}}</button></li></ul>
  <p class="route-note">{{routes.some(r=>r.supplemental)?(de?'Gestrichelte Linien: ergänzende GraphHopper-Wege. Ihre Identität mit dem ursprünglichen Routing-Weg ist nicht bestätigt.':'Dashed lines: supplemental GraphHopper paths. Their identity with the original routing itinerary is unverified.'):(de?'Es werden nur empfangene Weggeometrien angezeigt.':'Only received path geometries are displayed.')}}</p>
 </section>
</template>
