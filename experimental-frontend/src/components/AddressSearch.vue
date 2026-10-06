<script setup>
import {computed,nextTick,onUnmounted,ref,watch} from 'vue'
import {cachedPlaces,findPlaces} from '../services/geocoding.js'
const props=defineProps({modelValue:{default:null},locale:{default:'en'},id:{type:String,required:true},label:{type:String,required:true},disabled:Boolean})
const emit=defineEmits(['update:modelValue'])
const query=ref(props.modelValue?.label||''),results=ref([]),busy=ref(false),error=ref(''),searched=ref(false),opened=ref(false),active=ref(-1),input=ref(null),root=ref(null)
const de=computed(()=>props.locale==='de'),expanded=computed(()=>opened.value&&results.value.length>0)
let controller,timer,version=0,alive=true,composing=false
function invalidate(){clearTimeout(timer);version++;controller?.abort();busy.value=false;results.value=[];searched.value=false;error.value='';active.value=-1;opened.value=false}
function edit(value){query.value=value;invalidate();emit('update:modelValue',null);if(!props.disabled&&!composing&&value.trim().length>=3){if(cachedPlaces(value,props.locale,true)!==null)search(true);else timer=setTimeout(()=>search(true),650)}}
watch(()=>props.modelValue,value=>{if(value?.label&&value.label!==query.value){invalidate();query.value=value.label}})
watch(()=>props.disabled,value=>{if(value)invalidate()})
watch(()=>props.locale,()=>{invalidate();if(!props.modelValue&&query.value.trim().length>=3&&!props.disabled)timer=setTimeout(()=>search(true),650)})
async function search(automatic=false){
 clearTimeout(timer)
 if(props.disabled)return
 controller?.abort();controller=new AbortController();const current=++version
 busy.value=true;error.value='';results.value=[];searched.value=false;active.value=-1;opened.value=true
 try{
  const found=await findPlaces(query.value,props.locale,controller.signal,{autocomplete:automatic===true})
  if(alive&&current===version){results.value=found;searched.value=true}
 }catch(e){if(alive&&current===version&&e.name!=='AbortError')error.value=e.message==='query'?'query':e.message==='timeout'?'timeout':'network'}
 finally{if(alive&&current===version)busy.value=false}
}
function choose(place){invalidate();query.value=place.label;emit('update:modelValue',place);input.value?.focus({preventScroll:true})}
async function keydown(event){
 if(event.isComposing||composing)return
 if(event.key==='Escape'){event.preventDefault();invalidate();return}
 if(['ArrowDown','ArrowUp'].includes(event.key)&&results.value.length){event.preventDefault();opened.value=true;active.value=event.key==='ArrowDown'?(active.value+1)%results.value.length:(active.value<0?results.value.length-1:(active.value-1+results.value.length)%results.value.length);await nextTick();document.getElementById(`${props.id}-option-${active.value}`)?.scrollIntoView({block:'nearest',behavior:'instant'});return}
 if(event.key==='Enter'){event.preventDefault();if(expanded.value&&active.value>=0)choose(results.value[active.value]);else search()}
 if(['ArrowLeft','ArrowRight','Home','End'].includes(event.key))active.value=-1
}
function focusout(event){if(!root.value?.contains(event.relatedTarget)){clearTimeout(timer);controller?.abort();version++;busy.value=false;opened.value=false;active.value=-1}}
function focus(){if(results.value.length)opened.value=true}
onUnmounted(()=>{alive=false;invalidate()})
</script>
<template>
 <div ref="root" class="address-search" @focusout="focusout">
  <label :for="id">{{label}}</label>
  <div class="address-search-row"><input ref="input" :id="id" :value="query" class="nes-input" type="text" role="combobox" aria-autocomplete="list" aria-haspopup="listbox" :aria-expanded="!!expanded" :aria-controls="`${id}-options`" :aria-activedescendant="expanded&&active>=0?`${id}-option-${active}`:undefined" :disabled="disabled" :placeholder="de?'Straße, Hausnummer, Stadt':'Street, number, city'" maxlength="240" autocomplete="off" :aria-describedby="`${id}-status`" @input="edit($event.target.value)" @focus="focus" @keydown="keydown" @compositionstart="composing=true" @compositionend="composing=false;edit($event.target.value)"/><button class="nes-btn" type="button" :disabled="disabled||busy||query.trim().length<3" @click="search">{{busy?'…':de?'Suchen':'Search'}}</button></div>
  <p :id="`${id}-status`" class="route-note" role="status">{{modelValue?(modelValue.houseNumber?(de?'Adresse mit Hausnummer ausgewählt.':'Address with house number selected.'):(de?'Ort ausgewählt. Eine genaue Hausnummer wurde nicht bestätigt.':'Place selected. An exact house number has not been confirmed.')):busy?(de?'Vorschläge werden geladen…':'Loading suggestions…'):searched&&!results.length?(de?'Keine passende Adresse. Hausnummer prüfen und Stadt ergänzen.':'No matching address. Check the number and add the city.'):expanded?(de?'Mit ↑ ↓ und Enter oder per Klick auswählen.':'Choose with ↑ ↓ and Enter, or click a suggestion.'):(de?'Tippe mindestens 3 Zeichen. Vorschläge erscheinen automatisch.':'Type at least 3 characters. Suggestions appear automatically.')}}</p>
  <p class="route-note">{{de?'Adresssuche:':'Address search:'}} <a href="https://github.com/komoot/photon" target="_blank" rel="noopener noreferrer">Photon</a> · © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a></p>
  <p v-if="error" class="address-error" role="alert">{{error==='query'?(de?'Bitte 3–240 Zeichen eingeben.':'Enter 3–240 characters.'):error==='timeout'?(de?'Die Ortssuche dauert zu lange. Bitte erneut versuchen.':'Place search took too long. Please try again.'):(de?'Die Ortssuche ist nicht erreichbar. Bitte erneut versuchen.':'Place search is unavailable. Please try again.')}}</p>
  <ul v-show="expanded" :id="`${id}-options`" class="address-results address-autocomplete" role="listbox" :aria-label="label"><li v-for="(place,i) in results" :key="`${place.id}-${i}`" role="presentation"><button :id="`${id}-option-${i}`" type="button" role="option" tabindex="-1" class="nes-btn" :class="{'address-option--active':active===i}" :aria-selected="active===i" :disabled="disabled" @pointerdown.prevent @click="choose(place)"><span>{{place.label}}</span><small v-if="place.detail">{{place.detail}}</small><small>{{place.houseNumber?(de?'Hausnummer':'House number')+': '+place.houseNumber:(de?'Ort / Straße — keine bestätigte Hausnummer':'Place / street — no confirmed house number')}}</small></button></li></ul>
 </div>
</template>
