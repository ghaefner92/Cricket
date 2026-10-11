# Travel: mapa primero y selección directa — 0.4.84 / 95

Sustituye el flujo de confirmación de 0.4.83 a solicitud del autor.

Decisiones de interacción:

- Mapa fijo arriba, 60 % del área disponible; formulario abajo, 40 %.
  El mapa deja de estar dentro de ScreenColumn/verticalScroll. Solo los campos,
  sugerencias y opciones inferiores se desplazan. Find routes permanece visible
  debajo de esa zona desplazable. El teclado reduce el área disponible.
- Selector A/B y breve instrucción superpuestos arriba del mapa. Un toque coloca
  el punto y actualiza el campo inmediatamente. Seleccionar A pasa a B; seleccionar
  B permite corregir B mediante otro toque. Para corregir A se elige su selector.
  No hay candidato pendiente, confirmación ni bloqueo de búsqueda por geocodificar.
- La dirección inversa se completa en segundo plano. Conserva coordenadas exactas,
  etiqueta la dirección como cercana y no acredita número de edificio. Si falla,
  se mantienen las coordenadas. Una respuesta tardía solo actualiza la etiqueta si
  el mismo lugar sigue seleccionado y no empezó una búsqueda. Nunca vuelve a llamar
  a setPlace desde la respuesta ni invalida resultados por una etiqueta tardía.
- Se ocultan los textos auxiliares repetidos bajo los campos de esta pantalla;
  los otros usos de AddressField conservan sus indicaciones. La etiqueta del punto
  sigue distinguiendo la dirección cercana de las coordenadas elegidas.
- Los puntos elegidos por mapa no provocan encuadre automático tras cada toque.
  Las etiquetas actualizadas tampoco. Se conserva el encuadre inicial y al cambiar
  direcciones manuales. El toque y los gestos desactivan seguimiento de cámara.
- Scroll, zoom y doble toque de MapLibre se habilitan explícitamente. Separar el
  mapa de su antecesor desplazable elimina la competencia del formulario por el
  arrastre. Se conserva inclinación desactivada, capas, ajustes, POIs y avatar.
- El avatar permanece ligado a la posición física, separado de A. Al completar
  automáticamente un origen vacío mediante ubicación, el selector pasa a B.
  Find routes conserva el único disparador de búsqueda, recomendaciones y
  congelación prospectiva. No se modifican Passport, escalas ni aprendizaje.

Referencias técnicas consultadas:
[gestos MapLibre](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-ui-settings/index.html),
[intercepción de entrada en Compose](https://developer.android.com/reference/kotlin/androidx/compose/ui/input/pointer/pointerInteropFilter.modifier).
La distribución 60/40 es una decisión de este producto, no un estándar ni una
copia exacta de Google Maps.

Validación: build Debug y 82 pruebas JVM aprobadas. Copia previa verificada
20261011-190519, 29 búsquedas, Passport revisión9. Actualización install -r.
Inspección previa confirmó la composición problemática del mapa inferior dentro
del formulario. La interacción de arrastre/pinch en el diseño nuevo requiere
comprobación en teléfono; no se adquirió GPS ni se registraron elecciones o
Experience como datos de prueba. Las pruebas existentes de coordenadas, fallback
y exclusión de geometrías en planificación siguen aprobadas.
