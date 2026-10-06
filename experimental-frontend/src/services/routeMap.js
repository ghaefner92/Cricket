import {cardsFromResult,finiteNumber} from './routePlanning.js'
export const ROUTE_COLORS={car:'#ce4b4b',bike:'#16774f',foot:'#554ac0',walk:'#554ac0',pt:'#a16a00'}
export function validPoint(p){return finiteNumber(p?.latitude)&&finiteNumber(p?.longitude)&&Math.abs(p.latitude)<=90&&Math.abs(p.longitude)<=180}
export function mapRoutes(result){
 return cardsFromResult(result).filter(c=>c.route.available!==false).map(card=>{
  const candidate=(result?.candidate_routes||[]).find(c=>c.route_id===card.audit?.route_id)
  // Keep legs separate: a missing leg is never replaced by a joining line.
  const paths=(candidate?.legs||[]).map(leg=>leg.geometry).filter(g=>Array.isArray(g)&&g.length>=2&&g.every(validPoint)).map(g=>g.map(p=>[p.latitude,p.longitude]))
  return {id:`${card.route.rank}-${card.route.mode_key}`,rank:card.route.rank,mode:card.route.mode_key,paths,color:ROUTE_COLORS[card.route.mode_key]||'#2766a0',supplemental:card.audit?.geometry_evidence?.route_identity_verified===false}
 }).filter(r=>r.paths.length)
}
