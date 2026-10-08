import { createApp } from 'vue'
import '@fontsource/press-start-2p'
import 'nes.css/css/nes.min.css'
import './style.css'
import App from './App.vue'
import {initializeNative} from './services/nativePlatform.js'

initializeNative().catch(error=>console.error('Native initialization failed',error))
createApp(App).mount('#app')
import './styles/pixel-typography.css'

import './styles/journey-refinement.css'
import './styles/mobile.css'
