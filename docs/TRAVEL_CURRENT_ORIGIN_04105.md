# Travel: ubicación actual como origen — 0.4.105

En el selector de dirección de A · Origin se añade «My current location» /
«Mein aktueller Standort», encima del campo de búsqueda. Permite reemplazar un
origen elegido previamente por una lectura nueva de ubicación.

La acción usa los permisos y la activación de ubicación de Android, y las reglas
de actualidad de `currentMapLocation`. Mientras espera muestra progreso; si no
llega una posición en 25 segundos, conserva el selector y permite reintentar o
escribir una dirección. Al elegir una posición actualiza A y su borrador, conserva
B, cierra el selector y deja B como siguiente punto de selección en el mapa.
La ubicación elegida es la observación obtenida al pulsar, no un origen que se
reescriba automáticamente con cada movimiento posterior. El botón de centrado
del mapa conserva su función independiente.

La operación no inicia búsquedas de rutas, Journey ni Experience. Al cerrar el
selector se cancela su solicitud pendiente y se retira la escucha correspondiente.

Comprobación: APK 0.4.105 / código 116 compilada e instalada con reemplazo;
suite JVM aprobada. `validation/verify_current_location_origin.py` pasó en el
teléfono real: opción visible, ubicación elegida como origen y selector cerrado.
Capturas y resultado privados en `.local-backups/current-origin-04105`.
