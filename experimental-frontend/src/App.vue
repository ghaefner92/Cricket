<script setup>
import { onMounted, ref, shallowRef } from 'vue'
import LoadingScene from './components/LoadingScene.vue'

const query = new URLSearchParams(window.location.search)
const preview = query.get('preview') === 'loading'
const stored = localStorage.getItem('imiq.experimental.language')
const locale = ref(query.get('lang') === 'de' ? 'de' : query.get('lang') === 'en' ? 'en' : stored === 'de' ? 'de' : 'en')
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
      window.setTimeout(resolve, 4000)
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
onMounted(() => { document.documentElement.lang=locale.value;if(!preview)preparePage() })
</script>
<template>
  <LoadingScene v-if="loading || preview" :locale="locale" />
  <main v-else-if="error" class="companion-page"><section class="nes-container"><p>{{ locale === 'de' ? 'Die Seite konnte nicht geladen werden.' : 'The page could not be loaded.' }}</p><button class="nes-btn is-primary" @click="preparePage">{{ locale === 'de' ? 'Erneut versuchen' : 'Try again' }}</button></section></main>
  <component :is="page" v-else :locale="locale" @locale-change="setLocale" />
</template>
