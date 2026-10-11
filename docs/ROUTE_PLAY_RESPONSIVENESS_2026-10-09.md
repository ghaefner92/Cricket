# Rutas y Play: corrección 0.4.42

La mejora 0.4.41 dejó operaciones grandes en el inicio del viaje y una transición capaz de mostrar las opciones antes de crear el registro. Esta versión corrige ese recorrido y reduce el cálculo repetido del backend. APK 0.4.42 (53) instalado en Samsung RZCY70K63HV; backend local 8077 actualizado.

## Inicio y controles

- Play guarda la elección en Dispatchers.IO y devuelve directamente el índice persistido. Evita la segunda lectura completa del registro y la navegación provisional a preview.
- La creación del viaje, validación, guardado, comandos y GPS pasan por una única cola HandlerThread del servicio. El inicio en primer plano se anuncia inmediatamente, sin esperar al registro grande.
- TripScreen distingue la carga de datos del inicio del viaje. Mientras el servicio crea el registro muestra «Starting journey…»; no presenta «Your real journey» ni opciones manuales durante ese intervalo. Un efecto cancelado ya no marca erróneamente la lectura como terminada. Los fallos del servicio se comunican a la app y existe un límite de espera de inicio.
- Los cambios se publican después del commit de SQLite. Pausa/reanudación conservan el orden de comandos y los controles de notificación reflejan el estado guardado.
- La base utiliza WAL. Las preferencias pequeñas se leen en una sola consulta, sin adquirir una transacción de escritura que obligue a la interfaz a esperar los guardados grandes.
- changeActive valida y guarda a partir de una sola lectura dentro de la transacción. Estado y distancia de notificación se actualizan junto al puntero activo; renderizar la notificación ya no requiere parsear de nuevo toda la respuesta.

## Toques y mapa

La tarjeta recuperaba el perfil desde el registro completo al cambiar `card.id`, aunque no había cambiado la búsqueda. Ahora mantiene el perfil original de esa búsqueda en memoria, identificado por search_id. Si hay que recuperarlo de disco, la lectura ocurre en segundo plano; nunca sustituye un perfil histórico por el actual.

El mapa reutiliza sus paths proyectados mientras la cámara y las rutas no cambian. Animar el avatar y actualizar el reloj ya no vuelve a proyectar toda la geometría. La selección por toque usa los puntos ya proyectados. Preferencias y apariencia se recuperan al cambiar la revisión, en vez de consultarse durante cada frame de desplazamiento o animación.

El anclaje del avatar a la ruta y el estado pausado conservan las reglas y tests anteriores. No se cambia el proveedor de mapa ni se elimina la evidencia de Details.

## Cálculo del backend

Dentro de una sola solicitud, el participante y los parámetros del motor permanecen fijos. Las simulaciones de candidatos se reutilizan únicamente cuando los bytes del vector de perturbación alineado son idénticos. La trayectoria, topología, disponibilidad y estadísticas se copian para cada candidato; sus observaciones, warnings y metadatos siguen siendo propios.

No se comparte la caché entre búsquedas, perfiles o consultas de clima. No se recortan pasos de integración ni pruebas de robustez. La simulación baseline se mantiene separada.

## Resultados medidos

| Prueba | Antes / referencia | Versión corregida |
| --- | --- | --- |
| Replay de 11 candidatos sin clima, mismas entradas congeladas | 14 llamadas simulate; 10,249 s | 4 llamadas; 3,631 s; respuesta completa idéntica |
| Replay de 11 candidatos con clima congelado | 14 llamadas; 10,015 s | 4 llamadas; 3,788 s; respuesta completa idéntica |
| HTTP con perfil sintético, clima y routing reales | Muestra previa 11,270 s | Muestra actual 4,833 s, HTTP 200, 11 rutas y 11 auditorías |
| Transferencia HTTP actual | 4.700.476 bytes descomprimidos | 149.776 bytes por gzip; reducción 96,81 % |
| Play con registro sintético de 5 MB, mapa ya mostrado | — | Dispatch 1 ms; demora máxima de callbacks de pantalla 4 ms; inicio detectado en 2.327 ms |
| Pantalla intermedia incorrecta | Reportada en 0.4.41 | No observada en 2.491 nodos de accesibilidad muestreados durante el inicio |

Los tiempos son muestras, no garantías ni p50/p95. El replay usa exactamente los mismos contextos en ambas versiones; el HTTP real se ejecutó en otro momento y puede variar con routing, clima y red. La medición de Play espera a mostrar el mapa antes de pulsar, incluye sondeos de la base y accesibilidad, y distingue respuesta del toque de persistencia completa. No equivale a medir todos los gestos ni una carga fría de tiles.

## Validación

- 16 tests JVM de tracking/contratos, tarjeta y anclaje del avatar.
- 6 tests Python de transporte y equivalencia completa de simulaciones, incluidos vectores distintos y metadatos propios de cada ruta.
- Instrumentación en Samsung: Play con registro grande, consulta de pendientes y gzip en Android; además ciclo de servicio con segundo plano, pausa, reanudación por notificación, finalización y confirmación.
- El test de Play calcula hashes SHA-256 del historial por bloques antes/después y exige que todos los registros reales sigan idénticos. Solo añade un registro sintético y lo elimina al terminar. La comprobación utiliza SQLite directamente y contempla WAL, sin copiar únicamente el archivo principal abierto.
- Compilación testDebugUnitTest, assembleDebug y assembleDebugAndroidTest correcta. Whitespace comprobado con finales CRLF.

Resultados privados de replay y HTTP: `.local-backups/route-performance-20261009/simulation-reuse-replay.json` e `improvement-backend.json`. No se publican perfiles personales, direcciones ni GPS.
