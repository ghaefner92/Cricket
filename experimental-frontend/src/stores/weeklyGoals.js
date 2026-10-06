export const POINT_BUDGET = 10
// Canonical need identifiers, with user-facing weekly goal wording in i18n.
export const GOAL_KEYS = ['env','health_activity','cost','time','comfort_physical','reliable','flex','safety_crime','health_infection','crowding','safety_accident']
const prefix = 'imiq.experimental.weekly-goals.v1.'
export function localWeekStart(date = new Date()) {
  const monday = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  monday.setDate(monday.getDate() - (monday.getDay() + 6) % 7)
  return `${monday.getFullYear()}-${String(monday.getMonth()+1).padStart(2,'0')}-${String(monday.getDate()).padStart(2,'0')}`
}
export function blankPlan(weekStart = localWeekStart()) {
  return {version:1,weekStart,budget:POINT_BUDGET,points:Object.fromEntries(GOAL_KEYS.map(key=>[key,0]))}
}
export function validPoints(points) {
  return points && typeof points==='object' && !Array.isArray(points) &&
    Object.keys(points).length===GOAL_KEYS.length &&
    GOAL_KEYS.every(key=>Number.isInteger(points[key]) && points[key]>=0 && points[key]<=POINT_BUDGET) &&
    totalPoints(points)<=POINT_BUDGET
}
export function totalPoints(points) { return GOAL_KEYS.reduce((sum,key)=>sum+(points[key]||0),0) }
export function adjustPoints(points,key,delta) {
  if(!validPoints(points) || !GOAL_KEYS.includes(key) || ![-1,1].includes(delta)) return points
  const value=points[key]+delta
  if(value<0 || value>POINT_BUDGET || (delta>0 && totalPoints(points)>=POINT_BUDGET)) return points
  return {...points,[key]:value}
}
export function rankedGoals(points) {
  const sorted=GOAL_KEYS.filter(key=>points[key]>0).sort((a,b)=>points[b]-points[a] || GOAL_KEYS.indexOf(a)-GOAL_KEYS.indexOf(b))
  let rank=0,previous=-1
  return sorted.map(key=>{
    if(points[key]!==previous){rank++;previous=points[key]}
    return {key,points:points[key],rank}
  })
}
export function loadPlan(storage, weekStart = localWeekStart()) {
  // Only accept complete, valid current-week plans. Keep other weeks untouched.
  for(const suffix of ['draft','saved']){
    try {
      const raw=storage.getItem(`${prefix}${weekStart}.${suffix}`)
      const value=raw ? JSON.parse(raw) : null
      if(value?.version===1 && value.weekStart===weekStart && value.budget===POINT_BUDGET && validPoints(value.points)) {
        return {...blankPlan(weekStart),points:{...value.points}}
      }
    } catch { /* Corrupt or inaccessible local data never blocks the page. */ }
  }
  return blankPlan(weekStart)
}
export function savePlan(storage,plan,confirmed=false) {
  if(plan.version!==1 || plan.budget!==POINT_BUDGET || plan.weekStart!==localWeekStart() || !validPoints(plan.points)) throw new Error('Invalid weekly plan')
  if(confirmed && totalPoints(plan.points)!==POINT_BUDGET) throw new Error('Incomplete allocation')
  const payload={version:1,weekStart:plan.weekStart,budget:POINT_BUDGET,points:{...plan.points}}
  storage.setItem(`${prefix}${plan.weekStart}.${confirmed?'saved':'draft'}`,JSON.stringify(payload))
  return payload
}
export function formatWeek(weekStart,locale) {
  const [year,month,day]=weekStart.split('-').map(Number)
  const start=new Date(year,month-1,day)
  const end=new Date(year,month-1,day+6)
  const formatter=new Intl.DateTimeFormat(locale==='de'?'de-DE':'en-GB',{day:'numeric',month:'short'})
  return `${formatter.format(start)} – ${formatter.format(end)}`
}
