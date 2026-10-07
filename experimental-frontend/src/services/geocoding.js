import {coordinate} from './routePlanning.js'
const cache=new Map(),CACHE_MS=5*60*1000,MAX_CACHE=50
// Public demo for moderate use; deployments can select their own Photon service.
const configured=import.meta.env?.VITE_PHOTON_BASE_URL||'https://photon.komoot.io'
export const photonBase=configured.replace(/\/+$/,'')
let queue=Promise.resolve(),nextStart=0
function pointForBias(bias){try{return coordinate(bias?.lat,bias?.lon)}catch{return {lat:52.13,lon:11.62}}}
function nearDistance(place,point){const rad=Math.PI/180,a=place.lat*rad,b=point.lat*rad;return Math.sin((a-b)/2)**2+Math.cos(a)*Math.cos(b)*Math.sin((place.lon-point.lon)*rad/2)**2}
function cacheKey(query,locale,autocomplete,bias){const p=pointForBias(bias);return `${photonBase}:${locale==='de'?'de':'en'}:${p.lat},${p.lon}:${query.trim().replace(/\s+/g,' ').toLocaleLowerCase('de-DE')}`}
export function cachedPlaces(query,locale,autocomplete=false,bias=null){const entry=cache.get(cacheKey(query,locale,autocomplete,bias));if(!entry)return null;if(Date.now()-entry.at>CACHE_MS){cache.delete(cacheKey(query,locale,autocomplete,bias));return null}return entry.places.map(p=>({...p}))}
export function requestedHouseNumber(query){
 // Only explicit German-style number tokens; five-digit postcodes are not portals.
 const streetPart=query.split(',')[0].trim()
 const matches=[...streetPart.matchAll(/\s(\d{1,4}[a-z]?(?:[-/]\d{1,4}[a-z]?)?)(?=\s|$)/gi)]
 return matches.length?matches.at(-1)[1].toLocaleLowerCase('de-DE'):''
}
export function placesFromResponse(data) {
 if(data?.type==='FeatureCollection'&&Array.isArray(data.features)){
  data=data.features.flatMap(feature=>{
   if(feature?.geometry?.type!=='Point'||!Array.isArray(feature.geometry.coordinates)||feature.geometry.coordinates.length<2)return []
   const p=feature.properties;if(!p||typeof p!=='object')return []
   const [lon,lat]=feature.geometry.coordinates
   const street=String(p.street||'').trim(),house=String(p.housenumber||'').trim(),city=String(p.city||'').trim(),name=String(p.name||'').trim()
   const address=[street,house].filter(Boolean).join(' ')
   const title=[name&&name.toLocaleLowerCase()!==street.toLocaleLowerCase()?name:'',address||name,city].filter(Boolean)
   return [{lat,lon,name,street,housenumber:house,postcode:p.postcode,city,display_name:[...new Set(title)].join(', '),osm_id:`${p.osm_type||''}${p.osm_id||''}`}]
  })
 }
 if(!Array.isArray(data)&&Array.isArray(data?.results))data=data.results
 if(!Array.isArray(data))throw Error('response')
 const seen=new Set(),results=[]
 for(const item of data){
  if(!item||typeof item!=='object')continue
  let point;try{point=coordinate(item.lat,item.lon)}catch{continue}
  const houseNumber=String(item.housenumber||item.address?.house_number||'').trim(),street=String(item.street||item.address?.road||'').trim(),city=String(item.city||item.address?.city||'').trim(),postcode=String(item.postcode||item.address?.postcode||'').trim()
  const key=`${point.lat},${point.lon}:${houseNumber}`;if(seen.has(key))continue
  let label=String(item.display_name||item.name||[street,houseNumber,city].filter(Boolean).join(', ')).trim()
  if(houseNumber&&!label.split(/[\s,]+/).includes(houseNumber))label=`${street||label} ${houseNumber}${city?`, ${city}`:''}`
  if(!label)continue
  seen.add(key);results.push({...point,label,id:String(item.osm_id||key),houseNumber,street,postcode,detail:[postcode,city].filter(Boolean).join(' ')})
 }
 return results.slice(0,10)
}
function wait(ms,signal){
 if(signal.aborted)return Promise.reject(new DOMException('Aborted','AbortError'))
 return new Promise((resolve,reject)=>{
  const aborted=()=>{clearTimeout(timer);reject(new DOMException('Aborted','AbortError'))}
  const timer=setTimeout(()=>{signal.removeEventListener('abort',aborted);resolve()},ms)
  signal.addEventListener('abort',aborted,{once:true})
 })
}
function scheduledFetch(url,signal){
 const slot=queue.then(async()=>{
  await wait(Math.max(0,nextStart-Date.now()),signal)
  if(signal.aborted)throw new DOMException('Aborted','AbortError')
  nextStart=Date.now()+1000
 })
 queue=slot.catch(()=>{})
 return slot.then(()=>{if(signal.aborted)throw new DOMException('Aborted','AbortError');return fetch(url,{headers:{Accept:'application/json'},signal})})
}
export async function findPlaces(query,locale,signal,{autocomplete=false,bias=null}={}){
 const point=pointForBias(bias)
 const text=query.trim();if(text.length<3||text.length>240)throw Error('query')
 if(signal?.aborted)throw new DOMException('Aborted','AbortError')
 const cached=cachedPlaces(text,locale,autocomplete,bias);if(cached)return cached
 const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),20000)
 try{
  const language=locale==='de'?'de':'en',combined=signal?AbortSignal.any([signal,controller.signal]):controller.signal
  const url=`${photonBase}/api/?${new URLSearchParams({q:text,lang:language,limit:'10',lat:String(point.lat),lon:String(point.lon)})}`
  const r=await scheduledFetch(url,combined)
  if(!r.ok)throw Error('network')
  const data=await r.json()
  if(data?.type!=='FeatureCollection'||!Array.isArray(data.features))throw Error('response')
  const number=requestedHouseNumber(text)
  const places=placesFromResponse(data).filter(place=>!number||place.houseNumber.toLocaleLowerCase('de-DE')===number).sort((a,b)=>nearDistance(a,point)-nearDistance(b,point))
  if(combined.aborted)throw new DOMException('Aborted','AbortError')
  cache.set(cacheKey(text,locale,autocomplete,bias),{at:Date.now(),places})
  if(cache.size>MAX_CACHE)cache.delete(cache.keys().next().value)
  return places.map(p=>({...p}))
 }catch(e){if(controller.signal.aborted&&!signal?.aborted)throw Error('timeout');throw e}
 finally{clearTimeout(timer)}
}
