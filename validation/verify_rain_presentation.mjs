import assert from 'node:assert/strict'
import {createServer} from '../experimental-frontend/node_modules/vite/dist/node/index.js'
import {fileURLToPath} from 'node:url'
const server=await createServer({root:fileURLToPath(new URL('../experimental-frontend',import.meta.url)),server:{middlewareMode:true}})
let weatherTiles,displayedWeatherRows
try {({weatherTiles,displayedWeatherRows}=await server.ssrLoadModule('/src/services/routePresentation.js'))}
finally {await server.close()}
const reading=(source_entity_id,variable='rain',unit='mm/h',numeric_value=0,attribute='rainIntensity')=>({source_entity_id,variable,unit,numeric_value,status:'OBSERVED',source_metadata:{source_attribute:attribute}})
const winfred=reading('Sensor:Weather:Winfred')
const candidate={route_context:{raw_observations:[reading('Sensor:Weather:Walter'),reading('Sensor:Weather:Winfred','cumulativeRainfall','mm',110.49,'cumulativeRainfall'),winfred]}}
assert.deepEqual(displayedWeatherRows(candidate),[winfred])
const tile=weatherTiles(candidate).find(t=>t.variable==='rain')
assert.equal(tile.known,true)
assert.equal(tile.min,0)
assert.equal(tile.max,0)
assert.equal(tile.lastReport,null)
for(const raw of [reading('Sensor:Weather:Walter'),reading('Sensor:Weather:Winfred','rain','mm'),reading('Sensor:Weather:Winfred','rain','mm/h',-1)]){
 assert.equal(weatherTiles({route_context:{raw_observations:[raw]}}).find(t=>t.variable==='rain').known,false)
}
assert.equal(candidate.route_context.raw_observations.length,3)
console.log('Rain presentation checks passed: Winfred only, zero valid, no accumulation or fallback, raw records unchanged.')
