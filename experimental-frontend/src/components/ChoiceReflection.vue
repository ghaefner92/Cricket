<script setup>
import {computed,ref,watch,onMounted,onUnmounted,useId} from 'vue'
import AvatarReaction from './AvatarReaction.vue'
import WeatherInventory from './WeatherInventory.vue'
import {dialogueStatements} from '../services/dialogueWeather.js'
import {beliefMessages} from '../i18n/beliefs.js'
import {modelMode} from '../services/routeExplanation.js'
import PixelJourneyIcon from './PixelJourneyIcon.vue'
import DialogueMoodIcon from './DialogueMoodIcon.vue'
import {choiceReaction} from '../services/choiceReaction.js'
import {reactionMeaning} from '../services/reactionMeaning.js'
import {voicedStatements,voiceOpening} from '../services/choiceVoice.js'
import {narrativeRows,dialoguePages,dialogueTokens,paragraphIcon} from '../services/dialoguePresentation.js'
import {createDialogueReveal} from '../services/dialogueReveal.js'
const props=defineProps({companion:{type:Object,required:true},locale:{default:'en'},reflection:Object,choiceCard:Object,busy:Boolean,error:Boolean,paused:Boolean,embedded:Boolean})
const emit=defineEmits(['retry']),headingId=useId()
const de=computed(()=>props.locale==='de'),reducedMotion=ref(false),revealed=ref(0),page=ref(0),all=ref(false)
const reaction=computed(()=>choiceReaction(props.reflection))
const statements=computed(()=>voicedStatements(props.reflection))
const narrative=computed(()=>narrativeRows(props.reflection,dialogueStatements(statements.value,props.choiceCard),voiceOpening(props.reflection)))
const choiceMode=computed(()=>{const mode=modelMode(props.choiceCard?.route?.mode_key);return (beliefMessages[props.locale]||beliefMessages.en).modes[mode]||mode})
const pages=computed(()=>dialoguePages(narrative.value))
const current=computed(()=>all.value?narrative.value:pages.value[Math.min(page.value,pages.value.length-1)])
const rows=computed(()=>{let offset=0;return current.value.map(row=>{const value={...row,offset};offset+=Array.from(row.text).length;return value})})
const fullText=computed(()=>rows.value.map(row=>row.text).join(''))
const typing=computed(()=>revealed.value<Array.from(fullText.value).length)
const fragment=row=>Array.from(row.text).slice(0,Math.max(0,revealed.value-row.offset)).join('')
const reveal=createDialogueReveal(n=>{revealed.value=n})
watch(()=>props.reflection,()=>{page.value=0;all.value=false})
watch([fullText,()=>props.paused,reducedMotion],()=>reveal.start(fullText.value,!all.value&&!props.paused&&!reducedMotion.value),{immediate:true})
function readAll(){all.value=true;reveal.showAll()}
function next(){if(typing.value){reveal.showAll();return}if(page.value<pages.value.length-1)page.value++}
let media
function motionChanged(){reducedMotion.value=media.matches}
onMounted(()=>{media=window.matchMedia?.('(prefers-reduced-motion: reduce)');if(media){motionChanged();media.addEventListener?.('change',motionChanged)}})
onUnmounted(()=>{reveal.dispose();media?.removeEventListener?.('change',motionChanged)})
const weatherKinds=['temperature','wind','rain']
const modes=['car','bike','walk','pt']
</script>
<template>
 <section class="choice-reflection" :class="{'choice-reflection--embedded':embedded}" :aria-labelledby="headingId" :aria-busy="busy">
  <header class="choice-reflection-header">
   <AvatarReaction dialogue :companion="companion" :kind="busy||error?'unknown':reaction.kind" :expression="busy||error?'neutral':reaction.expression" :paused="paused||reducedMotion" :talking="typing&&!busy&&!error" :locale="locale"/>
   <div class="choice-speaker"><p class="choice-reflection-name">{{companion.name}}</p><p v-if="!busy&&!error&&reaction.kind!=='unknown'" class="route-note choice-comparison-meaning">{{reactionMeaning(reaction.kind,locale)}}</p><h3 :id="headingId" class="choice-accessible-text">{{de?'Deine Wahl gemeinsam betrachtet':'Looking at your choice together'}}</h3></div>
  </header>
  <div class="choice-dialogue nes-container">
   <div v-if="choiceCard" class="choice-saved-summary"><PixelJourneyIcon :kind="modelMode(choiceCard.route.mode_key)" :animated="false" class="dialogue-inline-icon"/><strong>{{choiceMode}}</strong><span class="choice-saved-label">✓ {{de?'Gewählt und gespeichert':'Chosen and saved'}}</span></div>
   <WeatherInventory v-if="choiceCard?.candidate" :candidate="choiceCard.candidate" :compact="true" dialogue :locale="locale" :animated="false"/>
   <p v-if="busy" role="status">{{de?'Ich schaue mir deine Wahl an…':'Let me look at your choice…'}}<span class="dialogue-cursor" aria-hidden="true">▮</span></p>
   <div v-else-if="error" role="alert"><p>{{de?'Deine Wahl ist gespeichert. Ich konnte die Erklärung gerade nicht laden.':'Your choice is saved. I couldn’t load the explanation just now.'}}</p><button type="button" class="nes-btn" @click="emit('retry')">{{de?'Noch einmal versuchen':'Try again'}}</button></div>
   <template v-else-if="reflection">
    <div class="choice-accessible-text">{{rows.map(row=>row.text).join(' ')}}</div>
    <div aria-hidden="true" class="choice-narrative">
     <p v-for="row in rows" v-show="revealed>row.offset||!typing" :key="row.id" class="dialogue-paragraph">
      <template v-if="paragraphIcon(row)"><PixelJourneyIcon v-if="weatherKinds.includes(paragraphIcon(row))" :kind="paragraphIcon(row)" :animated="false" class="dialogue-inline-icon"/><DialogueMoodIcon v-else :kind="paragraphIcon(row)" :animated="false"/></template>
      <template v-for="(token,i) in dialogueTokens(fragment(row),row)" :key="i"><span v-if="token.kind==='text'">{{token.text}}</span><span v-else class="dialogue-highlight" :class="'highlight-'+token.kind"><PixelJourneyIcon v-if="modes.includes(token.kind)" :kind="token.kind" :animated="false" class="dialogue-inline-icon"/>{{token.text}}</span></template><span v-if="typing&&revealed>row.offset&&revealed<row.offset+Array.from(row.text).length" class="dialogue-cursor">▮</span>
     </p>
    </div>
    <nav class="dialogue-controls" :aria-label="de?'Dialog lesen':'Read dialogue'">
     <button v-if="typing" type="button" class="nes-btn" @click="reveal.showAll()">{{de?'Text anzeigen':'Show text'}}</button>
     <button v-if="!all&&page>0" type="button" class="nes-btn" @click="page--">← {{de?'Zurück':'Back'}}</button>
     <button v-if="!all&&page<pages.length-1" type="button" class="nes-btn is-primary" @click="next">{{de?'Weiter':'Continue'}} →</button>
     <button v-if="!all&&pages.length>1" type="button" class="nes-btn" @click="readAll">{{de?'Alles lesen':'Read all'}}</button>
     <span v-if="!all&&pages.length>1" class="dialogue-position" role="status">{{page+1}} / {{pages.length}}</span>
    </nav>
    <footer class="dialogue-footnote"><span :title="de?'Alle Formulierungen beruhen auf verifizierten gespeicherten Ergebnissen.':'All wording is based on verified saved results.'">{{reflection.narration?.generated_by_llm?(de?'KI wählt verifizierte Formulierungen':'AI selects verified wording'):(de?'Verifizierte Formulierungen':'Verified wording')}}</span><button v-if="!paused" type="button" class="nes-btn" @click="emit('retry')">{{de?'Neu erzählen':'Refresh story'}}</button></footer>
   </template>
  </div>
 </section>
</template>
<style scoped>
.choice-reflection{margin-top:24px;color:#edf4ff;font-family:inherit}.choice-reflection-header{display:flex;align-items:center;gap:16px;margin:0 0 18px}.choice-reflection-name{color:#ffdb79;font-size:11px;line-height:2;margin:0 0 10px}.choice-dialogue{background:#101d35;color:#edf4ff;border:4px solid #a9bbdf;box-shadow:inset 0 0 0 3px #26385c,6px 6px 0 #070d1b;padding:22px;margin:0}.choice-reflection .dialogue-paragraph{line-height:2.15;margin:0 0 16px;overflow-wrap:anywhere}.dialogue-paragraph>:first-child:is(svg){margin-right:8px}.dialogue-highlight{display:inline;color:#edf4ff}.highlight-car{color:#a8d4ff}.highlight-bike{color:#a6ecc9}.highlight-walk{color:#95e9df}.highlight-pt{color:#d4c7ff}.highlight-positive{color:#a6ecc9}.highlight-tension{color:#ffdfa0}.dialogue-inline-icon{display:inline-block;width:18px;height:18px;vertical-align:middle;margin-right:5px;image-rendering:pixelated}.dialogue-controls{display:flex;align-items:center;flex-wrap:wrap;gap:12px;margin-top:16px}.choice-reflection button{font-family:inherit;min-height:44px}.dialogue-position{font-size:9px;color:#b9ceff}.dialogue-footnote{display:flex;align-items:center;flex-wrap:wrap;gap:12px;justify-content:space-between;margin-top:20px;color:#b9ceff;font-size:8px;line-height:2}.dialogue-footnote button{font-size:9px}.dialogue-cursor{display:inline-block;color:#ffdb79;animation:none}.choice-reflection button:focus-visible{outline:3px solid #ffdb79;outline-offset:4px}.choice-accessible-text{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip-path:inset(50%);white-space:nowrap;border:0}.choice-reflection--embedded{margin-top:0;display:grid;--dialogue-portrait-size:144px;grid-template-columns:var(--dialogue-portrait-size) minmax(0,1fr);gap:16px}.choice-reflection--embedded .choice-reflection-header{display:contents}.choice-reflection--embedded .choice-speaker{min-width:0;overflow-wrap:anywhere;grid-column:2;grid-row:1}.choice-reflection--embedded .avatar-reaction{grid-column:1;grid-row:1/3}.choice-reflection--embedded .choice-dialogue{grid-column:2;min-width:0;background:transparent;border:0!important;box-shadow:none;padding:0!important;margin:0!important}@keyframes dialogue-blink{50%{opacity:0}}@media(prefers-reduced-motion:reduce){.dialogue-cursor{animation:none}}@media(max-width:600px){.choice-reflection--embedded{--dialogue-portrait-size:104px;grid-template-columns:var(--dialogue-portrait-size) minmax(0,1fr);gap:12px}.choice-reflection--embedded .choice-dialogue{grid-column:1/-1}.choice-reflection--embedded .avatar-reaction{grid-row:1}.choice-dialogue{padding:16px}}
</style>
