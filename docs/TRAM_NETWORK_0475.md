# Trazados de tranvía 0.4.75 / 86

Solicitud del autor: resolver los trazados ausentes de transporte público.
La captura del teléfono de 14:00 UTC contiene línea 1 desde Spielhagenstraße
hasta ZOB/Adelheidring, con cinco anclas distintas. OSM tiene 1N/1S y solo una
relación 1S de sentido incompatible. Las posiciones de parada de esa relación
difieren 74/27 m del GTFS. El riel más cercano tampoco basta para determinar
un recorrido dirigido viable.

Se conserva primero la búsqueda por relación de línea y sentido. Cuando no
coincide, se busca una referencia en la red física de vías OSM ya archivada:
proyecciones candidatas hasta 25 m, conectividad exclusivamente por nodos,
sentidos OSM, extremos y todas las anclas GTFS en orden, longitud limitada.
Se priorizan referencias conectadas cortas frente a vueltas por terminales;
entre candidatas a menos de 50 m de la menor longitud se eligen extremos más
próximos. Corredores interiores divergentes a más de 25 m se rechazan.
No conectar rieles por cercanía ni unir cortes con rectas.

La línea 1 del caso devuelve 86 puntos y 1547.20 m cartográficos, extremos a
2.00/3.16 m; máxima separación de las cinco anclas 3.16 m. Tiempo y distancia
GTFS originales (1460.14 m) permanecen intactos. Línea 6 conserva su referencia
por relación (50 puntos / 821.17 m).

La alternativa se identifica como OSM_REFERENCE_NETWORK, con política de camino
y line_relation_matched=false. No es una shape de servicio certificada: muestra
«Reference tracks · service path unverified» y sigue discontinua. No demuestra
el trayecto efectivo bajo obras ni movimiento observado. Los registros antiguos
reciben solo overlay en memoria; nuevas rutas conservan procedencia explícita.
No actualizar Passport/HOTCO ni reinterpretar contexto de selección como exposición.

Verificación: 7 pruebas de trazado, 9 de paradas, 68 JVM y assembleDebug aprobados.
HTTP aplicado a la captura devuelve referencias de líneas 1 y 6. Pruebas nuevas
exigen escoger vía conectada frente a un riel cercano sin salida y rechazar dos
corredores distintos igualmente compatibles. Sin iniciar viajes ni respuestas.

APK instalada en el teléfono y versión 0.4.75 / 86 confirmada. La app estaba
sin seguimiento y mostrando alternativas antes de instalar. El autor navega
concurrentemente y salió al launcher durante la inspección; se detuvo la
navegación del agente. Inspección visual del nuevo tramo en el teléfono pendiente;
geometría del caso, endpoint HTTP y presentación/procedencia Android comprobados
con los datos archivados y pruebas JVM, sin crear una experiencia.
