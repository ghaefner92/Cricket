import wording from './choiceWordings.json' with {type:'json'}
function catalog(n){return n?.wording_version===wording.version?wording:wording.legacy?.[n?.wording_version]}
export function conversationOptions(t,w=wording){
 const rules=new Set(t?.statements?.map(row=>row.rule)||[])
 const tone=rules.has('GOALS_UNKNOWN')||rules.has('NO_CONTEXT_SIMULATION')?'uncertain':
  ['GOALS_OPPOSITION','DIFFERENT_CLEAR','CHOICE_BELOW_AMBIGUOUS_PAIR'].some(rule=>rules.has(rule))?'reflective':'warm'
 return {tone,questions:(w.questions?.[t?.language]||[]).filter(q=>q.requires.every(rule=>rules.has(rule)))}
}
export function conversationalVoice(value){return value?.narration?.wording_version===wording.version&&validVoice(value.narration,value.explanation)}
export function validVoice(n,t){
 const w=catalog(n)
 if(n===undefined)return true // phase-3 saved records
 if(!t||!n||n.schema_version!=='cricket-choice-voice-v1'||n.language!==t.language||
 n.source_template_id!==t.template_id||n.source_evidence_id!==t.source_evidence_id||
 n.choice_event_id!==t.choice_event_id||!w||
 n.prompt_version!==(w.prompt_version||'choice-voice-selector-1.0')||n.narration_mode!=='verified_phrase_selection'||
 n.provider!=='groq'||n.model!=='openai/gpt-oss-120b'||typeof n.narration_id!=='string')return false
 if(n.status==='TEMPLATE_FALLBACK')return n.generated_by_llm===false&&n.selection===null&&['NOT_CONFIGURED','PROVIDER_OR_VALIDATION_FAILURE'].includes(n.reason)
 if(n.status!=='AVAILABLE'||n.generated_by_llm!==true||n.reason!==null)return false
 const s=n.selection
 const modern=!!w.questions,options=conversationOptions(t,w)
 if(!s||Object.keys(s).sort().join(',')!==(modern?'emphasis,opening,question,tone,variants':'emphasis,opening,variants')||!Number.isInteger(s.opening)||
 !w.openings[t.language]?.[s.opening]||!['affinities','tensions'].includes(s.emphasis)||
 !s.variants||Array.isArray(s.variants)||Object.keys(s.variants).length!==t.statements.length)return false
 if(modern&&(s.tone!==options.tone||!w.tones[s.tone]?.includes(s.opening)||!options.questions.some(q=>q.id===s.question)))return false
 return t.statements.every(row=>{
  const v=s.variants[row.id],pair=w.replacements[t.language]?.[row.rule]
  return Number.isInteger(v)&&(v===0||v===1&&pair&&row.text.startsWith(pair[0]))
 })
}
export function voicedStatements(value){
 const t=value?.explanation,n=value?.narration,w=catalog(n)
 if(!t)return []
 if(!validVoice(n,t))return t.statements
 const modern=!!w?.questions
 if(!n?.generated_by_llm&&!modern)return t.statements
 return t.statements.map(row=>{
  const pair=w.replacements[t.language]?.[row.rule]
  const variant=n.generated_by_llm?n.selection.variants[row.id]:1
  return {...row,text:variant===1&&pair&&row.text.startsWith(pair[0])?pair[1]+row.text.slice(pair[0].length):row.text}
 })
}
export function voiceOpening(value){
 const n=value?.narration
 if(conversationalVoice(value)&&!n.generated_by_llm){const {tone}=conversationOptions(value.explanation);return wording.openings[n.language][wording.tones[tone][0]]}
 return n?.generated_by_llm&&validVoice(n,value.explanation)?catalog(n).openings[n.language][n.selection.opening]:''
}
export function voiceQuestion(value){
 if(!conversationalVoice(value))return ''
 const n=value.narration,{questions}=conversationOptions(value.explanation)
 const id=n.generated_by_llm?n.selection.question:
  questions.find(q=>q.id==='mixed')?.id||questions.find(q=>q.id==='tension')?.id||questions.find(q=>q.id==='support')?.id||'priorities'
 return questions.find(q=>q.id===id)?.text||''
}
export function voiceOrder(value){
 if(value?.explanation?.presentation_version==='choice-story-1.0')return value?.narration?.selection?.emphasis==='tensions'?['selection','tensions','affinities','balance','comparison','uncertainty']:['selection','affinities','tensions','balance','comparison','uncertainty']
 return value?.narration?.selection?.emphasis==='tensions'?['selection','comparison','tensions','affinities','context','uncertainty']:['selection','comparison','affinities','tensions','context','uncertainty']
}
