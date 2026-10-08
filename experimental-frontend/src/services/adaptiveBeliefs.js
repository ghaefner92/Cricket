import {GOAL_KEYS,validPoints,totalPoints,localWeekStart} from '../stores/weeklyGoals.js'
import {allAnswered,toBackendValences} from '../stores/valences.js'
import {apiFetch} from './apiTransport.js'
export const SCHEMA='adaptive_cognitive_passport_onboarding_1.0'
export const SESSION_KEY='imiq.experimental.adaptive-beliefs.v1'
export const PROFILE_KEY='imiq.experimental.adaptive-profile.v1'
export const NEED_MAP={env:'pro_env',health_activity:'physical',crowding:'privacy',flex:'autonomy',cost:'cost',time:'speed',safety_accident:'safety_accident',safety_crime:'safety_crime',comfort_physical:'comfort',reliable:'reliable',health_infection:'health_infection'}
export const isRating=value=>Number.isInteger(value)&&value>=1&&value<=7
export const stepRating=(value,direction)=>Math.max(1,Math.min(7,(isRating(value)?value:4)+direction))
export function buildInputs(agentId,plan,valences){
 if(!agentId||plan?.weekStart!==localWeekStart()||plan.budget!==10||!validPoints(plan.points)||totalPoints(plan.points)!==10)throw Error('goals')
 if(!allAnswered(valences))throw Error('valences')
 const needs=Object.fromEntries(GOAL_KEYS.map(key=>[NEED_MAP[key],1+Math.round(6*plan.points[key]/10)]))
 const payload={schema_version:SCHEMA,agent_id:agentId,responses:{needs,valences:toBackendValences(valences)}}
 return {payload,weekStart:plan.weekStart,goalPoints:{...plan.points},needsSource:'weekly_goal_allocation',conversion:'points_div10_round_to_1_7_v1',fingerprint:JSON.stringify({weekStart:plan.weekStart,payload})}
}
export function validStart(value){
 return value?.schema_version===SCHEMA&&value.stage==='questions'&&typeof value.session_id==='string'&&typeof value.artifact?.sha256==='string'&&Array.isArray(value.questions)&&value.questions.length===4&&new Set(value.questions.map(q=>q.cell_id)).size===4&&value.questions.every(q=>['walk','bike','pt','car'].includes(q.hotco_mode)&&Object.values(NEED_MAP).includes(q.model_need)&&q.cell_id===`${q.hotco_mode}__${q.model_need}`&&q.response_scale?.minimum===1&&q.response_scale?.maximum===7)
}
export function validAnswers(start,answers,complete=false){
 return validStart(start)&&answers&&typeof answers==='object'&&!Array.isArray(answers)&&Object.keys(answers).every(key=>start.questions.some(q=>q.cell_id===key)&&isRating(answers[key]))&&(!complete||Object.keys(answers).length===4)
}
export function readSession(storage,inputs){
 try{const data=JSON.parse(storage.getItem(SESSION_KEY)||'null');if(data?.fingerprint===inputs.fingerprint&&validStart(data.start)&&data.start.agent_id===inputs.payload.agent_id&&validAnswers(data.start,data.answers)&&Number.isInteger(data.index)&&data.index>=0&&data.index<4)return data}catch{}
 return null
}
export function writeSession(storage,inputs,start,answers,index){
 if(!validAnswers(start,answers)||!Number.isInteger(index)||index<0||index>3)throw Error('Invalid belief draft')
 storage.setItem(SESSION_KEY,JSON.stringify({fingerprint:inputs.fingerprint,start,answers:{...answers},index,conversion:inputs.conversion,goalPoints:inputs.goalPoints,needsSource:inputs.needsSource}))
}
export function completePayload(start,answers){if(!validAnswers(start,answers,true))throw Error('Four direct responses required');return {schema_version:SCHEMA,start,answers:{...answers}}}
export async function postJSON(path,payload,signal){
 const timeout=new AbortController(),timer=setTimeout(()=>timeout.abort(),20000)
 const combined=signal?AbortSignal.any([signal,timeout.signal]):timeout.signal
 try{
  const response=await apiFetch(path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload),signal:combined})
  if(!response.ok)throw Error(`HTTP ${response.status}`)
  return await response.json()
 }finally{clearTimeout(timer)}
}
