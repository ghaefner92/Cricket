export const MODES=['walk','bike','pt','car']
export const VALUES=[-3,-2,-1,0,1,2,3]
const DRAFT='imiq.experimental.valences-draft.v1'
const SAVED='imiq.experimental.valences.v1'
export const blankValences=()=>({version:1,index:0,answers:Object.fromEntries(MODES.map(mode=>[mode,null]))})
export const isValence=value=>Number.isInteger(value)&&VALUES.includes(value)
export function validDraft(value){return value?.version===1 && Number.isInteger(value.index) && value.index>=0 && value.index<MODES.length && value.answers && Object.keys(value.answers).length===4 && MODES.every(mode=>value.answers[mode]===null||isValence(value.answers[mode]))}
export function readValences(storage){
 for(const key of [DRAFT,SAVED]){
  try {const raw=storage.getItem(key);const value=raw?JSON.parse(raw):null;if(validDraft(value))return {version:1,index:value.index,answers:{...value.answers}}}catch{}
 }
 return blankValences()
}
export const allAnswered=draft=>MODES.every(mode=>isValence(draft.answers[mode]))
export function writeValences(storage,draft,confirmed=false){
 if(!validDraft(draft)||confirmed&&!allAnswered(draft))throw Error('Invalid valences')
 const value={version:1,index:draft.index,answers:{...draft.answers}}
 storage.setItem(confirmed?SAVED:DRAFT,JSON.stringify(value))
 return value
}
// Existing onboarding uses these integers directly; backend divides each by 3.
export function toBackendValences(draft){
 if(!validDraft(draft)||!allAnswered(draft))throw Error('All four direct responses required')
 return Object.fromEntries(MODES.map(mode=>[mode,draft.answers[mode]]))
}

// IMIQ valence reactions v2
export function stepValence(current,direction){
 if(![-1,1].includes(direction))throw Error('Direction must be -1 or +1')
 if(current!==null&&!isValence(current))throw Error('Invalid current valence')
 return Math.max(-3,Math.min(3,(current===null?0:current)+direction))
}