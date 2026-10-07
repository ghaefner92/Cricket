<script setup>
import { onMounted, ref, shallowRef } from 'vue'
import SettingsMenu from './components/SettingsMenu.vue'
import {readSettings,applySettings} from './services/appSettings.js'
import {readActivePassport} from './services/passportLifecycle.js'
import './styles/settings.css'
import LoadingScene from './components/LoadingScene.vue'

const query = new URLSearchParams(window.location.search)
const preview = query.get('preview') === 'loading'
const stored = localStorage.getItem('imiq.experimental.language')
const locale = ref(query.get('lang') === 'de' ? 'de' : query.get('lang') === 'en' ? 'en' : stored === 'de' ? 'de' : 'en')
const settingsOpen=ref(false),flowRevision=ref(0),menuButton=ref(null)
const startMode=ref('home')
function refreshFlow(value){startMode.value=value==='passport'?'passport':'home';flowRevision.value++}
function closeSettings(){settingsOpen.value=false;menuButton.value?.focus()}
const loading = ref(true)
const error = ref(false)
const page = shallowRef(null)
function setLocale(value) { locale.value = value;localStorage.setItem('imiq.experimental.language',value);document.documentElement.lang=value }

async function preparePage() {
  error.value=false;loading.value=true
  try {
    const logo = new Image()
    logo.src = `${import.meta.env.BASE_URL}branding/imiq-ovgu-logo.png`
    const minimumIntroDuration = new Promise(resolve => {
      window.setTimeout(resolve, readActivePassport(localStorage)?2000:4000)
    })

    const [module] = await Promise.all([
      import('./pages/CreateCompanionPage.vue'),
      logo.decode().catch(() => undefined),
      document.fonts.ready,
      minimumIntroDuration,
    ])
    page.value=module.default
    loading.value=false
  } catch (failure) { console.error('Companion page preparation failed',failure);error.value=true;loading.value=false }
}
onMounted(() => { applySettings(readSettings(localStorage));document.documentElement.lang=locale.value;if(!preview)preparePage() })
</script>
<template>
  <LoadingScene v-if="loading || preview" :locale="locale" />
  <main v-else-if="error" class="companion-page"><section class="nes-container"><p>{{ locale === 'de' ? 'Die Seite konnte nicht geladen werden.' : 'The page could not be loaded.' }}</p><button class="nes-btn is-primary" @click="preparePage">{{ locale === 'de' ? 'Erneut versuchen' : 'Try again' }}</button></section></main>
  <div v-else><div class="cricket-app-menu"><button ref="menuButton" class="nes-btn" type="button" @click="settingsOpen=true">☰ {{locale==='de'?'Menü':'Menu'}}</button></div><component :is="page" :key="flowRevision" :start-mode="startMode" :locale="locale" @locale-change="setLocale" /><SettingsMenu v-if="settingsOpen" :locale="locale" @locale-change="setLocale" @close="closeSettings" @flow-reset="refreshFlow"/></div>
</template>
