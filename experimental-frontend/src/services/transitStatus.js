// Explain why no transit route is shown without silently changing availability
// or the user's total walking limit.
export function transitNotice(result,locale='en'){
 if(!result)return null
 if(result.routing?.routes?.some(r=>r.available===true&&r.mode_key==='pt'))return null
 const de=locale==='de',audit=result.routing?.provider_audit?.otp
 if(result.journey_availability?.pt===false)return {message:de?'ÖPNV ist für diese Suche deaktiviert. Prüfe die verfügbaren Verkehrsmittel in den Einstellungen.':'Public transport is disabled for this search. Check available transport in Settings.'}
 if(audit?.status==='DISABLED')return {message:de?'Die ÖPNV-Suche ist derzeit nicht eingerichtet.':'Public transport search is not configured yet.'}
 if(audit?.status==='UNAVAILABLE')return {message:de?'Der ÖPNV-Dienst ist gerade nicht erreichbar. Andere verfügbare Wege werden weiterhin angezeigt.':'The public transport service is currently unavailable. Other available routes are still shown.'}
 const rejected=audit?.rejected_itineraries||[]
 const walking=rejected.filter(r=>r.reason==='walking_limit_exceeded'&&Number.isFinite(r.walk_distance_meters))
 if(walking.length){
  const minimum=Math.ceil(Math.min(...walking.map(r=>r.walk_distance_meters))),limit=audit.max_walk_meters
  const retryLimit=Math.ceil(minimum/50)*50
  return {message:de?`Die gefundenen ÖPNV-Verbindungen überschreiten dein Gehlimit${Number.isFinite(limit)?' von '+limit+' m':''}. Sie benötigen insgesamt mindestens ${minimum} m zu Fuß.`:`The returned public transport options exceed your walking limit${Number.isFinite(limit)?' of '+limit+' m':''}. They require at least ${minimum} m of walking in total.`,retryLimit:retryLimit<=10000?retryLimit:null}
 }
 if(rejected.some(r=>r.reason==='walking_unavailable'))return {message:de?'Die gefundenen ÖPNV-Verbindungen benötigen Fußwege. Gehen ist in deinen Einstellungen deaktiviert.':'The returned public transport options require walking. Walking is disabled in your settings.'}
 if(audit?.status==='NO_MATCHING_ITINERARIES')return {message:de?'Für diese Orte und Abfahrtszeit wurde keine passende ÖPNV-Verbindung gefunden.':'No matching public transport itinerary was found for these places and departure time.'}
 return null
}
