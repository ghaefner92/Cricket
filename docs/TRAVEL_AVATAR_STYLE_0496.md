# Botones con referencia de avatares — 0.4.96

El autor considera excesivos los sprites ornamentados de 0.4.95 y solicita
tomar como referencia los avatares originales. Se usa naturalist-sheet-v2.png,
el mismo atlas empleado por CompanionSprite, como referencia visual explícita.
No se modifican los personajes ni sus atlas.

Se generan variantes simplificadas de las seis acciones: siluetas claras,
contorno oscuro, sombreado moderado y colores suaves. Se retiran gemas, coronas,
estrellas y marcos decorativos de los botones. Cada imagen conserva cuatro
poses y transparencia; animación mediante cambios pequeños de iluminación.
Los sprites se muestran a 28 dp (intercambio 24) con áreas táctiles de 48 dp.
Intervalo de animación 640 ms, limitado a ciclo de vida STARTED y preferencia
de movimiento completo; estáticos con movimiento reducido.

Se conserva la disposición A/intercambio/B a la izquierda y las acciones de
búsqueda, preferencias y recientes a la derecha. Versiones originales guardadas
como v1; variantes nuevas travel-*-v2.png. Sin cambios de rutas, Passport ni HOTCO.

Habilidad imagegen y herramienta integrada image_gen.imagegen, con atlas
anterior como objetivo de edición y avatar como referencia de estilo.

Prompts exactos: [TRAVEL_AVATAR_STYLE_0496_PROMPTS.json](TRAVEL_AVATAR_STYLE_0496_PROMPTS.json).
Rutas, dimensiones y SHA256 de los seis archivos:
[TRAVEL_AVATAR_STYLE_0496_ASSETS.json](TRAVEL_AVATAR_STYLE_0496_ASSETS.json).

Verificación: assembleDebug y testDebugUnitTest correctos, 91 pruebas sin
fallos, errores ni exclusiones. Se comprueban los seis atlas RGBA con
transparencia real, cuatro celdas y coincidencia SHA256 dentro de la APK.
Instalación con install -r correcta; versión 0.4.96, código 107.
Respaldo previo verificado 20261011-213203: 43 búsquedas, Passport
0504e5fa-b367-5835-8e36-890f4a08b68a revisión 9. No se generan viajes ni
respuestas para validar el cambio. La revisión interactiva del tamaño y
animación en Travel queda disponible para el autor en el teléfono.
