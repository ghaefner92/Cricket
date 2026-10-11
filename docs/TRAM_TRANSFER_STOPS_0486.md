# Transbordo y paradas de recorrido — 0.4.86 / 97

Incidente reproducido en la búsqueda privada route-1dd9cf46: Tram1→9,
City Carré→Askanischer Platz para el segundo tramo. Su horario estaba presente,
pero la geometría de9 tenía APPROXIMATE_NO_SHAPES y Android la excluía. El conjunto
de vías OSM solo incluía miembros de relaciones de líneas. Tres anclas de9
quedaban a180–533m de la red disponible, aunque pertenecían al itinerario GTFS.

La extracción Overpass incluye ahora la unión de vías de las relaciones y todas
las vías tram/light_rail/subway de Magdeburg.23 relaciones/865 vías, osm_base
2026-10-11T17:19:51Z. Se conserva la copia previa en el diagnóstico privado.
El matcher existente sigue exigiendo red conectada, sentido, anclas en orden,
proximidad y ausencia de corredores divergentes. No se amplían tolerancias ni
se unen paradas con rectas. El trazado resultante es referencia cartográfica,
no forma de servicio certificada ni posición observada del vehículo.

Backend reiniciado. El endpoint de presentación tram-alignment confirma ambos
tramos del caso:178 y208 puntos respectivamente. Esto permite reparar la
visualización de búsquedas guardadas sin modificar su evidencia original.

Solicitud adicional del autor: estaciones intermedias atenuadas, finales y
transbordos reconocibles. Nuevo endpoint route-stops consulta la secuencia GTFS
del trip_id exacto y la recorta entre from/to en orden. Si el par es ambiguo,
ausente o invertido, no inventa paradas intermedias. Cache128 trips/ventana10min;
solo presentación, sin escrituras de elecciones/Passport. Android consulta
solo el itinerario seleccionado y cancela al cambiarlo. Se confirmó7/8 paradas
para los tramos1/9, con extremos compartidos deduplicados.

Plataformas OSM vinculadas explícitamente se conservan. Cuando falta asociación,
se muestra la referencia GTFS del itinerario; ese marcador no permite planificar
embarque como si fuera una plataforma de espera verificada. Las paradas del
itinerario se muestran a lo largo del recorrido independientemente del radio
de POIs cercanos. Solo intermedias tienen opacidad42%; transferencia y terminal
se destacan, terminal con sprite finish existente y etiqueta Final stop/Ausstieg.
El símbolo de conexión dibuja dos flechas opuestas en colores y trazos pixelados
del sistema existente; etiqueta de líneas1↔9 junto al nombre. No requiere imagen
generada ni fuente especial. Historia, horarios y geometría congelada intactos.

Validación:9 pruebas Python de alineación/secuencias y83 JVM aprobadas, build Debug.
La nueva prueba JVM conserva itinerario original, clasifica boarding/transfer/
intermediate/final, combina líneas del transbordo y no inventa intermedias sin
secuencia confirmada. Consultas HTTP del caso real aprobaron ambas geometrías
y ambas secuencias. APK install-r tras copia previa verificada. Presentación
final en teléfono pendiente de inspección; no se registró GPS, elección ni
Experience para validar.
