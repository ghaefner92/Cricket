import {cardsFromResult,environmentalRows,finiteNumber} from './routePlanning.js'
import {modelMode} from './routeExplanation.js'
import {sortTransitCards,transitGroups} from './transitPresentation.js'

// Consensus across separate what-if runs; never compare diagonal scores
// from different simulations as if they were one route competition.
export function recommendation(result){
 const cards=cardsFromResult(result),available=cards.filter(c=>c.route.available===true)
 const runs=available.map(c=>c.candidate?.hotco)
 const winners=runs.map(h=>h?.winner)
 const clear=runs.length>0&&runs.every(h=>h?.ambiguity_state==='CLEAR'&&finiteNumber(h.final_action_activations?.[h.winner]))
 const unanimous=clear&&winners.every(w=>w===winners[0])
 const matching=unanimous?available.filter(c=>modelMode(c.route.mode_key)===winners[0]):[]
 const primary=(winners[0]==='pt'?sortTransitCards(matching)[0]:matching[0])||available[0]||null
 const state=matching.length?'clear':available.some(c=>!c.candidate)?'incomplete':runs.some(h=>h?.ambiguity_state==='NEAR_TIE')?'similar':clear?'disagreement':'uncertain'
 return {cards,primary,state,winner:matching.length?winners[0]:null,modeOnly:matching.length>1,
  others:cards.filter(c=>c!==primary)}
}

export function modeGroups(result,now=Date.now()){
 const groups=new Map()
 for(const card of cardsFromResult(result)){
  const mode=modelMode(card.route.mode_key)
  if(!groups.has(mode))groups.set(mode,{mode,cards:[]})
  groups.get(mode).cards.push(card)
 }
 return [...groups.values()].map(group=>{
  const available=group.cards.filter(c=>c.route.available===true)
  const cards=group.mode==='pt'?sortTransitCards(available.length?available:group.cards,now):available.length?available:group.cards
  return {...group,representative:cards[0],departureGroups:group.mode==='pt'?transitGroups(cards,now):[]}
 }).sort((a,b)=>['walk','bike','car','pt'].indexOf(a.mode)-['walk','bike','car','pt'].indexOf(b.mode))
}

// Descriptive links in the Passport, not causal attributions of the winner.
export function goalLinks(passport,mode){
 const needs=passport?.profile?.needs||{},beliefs=passport?.profile?.beliefs?.[modelMode(mode)]||{}
 return Object.entries(needs).filter(([name,value])=>finiteNumber(value)&&value>0&&finiteNumber(beliefs[name])&&beliefs[name]>0)
  .map(([name,priority])=>({name,priority,belief:beliefs[name]}))
  .sort((a,b)=>b.priority-a.priority||b.belief-a.belief||a.name.localeCompare(b.name)).slice(0,3)
}

const VARIABLES=['temperature','humidity','rain','windSpeed','windGust','uvIndex','lightIntensity']
export function displayedWeatherRows(candidate){
 const rows=environmentalRows(candidate)
 const rain=rows.filter(o=>o.variable==='rain'&&o.status==='OBSERVED'&&finiteNumber(o.numeric_value)&&o.source_metadata?.normalization_eligible!==false&&o.source_entity_id==='Sensor:Weather:Winfred'&&o.source_metadata?.source_attribute==='rainIntensity'&&['mm/h','mm/hour','mm/hr'].includes(String(o.unit).toLowerCase())&&o.numeric_value>=0&&o.numeric_value<=450)
  .sort((a,b)=>(Date.parse(b.source_metadata?.last_report_at)||0)-(Date.parse(a.source_metadata?.last_report_at)||0)).slice(0,1)
 return [...rows.filter(o=>!['rain','rainIntensity','cumulativeRainfall','rainHourly','rainDaily','rainWeekly','rainMonthly','rainYearly'].includes(o.variable)),...rain]
}
export function weatherTiles(candidate){
 const rows=displayedWeatherRows(candidate)
 return VARIABLES.map(variable=>{
  let readings=rows.filter(o=>o.variable===variable&&o.status==='OBSERVED'&&finiteNumber(o.numeric_value)&&o.source_metadata?.normalization_eligible!==false&&o.unit)
  const units=[...new Set(readings.map(o=>o.unit))]
  if(!readings.length||units.length!==1)return {variable,known:false,unit:null,min:null,max:null}
  const values=readings.map(o=>o.numeric_value)
  const report=readings[0].source_metadata
  const lastReport=report?.last_report_time_provenance==='EXPLICIT_ATTRIBUTE_RECEIVED_AT'&&Number.isFinite(Date.parse(report.last_report_at))?report.last_report_at:null
  return {variable,known:true,unit:units[0],min:Math.min(...values),max:Math.max(...values),lastReport}
 })
}

export function weatherInfo(candidate){
 if(!candidate)return {status:'NO_CONTEXT',timestamps:[]}
 const rows=environmentalRows(candidate).filter(o=>o.source==='orion'&&o.status==='OBSERVED')
 const timestamps=[...new Set(rows.map(o=>o.timestamp).filter(t=>t&&Number.isFinite(Date.parse(t))))].sort((a,b)=>Date.parse(a)-Date.parse(b))
 const source=candidate.source_status?.weather
 const status=weatherTiles(candidate).some(t=>t.known)?'AVAILABLE':source==='UNAVAILABLE'?'UNAVAILABLE':source==='MALFORMED'?'MALFORMED':source==='EMPTY'?'EMPTY':source==='OBSERVED'?'UNUSABLE':'NOT_QUERIED'
 return {status,timestamps}
}
