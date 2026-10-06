export const TOLERANCES=['crowding','rain','darkness','heat','cold','wind','traffic']
export const TOLERANCE_KEY='imiq.experimental.tolerances.v2'
export const validRating=v=>Number.isInteger(v)&&v>=1&&v<=7
export function blankTolerances(){return {version:2,index:0,confirmed:false,answers:Object.fromEntries(TOLERANCES.map(k=>[k,null]))}}
export function validTolerances(s){return s?.version===2&&Number.isInteger(s.index)&&s.index>=0&&s.index<TOLERANCES.length&&typeof s.confirmed==='boolean'&&s.answers&&Object.keys(s.answers).length===TOLERANCES.length&&TOLERANCES.every(k=>s.answers[k]===null||validRating(s.answers[k]))}
export const allTolerancesAnswered=s=>validTolerances(s)&&TOLERANCES.every(k=>validRating(s.answers[k]))
export function readTolerances(storage){try{const s=JSON.parse(storage.getItem(TOLERANCE_KEY));if(validTolerances(s))return s}catch{}
 try{const old=JSON.parse(storage.getItem('imiq.experimental.tolerances.v1'));if(old?.version===1){const s=blankTolerances();for(const k of TOLERANCES)if(validRating(old.answers?.[k]))s.answers[k]=old.answers[k];s.index=3;return s}}catch{}return blankTolerances()}
export function writeTolerances(storage,s,confirmed=false){if(!validTolerances(s)||confirmed&&!allTolerancesAnswered(s))throw Error('tolerances');const value={...s,confirmed,answers:{...s.answers}};storage.setItem(TOLERANCE_KEY,JSON.stringify(value));return value}
export function toBackendTolerances(s){if(!allTolerancesAnswered(s)||!s.confirmed)throw Error('tolerances');return Object.fromEntries(TOLERANCES.map(k=>[k,(s.answers[k]-1)/6]))}
export function stepTolerance(value,delta){if(![-1,1].includes(delta))throw Error('direction');return Math.max(1,Math.min(7,(validRating(value)?value:4)+delta))}
