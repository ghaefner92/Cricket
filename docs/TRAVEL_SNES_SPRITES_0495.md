# Sprites SNES de búsqueda — 0.4.95

Solicitud: nuevos sprites JRPG de alta resolución animados para búsqueda,
preferencias, recientes, intercambio y letras A/B. Disposición izquierda A,
intercambio, B, sin línea ni símbolo que atraviese horizontalmente la tarjeta.

Se generan seis atlas PNG RGBA originales mediante la herramienta integrada
image_gen.imagegen (habilidad imagegen), un atlas 2 × 2 por acción, con cuatro
poses de destellos. Paleta menta/oro/lavanda y contorno ciruela, píxeles de
inspiración SNES. Prompts y procedencia: TRAVEL_SNES_SPRITES_0495_PROMPTS.json.
Activos de proyecto: android-native/app/src/main/assets/art/jrpg/travel-*-v1.png.

TravelActionSprite reutiliza el caché de imágenes y dibuja una celda por pose
sin decodificar imágenes en cada fotograma. Cambio de pose cada 320 ms, solo
con ciclo de vida STARTED y preferencia de movimiento completo. Movimiento
reducido conserva primera pose. El indicador de selección A/B se dibuja aparte.
Los botones mantienen etiquetas EN/DE y áreas de pulsación de 48 dp.

Columna izquierda: A / intercambio vertical / B. Dirección manual alineada
con A/B y espacio central; derecha: lupa / preferencias / recientes. Se conserva
la altura de tres acciones y no hay separadores horizontales ni nuevos pasos.
No se altera Passport, HOTCO, datos personales ni lógica de búsqueda.

Verificación: seis PNG RGBA de 1254 × 1254, cuatro celdas de 627 × 627 por
atlas, transparencia real, inspección visual de los seis, empaquetados en APK.
91 pruebas JVM aprobadas. 0.4.95/code106 instalado con reemplazo; respaldo
verificado con 43 búsquedas y Passport revisión 9. No se entra automáticamente
a Travel para probar adquisición de ubicación; revisión interactiva pendiente.

Rutas exactas y hashes: [manifiesto de activos](TRAVEL_SNES_SPRITES_0495_ASSETS.json).
Prompts completos: [prompts de generación](TRAVEL_SNES_SPRITES_0495_PROMPTS.json).
Archivos: travel-search-v1.png, travel-preferences-v1.png, travel-recent-v1.png,
travel-swap-v1.png, travel-origin-v1.png y travel-destination-v1.png, todos bajo
android-native/app/src/main/assets/art/jrpg/.
