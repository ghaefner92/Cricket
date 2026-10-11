# D-Day — entrega técnica 0.4.62 y pendientes de cierre científico

Fecha: 10 de octubre de 2026. La entrega sigue dentro de fase 4, sin activar
actualización de expectativas en el Passport ni conexiones aprendidas en HOTCO.
Las fases 5–6 siguen pendientes. F3, RL, deep learning y copia de estados
terminales quedan fuera del enfoque. No se cambió la UI.

## Entrega concreta

| Componente | Resultado |
|---|---|
| Contratos temporales de Orion | `Europe/Berlin` confirmado por el autor; Walter también fecha Winfred según garantía del proveedor confirmada por el autor |
| Auditoría real | Consultas públicas reproducibles de las dos estaciones; originales, atributos y diagnósticos archivados |
| Captura durante el viaje | Consultas ambientales asociadas a posiciones GPS retenidas, fuera de la cola de controles/SQLite |
| Calidad y trazabilidad | Fuentes, unidades, fechas originales, referencia a Walter, errores, interrupciones y ausencia explícita |
| Registro longitudinal | Campo opcional versionado en el contrato común Python/Android; compatible con viajes históricos sin captura |
| Revisión del piloto | Comparaciones prospectivas sobre eventos comunes y cobertura de intervalos predictivos de respuestas ordinales |
| Sensibilidad HOTCO | Red completa 11–4–4, ocho perfiles, tres priors, dos condiciones; trayectorias y parámetros conservados |
| Instalación | App 0.4.62, código 73, instalada; backend actualizado en 8077; proxy GTFS de 8092 conservado y `/status` comprobado |

## Reloj confirmado y auditoría de sensores reales

La zona de fechas locales es `Europe/Berlin`, con horario de verano/invierno.
No usar un desplazamiento UTC fijo. Fechas ambiguas o inexistentes durante el
cambio de hora siguen excluidas si falta información para resolverlas.

Winfred toma `Walter.datetime` únicamente de la misma QueryList cuando carece
de fecha propia. Se guardan `timestamp_reference_entity`, atributo de referencia
y `PROVIDER_SHARED_CLOCK_CONFIRMED_BY_AUTHOR`; no se inventa un `observedAt`
individual ni se reutiliza la fecha de otra consulta. La preparación del piloto
conserva la fecha textual y añade la zona con procedencia explícita.

La auditoría real está en `output/d-day-phase4-close/orion-contract-audit.json`.
Las consultas finales contienen lecturas de Walter y Winfred que superan los
30 minutos de antigüedad admitidos por el piloto. La respuesta del servidor
puede estar disponible y conservar sus valores aunque el conteo de meteorología
utilizable sea cero. No cambiar la regla para hacer pasar artificialmente datos
viejos. Es necesario comprobar con el proveedor la cadencia y vigencia reales.

Winfred proporciona `rainIntensity` en mm/h. El predictor histórico `rain`
espera mm; ambas cantidades no son equivalentes. Se conserva mm/h y su
incompatibilidad, sin convertirla arbitrariamente a una acumulación. Walter
carece de unidad explícita y de una ventana de lluvia identificada. La decisión
del predictor comparable de lluvia requiere un contrato de magnitud/ventana;
un eventual cambio de codificación exige versión y nueva evaluación, no
reetiquetar datos o simulaciones anteriores. Los periodos de ráfagas y de
promedio del viento también deben documentarse. UV permanece excluido.

Estas tareas de proveedor no necesitan participantes. La confirmación del autor
resuelve el contrato de reloj; no confirma por sí sola la actualización de los
valores ni las ventanas de acumulación/promedio.

## Captura ambiental durante viajes

Endpoint de lectura: `POST /api/dyconet/d-day/context/sample`, cuerpo únicamente
`lat` y `lon`. Consulta la estación más cercana dentro de 5 km y devuelve
observaciones originales, contexto preparado y auditoría. No recibe respuestas
del usuario ni IDs personales, no ajusta un posterior y no almacena en servidor.

Android captura desde una posición GPS aceptada: precisión máxima 50 m, sin
mock y vigencia del fix validada por el tracking existente. La política operativa
candidata consulta como máximo una vez por 60 segundos, con una única solicitud
pendiente y un límite de 240 muestras por viaje. Tras una interrupción puede
capturar de nuevo al disponer de un fix válido en un nuevo segmento. Son límites
operativos, no criterios de validez científica. Los fixes estacionarios se
retienen cada 60 segundos sin sumar desplazamientos de jitter.

Las llamadas HTTP corren en `Dispatchers.IO`, separadas del worker que atiende
GPS, pausa/finalización y SQLite. La persistencia vuelve a ese worker ordenado.
Una falla ambiental no detiene el tracking. Las pausas no inician consultas;
consultas iniciadas previamente conservan su procedencia. Finalización,
cancelación, recuperación y cierre del servicio interrumpen muestras pendientes.
Las respuestas tardías no alteran viajes cerrados o confirmados.

El campo `context_capture` usa `d-day-trip-context-v1`: cada muestra tiene ID,
referencia monotónica al fix, segmento, solicitud/recepción, estado y resultado.
El contrato rechaza muestras sin fix, IDs duplicados, solicitudes durante pausa,
ubicaciones incompatibles, respuestas fuera del viaje y pendientes tras cerrar.
El backend también vuelve a comprobar la auditoría y su correspondencia con
las observaciones originales. Se conservan fallas e interrupciones como tales.

La descripción disponible es **ambiente cercano durante consultas del viaje**,
no dosimetría personal ni reconstrucción continua de exposición. No se infieren
ventilación de un vehículo, refugio, tramo realmente usado o condiciones durante
lagunas. La fracción de tramos y cantidad de consultas no representan por sí
solas cobertura temporal. `capture_summary` informa rangos/cantidades por
variable y límite alcanzado; no fabrica un promedio ponderado.

La captura posterior a la elección no sustituye el contexto congelado de una
predicción prospectiva. Se preserva y exporta para el estudio de discrepancias
entre condiciones previstas y registradas. Usarla como otro predictor de
entrenamiento exige fijar el objetivo y contrato correspondientes; no hacerlo
silenciosamente en esta entrega.

## Revisión prospectiva y precisión

`comfort_pilot.pilot_review` reutiliza la validación y puntuación prospectivas.
Compara Passport estático/modelo básico/contextual solo cuando los tres son
elegibles para el mismo evento y modo; conserva exclusiones, diferencias
emparejadas y bloques diarios en `Europe/Berlin`. No supone independencia entre
viajes de la misma persona ni interpreta elecciones como respuestas de comodidad.

Informa log-score, RPS y error de respuesta codificada. Para cada distribución
ordinal calcula el intervalo predictivo central nominal de 90%, su masa real
discreta y si contiene la respuesta observada. Esa masa puede superar 90% por
la discreción de siete categorías. Esto es distinto del intervalo epistémico de
la expectativa; una única respuesta no revela la verdad de esa expectativa.
Los anchos de intervalos de expectativas quedan disponibles para análisis, sin
convertirlos en una regla automática de activación.

El protocolo conserva los modelos acumulativos de fase 4 en sombra y los
candidatos temporales offline. Los estudios previos mostraron subcobertura bajo
cambio/mala especificación. No se eligió una tasa temporal como validada ni se
ocultó ese resultado. No existe un mínimo de tres respuestas que garantice
validez. Precisión útil, calidad de explicación y sensibilidad de recomendación
deben evaluarse por separado; una recomendación estable no certifica la creencia.

## Sensibilidad de la red completa

Diseño congelado: `output/d-day-phase4-close/network-sensitivity/design.json`.
Ocho Passports sintéticos, 40 experiencias por modo, objetivos separados con
contextos meteorológicos compartidos, priors débil/moderado/fuerte, condiciones
templada/seca y fría/lluviosa/ventosa. Son 192 ajustes ordinales; todos los
diagnósticos de los ajustes utilizados pasaron, incluyendo reintentos cuando
procedía. Son datos sintéticos, no viajes o respuestas de participantes.

Para cada uno de los 48 casos se ejecutan 18 variantes: Passport original,
medias de expectativas y cambios de un solo modo/objetivo a cada extremo de
su intervalo marginal nominal de 90%. Se mantiene el solver de producción con
19 nodos, horizonte 40 y `dt=0.02`; los tiempos son internos del modelo.
Se conservan metas diarias y las otras 40 conexiones modo–necesidad. No hay F3.

| Prior | Casos | Cambio de ganador entre original/medias | Casos sensibles a extremos marginales |
|---|---:|---:|---:|
| Débil | 16 | 4 | 12 |
| Moderado | 16 | 4 | 11 |
| Fuerte | 16 | 4 | 9 |
| Total | 48 | 12 | 32 |

Se trata del máximo de activación terminal de acciones, no una elección del
usuario ni una evaluación de acierto. Hay nueve casos con medias próximas a
empate y seis con Passport original próximos a empate, usando el margen 0.01
ya existente del solver. Excluyendo cambios que involucren esos empates, quedan
siete cambios original/medias y 19 casos sensibles a extremos con margen mayor.

Un barrido de extremos marginales no es un conjunto creíble conjunto ni una
probabilidad posterior de ganar. No se estima covarianza comodidad–afecto.
Menos sensibilidad con el prior fuerte no prueba que sea mejor: puede resultar
de intervalos más estrechos aun cuando el prior sea incorrecto.

Se guardaron 864 trayectorias completas y topologías en NPZ; sus estados finales
coinciden con `results.json`. Se verificó que permanecen fijas las 40 conexiones
restantes. Pruebas adicionales comparan el solver por lotes con el solver de
producción, incluyendo indisponibilidad y límites de pesos. No se modifica HOTCO
en producción ni se alimenta automáticamente un estado terminal al siguiente.

## Verificación y artefactos

- 73 pruebas backend aprobadas.
- 43 pruebas JVM aprobadas; APK principal y de pruebas compilados.
- Seis pruebas en teléfono aprobadas: contrato común, persistencia, llamada
  Android al backend real sin escritura y concurrencia de SQLite.
- Dos pruebas visuales de controles no pudieron acceder a la pantalla porque
  el teléfono estaba bloqueado. No se declaran aprobadas ni se cambia la UI
  para hacerlas pasar. Requieren desbloquear y repetir `ComfortPilotDeviceTest`.
  **Seguimiento 11 de octubre:** ambas pasaron con el teléfono desbloqueado;
  también pasaron las tres pruebas de `JourneyContextDeviceTest` contra el
  backend reiniciado. Evidencia separada de la entrega original en
  `output/d-day-phase4-followup-2026-10-11/verification.json` y
  `context-device-tests.txt`. La app instalada sigue siendo 0.4.62 (73).
- Backend 8077: health, muestra real y validación HTTP de fixture con captura,
  todos HTTP 200. Proxy 8092 es GTFS realtime (`/status`), no proxy HTTP de
  los endpoints D-Day; permanece funcionando. USB reverse 8077 conservado.

`output/d-day-phase4-close/readiness.json` separa entrega, validación y pendientes.
`implementation_manifest.json` conserva hashes de fuentes y trayectorias.
`verification.json` identifica resultados de pruebas de esta entrega; no es una
certificación reutilizable si cambia el código. Las observaciones live y fixtures
sintéticos permanecen en archivos distintos.

## Reproducción

Desde `app-backend/services/dyconet`:

```powershell
python -m comfort_pilot.live_context_audit --output ../../../output/d-day-phase4-close/orion-contract-audit.json
python -m comfort_pilot.network_sensitivity --output ../../../output/d-day-phase4-close/network-sensitivity
python -m comfort_pilot.pilot_review exportacion-piloto.json --output revision-piloto.json
python -m comfort_pilot.closure_report --output ../../../output/d-day-phase4-close
```

## Qué falta para cerrar científicamente la fase 4

| Pendiente | Puede hacerse sin participantes | Condición de cierre |
|---|---|---|
| Vigencia y semántica del proveedor | Sí | Lecturas actualizadas y contrato de magnitudes/ventanas comparable |
| Recorrido técnico completo con datos actuales | Sí, mediante recorridos propios | Comprobar GPS, muestras, interrupciones y exportación durante un viaje completo |
| Comprensión del instrumento | No | Comprobar anclajes y atribución de comodidad/afecto con usuarios |
| Validación predictiva prospectiva | No | Experiencias confirmadas posteriores a predicciones congeladas, analizadas sin fuga temporal |
| Precisión útil y reglas de activación | Definición/simulaciones sí; validación empírica no | Preespecificar criterios por objetivo y comprobar su fiabilidad; no escoger después los resultados favorables |
| Adaptación temporal | La comparación offline sí | Resolver/someter a sensibilidad la subcobertura y comprobar beneficios prospectivos antes de elegir candidato |
| Comprobación visual | Sí; completada el 11 de octubre | Dos pruebas aprobadas con el dispositivo desbloqueado |

La entrega técnica queda preparada y probada con esos límites explícitos. No
marcar la fase como científicamente validada, ni activar fases 5–6 para aparentar
su cierre. La recogida ambiental no demuestra por sí sola exposición personal
o aprendizaje psicológico, y ninguna simulación sustituye evidencia empírica.

## Seguimiento del 11 de octubre de 2026

La nueva consulta pública de Orion a las 10:32 Europe/Berlin devolvió la fecha
original `2026-10-11 08:48:56` para Walter y, por referencia de la misma
QueryList, Winfred. La antigüedad ronda 103 minutos: ambas estaciones conservan
cero variables meteorológicas utilizables bajo la política del piloto.
Se archiva la consulta en
`output/d-day-phase4-followup-2026-10-11/orion-contract-audit.json`;
no se sobrescribe la auditoría de la entrega original ni se amplía la vigencia.

El autor aclaró posteriormente que permaneció en la cama trabajando, sin
desplazarse. La inspección de un snapshot SQLite estable encontró un registro
del 11 de octubre de 4.046 segundos, confirmado con Experience, cero puntos GPS,
un fix rechazado, distancia desconocida y sin pausa/reanudación ni muestras
ambientales. Su contrato es válido, pero es una prueba estacionaria: no usar
esa Experience como evidencia de comodidad/afecto de un viaje realizado.
Se conservó el original sin modificarlo. Los extractos JSON individuales y el
snapshot privado están en `.local-backups/d-day-stationary-2026-10-11/083510/`;
no equivalen a comprobar la exportación de backup desde la UI de la app.
La exclusión externa está en `evaluation-exclusions.json` dentro de esa carpeta.

El recorrido real, pausa/reanudación, captura durante un recorrido y exportación
de backup desde la app siguen pendientes. La conexión configurada por USB deja
de estar disponible al desconectar el cable; se requiere una conexión alternativa
al backend para obtener consultas ambientales exitosas durante un recorrido.
