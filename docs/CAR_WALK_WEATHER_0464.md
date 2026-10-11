# Auto con accesos a pie y presentación meteorológica — 0.4.64

Entrega autorizada por el autor el 11 de octubre de 2026, instalada en teléfono
(versionName 0.4.64, versionCode 75). Backend reiniciado; OTP/GTFS-RT conservados.

Cuando GraphHopper desplaza un extremo de auto más de cinco metros, se solicita
un recorrido a pie independiente hasta el extremo solicitado. Se validan sus
geometrías y tiempos; sus extremos deben quedar a no más de 25 m de los puntos
de enlace. Los pequeños márgenes permiten ajuste a la red vial y redondeo;
no implican correspondencia exacta con la puerta del destino. No se dibujan
segmentos rectos para reemplazar caminos faltantes.

Se conservan las piernas individuales, se suman tiempos/distancias del viaje
completo y se comprueban disponibilidad a pie y límite total de caminata.
Un acceso que excede esas preferencias impide ofrecer esa alternativa como
disponible. Los resúmenes del proveedor original permanecen en la procedencia;
no se afirma identidad con su opción ordenada. Los resultados históricos no
se recalculan. La tarjeta muestra «Includes N min walking», redondeando hacia
arriba, y esos minutos forman parte del tiempo total y hora estimada de llegada.

Consulta técnica del recorrido que fallaba: conducción 611,615 s; acceso final
a pie 627,377 s / 871,352 m; total 1.238,992 s. UI: 21 min totales, de los cuales
11 min caminando. El desplazamiento geográfico previo de 265,9 m no era la
distancia caminable. Auto ahora figura context_ready=true. No incluye búsqueda
de aparcamiento ni tiempo de estacionamiento.

Si una ruta disponible carece de candidato contextual, los indicadores pueden
usar exclusivamente la simulación básica archivada de esa búsqueda. Se rotulan
«Basic simulated goals» y el detalle declara ausencia de contexto y de evaluación
del acceso a pie en esos indicadores. No se fabrica un candidato ni se incorporan
esas contribuciones como votos de recomendación. Si no hay ninguna ruta con
contexto, el backend produce solo el baseline, sin consultar una ruta ficticia.

Lluvia: sigue mostrando solo rainIntensity de Winfred en mm/h. Se selecciona
el reporte por reloj explícito cuando existe, o por última fecha conocida;
la UI muestra fecha/hora Europe/Berlin sin «Last reported rain» ni edad desconocida.
La fecha publicada no certifica actualidad ni cambia las reglas de elegibilidad
meteorológica. Sin fecha disponible se omite la hora. Acumulados permanecen
fuera de la UI. UV queda fuera de las visualizaciones; registros crudos intactos.
Humedad usa gota con porcentaje; ráfagas usan manga de viento, distinta del viento.

Validación: 117 pruebas backend y 59 Android JVM aprobadas; APK compilada e
instalada con adb install -r, versión confirmada y actividad abierta. Consulta
técnica HTTP 200, auto completo y Orion/GTFS-RT disponibles. Captura inspeccionada
de apertura; presentación de la tarjeta del nuevo recorrido pendiente de revisión
visual en una nueva búsqueda del usuario. No se registró Experience, adquirió
GPS personal ni activaron posteriores/Passport/HOTCO.
