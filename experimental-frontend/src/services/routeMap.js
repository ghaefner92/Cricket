import {cardsFromResult,finiteNumber} from './routePlanning.js'
export const ROUTE_COLORS={car:'#ce4b4b',bike:'#16774f',foot:'#507ee8',walk:'#507ee8',pt:'#a16a00'}
export function validPoint(p){return finiteNumber(p?.latitude)&&finiteNumber(p?.longitude)&&Math.abs(p.latitude)<=90&&Math.abs(p.longitude)<=180}
function legPath(leg){
 if(Array.isArray(leg.geometry)&&leg.geometry.length>=2&&leg.geometry.every(validPoint))return leg.geometry.map(p=>[p.latitude,p.longitude])
 const coordinates=leg.geometry?.type==='LineString'?leg.geometry.coordinates:null
 if(!Array.isArray(coordinates)||coordinates.length<2||!coordinates.every(p=>Array.isArray(p)&&finiteNumber(p[0])&&finiteNumber(p[1])&&Math.abs(p[0])<=180&&Math.abs(p[1])<=90))return null
 return coordinates.map(p=>[p[1],p[0]])
}
export function mapRoutes(result){
 return cardsFromResult(result).filter(c=>c.route.available!==false).map(card=>{
  const candidate=(result?.candidate_routes||[]).find(c=>c.route_id===card.audit?.route_id)
  // Keep legs separate: a missing leg is never replaced by a joining line.
  // Provider geometry remains displayable when contextual adaptation is absent.
  // Preserve the supplied leg sequence and coordinate order; never join gaps.
  const segments=(candidate?.legs?.length?candidate.legs:card.route.legs||[]).map(leg=>({
   path:legPath(leg),mode:leg.mode,
   line:leg.transit?.line||null,approximate:leg.transit?.geometry_quality==='APPROXIMATE_NO_SHAPES',
   color:ROUTE_COLORS[leg.mode]||ROUTE_COLORS[card.route.mode_key]||'#2766a0'
  })).filter(segment=>segment.path)
  const paths=segments.map(s=>s.path)
  return {id:`${card.route.rank}-${card.route.mode_key}`,rank:card.route.rank,mode:card.route.mode_key,paths,segments,color:ROUTE_COLORS[card.route.mode_key]||'#2766a0',supplemental:card.audit?.geometry_evidence?.route_identity_verified===false}
 }).filter(r=>r.paths.length)
}
