# Auditoría de líneas y colores por vehículo — Android0.4.89/100

Solicitud: generar diversas simulaciones para todas las líneas de bus/tram de
Magdeburg y ajustar las geometrías a las vías; distinguir buses con un color
cercano al tranvía. Alcance verificable: inventario del feed local MVB utilizado
por OTP,37 líneas (12tram/25bus),277 variantes distintas de secuencias de paradas.
No certifica cobertura de otros operadores, ni que todas las variantes operen
en la fecha/hora actual. Recuento inicial299 corregido a277.

Laboratorio técnico offline `validation/audit_magdeburg_all_lines.py`, con GTFS
original y hash del archivo. Por cada patrón se prueban hasta cuatro ventanas
cortas de hasta tres paradas: inicio, tercio, dos tercios y final, deduplicando
ventanas coincidentes. No son simulaciones HOTCO ni trayectorias observadas;
no se guardan en la base de datos del usuario. No se probó exhaustivamente cada
par de origen/destino ni cada recorrido completo. La primera ejecución con
ventanas largas resultó excesivamente lenta y se detuvo; no se cuenta como
validación completada ni como mejora de rendimiento cuantificada.

Resultados antes/después en output/magdeburg-all-lines-audit-before.json y
output/magdeburg-all-lines-audit.json:1071 casos,562tram/509bus. Primera pasada
561tranvías alineados y1sin resolver; segunda,562/562. Dos correcciones:

-13 y N1 carecen de relación OSM equivalente al nombre del horario. La extracción
  incorpora los nombres tram confirmados por routes.txt del GTFS local, con hash.
  Esos nombres permiten buscar referencia física conectada en la red completa.
  No se aceptan nombres de buses ni nombres desconocidos como líneas de tranvía.
  Se conserva source OSM_REFERENCE_NETWORK, line_relation_matched=false y
  service_shape_verified=false. No inventar una relación OSM coincidente.
-El caso N1 Pfeiffersche Stiftungen→Cracau→Pfeiffersche Stiftungen era rechazado
  porque el destino quedaba cerca del origen. El fallback de vueltas forzadas
  respeta las anclas en orden y concatena tramos solo si comparten exactamente
  la misma posición proyectada sobre la vía. No interpola conexiones entre
  carriles. Si una pieza falta/es ambigua o la unión difiere, se rechaza.
  Resultado1593.35m, máximo desplazamiento de ancla3.97m. No se amplían25m ni
  se cambia el criterio de ambigüedad de corredores. Se reutilizan controles
  de dirección y conectividad existentes; metadatos señalan piece_count/política.

`validation/audit_magdeburg_schedule_cases.py` valida horarios de las1071
ventanas contra el GTFS original, incluidos tiempos posteriores a medianoche;
cero ventanas sin correspondencia o con duración negativa. Cuando un par aparece
más de una vez conserva un rango, sin inventar cuál ocurrió.509casosbus contienen
anclas de paradas, no formas verificadas: el feed no contiene shapes.txt.
No se presenta una polilínea de paradas como trazado vial exacto de bus.

Comprobación HTTP adicional:37/37 secuencias de viaje confirmadas por OTP/backend,
sin vacíos (output/magdeburg-all-lines-live.json). El caso N1 también se comprobó
mediante el endpoint tram-alignment de producción, tras reiniciar solo backend.
Las correcciones de presentación pueden aplicarse a búsquedas guardadas sin
reescribir sus geometrías/contexto/horarios congelados.

Android conserva source_mode por segmento: TRAM sigue Gold(#FFDA83); BUS y
TROLLEYBUS usan albaricoque(#FFA96B). Un itinerario mixto aplica cada tono a su
tramo, sin depender del modo general pt. La animación y atenuación existentes
utilizan el mismo color por tramo. Buses sin shape conservan trazo aproximado
y aviso EN/DE de conexiones de paradas. No cambian colores de metas ni pesos.

Validación:85 JVM y11 pruebas Python aprobadas, compilación Debug. Nuevas pruebas
comprueban vehículo/color distinto dentro de un itinerario mixto sin modificar
el original, elegibilidad de líneas GTFS especiales y retorno por terminal sin
atajo. Se mantienen controles contra sentido inverso, vías incompletas y
corredores ambiguos. APK install-r con copia203509 verificada35búsquedas,
Passportrev9. Colores/presentación final pendientes de inspección en teléfono.
No GPS/Experience/elecciones de prueba, no aprendizaje ni activación Passport.
