export function delayText(seconds,locale='en'){
 const de=locale==='de'
 if(!Number.isInteger(seconds))return de?'Keine Echtzeitprognose':'No live prediction'
 if(seconds===0)return de?'Laut Prognose pünktlich':'Predicted on time'
 const amount=Math.abs(seconds),n=new Intl.NumberFormat(locale,{maximumFractionDigits:1}).format(amount>=60?amount/60:amount)
 return `${n} ${amount>=60?'min':'s'} ${seconds<0?(de?'früher':'early'):(de?'später':'late')}`
}
export function clockText(value,locale='en'){
 if(typeof value!=='number'||!Number.isFinite(value))return '—'
 return new Intl.DateTimeFormat(locale,{hour:'2-digit',minute:'2-digit',timeZone:'Europe/Berlin'}).format(new Date(value))
}
export function transitLegs(route){
 return (route?.legs||[]).map((leg,index)=>({index,mode:leg.mode,duration:leg.duration_seconds,details:leg.transit||{},line:leg.transit?.line||null}))
}
export function transitTiming(route,now=Date.now()){
 const boarding=(route?.legs||[]).find(leg=>leg.mode==='pt')?.transit
 const leave=route?.transit?.start_time_ms,board=boarding?.start_time_ms
 const known=Number.isFinite(leave)&&Number.isFinite(board)
 return {leave,board,arrival:route?.transit?.end_time_ms,stop:boarding?.from?.name,
  line:boarding?.line,known,expired:known&&leave<now-30000,
  accessMinutes:known?Math.max(0,Math.ceil((board-leave)/60000)):null}
}
export function sortTransitCards(cards,now=Date.now()){
 return [...cards].sort((a,b)=>{
  const x=transitTiming(a.route,now),y=transitTiming(b.route,now)
  return Number(x.expired)-Number(y.expired)||Number(!x.known)-Number(!y.known)||
   (x.known&&y.known?x.board-y.board||x.leave-y.leave:0)||a.route.rank-b.route.rank
 })
}
export function transitGroups(cards,now=Date.now()){
 const groups=new Map()
 for(const card of sortTransitCards(cards,now)){
  const legs=(card.route.legs||[]).filter(l=>l.mode==='pt')
  const key=JSON.stringify(legs.map(l=>[l.transit?.source_mode,l.transit?.gtfs_route_id||l.transit?.line,
   l.transit?.from?.stop?.gtfsId||l.transit?.from?.name,l.transit?.to?.stop?.gtfsId||l.transit?.to?.name]))
  const id=key==='[]'?`route-${card.route.rank}`:key
  if(!groups.has(id))groups.set(id,{key:id,label:legs.map(l=>l.transit?.line||l.transit?.source_mode||'Transit').join(' → '),cards:[]})
  groups.get(id).cards.push(card)
 }
 return [...groups.values()]
}
