# Fase 1: contrato de viaje, tracking y experiencia

Versión Android 0.4.0. Implementación en la rama `android-compose`.

## Flujo en el teléfono

1. Buscar alternativas y elegir una ruta.
2. En la pantalla de elección, pulsar **Start trip / confirm experience**.
3. Elegir **Start journey with GPS** o **Confirm without GPS**.
4. Durante la grabación, pausar, reanudar o finalizar desde la app o notificación.
5. Al finalizar, confirmar realización y, opcionalmente, valorar aspectos concretos.
6. Revisar el registro desde **Menu → History → Actual journeys and experiences**.

Se admite un viaje por elección guardada y un solo viaje activo en el teléfono.
Una nueva elección permite un nuevo viaje. Una selección no confirma realización.
Cancelar conserva el registro como cancelado; no crea una experiencia confirmada.
El feedback pendiente puede completarse después desde el historial.

## Contratos

La definición común es `contracts/journey-v1.schema.json`. Se incluye como asset
Android y se valida con JSON Schema en Python. Kotlin interpreta los mismos
keywords empleados por ese esquema y añade las mismas reglas de integridad.

| Elemento | Contrato / contenido |
| --- | --- |
| Viaje | `cricket-journey-v1`; ID, búsqueda, índice y evento de elección |
| Identidad | Modo, rank, route ID, proveedor e itinerario OTP de la elección |
| Plan | Ruta original, alternativas, recomendación mostrada y contexto guardado |
| Pasaporte | Referencia original con revisión y fingerprint |
| Tracking | Puntos retenidos, precisión, horas, reloj monotónico, segmentos, eventos y métricas |
| Experiencia | `cricket-experience-v1`; realización, modos confirmados, cambios y valoraciones |
| Validación backend | `cricket-journey-validation-v1`; identidad, digest y fecha |

Estados: `recording → paused → recording → awaiting_confirmation → confirmed`.
Desde un estado activo se puede pasar a `cancelled`. Los viajes manuales empiezan
en `awaiting_confirmation`; no inventan inicio, duración ni distancia medida.
Confirmación y valoraciones son explícitas. Las valoraciones ausentes permanecen
ausentes; no se convierten en respuestas neutrales.

La experiencia tiene un ID estable vinculado al viaje. No se permite confirmarla
dos veces. Los registros confirmados se conservan; una corrección versionada de
experiencias queda para una ampliación posterior.

## Tracking y calidad

Se utiliza `LocationManager` mediante un servicio foreground de tipo `location`,
iniciado por el usuario y con notificación. Se solicitan permisos de ubicación y,
desde Android 13, notificaciones. No se pide ubicación permanente en background.
No se inicia automáticamente al arrancar el teléfono.

- Solicitud de posiciones cada 10 segundos / 5 metros; Android puede entregar
  muestras con otra frecuencia según proveedor, batería y señal.
- Se rechazan posiciones simuladas, precisión peor de 50 m, datos antiguos,
  coordenadas inválidas, tiempos repetidos y saltos superiores a 70 m/s.
- Se omiten pequeños movimientos por debajo de un umbral de precisión para
  reducir la acumulación de ruido estando quieto.
- Pausas, interrupciones y más de 120 segundos entre posiciones retenidas
  abren otro segmento. No se suma distancia entre segmentos.
- La distancia es una aproximación de los segmentos observados, puede subestimar
  el recorrido y permanece desconocida hasta disponer de suficientes puntos.
- El tiempo transcurrido incluye pausas. Se usa reloj monotónico; tras un reinicio
  se utiliza la diferencia temporal registrada como alternativa.
- No se infiere automáticamente vehículo, comodidad, llegada o realización.
- Límites: 20.000 puntos y 2.000 eventos por viaje. Alcanzarlos interrumpe la
  grabación con un aviso, conservando los datos anteriores.

La grabación permanece activa al pasar la app a segundo plano. Si Android mata
el proceso o el teléfono reinicia, los puntos guardados sobreviven; al volver se
marca la interrupción y el usuario reanuda explícitamente. No se promete recoger
posiciones durante un apagado, falta de permiso o pérdida de señal.

## Persistencia y datos

Los viajes se guardan en `journeys` dentro de cada búsqueda SQLite. Las escrituras
son transaccionales y leen el registro actual para no sobrescribir narraciones,
elecciones ni puntos que otro componente haya guardado.

Los datos se guardan localmente sin depender del backend. Las copias
`cricket-backup-v1` y la exportación individual incluyen viajes y experiencias.
La importación valida todas las experiencias antes de escribir y rechaza IDs
en conflicto. Se debe finalizar la grabación antes de importar o borrar datos.
Las copias con viajes aún activos se rechazan al importar: una copia no puede
activar un servicio ni declarar que está grabando realmente.

En **Data and validation → Validate with backend** hay una confirmación explícita
antes de enviar el viaje con sus posiciones y la búsqueda al backend configurado.
El endpoint valida y responde; no persiste trayectorias ni modifica el pasaporte.
La recepción local se vincula al contenido validado y deja de mostrarse vigente
si dicho contenido cambia. La validación verifica forma e integridad entre
registros; no certifica que los datos enviados por un dispositivo sean auténticos.

Endpoints:

- `GET /api/dyconet/journeys/contract`
- `POST /api/dyconet/journeys/validate`, body `{record, journey}`; 200 / 422 / 413.

El contexto meteorológico conservado corresponde a la consulta de búsqueda:
se guarda su hora y el snapshot original, incluidos timestamps de sensores
cuando estaban disponibles. Esta fase no consulta Orion durante el tracking.
Refrescar condiciones cerca de la salida y durante el trayecto corresponde a
la construcción de características contextuales de la fase 2.

## Validación realizada

- 38 casos compartidos Kotlin/Python: 7 válidos y 31 inválidos.
- 7 pruebas Kotlin: contratos, ciclo de viaje, pausa y segmentos, rechazo GPS,
  confirmación manual, recuperación serializada y cancelación.
- 4 pruebas Python: casos, HTTP, digest idempotente sin mutación y límites.
- 38 peticiones al backend real: resultados esperados en todos los casos.
- 2 pruebas de instrumentación en Samsung SM-A566B: servicio real en segundo
  plano, pausa/reanudación/finalización mediante notificación, persistencia y
  confirmación única; copia de seguridad e importación inválida transaccional.
- Revisión de UI: apertura de historial, elección guardada y controles de inicio
  GPS / confirmación manual, sin iniciar un viaje real.
- Comparación de snapshots privados antes/después: compañero y pasaporte
  intactos, las dos búsquedas que había al comenzar conservadas y todos los
  registros temporales eliminados. No quedó tracking de prueba activo.
- Se recibió una posición GPS real en una ejecución. En otras ejecuciones en
  interior no hubo puntos suficientemente precisos: la distancia quedó desconocida.

Se utilizaron registros temporales de prueba y se eliminaron después. La primera
prueba de retorno de ActivityScenario a foreground falló; se adaptó la prueba para
ejercer las acciones reales de la notificación mientras la actividad sigue parada.
No se validó todavía un recorrido físico completo ni el consumo de batería de un
viaje prolongado. Esas mediciones requieren un viaje real y no se sustituyen por
las pruebas de lógica con posiciones sintéticas.

Comandos reproducibles, desde la raíz y desde `experimental-frontend`, respectivamente:

```powershell
python validation/generate_phase1_fixtures.py
python validation/phase1_test.py
```

```powershell
npm run android:apk -- testDebugUnitTest assembleDebugAndroidTest assembleDebug
adb -s RZCY70K63HV install -r ../android-native/app/build/outputs/apk/debug/app-debug.apk
adb -s RZCY70K63HV install -r ../android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s RZCY70K63HV shell am instrument -w -e class de.ovgu.imiq.cricket.TrackingDeviceTest de.ovgu.imiq.cricket.test/androidx.test.runner.AndroidJUnitRunner
```

## Modelo

`learning_applied` es siempre `false`. Esta fase no aplica aprendizaje bayesiano,
no elimina F3 del solver y no actualiza metas, valencias ni creencias. Crea la
evidencia versionada para implementar esas capas posteriormente. El LLM permanece
independiente y no interviene en el tracking ni en las confirmaciones.

Referencias Android: [servicios de ubicación](https://developer.android.com/develop/background-work/services/fgs/service-types),
[restricciones de inicio](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start),
[permiso de notificaciones](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
