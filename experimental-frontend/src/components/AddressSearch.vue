<script setup>
import {computed,nextTick,onUnmounted,ref,watch} from 'vue'
import JourneyControlIcon from './JourneyControlIcon.vue'
import {addressTitle,addressPrecision} from '../services/addressPresentation.js'
import {cachedPlaces,findPlaces} from '../services/geocoding.js'
const props=defineProps({modelValue:{default:null},locale:{default:'en'},id:{type:String,required:true},label:{type:String,required:true},disabled:Boolean,compact:Boolean,bias:{default:null}})
const emit=defineEmits(['update:modelValue'])
const query=ref(props.modelValue?.label||''),results=ref([]),busy=ref(false),error=ref(''),searched=ref(false),opened=ref(false),active=ref(-1),input=ref(null),root=ref(null),stale=ref(false),slow=ref(false)
const de=computed(()=>props.locale==='de'),expanded=computed(()=>opened.value&&results.value.length>0)
let controller,timer,slowTimer,version=0,alive=true,composing=false
function invalidate(keep=false){clearTimeout(timer);clearTimeout(slowTimer);version++;controller?.abort();busy.value=false;slow.value=false;searched.value=false;error.value='';active.value=-1;stale.value=keep&&results.value.length>0;if(!keep)results.value=[];opened.value=keep&&results.value.length>0}
function schedule(){if(!props.disabled&&!composing&&query.value.trim().length>=3){if(cachedPlaces(query.value,props.locale,true,props.bias)!==null)search(true);else timer=setTimeout(()=>search(true),400)}}
function edit(value){query.value=value;invalidate(value.trim().length>=3);emit('update:modelValue',null);schedule()}
watch(()=>props.modelValue,(value,previous)=>{if(value?.label&&value.label!==query.value){invalidate();query.value=value.label}else if(!value&&previous?.label===query.value){invalidate();query.value=''}})
watch(()=>props.disabled,value=>{if(value)invalidate()})
watch([()=>props.locale,()=>props.bias?.lat,()=>props.bias?.lon],()=>{invalidate();if(!props.modelValue)schedule()})
async function search(automatic=false){
 clearTimeout(timer)
 if(props.disabled||busy.value)return
 controller?.abort();controller=new AbortController();const current=++version
 busy.value=true;slow.value=false;error.value='';stale.value=results.value.length>0;searched.value=false;active.value=-1;opened.value=true
 clearTimeout(slowTimer);slowTimer=setTimeout(()=>{if(alive&&current===version&&busy.value)slow.value=true},4000)
 try{
  const found=await findPlaces(query.value,props.locale,controller.signal,{autocomplete:automatic===true,bias:props.bias})
  if(alive&&current===version){results.value=found;searched.value=true;stale.value=false}
 }catch(e){if(alive&&current===version&&e.name!=='AbortError')error.value=e.message==='query'?'query':e.message==='timeout'?'timeout':'network'}
 finally{if(alive&&current===version){clearTimeout(slowTimer);busy.value=false;slow.value=false}}
}
function choose(place){if(stale.value||busy.value||props.disabled)return;invalidate();query.value=place.label;emit('update:modelValue',place);input.value?.focus({preventScroll:true})}
async function keydown(event){
 if(event.isComposing||composing)return
 if(event.key==='Escape'){event.preventDefault();invalidate();return}
 if(['ArrowDown','ArrowUp'].includes(event.key)&&results.value.length&&!stale.value&&!busy.value){event.preventDefault();opened.value=true;active.value=event.key==='ArrowDown'?(active.value+1)%results.value.length:(active.value<0?results.value.length-1:(active.value-1+results.value.length)%results.value.length);await nextTick();document.getElementById(`${props.id}-option-${active.value}`)?.scrollIntoView({block:'nearest',behavior:'instant'});return}
 if(event.key==='Enter'){event.preventDefault();if(busy.value)return;if(expanded.value&&active.value>=0&&!stale.value)choose(results.value[active.value]);else search()}
 if(['ArrowLeft','ArrowRight','Home','End'].includes(event.key))active.value=-1
}
function focusout(event){if(!root.value?.contains(event.relatedTarget)){clearTimeout(timer);clearTimeout(slowTimer);controller?.abort();version++;if(busy.value)stale.value=results.value.length>0;busy.value=false;slow.value=false;opened.value=false;active.value=-1}}
function focus(){if(results.value.length)opened.value=true;if(stale.value&&!busy.value)schedule()}
onUnmounted(()=>{alive=false;invalidate()})
</script>
<template>
 <div ref="root" class="address-search" :class="{'address-search--compact':compact}" @focusout="focusout">
  <label :for="id">{{label}}</label>
  <div class="address-search-row"><input ref="input" :id="id" :value="query" class="nes-input" type="text" role="combobox" aria-autocomplete="list" aria-haspopup="listbox" :aria-expanded="!!expanded" :aria-controls="`${id}-options`" :aria-activedescendant="expanded&&active>=0?`${id}-option-${active}`:undefined" :disabled="disabled" :placeholder="compact?label:de?'Straße, Hausnummer, Stadt':'Street, number, city'" maxlength="240" autocomplete="off" :aria-describedby="`${id}-status`" @input="edit($event.target.value)" @focus="focus" @keydown="keydown" @compositionstart="composing=true" @compositionend="composing=false;edit($event.target.value)"/><button class="nes-btn" type="button" :disabled="disabled||busy||query.trim().length<3" :aria-label="de?'Adressen suchen':'Search addresses'" :title="de?'Adressen suchen':'Search addresses'" @click="search"><JourneyControlIcon v-if="compact" kind="search"/><template v-else>{{busy?'…':de?'Suchen':'Search'}}</template></button></div>
  <p v-show="modelValue||busy||searched||expanded" :id="`${id}-status`" class="route-note" role="status">{{modelValue?.source==='geolocation'?(de?'Aktueller Standort ausgewählt.':'Current location selected.'):modelValue?(modelValue.houseNumber?(de?'Adresse mit Hausnummer ausgewählt.':'Address with house number selected.'):(de?'Ort ausgewählt. Eine genaue Hausnummer wurde nicht bestätigt.':'Place selected. An exact house number has not been confirmed.')):busy?(slow?(de?'Der Suchdienst braucht länger. Bitte warten oder abbrechen.':'The search service is taking longer. Please wait or cancel.'):(de?'Vorschläge werden geladen…':'Loading suggestions…')):searched&&!results.length?(de?'Keine passende Adresse. Hausnummer prüfen und Stadt ergänzen.':'No matching address. Check the number and add the city.'):expanded?(de?'Mit ↑ ↓ und Enter oder per Klick auswählen.':'Choose with ↑ ↓ and Enter, or click a suggestion.'):(de?'Tippe mindestens 3 Zeichen. Vorschläge erscheinen automatisch.':'Type at least 3 characters. Suggestions appear automatically.')}}</p>
  <p v-if="error" class="address-error" role="alert">{{error==='query'?(de?'Bitte 3–240 Zeichen eingeben.':'Enter 3–240 characters.'):error==='timeout'?(de?'Die Ortssuche dauert zu lange. Bitte erneut versuchen.':'Place search took too long. Please try again.'):(de?'Die Ortssuche ist nicht erreichbar. Bitte erneut versuchen.':'Place search is unavailable. Please try again.')}}</p>
  <div v-if="busy||stale" class="address-update-status"><p v-if="stale&&results.length" class="route-note">{{de?'Vorherige Vorschläge — werden aktualisiert.':'Previous suggestions — updating.'}}</p><button type="button" class="nes-btn" @click="invalidate()">{{de?'Abbrechen':'Cancel'}}</button></div>
  <ul v-show="expanded" :id="`${id}-options`" class="address-results address-autocomplete" role="listbox" :aria-label="label"><li v-for="(place,i) in results" :key="`${place.id}-${i}`" role="presentation"><button :id="`${id}-option-${i}`" type="button" role="option" tabindex="-1" class="nes-btn" :class="{'address-option--active':active===i}" :aria-selected="active===i" :disabled="disabled||busy||stale" @pointerdown.prevent @click="choose(place)"><span class="address-suggestion-title">{{addressTitle(place)}}</span><small v-if="place.detail" class="address-suggestion-locality">{{place.detail}}</small><small class="address-precision" :class="{'address-precision--exact':!!place.houseNumber}">{{addressPrecision(place,locale)}}</small></button></li></ul>
 </div>
</template>

<style scoped>
html body #app .address-search--compact{margin:0;min-width:0}html body #app .address-search--compact>label{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%);white-space:nowrap}
html body #app .address-search--compact .address-search-row{display:grid;grid-template-columns:minmax(0,1fr) 44px;gap:8px;align-items:center}
html body #app .address-search--compact .address-search-row .nes-input{margin:0;min-width:0;min-height:44px;padding:10px;font-size:11px;line-height:1.6}
html body #app .address-search--compact .address-search-row button{width:44px;height:44px;min-height:44px;padding:8px;display:grid;place-items:center;margin:0}
html body #app .address-search--compact .route-note{margin:6px 0;font-size:9px;line-height:1.8}
html body #app .address-suggestion-title{display:block;color:#edf4ff;overflow-wrap:anywhere}
html body #app .address-suggestion-locality{color:#b9ccec;margin-top:6px}
html body #app .address-precision{color:#d5ddec;border-left:3px dashed #94a6bf;padding-left:8px;margin-top:8px}
html body #app .address-precision--exact{border-left-style:solid;border-left-color:#a6e6c5;color:#c1edda}
html body #app .address-results button{background:#1b2c48;color:#edf4ff}
html body #app .address-results button.address-option--active{background:#30486a;outline:3px solid #ffda83;outline-offset:-3px}
</style>
