import {validActive,RENEWAL_KEY} from './passportLifecycle.js'
import {isNative} from './nativePlatform.js'
import {PASSPORT_KEY} from './passport.js'
import {listSearches,mergeHistory,validRecord,clearHistory} from './simulationHistory.js'
const exact=['imiq.experimental.agent-id.v1','imiq.experimental.onboarding-step.v1','imiq.experimental.companion-name','imiq.experimental.companion.v1','imiq.experimental.companion-draft.v1','imiq.experimental.availability.v1','imiq.experimental.valences.v1','imiq.experimental.valences-draft.v1','imiq.experimental.tolerances.v1','imiq.experimental.tolerances.v2','imiq.experimental.adaptive-beliefs.v1','imiq.experimental.adaptive-profile.v1','imiq.experimental.language',PASSPORT_KEY,RENEWAL_KEY,'cricket.settings.v1','cricket.recent-places.v1','cricket.chosen-journey.v1']
export const allowedKey=key=>key==='cricket.connection.v1'||exact.includes(key)||key.startsWith('imiq.experimental.weekly-goals.v1.')||key.startsWith(PASSPORT_KEY+'.archive.')
export function storedData(storage){const values={};for(let i=0;i<storage.length;i++){const key=storage.key(i);if(allowedKey(key))values[key]=storage.getItem(key)}return values}
export async function exportData(storage){return {schema:'cricket-backup-v1',exported_at:new Date().toISOString(),storage:storedData(storage),simulations:await listSearches()}}
export function validateBackup(value){if(value?.schema!=='cricket-backup-v1'||!value.storage||typeof value.storage!=='object'||Array.isArray(value.storage)||!Object.entries(value.storage).every(([key,text])=>allowedKey(key)&&typeof text==='string')||!Array.isArray(value.simulations)||!value.simulations.every(validRecord))throw Error('backup');if(value.storage[PASSPORT_KEY]&&!validActive(JSON.parse(value.storage[PASSPORT_KEY])))throw Error('backup');return value}
export async function importData(storage,input){const backup=validateBackup(input),before=storedData(storage);for(const [key,value]of Object.entries(before)){if(key.startsWith(PASSPORT_KEY+'.archive.')&&backup.storage[key]&&backup.storage[key]!==value)throw Error('backup')}try{for(const key of Object.keys(before))storage.removeItem(key);for(const [key,value]of Object.entries(backup.storage))storage.setItem(key,value);for(const [key,value]of Object.entries(before)){if(key.startsWith(PASSPORT_KEY+'.archive.')&&!backup.storage[key])storage.setItem(key,value)}if(before[PASSPORT_KEY]&&before[PASSPORT_KEY]!==backup.storage[PASSPORT_KEY]){const old=JSON.parse(before[PASSPORT_KEY]);if(validActive(old))storage.setItem(PASSPORT_KEY+'.archive.'+old.response.cognitive_passport.lineage.passport_id,before[PASSPORT_KEY])}await mergeHistory(backup.simulations)}catch(error){for(const key of Object.keys(storedData(storage)))storage.removeItem(key);for(const [key,value]of Object.entries(before))storage.setItem(key,value);throw error}}
export async function resetApp(storage){await clearHistory();for(const key of Object.keys(storedData(storage)))storage.removeItem(key)}
export async function downloadJSON(value,name){
 if(isNative()){
  const [{Filesystem,Directory,Encoding},{Share}]=await Promise.all([import('@capacitor/filesystem'),import('@capacitor/share')])
  const file=await Filesystem.writeFile({path:name.replace(/[^a-zA-Z0-9._-]/g,'_'),directory:Directory.Cache,data:JSON.stringify(value,null,2),encoding:Encoding.UTF8})
  await Share.share({title:'Cricket backup',files:[file.uri]})
  return
 }
 const url=URL.createObjectURL(new Blob([JSON.stringify(value,null,2)],{type:'application/json'})),link=document.createElement('a');link.href=url;link.download=name;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000)
}
