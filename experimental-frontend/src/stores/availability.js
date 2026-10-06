export const MODES=['walk','bike','pt','car']
export const KEY='imiq.experimental.availability.v1'
export function blankAvailability(){return {version:1,answers:Object.fromEntries(MODES.map(m=>[m,null]))}}
export function completeAvailability(state){return MODES.every(m=>typeof state?.answers?.[m]==='boolean')&&MODES.some(m=>state.answers[m]===true)}
export function readAvailability(storage){try{const s=JSON.parse(storage.getItem(KEY));if(s?.version===1&&MODES.every(m=>s.answers?.[m]===null||typeof s.answers?.[m]==='boolean'))return {version:1,answers:Object.fromEntries(MODES.map(m=>[m,s.answers[m]]))}}catch{}return blankAvailability()}
export function writeAvailability(storage,state){if(!MODES.every(m=>state.answers[m]===null||typeof state.answers[m]==='boolean'))throw Error('Invalid availability');storage.setItem(KEY,JSON.stringify(state))}
export function toBackendAvailability(state){if(!completeAvailability(state))throw Error('Answer all modes and choose at least one');return Object.fromEntries(MODES.map(m=>[m,state.answers[m]]))}
