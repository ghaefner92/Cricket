# Aparcamientos y paradas de Magdeburg — 0.4.71

El autor pidió símbolos P en un radio geográfico de 2 km de origen y destino,
propuesta automática del aparcamiento público más cercano con posibilidad de
cambiarlo, y paradas con nombres al seleccionar transporte público. El lugar
seleccionado permanece como origen/destino personal; no se afirma que el auto
esté dentro de una estación ni que su ubicación real sea conocida.

## Datos y presentación

`contracts/magdeburg-mobility.json` contiene una instantánea de OpenStreetMap
para el área administrativa de Magdeburg, descargada con
`validation/fetch_magdeburg_mobility.py`. Incluye 4574 elementos de aparcamiento
y entradas y 1125 plataformas/paradas. Estos son elementos cartográficos, no
un inventario certificado de instalaciones distintas ni de plazas libres.
Cada elemento conserva identificador OSM, etiquetas, fecha de datos y si su
posición es un nodo o el centro de una superficie. Fuente OSM/Overpass,
licencia ODbL, atribución visible. Se puede regenerar la instantánea; no es una
consulta de ocupación en tiempo real.

Android filtra por modalidad y distancia <=2000 m a cualquiera de los extremos.
Auto muestra P azules de acceso público o no especificado y grises para acceso
restringido. Al alejar el mapa se agrupan los puntos, con recuento; tocar un
grupo acerca el mapa y permite consultar sus elementos. A zoom >=17.5 se
muestran individualmente. Los aparcamientos usados por la ruta se conservan
individuales y resaltados. PT muestra H y nombres con separación para evitar
superposición; tocar una parada abre su nombre. La existencia cartográfica de
una parada no implica que el itinerario seleccionado pare allí.

## Supuesto y recálculo

La política `nearest-public-osm-magdeburg-v1` se solicita desde Android cuando
hay aparcamientos explícitamente públicos dentro de 2 km de ambos extremos.
El backend propone el más cercano por distancia geográfica, con desempate por
ID; esto no minimiza la caminata ni prueba cuál auto pertenece a la persona.
Acceso público exige `yes` o `permissive`, atendiendo restricciones específicas
de motorcar/motor_vehicle antes de access. No se proponen privados, permisos,
clientes o restricciones. Un aparcamiento sin acceso indicado puede elegirse
manualmente con esa incertidumbre visible; los restringidos solo se consultan.

Tocar P permite usarlo en origen o destino y consultar acceso/tarifa conocida.
Se hace una búsqueda nueva, conservando los resultados históricos. Auto usa
geometría real entre los aparcamientos propuestos, con caminos reales desde
el origen personal y hacia el destino personal. Los tramos a pie cuentan en
distancia y tiempo, con el límite de caminata y disponibilidad ya existentes.
Las uniones auto–pie siguen limitadas a 25 m; el punto vial debe estar a <=250 m
del aparcamiento. El centro de una superficie no certifica una entrada.
Ausencia de plazas libres, ubicación del vehículo y entrada exacta quedan
explícitamente sin confirmar. Fallo de acceso no produce un recorrido inventado.

La respuesta conserva `parking_assumption` en resumen/evidencia, seleccionados
por el usuario frente a propuestos, fecha OSM, distancias geográficas y los
indicadores de incertidumbre. El avatar empieza a pie si el primer tramo es a
pie. Se conservan las reglas de acceso inicial sin tiempo de 0.4.70.

## Validación

Backend: cuatro pruebas de selección pública, restricciones, radio, ausencia de
aparcamientos y conservación de piernas/tiempos/supuestos; siete pruebas de
acceso auto–pie previas. Android: filtros por modo y ambos radios, selección
pública y agrupación sin pérdida de elementos, más la suite existente.
Consulta real estación–Business School: propuestas a 139.53 y 83.68 m;
570.657 s cartografiados, 388.749 s a pie, 2838.229 m. Se conserva el acceso
inicial sin trazar de 62.05 m y la llegada desconocida.

APK instalada; 64 pruebas Android y 11 de backend correctas. En el teléfono se
verificaron la ruta de auto entre las propuestas, caminata mostrada de 7 min
(redondeo hacia arriba), agrupaciones y apertura táctil de la ficha P con
acciones para origen/destino. El recálculo con ID explícito se verificó contra
el proveedor sin cambiar registros del teléfono. La comprobación adicional
del botón en vivo se interrumpió porque el teléfono volvió a la búsqueda
mientras el autor lo utilizaba; no se forzó su navegación. PT está cubierto
por pruebas de filtro/modalidad y código de nombres, sin captura visual final
en ese teléfono. No se inició GPS, eligió viaje ni registró Experience.

Límite independiente observado durante búsquedas de validación: el endpoint de
predicción prospectiva devuelve 413 en búsquedas grandes, como ya ocurría en
0.4.70. No se presenta ese fallo como predicción válida y no es una validación
de D-Day. La corrección de tamaño de ese flujo queda fuera de esta entrega de
aparcamientos/paradas.

Fuentes semánticas:
- https://wiki.openstreetmap.org/wiki/Tag:amenity%3Dparking
- https://wiki.openstreetmap.org/wiki/Tag:amenity%3Dparking_entrance
- https://wiki.openstreetmap.org/wiki/Access
- https://www.openstreetmap.org/copyright
