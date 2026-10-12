# Travel: edición manual de A protegida frente al GPS — 0.4.106

Al borrar la dirección de A, `AddressField` elimina la selección del origen.
La escucha continua de planificación detectaba entonces origen y borrador vacíos,
lo rellenaba con «My current location» y cerraba el editor mediante
`onTargetChange`. Por eso el usuario no podía empezar a escribir una dirección.

Abrir el editor de dirección, seleccionar A en el mapa o intercambiar A/B
deshabilita el relleno automático del origen durante esa pantalla de búsqueda.
La escucha lee el permiso actualizado con `rememberUpdatedState`, sin reiniciar
ni detener el GPS. Conserva la propuesta inicial de ubicación al entrar con
origen vacío, antes de que el usuario intervenga.

«My current location» en el selector sigue siendo una acción explícita. Vaciar
el campo, escribir o esperar nuevas lecturas no activa esa opción. Se conservan
la escucha del mapa y el centrado independiente.

APK 0.4.106 / código 117 compilada e instalada con reemplazo; suite JVM aprobada.
El script `validation/verify_current_location_origin.py` comprueba en teléfono
el campo vacío durante tres segundos, la escritura de una dirección durante
otros tres y, después, la selección explícita de ubicación actual. Artefactos
privados en `.local-backups/current-origin-04106`.
