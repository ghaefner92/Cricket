# Auditoría y correcciones de movilidad — 0.4.76 / 87

Solicitada tras observar lentitud al dibujar tranvías. Se revisaron el mapa
Compose/MapLibre, referencias OSM, adaptación OTP/GTFS, accesos GraphHopper,
selección de paradas y carga del historial. No se inicia GPS ni se registra
Experience para comprobar rendimiento.

## Hallazgos y cambios

| Hallazgo | Corrección |
|---|---|
| Cada salida repetía el cálculo de la misma geometría de tranvía | Caché LRU de 128 referencias por línea, coordenadas, anclas e identidad de instantánea; copias independientes al devolver. No cachear horarios, retrasos ni meteorología. |
| Proyecciones candidatas de extremos se recalculaban dentro del bucle | Caché local de proyecciones durante cada búsqueda. |
| La fase de animación invalidaba cinco contornos, POIs y etiquetas | Tres capas de dibujo: bases estáticas, luz móvil y POIs estáticos. Fase de luz limitada a 30 actualizaciones/s; contornos discontinuos estáticos. |
| Registro de viaje recargado por GPS podía reiniciar la consulta de trazado | Resultados inmutables recordados por search_id, no por cada objeto JSON de estado. |
| Catálogo de plataformas se combinaba en el hilo UI | Combinación en Dispatchers.Default; selección/cards recordados por resultado y ruta. |
| Historial leía todas las simulaciones en el hilo UI | Páginas de metadatos en IO; conservar solo identificadores, elecciones y métricas de viajes, sin resultados ni puntos GPS. Abrir/exportar/detalle recuperan el original completo en IO. |
| Reflexión leía el registro completo en el hilo UI | Lectura en IO. |
| Aviso de acceso desconocido ocultaba la salida vencida | «Departure passed · search again» tiene prioridad; estados de viaje activo conservan su prioridad. |
| Adaptador aceptaba duraciones incompatibles con sus relojes | Rechazar diferencias >2 s entre duración y timestamps, tramos fuera del intervalo, solapes y uniones geométricas >25 m. |
| Acceso a parada solo comprobaba la coordenada GTFS | Comprobar también la unión a la geometría real del primer tramo OTP. |

## Coherencia de las fuentes

OTP proporciona identidad de viaje, paradas, horario y métricas PT. El reloj
local usa Europe/Berlin; tiempos del registro permanecen epoch ms. La referencia
OSM solo cambia geometría y conserva longitud cartográfica por separado. No
recalcular tiempo GTFS con una velocidad de tranvía ni convertir referencia
física en shape de servicio certificada. Las alternativas de red continúan
discontinuas y con aviso explícito de servicio no confirmado.

GraphHopper aporta caminos y métricas de pie/bici/auto del mismo proveedor.
Accesos a pie de auto se suman una vez al total, con unión <=25 m. Para subir a
una parada elegida, se calcula acceso antes de solicitar la conexión alcanzable,
se conserva viaje/salida/parada y no se duplica la espera inicial. Acceso sin
trazado conserva tiempo desconocido; auto muestra límite inferior de llegada.
La geometría cartográfica y el horario pueden tener distancias distintas; eso
se conserva como procedencia, no como incoherencia que deba ocultarse.

La captura 14:00:32 UTC contiene cinco tramos de tranvía. Auditoría offline:
ninguna discrepancia de duración/timestamp, solape o unión >25 m. La respuesta
OTP original se readaptó con los nuevos controles: tres itinerarios, sin rechazos.
Eso valida el caso técnico, no toda la red ni desvíos de servicio actuales.

## Rendimiento y validación

Mismo fixture privado y proceso Python nuevo, tres pasadas del lote de cinco
tramos: antes 2262 / 2064 / 2066 ms; después 398 / 0.84 / 1.01 ms. La primera
pasada incluye cómputo de referencias distintas; las siguientes reutilizan
geometría. Es un benchmark local, no latencia total de búsqueda ni FPS Android.
Script reproducible: validation/audit_mobility_performance.py.

Teléfono antes de cambios: gfxinfo acumulado 13050 frames, 8.26% con deadline
incumplido; p95=34 ms, p99=150 ms. Caché gráfica de máscaras 74.72 MB / 965
entradas. No demuestra fuga ni atribución exclusiva al mapa. Sin A/B controlado
equivalente no afirmar una mejora porcentual de fluidez del teléfono.

Pruebas: 8 de referencias, 11 de paradas, 7 de accesos auto, 4 de parking y
69 JVM. Incluyen caché sin mutación compartida, incompatibilidad de relojes,
unión real de acceso, cortes, dirección, ambigüedad y previews sin GPS/resultados.
El laboratorio D-Day y Passport/HOTCO no se modifican; no reinterpretar un
trazado de referencia ni una prueba técnica como experiencia observada.

Persisten límites: falta shapes certificadas del feed; catálogo/teselas OSM
pueden discrepar con obras; consultas externas, deliberación y payloads grandes
(forecast 413 previamente observado) son costes separados de esta optimización.
La caché de referencias no intenta resolverlos ni congelar actualidad de servicio.

Instalación final confirmada: teléfono 0.4.76 / 87 y backend actualizado. Historial
carga sus previews y abre una búsqueda original; PT de línea 1 muestra referencia
de vías y horario. No se confirmó una elección ni se inició seguimiento. El lote
total de pruebas aprobado suma 99 (69 JVM + 30 backend). El historial todavía
debe leer los registros para extraer metadatos; hacerlo en IO evita bloquear la
UI, pero no constituye un índice materializado ni elimina ese coste de lectura.

Muestra posterior de unos 39 s con el mapa PT abierto, cámara quieta y animación:
4725 frames, p95=19 ms, p99=20 ms; 5 deadlines incumplidos (0.11%). El contador
legacy da 82.20%, por lo que se conservan ambas métricas y no se infiere FPS ni
cumplimiento de todas las tasas de refresco. Esta muestra no es equivalente al
acumulado inicial de varias pantallas ni prueba rendimiento de navegación/GPS.
Después de dos desplazamientos horizontales de cámara, muestra acumulada:
8499 frames, p95=18 ms, p99=24 ms, 66 deadlines incumplidos (0.78%);
legacy=80.48%. Persisten fotogramas lentos durante cambios de cámara y no se
declara resuelto todo el presupuesto de 120 Hz. Se verificó visualmente la
línea 1, accesos a pie y horario, sin cambiar la evidencia del registro original.
