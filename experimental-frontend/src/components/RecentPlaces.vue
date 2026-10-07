<script setup>
import {computed} from 'vue'
const props=defineProps({places:{type:Array,default:()=>[]},locale:{default:'en'},target:{default:'origin'},disabled:Boolean,embedded:Boolean})
const emit=defineEmits(['select','target-change','clear'])
const de=computed(()=>props.locale==='de')
function kind(place){if(place.source==='geolocation'||String(place.id||'').startsWith('geo-')||/^(my current location|mein aktueller standort)$/i.test(place.label))return 'location';return /hauptbahnhof|bahnhof|central station|train station/i.test(place.label)?'station':'address'}
function title(place){return kind(place)==='location'?(de.value?'Gespeicherter Standort':'Saved location'):place.label.split(',')[0].trim()}
function detail(place){if(kind(place)==='location')return de.value?'Position einer früheren Suche':'Position from an earlier search';const rest=place.label.split(',').slice(1).join(',').trim(),extra=String(place.detail||'').trim();return [rest,extra&&!rest.includes(extra)?extra:''].filter(Boolean).join(' · ')}
</script>
<template>
 <details v-if="places.length" class="journey-recent recent-panel" :open="embedded||undefined">
  <summary v-if="!embedded">{{de?'Zuletzt verwendete Orte':'Recent places'}} <span class="recent-count">{{places.length}}</span></summary>
  <div class="recent-targets" role="group" :aria-label="de?'Ort verwenden als':'Use place as'">
   <button type="button" class="nes-btn recent-target recent-target--origin" :aria-pressed="target==='origin'" :disabled="disabled" @click="emit('target-change','origin')"><span aria-hidden="true">A</span> {{de?'Als Start':'Use as origin'}}</button>
   <button type="button" class="nes-btn recent-target recent-target--destination" :aria-pressed="target==='destination'" :disabled="disabled" @click="emit('target-change','destination')"><span aria-hidden="true">B</span> {{de?'Als Ziel':'Use as destination'}}</button>
  </div>
  <p class="recent-hint">{{target==='origin'?(de?'Ort wählen → Start setzen':'Choose a place → set origin'):(de?'Ort wählen → Ziel setzen':'Choose a place → set destination')}}</p>
  <ul class="recent-cards">
   <li v-for="place in places" :key="`${place.lat},${place.lon}`">
    <button type="button" class="nes-btn recent-place-card" :class="`recent-place-card--${kind(place)}`" :disabled="disabled" @click="emit('select',place)">
     <span class="recent-place-icon" aria-hidden="true"><svg viewBox="0 0 16 16" shape-rendering="crispEdges" focusable="false">
      <path v-if="kind(place)==='station'" d="M4 1h8v1h1v10h-1v1h-1l2 2h-2l-2-2H7l-2 2H3l2-2H4v-1H3V2h1zM5 3v4h6V3zM5 9v2h2V9zM9 9v2h2V9z" fill="currentColor" fill-rule="evenodd"/>
      <path v-else-if="kind(place)==='location'" d="M7 0h2v3h3v1h1v3h3v2h-3v3h-1v1H9v3H7v-3H4v-1H3V9H0V7h3V4h1V3h3zM5 5v6h6V5zM7 7h2v2H7z" fill="currentColor" fill-rule="evenodd"/>
      <path v-else d="M5 1h6v1h2v2h1v5h-1v2h-2v2H9v2H7v-2H5v-2H3V9H2V4h1V2h2zM6 4v4h4V4z" fill="currentColor" fill-rule="evenodd"/>
     </svg></span>
     <span class="recent-place-copy"><strong>{{title(place)}}</strong><small v-if="detail(place)">{{detail(place)}}</small></span>
     <span class="recent-place-arrow" aria-hidden="true">→</span>
    </button>
   </li>
  </ul>
  <button type="button" class="nes-btn recent-clear" :disabled="disabled" @click="emit('clear')">{{de?'Liste löschen':'Clear recent places'}}</button>
 </details>
</template>
<style scoped>
.recent-panel{padding:16px 0;border-top:2px solid #647da9;border-bottom:2px solid #647da9}
.recent-panel summary{cursor:pointer;color:#e1eaff;min-height:44px;list-style:disclosure-closed}
.recent-count{padding:4px 8px;background:#111c35;border:2px solid #647da9;color:#ffda83;font-size:10px;margin-left:12px}
.recent-targets{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin:16px 0}
.recent-targets .recent-target{display:flex;align-items:center;justify-content:center;gap:12px;background:#17233e;color:#dce6ff;border:3px solid #647da9;min-height:52px;font-size:10px}
.recent-target span{display:grid;place-items:center;width:24px;height:24px;border:2px solid currentColor;flex-shrink:0}
.recent-target--origin[aria-pressed=true]{background:#15382e;color:#b6f5cb;border-color:#80dca9}
.recent-target--destination[aria-pressed=true]{background:#46341d;color:#ffe1a0;border-color:#ffcf70}
.recent-hint{color:#cbdaf5;margin:12px 0 20px}
.recent-panel .recent-cards{display:grid;gap:14px;padding:0;margin:0 0 20px;list-style:none}
.recent-panel .recent-cards li{margin:0;min-width:0}
.recent-panel .recent-cards .recent-place-card{--place-color:#c5aeff;display:grid;grid-template-columns:44px minmax(0,1fr) 18px;align-items:center;gap:14px;width:100%;min-height:80px;padding:14px;text-align:left;background:#17223c;color:#f1f4ff;border:3px solid #6e82a8;border-left:6px solid var(--place-color);box-shadow:3px 3px #080f21;line-height:1.9}
.recent-panel .recent-cards .recent-place-card--station{--place-color:#8dcfff}
.recent-panel .recent-cards .recent-place-card--location{--place-color:#99e5b5}
.recent-place-icon{display:grid;place-items:center;width:44px;height:44px;background:#0d172d;border:2px solid #425675;color:var(--place-color)}
.recent-place-icon svg{width:28px;height:28px;display:block}
.recent-place-copy{min-width:0;overflow-wrap:anywhere}
.recent-place-copy strong{display:block;font-size:11px;line-height:1.9;color:#f1f4ff}
.recent-place-copy small{display:block;font-size:9px;line-height:2;color:#bfcfe9;margin-top:8px}
.recent-place-arrow{color:var(--place-color);font-size:12px}
.recent-panel .recent-clear{background:#2f2031;color:#ffc4c4;border:2px solid #af7b91;font-size:9px;min-height:44px}
.recent-place-card:not(:disabled):hover{background:#243453!important;border-color:var(--place-color)!important}
button:focus-visible,summary:focus-visible{outline:3px solid #ffda83;outline-offset:5px}
button:disabled{opacity:.55;cursor:default}
@media(max-width:480px){.recent-targets{gap:12px}.recent-targets .recent-target{font-size:9px;padding:8px;gap:8px}.recent-panel .recent-cards .recent-place-card{grid-template-columns:32px minmax(0,1fr) 14px;gap:10px;padding:12px}.recent-place-icon{width:32px;height:36px}.recent-place-icon svg{width:24px;height:24px}.recent-place-copy strong{font-size:10px}}
html[data-cricket-text=large] .recent-place-copy strong{font-size:13px}html[data-cricket-text=large] .recent-place-copy small{font-size:11px}
</style>
