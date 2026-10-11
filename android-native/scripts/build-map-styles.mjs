// Reproducible Companion styles derived from OpenFreeMap's Liberty style.
// Source: https://tiles.openfreemap.org/styles/liberty (retrieved 2026-10-09).
import {readFileSync,writeFileSync} from 'node:fs'
import {fileURLToPath} from 'node:url'

const root=new URL('../app/src/main/assets/maps/',import.meta.url)
const original=JSON.parse(readFileSync(new URL('liberty-source.json',root),'utf8').replace(/^\uFEFF/,''))
const width=['interpolate',['linear'],['zoom'],12,1.5,16,3,20,7]
const pathLayer=(id,color,filter,dash)=>({id,type:'line',source:'openmaptiles','source-layer':'transportation',minzoom:12,
 filter:['all',['==',['geometry-type'],'LineString'],filter],layout:{'line-join':'round','line-cap':'round'},
 paint:{'line-color':color,'line-width':width,...(dash?{'line-dasharray':dash}:{})}})

for(const variant of ['clear','night','mobility']) {
 const style=structuredClone(original)
 const night=variant==='night'
 style.name=`Companion ${variant}`
 style.metadata={...style.metadata,'companion:base':'OpenFreeMap Liberty','companion:variant':variant}
 // Attribution remains visible and interactive in the native map widget.
 style.sources.openmaptiles.attribution='<a href="https://openfreemap.org/">OpenFreeMap</a> · <a href="https://openmaptiles.org/">OpenMapTiles</a> · <a href="https://www.openstreetmap.org/copyright">© OpenStreetMap contributors</a>'
 style.layers=style.layers.filter(l=>l.type!=='fill-extrusion'&&l.id!=='natural_earth')
 for(const layer of style.layers) {
  const id=layer.id
  // Interactive passenger platforms / parking have one controlled overlay.
  // Tile POIs cannot distinguish a rail stop_position from a waiting platform.
  if(layer['source-layer']==='poi')layer.filter=['all',layer.filter??true,['!', ['match',['get','class'],['bus','rail','parking'],true,false]]]
  const paint=layer.paint??={}
  if(layer.type==='background')paint['background-color']=night?'#19151F':'#F5F1EA'
  if(layer.type==='fill') {
   const color=id.includes('water')?'#BBCFD5':id.includes('wood')?'#C3D4BE':id.includes('grass')||id.includes('park')?'#D9E4CE':id.includes('building')?'#DCD5CF':id.includes('residential')?'#EFE9E2':id.includes('hospital')?'#E9D9DE':id.includes('school')?'#EAE0CD':id.includes('sand')?'#E8DFC5':'#E6E4D7'
   const dark=id.includes('water')?'#253847':id.includes('wood')?'#293C33':id.includes('grass')||id.includes('park')?'#2A3933':id.includes('building')?'#3B3243':id.includes('hospital')?'#3E2D3A':id.includes('school')?'#40382E':'#27212F'
   paint['fill-color']=night?dark:color
   if('fill-outline-color' in paint)paint['fill-outline-color']=night?'#50465A':'#C8CEC0'
   if(id.includes('building'))paint['fill-opacity']=night?.85:.65
  }
  if(layer.type==='line') {
   if(night)paint['line-color']=id.includes('water')?'#43677A':id.includes('rail')?'#8B789C':id.includes('boundary')?'#796981':id.includes('casing')?'#1C1822':id.includes('motorway')?'#766650':id.includes('trunk')||id.includes('primary')?'#635550':id.includes('path')?'#849484':'#51485B'
   else if(layer['source-layer']==='transportation')paint['line-color']=id.includes('rail')?'#908395':id.includes('casing')?'#D4CEC7':id.includes('motorway')?'#DCC49C':id.includes('trunk')||id.includes('primary')?'#E8D6B9':id.includes('path')?'#899989':'#FFFFFF'
   if(variant==='mobility'&&id.includes('rail')&&!id.includes('hatching'))paint['line-color']='#80649C'
  }
  if(layer.type==='symbol') {
   if('text-color' in paint)paint['text-color']=night?'#E9E0EC':'#554D59'
   if('text-halo-color' in paint)paint['text-halo-color']=night?'#211B28':'#F5F1EA'
   if(id==='poi_transit') {
    paint['text-color']=night?'#CFB8E7':'#674C89'
    if(variant==='mobility'){layer.minzoom=12;layer.layout['icon-size']=.95;layer.layout['text-size']=13}
   }
   // Keep orientation landmarks, while reducing unrelated commercial labels.
   if(id.startsWith('poi_r'))layer.minzoom=Math.max(layer.minzoom??0,variant==='mobility'?17:16)
  }
 }
 if(variant==='mobility') {
  const overlays=[
   pathLayer('companion-footways','#297BAC',['all',['match',['get','class'],['path','pedestrian'],true,false],['!=',['get','subclass'],'cycleway']],[1.2,.8]),
   pathLayer('companion-cycleways','#168269',['any',['==',['get','subclass'],'cycleway'],['==',['get','class'],'cycleway']]),
  ]
  const before=style.layers.findIndex(l=>l.type==='symbol')
  style.layers.splice(before<0?style.layers.length:before,0,...overlays)
 }
 writeFileSync(new URL(`companion-${variant}.json`,root),JSON.stringify(style))
 console.log(`${variant}: ${style.layers.length} layers → ${fileURLToPath(new URL(`companion-${variant}.json`,root))}`)
}
