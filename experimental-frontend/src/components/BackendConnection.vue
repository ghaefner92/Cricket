<script setup>
import {computed,ref} from 'vue'
import {apiFetch,backendBase,saveBackend} from '../services/apiTransport.js'
const props=defineProps({locale:{default:'en'}})
const de=computed(()=>props.locale==='de'),address=ref(backendBase()),busy=ref(false),message=ref(''),failed=ref(false)
async function connect(){
 busy.value=true;message.value='';failed.value=false
 const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),12000)
 try{
  address.value=saveBackend(address.value)
  const response=await apiFetch('/api/dyconet/health',{signal:controller.signal})
  const health=await response.json()
  if(!response.ok||!health.model_version)throw Error('health')
  message.value=de.value?'Server verbunden.':'Server connected.'
 }catch{failed.value=true;message.value=de.value?'Verbindung fehlgeschlagen. Adresse und Server prüfen.':'Could not connect. Check the address and server.'}
 finally{clearTimeout(timer);busy.value=false}
}
</script>
<template>
 <section>
  <h2>{{de?'Verbindung':'Connection'}}</h2>
  <p>{{de?'Adresse des Cricket-Servers. Dein Profil bleibt auf diesem Gerät.':'Cricket server address. Your profile stays on this device.'}}</p>
  <label>{{de?'Serveradresse':'Server address'}}<input v-model="address" type="url" inputmode="url" autocapitalize="off" autocomplete="off" spellcheck="false" :disabled="busy" placeholder="https://cricket.example.org"/></label>
  <button type="button" class="nes-btn is-primary" :disabled="busy" @click="connect">{{busy?'…':de?'Speichern und verbinden':'Save and connect'}}</button>
  <p v-if="message" :role="failed?'alert':'status'">{{message}}</p>
 </section>
</template>
