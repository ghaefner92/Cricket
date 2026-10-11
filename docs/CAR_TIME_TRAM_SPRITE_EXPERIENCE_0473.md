# 0.4.73 / 84 — tiempos, tranvía, sprite y Experience

Solicitudes del autor: faltan duración/llegada de auto; el tranvía debe seguir
sus vías; retirar el control «GPS estimate» y confiar en el sprite automático;
Experience debe mostrar «As Planned»/«With Changes» sin la instrucción
«Report this actual journey…».

## Tiempo de auto

Lectura del teléfono: la búsqueda de 13:18 UTC conservaba auto disponible,
701.065 s (aprox. 12 min), incluidos 501.751 s a pie (aprox. 9 min).
Persistía el acceso inicial sin geometría/tiempo de 62.05 m. El cálculo existía;
el helper de llegada devolvía null deliberadamente por ese acceso desconocido.

El resumen muestra tiempo conocido + acceso pendiente y una llegada mínima:
hora de salida + duración de los tramos calculados. Ejemplo: «12 min +» y
«Arrival no earlier: 15:30». No es una predicción completa puerta a puerta.
El plus representa tiempo adicional desconocido; no estimar los 62 m con una
velocidad inventada ni crear una recta. La caminata real permanece incluida.
`summaryArrivalMs` conserva su contrato original; el límite inferior se calcula
en un helper de presentación separado, solo para auto. No modificar métricas
guardadas, aprendizaje ni criterios de elegibilidad.

## Vías del tranvía

El feed local carece de shapes.txt; sus polilíneas unían paradas. Se incorpora
una instantánea OSM de 23 relaciones de tranvía y 613 vías, con licencia y
consulta reproducible en contracts/magdeburg-tram-lines.json.

`tram_alignment.py` usa exclusivamente vías de la relación correspondiente,
conectividad de nodos, sentido permitido, orden de parada inicial/final y todas
las anclas intermedias originales. Límites: paradas/anclas <=25 m de referencia,
recorrido dirigido viable, longitud no desproporcionada y variantes compatibles.
No proyectar cada punto a cualquier riel cercano ni crear un puente en cortes.
Las variantes 1N/1S publicadas por MVB son referencias candidatas para «1»,
pero requieren los mismos controles de orden y anclas.

Caso del teléfono: línea 6, Köln­er Platz→Arndtstraße, coincide con relación
2072898; 94 puntos de vía, 1206.87 m cartográficos, extremos a 2.11/3.54 m de
los puntos GTFS. Conserva horario y métricas GTFS (1119.34 m del tramo), anotando
la longitud de referencia separada. La línea «1» a Spielhagenstraße no coincide:
la referencia OSM 1S comienza a más de 300 m del embarque GTFS. No forzar enlace.

La nueva búsqueda añade OSM_REFERENCE_LINE y metadatos a los tramos compatibles.
También hay una consulta de presentación para registros ya guardados: devuelve
solo correcciones de geometría por ruta/tramo. Android las aplica en memoria;
no reescribir el registro ni reconstruir evidencia antigua. Un tranvía sin
referencia compatible no dibuja su antigua diagonal y muestra «Tram path
unavailable»; se conservan caminatas, horarios y datos originales.

La referencia conserva trazado discontinuo y fidelidad baja: no es una
trayectoria observada ni certifica el servicio efectivo o desvíos actuales.
Passport/HOTCO no se actualizan con el estado final de simulaciones. El problema
prospectivo 413 de resultados grandes continúa independiente de esta entrega.

## Sprite y Experience

Se elimina el botón, etiqueta y diálogo «GPS estimate», así como el override
manual de sprite. Se conserva JourneyAvatarMotion, sus umbrales existentes de
velocidad/precisión/proximidad y su histéresis. No retirar GPS, pausa, captura,
ni cambiar modo registrado por lo que muestra el sprite. Reconocimiento sigue
siendo heurístico y requiere ensayos reales posteriores.

Experience cambia exclusivamente las dos etiquetas y elimina la instrucción
solicitada en EN/DE. Conserva `as_planned`/`changed`, escalas, valores obligatorios
y validadores. El agente no introduce respuestas ni confirma Experience.

## Verificación

68 pruebas JVM, 5 de alineación y 9 de itinerarios aprobadas. El fixture de
alineación exige un giro real del riel, rechaza sentido contrario, otra línea,
anclas fuera de vía y cortes sin conexión. La prueba Android verifica que el
overlay no altera el registro y que conserva caminatas al ocultar una diagonal.
Caso guardado del teléfono reproducido offline: línea 6 coincide, línea 1 no.
Endpoint HTTP de overlay comprobado con geometría de 94 puntos.

No iniciar, pausar ni terminar viajes del teléfono para validar. Durante el
trabajo el autor inició y terminó viajes; eso no es una validación de campo del
agente ni demuestra movimiento real. Instalación y revisión visual se realizan
al disponer de la app sin seguimiento ni respuestas de Experience pendientes.

Instalada 0.4.73 / versionCode 84 en el teléfono del autor. Antes de instalar se
comprobó Experience sin respuestas seleccionadas y sin seguimiento activo.
Tras instalar se abrió el registro pendiente desde Home: aparecen «As Planned»
y «With Changes» y desaparece la instrucción solicitada. Se dejó esa pantalla
abierta, sin seleccionar respuestas ni confirmar. La presentación de auto y
tranvía tiene pruebas de datos/HTTP/JVM; no se realizó un nuevo viaje de campo
ni se verificó visualmente cada línea en el teléfono.

Fuentes: [GTFS.de servicios/shapes](https://www.gtfs.de/en/services/),
[MVB línea 1](https://www.mvbnet.de/fahrinfo/netz_fahrplaene/strassenbahnlinie-1/),
[MVB red durante obras](https://www.mvbnet.de/zukunftsgleise-innenstadt-neues-liniennetz-waehrend-bauzeit-am-alten-markt/),
[OSM licencia](https://www.openstreetmap.org/copyright).
