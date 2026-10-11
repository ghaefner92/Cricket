# Cricket · arte JRPG · 0.4.1

## Dirección visual

Estética JRPG retro moderna: siluetas chibi, textura fina de pixel art,
contornos azul noche suaves, luces doradas y detalles menta. «64 bits» se
interpreta como referencia estética de videojuegos, no como profundidad de PNG.
Se mantienen robot, explorer, creature y naturalist, sus accesorios y mascotas.

## Recursos e integración

Diez recursos nuevos en `android-native/app/src/main/assets/art/jrpg/`:
cuatro láminas de expresiones, cuatro láminas de transporte y dos fondos.
Generados mediante la herramienta integrada image_gen. Prompts y modo en
`docs/JRPG_ASSET_PROMPTS.json`. Los originales permanecen como referencia;
la interfaz nativa usa los nuevos recursos v2.

Cada lámina de expresiones tiene 6 columnas y 4 estados: reposo, conversación,
alegría y reflexión. Las láminas de transporte representan caminar, bicicleta,
coche y tranvía. `transport-atlas.json` contiene recortes medidos por fotograma;
se conserva una escala común por modo y una línea de suelo fija para evitar
estiramiento, saltos de tamaño y ruedas cortadas. El script opcional
`android-native/scripts/inspect-sprite-atlas.py` lee geometría alfa y escribe
metadatos; no modifica imágenes. Pillow solo se requiere para regenerarlos.

`JrpgUi.kt` dibuja los sprites, respiración y movimiento usando el reloj de
fotogramas Compose. Los iconos de navegación, metas y acciones se dibujan
en un lienzo de 64 unidades, con curvas, luces y contornos, para conservar
nitidez a distintos tamaños. Una caché compartida de 32 MB evita decodificar el
mismo arte en cada tarjeta; nunca se decodifica en cada fotograma. Reduced/Off
mantienen personajes y fondos estáticos. El cambio de meta usa un fundido suave.

## Interfaz

- Marcos y botones redondeados; superficies azul noche con degradado suave.
- Home, Travel, Passport y Menu tienen iconos semánticos propios.
- Las once metas tienen símbolos diferenciados.
- Generador de Passport con etapas e ilustraciones de jardín/estación.
- Search y Journey preferences se expanden al tocar el encabezado.
- My location y Swap tienen iconos y despliegan información del resultado
  de la acción, conservando sus funciones directas. El panel se cierra con «−».
- Menu incorpora los mismos iconos en sus opciones.

## Alcance

Esta entrega cambia presentación y animación. Conserva los contratos de viajes,
tracking, GPS, datos de usuario y lógica HOTCO/Orion/OTP. No reintroduce tolerancias.
Los fondos son ilustraciones artísticas; el mapa de rutas continúa siendo el mapa
geográfico real. La generación no garantiza seis poses de marcha perfectamente
equidistantes: la fluidez incluye movimiento continuo de escena y respiración,
con animación de poses a 6 fps para transporte y cambios de expresión más lentos.

Construcción: `npm run android:apk` desde `experimental-frontend`.
APK: `android-native/app/build/outputs/apk/debug/app-debug.apk`.

## Entrega

Compilación `assembleDebug` correcta e instalación `adb install -r` correcta
en Samsung SM-A566B (RZCY70K63HV), versión 0.4.1, código 12.
Revisión visual en el dispositivo de Home, Search con preferencias expandidas,
Passport/Feelings y Menu; se observaron los compañeros y las escenas de caminar,
tranvía y coche. Esta tarea no ejecutó suites de pruebas de contratos ni tracking.
