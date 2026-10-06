<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { startMagdeburgScene } from '../animation/magdeburgScene.js'

const props = defineProps({ locale: { type: String, default: 'en' } })
const canvas = ref(null)
const logoUrl = `${import.meta.env.BASE_URL}branding/imiq-ovgu-logo.png`
const copy = computed(() => props.locale === 'de' ? {
  title: 'Deine Reise beginnt', loading: 'Dein Begleiter wird vorbereitet…',
  city: 'Magdeburg', scene: 'Pixel-Art-Szene des Magdeburger Doms mit einer Person zu Fuß, einem Fahrrad, einem Auto und einer Straßenbahn.',
  modes: ['Zu Fuß', 'Fahrrad', 'Auto', 'ÖPNV'],
} : {
  title: 'Your journey starts here', loading: 'Preparing your companion…',
  city: 'Magdeburg', scene: 'Pixel-art scene of Magdeburg Cathedral with a pedestrian, a bicycle, a car and a tram.',
  modes: ['Walk', 'Bike', 'Car', 'Public transport'],
})
let stop = () => {}, media
function restart() { stop(); if (canvas.value) stop = startMagdeburgScene(canvas.value, { reducedMotion: media.matches }) }
onMounted(() => { media = window.matchMedia('(prefers-reduced-motion: reduce)'); restart(); media.addEventListener('change', restart) })
onBeforeUnmount(() => { stop(); media?.removeEventListener('change', restart) })
</script>

<template>
  <main class="loading-shell" :lang="locale" aria-busy="true">
    <div class="loading-heading"><span class="scene-kicker">IMIQ · DIGITAL COMPANION</span><h1>{{ copy.title }}</h1></div>
    <section class="nes-container is-dark scene-frame">
      <span class="scene-city">{{ copy.city }}</span>
      <canvas ref="canvas" class="city-canvas" role="img" :aria-label="copy.scene"></canvas>
      <ul class="mode-legend" :aria-label="locale === 'de' ? 'Verkehrsmittel' : 'Transport modes'"><li v-for="mode in copy.modes" :key="mode">{{ mode }}</li></ul>
    </section>
    <div class="loading-status" role="status"><span class="loading-dot" aria-hidden="true"></span><p>{{ copy.loading }}</p></div>
    <footer class="branding-strip"><img :src="logoUrl" alt="Otto-von-Guericke-Universität Magdeburg · Intelligenter Mobilitätsraum" width="421" height="97"></footer>
  </main>
</template>
