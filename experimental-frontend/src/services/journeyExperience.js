import {coordinate} from './routePlanning.js'
export const RECENT_PLACES_KEY = 'cricket.recent-places.v1'
export const JOURNEY_KEY = 'cricket.chosen-journey.v1'
export function cleanPlace(place) {
 try {
  const point = coordinate(place?.lat, place?.lon)
  if (typeof place?.label !== 'string' || !place.label.trim()) return null
  return {...point, label:place.label.slice(0,240), id:String(place.id || ''), houseNumber:String(place.houseNumber || ''), street:String(place.street || ''), postcode:String(place.postcode || ''), detail:String(place.detail || '')}
 } catch { return null }
}
export function recentPlaces(storage) {
 try { const list=JSON.parse(storage.getItem(RECENT_PLACES_KEY)||'[]'); return Array.isArray(list)?list.map(cleanPlace).filter(Boolean).slice(0,6):[] } catch { return [] }
}
export function rememberPlaces(storage, places) {
 const merged=[...places,...recentPlaces(storage)].map(cleanPlace).filter(Boolean)
 const seen=new Set(), list=merged.filter(place=>{const key=`${place.lat},${place.lon}`;if(seen.has(key))return false;seen.add(key);return true}).slice(0,6)
 try { storage.setItem(RECENT_PLACES_KEY,JSON.stringify(list)) } catch {}
 return list
}
export function saveJourney(storage, journey) {
 try { storage.setItem(JOURNEY_KEY, JSON.stringify(journey)); return true } catch { return false }
}
