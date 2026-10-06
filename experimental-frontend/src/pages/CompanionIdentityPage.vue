<script setup>
import { computed, reactive, ref, watch } from 'vue'
import AvailabilityKit from '../components/AvailabilityKit.vue'
import CompanionAvatar from '../components/CompanionAvatar.vue'
import { messages } from '../i18n/companion.js'
import { palettes } from '../art/companions.js'
import { readCompanion, writeCompanion } from '../stores/companion.js'
import '../styles/companion-page.css'
const props = defineProps({ locale: { type: String, default: 'en' } })
const emit = defineEmits(['locale-change', 'continue'])
const copy = computed(() => messages[props.locale] || messages.en)
let storage
try { storage = window.localStorage } catch { storage = { getItem() { return null }, setItem() { throw new Error('Storage unavailable') } } }
const companion = reactive(readCompanion(storage))
const kitReady = ref(false)
const saved = ref(false)
const draftSaved = ref(false)
const errorKey = ref('')
const nameInput = ref(null)
const previewName = computed(() => companion.name.trim() || copy.value.anonymous)
const count = computed(() => [...companion.name].length)
watch(companion, () => {
  saved.value = false
  errorKey.value = ''
  try { writeCompanion(storage, companion); draftSaved.value = true }
  catch { draftSaved.value = false; errorKey.value = 'storage' }
}, { deep: true })
function save() {
  if (!kitReady.value) return
  const name = companion.name.trim()
  if (!name || [...name].length > 24) {
    errorKey.value = name ? 'long' : 'empty'
    nameInput.value?.focus()
    return
  }
  try {
    writeCompanion(storage, { ...companion, name }, true)
    writeCompanion(storage, { ...companion, name })
    saved.value = true
    emit('continue')
    draftSaved.value = true
    errorKey.value = ''
  } catch { errorKey.value = 'storage' }
}
</script>
<template>
  <main class="companion-page companion-builder" :lang="locale">
    <header class="builder-topbar">
      <span class="builder-brand">IMIQ <span>·</span></span>
      <nav class="language-switch" :aria-label="copy.language">
        <button v-for="language in ['en', 'de']" :key="language" type="button" class="nes-btn" :class="{ 'is-primary': locale === language }" :aria-pressed="locale === language" @click="emit('locale-change', language)">{{ language.toUpperCase() }}</button>
      </nav>
    </header>
    <header class="builder-heading">
      <p class="builder-kicker">{{ copy.step }}</p>
      <h1 tabindex="-1">{{ copy.title }}</h1>
      <p class="builder-copy">{{ copy.intro }}</p>
    </header>
    <div class="builder-layout">
      <section class="nes-container with-title preview-panel" :aria-label="copy.preview">
        <p class="title">{{ copy.preview }}</p>
        <div class="avatar-stage" :style="{ '--accent': palettes[companion.palette][2] }">
          <span class="stage-star star-one" aria-hidden="true">✦</span><span class="stage-star star-two" aria-hidden="true">✦</span>
          <CompanionAvatar :appearance="companion.appearance" :palette="companion.palette" animated />
          <span class="avatar-shadow" aria-hidden="true"></span>
        </div>
        <h2 class="preview-name">{{ previewName }}</h2>
        <p class="character-type">{{ copy.characters[companion.appearance] }}</p>
        <div class="nes-balloon from-left preview-speech"><p class="builder-copy">{{ companion.name.trim() ? copy.speech(companion.name.trim()) : copy.greeting }}</p></div>
      </section>
      <form class="nes-container builder-form" novalidate @submit.prevent="save">
        <fieldset class="builder-fieldset">
          <legend>{{ copy.appearance }}</legend>
          <p id="appearance-hint" class="builder-copy field-hint">{{ copy.appearanceHint }}</p>
          <div class="character-options" aria-describedby="appearance-hint">
            <label v-for="appearance in ['robot', 'explorer', 'creature']" :key="appearance" class="character-option" :class="{ 'is-selected': companion.appearance === appearance }">
              <CompanionAvatar :appearance="appearance" :palette="companion.palette" />
              <span class="radio-line"><input v-model="companion.appearance" type="radio" class="nes-radio" name="appearance" :value="appearance"><span>{{ copy.characters[appearance] }}</span></span>
            </label>
          </div>
          <p class="builder-copy character-description">{{ copy.descriptions[companion.appearance] }}</p>
        </fieldset>
        <fieldset class="builder-fieldset palette-fieldset">
          <legend>{{ copy.palette }}</legend>
          <div class="palette-options">
            <label v-for="(colors, palette) in palettes" :key="palette" class="palette-option">
              <input v-model="companion.palette" type="radio" class="nes-radio" name="palette" :value="palette">
              <span><i class="palette-swatch" :style="{ background: colors[2] }" aria-hidden="true"></i>{{ copy.palettes[palette] }}</span>
            </label>
          </div>
        </fieldset>
        <div class="nes-field builder-name-field">
          <label for="companion-name">{{ copy.name }}</label>
          <input id="companion-name" ref="nameInput" v-model="companion.name" class="nes-input" :class="{ 'is-error': ['empty','long'].includes(errorKey) }" :placeholder="copy.placeholder" :aria-invalid="['empty','long'].includes(errorKey)" aria-describedby="name-hint name-count companion-error" autocomplete="off" maxlength="24" required>
          <div class="name-help"><p id="name-hint" class="builder-copy field-hint">{{ copy.hint }}</p><span id="name-count">{{ count }}/24</span></div>
        </div>
        <AvailabilityKit :locale="locale" :appearance="companion.appearance" :palette="companion.palette" @ready="kitReady = $event" />
        <p id="companion-error" class="builder-copy builder-error" role="alert">{{ errorKey ? copy[errorKey] : '' }}</p>
        <button type="submit" class="nes-btn is-primary builder-save" :disabled="!kitReady">{{ locale === 'de' ? 'Weiter: Wochenziele' : 'Next: weekly goals' }}</button>
        <p class="builder-copy draft-status" role="status">{{ draftSaved && !errorKey ? copy.autosaved : '' }}</p>
      </form>
    </div>
    <section v-if="saved" class="nes-container is-rounded saved-panel" role="status">
      <h2><i class="nes-icon is-small star" aria-hidden="true"></i> {{ copy.saved }}</h2>
      <p class="builder-copy">{{ copy.savedBody(companion.name.trim()) }}</p>
    </section>
    <aside class="builder-next"><h2>{{ copy.next }}</h2><p class="builder-copy">{{ copy.nextBody }}</p></aside>
  </main>
</template>
