import {buildInputs,readSession,validAnswers,PROFILE_KEY,SCHEMA} from './adaptiveBeliefs.js'
import {loadPlan} from '../stores/weeklyGoals.js'
import {readValences} from '../stores/valences.js'
import {readAvailability,toBackendAvailability,MODES} from '../stores/availability.js'
import {readTolerances,toBackendTolerances} from '../stores/tolerances.js'
export const PASSPORT_KEY='imiq.experimental.cognitive-passport.v1'
const sameMap=(a,b)=>a&&b&&Object.keys(a).length===Object.keys(b).length&&Object.keys(a).every(k=>a[k]===b[k])
export function passportSource(storage,{requireTolerances=true}={}){
 const agent=storage.getItem('imiq.experimental.agent-id.v1'),plan=loadPlan(storage),valences=readValences(storage)
 const inputs=buildInputs(agent,plan,valences)
 let availability
 try{availability=toBackendAvailability(readAvailability(storage))}catch{throw Error('availability')}
 let completed
 try{completed=JSON.parse(storage.getItem(PROFILE_KEY)||'null')}catch{throw Error('beliefs')}
 const session=readSession(storage,inputs),r=completed?.response
 if(completed?.fingerprint!==inputs.fingerprint||r?.schema_version!==SCHEMA||r.stage!=='complete'||r.agent_id!==agent||r.ready_for_hotco!==true||!session||!validAnswers(session.start,session.answers,true)||r.session_id!==session.start.session_id)throw Error('beliefs')
 const observed=r.observed_measurements
 if(!sameMap(observed?.needs_raw_1_to_7,inputs.payload.responses.needs)||!sameMap(observed?.valences_raw_minus3_to_3,inputs.payload.responses.valences)||!sameMap(observed?.beliefs_raw_1_to_7,session.answers))throw Error('beliefs')
 let tolerances=null,toleranceState=readTolerances(storage)
 try{tolerances=toBackendTolerances(toleranceState)}catch{if(requireTolerances)throw Error('tolerances')}
 const payload={schema_version:'adaptive_hotco_bootstrap_1.0',completed_passport:r,availability,...(tolerances?{environmental_tolerances:tolerances}:{})}
 const fingerprint=JSON.stringify({inputs:inputs.fingerprint,session:r.session_id,beliefs:session.answers,artifact:r.artifact?.sha256,availability,tolerances})
 return {agent,plan,valences,availability,tolerances,toleranceState,inputs,payload,fingerprint}
}
export function validPassport(result,source){
 const p=result?.cognitive_passport
 return p?.agent_id===source.agent&&p.schema_version==='2.0'&&typeof p.lineage?.passport_id==='string'&&p.lineage.revision===1&&p.lineage.origin==='initial_adaptive_calibration'&&p.adaptive_passport?.belief_cells_total===44&&p.profile?.needs&&p.profile?.beliefs&&p.deliberation&&sameMap(p.profile?.availability,source.availability)&&sameMap(p.observed_measurements?.beliefs_raw_1_to_7,source.payload.completed_passport.observed_measurements.beliefs_raw_1_to_7)&&source.tolerances&&Object.keys(source.tolerances).every(k=>typeof p.profile.environmental_tolerances?.[k]==='number'&&Math.abs(p.profile.environmental_tolerances[k]-source.tolerances[k])<1e-8)&&MODES.every(m=>p.profile.beliefs[m])
}
export function readPassport(storage,source){
 try{const item=JSON.parse(storage.getItem(PASSPORT_KEY)||'null');if(item?.version===1&&item.fingerprint===source.fingerprint&&validPassport(item.response,source))return item}catch{}
 return null
}
export function savePassport(storage,source,result){
 if(!validPassport(result,source))throw Error('response')
 const value={version:1,confirmedAt:new Date().toISOString(),sourceSnapshot:JSON.parse(JSON.stringify(source)),fingerprint:source.fingerprint,createdAt:new Date().toISOString(),weekStart:source.inputs.weekStart,goalPoints:source.inputs.goalPoints,needsSource:source.inputs.needsSource,conversion:source.inputs.conversion,availability:source.availability,tolerancesRaw:{...source.toleranceState.answers},toleranceConversion:'rating_minus1_div6_v1',temperatureScope:'independent_heat_cold',response:result}
 const previous=storage.getItem(PASSPORT_KEY)
 if(previous){try{const old=JSON.parse(previous),id=old.response?.cognitive_passport?.lineage?.passport_id;if(id&&old.fingerprint!==source.fingerprint)storage.setItem(`${PASSPORT_KEY}.archive.${id}`,previous)}catch(e){if(!(e instanceof SyntaxError))throw e}}
 storage.setItem(PASSPORT_KEY,JSON.stringify(value));return value
}
