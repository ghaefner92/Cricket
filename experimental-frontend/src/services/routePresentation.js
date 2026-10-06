import {cardsFromResult,environmentalRows,finiteNumber} from './routePlanning.js'
import {modelMode} from './routeExplanation.js'

// Consensus across separate what-if runs; never compare diagonal scores
// from different simulations as if they were one route competition.
export function recommendation(result){
 const cards=cardsFromResult(result),available=cards.filter(c=>c.route.available===true)
 const runs=available.map(c=>c.candidate?.hotco)
 const winners=runs.map(h=>h?.winner)
 const clear=runs.length>0&&runs.every(h=>h?.ambiguity_state==='CLEAR'&&finiteNumber(h.final_action_activations?.[h.winner]))
 const unanimous=clear&&winners.every(w=>w===winners[0])
 const matching=unanimous?available.filter(c=>modelMode(c.route.mode_key)===winners[0]):[]
 const primary=matching[0]||available[0]||null
 const state=matching.length?'clear':available.some(c=>!c.candidate)?'incomplete':runs.some(h=>h?.ambiguity_state==='NEAR_TIE')?'similar':clear?'disagreement':'uncertain'
 return {cards,primary,state,winner:matching.length?winners[0]:null,modeOnly:matching.length>1,
  others:cards.filter(c=>c!==primary)}
}

// Descriptive links in the Passport, not causal attributions of the winner.
export function goalLinks(passport,mode){
 const needs=passport?.profile?.needs||{},beliefs=passport?.profile?.beliefs?.[modelMode(mode)]||{}
 return Object.entries(needs).filter(([name,value])=>finiteNumber(value)&&value>0&&finiteNumber(beliefs[name])&&beliefs[name]>0)
  .map(([name,priority])=>({name,priority,belief:beliefs[name]}))
  .sort((a,b)=>b.priority-a.priority||b.belief-a.belief||a.name.localeCompare(b.name)).slice(0,3)
}

const VARIABLES=['temperature','humidity','rain','windSpeed','windGust','uvIndex','lightIntensity']
export function weatherTiles(candidate){
 const rows=environmentalRows(candidate)
 return VARIABLES.map(variable=>{
  const readings=rows.filter(o=>o.variable===variable&&o.status==='OBSERVED'&&finiteNumber(o.numeric_value)&&o.source_metadata?.normalization_eligible!==false&&o.unit)
  const units=[...new Set(readings.map(o=>o.unit))]
  if(!readings.length||units.length!==1)return {variable,known:false,unit:null,min:null,max:null}
  const values=readings.map(o=>o.numeric_value)
  return {variable,known:true,unit:units[0],min:Math.min(...values),max:Math.max(...values)}
 })
}
