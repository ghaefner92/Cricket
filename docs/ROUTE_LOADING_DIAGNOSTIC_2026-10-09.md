# Diagnóstico de carga de rutas y mapa — 9 de octubre de 2026

Se revisó Companion 0.4.40, el backend local y el Samsung conectado. No se modificó la implementación, no se reiniciaron servicios ni se cambiaron ajustes o datos de la app. Las consultas de prueba usaron un perfil sintético y puntos públicos de Magdeburgo; no se guardaron como búsquedas en el celular.

La lentitud tiene al menos dos causas con evidencia: cálculo previo a mostrar las rutas y trabajo de lectura/JSON en el hilo de interfaz. El mapa añade una carga independiente de recursos externos y un indicador que espera a un renderizado completo. Aún no hay una medición separada del tiempo de primera imagen del mapa en el celular.

## Mediciones

| Prueba | Resultado | Alcance |
| --- | --- | --- |
| Salud del backend | 0,055 s | Una consulta desde el PC a localhost |
| Clima aislado | 0,121 s, 19 KB | Una consulta desde el PC; no explica por sí sola los segundos de espera |
| Búsqueda completa, sin clima | 11,224 s, 1,41 MB, 11 alternativas | Perfil sintético; respuesta completa HTTP 200 |
| Búsqueda completa, con clima | 12,097 s, 4,71 MB, 11 alternativas | Perfil sintético; respuesta completa HTTP 200 |
| Evaluación contextual sin red | 11,711 s; 14 simulaciones, 10,220 s dentro de `simulate` | Reproducción de candidatos en otro proceso del PC; no es un desglose de la petición HTTP anterior |
| Historial real del celular | 9 registros, 32,20 MB de texto; máximo 5,43 MB por registro | Copia de diagnóstico de SQLite, abierta solo para lectura; comprobación de integridad correcta |
| Lectura por bloques y parseo de ese historial | 128 consultas de bloques, 0,912 s | Reproducción del algoritmo en el PC; NO es una medida de duración en Android |
| Renderizado acumulado de la app | 26.452 frames; 809 fuera de plazo (3,06 %); p99 26 ms; GPU p99 9 ms | Contadores de Android de la sesión existente, sin reiniciarlos |
| Bloqueos de interfaz | Log de Choreographer: 443 frames omitidos | Evidencia de pausas perceptibles; no atribuye por sí sola la pausa a una función concreta |

Hay una muestra HTTP por configuración. Las alternativas de transporte público dependen de la hora; no debe interpretarse la diferencia de 0,87 s como una medición causal exacta del coste del clima. No se ha medido aún una distribución p50/p95 de búsquedas completas ni una sesión aislada de renderizado.

## 1. Historial y tarjeta: principal problema local

`CricketModel.run()` ejecuta el flujo en `viewModelScope.launch`, mientras que `Api.call()` mueve solamente la petición y su parseo a `Dispatchers.IO`. Al volver, guardar el resultado y calcular la recomendación siguen en el hilo principal.

`TripBanner()` llama a `store.history()` cuando no hay viaje activo. Ese método lee y convierte a JSON todos los registros antes de buscar una experiencia pendiente. En este celular son 32 MB. Puede bloquear la interfaz al entrar a rutas o al actualizar la app. No hace falta cargar todo ese contenido para dibujar un aviso.

Durante un viaje, `TripScreen()`, `TripRepository.active()` y el cálculo del modo inmersivo también pueden leer el registro completo. `TripRepository.change()` valida y reescribe el registro entero al pausar, terminar o actualizar el GPS. Esto añade trabajo local al workflow, aunque no se esté descargando un mapa nuevo.

Las lecturas del perfil en la tarjeta ya se cachearon en un cambio anterior, y `NativeJourneyMap` ya conserva la geometría por búsqueda. Esas mejoras no eliminan las lecturas anteriores del historial. `RouteExplorerScreen` todavía vuelve a construir tarjetas/recomendación con sus actualizaciones, y las proyecciones y paths de las rutas se recalculan al dibujar el overlay.

**Mejora prioritaria:** leer/validar/guardar fuera del hilo principal; mantener un pequeño índice de viaje activo/experiencias pendientes; usar snapshots en memoria que se invaliden al cambiar el registro. Conservar las lecturas por bloques para evitar el límite de CursorWindow. No borrar historial ni sustituirlo por valores inventados.

## 2. Respuestas demasiado grandes para una tarjeta

En la última búsqueda real, la sección contextual ocupa 4,68 MB y la geometría/candidatos y routing juntos aproximadamente 123 KB. Dentro de sus candidatos, `route_context` suma 3,98 MB. Se contabilizan 135 segmentos y 1.215 filas de observaciones a nivel de ruta con solo 9 combinaciones diferentes de entidad/variable/timestamp/valor/unidad. La repetición conserva asociaciones y metadatos por segmento; no debe eliminarse sin preservar esas relaciones.

La respuesta sintética con clima pesa 4.706.691 bytes sin compresión HTTP. Comprimiendo esos mismos bytes con gzip en el diagnóstico queda en 140.077 bytes, aproximadamente un 97 % menos. El servidor no devolvió `Content-Encoding`, aun solicitando gzip.

**Mejoras propuestas:** compresión HTTP y una representación compacta con observaciones compartidas por referencia. Una respuesta inicial para mapa/tarjeta podría incluir rutas, métricas y evaluación resumida; la evidencia completa debe seguir disponible y conservarse correctamente para Details, historial, explicación y validación. Gzip reduce transferencia, pero por sí solo no reduce el JSON descomprimido que Android debe parsear o guardar.

## 3. La app espera al cálculo completo antes de crear el mapa

La secuencia actual es: routing → OTP → geometría complementaria → contexto/HOTCO → serialización → guardado local → pantalla de rutas → creación del mapa. `model.result` solo se publica después de recibir y guardar toda la respuesta.

La reproducción sin red confirma un coste importante de simulación: 14 llamadas tardaron 10,22 s dentro de una evaluación de 11,71 s. Los tiempos incluyen las comprobaciones de robustez. No se deben recortar esas comprobaciones o el integrador científico para aparentar una mejora de UX.

**Mejora propuesta:** preparar el mapa mientras se busca y, si se acuerda un contrato en dos fases, mostrar geometría/métricas al estar listas y completar después la recomendación. La evaluación pendiente debe aparecer explícitamente como pendiente. Para reducir CPU, investigar reutilización estrictamente equivalente de cálculos, con claves que incluyan perfil, disponibilidad, parámetros y contexto efectivo.

Ya existen dos optimizaciones: GraphHopper recupera modos faltantes en paralelo y el proveedor meteorológico reutiliza su QueryList dentro de una solicitud. No hay evidencia para recomendar volver a consultar el clima por cada segmento ni para asumir que todos los proveedores están totalmente serializados.

## 4. Mapa: carga y percepción de espera

Los estilos están empaquetados localmente (el estilo Light pesa 42,8 KB y tiene 109 capas); no se descarga el JSON del estilo desde internet. Los tiles, sprites y fuentes sí se obtienen de OpenFreeMap. El mapa no depende del endpoint de backend 8077 para esos recursos.

`VectorMapSurface` solo quita el indicador cuando `OnDidFinishRenderingMapListener` informa `fully = true`. Puede haber contenido visible mientras el indicador sigue mostrando «Loading map». `NativeMap` aplica un timeout de 25 s. Hay que distinguir estilo listo, primera imagen utilizable y recursos completos.

Se conserva una instancia de `MapView` dentro de cada pantalla. Al pasar de comparación a seguimiento, cambia el árbol de pantalla y se destruye la instancia anterior; la siguiente debe inicializarse otra vez. Esto no demuestra que se pierda toda la caché de tiles, pero sí identifica una recreación evitable.

**Mejoras propuestas:** reutilizar la instancia/cámara al cambiar de fase del viaje; medir los tres hitos de carga; hacer que el indicador represente contenido útil visible y que el resto de recursos se complete en segundo plano. Optimizar overlays solo después de medir sus paths/proyecciones.

La consulta externa de diagnóstico a OpenFreeMap quedó primero bloqueada por el sandbox y después devolvió HTTP 403 fuera de él. Por ello no hay un tiempo fiable del CDN desde esta herramienta. Esto NO demuestra que OpenFreeMap falle en el celular ni justifica cambiar de proveedor todavía.

## Orden recomendado antes de una implementación

1. Eliminar las lecturas completas de historial y el guardado/validación en el hilo de interfaz. Medir apertura de rutas, pausa y detener con los mismos datos.
2. Activar transferencia comprimida y diseñar el payload compacto sin perder evidencia ni alterar Details.
3. Reutilizar el mapa y distinguir primera imagen del renderizado completo.
4. Separar la presentación de rutas del cálculo completo de recomendación, si se acepta ese nuevo contrato. Perfilar y verificar equivalencia antes de optimizar HOTCO.

Para validar mejoras: comparar primera tarjeta visible, primer mapa útil, respuesta a pausa/detener, tamaño transferido y p50/p95 de búsquedas con la misma ruta/perfil, indicando caché caliente/fría. No puedo prometer una reducción concreta del tiempo total con las muestras actuales.

Los scripts y resultados técnicos están en `.local-backups/route-performance-20261009/`; la copia de SQLite es privada y no forma parte de este informe. No se incluyen direcciones, perfil personal ni puntos GPS en el documento.
