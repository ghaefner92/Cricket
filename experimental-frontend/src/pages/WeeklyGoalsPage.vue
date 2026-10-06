<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import GoalScene from '../components/GoalScene.vue'
import { readCompanion } from '../stores/companion.js'
import { POINT_BUDGET, GOAL_KEYS, loadPlan, savePlan, totalPoints, adjustPoints, rankedGoals, localWeekStart, formatWeek } from '../stores/weeklyGoals.js'
import { weeklyMessages } from '../i18n/weeklyGoals.js'
import '../styles/weekly-goals.css'
const props=defineProps({locale:{type:String,default:'en'}})
const emit=defineEmits(['locale-change','back','continue'])
const copy=computed(()=>weeklyMessages[props.locale]||weeklyMessages.en)
let storage
try { storage=window.localStorage } catch { storage={getItem(){return null},setItem(){throw Error('Storage unavailable')}} }
const companion=readCompanion(storage)
const plan=ref(loadPlan(storage))
const saved=ref(false),draftSaved=ref(false),error=ref(false),weekChanged=ref(false)
const selected=ref(GOAL_KEYS[0]),page=ref(0),showAll=ref(false),pulse=ref(0)
const visibleGoals=computed(()=>showAll.value?GOAL_KEYS:GOAL_KEYS.slice(page.value*4,page.value*4+4))
const pageCount=Math.ceil(GOAL_KEYS.length/4)
function selectGoal(key){selected.value=key;page.value=Math.floor(GOAL_KEYS.indexOf(key)/4)}
function changePage(delta){page.value=Math.max(0,Math.min(pageCount-1,page.value+delta));selected.value=GOAL_KEYS[page.value*4]}
let pulseTimer
const total=computed(()=>totalPoints(plan.value.points))
const remaining=computed(()=>POINT_BUDGET-total.value)
const rankings=computed(()=>rankedGoals(plan.value.points))
const rankMap=computed(()=>Object.fromEntries(rankings.value.map(item=>[item.key,item.rank])))
const weekLabel=computed(()=>formatWeek(plan.value.weekStart,props.locale))
watch(plan,()=>{
 saved.value=false
 try { savePlan(storage,plan.value);draftSaved.value=true;error.value=false }
 catch { error.value=true;draftSaved.value=false }
},{deep:true,flush:'sync'})
function refreshWeek() {
 if(plan.value.weekStart===localWeekStart()) return false
 plan.value=loadPlan(storage)
 saved.value=false;weekChanged.value=true
 return true
}
function adjust(key,delta) {
 if(refreshWeek()) return
 const next=adjustPoints(plan.value.points,key,delta)
 if(next!==plan.value.points){plan.value={...plan.value,points:next};if(delta>0){pulse.value++;clearTimeout(pulseTimer);pulseTimer=setTimeout(()=>pulse.value=0,650)}}
}
function save() {
 if(refreshWeek() || remaining.value!==0) return
 try { savePlan(storage,plan.value,true);saved.value=true;error.value=false;emit('continue') }
 catch { error.value=true }
}
let timer
onMounted(()=>{timer=window.setInterval(refreshWeek,60000);window.addEventListener('focus',refreshWeek)})
onUnmounted(()=>{clearTimeout(pulseTimer);window.clearInterval(timer);window.removeEventListener('focus',refreshWeek)})
</script>
<template>
 <main class="weekly-goals" :lang="locale">
  <header class="goals-topbar">
   <button type="button" class="nes-btn goals-back" @click="emit('back')">← {{ copy.back }}</button>
   <nav class="goals-language" :aria-label="copy.language"><button v-for="language in ['en','de']" :key="language" type="button" class="nes-btn" :class="{'is-primary':locale===language}" :aria-pressed="locale===language" @click="emit('locale-change',language)">{{ language.toUpperCase() }}</button></nav>
  </header>
  <header class="goals-heading"><p class="goals-kicker">{{ copy.step }}</p><h1 tabindex="-1">{{ copy.title }}</h1><p class="goals-copy">{{ copy.intro }}</p></header>
  <section class="nes-container budget-panel" :aria-label="copy.points">
   <div class="budget-details"><div><p class="goals-kicker">{{ copy.week }}</p><p class="week-dates">{{ weekLabel }}</p></div><div class="point-wallet"><strong>{{ remaining }}</strong><span>{{ copy.points }}</span></div></div>
   <progress class="nes-progress is-primary" :value="total" :max="POINT_BUDGET" :aria-label="copy.allocated"></progress>
   <p class="goals-copy budget-caption" role="status" aria-atomic="true">{{ total }}/{{ POINT_BUDGET }} {{ copy.allocated }}</p>
  </section>
  <p class="goals-copy goals-instruction">{{ copy.instruction }}</p>
  <p v-if="weekChanged" class="goals-copy week-notice" role="status">{{ copy.newWeek }}</p>
  <div class="mission-layout">
   <section class="nes-container mission-detail" :aria-label="copy.goals[selected].title">
    <p class="goals-kicker">{{ companion.name || (locale==='de'?'Dein Begleiter':'Your companion') }}</p>
    <GoalScene :goal="selected" :appearance="companion.appearance" :palette="companion.palette" :label="copy.goals[selected].title" :pulse="pulse" />
    <div class="mission-heading"><span class="goal-rank">{{ rankMap[selected] ? `${copy.priority} ${rankMap[selected]}` : copy.zero }}</span><span class="mission-index">{{ GOAL_KEYS.indexOf(selected)+1 }}/11</span></div>
    <h2 id="selected-goal">{{ copy.goals[selected].title }}</h2>
    <p class="goals-copy goal-hint">{{ copy.goals[selected].hint }}</p>
    <div class="goal-counter mission-counter" role="group" aria-labelledby="selected-goal">
     <button type="button" class="nes-btn" :disabled="plan.points[selected]===0" :aria-label="copy.decrease(copy.goals[selected].title)" @click="adjust(selected,-1)">−</button>
     <span class="goal-value" aria-live="polite">{{ plan.points[selected] }}<small>/10</small></span>
     <button type="button" class="nes-btn is-primary" :disabled="remaining===0" :aria-label="copy.increase(copy.goals[selected].title)" @click="adjust(selected,1)">+</button>
    </div>
   </section>
   <section class="nes-container mission-menu" :aria-label="copy.title">
    <div class="mission-menu-heading"><h2>{{ locale==='de'?'Ziele auswählen':'Choose goals' }}</h2><button class="nes-btn" type="button" :aria-expanded="showAll" @click="showAll=!showAll">{{ showAll ? (locale==='de'?'Seiten':'Pages') : (locale==='de'?'Alle':'All') }}</button></div>
    <div class="mission-goal-list">
     <button v-for="key in visibleGoals" :key="key" type="button" class="mission-goal-row" :class="{'is-selected':selected===key,'has-points':plan.points[key]>0}" :aria-pressed="selected===key" @click="selectGoal(key)">
      <GoalScene :goal="key" :label="copy.goals[key].title" icon />
      <span>{{ copy.goals[key].title }}</span><strong>{{ plan.points[key] }}</strong>
     </button>
    </div>
    <nav v-if="!showAll" class="mission-pagination" :aria-label="locale==='de'?'Zielseiten':'Goal pages'">
     <button class="nes-btn" type="button" :disabled="page===0" :aria-label="locale==='de'?'Vorherige Ziele':'Previous goals'" @click="changePage(-1)">←</button>
     <span>{{ page+1 }}/{{ pageCount }}</span>
     <button class="nes-btn" type="button" :disabled="page===pageCount-1" :aria-label="locale==='de'?'Weitere Ziele':'More goals'" @click="changePage(1)">→</button>
    </nav>
   </section>
   <aside class="nes-container mission-summary">
    <h2>{{ copy.ranking }}</h2>
    <p v-if="!rankings.length" class="goals-copy">{{ copy.empty }}</p>
    <div v-else class="mission-priorities"><button v-for="item in rankings" :key="item.key" type="button" @click="selectGoal(item.key)"><span>{{ item.rank }}. {{ copy.goals[item.key].title }}</span><strong>{{ item.points }}</strong></button></div>
    <p class="goals-copy summary-note">{{ copy.tie }}</p>
    <p class="goals-copy summary-note">{{ copy.noEffect }}</p>
   </aside>
  </div>
  <footer class="goals-actions">
   <p class="goals-copy allocation-status">{{ remaining ? copy.left(remaining) : copy.ready }}</p>
   <p v-if="error" class="goals-copy goals-error" role="alert">{{ copy.storage }}</p>
   <button type="button" class="nes-btn is-primary goals-save" :disabled="remaining!==0" @click="save">{{ locale === 'de' ? 'Weiter: Gefühle zur Mobilität' : 'Next: transport feelings' }}</button>
   <p class="goals-copy goals-draft">{{ draftSaved && !error ? copy.draft : '' }}</p>
  </footer>
  <section v-if="saved" class="nes-container goals-confirmation" role="status"><h2>{{ copy.saved }}</h2><p class="goals-copy">{{ copy.savedBody }}</p></section>
 </main>
</template>
