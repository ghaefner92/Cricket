# Acceso de auto y respuesta al pausar — 0.4.65–0.4.66

Corrección del error señalado por el autor en una nueva ruta el 11 de octubre.
La consulta de auto recibía geometría, pero el recorrido a pie terminaba a
62,05 m del punto geocodificado de la estación; el filtro de 25 m borraba
la alternativa completa. No era una caída del servicio.

Se mantiene la comprobación estricta de unión auto–pie. El último extremo de
pie puede ser un acceso cartografiado dentro del margen existente de 250 m
del destino; su desplazamiento queda explícito en summary y procedencia.
No se inventa geometría ni tiempo para el tramo final. Tarjeta: tiempo hasta
el acceso, hora aproximada en ese acceso y distancia geográfica pendiente,
con tiempo desconocido. Detalles explican que el final no entra en las sumas.

Consulta técnica actual: auto 175,39 s / 1.955,192 m; pie 339,92 s / 470,098 m;
total conocido 515,31 s. UI redondeada: 9 min hasta acceso, 6 min a pie.
Auto context_ready=true; PT devuelve siete alternativas. Captura del teléfono
inspeccionada en `.local-backups/auto-goals-audit/ui-0465.png`: trazados, goals
y tiempos visibles; se detectó y corrigió recorte del texto de caminata.

El autor confirmó que pausa funcionaba, pero respondía lentamente. La UI ahora
reconoce la orden con un indicador de progreso y evita repetir órdenes mientras
se confirma el estado. No presenta una pausa pendiente como ya persistida.
El servicio elimina actualizaciones GPS al recibir pause/finish/cancel, antes
de esperar a su cola de SQLite; callbacks pendientes comprueban una bandera
volátil. Start/resume no reactivan GPS si llegó después una solicitud de pausa.
Las escrituras y eventos durables conservan su orden. Se avisa si la respuesta
supera 15 s; no se promete un límite de latencia de almacenamiento.

APK final 0.4.66/code 77 instalada con reemplazo, versión comprobada. No había
puntero de viaje activo al instalar. 118 pruebas backend aprobadas para la
corrección de geometría, y 60 JVM Android aprobadas. No se inició un viaje ni
se registró Experience/GPS para validar la pausa; su latencia real tras este
cambio permanece sin medir. Passport y HOTCO no se activaron ni modificaron.
