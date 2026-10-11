# Transporte público: llegada y paradas cercanas — 0.4.90

Decisión del autor del 11 de octubre de 2026: primera alternativa de transporte
público por llegada más temprana al destino, contando acceso a pie, espera,
transbordos y último tramo a pie. Esta decisión sustituye la propuesta anterior
de priorizar la parada más cercana. La proximidad sigue visible, no gobierna
la primera alternativa. No cambia la comparación modal HOTCO ni el Passport.

## Diagnóstico de la búsqueda del teléfono

Inspección de solo lectura de la búsqueda `route-854aee8c-88dc-4a94-b86f-4f33fa22f137`,
registrada a las 20:41:08 Europe/Berlin. Horarios conservados del proveedor:

| Conexión | Primera parada | Acceso a pie | Subida | Llegada |
| --- | --- | --- | --- | --- |
| Bus 73 | Olvenstedter Platz | 1.168,55 m / 949 s | 21:00 | 21:19 |
| Tram 6 → Bus 48 | Arndtstraße | 582,64 m / 457 s | 21:04 | 21:29 |
| Tram 6 → Bus 48, siguiente salida | Arndtstraße | 582,64 m / 457 s | 21:24 | 21:49 |
| Tram 1 → 9 | Spielhagenstraße | 338,35 m / 287 s | 21:28 | 21:57 |

La conexión 1 → 9 estaba presente y ambos tramos tenían alineación OSM.
La presentación anterior ordenaba por primera subida, no por llegada. En este
caso concreto ambos criterios ponen primero al 73, pero no son equivalentes.
Estos son horarios de la búsqueda guardada, no una comprobación de operación
real ni una búsqueda actualizada al momento de leer este documento.

## Implementación

- Alternativas disponibles y no vencidas primero; llegada al destino ascendente.
  Desempates por acceso a pie al primer vehículo, subida y rango original.
  Datos ausentes no se convierten en cero. Rutas vencidas permanecen en el
  detalle, fuera de las tres sugerencias utilizables.
- Hasta tres conexiones distintas en una barra compacta del mapa de resultados.
  Cada una muestra líneas, llegada y minutos de caminata inicial. La primera
  salida de cada patrón representa ese patrón; las demás siguen en el detalle.
  Cambiar de opción cambia la vista, sin registrar una elección de viaje.
- Antes de buscar rutas, tres plataformas de espera cercanas al origen se
  destacan individualmente con nombre y distancia explícita en línea recta.
  Son un suplemento al radio configurable de 200 m, limitado a 2 km y al
  interruptor de paradas. No se muestran posiciones de vehículos sobre raíles
  como plataformas. Pulsar una parada abre información, no reemplaza el destino.
- La selección de transporte público y las pantallas compartidas utilizan el
  mismo orden. Inglés y alemán; se reutilizan componentes y estilo existentes.

La primera opción es la llegada más temprana **entre las alternativas devueltas
y utilizables**; no demuestra optimalidad entre todos los itinerarios posibles.
La proximidad cartográfica de una plataforma no garantiza conexión al destino.
No se modifican horarios, evidencia congelada, rangos del proveedor, simulaciones
HOTCO, predicciones ni mediciones personales.

## Verificación

89 pruebas JVM aprobadas, incluyendo llegada frente a primera subida, exclusión
de vencidas, deduplicación de patrones y plataformas/toggle. APK compilado.
No se generan elecciones, feedback, GPS ni experiencias para probar el cambio.
La comprobación visual interactiva de la barra y las plataformas queda al usuario.
