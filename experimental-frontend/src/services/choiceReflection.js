import {validVoice} from './choiceVoice.js'
export function confirmedSnapshot(storage,record){
 const snapshot=record.confirmed_snapshot
 if(snapshot)return snapshot
 const key='imiq.experimental.cognitive-passport.v1',ref=record.passport_reference
 for(let i=0;i<storage.length;i++){
  const name=storage.key(i)
  if(name!==key&&!name?.startsWith(key+'.archive.'))continue
  try{const item=JSON.parse(storage.getItem(name)),lineage=item?.response?.cognitive_passport?.lineage
   if(lineage?.passport_id===ref?.passport_id&&lineage?.revision===ref?.revision&&item?.fingerprint===ref?.fingerprint)return item
  }catch{}
 }
 return null
}
export function validReflection(value,record,index,language){
 const choice=record.choices?.[index],t=value?.explanation,id=value?.identity
 return value?.schema_version==='cricket-choice-explanation-response-v1'&&
  id?.search_id===record.search_id&&id?.choice_index===index&&id?.route_id===choice?.route_id&&
  id?.mode_key===choice?.mode_key&&id?.chosen_at===choice?.chosen_at&&
  typeof id?.choice_event_id==='string'&&typeof value.evidence_id==='string'&&
  validVoice(value?.narration,t)&&t?.schema_version==='cricket-choice-template-v1'&&t.language===language&&t.generated_by_llm===false&&
  t.choice_event_id===id.choice_event_id&&t.source_evidence_id===value.evidence_id&&
  typeof t.template_id==='string'&&typeof t.message==='string'&&Array.isArray(t.statements)&&
  t.statements.every(s=>typeof s.id==='string'&&typeof s.rule==='string'&&typeof s.section==='string'&&typeof s.text==='string'&&Array.isArray(s.evidence_refs))
}
export async function requestReflection(record,index,snapshot,language,signal){
 const timeout=new AbortController(),timer=setTimeout(()=>timeout.abort(),20000)
 try{
  const response=await fetch('/api/dyconet/choice-explanation',{method:'POST',
   headers:{'Content-Type':'application/json'},signal:signal?AbortSignal.any([signal,timeout.signal]):timeout.signal,
   body:JSON.stringify({record,choice_index:index,confirmed_snapshot:snapshot,language})})
  if(!response.ok)throw Error('reflection')
  const value=await response.json()
  if(!validReflection(value,record,index,language))throw Error('reflection')
  return value
 }finally{clearTimeout(timer)}
}
