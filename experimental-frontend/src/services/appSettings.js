export const JOURNEY_MODES=['walk','bike','pt','car']
export function validAvailability(value){return !!value&&typeof value==='object'&&!Array.isArray(value)&&Object.keys(value).length===4&&JOURNEY_MODES.every(mode=>typeof value[mode]==='boolean')&&JOURNEY_MODES.some(mode=>value[mode])}
export function effectiveAvailability(settings,passport){const value=settings?.journeyAvailability??passport?.profile?.availability;if(!validAvailability(value))throw Error('availability');return Object.fromEntries(JOURNEY_MODES.map(mode=>[mode,value[mode]]))}
export const SETTINGS_KEY='cricket.settings.v1'
export const defaults={version:1,motion:'full',textSize:'normal',maxWalk:500,journeyAvailability:null}
export function normalizeSettings(value){return {version:1,journeyAvailability:validAvailability(value?.journeyAvailability)?{...value.journeyAvailability}:null,motion:['full','reduced','off'].includes(value?.motion)?value.motion:'full',textSize:value?.textSize==='large'?'large':'normal',maxWalk:Number.isInteger(value?.maxWalk)&&value.maxWalk>=0&&value.maxWalk<=10000?value.maxWalk:500}}
export function readSettings(storage){try{return normalizeSettings(JSON.parse(storage.getItem(SETTINGS_KEY)||'null'))}catch{return {...defaults}}}
export function saveSettings(storage,value){if(value?.journeyAvailability!=null&&!validAvailability(value.journeyAvailability))throw Error('availability');const settings=normalizeSettings(value);storage.setItem(SETTINGS_KEY,JSON.stringify(settings));return settings}
export function applySettings(settings){if(typeof document==='undefined')return;document.documentElement.dataset.cricketMotion=settings.motion;document.documentElement.dataset.cricketText=settings.textSize}
export function announceSettings(){window.dispatchEvent(new Event('cricket-settings-change'))}

export function quietMotion(){try{return readSettings(window.localStorage).motion!=='full'}catch{return false}}
