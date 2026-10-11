# Mejora de carga de rutas — 0.4.41

Actualización: la regresión de Play y el cálculo repetido se corrigen en [0.4.42](ROUTE_PLAY_RESPONSIVENESS_2026-10-09.md). Los resultados de este documento corresponden a 0.4.41.

Implementada a partir del [diagnóstico](ROUTE_LOADING_DIAGNOSTIC_2026-10-09.md). Instalada en Samsung RZCY70K63HV, versión 0.4.41 (52). Backend local 8077 reiniciado y saludable; acceso USB inverso restablecido.

## Cambios

- El backend negocia gzip para JSON de al menos 1 KB cuando el cliente lo acepta. Conserva exactamente los bytes del JSON descomprimido y añade `Vary: Accept-Encoding`. No comprime otra vez respuestas ya codificadas, streaming, rangos o respuestas con ETag. Android utiliza la descompresión automática de HttpURLConnection, comprobada en dispositivo.
- TripBanner consulta en segundo plano. SQLite prefiltra los registros con el token de estado pendiente y Kotlin verifica el estado real; deja de cargar y parsear todos los registros para encontrar una sola experiencia. No requiere JSON1, migración de base de datos ni cachés susceptibles de quedar obsoletas entre el servicio y la app.
- TripScreen lee el registro fuera del hilo de interfaz al abrirlo y al recibir cambios. Mantiene el último snapshot durante la actualización del mismo viaje, para que el mapa no se desmonte en cada actualización. Al abrir otro viaje muestra un indicador hasta recibir su propio registro.
- La decisión de presentar el seguimiento a pantalla completa consulta únicamente el pequeño puntero activo, en vez de cargar el registro completo durante la composición.
- La búsqueda guarda los registros grandes en Dispatchers.IO y prepara la recomendación en Dispatchers.Default. Publica el resultado después de persistirlo. El guardado de un fallo o cancelación se completa antes de propagar el error.
- El explorador reutiliza las tarjetas y la evaluación del mismo resultado en vez de reconstruirlas en cada recomposición o cambio de modo.

No se eliminan observaciones ni asociaciones por segmento. Details, contratos, HOTCO y las comprobaciones de robustez conservan su contenido y reglas.

## Mediciones y pruebas

| Comprobación | Resultado |
| --- | --- |
| Búsqueda HTTP con perfil sintético y clima, backend local | HTTP 200, 11 rutas y 11 entradas de auditoría |
| JSON de esa búsqueda | 4.701.606 bytes descomprimidos; 150.105 bytes transmitidos; reducción del 96,81 % |
| Tiempo de esa búsqueda | 11,226 s hasta cabeceras; 11,270 s total |
| Historial sintético en Samsung, cinco registros de unos 3 MB | Método anterior 396 ms; consulta de pendientes 163 ms |
| HTTP gzip en Android, servidor de prueba local | 3.520.041 bytes de JSON, 8.628 bytes comprimidos; evidencia Unicode recuperada exactamente |
| Tests JVM Android | 16 aprobados: tracking/contratos, tarjeta y anclaje del avatar |
| Tests Python de transporte | 4 aprobados: bytes exactos, negociación/calidades/HEAD, respuestas pequeñas y ya codificadas, errores HTTP |
| Instrumentación Samsung | 2 tests de rendimiento aprobados; 1 test de inicio, duplicado, segundo plano, pausa, reanudación, finalización y persistencia aprobado |
| Historial real antes/después | 9 registros; ninguno añadido, eliminado ni modificado; SHA-256 de cada registro idéntico; SQLite quick_check correcto |
| Compilación | testDebugUnitTest, assembleDebug y assembleDebugAndroidTest correctos |

El test de historial también verifica falsos positivos del token, cambio de pendiente a confirmado y actualizaciones mediante otra instancia de Store. Usa bases aisladas que se eliminan al terminar. El test funcional usa un registro de prueba y lo retira después; no simula puntos GPS.

Estas cifras son muestras individuales, no p50/p95 de búsquedas reales. La prueba de historial ejecuta el método anterior antes del nuevo y no separa caché fría/caliente. La evidencia repetitiva del test HTTP comprime más que la respuesta real; el 96,81 % corresponde al endpoint real con datos sintéticos.

## Límites y próximos pasos

La compresión reduce transferencia, no el tamaño descomprimido ni el trabajo científico. El cálculo completo sigue costando aproximadamente 11 s en esta muestra; no se atribuye la diferencia respecto a la muestra previa a una aceleración demostrada de HOTCO.

El mapa se beneficia de menos trabajo grande en el hilo de pantalla y de una transferencia menor antes de abrir las rutas. No se han medido primer mapa útil ni tiempos de tiles del CDN, ni cambiado el proveedor, la interpretación del renderizado completo o la instancia al pasar de comparación a seguimiento. No se afirma una reducción medida de carga del mapa.

El prefiltrado de pendientes todavía recorre texto dentro de SQLite, aunque evita trasladar y parsear registros ajenos en Kotlin. Con historiales mayores conviene un índice de metadatos mantenido transaccionalmente. El servicio GPS, la selección de ruta y otras pantallas aún tienen operaciones síncronas; sacarlas del hilo principal exige conservar el orden de acciones, transacciones y ciclo del servicio. Esa segunda etapa queda separada de esta mejora comprobada.

Resultados técnicos y copias privadas de SQLite: `.local-backups/route-performance-20261009/`. No contienen material destinado a publicarse y no se añaden al repositorio. La comprobación de whitespace de los archivos modificados pasa considerando correctamente los finales CRLF de Windows.
