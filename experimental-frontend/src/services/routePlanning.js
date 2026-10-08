import {validAvailability} from './appSettings.js'
import {apiFetch} from './apiTransport.js'
export function coordinate(lat, lon) {
  if ([lat, lon].some(v => v === null || v === undefined || String(v).trim() === '')) throw Error('coordinates')
  const point = { lat: Number(lat), lon: Number(lon) }
  if (!Number.isFinite(point.lat) || !Number.isFinite(point.lon) || Math.abs(point.lat) > 90 || Math.abs(point.lon) > 180) throw Error('coordinates')
  return point
}
export function routePayload(passport, values, now = new Date()) {
  if (!passport?.profile?.needs || !passport?.profile?.availability) throw Error('passport')
  const limit = Number(values.maxWalk)
  if (String(values.maxWalk).trim() === '' || !Number.isFinite(limit) || limit < 0 || limit > 10000) throw Error('walking')
  if(values.availability!==undefined&&!validAvailability(values.availability))throw Error('availability')
  return {
    ...(values.availability?{journey_availability:{...values.availability}}:{}),
    search_id: `route-${crypto.randomUUID()}`, datetime: now.toISOString(),
    cognitive_passport: passport,
    start: coordinate(values.startLat, values.startLon), stop: coordinate(values.stopLat, values.stopLon),
    max_walk_m: limit,
    contextual_query: { query_orion: true, entity_families: ['weather'], weather_radius_meters: 5000 }
  }
}
export async function searchRoutes(payload, signal, timeoutMs = 180000) {
  const timeout = new AbortController(), timer = setTimeout(() => timeout.abort(), timeoutMs)
  try {
    const response = await apiFetch('/api/dyconet/routed-contextual-deliberation', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload),
      signal: signal ? AbortSignal.any([signal, timeout.signal]) : timeout.signal
    })
    if (!response.ok) throw Error(response.status === 502 || response.status === 503 ? 'unavailable' : 'request')
    const result = await response.json()
    if (result?.schema_version !== 'routed-contextual-deliberation-v1' || !Array.isArray(result?.routing?.routes) || !Array.isArray(result.route_audit)) throw Error('response')
    if(payload.journey_availability&&(!validAvailability(result.journey_availability)||Object.keys(payload.journey_availability).some(mode=>payload.journey_availability[mode]!==result.journey_availability[mode])))throw Error('response')
    return result
  } catch (error) {
    if (timeout.signal.aborted && !signal?.aborted) throw Error('timeout')
    throw error
  } finally { clearTimeout(timer) }
}
export function cardsFromResult(result) {
  return (result?.routing?.routes || []).map(route => {
    const audit = (result.route_audit || []).find(a => a.routing_rank === route.rank && a.mode_key === route.mode_key)
    const candidate = (result.contextual_deliberation?.candidate_results || []).find(c => c.route_id === audit?.route_id)
    return { route, audit, candidate }
  })
}
export function finiteNumber(value) { return typeof value === 'number' && Number.isFinite(value) }
export function environmentalRows(candidate) {
  const raw = candidate?.route_context?.raw_observations || candidate?.route_context?.segments?.flatMap(s => s.raw_observations || []) || []
  const seen = new Set()
  return raw.filter(o => {
    const key = JSON.stringify([o.source_entity_id, o.variable, o.raw_value, o.numeric_value, o.timestamp, o.unit])
    if (seen.has(key)) return false
    seen.add(key); return true
  })
}
export function needChanges(candidate) {
  return Object.entries(candidate?.context_perturbation?.need_perturbations || {}).filter(([,v]) => finiteNumber(v) && Math.abs(v) > 1e-10)
}
