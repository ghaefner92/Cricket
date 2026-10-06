import {environmentalRows,finiteNumber,needChanges} from './routePlanning.js'
const SUPPORTED=['rain','wind','heat','cold','darkness','traffic','crowding']
export function modelMode(key){return {foot:'walk',car_driver:'car',pt_bus_tram:'pt'}[key]||key}
export function explainRoute(card){
 const candidate=card?.candidate,changes=needChanges(candidate)
 const stressors=(candidate?.route_context?.normalized_stressors||[]).filter(s=>SUPPORTED.includes(s.name))
 const known=stressors.filter(s=>s.status!=='UNKNOWN'&&finiteNumber(s.value))
 const unknown=SUPPORTED.filter(name=>!known.some(s=>s.name===name))
 const delta=candidate?.difference_from_baseline?.final_action_activations?.[modelMode(card?.route?.mode_key)]
 const readings=environmentalRows(candidate).filter(o=>o.status==='OBSERVED'&&finiteNumber(o.numeric_value))
 return {available:!!candidate,changes,knownCount:known.length,unknown,
  activationDelta:finiteNumber(delta)?delta:null,
  direction:!finiteNumber(delta)?'unknown':Math.abs(delta)<1e-9?'unchanged':delta>0?'up':'down',
  measurementAgeUnknown:readings.length>0&&readings.some(o=>!o.timestamp||o.source_metadata?.temporal_freshness==='UNKNOWN'),
  supplemental:card?.audit?.geometry_evidence?.route_identity_verified===false}
}
